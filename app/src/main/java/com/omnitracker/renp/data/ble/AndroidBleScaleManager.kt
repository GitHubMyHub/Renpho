package com.omnitracker.renp.data.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.omnitracker.renp.domain.UserPreferences
import com.omnitracker.renp.domain.scale.DiscoveredScale
import com.omnitracker.renp.domain.scale.ScaleError
import com.omnitracker.renp.domain.scale.ScaleManager
import com.omnitracker.renp.domain.scale.ScaleReading
import com.omnitracker.renp.domain.scale.ScaleStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * Android BLE implementation of [ScaleManager].
 *
 * All GATT callbacks are funnelled into a single coroutine ([events] channel)
 * so the protocol state machine and the operation queue never race.
 */
@SuppressLint("MissingPermission") // every entry point checks hasPermissions()
class AndroidBleScaleManager(
    private val context: Context,
    private val preferences: UserPreferences,
) : ScaleManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    private val adapter: BluetoothAdapter? get() = bluetoothManager?.adapter

    private val _status = MutableStateFlow<ScaleStatus>(ScaleStatus.Idle)
    override val status: StateFlow<ScaleStatus> = _status.asStateFlow()

    private val _discovered = MutableStateFlow<List<DiscoveredScale>>(emptyList())
    override val discoveredScales: StateFlow<List<DiscoveredScale>> = _discovered.asStateFlow()

    private val _readings = MutableSharedFlow<ScaleReading>(extraBufferCapacity = 4)
    override val readings: Flow<ScaleReading> = _readings.asSharedFlow()

    private var scanTimeoutJob: Job? = null
    @Volatile
    private var isScanning = false

    @Volatile
    private var connection: Connection? = null

    // ---------------------------------------------------------------- scanning

    override fun isBluetoothEnabled(): Boolean = adapter?.isEnabled == true

    override fun startScan() {
        val preflightError = preflight()
        if (preflightError != null) {
            _status.value = ScaleStatus.Failed(preflightError)
            return
        }
        val scanner = adapter?.bluetoothLeScanner ?: run {
            _status.value = ScaleStatus.Failed(ScaleError.BLUETOOTH_OFF)
            return
        }
        disconnect()
        stopScan()
        _discovered.value = emptyList()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        try {
            // No service filter: many QN scales do not advertise their service UUID
            scanner.startScan(null, settings, scanCallback)
            isScanning = true
            _status.value = ScaleStatus.Scanning
        } catch (e: Exception) {
            Log.e(TAG, "startScan failed", e)
            _status.value = ScaleStatus.Failed(ScaleError.SCAN_FAILED)
            return
        }
        scanTimeoutJob = scope.launch {
            delay(SCAN_TIMEOUT_MS)
            if (isScanning) {
                stopScan()
                _status.value = ScaleStatus.Idle
            }
        }
    }

    override fun stopScan() {
        scanTimeoutJob?.cancel()
        scanTimeoutJob = null
        if (!isScanning) return
        isScanning = false
        try {
            if (hasPermissions()) adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            Log.w(TAG, "stopScan failed", e)
        }
        if (_status.value == ScaleStatus.Scanning) _status.value = ScaleStatus.Idle
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val record = result.scanRecord
            val name = record?.deviceName ?: safeDeviceName(result.device) ?: return
            val services = record?.serviceUuids?.map { it.uuid }.orEmpty()
            if (!isSupportedScale(name, services)) return
            val scale = DiscoveredScale(result.device.address, name, result.rssi)
            _discovered.update { list ->
                (list.filterNot { it.address == scale.address } + scale).sortedByDescending { it.rssi }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "scan failed: $errorCode")
            isScanning = false
            _status.value = ScaleStatus.Failed(ScaleError.SCAN_FAILED)
        }
    }

    private fun safeDeviceName(device: BluetoothDevice): String? =
        try { device.name } catch (_: SecurityException) { null }

    // ---------------------------------------------------------------- connection

    override fun connect(address: String) {
        preflight()?.let {
            _status.value = ScaleStatus.Failed(it)
            return
        }
        stopScan()
        connection?.close()
        val device = try {
            adapter?.getRemoteDevice(address)
        } catch (_: IllegalArgumentException) {
            null
        } ?: run {
            _status.value = ScaleStatus.Failed(ScaleError.CONNECTION_FAILED)
            return
        }
        val name = _discovered.value.firstOrNull { it.address == address }?.name
            ?: safeDeviceName(device) ?: address
        connection = Connection(device, name).also { it.open() }
    }

    override fun disconnect() {
        connection?.close()
        connection = null
        if (_status.value !is ScaleStatus.Failed && _status.value != ScaleStatus.Scanning) {
            _status.value = ScaleStatus.Idle
        }
    }

    private fun preflight(): ScaleError? = when {
        adapter == null -> ScaleError.BLUETOOTH_UNAVAILABLE
        !hasPermissions() -> ScaleError.PERMISSION_DENIED
        adapter?.isEnabled != true -> ScaleError.BLUETOOTH_OFF
        else -> null
    }

    private fun hasPermissions(): Boolean = requiredPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    /** One GATT connection. Lives until [close]. */
    private inner class Connection(private val device: BluetoothDevice, private val name: String) {
        private val events = Channel<GattEvent>(Channel.UNLIMITED)
        private var gatt: BluetoothGatt? = null
        private var protocol: ScaleProtocol? = null
        // Completed directly from the binder thread: the event loop is suspended
        // while it waits for this, so routing it through [events] would deadlock.
        @Volatile
        private var pending: CompletableDeferred<Boolean>? = null
        private var debounceJob: Job? = null
        private var closed = false
        private val job: Job = scope.launch { processEvents() }

        @Suppress("DEPRECATION")
        fun open() {
            _status.value = ScaleStatus.Connecting(name)
            gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(context, false, callback)
            }
            scope.launch {
                delay(CONNECT_TIMEOUT_MS)
                if (!closed && _status.value is ScaleStatus.Connecting) {
                    fail(ScaleError.CONNECTION_FAILED)
                }
            }
        }

        fun close() {
            if (closed) return
            closed = true
            debounceJob?.cancel()
            job.cancel()
            pending?.complete(false)
            try {
                gatt?.disconnect()
                gatt?.close()
            } catch (e: Exception) {
                Log.w(TAG, "close failed", e)
            }
            gatt = null
        }

        private fun fail(error: ScaleError) {
            close()
            if (connection === this) connection = null
            _status.value = ScaleStatus.Failed(error)
        }

        private suspend fun processEvents() {
            for (event in events) {
                when (event) {
                    is GattEvent.ConnectionChanged -> onConnectionChanged(event)
                    is GattEvent.ServicesDiscovered -> onServicesDiscovered(event.success)
                    is GattEvent.Notification -> onNotification(event.characteristic, event.value)
                }
            }
        }

        private fun onConnectionChanged(event: GattEvent.ConnectionChanged) {
            if (event.connected && event.success) {
                // Small delay improves reliability of service discovery on some stacks
                scope.launch {
                    delay(300)
                    if (!closed) gatt?.discoverServices()
                }
            } else {
                if (closed) return
                close()
                if (connection === this) connection = null
                // Scales power down after a measurement - once configured that's a normal disconnect
                _status.value = if (protocol != null) {
                    ScaleStatus.Disconnected
                } else {
                    ScaleStatus.Failed(ScaleError.CONNECTION_FAILED)
                }
            }
        }

        private suspend fun onServicesDiscovered(success: Boolean) {
            val g = gatt ?: return
            if (!success) return fail(ScaleError.CONNECTION_FAILED)
            val unit = preferences.settings.first().weightUnit
            val serviceIds = g.services.map { it.uuid }.toSet()
            val selected: ScaleProtocol = when {
                QnScaleProtocol.Variant.TYPE_1.service in serviceIds ->
                    QnScaleProtocol(QnScaleProtocol.Variant.TYPE_1, unit)
                QnScaleProtocol.Variant.TYPE_2.service in serviceIds ->
                    QnScaleProtocol(QnScaleProtocol.Variant.TYPE_2, unit)
                StandardScaleProtocol.WEIGHT_SERVICE in serviceIds ||
                    StandardScaleProtocol.BODY_COMPOSITION_SERVICE in serviceIds ->
                    StandardScaleProtocol(
                        hasWeightService = StandardScaleProtocol.WEIGHT_SERVICE in serviceIds,
                        hasBodyCompositionService = StandardScaleProtocol.BODY_COMPOSITION_SERVICE in serviceIds,
                    )
                else -> return fail(ScaleError.UNSUPPORTED_DEVICE)
            }
            protocol = selected
            for (command in selected.setupCommands()) {
                if (!execute(command)) {
                    Log.w(TAG, "setup command failed: $command")
                    return fail(ScaleError.CONNECTION_FAILED)
                }
            }
            Log.d(TAG, "scale configured: ${selected::class.simpleName}")
            _status.value = ScaleStatus.Connected(name)
        }

        private suspend fun onNotification(characteristic: UUID, value: ByteArray) {
            Log.d(TAG, "notify $characteristic: ${value.joinToString(" ") { "%02X".format(it) }}")
            val p = protocol ?: return
            val output = p.onNotification(characteristic, value)
            output.commands.forEach { execute(it) }
            for (event in output.events) {
                when (event) {
                    is ScaleEvent.LiveWeight -> _status.value = ScaleStatus.Measuring(name, event.weightKg)
                    is ScaleEvent.Final -> {
                        _status.value = ScaleStatus.Measuring(name, event.reading.weightKg)
                        if (p.debounceFinal) {
                            debounceJob?.cancel()
                            debounceJob = scope.launch {
                                delay(FINAL_DEBOUNCE_MS)
                                _readings.emit(event.reading)
                            }
                        } else {
                            _readings.emit(event.reading)
                        }
                    }
                }
            }
        }

        /** Executes one GATT operation and waits for its callback. */
        private suspend fun execute(command: GattCommand): Boolean {
            val g = gatt ?: return false
            val characteristic = g.getService(command.service)?.getCharacteristic(command.characteristic)
            if (characteristic == null) {
                return command is GattCommand.EnableNotifications && !command.required
            }
            val deferred = CompletableDeferred<Boolean>()
            pending = deferred
            val started = when (command) {
                is GattCommand.EnableNotifications -> enableNotifications(g, characteristic, command.indicate)
                is GattCommand.Write -> write(g, characteristic, command)
            }
            if (!started) {
                pending = null
                return command is GattCommand.EnableNotifications && !command.required
            }
            val result = withTimeoutOrNull(OPERATION_TIMEOUT_MS) { deferred.await() } ?: false
            pending = null
            return result || (command is GattCommand.EnableNotifications && !command.required)
        }

        @Suppress("DEPRECATION")
        private fun enableNotifications(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            indicate: Boolean,
        ): Boolean {
            if (!g.setCharacteristicNotification(characteristic, true)) return false
            val descriptor = characteristic.getDescriptor(CCCD) ?: return false
            val value = if (indicate) {
                BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            } else {
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            }
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeDescriptor(descriptor, value) == BluetoothGatt.GATT_SUCCESS
            } else {
                descriptor.value = value
                g.writeDescriptor(descriptor)
            }
        }

        @Suppress("DEPRECATION")
        private fun write(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            command: GattCommand.Write,
        ): Boolean {
            val supportsNoResponse =
                characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
            val supportsWrite = characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0
            val type = if (command.withResponse && supportsWrite || !supportsNoResponse) {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            }
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeCharacteristic(characteristic, command.value, type) == BluetoothGatt.GATT_SUCCESS
            } else {
                characteristic.writeType = type
                characteristic.value = command.value
                g.writeCharacteristic(characteristic)
            }
        }

        private val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                events.trySend(
                    GattEvent.ConnectionChanged(
                        connected = newState == BluetoothProfile.STATE_CONNECTED,
                        success = status == BluetoothGatt.GATT_SUCCESS,
                    ),
                )
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                events.trySend(GattEvent.ServicesDiscovered(status == BluetoothGatt.GATT_SUCCESS))
            }

            override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                pending?.complete(status == BluetoothGatt.GATT_SUCCESS)
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int,
            ) {
                pending?.complete(status == BluetoothGatt.GATT_SUCCESS)
            }

            // API 33+
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
            ) {
                events.trySend(GattEvent.Notification(characteristic.uuid, value.copyOf()))
            }

            // API < 33
            @Deprecated("Deprecated in Java")
            @Suppress("DEPRECATION")
            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
                val value = characteristic.value ?: return
                events.trySend(GattEvent.Notification(characteristic.uuid, value.copyOf()))
            }
        }
    }

    private sealed interface GattEvent {
        data class ConnectionChanged(val connected: Boolean, val success: Boolean) : GattEvent
        data class ServicesDiscovered(val success: Boolean) : GattEvent
        class Notification(val characteristic: UUID, val value: ByteArray) : GattEvent
    }

    companion object {
        private const val TAG = "BleScale"
        private const val SCAN_TIMEOUT_MS = 60_000L
        private const val CONNECT_TIMEOUT_MS = 15_000L
        private const val OPERATION_TIMEOUT_MS = 3_000L
        private const val FINAL_DEBOUNCE_MS = 1_500L
        private val CCCD: UUID = bleUuid(0x2902)

        private val NAME_PREFIXES = listOf("QN-", "Yolanda", "Renpho", "RENPHO", "ES-", "Elis")
        private val SUPPORTED_SERVICES = setOf(
            QnScaleProtocol.Variant.TYPE_1.service,
            QnScaleProtocol.Variant.TYPE_2.service,
            StandardScaleProtocol.WEIGHT_SERVICE,
            StandardScaleProtocol.BODY_COMPOSITION_SERVICE,
        )

        fun isSupportedScale(name: String, advertisedServices: List<UUID>): Boolean =
            NAME_PREFIXES.any { name.startsWith(it, ignoreCase = true) } ||
                advertisedServices.any { it in SUPPORTED_SERVICES }

        fun requiredPermissions(): List<String> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                listOf(Manifest.permission.ACCESS_FINE_LOCATION)
            }
    }
}
