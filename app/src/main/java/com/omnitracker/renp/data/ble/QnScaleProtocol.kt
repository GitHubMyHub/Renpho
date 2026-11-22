package com.omnitracker.renp.data.ble

import com.omnitracker.renp.domain.model.WeightUnit
import com.omnitracker.renp.domain.scale.ScaleReading
import java.util.UUID

/**
 * Qingniu ("QN") / Yolanda protocol used by most Renpho Bluetooth scales
 * (advertised as "QN-Scale", "QN-Scale1", "Yolanda-CS20*", "Renpho-*").
 *
 * Identified from the original app (vendor services 0xFFE0 / 0xFFF0) and matching
 * the publicly documented, interoperability-oriented openScale implementation.
 *
 * Packets on the notify characteristic, byte 0 = opcode:
 *  - 0x10 weight:  [3..4] weight BE / factor, [5] 0=unstable 1=stable,
 *                  [6..7] resistance (50 kHz) BE, [8..9] resistance (500 kHz) BE
 *  - 0x12 scale info: [10] weight resolution flag
 *  - 0x14 request for time sync
 * Every outgoing packet ends with an 8-bit additive checksum.
 */
class QnScaleProtocol(
    private val variant: Variant,
    private val displayUnit: WeightUnit = WeightUnit.KG,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : ScaleProtocol {

    enum class Variant(
        val service: UUID,
        val notify: UUID,
        val indicate: UUID?,
        val configWrite: UUID,
        val timeWrite: UUID,
    ) {
        /** Service 0xFFE0 (most common). */
        TYPE_1(bleUuid(0xFFE0), bleUuid(0xFFE1), bleUuid(0xFFE2), bleUuid(0xFFE3), bleUuid(0xFFE4)),

        /** Service 0xFFF0 (newer firmware). */
        TYPE_2(bleUuid(0xFFF0), bleUuid(0xFFF1), null, bleUuid(0xFFF2), bleUuid(0xFFF2)),
    }

    private var weightDivider: Float = 100f
    private var finalDelivered = false

    override fun setupCommands(): List<GattCommand> = buildList {
        add(GattCommand.EnableNotifications(variant.service, variant.notify))
        variant.indicate?.let {
            add(GattCommand.EnableNotifications(variant.service, it, indicate = true, required = false))
        }
        add(write(variant.configWrite, configPacket()))
        add(write(variant.timeWrite, timePacket()))
    }

    override fun onNotification(characteristic: UUID, data: ByteArray): ProtocolOutput {
        if (characteristic != variant.notify || data.isEmpty()) return ProtocolOutput.NONE
        return when (data.u8(0)) {
            OP_SCALE_INFO -> {
                // Bit 0 = 0.01 kg resolution (seen as 0x01 and 0x03 on real scales)
                if (data.size > 10) weightDivider = if (data.u8(10) and 0x01 != 0) 100f else 10f
                ProtocolOutput(commands = listOf(write(variant.configWrite, configPacket())))
            }

            OP_TIME_REQUEST -> ProtocolOutput(commands = listOf(write(variant.configWrite, timeSyncPacket())))

            OP_WEIGHT -> parseWeight(data)

            else -> ProtocolOutput.NONE
        }
    }

    private fun parseWeight(data: ByteArray): ProtocolOutput {
        if (data.size < 6) return ProtocolOutput.NONE
        val weightKg = decodeWeight(data.u16be(3))
        val stable = data.u8(5) == 1

        if (!stable) {
            // User stepped off and on again -> allow a new final reading
            if (weightKg < 1f) finalDelivered = false
            return if (weightKg > 0f) ProtocolOutput(events = listOf(ScaleEvent.LiveWeight(weightKg)))
            else ProtocolOutput.NONE
        }

        val ack = write(variant.configWrite, checksummed(0x1F, 0x05, 0x15, 0x10))
        if (finalDelivered || weightKg <= 0f) return ProtocolOutput(commands = listOf(ack))

        val impedance = if (data.size >= 8) data.u16be(6).takeIf { it > 0 } else null
        finalDelivered = true
        return ProtocolOutput(
            commands = listOf(ack),
            events = listOf(ScaleEvent.Final(ScaleReading(weightKg = weightKg, impedanceOhm = impedance))),
        )
    }

    private fun decodeWeight(raw: Int): Float {
        var kg = raw / weightDivider
        // Some scales never send 0x12; a /10 reading above 300 kg means the factor is 100
        if (kg > 300f && weightDivider == 10f) kg = raw / 100f
        return kg
    }

    /** 0x13 0x09 0x15 <unit> 0x10 0x00 0x00 0x00 <checksum>; unit 1 = kg, 2 = lb. */
    private fun configPacket(): ByteArray {
        val unit = if (displayUnit == WeightUnit.KG) 0x01 else 0x02
        return checksummed(0x13, 0x09, 0x15, unit, 0x10, 0x00, 0x00, 0x00)
    }

    /** 0x02 + seconds since 2000-01-01 (little endian). */
    private fun timePacket(): ByteArray {
        val ts = scaleTimestamp()
        return byteArrayOf(0x02, ts.toByte(), (ts shr 8).toByte(), (ts shr 16).toByte(), (ts shr 24).toByte())
    }

    /** 0x20 0x08 0x15 <ts LE x4> <checksum>. */
    private fun timeSyncPacket(): ByteArray {
        val ts = scaleTimestamp()
        return checksummed(
            0x20, 0x08, 0x15,
            (ts and 0xFF).toInt(), ((ts shr 8) and 0xFF).toInt(),
            ((ts shr 16) and 0xFF).toInt(), ((ts shr 24) and 0xFF).toInt(),
        )
    }

    private fun scaleTimestamp(): Long = clock() / 1000L - SCALE_EPOCH_OFFSET_SECONDS

    private fun write(characteristic: UUID, value: ByteArray) =
        GattCommand.Write(variant.service, characteristic, value)

    companion object {
        const val OP_WEIGHT = 0x10
        const val OP_SCALE_INFO = 0x12
        const val OP_TIME_REQUEST = 0x14

        /** 2000-01-01T00:00:00 at UTC-5, as used by the scale firmware. */
        const val SCALE_EPOCH_OFFSET_SECONDS = 946_702_800L

        fun checksummed(vararg bytes: Int): ByteArray {
            val out = ByteArray(bytes.size + 1)
            var sum = 0
            bytes.forEachIndexed { i, b ->
                out[i] = b.toByte()
                sum += b and 0xFF
            }
            out[bytes.size] = sum.toByte()
            return out
        }
    }
}
