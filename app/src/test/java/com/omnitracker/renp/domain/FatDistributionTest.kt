package com.omnitracker.renp.domain

import com.omnitracker.renp.domain.model.Sex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FatDistributionTest {

    @Test
    fun `subcutaneous plus visceral share equals total body fat`() {
        val split = BodyCompositionCalculator.fatDistribution(80f, 22f, 35, Sex.MALE)
        // male, 35 y: literature share 0.08 + 17 * 0.0022 = 0.1174, x 0.71 calibration
        assertEquals(22f * (1 - 0.1174f * 0.71f), split.subcutaneousPercent, 0.01f)
    }

    @Test
    fun `typical adults get plausible visceral ratings`() {
        val fitMan = BodyCompositionCalculator.fatDistribution(80f, 18f, 30, Sex.MALE).visceralRating
        val obeseMan = BodyCompositionCalculator.fatDistribution(115f, 35f, 55, Sex.MALE).visceralRating
        val woman = BodyCompositionCalculator.fatDistribution(65f, 30f, 30, Sex.FEMALE).visceralRating
        assertTrue("fit man $fitMan", fitMan in 3..9)
        assertTrue("obese man $obeseMan", obeseMan >= 15)
        assertTrue("woman $woman", woman in 1..9)
    }

    @Test
    fun `visceral rating increases with age and is clamped`() {
        val young = BodyCompositionCalculator.fatDistribution(90f, 28f, 25, Sex.MALE).visceralRating
        val old = BodyCompositionCalculator.fatDistribution(90f, 28f, 65, Sex.MALE).visceralRating
        assertTrue(old > young)
        val extreme = BodyCompositionCalculator.fatDistribution(250f, 60f, 90, Sex.MALE).visceralRating
        assertEquals(30, extreme)
    }

    @Test
    fun `calculator fills both values only with impedance`() {
        val withImpedance = BodyCompositionCalculator.calculate(80f, 180f, 35, Sex.MALE, 500)
        assertTrue(withImpedance.visceralFatRating != null && withImpedance.subcutaneousFatPercent != null)
        val without = BodyCompositionCalculator.calculate(80f, 180f, 35, Sex.MALE, null)
        assertNull(without.visceralFatRating)
        assertNull(without.subcutaneousFatPercent)
    }
}
