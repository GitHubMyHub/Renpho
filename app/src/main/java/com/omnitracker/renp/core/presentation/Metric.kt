package com.omnitracker.renp.core.presentation

import androidx.annotation.StringRes
import com.omnitracker.renp.R
import com.omnitracker.renp.domain.BodyCompositionCalculator
import com.omnitracker.renp.domain.model.Measurement
import com.omnitracker.renp.domain.model.Sex
import com.omnitracker.renp.domain.model.WeightUnit

enum class Tone { GOOD, WARN, BAD }

enum class MetricStatus(@StringRes val label: Int, val tone: Tone) {
    LOW(R.string.status_low, Tone.WARN),
    NORMAL(R.string.status_normal, Tone.GOOD),
    HIGH(R.string.status_high, Tone.WARN),
    VERY_HIGH(R.string.status_very_high, Tone.BAD),
    EXCELLENT(R.string.status_excellent, Tone.GOOD),
}

/** The metrics shown on dashboard, history and detail screens. */
enum class Metric(@StringRes val label: Int) {
    WEIGHT(R.string.metric_weight),
    BMI(R.string.metric_bmi),
    BODY_FAT(R.string.metric_body_fat),
    VISCERAL_FAT(R.string.metric_visceral_fat),
    SUBCUTANEOUS_FAT(R.string.metric_subcutaneous_fat),
    WATER(R.string.metric_water),
    MUSCLE_MASS(R.string.metric_muscle_mass),
    SKELETAL_MUSCLE(R.string.metric_skeletal_muscle),
    FAT_FREE_MASS(R.string.metric_fat_free_mass),
    BONE_MASS(R.string.metric_bone_mass),
    PROTEIN(R.string.metric_protein),
    BMR(R.string.metric_bmr),
    METABOLIC_AGE(R.string.metric_metabolic_age);

    val isMass: Boolean get() = this == WEIGHT || this == MUSCLE_MASS || this == FAT_FREE_MASS || this == BONE_MASS

    /** Raw value in canonical units (kg, %, kcal, years). */
    fun valueOf(m: Measurement): Float? = with(m.composition) {
        when (this@Metric) {
            WEIGHT -> m.weightKg
            BMI -> bmi
            BODY_FAT -> bodyFatPercent
            VISCERAL_FAT -> visceralFatRating?.toFloat()
            SUBCUTANEOUS_FAT -> subcutaneousFatPercent
            WATER -> waterPercent
            MUSCLE_MASS -> muscleMassKg
            SKELETAL_MUSCLE -> skeletalMusclePercent
            FAT_FREE_MASS -> fatFreeMassKg
            BONE_MASS -> boneMassKg
            PROTEIN -> proteinPercent
            BMR -> bmrKcal?.toFloat()
            METABOLIC_AGE -> metabolicAge?.toFloat()
        }
    }

    /** Numeric value in the user's display unit (used by charts). */
    fun displayValue(m: Measurement, unit: WeightUnit): Float? {
        val v = valueOf(m) ?: return null
        return when {
            this == WEIGHT -> UnitFormatter.toDisplay(v, unit)
            isMass -> if (unit == WeightUnit.KG) v else v / UnitFormatter.KG_PER_LB
            else -> v
        }
    }

    /** Unit suffix for chart axes; null for unit-less metrics. */
    fun displayUnit(unit: WeightUnit): String? = when {
        this == WEIGHT -> UnitFormatter.unitLabel(unit)
        isMass -> if (unit == WeightUnit.KG) "kg" else "lb"
        this == BMI || this == METABOLIC_AGE || this == VISCERAL_FAT -> null
        this == BMR -> "kcal"
        else -> "%"
    }

    fun format(m: Measurement, unit: WeightUnit): String? {
        val v = valueOf(m) ?: return null
        return when (this) {
            WEIGHT -> UnitFormatter.weight(v, unit)
            MUSCLE_MASS, FAT_FREE_MASS, BONE_MASS -> UnitFormatter.mass(v, unit)
            BMI -> UnitFormatter.oneDecimal(v)
            BODY_FAT, SUBCUTANEOUS_FAT, WATER, SKELETAL_MUSCLE, PROTEIN -> "${UnitFormatter.oneDecimal(v)} %"
            VISCERAL_FAT -> v.toInt().toString()
            BMR -> "${v.toInt()} kcal"
            METABOLIC_AGE -> v.toInt().toString()
        }
    }

    /** Health classification; null when no reference range applies. */
    fun status(m: Measurement, sex: Sex, age: Int): MetricStatus? {
        val v = valueOf(m) ?: return null
        val male = sex == Sex.MALE
        return when (this) {
            BMI -> when {
                v < 18.5f -> MetricStatus.LOW
                v < 25f -> MetricStatus.NORMAL
                v < 30f -> MetricStatus.HIGH
                else -> MetricStatus.VERY_HIGH
            }
            // Gallagher et al. 2000 (simplified to two age bands)
            BODY_FAT -> {
                val older = age >= 40
                val (low, normal, high) = if (male) {
                    if (older) Triple(11f, 22f, 28f) else Triple(8f, 20f, 25f)
                } else {
                    if (older) Triple(23f, 34f, 40f) else Triple(21f, 33f, 39f)
                }
                when {
                    v < low -> MetricStatus.LOW
                    v < normal -> MetricStatus.NORMAL
                    v < high -> MetricStatus.HIGH
                    else -> MetricStatus.VERY_HIGH
                }
            }
            VISCERAL_FAT -> when {
                v < 10f -> MetricStatus.NORMAL
                v < 15f -> MetricStatus.HIGH
                else -> MetricStatus.VERY_HIGH
            }
            SUBCUTANEOUS_FAT -> {
                val (low, high) = if (male) 8f to 17f else 18f to 27f
                when {
                    v < low -> MetricStatus.LOW
                    v <= high -> MetricStatus.NORMAL
                    else -> MetricStatus.HIGH
                }
            }
            WATER -> {
                val (low, high) = if (male) 50f to 65f else 45f to 60f
                when {
                    v < low -> MetricStatus.LOW
                    v <= high -> MetricStatus.NORMAL
                    else -> MetricStatus.HIGH
                }
            }
            SKELETAL_MUSCLE -> {
                // Janssen reference ranges, scaled like the calibrated values
                val k = BodyCompositionCalculator.SKELETAL_MUSCLE_CALIBRATION.toFloat()
                val (low, high) = if (male) 33f * k to 39f * k else 24f * k to 30f * k
                when {
                    v < low -> MetricStatus.LOW
                    v <= high -> MetricStatus.NORMAL
                    else -> MetricStatus.EXCELLENT
                }
            }
            PROTEIN -> when {
                v < 16f -> MetricStatus.LOW
                v <= 20f -> MetricStatus.NORMAL
                else -> MetricStatus.EXCELLENT
            }
            METABOLIC_AGE -> if (v <= age) MetricStatus.EXCELLENT else MetricStatus.HIGH
            else -> null
        }
    }
}
