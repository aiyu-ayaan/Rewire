package com.aiyu.rewire.feature.quit

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiyu.rewire.R
import com.aiyu.rewire.core.undo.UndoCenter
import com.aiyu.rewire.core.undo.UndoOffer
import com.aiyu.rewire.data.QuitRepository
import com.aiyu.rewire.domain.quit.Quit
import com.aiyu.rewire.domain.quit.QuitData
import com.aiyu.rewire.domain.quit.QuitHabit
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

    /** Runs [op] on one tracker and offers to undo it: undo puts the old tracker back, redo the new one. */
    private fun undoable(@StringRes message: Int, id: String, op: (QuitData, Long) -> QuitData) = viewModelScope.launch {
        val t = System.currentTimeMillis()
        var before: QuitHabit? = null
        var after: QuitHabit? = null
        var index = 0
        repo.update { d ->
            index = d.habits.indexOfFirst { it.id == id }
            before = d.habits.getOrNull(index)
            op(d, t).also { n -> after = n.habits.find { it.id == id } }
        }
        if (before == after) return@launch // rejected (e.g. blank name): nothing to take back
        val (b, a, i) = Triple(before, after, index)
        UndoCenter.offer(UndoOffer(message, undo = { repo.update { Quit.put(it, id, b, i) } }, redo = { repo.update { Quit.put(it, id, a, i) } }))
    }

    fun add(name: String, reason: String, startedAt: Long) = UUID.randomUUID().toString().let { id -> undoable(R.string.undo_quit_added, id) { d, t -> Quit.add(d, id, name, reason, startedAt, t) } }
    fun edit(id: String, name: String, reason: String, startedAt: Long) = undoable(R.string.undo_quit_edited, id) { d, t -> Quit.edit(d, id, name, reason, startedAt, t) }
    fun slip(id: String) = undoable(R.string.undo_quit_slipped, id) { d, t -> Quit.slip(d, id, t) }
    fun delete(id: String) = undoable(R.string.undo_quit_deleted, id) { d, _ -> Quit.delete(d, id) }
    fun urgeRidden() = update { d, _ -> Quit.urgeRidden(d) }
}
