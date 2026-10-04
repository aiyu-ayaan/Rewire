package com.aiyu.rewire.feature.quit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiyu.rewire.data.QuitRepository
import com.aiyu.rewire.domain.quit.Quit
import com.aiyu.rewire.domain.quit.QuitData
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

    fun add(name: String, reason: String, startedAt: Long) = update { d, t -> Quit.add(d, UUID.randomUUID().toString(), name, reason, startedAt, t) }
    fun edit(id: String, name: String, reason: String, startedAt: Long) = update { d, t -> Quit.edit(d, id, name, reason, startedAt, t) }
    fun slip(id: String) = update { d, t -> Quit.slip(d, id, t) }
    fun delete(id: String) = update { d, _ -> Quit.delete(d, id) }
    fun urgeRidden() = update { d, _ -> Quit.urgeRidden(d) }
}
