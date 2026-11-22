package com.omnitracker.renpho.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnitracker.renpho.core.presentation.UnitFormatter
import com.omnitracker.renpho.domain.MeasurementDataSource
import com.omnitracker.renpho.domain.MeasurementFactory
import com.omnitracker.renpho.domain.UserPreferences
import com.omnitracker.renpho.domain.model.HeightUnit
import com.omnitracker.renpho.domain.model.Measurement
import com.omnitracker.renpho.domain.model.Sex
import com.omnitracker.renpho.domain.model.WeightUnit
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ProfileSummaryUi(
    val name: String,
    val sex: Sex,
    val age: Int,
    val height: String,
)

data class SettingsState(
    val profile: ProfileSummaryUi? = null,
    val weightUnit: WeightUnit = WeightUnit.KG,
    val heightUnit: HeightUnit = HeightUnit.CM,
    val goalText: String? = null,
    val pairedScaleName: String? = null,
    val measurementCount: Int = 0,
    val goalDialog: GoalDialogUi? = null,
    val showDeleteAllDialog: Boolean = false,
)

data class GoalDialogUi(val value: String, val isError: Boolean = false)

sealed interface SettingsAction {
    data object OnEditProfileClick : SettingsAction
    data class OnWeightUnitSelect(val unit: WeightUnit) : SettingsAction
    data class OnHeightUnitSelect(val unit: HeightUnit) : SettingsAction
    data object OnGoalClick : SettingsAction
    data class OnGoalValueChange(val value: String) : SettingsAction
    data object OnGoalSave : SettingsAction
    data object OnGoalRemove : SettingsAction
    data object OnGoalDismiss : SettingsAction
    data object OnForgetScaleClick : SettingsAction
    data object OnExportClick : SettingsAction
    data object OnDeleteAllClick : SettingsAction
    data object OnDeleteAllConfirm : SettingsAction
    data object OnDeleteAllDismiss : SettingsAction
}

sealed interface SettingsEvent {
    data object NavigateToEditProfile : SettingsEvent
    data class ShareCsv(val csv: String) : SettingsEvent
}

class SettingsViewModel(
    private val preferences: UserPreferences,
    private val measurements: MeasurementDataSource,
) : ViewModel() {

    private val dialogs = MutableStateFlow(SettingsState())

    private val _events = Channel<SettingsEvent>()
    val events = _events.receiveAsFlow()

    val state = combine(
        preferences.profile,
        preferences.settings,
        measurements.observeAll(),
        dialogs,
    ) { profile, settings, list, d ->
        SettingsState(
            profile = profile?.let {
                ProfileSummaryUi(
                    name = it.name,
                    sex = it.sex,
                    age = MeasurementFactory.ageAt(it, Instant.now()),
                    height = UnitFormatter.height(it.heightCm, settings.heightUnit),
                )
            },
            weightUnit = settings.weightUnit,
            heightUnit = settings.heightUnit,
            goalText = profile?.goalWeightKg?.let { UnitFormatter.weight(it, settings.weightUnit) },
            pairedScaleName = settings.pairedScale?.name,
            measurementCount = list.size,
            goalDialog = d.goalDialog,
            showDeleteAllDialog = d.showDeleteAllDialog,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())

    fun onAction(action: SettingsAction) {
        when (action) {
            SettingsAction.OnEditProfileClick -> send(SettingsEvent.NavigateToEditProfile)
            is SettingsAction.OnWeightUnitSelect -> viewModelScope.launch { preferences.setWeightUnit(action.unit) }
            is SettingsAction.OnHeightUnitSelect -> viewModelScope.launch { preferences.setHeightUnit(action.unit) }
            SettingsAction.OnGoalClick -> viewModelScope.launch {
                val unit = preferences.settings.first().weightUnit
                val goal = preferences.profile.first()?.goalWeightKg
                val text = goal?.let { UnitFormatter.oneDecimal(UnitFormatter.toDisplay(it, unit)) }.orEmpty()
                dialogs.update { it.copy(goalDialog = GoalDialogUi(text)) }
            }
            is SettingsAction.OnGoalValueChange -> dialogs.update {
                it.copy(goalDialog = GoalDialogUi(action.value.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(6)))
            }
            SettingsAction.OnGoalSave -> viewModelScope.launch {
                val dialog = dialogs.value.goalDialog ?: return@launch
                val unit = preferences.settings.first().weightUnit
                val kg = dialog.value.replace(',', '.').toFloatOrNull()?.let { UnitFormatter.fromDisplay(it, unit) }
                if (kg == null || kg !in 2f..300f) {
                    dialogs.update { it.copy(goalDialog = dialog.copy(isError = true)) }
                } else {
                    preferences.setGoalWeight(kg)
                    dialogs.update { it.copy(goalDialog = null) }
                }
            }
            SettingsAction.OnGoalRemove -> viewModelScope.launch {
                preferences.setGoalWeight(null)
                dialogs.update { it.copy(goalDialog = null) }
            }
            SettingsAction.OnGoalDismiss -> dialogs.update { it.copy(goalDialog = null) }
            SettingsAction.OnForgetScaleClick -> viewModelScope.launch { preferences.setPairedScale(null) }
            SettingsAction.OnExportClick -> viewModelScope.launch {
                send(SettingsEvent.ShareCsv(toCsv(measurements.observeAll().first())))
            }
            SettingsAction.OnDeleteAllClick -> dialogs.update { it.copy(showDeleteAllDialog = true) }
            SettingsAction.OnDeleteAllDismiss -> dialogs.update { it.copy(showDeleteAllDialog = false) }
            SettingsAction.OnDeleteAllConfirm -> viewModelScope.launch {
                measurements.deleteAll()
                dialogs.update { it.copy(showDeleteAllDialog = false) }
            }
        }
    }

    private fun send(event: SettingsEvent) {
        viewModelScope.launch { _events.send(event) }
    }

    private fun toCsv(list: List<Measurement>): String = buildString {
        appendLine(
            "timestamp,source,weight_kg,impedance_ohm,bmi,body_fat_pct,visceral_fat_rating,subcutaneous_fat_pct," +
                "fat_free_mass_kg,water_pct," +
                "muscle_mass_kg,skeletal_muscle_pct,bone_mass_kg,protein_pct,bmr_kcal,metabolic_age",
        )
        list.asReversed().forEach { m ->
            val c = m.composition
            listOf(
                DateTimeFormatter.ISO_INSTANT.format(m.timestamp),
                m.source.name.lowercase(Locale.ROOT),
                m.weightKg.csv(),
                m.impedanceOhm?.toString().orEmpty(),
                c.bmi.csv(),
                c.bodyFatPercent.csv(),
                c.visceralFatRating?.toString().orEmpty(),
                c.subcutaneousFatPercent.csv(),
                c.fatFreeMassKg.csv(),
                c.waterPercent.csv(),
                c.muscleMassKg.csv(),
                c.skeletalMusclePercent.csv(),
                c.boneMassKg.csv(),
                c.proteinPercent.csv(),
                c.bmrKcal?.toString().orEmpty(),
                c.metabolicAge?.toString().orEmpty(),
            ).joinTo(this, ",")
            appendLine()
        }
    }

    private fun Float?.csv(): String = this?.let { String.format(Locale.ROOT, "%.2f", it) }.orEmpty()
}
