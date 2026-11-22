package com.omnitracker.renp.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omnitracker.renp.R
import com.omnitracker.renp.core.presentation.ObserveAsEvents
import com.omnitracker.renp.core.presentation.UnitFormatter
import com.omnitracker.renp.domain.model.HeightUnit
import com.omnitracker.renp.domain.model.Sex
import com.omnitracker.renp.ui.theme.RenpTheme
import org.koin.compose.viewmodel.koinViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun ProfileRoot(
    onFinished: (isOnboarding: Boolean) -> Unit,
    onBack: () -> Unit,
    viewModel: ProfileViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is ProfileEvent.Finished -> onFinished(event.isOnboarding)
            ProfileEvent.NavigateBack -> onBack()
        }
    }
    ProfileScreen(state = state, onAction = viewModel::onAction)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    state: ProfileState,
    onAction: (ProfileAction) -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (state.isOnboarding) R.string.profile_setup_title else R.string.profile_edit_title))
                },
                navigationIcon = {
                    if (!state.isOnboarding) {
                        IconButton(onClick = { onAction(ProfileAction.OnBackClick) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.profile_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = state.name,
                onValueChange = { onAction(ProfileAction.OnNameChange(it)) },
                label = { Text(stringResource(R.string.profile_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(stringResource(R.string.profile_sex), style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val options = listOf(Sex.MALE to R.string.sex_male, Sex.FEMALE to R.string.sex_female)
                options.forEachIndexed { index, (sex, label) ->
                    SegmentedButton(
                        selected = state.sex == sex,
                        onClick = { onAction(ProfileAction.OnSexChange(sex)) },
                        shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    ) { Text(stringResource(label)) }
                }
            }

            Text(stringResource(R.string.profile_birth_date), style = MaterialTheme.typography.labelLarge)
            OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.CalendarMonth, contentDescription = null)
                Spacer(Modifier.padding(4.dp))
                Text(
                    state.birthDate?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))
                        ?: stringResource(R.string.profile_select_date),
                )
            }
            state.birthDateError?.let {
                Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val options = listOf(HeightUnit.CM to R.string.unit_cm, HeightUnit.FT_IN to R.string.unit_ft_in)
                options.forEachIndexed { index, (unit, label) ->
                    SegmentedButton(
                        selected = state.heightUnit == unit,
                        onClick = { onAction(ProfileAction.OnHeightUnitChange(unit)) },
                        shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    ) { Text(stringResource(label)) }
                }
            }
            val heightError: @Composable (() -> Unit)? = state.heightError?.let { { Text(stringResource(it)) } }
            when (state.heightUnit) {
                HeightUnit.CM -> OutlinedTextField(
                    value = state.heightCm,
                    onValueChange = { onAction(ProfileAction.OnHeightCmChange(it)) },
                    label = { Text(stringResource(R.string.profile_height_cm)) },
                    isError = state.heightError != null,
                    supportingText = heightError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                HeightUnit.FT_IN -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = state.heightFeet,
                        onValueChange = { onAction(ProfileAction.OnHeightFeetChange(it)) },
                        label = { Text(stringResource(R.string.profile_height_ft)) },
                        isError = state.heightError != null,
                        supportingText = heightError,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = state.heightInches,
                        onValueChange = { onAction(ProfileAction.OnHeightInchesChange(it)) },
                        label = { Text(stringResource(R.string.profile_height_in)) },
                        isError = state.heightError != null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            OutlinedTextField(
                value = state.goalWeight,
                onValueChange = { onAction(ProfileAction.OnGoalWeightChange(it)) },
                label = { Text(stringResource(R.string.profile_goal_weight, UnitFormatter.unitLabel(state.weightUnit))) },
                isError = state.goalError != null,
                supportingText = state.goalError?.let { { Text(stringResource(it)) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { onAction(ProfileAction.OnSaveClick) },
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(stringResource(if (state.isOnboarding) R.string.profile_continue else R.string.save))
            }
        }
    }

    if (showDatePicker) {
        val initial = state.birthDate ?: LocalDate.now().minusYears(30)
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            yearRange = 1900..LocalDate.now().year,
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        onAction(ProfileAction.OnBirthDateChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()))
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel)) }
            },
        ) { DatePicker(state = pickerState) }
    }
}

@Preview
@Composable
private fun ProfileScreenPreview() {
    RenpTheme {
        ProfileScreen(state = ProfileState(name = "Alex", heightCm = "178"), onAction = {})
    }
}
