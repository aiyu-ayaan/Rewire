package com.aiyu.rewire.core.undo

import androidx.annotation.StringRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The last change the user can take back. One offer at a time: a newer change replaces the older one.
 * [undone] flips each time the app-level snackbar runs [undo] or [redo].
 */
data class UndoOffer(@StringRes val message: Int, val undo: suspend () -> Unit, val redo: suspend () -> Unit, val undone: Boolean = false)

/** ponytail: in-memory only; an offer dies with the process, which is fine for a few-second snackbar. */
object UndoCenter {
    private val _offer = MutableStateFlow<UndoOffer?>(null)
    val offer: StateFlow<UndoOffer?> = _offer

    fun offer(o: UndoOffer) { _offer.value = o }

    /** Replace [current] with [next] only if nothing newer arrived meanwhile. */
    fun settle(current: UndoOffer, next: UndoOffer?) { _offer.compareAndSet(current, next) }
}
