package com.omnitracker.renp.data.ble

import com.omnitracker.renp.domain.scale.ScaleReading
import java.util.UUID

/**
 * Bluetooth SIG Weight Scale Service (0x181D) and Body Composition Service (0x181B),
 * also referenced by the original app.
 */
class StandardScaleProtocol(
    private val hasWeightService: Boolean,
    private val hasBodyCompositionService: Boolean,
) : ScaleProtocol {

    private var lastWeightKg: Float? = null
    private var lastBodyFat: Float? = null
    private var lastImpedance: Int? = null

    override val debounceFinal: Boolean = true

    override fun setupCommands(): List<GattCommand> = buildList {
        if (hasWeightService) {
            add(GattCommand.EnableNotifications(WEIGHT_SERVICE, WEIGHT_MEASUREMENT, indicate = true))
        }
        if (hasBodyCompositionService) {
            add(
                GattCommand.EnableNotifications(
                    BODY_COMPOSITION_SERVICE, BODY_COMPOSITION_MEASUREMENT, indicate = true, required = false,
                ),
            )
        }
    }

    override fun onNotification(characteristic: UUID, data: ByteArray): ProtocolOutput {
        when (characteristic) {
            WEIGHT_MEASUREMENT -> parseWeightMeasurement(data)?.let { lastWeightKg = it } ?: return ProtocolOutput.NONE
            BODY_COMPOSITION_MEASUREMENT -> if (!parseBodyComposition(data)) return ProtocolOutput.NONE
            else -> return ProtocolOutput.NONE
        }
        val weight = lastWeightKg ?: return ProtocolOutput.NONE
        return ProtocolOutput(
            events = listOf(
                ScaleEvent.Final(
                    ScaleReading(weightKg = weight, impedanceOhm = lastImpedance, deviceBodyFatPercent = lastBodyFat),
                ),
            ),
        )
    }

    /** org.bluetooth.characteristic.weight_measurement (0x2A9D). */
    internal fun parseWeightMeasurement(data: ByteArray): Float? {
        if (data.size < 3) return null
        val imperial = data.u8(0) and 0x01 != 0
        val raw = data.u16le(1)
        if (raw == 0xFFFF) return null // "measurement unsuccessful"
        return if (imperial) raw * 0.01f * LB_TO_KG else raw * 0.005f
    }

    /** org.bluetooth.characteristic.body_composition_measurement (0x2A9C). */
    internal fun parseBodyComposition(data: ByteArray): Boolean {
        if (data.size < 4) return false
        val flags = data.u16le(0)
        val imperial = flags and 0x0001 != 0
        val massFactor = if (imperial) 0.01f * LB_TO_KG else 0.005f
        val bodyFatRaw = data.u16le(2)
        var i = 4
        fun has(bit: Int) = flags and (1 shl bit) != 0
        fun next16(): Int? = if (i + 1 < data.size) data.u16le(i).also { i += 2 } else null

        if (has(1)) i += 7 // timestamp
        if (has(2)) i += 1 // user id
        if (has(3)) next16() // BMR
        if (has(4)) next16() // muscle %
        if (has(5)) next16() // muscle mass
        if (has(6)) next16() // fat free mass
        if (has(7)) next16() // soft lean mass
        if (has(8)) next16() // body water mass
        val impedance = if (has(9)) next16()?.let { it / 10 } else null
        val weight = if (has(10)) next16()?.let { it * massFactor } else null

        if (bodyFatRaw != 0xFFFF) lastBodyFat = bodyFatRaw / 10f
        if (impedance != null && impedance > 0) lastImpedance = impedance
        if (weight != null && weight > 0f) lastWeightKg = weight
        return true
    }

    companion object {
        private const val LB_TO_KG = 0.45359237f
        val WEIGHT_SERVICE: UUID = bleUuid(0x181D)
        val WEIGHT_MEASUREMENT: UUID = bleUuid(0x2A9D)
        val BODY_COMPOSITION_SERVICE: UUID = bleUuid(0x181B)
        val BODY_COMPOSITION_MEASUREMENT: UUID = bleUuid(0x2A9C)
    }
}
