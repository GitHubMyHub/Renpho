package com.omnitracker.renp.data.local

import com.omnitracker.renp.domain.BodyCompositionCalculator
import com.omnitracker.renp.domain.MeasurementFactory
import com.omnitracker.renp.domain.UserPreferences
import com.omnitracker.renp.domain.model.MeasurementSource
import kotlinx.coroutines.flow.first
import java.time.Instant

/**
 * Re-derives the body composition of all stored measurements from their raw
 * data (weight + impedance) whenever [BodyCompositionCalculator.CALC_VERSION]
 * changes, so history stays consistent with the current formulas.
 */
class MeasurementRecalculator(
    private val dao: MeasurementDao,
    private val preferences: UserPreferences,
) {
    suspend fun runIfNeeded() {
        if (preferences.getCalcVersion() >= BodyCompositionCalculator.CALC_VERSION) return
        val profile = preferences.profile.first() ?: return
        val updated = dao.getAll().map { e ->
            val m = e.toMeasurement()
            // Standard (BCS) scales without impedance report body fat themselves
            val deviceBodyFat = if (m.impedanceOhm == null && m.source == MeasurementSource.SCALE) {
                m.composition.bodyFatPercent
            } else null
            MeasurementFactory.create(
                profile = profile,
                weightKg = m.weightKg,
                impedanceOhm = m.impedanceOhm,
                source = m.source,
                timestamp = Instant.ofEpochMilli(e.timestampMillis),
                deviceBodyFatPercent = deviceBodyFat,
            ).copy(id = e.id).toEntity()
        }
        dao.update(updated)
        preferences.setCalcVersion(BodyCompositionCalculator.CALC_VERSION)
    }
}
