package com.omnitracker.renp.data.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StandardScaleProtocolTest {

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test
    fun `parses SI weight measurement`() {
        val protocol = StandardScaleProtocol(hasWeightService = true, hasBodyCompositionService = false)
        // flags 0 (kg), weight 15040 * 0.005 = 75.2 kg
        val out = protocol.onNotification(StandardScaleProtocol.WEIGHT_MEASUREMENT, bytes(0x00, 0xC0, 0x3A))
        val final = out.events.single() as ScaleEvent.Final
        assertEquals(75.2f, final.reading.weightKg, 0.001f)
        assertNull(final.reading.impedanceOhm)
    }

    @Test
    fun `parses imperial weight measurement`() {
        val protocol = StandardScaleProtocol(hasWeightService = true, hasBodyCompositionService = false)
        // flags 1 (lb), 16578 * 0.01 = 165.78 lb = 75.2 kg
        val kg = protocol.parseWeightMeasurement(bytes(0x01, 0xC2, 0x40))!!
        assertEquals(75.2f, kg, 0.01f)
    }

    @Test
    fun `body composition adds fat and impedance to the weight`() {
        val protocol = StandardScaleProtocol(hasWeightService = true, hasBodyCompositionService = true)
        protocol.onNotification(StandardScaleProtocol.WEIGHT_MEASUREMENT, bytes(0x00, 0xC0, 0x3A))
        // flags: impedance present (bit 9) = 0x0200; body fat 215 = 21.5 %; impedance 5120 = 512.0 ohm
        val out = protocol.onNotification(
            StandardScaleProtocol.BODY_COMPOSITION_MEASUREMENT,
            bytes(0x00, 0x02, 0xD7, 0x00, 0x00, 0x14),
        )
        val reading = (out.events.single() as ScaleEvent.Final).reading
        assertEquals(75.2f, reading.weightKg, 0.001f)
        assertEquals(21.5f, reading.deviceBodyFatPercent!!, 0.01f)
        assertEquals(512, reading.impedanceOhm)
    }
}
