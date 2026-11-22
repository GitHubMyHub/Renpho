package com.omnitracker.renp.feature.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omnitracker.renp.R
import com.omnitracker.renp.core.presentation.Metric
import com.omnitracker.renp.core.presentation.MetricStatus
import com.omnitracker.renp.core.presentation.ObserveAsEvents
import com.omnitracker.renp.core.presentation.Tone
import com.omnitracker.renp.ui.components.MetricTile
import com.omnitracker.renp.ui.components.MetricValueUi
import com.omnitracker.renp.ui.theme.RenpTheme
import com.omnitracker.renp.ui.theme.color
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DashboardRoot(
    onMeasure: () -> Unit,
    onAddManual: () -> Unit,
    onMetricClick: (Metric) -> Unit,
    onMeasurementClick: (Long) -> Unit,
    viewModel: DashboardViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            DashboardEvent.NavigateToMeasure -> onMeasure()
            DashboardEvent.NavigateToAddManual -> onAddManual()
            is DashboardEvent.NavigateToMetric -> onMetricClick(event.metric)
            is DashboardEvent.NavigateToMeasurement -> onMeasurementClick(event.id)
        }
    }
    DashboardScreen(state = state, onAction = viewModel::onAction)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: DashboardState,
    onAction: (DashboardAction) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.userName.isBlank()) stringResource(R.string.dashboard_greeting_anon)
                        else stringResource(R.string.dashboard_greeting, state.userName),
                    )
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }
            state.latest == null -> EmptyDashboard(Modifier.padding(padding), onAction)
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "weight") {
                    WeightCard(state, onClick = { onAction(DashboardAction.OnLatestClick) })
                }
                item(span = { GridItemSpan(maxLineSpan) }, key = "actions") {
                    ActionButtons(onAction)
                }
                item(span = { GridItemSpan(maxLineSpan) }, key = "header") {
                    Text(
                        stringResource(R.string.dashboard_body_composition),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (!state.latest.hasComposition) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "hint") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Info, contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(
                                stringResource(R.string.dashboard_no_impedance),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                items(state.metrics, key = { it.first.name }) { (metric, value) ->
                    MetricTile(value, onClick = { onAction(DashboardAction.OnMetricClick(metric)) })
                }
            }
        }
    }
}

@Composable
private fun WeightCard(state: DashboardState, onClick: () -> Unit) {
    val latest = state.latest ?: return
    Card(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.metric_weight), style = MaterialTheme.typography.labelLarge)
            Text(
                latest.weightText,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
            )
            latest.deltaText?.let {
                Text(
                    stringResource(R.string.dashboard_change_since, it),
                    style = MaterialTheme.typography.bodyMedium,
                    color = latest.deltaTone?.color() ?: MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.dashboard_last_measured, latest.measuredAt),
                style = MaterialTheme.typography.bodySmall,
            )
            state.goal?.let { goal ->
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { goal.progress },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                )
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.dashboard_goal, goal.goalText),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        if (goal.reached) stringResource(R.string.dashboard_goal_reached)
                        else stringResource(R.string.dashboard_to_go, goal.remainingText),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionButtons(onAction: (DashboardAction) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = { onAction(DashboardAction.OnMeasureClick) },
            modifier = Modifier.weight(1f).height(52.dp),
        ) {
            Icon(Icons.Filled.Bluetooth, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.action_weigh))
        }
        OutlinedButton(
            onClick = { onAction(DashboardAction.OnAddManualClick) },
            modifier = Modifier.weight(1f).height(52.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.action_add_manual))
        }
    }
}

@Composable
private fun EmptyDashboard(modifier: Modifier, onAction: (DashboardAction) -> Unit) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.MonitorWeight, contentDescription = null,
            modifier = Modifier.size(96.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.dashboard_empty_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.dashboard_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        ActionButtons(onAction)
    }
}

@Preview
@Composable
private fun DashboardScreenPreview() {
    RenpTheme {
        DashboardScreen(
            state = DashboardState(
                isLoading = false,
                userName = "Alex",
                latest = LatestMeasurementUi(1, "78.2 kg", "22 Nov 2026, 07:31", "−0.4 kg", Tone.GOOD, true),
                goal = GoalUi("75.0 kg", "3.2 kg", 0.6f, false),
                metrics = listOf(
                    Metric.BMI to MetricValueUi(R.string.metric_bmi, "24.1", MetricStatus.NORMAL),
                    Metric.BODY_FAT to MetricValueUi(R.string.metric_body_fat, "21.4 %", MetricStatus.HIGH),
                ),
            ),
            onAction = {},
        )
    }
}
