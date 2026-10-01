package com.rewire.app.feature.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rewire.app.AppContainer
import com.rewire.app.core.settings.FocusBypass
import com.rewire.app.domain.focus.FocusConfig
import com.rewire.app.domain.focus.FocusSession
import com.rewire.app.domain.focus.FocusState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** UI facade over the app-scoped [com.rewire.app.core.focus.FocusController]; the timer itself survives this ViewModel. */
class FocusViewModel(private val c: AppContainer) : ViewModel() {

    val state: StateFlow<FocusState> = c.focus.current
    val now: StateFlow<Long> = c.focus.now
    val awaitingNote: StateFlow<FocusSession?> = c.focus.awaitingNote

    private val _draft = MutableStateFlow(FocusConfig(focusMinutes = 25, breakMinutes = 5, cycles = 4))
    val draft: StateFlow<FocusConfig> = _draft.asStateFlow()

    val bypass: StateFlow<FocusBypass?> = c.settings.map { it?.focusBypass }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val focusDndEnabled: StateFlow<Boolean?> = c.settings.map { it?.focusDndEnabled }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val isDndAccessGranted: Boolean get() = c.dndManager.isAccessGranted
    fun dndSettingsIntent() = c.dndManager.dndSettingsIntent()

    fun setDraft(config: FocusConfig) {
        // Keep break <= focus while the user drags focus down.
        _draft.value = config.copy(breakMinutes = config.breakMinutes.coerceAtMost(config.focusMinutes))
    }

    fun setBypass(b: FocusBypass) = viewModelScope.launch { c.settingsRepository.setFocusBypass(b) }

    fun setFocusDndEnabled(enabled: Boolean) = viewModelScope.launch {
        c.settingsRepository.setFocusDndEnabled(enabled)
        c.focus.onDndSettingChanged(enabled)
    }

    fun start() = c.focus.start(_draft.value)

    /** Debug builds only: 20 s / 10 s session to exercise phase changes, chime and notifications fast. */
    fun startQuickTest() = c.focus.start(FocusState.QUICK_TEST)

    fun pause() = c.focus.pause()
    fun resume() = c.focus.resume()
    fun skipBreak() = c.focus.skipBreak()
    fun end() = c.focus.end()
    fun reset() = c.focus.reset(_draft.value)

    fun saveNote(note: String) = c.focus.saveNote(note)
    fun skipNote() = c.focus.skipNote()
}
