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

/** What a recent change did to a tracker. */
enum class QuitChangeKind { ADDED, EDITED, SLIPPED, DELETED }

/**
 * One recent change that can be undone and redone: the tracker as it was [before] and [after] (null = absent),
 * and where it sat in the list. Kept for [Quit.UNDO_KEEP_MS], then deleted.
 */
@Serializable
data class QuitChange(
    val id: String,
    val kind: QuitChangeKind,
    val trackerId: String,
    val index: Int,
    val before: QuitHabit?,
    val after: QuitHabit?,
    val at: Long,
    val undone: Boolean = false,
)

/** Everything the Quit tab stores. [urgesRidden] counts finished "ride the wave" sessions; [changes] is the undo history. */
@Serializable
data class QuitData(val habits: List<QuitHabit> = emptyList(), val urgesRidden: Int = 0, val changes: List<QuitChange> = emptyList())

/** Where a run stands against the milestone ladder; [progress] is 0..1 from [previous] to [next] days. */
data class Milestone(val previous: Int, val next: Int, val progress: Float)

object Quit {
    const val DAY_MS = 86_400_000L
    const val MAX_NAME = 40
    const val MAX_REASON = 160
    /** ponytail: slip history capped; only recent slips are shown. */
    const val MAX_SLIPS = 100

    /** How long a change stays undoable before it is deleted. */
    const val UNDO_KEEP_MS = 30 * 60_000L
    /** ponytail: history capped; enough for any "oops" within half an hour. */
    const val MAX_CHANGES = 20

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

    /** Puts one tracker back as it was ([habit] null = absent), at [index] if it has to be re-inserted. Undo and redo use this. */
    fun put(data: QuitData, id: String, habit: QuitHabit?, index: Int): QuitData {
        val rest = data.habits.filterNot { it.id == id }
        if (habit == null) return data.copy(habits = rest)
        val at = data.habits.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: index
        return data.copy(habits = rest.toMutableList().apply { add(at.coerceIn(0, rest.size), habit) })
    }

    /** Changes still inside the 30-minute window, newest first. */
    fun recentChanges(data: QuitData, now: Long) = data.changes.filter { now - it.at < UNDO_KEEP_MS }.reversed()

    /** The newest change to one tracker still inside the window, if any. */
    fun lastChange(data: QuitData, trackerId: String, now: Long) = recentChanges(data, now).firstOrNull { it.trackerId == trackerId }

    /** Drops changes older than the window. */
    fun prune(data: QuitData, now: Long) = data.copy(changes = data.changes.filter { now - it.at < UNDO_KEEP_MS })

    /**
     * Applies [op] to tracker [trackerId] and records it as an undoable change with id [changeId].
     * A rejected op (nothing changed) records nothing.
     */
    fun record(data: QuitData, changeId: String, kind: QuitChangeKind, trackerId: String, now: Long, op: (QuitData) -> QuitData): QuitData {
        val index = data.habits.indexOfFirst { it.id == trackerId }
        val before = data.habits.getOrNull(index)
        val next = op(data)
        val after = next.habits.find { it.id == trackerId }
        if (before == after) return data
        val change = QuitChange(changeId, kind, trackerId, index, before, after, now)
        return prune(next, now).let { it.copy(changes = (it.changes + change).takeLast(MAX_CHANGES)) }
    }

    /** Undo only while the tracker still looks as this change left it, so an older change never overwrites a newer one. */
    fun canUndo(data: QuitData, c: QuitChange) = !c.undone && data.habits.find { it.id == c.trackerId } == c.after

    fun canRedo(data: QuitData, c: QuitChange) = c.undone && data.habits.find { it.id == c.trackerId } == c.before

    fun undo(data: QuitData, changeId: String): QuitData {
        val c = data.changes.find { it.id == changeId }?.takeIf { canUndo(data, it) } ?: return data
        return put(data, c.trackerId, c.before, c.index).markUndone(changeId, true)
    }

    fun redo(data: QuitData, changeId: String): QuitData {
        val c = data.changes.find { it.id == changeId }?.takeIf { canRedo(data, it) } ?: return data
        return put(data, c.trackerId, c.after, c.index).markUndone(changeId, false)
    }

    private fun QuitData.markUndone(changeId: String, undone: Boolean) =
        copy(changes = changes.map { if (it.id == changeId) it.copy(undone = undone) else it })

    fun urgeRidden(data: QuitData) = data.copy(urgesRidden = data.urgesRidden + 1)

    /**
     * Same thought all day, a new one tomorrow; [offset] lets the user ask for another. The stride
     * hops between themes (the list is grouped by theme) and, being prime, still visits every line.
     */
    fun thoughtIndex(epochDay: Long, offset: Int, size: Int): Int =
        if (size == 0) 0 else Math.floorMod((epochDay + offset) * 131, size.toLong()).toInt()

    private fun QuitData.map(id: String, f: (QuitHabit) -> QuitHabit) = copy(habits = habits.map { if (it.id == id) f(it) else it })
}
