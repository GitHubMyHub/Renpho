package com.omnitracker.renpho.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnitracker.renpho.core.presentation.Metric
import com.omnitracker.renpho.core.presentation.Tone
import com.omnitracker.renpho.core.presentation.UnitFormatter
import com.omnitracker.renpho.core.presentation.toMetricValues
import com.omnitracker.renpho.domain.MeasurementDataSource
import com.omnitracker.renpho.domain.UserPreferences
import com.omnitracker.renpho.domain.model.Measurement
import com.omnitracker.renpho.domain.model.UserProfile
import com.omnitracker.renpho.domain.model.WeightUnit
import com.omnitracker.renpho.ui.components.MetricValueUi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.abs

data class GoalUi(
    val goalText: String,
    val remainingText: String,
    val progress: Float,
    val reached: Boolean,
)

data class LatestMeasurementUi(
    val id: Long,
    val weightText: String,
    val measuredAt: String,
    val deltaText: String?,
    val deltaTone: Tone?,
    val hasComposition: Boolean,
)

data class DashboardState(
    val isLoading: Boolean = true,
    val userName: String = "",
    val latest: LatestMeasurementUi? = null,
    val goal: GoalUi? = null,
    val metrics: List<Pair<Metric, MetricValueUi>> = emptyList(),
)

sealed interface DashboardAction {
    data object OnMeasureClick : DashboardAction
    data object OnAddManualClick : DashboardAction
    data class OnMetricClick(val metric: Metric) : DashboardAction
    data object OnLatestClick : DashboardAction
}

sealed interface DashboardEvent {
    data object NavigateToMeasure : DashboardEvent
    data object NavigateToAddManual : DashboardEvent
    data class NavigateToMetric(val metric: Metric) : DashboardEvent
    data class NavigateToMeasurement(val id: Long) : DashboardEvent
}

class DashboardViewModel(
    measurements: MeasurementDataSource,
    preferences: UserPreferences,
) : ViewModel() {

    private val _events = Channel<DashboardEvent>()
    val events = _events.receiveAsFlow()

    val state = combine(
        measurements.observeAll(),
        preferences.profile,
        preferences.settings,
    ) { list, profile, settings ->
        buildState(list, profile, settings.weightUnit)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardState())

    fun onAction(action: DashboardAction) {
        viewModelScope.launch {
            when (action) {
                DashboardAction.OnMeasureClick -> _events.send(DashboardEvent.NavigateToMeasure)
                DashboardAction.OnAddManualClick -> _events.send(DashboardEvent.NavigateToAddManual)
                is DashboardAction.OnMetricClick -> _events.send(DashboardEvent.NavigateToMetric(action.metric))
                DashboardAction.OnLatestClick ->
                    state.value.latest?.let { _events.send(DashboardEvent.NavigateToMeasurement(it.id)) }
            }
        }
    }

    private fun buildState(list: List<Measurement>, profile: UserProfile?, unit: WeightUnit): DashboardState {
        val latest = list.firstOrNull()
        val previous = list.getOrNull(1)
        val goalKg = profile?.goalWeightKg

        val latestUi = latest?.let { m ->
            val delta = previous?.let { m.weightKg - it.weightKg }
            LatestMeasurementUi(
                id = m.id,
                weightText = UnitFormatter.weight(m.weightKg, unit),
                measuredAt = UnitFormatter.dateTime(m.timestamp),
                deltaText = delta?.let { UnitFormatter.weightDelta(it, unit) },
                // Only colour the change when there is a goal to compare against
                deltaTone = if (previous != null && goalKg != null && delta != null && abs(delta) >= 0.05f) {
                    val movedTowardGoal = abs(goalKg - m.weightKg) < abs(goalKg - previous.weightKg)
                    if (movedTowardGoal) Tone.GOOD else Tone.WARN
                } else null,
                hasComposition = m.composition.hasBodyComposition,
            )
        }

        val goalUi = if (latest != null && goalKg != null) {
            val start = list.last().weightKg
            val current = latest.weightKg
            val reached = if (start >= goalKg) current <= goalKg else current >= goalKg
            val total = abs(start - goalKg)
            val progress = if (total < 0.01f) 1f else (1f - abs(current - goalKg) / total).coerceIn(0f, 1f)
            GoalUi(
                goalText = UnitFormatter.weight(goalKg, unit),
                remainingText = UnitFormatter.weight(abs(current - goalKg), unit),
                progress = if (reached) 1f else progress,
                reached = reached,
            )
        } else null

        val metrics = latest?.let { m ->
            val shown = Metric.entries.filter { it != Metric.WEIGHT }
            shown.zip(m.toMetricValues(profile, unit, shown))
        }.orEmpty()

        return DashboardState(
            isLoading = false,
            userName = profile?.name.orEmpty(),
            latest = latestUi,
            goal = goalUi,
            metrics = metrics,
        )
    }
}
