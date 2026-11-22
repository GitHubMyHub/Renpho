package com.omnitracker.renpho.core.presentation

import com.omnitracker.renpho.domain.MeasurementFactory
import com.omnitracker.renpho.domain.model.Measurement
import com.omnitracker.renpho.domain.model.UserProfile
import com.omnitracker.renpho.domain.model.WeightUnit
import com.omnitracker.renpho.ui.components.MetricValueUi

fun Measurement.toMetricValues(
    profile: UserProfile?,
    unit: WeightUnit,
    metrics: List<Metric> = Metric.entries,
): List<MetricValueUi> {
    val age = profile?.let { MeasurementFactory.ageAt(it, timestamp) }
    return metrics.map { metric ->
        MetricValueUi(
            label = metric.label,
            value = metric.format(this, unit),
            status = if (profile != null && age != null) metric.status(this, profile.sex, age) else null,
        )
    }
}
