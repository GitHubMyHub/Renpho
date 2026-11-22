package com.omnitracker.renp.data.ble

import com.omnitracker.renp.domain.scale.ScaleReading
import java.util.UUID

fun bleUuid(shortCode: Int): UUID =
    UUID.fromString(String.format("0000%04x-0000-1000-8000-00805f9b34fb", shortCode))

/** A single GATT operation the BLE layer must execute (in order). */
sealed interface GattCommand {
    val service: UUID
    val characteristic: UUID

    data class EnableNotifications(
        override val service: UUID,
        override val characteristic: UUID,
        val indicate: Boolean = false,
        /** If false, a missing characteristic is silently skipped. */
        val required: Boolean = true,
    ) : GattCommand

    data class Write(
        override val service: UUID,
        override val characteristic: UUID,
        val value: ByteArray,
        val withResponse: Boolean = true,
    ) : GattCommand {
        override fun equals(other: Any?): Boolean =
            other is Write && other.service == service && other.characteristic == characteristic &&
                other.value.contentEquals(value) && other.withResponse == withResponse

        override fun hashCode(): Int =
            31 * (31 * service.hashCode() + characteristic.hashCode()) + value.contentHashCode()
    }
}

sealed interface ScaleEvent {
    data class LiveWeight(val weightKg: Float) : ScaleEvent
    data class Final(val reading: ScaleReading) : ScaleEvent
}

data class ProtocolOutput(
    val commands: List<GattCommand> = emptyList(),
    val events: List<ScaleEvent> = emptyList(),
) {
    companion object {
        val NONE = ProtocolOutput()
    }
}

/**
 * Pure (Android-free) state machine for one connection to one scale.
 * The BLE layer feeds it notifications and executes the commands it returns.
 */
interface ScaleProtocol {
    /** GATT operations to run right after service discovery. */
    fun setupCommands(): List<GattCommand>

    fun onNotification(characteristic: UUID, data: ByteArray): ProtocolOutput

    /**
     * True if [ScaleEvent.Final] may be followed by a better one shortly after
     * (e.g. weight first, body composition second) and should be debounced.
     */
    val debounceFinal: Boolean get() = false
}

internal fun ByteArray.u8(index: Int): Int = this[index].toInt() and 0xFF
internal fun ByteArray.u16be(index: Int): Int = (u8(index) shl 8) or u8(index + 1)
internal fun ByteArray.u16le(index: Int): Int = u8(index) or (u8(index + 1) shl 8)
