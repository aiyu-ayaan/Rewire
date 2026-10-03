package com.aiyu.rewire.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.aiyu.rewire.domain.goals.Goals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

interface GoalsRepository {
    val goals: Flow<Goals>
    suspend fun current(): Goals
    /** Invalid values are ignored so a bad import can never poison the store. */
    suspend fun set(goals: Goals)
}

private val Context.goalsStore by preferencesDataStore("goals")

/** Goals live in their own small DataStore: the Room schema is not touched. */
class DataStoreGoalsRepository(context: Context) : GoalsRepository {
    private val store = context.applicationContext.goalsStore
    private val focus = intPreferencesKey("daily_focus_minutes")
    private val overrides = intPreferencesKey("max_overrides_per_day")

    override val goals: Flow<Goals> = store.data.map { it.toGoals() }

    override suspend fun current(): Goals = goals.first()

    override suspend fun set(goals: Goals) {
        if (!goals.isValid) return
        store.edit { p ->
            goals.dailyFocusMinutes?.let { p[focus] = it } ?: p.remove(focus)
            goals.maxOverridesPerDay?.let { p[overrides] = it } ?: p.remove(overrides)
        }
    }

    private fun Preferences.toGoals() = Goals(this[focus], this[overrides]).takeIf { it.isValid } ?: Goals()
}
