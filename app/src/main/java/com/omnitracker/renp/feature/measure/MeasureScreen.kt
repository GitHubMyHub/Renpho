package com.omnitracker.renp.feature.measure

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omnitracker.renp.R
import com.omnitracker.renp.core.presentation.MetricStatus
import com.omnitracker.renp.core.presentation.ObserveAsEvents
import com.omnitracker.renp.data.ble.AndroidBleScaleManager
import com.omnitracker.renp.domain.scale.DiscoveredScale
import com.omnitracker.renp.ui.components.MetricRow
import com.omnitracker.renp.ui.components.MetricValueUi
import com.omnitracker.renp.ui.theme.RenpTheme
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MeasureRoot(
    onBack: () -> Unit,
    onShowMeasurement: (Long) -> Unit,
    viewModel: MeasureViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissions = remember { AndroidBleScaleManager.requiredPermissions().toTypedArray() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result -> viewModel.onAction(MeasureAction.OnPermissionResult(result.values.all { it })) }

    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { viewModel.onAction(MeasureAction.OnBluetoothEnableResult) }

    LaunchedEffect(Unit) {
        val granted = permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (granted) viewModel.onAction(MeasureAction.OnPermissionResult(true))
        else permissionLauncher.launch(permissions)
    }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            MeasureEvent.RequestPermissions -> permissionLauncher.launch(permissions)
            MeasureEvent.RequestEnableBluetooth -> try {
                enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            } catch (_: SecurityException) {
                permissionLauncher.launch(permissions)
            }
            MeasureEvent.NavigateBack -> onBack()
            is MeasureEvent.ShowMeasurement -> onShowMeasurement(event.id)
        }
    }

    MeasureScreen(state = state, onAction = viewModel::onAction)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasureScreen(
    state: MeasureState,
    onAction: (MeasureAction) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.measure_title)) },
                navigationIcon = {
                    IconButton(onClick = { onAction(MeasureAction.OnBackClick) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        AnimatedContent(
            targetState = state.phase,
            contentKey = { it::class },
            modifier = Modifier.fillMaxSize().padding(padding),
            label = "measurePhase",
        ) { phase ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (phase) {
                    MeasurePhase.Starting -> CircularProgressIndicator(Modifier.padding(top = 64.dp))

                    MeasurePhase.NeedsPermission -> Message(
                        icon = Icons.Filled.Bluetooth,
                        title = stringResource(R.string.measure_permission_title),
                        body = stringResource(R.string.measure_permission_body),
                        action = stringResource(R.string.measure_grant),
                        onClick = { onAction(MeasureAction.OnRequestPermissionClick) },
                    )

                    MeasurePhase.BluetoothOff -> Message(
                        icon = Icons.Filled.BluetoothDisabled,
                        title = stringResource(R.string.measure_bt_off_title),
                        body = stringResource(R.string.measure_bt_off_body),
                        action = stringResource(R.string.measure_enable_bt),
                        onClick = { onAction(MeasureAction.OnEnableBluetoothClick) },
                    )

                    MeasurePhase.Scanning -> ScanningContent(state, onAction)

                    is MeasurePhase.Connecting -> Progress(stringResource(R.string.measure_connecting, phase.name))

                    is MeasurePhase.Connected -> {
                        PulsingIcon(Icons.Filled.MonitorWeight)
                        Text(
                            stringResource(R.string.measure_connected),
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            phase.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    is MeasurePhase.Measuring -> {
                        Spacer(Modifier.height(48.dp))
                        Text(
                            phase.weightText,
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(stringResource(R.string.measure_measuring), style = MaterialTheme.typography.titleMedium)
                        CircularProgressIndicator()
                    }

                    is MeasurePhase.Result -> ResultContent(phase, onAction)

                    MeasurePhase.NotFound -> Message(
                        icon = Icons.Filled.SearchOff,
                        title = stringResource(R.string.measure_no_scale_found),
                        body = stringResource(R.string.measure_no_scale_found_body),
                        action = stringResource(R.string.measure_scan_again),
                        onClick = { onAction(MeasureAction.OnRetryClick) },
                    )

                    is MeasurePhase.Error -> Message(
                        icon = Icons.Filled.ErrorOutline,
                        title = stringResource(phase.message),
                        body = null,
                        action = stringResource(R.string.retry),
                        onClick = { onAction(MeasureAction.OnRetryClick) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ScanningContent(state: MeasureState, onAction: (MeasureAction) -> Unit) {
    PulsingIcon(Icons.AutoMirrored.Filled.BluetoothSearching)
    Text(stringResource(R.string.measure_scanning_title), style = MaterialTheme.typography.titleLarge)
    Text(
        stringResource(R.string.measure_scanning_body),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    state.pairedScaleName?.let {
        Text(
            stringResource(R.string.measure_paired_hint, it),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    if (state.discovered.isNotEmpty()) {
        Text(
            stringResource(R.string.measure_found_scales),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        Card(Modifier.fillMaxWidth()) {
            state.discovered.forEachIndexed { index, scale ->
                if (index > 0) HorizontalDivider()
                ScaleRow(scale) { onAction(MeasureAction.OnScaleClick(scale.address)) }
            }
        }
    }
}

@Composable
private fun ScaleRow(scale: DiscoveredScale, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.MonitorWeight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.size(16.dp))
        Column(Modifier.weight(1f)) {
            Text(scale.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                scale.address,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text("${scale.rssi} dBm", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ResultContent(phase: MeasurePhase.Result, onAction: (MeasureAction) -> Unit) {
    Icon(
        Icons.Filled.CheckCircle, contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(56.dp),
    )
    Text(stringResource(R.string.measure_result_title), style = MaterialTheme.typography.titleMedium)
    Text(phase.weightText, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
    if (!phase.hasComposition) {
        Text(
            stringResource(R.string.measure_result_no_impedance),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Card(Modifier.fillMaxWidth().clickable { onAction(MeasureAction.OnDetailsClick) }) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            phase.metrics.forEachIndexed { i, metric ->
                if (i > 0) HorizontalDivider()
                MetricRow(metric)
            }
        }
    }
    Button(onClick = { onAction(MeasureAction.OnDoneClick) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        Text(stringResource(R.string.done))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { onAction(MeasureAction.OnDiscardClick) }) { Text(stringResource(R.string.measure_discard)) }
        TextButton(onClick = { onAction(MeasureAction.OnRetryClick) }) { Text(stringResource(R.string.measure_measure_again)) }
    }
}

@Composable
private fun Message(icon: ImageVector, title: String, body: String?, action: String, onClick: () -> Unit) {
    Spacer(Modifier.height(32.dp))
    Icon(icon, contentDescription = null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
    Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
    body?.let {
        Text(
            it,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    OutlinedButton(onClick = onClick) { Text(action) }
}

@Composable
private fun Progress(text: String) {
    Spacer(Modifier.height(48.dp))
    CircularProgressIndicator()
    Text(text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
}

@Composable
private fun PulsingIcon(icon: ImageVector) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulseScale",
    )
    Spacer(Modifier.height(24.dp))
    Icon(
        icon, contentDescription = null,
        modifier = Modifier.size(96.dp).scale(scale),
        tint = MaterialTheme.colorScheme.primary,
    )
}

@Preview
@Composable
private fun MeasureResultPreview() {
    RenpTheme {
        MeasureScreen(
            state = MeasureState(
                phase = MeasurePhase.Result(
                    measurementId = 1,
                    weightText = "78.2 kg",
                    hasComposition = true,
                    metrics = listOf(
                        MetricValueUi(R.string.metric_bmi, "24.1", MetricStatus.NORMAL),
                        MetricValueUi(R.string.metric_body_fat, "21.4 %", MetricStatus.HIGH),
                    ),
                ),
            ),
            onAction = {},
        )
    }
}

@Preview
@Composable
private fun MeasureScanningPreview() {
    RenpTheme {
        MeasureScreen(
            state = MeasureState(
                phase = MeasurePhase.Scanning,
                discovered = listOf(DiscoveredScale("AA:BB:CC:DD:EE:FF", "QN-Scale", -58)),
            ),
            onAction = {},
        )
    }
}
