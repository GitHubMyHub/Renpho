package com.omnitracker.renp.domain

import com.omnitracker.renp.domain.model.Sex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyCompositionCalculatorTest {

    @Test
    fun `bmi is weight divided by height squared`() {
        val c = BodyCompositionCalculator.calculate(80f, 180f, 35, Sex.MALE, impedanceOhm = null)
        assertEquals(24.69f, c.bmi, 0.01f)
    }

    @Test
    fun `without impedance only bmi is available`() {
        val c = BodyCompositionCalculator.calculate(80f, 180f, 35, Sex.MALE, impedanceOhm = null)
        assertNull(c.bodyFatPercent)
        assertNull(c.bmrKcal)
        assertFalse(c.hasBodyComposition)
    }

    @Test
    fun `out of range impedance is ignored`() {
        val c = BodyCompositionCalculator.calculate(80f, 180f, 35, Sex.MALE, impedanceOhm = 5000)
        assertNull(c.bodyFatPercent)
    }

    @Test
    fun `male with typical impedance gives plausible composition`() {
        val c = BodyCompositionCalculator.calculate(80f, 180f, 35, Sex.MALE, impedanceOhm = 500)
        // Sun 2003: FFM = -10.68 + 0.65 * 64.8 + 0.26 * 80 + 0.02 * 500 = 62.24 kg, x calibration
        val ffm = 62.24 * BodyCompositionCalculator.FFM_CALIBRATION
        assertEquals(ffm.toFloat(), c.fatFreeMassKg!!, 0.05f)
        assertEquals(((80 - ffm) / 80 * 100).toFloat(), c.bodyFatPercent!!, 0.1f)
        assertEquals((370 + 21.6 * ffm).toInt(), c.bmrKcal!!)
        assertTrue(c.waterPercent!! in 45f..65f)
        assertTrue(c.skeletalMusclePercent!! in 25f..50f)
        assertTrue(c.proteinPercent!! in 5f..25f)
        assertTrue(c.boneMassKg!! in 2f..3.5f)
        assertNotNull(c.metabolicAge)
    }

    @Test
    fun `skeletal muscle is Janssen scaled by the Renpho calibration`() {
        val c = BodyCompositionCalculator.calculate(80f, 180f, 35, Sex.MALE, impedanceOhm = 500)
        // Janssen: 0.401 * 64.8 + 3.825 - 0.071 * 35 + 5.102 = 32.427 kg
        val expected = 32.427 * BodyCompositionCalculator.SKELETAL_MUSCLE_CALIBRATION / 80.0 * 100.0
        assertEquals(expected.toFloat(), c.skeletalMusclePercent!!, 0.05f)
    }

    @Test
    fun `reproduces the Renpho reference weigh-in`() {
        // Male, 37 y, 170 cm, 61.1 kg, ~496 ohm - values shown by the Renpho app
        val c = BodyCompositionCalculator.calculate(61.1f, 170f, 37, Sex.MALE, impedanceOhm = 496)
        assertEquals(21.1f, c.bmi, 0.05f)
        assertEquals(18.5f, c.bodyFatPercent!!, 0.1f)
        assertEquals(49.8f, c.fatFreeMassKg!!, 0.1f)
        assertEquals(58.8f, c.waterPercent!!, 0.1f)
        assertEquals(52.7f, c.skeletalMusclePercent!!, 0.1f)
        assertEquals(47.3f, c.muscleMassKg!!, 0.1f)
        assertEquals(2.49f, c.boneMassKg!!, 0.01f)
        assertEquals(18.6f, c.proteinPercent!!, 0.1f)
        assertEquals(1445, c.bmrKcal)
        assertEquals(16.9f, c.subcutaneousFatPercent!!, 0.1f)
        assertEquals(4, c.visceralFatRating)
    }

    @Test
    fun `female has higher body fat than male for same inputs`() {
        val male = BodyCompositionCalculator.calculate(65f, 168f, 30, Sex.MALE, 550)
        val female = BodyCompositionCalculator.calculate(65f, 168f, 30, Sex.FEMALE, 550)
        assertTrue(female.bodyFatPercent!! > male.bodyFatPercent!!)
    }

    @Test
    fun `higher impedance means more body fat`() {
        val low = BodyCompositionCalculator.calculate(80f, 180f, 35, Sex.MALE, 400)
        val high = BodyCompositionCalculator.calculate(80f, 180f, 35, Sex.MALE, 650)
        assertTrue(high.bodyFatPercent!! > low.bodyFatPercent!!)
    }

    @Test
    fun `body fat stays within physiological bounds`() {
        val extreme = BodyCompositionCalculator.calculate(150f, 150f, 60, Sex.FEMALE, 1400)
        assertTrue(extreme.bodyFatPercent!! in 3f..60.01f)
    }

    @Test
    fun `device reported body fat is used when there is no impedance`() {
        val c = BodyCompositionCalculator.calculate(80f, 180f, 35, Sex.MALE, null, deviceBodyFatPercent = 20f)
        assertEquals(20f, c.bodyFatPercent!!, 0.01f)
        assertEquals(64f, c.fatFreeMassKg!!, 0.01f)
    }
}
