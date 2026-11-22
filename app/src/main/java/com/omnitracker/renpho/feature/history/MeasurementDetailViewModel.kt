package com.omnitracker.renpho.feature.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.omnitracker.renpho.core.presentation.Metric
import com.omnitracker.renpho.core.presentation.UnitFormatter
import com.omnitracker.renpho.core.presentation.toMetricValues
import com.omnitracker.renpho.domain.MeasurementDataSource
import com.omnitracker.renpho.domain.UserPreferences
import com.omnitracker.renpho.domain.model.MeasurementSource
import com.omnitracker.renpho.navigation.MeasurementDetailRoute
import com.omnitracker.renpho.ui.components.MetricValueUi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MeasurementDetailState(
    val isLoading: Boolean = true,
    val dateTime: String = "",
    val weight: String = "",
    val isManual: Boolean = false,
    val impedanceOhm: Int? = null,
    val hasComposition: Boolean = false,
    val metrics: List<MetricValueUi> = emptyList(),
    val showDeleteDialog: Boolean = false,
)

sealed interface MeasurementDetailAction {
    data object OnDeleteClick : MeasurementDetailAction
    data object OnDeleteConfirm : MeasurementDetailAction
    data object OnDeleteDismiss : MeasurementDetailAction
    data object OnBackClick : MeasurementDetailAction
}

sealed interface MeasurementDetailEvent {
    data object NavigateBack : MeasurementDetailEvent
}

class MeasurementDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val measurements: MeasurementDataSource,
    preferences: UserPreferences,
) : ViewModel() {

    private val id = savedStateHandle.toRoute<MeasurementDetailRoute>().id
    private val showDelete = MutableStateFlow(false)
    private var deleting = false

    private val _events = Channel<MeasurementDetailEvent>()
    val events = _events.receiveAsFlow()

    val state = combine(
        measurements.observe(id),
        preferences.profile,
        preferences.settings,
        showDelete,
    ) { m, profile, settings, dialog ->
        if (m == null) {
            if (!deleting) _events.send(MeasurementDetailEvent.NavigateBack)
            MeasurementDetailState(isLoading = true)
        } else {
            MeasurementDetailState(
                isLoading = false,
                dateTime = UnitFormatter.dateTime(m.timestamp),
                weight = UnitFormatter.weight(m.weightKg, settings.weightUnit),
                isManual = m.source == MeasurementSource.MANUAL,
                impedanceOhm = m.impedanceOhm,
                hasComposition = m.composition.hasBodyComposition,
                metrics = m.toMetricValues(profile, settings.weightUnit, Metric.entries.filter { it != Metric.WEIGHT }),
                showDeleteDialog = dialog,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MeasurementDetailState())

    fun onAction(action: MeasurementDetailAction) {
        when (action) {
            MeasurementDetailAction.OnDeleteClick -> showDelete.value = true
            MeasurementDetailAction.OnDeleteDismiss -> showDelete.value = false
            MeasurementDetailAction.OnDeleteConfirm -> viewModelScope.launch {
                deleting = true
                showDelete.value = false
                measurements.delete(id)
                _events.send(MeasurementDetailEvent.NavigateBack)
            }
            MeasurementDetailAction.OnBackClick -> viewModelScope.launch {
                _events.send(MeasurementDetailEvent.NavigateBack)
            }
        }
    }
}
