package com.omnitracker.renp.core.presentation

import com.omnitracker.renp.domain.MeasurementFactory
import com.omnitracker.renp.domain.model.Measurement
import com.omnitracker.renp.domain.model.UserProfile
import com.omnitracker.renp.domain.model.WeightUnit
import com.omnitracker.renp.ui.components.MetricValueUi

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
