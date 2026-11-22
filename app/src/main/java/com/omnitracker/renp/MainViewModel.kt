package com.omnitracker.renp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnitracker.renp.data.local.MeasurementRecalculator
import com.omnitracker.renp.domain.UserPreferences
import com.omnitracker.renp.navigation.DashboardRoute
import com.omnitracker.renp.navigation.ProfileRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Decides the start destination once (onboarding vs. dashboard). */
class MainViewModel(
    preferences: UserPreferences,
    recalculator: MeasurementRecalculator,
) : ViewModel() {

    private val _startDestination = MutableStateFlow<Any?>(null)
    val startDestination = _startDestination.asStateFlow()

    init {
        viewModelScope.launch {
            val profile = preferences.profile.first()
            _startDestination.value = if (profile == null) ProfileRoute(isOnboarding = true) else DashboardRoute
            recalculator.runIfNeeded()
        }
    }
}
