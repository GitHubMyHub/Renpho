package com.omnitracker.renp.feature.measure

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnitracker.renp.R
import com.omnitracker.renp.core.presentation.Metric
import com.omnitracker.renp.core.presentation.UnitFormatter
import com.omnitracker.renp.core.presentation.toMetricValues
import com.omnitracker.renp.domain.MeasurementDataSource
import com.omnitracker.renp.domain.MeasurementFactory
import com.omnitracker.renp.domain.UserPreferences
import com.omnitracker.renp.domain.model.AppSettings
import com.omnitracker.renp.domain.model.MeasurementSource
import com.omnitracker.renp.domain.model.PairedScale
import com.omnitracker.renp.domain.scale.DiscoveredScale
import com.omnitracker.renp.domain.scale.ScaleError
import com.omnitracker.renp.domain.scale.ScaleManager
import com.omnitracker.renp.domain.scale.ScaleReading
import com.omnitracker.renp.domain.scale.ScaleStatus
import com.omnitracker.renp.ui.components.MetricValueUi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface MeasurePhase {
    data object Starting : MeasurePhase
    data object NeedsPermission : MeasurePhase
    data object BluetoothOff : MeasurePhase
    data object Scanning : MeasurePhase
    data class Connecting(val name: String) : MeasurePhase
    data class Connected(val name: String) : MeasurePhase
    data class Measuring(val weightText: String) : MeasurePhase
    data class Result(
        val measurementId: Long,
        val weightText: String,
        val hasComposition: Boolean,
        val metrics: List<MetricValueUi>,
    ) : MeasurePhase
    data object NotFound : MeasurePhase
    data class Error(val message: Int) : MeasurePhase
}

data class MeasureState(
    val phase: MeasurePhase = MeasurePhase.Starting,
    val discovered: List<DiscoveredScale> = emptyList(),
    val pairedScaleName: String? = null,
)

sealed interface MeasureAction {
    data class OnPermissionResult(val granted: Boolean) : MeasureAction
    data object OnBluetoothEnableResult : MeasureAction
    data object OnRequestPermissionClick : MeasureAction
    data object OnEnableBluetoothClick : MeasureAction
    data class OnScaleClick(val address: String) : MeasureAction
    data object OnRetryClick : MeasureAction
    data object OnDiscardClick : MeasureAction
    data object OnDoneClick : MeasureAction
    data object OnDetailsClick : MeasureAction
    data object OnBackClick : MeasureAction
}

sealed interface MeasureEvent {
    data object RequestPermissions : MeasureEvent
    data object RequestEnableBluetooth : MeasureEvent
    data object NavigateBack : MeasureEvent
    data class ShowMeasurement(val id: Long) : MeasureEvent
}

