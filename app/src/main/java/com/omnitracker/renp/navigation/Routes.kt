package com.omnitracker.renp.navigation

import kotlinx.serialization.Serializable

@Serializable data class ProfileRoute(val isOnboarding: Boolean)

@Serializable data object DashboardRoute

/** [metric] is a [com.omnitracker.renp.core.presentation.Metric] name to preselect. */
@Serializable data class HistoryRoute(val metric: String? = null)

@Serializable data object SettingsRoute

@Serializable data object MeasureRoute

@Serializable data object AddEntryRoute

@Serializable data class MeasurementDetailRoute(val id: Long)
