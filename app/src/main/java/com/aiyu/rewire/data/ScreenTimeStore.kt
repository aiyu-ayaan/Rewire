package com.aiyu.rewire.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aiyu.rewire.core.guard.UsageTracker
import com.aiyu.rewire.domain.analytics.ScreenTimeHistory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.LocalDate

private val Context.screenTimeStore by preferencesDataStore("screen_time")

/** Daily screen-time snapshots so weekly/monthly charts keep past days after UsageStats forgets them. Local only. */
class ScreenTimeStore(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.screenTimeStore)

    val history: Flow<Map<LocalDate, Int>> = store.data.map { decode(it[KEY]) }

    /** Reads today and any missing day of the past week from [usage]; a no-op without Usage access. */
    suspend fun sync(usage: UsageTracker, today: LocalDate = LocalDate.now()) {
        store.edit { prefs ->
            var map = decode(prefs[KEY])
            for (day in ScreenTimeHistory.daysToSync(map, today)) {
                val minutes = usage.screenTimeOn(day) ?: return@edit
                map = ScreenTimeHistory.merge(map, day, minutes, today)
            }
            prefs[KEY] = encode(map)
        }
    }

    /** Backup restore: replaces the stored days with [days] (already sanitised). */
    suspend fun replaceAll(days: Map<LocalDate, Int>) { store.edit { it[KEY] = encode(days) } }

    /** Clear-history support. */
    suspend fun clear() { store.edit { it.remove(KEY) } }

    private companion object {
        val KEY = stringPreferencesKey("days")
        val serializer = MapSerializer(String.serializer(), Int.serializer())

        fun encode(map: Map<LocalDate, Int>) = Json.encodeToString(serializer, map.mapKeys { it.key.toString() })

        /** Corrupt storage reads as "no history" rather than crashing Matrix. */
        fun decode(text: String?): Map<LocalDate, Int> =
            if (text == null) emptyMap()
            else runCatching { Json.decodeFromString(serializer, text).mapKeys { LocalDate.parse(it.key) } }.getOrDefault(emptyMap())
    }
}
