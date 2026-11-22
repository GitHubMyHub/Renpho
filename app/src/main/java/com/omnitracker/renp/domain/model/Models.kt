package com.omnitracker.renp.domain.model

import java.time.Instant
import java.time.LocalDate

enum class Sex { MALE, FEMALE }

enum class WeightUnit { KG, LB, ST }

enum class HeightUnit { CM, FT_IN }

data class UserProfile(
    val name: String,
    val sex: Sex,
    val birthDate: LocalDate,
    val heightCm: Float,
    val goalWeightKg: Float?,
)

data class PairedScale(
    val address: String,
    val name: String,
)

data class AppSettings(
    val weightUnit: WeightUnit = WeightUnit.KG,
    val heightUnit: HeightUnit = HeightUnit.CM,
    val pairedScale: PairedScale? = null,
)

enum class MeasurementSource { SCALE, MANUAL }

/**
 * Body composition derived from weight (+ bio-impedance when available).
 * Every field except [bmi] is null when the measurement had no impedance
 * (e.g. measured with socks/shoes, or entered manually).
 */
data class BodyComposition(
    val bmi: Float,
    val bodyFatPercent: Float? = null,
    /** Estimated visceral fat rating, 1..30 (lower is better). */
    val visceralFatRating: Int? = null,
    /** Estimated subcutaneous fat as % of body weight. */
    val subcutaneousFatPercent: Float? = null,
    val fatFreeMassKg: Float? = null,
    val waterPercent: Float? = null,
    val muscleMassKg: Float? = null,
    val skeletalMusclePercent: Float? = null,
    val boneMassKg: Float? = null,
    val proteinPercent: Float? = null,
    val bmrKcal: Int? = null,
    val metabolicAge: Int? = null,
) {
    val hasBodyComposition: Boolean get() = bodyFatPercent != null
}

data class Measurement(
    val id: Long = 0,
    val timestamp: Instant,
    val weightKg: Float,
    val impedanceOhm: Int?,
    val source: MeasurementSource,
    val composition: BodyComposition,
)
