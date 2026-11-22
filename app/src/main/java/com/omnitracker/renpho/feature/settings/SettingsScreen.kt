package com.omnitracker.renpho.feature.settings

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omnitracker.renpho.R
import com.omnitracker.renpho.core.presentation.ObserveAsEvents
import com.omnitracker.renpho.domain.model.HeightUnit
import com.omnitracker.renpho.domain.model.Sex
import com.omnitracker.renpho.domain.model.WeightUnit
import com.omnitracker.renpho.ui.theme.RenphoTheme
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SettingsRoot(
    onEditProfile: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val exportTitle = stringResource(R.string.settings_export_chooser)
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            SettingsEvent.NavigateToEditProfile -> onEditProfile()
            is SettingsEvent.ShareCsv -> {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_SUBJECT, "measurements.csv")
                    putExtra(Intent.EXTRA_TEXT, event.csv)
                }
                context.startActivity(Intent.createChooser(send, exportTitle))
            }
        }
    }
    SettingsScreen(state = state, appVersion = version, onAction = viewModel::onAction)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsState,
    appVersion: String,
    onAction: (SettingsAction) -> Unit,
) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle(stringResource(R.string.settings_profile))
            Card(Modifier.fillMaxWidth()) {
                val p = state.profile
                SettingsRow(
                    icon = Icons.Filled.Person,
                    title = p?.name?.ifBlank { null } ?: stringResource(R.string.settings_profile),
                    subtitle = p?.let {
                        listOf(
                            stringResource(if (it.sex == Sex.MALE) R.string.sex_male else R.string.sex_female),
                            stringResource(R.string.profile_age, it.age),
                            it.height,
                        ).joinToString(" · ")
                    },
                    onClick = { onAction(SettingsAction.OnEditProfileClick) },
                    showChevron = true,
                )
                HorizontalDivider()
                SettingsRow(
                    icon = Icons.Filled.Flag,
                    title = stringResource(R.string.settings_goal),
                    subtitle = state.goalText ?: stringResource(R.string.settings_goal_none),
                    onClick = { onAction(SettingsAction.OnGoalClick) },
                    showChevron = true,
                )
            }

            SectionTitle(stringResource(R.string.settings_units))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.settings_weight_unit), style = MaterialTheme.typography.labelLarge)
                    val weightOptions = listOf(
                        WeightUnit.KG to R.string.unit_kg,
                        WeightUnit.LB to R.string.unit_lb,
                        WeightUnit.ST to R.string.unit_st,
                    )
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        weightOptions.forEachIndexed { i, (unit, label) ->
                            SegmentedButton(
                                selected = state.weightUnit == unit,
                                onClick = { onAction(SettingsAction.OnWeightUnitSelect(unit)) },
                                shape = SegmentedButtonDefaults.itemShape(i, weightOptions.size),
                            ) { Text(stringResource(label)) }
                        }
                    }
                    Text(stringResource(R.string.settings_height_unit), style = MaterialTheme.typography.labelLarge)
                    val heightOptions = listOf(HeightUnit.CM to R.string.unit_cm, HeightUnit.FT_IN to R.string.unit_ft_in)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        heightOptions.forEachIndexed { i, (unit, label) ->
                            SegmentedButton(
                                selected = state.heightUnit == unit,
                                onClick = { onAction(SettingsAction.OnHeightUnitSelect(unit)) },
                                shape = SegmentedButtonDefaults.itemShape(i, heightOptions.size),
                            ) { Text(stringResource(label)) }
                        }
                    }
                }
            }

            SectionTitle(stringResource(R.string.settings_scale))
            Card(Modifier.fillMaxWidth()) {
                if (state.pairedScaleName != null) {
                    Row(Modifier.padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.MonitorWeight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.size(16.dp))
                        Text(state.pairedScaleName, modifier = Modifier.weight(1f).padding(vertical = 16.dp))
                        TextButton(onClick = { onAction(SettingsAction.OnForgetScaleClick) }) {
                            Text(stringResource(R.string.settings_forget_scale))
                        }
                    }
                } else {
                    Text(
                        stringResource(R.string.settings_scale_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            SectionTitle(stringResource(R.string.settings_data))
            Card(Modifier.fillMaxWidth()) {
                SettingsRow(
                    icon = Icons.Filled.IosShare,
                    title = stringResource(R.string.settings_export),
                    subtitle = null,
                    enabled = state.measurementCount > 0,
                    onClick = { onAction(SettingsAction.OnExportClick) },
                )
                HorizontalDivider()
                SettingsRow(
                    icon = Icons.Filled.DeleteForever,
                    title = stringResource(R.string.settings_delete_all),
                    subtitle = null,
                    enabled = state.measurementCount > 0,
                    onClick = { onAction(SettingsAction.OnDeleteAllClick) },
                )
            }

            SectionTitle(stringResource(R.string.settings_about))
            Text(
                stringResource(R.string.settings_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (appVersion.isNotEmpty()) {
                Text(
                    stringResource(R.string.settings_version, appVersion),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    state.goalDialog?.let { dialog ->
        AlertDialog(
            onDismissRequest = { onAction(SettingsAction.OnGoalDismiss) },
            title = { Text(stringResource(R.string.settings_goal)) },
            text = {
                OutlinedTextField(
                    value = dialog.value,
                    onValueChange = { onAction(SettingsAction.OnGoalValueChange(it)) },
                    label = { Text(stringResource(R.string.add_weight, com.omnitracker.renpho.core.presentation.UnitFormatter.unitLabel(state.weightUnit))) },
                    isError = dialog.isError,
                    supportingText = if (dialog.isError) {
                        { Text(stringResource(R.string.error_goal_invalid)) }
                    } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = { onAction(SettingsAction.OnGoalSave) }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                Row {
                    if (state.goalText != null) {
                        TextButton(onClick = { onAction(SettingsAction.OnGoalRemove) }) {
                            Text(stringResource(R.string.settings_goal_remove))
                        }
                    }
                    TextButton(onClick = { onAction(SettingsAction.OnGoalDismiss) }) { Text(stringResource(R.string.cancel)) }
                }
            },
        )
    }

    if (state.showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { onAction(SettingsAction.OnDeleteAllDismiss) },
            title = { Text(stringResource(R.string.settings_delete_all)) },
            text = { Text(stringResource(R.string.settings_delete_all_body)) },
            confirmButton = {
                TextButton(onClick = { onAction(SettingsAction.OnDeleteAllConfirm) }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { onAction(SettingsAction.OnDeleteAllDismiss) }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    enabled: Boolean = true,
    showChevron: Boolean = false,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        Icon(icon, contentDescription = null, tint = tint)
        Spacer(Modifier.size(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
            )
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (showChevron) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}

@Preview
@Composable
private fun SettingsScreenPreview() {
    RenphoTheme {
        SettingsScreen(
            state = SettingsState(
                profile = ProfileSummaryUi("Alex", Sex.MALE, 34, "178 cm"),
                goalText = "75.0 kg",
                pairedScaleName = "QN-Scale",
                measurementCount = 3,
            ),
            appVersion = "1.0",
            onAction = {},
        )
    }
}
