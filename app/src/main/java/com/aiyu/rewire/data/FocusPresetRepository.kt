package com.aiyu.rewire.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aiyu.rewire.domain.focus.FocusConfig
import com.aiyu.rewire.domain.focus.FocusPreset
import com.aiyu.rewire.domain.focus.FocusPresetError
import com.aiyu.rewire.domain.focus.FocusPresets
import com.aiyu.rewire.domain.focus.PresetResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

/** User-saved focus presets (built-ins live in [FocusPresets.builtIn]). Mutations return the rule violation, or null on success. */
interface FocusPresetRepository {
    val userPresets: Flow<List<FocusPreset>>
    suspend fun add(name: String, config: FocusConfig): FocusPresetError?
    suspend fun rename(id: String, name: String): FocusPresetError?
    suspend fun delete(id: String)

    /** Backup restore: replaces all user presets with the valid subset of [presets]. */
    suspend fun replaceAll(presets: List<FocusPreset>)
}

private val Context.presetStore by preferencesDataStore("focus_presets")

class DataStoreFocusPresetRepository(private val store: DataStore<Preferences>) : FocusPresetRepository {
    constructor(context: Context) : this(context.presetStore)

    override val userPresets: Flow<List<FocusPreset>> = store.data.map { decode(it[KEY]) }

    override suspend fun add(name: String, config: FocusConfig) =
        mutate { FocusPresets.add(it, UUID.randomUUID().toString(), name, config) }

    override suspend fun rename(id: String, name: String) = mutate { FocusPresets.rename(it, id, name) }

    override suspend fun delete(id: String) { mutate { PresetResult.Ok(FocusPresets.delete(it, id)) } }

    override suspend fun replaceAll(presets: List<FocusPreset>) {
        store.edit { it[KEY] = encode(FocusPresets.sanitize(presets)) }
    }

    /** DataStore.edit serialises writers, so read-validate-write is atomic. */
    private suspend fun mutate(op: (List<FocusPreset>) -> PresetResult): FocusPresetError? {
        var error: FocusPresetError? = null
        store.edit { prefs ->
            when (val r = op(decode(prefs[KEY]))) {
                is PresetResult.Ok -> prefs[KEY] = encode(r.presets)
                is PresetResult.Err -> error = r.error
            }
        }
        return error
    }

    private companion object {
        val KEY = stringPreferencesKey("user_presets")
        val json = Json { ignoreUnknownKeys = true }
        val serializer = ListSerializer(FocusPreset.serializer())

        fun encode(list: List<FocusPreset>) = json.encodeToString(serializer, list)

        /** Corrupt storage reads as "no presets" rather than crashing the Focus tab. */
        fun decode(text: String?): List<FocusPreset> =
            if (text == null) emptyList() else runCatching { FocusPresets.sanitize(json.decodeFromString(serializer, text)) }.getOrDefault(emptyList())
    }
}