class MeasureViewModel(
    private val scaleManager: ScaleManager,
    private val measurements: MeasurementDataSource,
    private val preferences: UserPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(MeasureState())
    val state = _state.asStateFlow()

    private val _events = Channel<MeasureEvent>()
    val events = _events.receiveAsFlow()

    /** True while this screen owns an active scan/connection. */
    private var active = false
    private var autoConnectAttempted = false
    private var connectedAddress: String? = null
    private var settings = AppSettings()

    init {
        viewModelScope.launch {
            preferences.settings.collect { s ->
                settings = s
                _state.update { it.copy(pairedScaleName = s.pairedScale?.name) }
            }
        }
        viewModelScope.launch { scaleManager.status.collect(::onStatus) }
        viewModelScope.launch {
            scaleManager.discoveredScales.collect { list ->
                _state.update { it.copy(discovered = list) }
                maybeAutoConnect(list)
            }
        }
        viewModelScope.launch { scaleManager.readings.collect(::onReading) }
    }

    fun onAction(action: MeasureAction) {
        when (action) {
            is MeasureAction.OnPermissionResult ->
                if (action.granted) startScanning() else setPhase(MeasurePhase.NeedsPermission)
            MeasureAction.OnBluetoothEnableResult -> startScanning()
            MeasureAction.OnRequestPermissionClick -> send(MeasureEvent.RequestPermissions)
            MeasureAction.OnEnableBluetoothClick -> send(MeasureEvent.RequestEnableBluetooth)
            is MeasureAction.OnScaleClick -> connect(action.address)
            MeasureAction.OnRetryClick -> startScanning()
            MeasureAction.OnDiscardClick -> {
                val phase = _state.value.phase as? MeasurePhase.Result ?: return
                viewModelScope.launch {
                    measurements.delete(phase.measurementId)
                    _events.send(MeasureEvent.NavigateBack)
                }
            }
            MeasureAction.OnDoneClick, MeasureAction.OnBackClick -> {
                stop()
                send(MeasureEvent.NavigateBack)
            }
            MeasureAction.OnDetailsClick -> {
                val phase = _state.value.phase as? MeasurePhase.Result ?: return
                send(MeasureEvent.ShowMeasurement(phase.measurementId))
            }
        }
    }

    private fun startScanning() {
        if (!scaleManager.isBluetoothEnabled()) {
            setPhase(MeasurePhase.BluetoothOff)
            return
        }
        active = true
        autoConnectAttempted = false
        connectedAddress = null
        setPhase(MeasurePhase.Scanning)
        scaleManager.startScan()
    }

    private fun connect(address: String) {
        active = true
        connectedAddress = address
        scaleManager.connect(address)
    }

    private fun maybeAutoConnect(list: List<DiscoveredScale>) {
        val paired = settings.pairedScale ?: return
        if (!active || autoConnectAttempted || _state.value.phase != MeasurePhase.Scanning) return
        if (list.any { it.address == paired.address }) {
            autoConnectAttempted = true
            connect(paired.address)
        }
    }

    private fun onStatus(status: ScaleStatus) {
        if (!active) return
        val current = _state.value.phase
        when (status) {
            ScaleStatus.Idle -> if (current == MeasurePhase.Scanning) setPhase(MeasurePhase.NotFound)
            ScaleStatus.Scanning -> setPhase(MeasurePhase.Scanning)
            is ScaleStatus.Connecting -> setPhase(MeasurePhase.Connecting(status.name))
            is ScaleStatus.Connected -> setPhase(MeasurePhase.Connected(status.name))
            is ScaleStatus.Measuring ->
                setPhase(MeasurePhase.Measuring(UnitFormatter.weight(status.liveWeightKg, settings.weightUnit)))
            ScaleStatus.Disconnected -> setPhase(MeasurePhase.Error(R.string.measure_disconnected))
            is ScaleStatus.Failed -> {
                active = false
                setPhase(
                    when (status.error) {
                        ScaleError.PERMISSION_DENIED -> MeasurePhase.NeedsPermission
                        ScaleError.BLUETOOTH_OFF -> MeasurePhase.BluetoothOff
                        ScaleError.BLUETOOTH_UNAVAILABLE -> MeasurePhase.Error(R.string.error_bt_unavailable)
                        ScaleError.SCAN_FAILED -> MeasurePhase.Error(R.string.error_scan_failed)
                        ScaleError.CONNECTION_FAILED -> MeasurePhase.Error(R.string.error_connection_failed)
                        ScaleError.UNSUPPORTED_DEVICE -> MeasurePhase.Error(R.string.error_unsupported_device)
                    },
                )
            }
        }
    }

    private suspend fun onReading(reading: ScaleReading) {
        if (!active) return
        active = false
        val profile = preferences.profile.first()
        if (profile == null) {
            setPhase(MeasurePhase.Error(R.string.measure_no_profile))
            return
        }
        val measurement = MeasurementFactory.create(
            profile = profile,
            weightKg = reading.weightKg,
            impedanceOhm = reading.impedanceOhm,
            source = MeasurementSource.SCALE,
            deviceBodyFatPercent = reading.deviceBodyFatPercent,
        )
        val id = measurements.insert(measurement)

        // Remember the scale so future measurements connect automatically
        val address = connectedAddress
        if (address != null && settings.pairedScale?.address != address) {
            val name = _state.value.discovered.firstOrNull { it.address == address }?.name ?: address
            preferences.setPairedScale(PairedScale(address, name))
        }

        val keyMetrics = listOf(Metric.BMI, Metric.BODY_FAT, Metric.VISCERAL_FAT, Metric.MUSCLE_MASS, Metric.WATER)
        setPhase(
            MeasurePhase.Result(
                measurementId = id,
                weightText = UnitFormatter.weight(measurement.weightKg, settings.weightUnit),
                hasComposition = measurement.composition.hasBodyComposition,
                metrics = measurement.copy(id = id).toMetricValues(profile, settings.weightUnit, keyMetrics),
            ),
        )
        // Give the scale time to receive the acknowledgement before disconnecting
        delay(1_000)
        scaleManager.disconnect()
    }

    private fun stop() {
        active = false
        scaleManager.stopScan()
        scaleManager.disconnect()
    }

    private fun setPhase(phase: MeasurePhase) = _state.update { it.copy(phase = phase) }

    private fun send(event: MeasureEvent) {
        viewModelScope.launch { _events.send(event) }
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }
}
