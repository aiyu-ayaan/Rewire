package com.rewire.app.domain.warning

import com.rewire.app.domain.habit.WarningLevel
import kotlinx.serialization.Serializable
import kotlin.random.Random

enum class WarningCategory {
    DISCIPLINE, STUDY, FITNESS, SOCIAL_MEDIA, GAMING, PRODUCTIVITY, SLEEP, MONEY, CUSTOM
}

@Serializable
data class Warning(
    val id: String,
    val category: WarningCategory,
    val level: WarningLevel,
    val title: String,
    val message: String,
    val motivationalMessage: String,
    val enabled: Boolean = true,
    val favorite: Boolean = false,
    val custom: Boolean = false,
)

object WarningPicker {
    /**
     * Picks an enabled warning for [level]. Favorites get double weight.
     * Falls back to any enabled warning so a configured habit never shows an empty screen.
     */
    fun pick(warnings: List<Warning>, level: WarningLevel, random: Random = Random): Warning? {
        val enabled = warnings.filter { it.enabled }
        val pool = enabled.filter { it.level == level }.ifEmpty { enabled }
        if (pool.isEmpty()) return null
        val weighted = pool.flatMap { if (it.favorite) listOf(it, it) else listOf(it) }
        return weighted[random.nextInt(weighted.size)]
    }
}
