package com.aiyu.rewire.domain.quit

import kotlinx.serialization.Serializable

/**
 * Something the user is quitting outright (no app involved). The name is the user's own words and
 * never leaves the device. [startedAt] is the start of the current run; a slip moves it to "now".
 */
@Serializable
data class QuitHabit(
    val id: String,
    val name: String,
    val reason: String = "",
    val startedAt: Long,
    val createdAt: Long,
    val bestMillis: Long = 0,
    val slips: List<Long> = emptyList(),
)

/** Everything the Quit tab stores. [urgesRidden] counts finished "ride the wave" sessions. */
@Serializable
data class QuitData(val habits: List<QuitHabit> = emptyList(), val urgesRidden: Int = 0)

/** Where a run stands against the milestone ladder; [progress] is 0..1 from [previous] to [next] days. */
data class Milestone(val previous: Int, val next: Int, val progress: Float)

object Quit {
    const val DAY_MS = 86_400_000L
    const val MAX_NAME = 40
    const val MAX_REASON = 160
    /** ponytail: slip history capped; only recent slips are shown. */
    const val MAX_SLIPS = 100

    /** Days worth celebrating; after the last one every further year counts. */
    val MILESTONES = listOf(1, 3, 7, 14, 21, 30, 60, 90, 180, 365)

    fun isValidName(name: String) = name.isNotBlank() && name.trim().length <= MAX_NAME

    fun runMillis(h: QuitHabit, now: Long) = (now - h.startedAt).coerceAtLeast(0)

    fun runDays(h: QuitHabit, now: Long) = (runMillis(h, now) / DAY_MS).toInt()

    fun bestMillis(h: QuitHabit, now: Long) = maxOf(h.bestMillis, runMillis(h, now))

    fun milestone(days: Int): Milestone {
        val next = MILESTONES.firstOrNull { it > days } ?: ((days / 365 + 1) * 365)
        val previous = MILESTONES.lastOrNull { it <= days }?.let { if (days >= 365) days / 365 * 365 else it } ?: 0
        return Milestone(previous, next, (days - previous).toFloat() / (next - previous))
    }

    /** Milestones already reached in the current run. */
    fun reached(days: Int) = MILESTONES.filter { it <= days }

    /** Index into the encouragement lines: fresh start, first days, first week, first month, three months, beyond. */
    fun stage(days: Int) = when {
        days < 1 -> 0
        days < 3 -> 1
        days < 7 -> 2
        days < 30 -> 3
        days < 90 -> 4
        else -> 5
    }

    /** Length of each run that a slip ended, newest first; the first slip has no known start, so it is skipped. */
    fun endedRuns(h: QuitHabit): List<Pair<Long, Long>> =
        h.slips.zipWithNext { a, b -> b to (b - a) }.reversed()

    fun add(data: QuitData, id: String, name: String, reason: String, startedAt: Long, now: Long): QuitData {
        if (!isValidName(name)) return data
        val h = QuitHabit(id, name.trim(), reason.trim().take(MAX_REASON), startedAt.coerceAtMost(now), now)
        return data.copy(habits = data.habits + h)
    }

    /** Edits keep the best run; moving the start date earlier can only lengthen the current run. */
    fun edit(data: QuitData, id: String, name: String, reason: String, startedAt: Long, now: Long): QuitData {
        if (!isValidName(name)) return data
        return data.map(id) { it.copy(name = name.trim(), reason = reason.trim().take(MAX_REASON), startedAt = startedAt.coerceAtMost(now)) }
    }

    /** A slip keeps the best run, records when, and starts a new run now. Nothing is erased. */
    fun slip(data: QuitData, id: String, now: Long): QuitData = data.map(id) {
        it.copy(bestMillis = bestMillis(it, now), startedAt = now, slips = (it.slips + now).takeLast(MAX_SLIPS))
    }

    fun delete(data: QuitData, id: String) = data.copy(habits = data.habits.filterNot { it.id == id })

    fun urgeRidden(data: QuitData) = data.copy(urgesRidden = data.urgesRidden + 1)

    /**
     * Same thought all day, a new one tomorrow; [offset] lets the user ask for another. The stride
     * hops between themes (the list is grouped by theme) and, being prime, still visits every line.
     */
    fun thoughtIndex(epochDay: Long, offset: Int, size: Int): Int =
        if (size == 0) 0 else Math.floorMod((epochDay + offset) * 131, size.toLong()).toInt()

    private fun QuitData.map(id: String, f: (QuitHabit) -> QuitHabit) = copy(habits = habits.map { if (it.id == id) f(it) else it })
}
