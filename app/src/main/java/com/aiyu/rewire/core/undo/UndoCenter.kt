package com.aiyu.rewire.core.undo

import androidx.annotation.StringRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The last change the user can take back. One offer at a time: a newer change replaces the older one.
 * [undone] flips each time the app-level snackbar runs [undo] or [redo]. With [saved] >= 2 changes on hand the
 * snackbar points to the full history instead.
 */
data class UndoOffer(@StringRes val message: Int, val saved: Int, val undo: suspend () -> Unit, val redo: suspend () -> Unit, val undone: Boolean = false)

/** ponytail: the snackbar offer is in-memory only; the history behind it is persisted by the feature. */
object UndoCenter {
    private val _offer = MutableStateFlow<UndoOffer?>(null)
    val offer: StateFlow<UndoOffer?> = _offer

    fun offer(o: UndoOffer) { _offer.value = o }

    private val _history = MutableStateFlow(false)
    /** The snackbar's "View" asks the screen on display to open its change history. */
    val historyRequested: StateFlow<Boolean> = _history

    fun requestHistory() { _history.value = true }
    fun historyShown() { _history.value = false }

    /** Replace [current] with [next] only if nothing newer arrived meanwhile. */
    fun settle(current: UndoOffer, next: UndoOffer?) { _offer.compareAndSet(current, next) }
}
