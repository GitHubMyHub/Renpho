package com.omnitracker.renpho.feature.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.omnitracker.renpho.R
import com.omnitracker.renpho.core.presentation.UnitFormatter
import com.omnitracker.renpho.domain.UserPreferences
import com.omnitracker.renpho.domain.model.HeightUnit
import com.omnitracker.renpho.domain.model.Sex
import com.omnitracker.renpho.domain.model.UserProfile
import com.omnitracker.renpho.domain.model.WeightUnit
import com.omnitracker.renpho.navigation.ProfileRoute
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

data class ProfileState(
    val isOnboarding: Boolean = true,
    val name: String = "",
    val sex: Sex = Sex.MALE,
    val birthDate: LocalDate? = null,
    val heightUnit: HeightUnit = HeightUnit.CM,
    val weightUnit: WeightUnit = WeightUnit.KG,
    val heightCm: String = "",
    val heightFeet: String = "",
    val heightInches: String = "",
    val goalWeight: String = "",
    val heightError: Int? = null,
    val birthDateError: Int? = null,
    val goalError: Int? = null,
    val isSaving: Boolean = false,
)

sealed interface ProfileAction {
    data class OnNameChange(val value: String) : ProfileAction
    data class OnSexChange(val sex: Sex) : ProfileAction
    data class OnBirthDateChange(val date: LocalDate) : ProfileAction
    data class OnHeightUnitChange(val unit: HeightUnit) : ProfileAction
    data class OnHeightCmChange(val value: String) : ProfileAction
    data class OnHeightFeetChange(val value: String) : ProfileAction
    data class OnHeightInchesChange(val value: String) : ProfileAction
    data class OnGoalWeightChange(val value: String) : ProfileAction
    data object OnSaveClick : ProfileAction
    data object OnBackClick : ProfileAction
}

sealed interface ProfileEvent {
    data class Finished(val isOnboarding: Boolean) : ProfileEvent
    data object NavigateBack : ProfileEvent
}

class ProfileViewModel(
    savedStateHandle: SavedStateHandle,
    private val preferences: UserPreferences,
) : ViewModel() {

    private val isOnboarding = savedStateHandle.toRoute<ProfileRoute>().isOnboarding

    private val _state = MutableStateFlow(ProfileState(isOnboarding = isOnboarding))
    val state = _state.asStateFlow()

    private val _events = Channel<ProfileEvent>()
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            val settings = preferences.settings.first()
            val profile = preferences.profile.first()
            _state.update { s ->
                val (ft, inch) = profile?.let { UnitFormatter.cmToFeetInches(it.heightCm) } ?: (null to null)
                s.copy(
                    heightUnit = settings.heightUnit,
                    weightUnit = settings.weightUnit,
                    name = profile?.name ?: s.name,
                    sex = profile?.sex ?: s.sex,
                    birthDate = profile?.birthDate ?: s.birthDate,
                    heightCm = profile?.heightCm?.roundToInt()?.toString() ?: s.heightCm,
                    heightFeet = ft?.toString() ?: s.heightFeet,
                    heightInches = inch?.toString() ?: s.heightInches,
                    goalWeight = profile?.goalWeightKg
                        ?.let { UnitFormatter.oneDecimal(UnitFormatter.toDisplay(it, settings.weightUnit)) }
                        ?: s.goalWeight,
                )
            }
        }
    }

    fun onAction(action: ProfileAction) {
        when (action) {
            is ProfileAction.OnNameChange -> _state.update { it.copy(name = action.value.take(40)) }
            is ProfileAction.OnSexChange -> _state.update { it.copy(sex = action.sex) }
            is ProfileAction.OnBirthDateChange -> _state.update { it.copy(birthDate = action.date, birthDateError = null) }
            is ProfileAction.OnHeightUnitChange -> _state.update { s ->
                // Convert the current input so switching units keeps the value
                val cm = s.currentHeightCm()
                if (cm == null) s.copy(heightUnit = action.unit) else {
                    val (ft, inch) = UnitFormatter.cmToFeetInches(cm)
                    s.copy(
                        heightUnit = action.unit,
                        heightCm = cm.roundToInt().toString(),
                        heightFeet = ft.toString(),
                        heightInches = inch.toString(),
                    )
                }
            }
            is ProfileAction.OnHeightCmChange -> _state.update { it.copy(heightCm = action.value.digitsOnly(3), heightError = null) }
            is ProfileAction.OnHeightFeetChange -> _state.update { it.copy(heightFeet = action.value.digitsOnly(1), heightError = null) }
            is ProfileAction.OnHeightInchesChange -> _state.update { it.copy(heightInches = action.value.digitsOnly(2), heightError = null) }
            is ProfileAction.OnGoalWeightChange -> _state.update { it.copy(goalWeight = action.value.decimalOnly(), goalError = null) }
            ProfileAction.OnSaveClick -> save()
            ProfileAction.OnBackClick -> viewModelScope.launch { _events.send(ProfileEvent.NavigateBack) }
        }
    }

    private fun save() {
        val s = _state.value
        val heightCm = s.currentHeightCm()?.takeIf { it in 90f..250f }
        val birthDate = s.birthDate?.takeIf { it.isBefore(LocalDate.now().minusYears(3)) && it.year > 1900 }
        val goalText = s.goalWeight.replace(',', '.')
        val goalKg = goalText.takeIf { it.isNotBlank() }?.toFloatOrNull()
            ?.let { UnitFormatter.fromDisplay(it, s.weightUnit) }
        val goalInvalid = goalText.isNotBlank() && (goalKg == null || goalKg !in 2f..300f)

        _state.update {
            it.copy(
                heightError = if (heightCm == null) R.string.error_height_invalid else null,
                birthDateError = if (birthDate == null) R.string.error_birth_date else null,
                goalError = if (goalInvalid) R.string.error_goal_invalid else null,
            )
        }
        if (heightCm == null || birthDate == null || goalInvalid) return

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            preferences.setHeightUnit(s.heightUnit)
            preferences.saveProfile(
                UserProfile(
                    name = s.name.trim(),
                    sex = s.sex,
                    birthDate = birthDate,
                    heightCm = heightCm,
                    goalWeightKg = goalKg,
                ),
            )
            _state.update { it.copy(isSaving = false) }
            _events.send(ProfileEvent.Finished(isOnboarding))
        }
    }

    private fun ProfileState.currentHeightCm(): Float? = when (heightUnit) {
        HeightUnit.CM -> heightCm.toFloatOrNull()
        HeightUnit.FT_IN -> {
            val ft = heightFeet.toIntOrNull()
            val inch = heightInches.toIntOrNull() ?: 0
            ft?.let { UnitFormatter.feetInchesToCm(it, inch) }
        }
    }

    private fun String.digitsOnly(max: Int) = filter(Char::isDigit).take(max)
    private fun String.decimalOnly() = filter { it.isDigit() || it == '.' || it == ',' }.take(6)
}
