package com.boulderbuddy.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boulderbuddy.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Liefert den app-weiten Dark-Mode-Override an [MainActivity] (7.4a). `null` = dem System
 * folgen; die Activity löst das gegen `isSystemInDarkTheme()` auf. Bewusst schlank, damit
 * das Theme schon vor dem eigentlichen Screen-Graphen steht.
 */
@HiltViewModel
class ThemeViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val darkModeOverride: StateFlow<Boolean?> = settingsRepository.darkMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null,
        )
}
