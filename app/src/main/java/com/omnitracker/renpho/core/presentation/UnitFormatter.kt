package com.omnitracker.renpho.core.presentation

import com.omnitracker.renpho.domain.model.HeightUnit
import com.omnitracker.renpho.domain.model.WeightUnit
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

object UnitFormatter {
    const val KG_PER_LB = 0.45359237f
    private const val LB_PER_STONE = 14f

    fun unitLabel(unit: WeightUnit): String = when (unit) {
        WeightUnit.KG -> "kg"
        WeightUnit.LB -> "lb"
        WeightUnit.ST -> "st"
    }

    /** Converts kg into the numeric value shown for [unit] (stones as decimal). */
    fun toDisplay(kg: Float, unit: WeightUnit): Float = when (unit) {
        WeightUnit.KG -> kg
        WeightUnit.LB -> kg / KG_PER_LB
        WeightUnit.ST -> kg / KG_PER_LB / LB_PER_STONE
    }

    fun fromDisplay(value: Float, unit: WeightUnit): Float = when (unit) {
        WeightUnit.KG -> value
        WeightUnit.LB -> value * KG_PER_LB
        WeightUnit.ST -> value * LB_PER_STONE * KG_PER_LB
    }

    /** Body weight: "72.4 kg", "159.6 lb", "11 st 5.6 lb". */
    fun weight(kg: Float, unit: WeightUnit): String = when (unit) {
        WeightUnit.ST -> {
            val totalLb = kg / KG_PER_LB
            val stones = floor(totalLb / LB_PER_STONE).toInt()
            val lb = totalLb - stones * LB_PER_STONE
            "$stones st ${oneDecimal(lb)} lb"
        }
        else -> "${oneDecimal(toDisplay(kg, unit))} ${unitLabel(unit)}"
    }

    /** Body component masses (muscle, bone, ...); stones are shown as pounds. */
    fun mass(kg: Float, unit: WeightUnit): String =
        if (unit == WeightUnit.KG) "${oneDecimal(kg)} kg" else "${oneDecimal(kg / KG_PER_LB)} lb"

    /** Signed difference, e.g. "+0.4 kg" / "−1.2 lb". */
    fun weightDelta(deltaKg: Float, unit: WeightUnit): String {
        val unitForDelta = if (unit == WeightUnit.ST) WeightUnit.LB else unit
        val v = toDisplay(deltaKg, unitForDelta)
        val sign = when {
            v > 0.05f -> "+"
            v < -0.05f -> "−"
            else -> "±"
        }
        return "$sign${oneDecimal(abs(v))} ${unitLabel(unitForDelta)}"
    }

    fun height(cm: Float, unit: HeightUnit): String = when (unit) {
        HeightUnit.CM -> "${cm.roundToInt()} cm"
        HeightUnit.FT_IN -> {
            val (ft, inch) = cmToFeetInches(cm)
            "$ft′ $inch″"
        }
    }

    fun cmToFeetInches(cm: Float): Pair<Int, Int> {
        val totalInches = (cm / 2.54f).roundToInt()
        return totalInches / 12 to totalInches % 12
    }

    fun feetInchesToCm(feet: Int, inches: Int): Float = (feet * 12 + inches) * 2.54f

    fun oneDecimal(value: Float): String = String.format(Locale.getDefault(), "%.1f", value)

    private val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    private val dateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    private val shortDateFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

    fun date(instant: Instant): String = dateFormatter.format(instant.atZone(ZoneId.systemDefault()))
    fun dateTime(instant: Instant): String = dateTimeFormatter.format(instant.atZone(ZoneId.systemDefault()))
    fun shortDate(instant: Instant): String = shortDateFormatter.format(instant.atZone(ZoneId.systemDefault()))
}
