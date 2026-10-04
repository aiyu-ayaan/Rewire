package com.aiyu.rewire.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aiyu.rewire.domain.quit.QuitData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/**
 * Quit trackers. Private by design: own DataStore, not in Room, events, Matrix or backup export,
 * so nothing about them shows up anywhere else in the app.
 */
interface QuitRepository {
    val data: Flow<QuitData>
    /** Read-modify-write; [op] is a pure function from [com.aiyu.rewire.domain.quit.Quit]. */
    suspend fun update(op: (QuitData) -> QuitData)
}

private val Context.quitStore by preferencesDataStore("quit")

class DataStoreQuitRepository(private val store: DataStore<Preferences>) : QuitRepository {
    constructor(context: Context) : this(context.applicationContext.quitStore)

    override val data: Flow<QuitData> = store.data.map { decode(it[KEY]) }

    override suspend fun update(op: (QuitData) -> QuitData) {
        store.edit { it[KEY] = json.encodeToString(QuitData.serializer(), op(decode(it[KEY]))) }
    }

    private companion object {
        val KEY = stringPreferencesKey("data")
        val json = Json { ignoreUnknownKeys = true }

        /** Corrupt storage reads as empty rather than crashing the tab. */
        fun decode(text: String?): QuitData =
            if (text == null) QuitData() else runCatching { json.decodeFromString(QuitData.serializer(), text) }.getOrDefault(QuitData())
    }
}
