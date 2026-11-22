package com.omnitracker.renpho.feature.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omnitracker.renpho.R
import com.omnitracker.renpho.core.presentation.MetricStatus
import com.omnitracker.renpho.core.presentation.ObserveAsEvents
import com.omnitracker.renpho.ui.components.MetricRow
import com.omnitracker.renpho.ui.components.MetricValueUi
import com.omnitracker.renpho.ui.theme.RenphoTheme
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MeasurementDetailRoot(
    onBack: () -> Unit,
    viewModel: MeasurementDetailViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            MeasurementDetailEvent.NavigateBack -> onBack()
        }
    }
    MeasurementDetailScreen(state = state, onAction = viewModel::onAction)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasurementDetailScreen(
    state: MeasurementDetailState,
    onAction: (MeasurementDetailAction) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.detail_title)) },
                navigationIcon = {
                    IconButton(onClick = { onAction(MeasurementDetailAction.OnBackClick) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { onAction(MeasurementDetailAction.OnDeleteClick) }, enabled = !state.isLoading) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(state.dateTime, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(state.weight, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.detail_source) + ": " +
                    stringResource(if (state.isManual) R.string.source_manual else R.string.source_scale) +
                    (state.impedanceOhm?.let { " · " + stringResource(R.string.detail_impedance_value, it) } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!state.hasComposition) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.dashboard_no_impedance),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(16.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    state.metrics.forEachIndexed { i, metric ->
                        if (i > 0) HorizontalDivider()
                        MetricRow(metric)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.settings_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (state.showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { onAction(MeasurementDetailAction.OnDeleteDismiss) },
            title = { Text(stringResource(R.string.detail_delete_title)) },
            text = { Text(stringResource(R.string.detail_delete_body)) },
            confirmButton = {
                TextButton(onClick = { onAction(MeasurementDetailAction.OnDeleteConfirm) }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { onAction(MeasurementDetailAction.OnDeleteDismiss) }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Preview
@Composable
private fun MeasurementDetailPreview() {
    RenphoTheme {
        MeasurementDetailScreen(
            state = MeasurementDetailState(
                isLoading = false,
                dateTime = "22 Nov 2026, 07:31",
                weight = "78.2 kg",
                impedanceOhm = 512,
                hasComposition = true,
                metrics = listOf(
                    MetricValueUi(R.string.metric_bmi, "24.1", MetricStatus.NORMAL),
                    MetricValueUi(R.string.metric_body_fat, "21.4 %", MetricStatus.HIGH),
                    MetricValueUi(R.string.metric_bmr, "1712 kcal", null),
                ),
            ),
            onAction = {},
        )
    }
}
