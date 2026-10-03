package com.aiyu.rewire.domain.focus

import kotlinx.serialization.Serializable

/** A named focus/break/cycles combination. Built-ins ship as data (display names come from strings.xml by id); user presets are persisted. */
@Serializable
data class FocusPreset(
    val id: String,
    val name: String,
    val focusMinutes: Int,
    val breakMinutes: Int,
    val cycles: Int,
) {
    fun toConfig() = FocusConfig(focusMinutes, breakMinutes, cycles)
}

enum class FocusPresetError { NAME_BLANK, NAME_TOO_LONG, NAME_TAKEN, TOO_MANY, INVALID_CONFIG }

sealed interface PresetResult {
    data class Ok(val presets: List<FocusPreset>) : PresetResult
    data class Err(val error: FocusPresetError) : PresetResult
}

/** Pure preset rules: naming (non-blank, unique, max length) and [FocusConfig] validity. */
object FocusPresets {
    const val MAX_NAME = 24
    const val MAX_USER_PRESETS = 12

    val builtIn: List<FocusPreset> = listOf(
        FocusPreset("builtin_pomodoro", "Pomodoro", 25, 5, 4),
        FocusPreset("builtin_deep_work", "Deep work", 50, 10, 4),
        FocusPreset("builtin_sprint", "Sprint", 20, 5, 3),
    )

    private fun norm(s: String) = s.trim().lowercase()

    /** [user] = existing user presets; [ignoreId] = the preset being renamed. */
    fun validateName(name: String, user: List<FocusPreset>, ignoreId: String? = null): FocusPresetError? = when {
        name.isBlank() -> FocusPresetError.NAME_BLANK
        name.trim().length > MAX_NAME -> FocusPresetError.NAME_TOO_LONG
        (builtIn + user).any { it.id != ignoreId && norm(it.name) == norm(name) } -> FocusPresetError.NAME_TAKEN
        else -> null
    }

    fun add(user: List<FocusPreset>, id: String, name: String, config: FocusConfig): PresetResult {
        if (config.validate() != null) return PresetResult.Err(FocusPresetError.INVALID_CONFIG)
        if (user.size >= MAX_USER_PRESETS) return PresetResult.Err(FocusPresetError.TOO_MANY)
        validateName(name, user)?.let { return PresetResult.Err(it) }
        return PresetResult.Ok(user + FocusPreset(id, name.trim(), config.focusMinutes, config.breakMinutes, config.cycles))
    }

    fun rename(user: List<FocusPreset>, id: String, name: String): PresetResult {
        validateName(name, user, ignoreId = id)?.let { return PresetResult.Err(it) }
        return PresetResult.Ok(user.map { if (it.id == id) it.copy(name = name.trim()) else it })
    }

    fun delete(user: List<FocusPreset>, id: String) = user.filterNot { it.id == id }

    /** Drops anything invalid or duplicated, e.g. from a hand-edited backup. */
    fun sanitize(list: List<FocusPreset>): List<FocusPreset> =
        list.fold(emptyList()) { acc, p ->
            if (acc.size < MAX_USER_PRESETS && p.toConfig().isValid && validateName(p.name, acc) == null && acc.none { it.id == p.id }) acc + p.copy(name = p.name.trim()) else acc
        }
}
