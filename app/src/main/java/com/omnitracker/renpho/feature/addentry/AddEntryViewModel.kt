package com.omnitracker.renpho.feature.addentry

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnitracker.renpho.R
import com.omnitracker.renpho.core.presentation.UnitFormatter
import com.omnitracker.renpho.domain.MeasurementDataSource
import com.omnitracker.renpho.domain.MeasurementFactory
import com.omnitracker.renpho.domain.UserPreferences
import com.omnitracker.renpho.domain.model.MeasurementSource
import com.omnitracker.renpho.domain.model.WeightUnit
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

data class AddEntryState(
    val weight: String = "",
    val unit: WeightUnit = WeightUnit.KG,
    val date: LocalDate = LocalDate.now(),
    val time: LocalTime = LocalTime.now().withSecond(0).withNano(0),
    val weightError: Int? = null,
    val isSaving: Boolean = false,
)

sealed interface AddEntryAction {
    data class OnWeightChange(val value: String) : AddEntryAction
    data class OnDateChange(val date: LocalDate) : AddEntryAction
    data class OnTimeChange(val time: LocalTime) : AddEntryAction
    data object OnSaveClick : AddEntryAction
    data object OnBackClick : AddEntryAction
}

sealed interface AddEntryEvent {
    data object NavigateBack : AddEntryEvent
}

class AddEntryViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val measurements: MeasurementDataSource,
    private val preferences: UserPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(AddEntryState(weight = savedStateHandle[KEY_WEIGHT] ?: ""))
    val state = _state.asStateFlow()

    private val _events = Channel<AddEntryEvent>()
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            val unit = preferences.settings.first().weightUnit
            _state.update { it.copy(unit = unit) }
        }
    }

    fun onAction(action: AddEntryAction) {
        when (action) {
            is AddEntryAction.OnWeightChange -> {
                val value = action.value.filter { it.isDigit() || it == '.' || it == ',' }.take(6)
                savedStateHandle[KEY_WEIGHT] = value
                _state.update { it.copy(weight = value, weightError = null) }
            }
            is AddEntryAction.OnDateChange -> _state.update { it.copy(date = action.date) }
            is AddEntryAction.OnTimeChange -> _state.update { it.copy(time = action.time) }
            AddEntryAction.OnSaveClick -> save()
            AddEntryAction.OnBackClick -> viewModelScope.launch { _events.send(AddEntryEvent.NavigateBack) }
        }
    }

    private fun save() {
        val s = _state.value
        val kg = s.weight.replace(',', '.').toFloatOrNull()?.let { UnitFormatter.fromDisplay(it, s.unit) }
        if (kg == null || kg !in 2f..300f) {
            _state.update { it.copy(weightError = R.string.error_weight_invalid) }
            return
        }
        viewModelScope.launch {
            val profile = preferences.profile.first() ?: return@launch
            _state.update { it.copy(isSaving = true) }
            val timestamp = LocalDateTime.of(s.date, s.time).atZone(ZoneId.systemDefault()).toInstant()
            measurements.insert(
                MeasurementFactory.create(
                    profile = profile,
                    weightKg = kg,
                    impedanceOhm = null,
                    source = MeasurementSource.MANUAL,
                    timestamp = timestamp,
                ),
            )
            _events.send(AddEntryEvent.NavigateBack)
        }
    }

    private companion object {
        const val KEY_WEIGHT = "weight"
    }
}
