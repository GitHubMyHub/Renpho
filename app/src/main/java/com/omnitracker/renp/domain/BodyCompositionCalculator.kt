package com.omnitracker.renp.domain

import com.omnitracker.renp.domain.model.BodyComposition
import com.omnitracker.renp.domain.model.Sex
import kotlin.math.roundToInt

/**
 * Body composition estimation from weight, height, age, sex and foot-to-foot
 * bio-impedance.
 *
 * The original app delegates this to a proprietary native library
 * (libyolanda_calc.so) that cannot be ported. This class starts from published
 * equations and calibrates them against values shown by the Renpho app:
 *
 *  - Fat-free mass:          Sun et al. 2003 (Am J Clin Nutr 77:331) x [FFM_CALIBRATION]
 *  - Body water, bone:       fixed fractions of fat-free mass (as the Renpho app does)
 *  - Muscle mass:            fat-free mass - bone
 *  - Protein:                (fat-free mass - water - bone) / weight
 *  - Skeletal muscle:        Janssen et al. 2000 (J Appl Physiol 89:465) x [SKELETAL_MUSCLE_CALIBRATION]
 *  - BMR:                    Katch–McArdle, 370 + 21.6 x fat-free mass
 *  - Metabolic age:          age at which the Mifflin–St Jeor population BMR equals the user's BMR
 *                            (NOT calibrated - Renpho uses a different, unknown model)
 *  - Visceral/subcutaneous:  fat mass split by an age- and sex-dependent visceral share
 *
 * Calibration reference (one weigh-in, male, 37 y, 170 cm, 61.1 kg, ~496 Ω):
 * Renpho showed body fat 18.5 %, fat-free 49.8 kg, water 58.8 %, skeletal muscle 52.7 %,
 * muscle 47.3 kg, bone 2.49 kg, protein 18.6 %, BMR 1445 kcal, subcutaneous 16.9 %, visceral 4.
 * More paired weigh-ins (different weights, female users) would make these factors more reliable.
 */
object BodyCompositionCalculator {

    /**
     * Bump whenever the formulas change; stored measurements are then recalculated
     * (see MeasurementRecalculator).
     */
    const val CALC_VERSION = 2

    /** Sun 2003 FFM (hand-to-foot) -> Renpho FFM (foot-to-foot): 49.8 / 53.0 kg. */
    const val FFM_CALIBRATION = 0.9396

    /** Janssen 2000 SMM -> Renpho skeletal muscle: 52.7 % / 48.55 %. */
    const val SKELETAL_MUSCLE_CALIBRATION = 1.0855

    /** Water as fraction of fat-free mass (Renpho: 35.9 kg / 49.8 kg). */
    const val HYDRATION_FRACTION = 0.7214

    /** Bone mass as fraction of fat-free mass (Renpho: 2.49 kg / 49.8 kg). */
    const val BONE_FRACTION = 0.05

    /** Visceral fat mass (kg) per rating point (Renpho: ~0.98 kg -> 4). */
    private const val KG_PER_VISCERAL_POINT = 0.25

    /** Scales the literature visceral share to Renpho (8.65 % vs 12.2 % at 37 y, male). */
    private const val VISCERAL_SHARE_CALIBRATION = 0.71

    /** Impedance outside this range is treated as "no contact" (socks, shoes, ...). */
    val VALID_IMPEDANCE_RANGE = 150..1500

    data class FatDistribution(val visceralRating: Int, val subcutaneousPercent: Float)

