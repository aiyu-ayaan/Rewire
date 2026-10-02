package com.aiyu.rewire.core.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import com.aiyu.rewire.BuildConfig
import com.aiyu.rewire.core.settings.Settings
import com.aiyu.rewire.core.settings.SettingsRepository
import com.aiyu.rewire.di.AppScope
import com.aiyu.rewire.domain.update.Release
import com.aiyu.rewire.domain.update.ReleaseAsset
import com.aiyu.rewire.domain.update.Releases
import com.aiyu.rewire.domain.update.UpdateChannel
import com.aiyu.rewire.domain.update.Version
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The app keeping itself up to date from the GitHub releases it was built by (same design as BetweenUs,
 * see development/docs/UPDATES.md). No store, no update server: ask GitHub what exists, work out whether
 * any of it is newer, download the APK, hand it to Android's package installer.
 *
 * It never installs behind anybody's back: the last screen is always the system's. The only thing that
 * leaves the device is one unauthenticated GET of the public release list; no usage data is ever sent.
 */
@Singleton
class AppUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: SettingsRepository,
    private val settings: StateFlow<Settings?>,
    @AppScope private val scope: CoroutineScope,
) {
    private val downloads = File(context.cacheDir, "updates")

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** True while the Updates screen is open: it draws the offer itself, so the global sheet stays down. */
    val offerShownInline = MutableStateFlow(false)

    val installedName: String get() = BuildConfig.VERSION_NAME
    private val installed: Version? by lazy { Version.parse(BuildConfig.VERSION_NAME) }

    val enabled: Boolean get() = settings.value?.updatesEnabled ?: true
    val channel: UpdateChannel get() = settings.value?.updateChannel ?: UpdateChannel.forVersion(installedName)
    private val snoozed: Boolean get() = System.currentTimeMillis() < (settings.value?.updateSnoozedUntil ?: 0)

    /**
     * Ask GitHub what exists. [manual] is the Check button: it says so when nothing is new and ignores a
     * snooze. The launch / daily check stays silent, and skips if it asked within the last hour
     * (unauthenticated GitHub allows 60 requests an hour per address). [channel] is passed explicitly after
     * the user changes it, because the settings flow lags the database write by a moment.
     */
    suspend fun check(manual: Boolean = false, channel: UpdateChannel = this.channel): UpdateState = withContext(Dispatchers.IO) {
        val busy = _state.value
        if (busy is UpdateState.Downloading || busy is UpdateState.Ready) return@withContext busy
        if (!manual) {
            if (!enabled || snoozed) return@withContext busy
            val last = settings.value?.updateLastChecked ?: 0
            if (busy is UpdateState.Available || System.currentTimeMillis() - last < MIN_GAP_MILLIS) return@withContext busy
        }
        _state.value = UpdateState.Checking
        val next = runCatching {
            val connection = open(Releases.API)
            try {
                if (connection.responseCode !in 200..299) error("GitHub answered ${connection.responseCode}")
                val pick = Releases.pick(Releases.parse(connection.inputStream.bufferedReader().use { it.readText() }), installed, channel)
                val apk = pick?.let { Releases.apkFor(it, lite = !BuildConfig.ACCESSIBILITY) } // a release whose Android job failed still exists, and is not an offer
                if (pick == null || apk == null) UpdateState.UpToDate else UpdateState.Available(pick, apk)
            } finally {
                connection.disconnect()
            }
        }.getOrElse { UpdateState.Failed(it.message ?: "The update check failed") }
        repo.setUpdateLastChecked(System.currentTimeMillis())
        _state.value = next
        next
    }

    /** Download in the app scope, so leaving the screen doesn't cancel it. Progress is on [state]. */
    fun download(release: Release, asset: ReleaseAsset) {
        if (_state.value is UpdateState.Downloading) return
        scope.launch(Dispatchers.IO) {
            runCatching { fetch(release, asset) }
                .onFailure { _state.value = UpdateState.Failed(it.message ?: "The download failed") }
        }
    }

    private fun fetch(release: Release, asset: ReleaseAsset) {
        downloads.mkdirs()
        // Everything else here is a previous attempt or version.
        downloads.listFiles()?.forEach { if (it.name != asset.name) it.delete() }
        val target = File(downloads, asset.name)
        if (asset.size > 0 && target.length() == asset.size && verified(target, asset)) {
            _state.value = UpdateState.Ready(release, target)
            return
        }
        _state.value = UpdateState.Downloading(release, 0f)
        val connection = open(asset.url)
        try {
            if (connection.responseCode !in 200..299) error("The download failed (${connection.responseCode})")
            val total = if (asset.size > 0) asset.size else connection.contentLengthLong
            var written = 0L
            var lastReported = 0f
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        written += read
                        val fraction = if (total > 0) (written.toFloat() / total).coerceIn(0f, 1f) else 0f
                        if (fraction - lastReported >= 0.01f) { // a state emission per percent, not per 64 KB
                            lastReported = fraction
                            _state.value = UpdateState.Downloading(release, fraction)
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        if (!verified(target, asset)) {
            target.delete()
            error("The download did not match the checksum GitHub published for it")
        }
        _state.value = UpdateState.Ready(release, target)
    }

    /** SHA-256 when GitHub records one for the asset; Android's signature check is the backstop either way. */
    private fun verified(file: File, asset: ReleaseAsset): Boolean {
        val expected = asset.sha256 ?: return true
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) { val n = input.read(buffer); if (n == -1) break; digest.update(buffer, 0, n) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) } == expected
    }

    /**
     * "Install unknown apps" is a per-app setting from API 26, not a runtime permission: it can only be
     * opened in settings, not asked for with a dialog.
     */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun installPermissionIntent(): Intent =
        Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Hand the APK to Android's package installer, which shows what replaces what and asks. A session
     * rather than ACTION_VIEW so a refusal (typically an APK signed with a different key) comes back to
     * [UpdateInstallReceiver] with a reason instead of a dialog that just closes.
     */
    fun install(apk: File) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                val installer = context.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    setAppPackageName(context.packageName)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
                }
                val id = installer.createSession(params)
                installer.openSession(id).use { session ->
                    session.openWrite("rewire.apk", 0, apk.length()).use { sink ->
                        apk.inputStream().use { it.copyTo(sink) }
                        session.fsync(sink)
                    }
                    val status = PendingIntent.getBroadcast(
                        context, id,
                        Intent(context, UpdateInstallReceiver::class.java).setAction(UpdateInstallReceiver.ACTION_STATUS),
                        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT, // the system adds the status extras
                    )
                    session.commit(status.intentSender)
                }
            }.onFailure { fail(it.message ?: "Could not start the install") }
        }
    }

    /** "Not now": quiet for a day. Asking again tomorrow is asking; asking on every launch is nagging. */
    fun snooze() {
        _state.value = UpdateState.Idle
        scope.launch { repo.setUpdateSnoozedUntil(System.currentTimeMillis() + DAY_MILLIS) }
    }

    /** Dismissed without deciding; comes back on the next launch. */
    fun dismiss() { _state.value = UpdateState.Idle }

    /** What was on offer came from the old channel and may not exist on a narrower one. */
    fun onChannelChanged() { _state.value = UpdateState.Idle }

    fun fail(message: String) { _state.value = UpdateState.Failed(message) }

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 20_000
        setRequestProperty("Accept", "application/vnd.github+json")
        setRequestProperty("User-Agent", "Rewire/${BuildConfig.VERSION_NAME}") // GitHub rejects requests without one
    }

    private companion object {
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
        const val MIN_GAP_MILLIS = 60L * 60 * 1000
    }
}

/** Where a check, a download or an install has got to. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: Release, val apk: ReleaseAsset) : UpdateState
    data class Downloading(val release: Release, val progress: Float) : UpdateState
    data class Ready(val release: Release, val file: File) : UpdateState
    data class Failed(val message: String) : UpdateState
}
