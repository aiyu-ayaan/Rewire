package com.aiyu.rewire.feature.profile

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiyu.rewire.data.BackupRepository
import com.aiyu.rewire.domain.backup.BackupCodec
import com.aiyu.rewire.domain.backup.BackupException
import com.aiyu.rewire.domain.backup.BackupSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** One-shot result the screen shows once, then [DataViewModel.messageShown] clears. */
enum class DataMessage { EXPORTED, IMPORTED, CLEARED, EXPORT_FAILED, FILE_UNREADABLE, NOT_A_BACKUP, UNSUPPORTED_VERSION, INCONSISTENT, FOCUS_ACTIVE, IMPORT_FAILED, CLEAR_FAILED }

data class DataUiState(
    val busy: Boolean = false,
    /** A parsed, valid file waiting for the user to confirm it overwrites current data. */
    val pendingImport: BackupSnapshot? = null,
    val confirmClear: Boolean = false,
    val message: DataMessage? = null,
)

@HiltViewModel
class DataViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backup: BackupRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(DataUiState())
    val state: StateFlow<DataUiState> = _state.asStateFlow()

    fun export(uri: Uri) = run(DataMessage.EXPORT_FAILED) {
        val text = BackupCodec.encode(backup.export())
        withContext(Dispatchers.IO) {
            // "wt" truncates: a shorter file must not keep the tail of an old one.
            context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(text) } ?: error("no stream")
        }
        DataMessage.EXPORTED
    }

    /** Reads and validates only; nothing is written until [confirmImport]. */
    fun readImport(uri: Uri) = run(DataMessage.FILE_UNREADABLE) {
        val text = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("no stream")
        }
        _state.update { it.copy(pendingImport = BackupCodec.decode(text)) }
        null
    }

    fun confirmImport() {
        val snapshot = _state.value.pendingImport ?: return
        _state.update { it.copy(pendingImport = null) }
        run(DataMessage.IMPORT_FAILED) { backup.import(snapshot); DataMessage.IMPORTED }
    }

    fun requestClear() = _state.update { it.copy(confirmClear = true) }

    fun confirmClear() {
        _state.update { it.copy(confirmClear = false) }
        run(DataMessage.CLEAR_FAILED) { backup.clearHistory(); DataMessage.CLEARED }
    }

    fun dismissDialogs() = _state.update { it.copy(pendingImport = null, confirmClear = false) }
    fun messageShown() = _state.update { it.copy(message = null) }

    private fun run(fallback: DataMessage, block: suspend () -> DataMessage?) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val message = try {
                block()
            } catch (e: BackupException) {
                when (e.reason) {
                    BackupException.Reason.FOCUS_ACTIVE -> DataMessage.FOCUS_ACTIVE
                    BackupException.Reason.UNSUPPORTED_VERSION -> DataMessage.UNSUPPORTED_VERSION
                    BackupException.Reason.INCONSISTENT -> DataMessage.INCONSISTENT
                    BackupException.Reason.NOT_A_BACKUP, BackupException.Reason.MALFORMED -> DataMessage.NOT_A_BACKUP
                }
            } catch (e: Exception) {
                fallback
            }
            _state.update { it.copy(busy = false, message = message ?: it.message) }
        }
    }
}
