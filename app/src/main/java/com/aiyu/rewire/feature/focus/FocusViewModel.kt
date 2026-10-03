package com.aiyu.rewire.feature.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiyu.rewire.core.focus.FocusController
import com.aiyu.rewire.core.notifications.FocusDndManager
import com.aiyu.rewire.core.settings.Settings
import com.aiyu.rewire.core.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.aiyu.rewire.core.settings.FocusBypass
import com.aiyu.rewire.data.FocusPresetRepository
import com.aiyu.rewire.domain.focus.FocusConfig
import com.aiyu.rewire.domain.focus.FocusPreset
import com.aiyu.rewire.domain.focus.FocusPresetError
import com.aiyu.rewire.domain.focus.FocusSession
import com.aiyu.rewire.domain.focus.FocusState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** UI facade over the app-scoped [com.aiyu.rewire.core.focus.FocusController]; the timer itself survives this ViewModel. */
@HiltViewModel
class FocusViewModel @Inject constructor(
    private val focus: FocusController,
    private val settingsRepository: SettingsRepository,
    private val dnd: FocusDndManager,
    private val presets: FocusPresetRepository,
    settings: StateFlow<Settings?>,
) : ViewModel() {

    val state: StateFlow<FocusState> = focus.current
    val now: StateFlow<Long> = focus.now
    val awaitingNote: StateFlow<FocusSession?> = focus.awaitingNote

    private val _draft = MutableStateFlow(FocusConfig(focusMinutes = 25, breakMinutes = 5, cycles = 4))
    val draft: StateFlow<FocusConfig> = _draft.asStateFlow()

    val bypass: StateFlow<FocusBypass?> = settings.map { it?.focusBypass }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val focusDndEnabled: StateFlow<Boolean?> = settings.map { it?.focusDndEnabled }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val isDndAccessGranted: Boolean get() = dnd.isAccessGranted
    fun dndSettingsIntent() = dnd.dndSettingsIntent()

    fun setDraft(config: FocusConfig) {
        // Keep break <= focus while the user drags focus down.
        _draft.value = config.copy(breakMinutes = config.breakMinutes.coerceAtMost(config.focusMinutes))
    }

    val userPresets: StateFlow<List<FocusPreset>> = presets.userPresets.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Each returns the rule violation (shown in the dialog) or null once saved. */
    suspend fun savePreset(name: String): FocusPresetError? = presets.add(name, _draft.value)
    suspend fun renamePreset(id: String, name: String): FocusPresetError? = presets.rename(id, name)
    fun deletePreset(id: String) = viewModelScope.launch { presets.delete(id) }

    fun setBypass(b: FocusBypass) = viewModelScope.launch { settingsRepository.setFocusBypass(b) }

    fun setFocusDndEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setFocusDndEnabled(enabled)
        focus.onDndSettingChanged(enabled)
    }

    fun start() = focus.start(_draft.value)

    /** Debug builds only: 20 s / 10 s session to exercise phase changes, chime and notifications fast. */
    fun startQuickTest() = focus.start(FocusState.QUICK_TEST)

    fun pause() = focus.pause()
    fun resume() = focus.resume()
    fun skipBreak() = focus.skipBreak()
    fun end() = focus.end()
    fun reset() = focus.reset(_draft.value)

    fun saveNote(note: String) = focus.saveNote(note)
    fun skipNote() = focus.skipNote()
}
