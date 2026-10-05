package com.aiyu.rewire.feature.quit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiyu.rewire.core.undo.UndoCenter
import com.aiyu.rewire.core.undo.UndoOffer
import com.aiyu.rewire.data.QuitRepository
import com.aiyu.rewire.domain.quit.Quit
import com.aiyu.rewire.domain.quit.QuitData
import com.aiyu.rewire.domain.quit.QuitChangeKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class QuitViewModel @Inject constructor(private val repo: QuitRepository) : ViewModel() {

    /** Null until DataStore answers, so the empty state never flashes before real data. */
    val data: StateFlow<QuitData?> = repo.data.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Runs show minutes; a 15 s tick keeps them current without a busy loop. */
    val now: StateFlow<Long> = flow {
        while (true) { emit(System.currentTimeMillis()); delay(15_000) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), System.currentTimeMillis())

    private fun update(op: (QuitData, Long) -> QuitData) = viewModelScope.launch {
        val t = System.currentTimeMillis()
        repo.update { op(it, t) }
    }

    // Changes older than the undo window are deleted whenever the tab opens.
    init { viewModelScope.launch { repo.update { Quit.prune(it, System.currentTimeMillis()) } } }

    /** Runs [op] on one tracker, saves it to the undo history and offers to take it back. */
    private fun undoable(kind: QuitChangeKind, trackerId: String, op: (QuitData, Long) -> QuitData) = viewModelScope.launch {
        val changeId = UUID.randomUUID().toString()
        var saved = 0
        repo.update { d ->
            val t = System.currentTimeMillis()
            Quit.record(d, changeId, kind, trackerId, t) { op(it, t) }.also { n -> saved = if (n.changes.any { it.id == changeId }) n.changes.size else 0 }
        }
        if (saved > 0) UndoCenter.offer(UndoOffer(kind.message, saved, undo = { repo.update { Quit.undo(it, changeId) } }, redo = { repo.update { Quit.redo(it, changeId) } }))
    }

    fun add(name: String, reason: String, startedAt: Long) = UUID.randomUUID().toString().let { id -> undoable(QuitChangeKind.ADDED, id) { d, t -> Quit.add(d, id, name, reason, startedAt, t) } }
    fun edit(id: String, name: String, reason: String, startedAt: Long) = undoable(QuitChangeKind.EDITED, id) { d, t -> Quit.edit(d, id, name, reason, startedAt, t) }
    fun slip(id: String) = undoable(QuitChangeKind.SLIPPED, id) { d, t -> Quit.slip(d, id, t) }
    fun delete(id: String) = undoable(QuitChangeKind.DELETED, id) { d, _ -> Quit.delete(d, id) }
    fun undo(changeId: String) = update { d, _ -> Quit.undo(d, changeId) }
    fun redo(changeId: String) = update { d, _ -> Quit.redo(d, changeId) }
    fun urgeRidden() = update { d, _ -> Quit.urgeRidden(d) }
}
