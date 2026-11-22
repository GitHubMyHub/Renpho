package com.omnitracker.renpho.domain

import com.omnitracker.renpho.domain.model.Measurement
import com.omnitracker.renpho.domain.model.MeasurementSource
import com.omnitracker.renpho.domain.model.UserProfile
import java.time.Instant
import java.time.Period
import java.time.ZoneId

/** Builds a [Measurement] and computes its body composition for the given profile. */
object MeasurementFactory {

    fun create(
        profile: UserProfile,
        weightKg: Float,
        impedanceOhm: Int?,
        source: MeasurementSource,
        timestamp: Instant = Instant.now(),
        deviceBodyFatPercent: Float? = null,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Measurement {
        val age = ageAt(profile, timestamp, zone)
        val composition = BodyCompositionCalculator.calculate(
            weightKg = weightKg,
            heightCm = profile.heightCm,
            ageYears = age,
            sex = profile.sex,
            impedanceOhm = impedanceOhm,
            deviceBodyFatPercent = deviceBodyFatPercent,
        )
        return Measurement(
            timestamp = timestamp,
            weightKg = weightKg,
            impedanceOhm = impedanceOhm,
            source = source,
            composition = composition,
        )
    }

    fun ageAt(profile: UserProfile, timestamp: Instant, zone: ZoneId = ZoneId.systemDefault()): Int =
        Period.between(profile.birthDate, timestamp.atZone(zone).toLocalDate()).years.coerceAtLeast(0)
}