    /**
     * Splits total fat into visceral and subcutaneous parts.
     *
     * The visceral share of total fat grows with age and is roughly twice as high
     * in men as in women (MRI studies); scaled by [VISCERAL_SHARE_CALIBRATION].
     * The rating maps visceral fat mass onto the 1..30 scale used by scales
     * (1-9 healthy, 10-14 high, 15+ very high).
     */
    fun fatDistribution(weightKg: Float, bodyFatPercent: Float, ageYears: Int, sex: Sex): FatDistribution {
        val age = ageYears.coerceIn(18, 80) - 18
        val literatureShare = if (sex == Sex.MALE) {
            (0.08 + age * 0.0022).coerceAtMost(0.20)
        } else {
            (0.04 + age * 0.0015).coerceAtMost(0.12)
        }
        val visceralShare = literatureShare * VISCERAL_SHARE_CALIBRATION
        val fatMass = weightKg * bodyFatPercent / 100.0
        val visceralKg = fatMass * visceralShare
        val rating = (visceralKg / KG_PER_VISCERAL_POINT).roundToInt().coerceIn(1, 30)
        val subcutaneous = (bodyFatPercent * (1.0 - visceralShare)).toFloat()
        return FatDistribution(rating, subcutaneous)
    }

    fun calculate(
        weightKg: Float,
        heightCm: Float,
        ageYears: Int,
        sex: Sex,
        impedanceOhm: Int?,
        deviceBodyFatPercent: Float? = null,
    ): BodyComposition {
        require(weightKg > 0f && heightCm > 0f) { "weight and height must be positive" }
        val w = weightKg.toDouble()
        val h = heightCm.toDouble()
        val male = sex == Sex.MALE
        val heightM = h / 100.0
        val bmi = (w / (heightM * heightM)).toFloat()

        val r = impedanceOhm?.takeIf { it in VALID_IMPEDANCE_RANGE }?.toDouble()
        val impedanceIndex = r?.let { h * h / it }

        val rawFfm: Double = when {
            r != null -> {
                val index = h * h / r
                FFM_CALIBRATION *
                    if (male) -10.68 + 0.65 * index + 0.26 * w + 0.02 * r
                    else -9.53 + 0.69 * index + 0.17 * w + 0.02 * r
            }
            deviceBodyFatPercent != null && deviceBodyFatPercent in 1f..75f ->
                w * (1.0 - deviceBodyFatPercent / 100.0)
            else -> return BodyComposition(bmi = bmi)
        }
        // Keep body fat within a physiologically plausible 3 % .. 60 %
        val ffm = rawFfm.coerceIn(w * 0.40, w * 0.97)
        val bodyFatPercent = (w - ffm) / w * 100.0

        val tbw = ffm * HYDRATION_FRACTION
        val bone = ffm * BONE_FRACTION
        val muscle = ffm - bone

        val skeletalMuscle = if (impedanceIndex != null) {
            (0.401 * impedanceIndex + (if (male) 3.825 else 0.0) - 0.071 * ageYears + 5.102) *
                SKELETAL_MUSCLE_CALIBRATION
        } else {
            ffm * if (male) 0.56 else 0.52
        }.coerceIn(0.0, muscle)

        val protein = ((ffm - tbw - bone) / w * 100.0).coerceIn(5.0, 25.0)

        val bmr = 370.0 + 21.6 * ffm
        val mifflinWithoutAge = 10.0 * w + 6.25 * h + if (male) 5.0 else -161.0
        val metabolicAge = ((mifflinWithoutAge - bmr) / 5.0).roundToInt().coerceIn(12, 90)

        val fatSplit = fatDistribution(weightKg, bodyFatPercent.toFloat(), ageYears, sex)

        return BodyComposition(
            bmi = bmi,
            bodyFatPercent = bodyFatPercent.toFloat(),
            visceralFatRating = fatSplit.visceralRating,
            subcutaneousFatPercent = fatSplit.subcutaneousPercent,
            fatFreeMassKg = ffm.toFloat(),
            waterPercent = (tbw / w * 100.0).toFloat(),
            muscleMassKg = muscle.toFloat(),
            skeletalMusclePercent = (skeletalMuscle / w * 100.0).toFloat(),
            boneMassKg = bone.toFloat(),
            proteinPercent = protein.toFloat(),
            // Renpho truncates (1445.7 -> 1445)
            bmrKcal = bmr.toInt(),
            metabolicAge = metabolicAge,
        )
    }
}
