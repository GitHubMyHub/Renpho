package com.omnitracker.renpho.data.ble

import com.omnitracker.renpho.domain.model.WeightUnit
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QnScaleProtocolTest {

    private val variant = QnScaleProtocol.Variant.TYPE_1

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    /** 0x10 weight packet: weight BE at [3..4], stable flag [5], resistance BE at [6..7]. */
    private fun weightPacket(raw: Int, stable: Boolean, resistance: Int = 0) = bytes(
        0x10, 0x0E, 0x15,
        raw shr 8 and 0xFF, raw and 0xFF,
        if (stable) 1 else 0,
        resistance shr 8 and 0xFF, resistance and 0xFF,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    )

    @Test
    fun `checksum is the 8-bit sum of all bytes`() {
        assertArrayEquals(bytes(0x1F, 0x05, 0x15, 0x10, 0x49), QnScaleProtocol.checksummed(0x1F, 0x05, 0x15, 0x10))
        assertArrayEquals(
            bytes(0x20, 0x08, 0x15, 0x09, 0x0B, 0xAC, 0x29, 0x26),
            QnScaleProtocol.checksummed(0x20, 0x08, 0x15, 0x09, 0x0B, 0xAC, 0x29),
        )
    }

    @Test
    fun `setup enables notifications and sends config and time`() {
        val protocol = QnScaleProtocol(variant, WeightUnit.LB, clock = { (946_702_800L + 1_000_000L) * 1000L })
        val cmds = protocol.setupCommands()
        assertEquals(GattCommand.EnableNotifications(variant.service, variant.notify), cmds[0])
        val writes = cmds.filterIsInstance<GattCommand.Write>()
        val config = writes.first { it.characteristic == variant.configWrite }
        assertArrayEquals(bytes(0x13, 0x09, 0x15, 0x02, 0x10, 0x00, 0x00, 0x00, 0x43), config.value)
        val time = writes.first { it.characteristic == variant.timeWrite }
        // 1_000_000 s = 0x000F4240, little endian
        assertArrayEquals(bytes(0x02, 0x40, 0x42, 0x0F, 0x00), time.value)
    }

    @Test
    fun `unstable weight produces live weight event`() {
        val protocol = QnScaleProtocol(variant)
        val out = protocol.onNotification(variant.notify, weightPacket(7_520, stable = false))
        assertEquals(listOf(ScaleEvent.LiveWeight(75.2f)), out.events)
        assertTrue(out.commands.isEmpty())
    }

    @Test
    fun `stable weight produces final reading with impedance and ack`() {
        val protocol = QnScaleProtocol(variant)
        val out = protocol.onNotification(variant.notify, weightPacket(7_520, stable = true, resistance = 512))
        val final = out.events.single() as ScaleEvent.Final
        assertEquals(75.2f, final.reading.weightKg, 0.001f)
        assertEquals(512, final.reading.impedanceOhm)
        val ack = out.commands.single() as GattCommand.Write
        assertArrayEquals(bytes(0x1F, 0x05, 0x15, 0x10, 0x49), ack.value)
    }

    @Test
    fun `final reading is delivered only once per weigh-in`() {
        val protocol = QnScaleProtocol(variant)
        protocol.onNotification(variant.notify, weightPacket(7_520, stable = true, resistance = 512))
        val second = protocol.onNotification(variant.notify, weightPacket(7_520, stable = true, resistance = 512))
        assertTrue(second.events.isEmpty())
        // Stepping off resets the state
        protocol.onNotification(variant.notify, weightPacket(0, stable = false))
        val third = protocol.onNotification(variant.notify, weightPacket(7_600, stable = true))
        assertEquals(1, third.events.size)
    }

    @Test
    fun `zero resistance means no impedance`() {
        val protocol = QnScaleProtocol(variant)
        val final = protocol.onNotification(variant.notify, weightPacket(7_520, stable = true)).events.single()
        assertNull((final as ScaleEvent.Final).reading.impedanceOhm)
    }

    @Test
    fun `scale info with resolution flag 0 switches to divide by 10`() {
        val protocol = QnScaleProtocol(variant)
        val info = bytes(0x12, 0x11, 0x15, 0, 0, 0, 0, 0, 0, 0, 0x00, 0, 0, 0, 0, 0, 0)
        val reply = protocol.onNotification(variant.notify, info)
        assertEquals(1, reply.commands.size)
        val out = protocol.onNotification(variant.notify, weightPacket(752, stable = false))
        assertEquals(listOf(ScaleEvent.LiveWeight(75.2f)), out.events)
    }

    @Test
    fun `notifications on other characteristics are ignored`() {
        val protocol = QnScaleProtocol(variant)
        val out = protocol.onNotification(bleUuid(0x2A19), weightPacket(7_520, stable = true))
        assertTrue(out.events.isEmpty())
    }

    @Test
    fun `real FFF0 scale session decodes weight and impedance`() {
        val v = QnScaleProtocol.Variant.TYPE_2
        val protocol = QnScaleProtocol(v)
        // Packets captured from a real scale (resolution flag 0x03)
        protocol.onNotification(v.notify, bytes(0x12, 0x11, 0xFF, 0x8D, 0x5E, 0x45, 0x00, 0x03, 0xFF, 0x03, 0x03, 0x05, 0x00, 0x01, 0x05, 0x07, 0x6C))
        val live = protocol.onNotification(v.notify, bytes(0x10, 0x0B, 0xFF, 0x17, 0xCF, 0x11, 0x00, 0x00, 0x00, 0x00, 0x11))
        assertEquals(listOf(ScaleEvent.LiveWeight(60.95f)), live.events)
        val final = protocol.onNotification(v.notify, bytes(0x10, 0x0B, 0xFF, 0x17, 0xCF, 0x01, 0x01, 0xF0, 0x01, 0xF3, 0xE6))
            .events.single() as ScaleEvent.Final
        assertEquals(60.95f, final.reading.weightKg, 0.001f)
        assertEquals(496, final.reading.impedanceOhm)
    }

    @Test
    fun `light weight on 0_01 kg scale is not misread`() {
        val protocol = QnScaleProtocol(variant)
        protocol.onNotification(variant.notify, bytes(0x12, 0x11, 0x15, 0, 0, 0, 0, 0, 0, 0, 0x03, 0, 0, 0, 0, 0, 0))
        val out = protocol.onNotification(variant.notify, weightPacket(2_500, stable = false))
        assertEquals(listOf(ScaleEvent.LiveWeight(25f)), out.events)
    }
}
