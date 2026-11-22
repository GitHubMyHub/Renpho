package com.omnitracker.renp.feature.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.omnitracker.renp.R
import com.omnitracker.renp.core.presentation.Metric
import com.omnitracker.renp.core.presentation.UnitFormatter
import com.omnitracker.renp.domain.MeasurementDataSource
import com.omnitracker.renp.domain.UserPreferences
import com.omnitracker.renp.domain.model.Measurement
import com.omnitracker.renp.domain.model.MeasurementSource
import com.omnitracker.renp.domain.model.UserProfile
import com.omnitracker.renp.domain.model.WeightUnit
import com.omnitracker.renp.navigation.HistoryRoute
import com.omnitracker.renp.ui.components.ChartPoint
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.util.Locale

enum class TimeRange(val label: Int, val duration: Duration?) {
    WEEK(R.string.range_week, Duration.ofDays(7)),
    MONTH(R.string.range_month, Duration.ofDays(30)),
    THREE_MONTHS(R.string.range_3months, Duration.ofDays(91)),
    YEAR(R.string.range_year, Duration.ofDays(365)),
    ALL(R.string.range_all, null),
}

data class HistoryItemUi(
    val id: Long,
    val date: String,
    val value: String,
    val weight: String,
    val isManual: Boolean,
)

data class HistoryStatsUi(
    val min: String,
    val max: String,
    val average: String,
    val change: String,
)

data class HistoryState(
    val isLoading: Boolean = true,
    val metric: Metric = Metric.WEIGHT,
    val range: TimeRange = TimeRange.MONTH,
    val unitSuffix: String? = null,
    val points: List<ChartPoint> = emptyList(),
    val goal: Float? = null,
    val startLabel: String = "",
    val endLabel: String = "",
    val stats: HistoryStatsUi? = null,
    val items: List<HistoryItemUi> = emptyList(),
)

sealed interface HistoryAction {
    data class OnMetricSelect(val metric: Metric) : HistoryAction
    data class OnRangeSelect(val range: TimeRange) : HistoryAction
    data class OnItemClick(val id: Long) : HistoryAction
    data object OnAddClick : HistoryAction
}

sealed interface HistoryEvent {
    data class NavigateToDetail(val id: Long) : HistoryEvent
    data object NavigateToAdd : HistoryEvent
}

class HistoryViewModel(
    savedStateHandle: SavedStateHandle,
    measurements: MeasurementDataSource,
    preferences: UserPreferences,
) : ViewModel() {

    private val initialMetric = savedStateHandle.toRoute<HistoryRoute>().metric
        ?.let { runCatching { Metric.valueOf(it) }.getOrNull() } ?: Metric.WEIGHT

    private val selection = MutableStateFlow(initialMetric to TimeRange.MONTH)

    private val _events = Channel<HistoryEvent>()
    val events = _events.receiveAsFlow()

    val state = combine(
        measurements.observeAll(),
        preferences.profile,
        preferences.settings,
        selection,
    ) { list, profile, settings, (metric, range) ->
        buildState(list, profile, settings.weightUnit, metric, range)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryState(metric = initialMetric))

    fun onAction(action: HistoryAction) {
        when (action) {
            is HistoryAction.OnMetricSelect -> selection.update { it.copy(first = action.metric) }
            is HistoryAction.OnRangeSelect -> selection.update { it.copy(second = action.range) }
            is HistoryAction.OnItemClick -> viewModelScope.launch { _events.send(HistoryEvent.NavigateToDetail(action.id)) }
            HistoryAction.OnAddClick -> viewModelScope.launch { _events.send(HistoryEvent.NavigateToAdd) }
        }
    }

    private fun buildState(
        all: List<Measurement>,
        profile: UserProfile?,
        unit: WeightUnit,
        metric: Metric,
        range: TimeRange,
    ): HistoryState {
        val cutoff = range.duration?.let { Instant.now().minus(it) }
        val inRange = all.filter { cutoff == null || it.timestamp >= cutoff } // newest first
        val withValue = inRange.mapNotNull { m -> metric.displayValue(m, unit)?.let { m to it } }
        val chronological = withValue.asReversed()
        val values = withValue.map { it.second }
        val suffix = metric.displayUnit(unit)

        fun fmt(v: Float): String {
            val text = if (metric == Metric.BMR || metric == Metric.METABOLIC_AGE || metric == Metric.VISCERAL_FAT) {
                v.toInt().toString()
            } else {
                UnitFormatter.oneDecimal(v)
            }
            return if (suffix != null) "$text $suffix" else text
        }

        val stats = if (values.isNotEmpty()) {
            val change = chronological.last().second - chronological.first().second
            val sign = if (change > 0) "+" else if (change < 0) "−" else "±"
            HistoryStatsUi(
                min = fmt(values.min()),
                max = fmt(values.max()),
                average = fmt(values.average().toFloat()),
                change = sign + fmt(kotlin.math.abs(change)),
            )
        } else null

        return HistoryState(
            isLoading = false,
            metric = metric,
            range = range,
            unitSuffix = suffix,
            points = chronological.map { (m, v) -> ChartPoint(m.timestamp.toEpochMilli(), v) },
            goal = if (metric == Metric.WEIGHT) profile?.goalWeightKg?.let { UnitFormatter.toDisplay(it, unit) } else null,
            startLabel = chronological.firstOrNull()?.let { UnitFormatter.shortDate(it.first.timestamp) }.orEmpty(),
            endLabel = chronological.lastOrNull()?.let { UnitFormatter.shortDate(it.first.timestamp) }.orEmpty(),
            stats = stats,
            items = inRange.map { m ->
                HistoryItemUi(
                    id = m.id,
                    date = UnitFormatter.dateTime(m.timestamp),
                    value = metric.format(m, unit) ?: "—",
                    weight = UnitFormatter.weight(m.weightKg, unit),
                    isManual = m.source == MeasurementSource.MANUAL,
                )
            },
        )
    }

    companion object {
        fun formatAxis(value: Float): String = String.format(Locale.getDefault(), "%.1f", value)
    }
}
