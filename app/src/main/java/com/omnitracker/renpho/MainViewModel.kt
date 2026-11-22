package com.omnitracker.renpho

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnitracker.renpho.data.local.MeasurementRecalculator
import com.omnitracker.renpho.domain.UserPreferences
import com.omnitracker.renpho.navigation.DashboardRoute
import com.omnitracker.renpho.navigation.ProfileRoute
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
