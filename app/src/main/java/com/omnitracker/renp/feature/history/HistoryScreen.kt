package com.omnitracker.renp.feature.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import com.omnitracker.renp.core.presentation.ObserveAsEvents
import com.omnitracker.renp.ui.components.ChartPoint
import com.omnitracker.renp.ui.components.TrendChart
import com.omnitracker.renp.ui.theme.RenpTheme
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HistoryRoot(
    onMeasurementClick: (Long) -> Unit,
    onAddManual: () -> Unit,
    viewModel: HistoryViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is HistoryEvent.NavigateToDetail -> onMeasurementClick(event.id)
            HistoryEvent.NavigateToAdd -> onAddManual()
        }
    }
    HistoryScreen(state = state, onAction = viewModel::onAction)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    state: HistoryState,
    onAction: (HistoryAction) -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.history_title)) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { onAction(HistoryAction.OnAddClick) }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_title))
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "metrics") {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Metric.entries.forEach { metric ->
                        FilterChip(
                            selected = metric == state.metric,
                            onClick = { onAction(HistoryAction.OnMetricSelect(metric)) },
                            label = { Text(stringResource(metric.label)) },
                        )
                    }
                }
            }
            item(key = "range") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    TimeRange.entries.forEachIndexed { index, range ->
                        SegmentedButton(
                            selected = range == state.range,
                            onClick = { onAction(HistoryAction.OnRangeSelect(range)) },
                            shape = SegmentedButtonDefaults.itemShape(index, TimeRange.entries.size),
                            icon = {},
                        ) { Text(stringResource(range.label)) }
                    }
                }
            }
            item(key = "chart") {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.padding(16.dp)) {
                        val title = stringResource(state.metric.label)
                        Text(
                            if (state.unitSuffix != null) "$title (${state.unitSuffix})" else title,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(12.dp))
                        if (state.points.isEmpty()) {
                            Box(Modifier.fillMaxWidth().height(200.dp), Alignment.Center) {
                                Text(
                                    stringResource(R.string.history_empty),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        } else {
                            TrendChart(
                                points = state.points,
                                goal = state.goal,
                                startLabel = state.startLabel,
                                endLabel = state.endLabel,
                                formatValue = HistoryViewModel::formatAxis,
                                contentDescription = title,
                            )
                        }
                    }
                }
            }
            state.stats?.let { stats ->
                item(key = "stats") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatCell(stringResource(R.string.history_min), stats.min, Modifier.weight(1f))
                        StatCell(stringResource(R.string.history_max), stats.max, Modifier.weight(1f))
                        StatCell(stringResource(R.string.history_avg), stats.average, Modifier.weight(1f))
                        StatCell(stringResource(R.string.history_change), stats.change, Modifier.weight(1f))
                    }
                }
            }
            if (state.items.isNotEmpty()) {
                item(key = "listHeader") {
                    Text(
                        stringResource(R.string.history_measurements),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(state.items, key = { it.id }) { item ->
                    HistoryRow(item, state.metric) { onAction(HistoryAction.OnItemClick(item.id)) }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 10.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun HistoryRow(item: HistoryItemUi, metric: Metric, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (item.isManual) Icons.Filled.Edit else Icons.Filled.Bluetooth,
            contentDescription = stringResource(if (item.isManual) R.string.source_manual else R.string.source_scale),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.date, style = MaterialTheme.typography.bodyMedium)
            if (metric != Metric.WEIGHT) {
                Text(item.weight, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(item.value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Preview
@Composable
private fun HistoryScreenPreview() {
    RenpTheme {
        HistoryScreen(
            state = HistoryState(
                isLoading = false,
                unitSuffix = "kg",
                points = listOf(80f, 79.5f, 79.8f, 78.9f).mapIndexed { i, v -> ChartPoint(i * 86_400_000L, v) },
                goal = 76f,
                startLabel = "1 Nov",
                endLabel = "4 Nov",
                stats = HistoryStatsUi("78.9 kg", "80.0 kg", "79.6 kg", "−1.1 kg"),
                items = listOf(HistoryItemUi(1, "4 Nov 2026, 07:30", "78.9 kg", "78.9 kg", false)),
            ),
            onAction = {},
        )
    }
}
