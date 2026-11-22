package com.omnitracker.renpho.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.omnitracker.renpho.ui.theme.RenphoTheme
import kotlin.math.max

@Immutable
data class ChartPoint(val epochMillis: Long, val value: Float)

/**
 * Simple time-based line chart. Points must be sorted by time (oldest first).
 */
@Composable
fun TrendChart(
    points: List<ChartPoint>,
    modifier: Modifier = Modifier,
    goal: Float? = null,
    startLabel: String = "",
    endLabel: String = "",
    formatValue: (Float) -> String = { "%.1f".format(it) },
    contentDescription: String = "",
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val goalColor = MaterialTheme.colorScheme.tertiary
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val textMeasurer = rememberTextMeasurer()

    val bounds = remember(points, goal) {
        val values = points.map { it.value } + listOfNotNull(goal)
        if (values.isEmpty()) null else {
            var min = values.min()
            var max = values.max()
            val pad = max((max - min) * 0.15f, 0.5f)
            min -= pad
            max += pad
            min to max
        }
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(200.dp)
            .semantics { this.contentDescription = contentDescription },
    ) {
        if (points.isEmpty() || bounds == null) return@Canvas
        val (minV, maxV) = bounds
        val leftPad = 44.dp.toPx()
        val bottomPad = 20.dp.toPx()
        val topPad = 8.dp.toPx()
        val rightPad = 8.dp.toPx()
        val chartW = size.width - leftPad - rightPad
        val chartH = size.height - topPad - bottomPad

        val t0 = points.first().epochMillis
        val t1 = points.last().epochMillis
        val span = (t1 - t0).coerceAtLeast(1L).toFloat()

        fun x(t: Long) = if (points.size == 1) leftPad + chartW / 2 else leftPad + (t - t0) / span * chartW
        fun y(v: Float) = topPad + (1f - (v - minV) / (maxV - minV)) * chartH

        // Horizontal grid + labels
        for (i in 0..3) {
            val v = minV + (maxV - minV) * i / 3f
            val yy = y(v)
            drawLine(gridColor, Offset(leftPad, yy), Offset(size.width - rightPad, yy), strokeWidth = 1f)
            val layout = textMeasurer.measure(formatValue(v), labelStyle)
            drawText(layout, topLeft = Offset(leftPad - layout.size.width - 6.dp.toPx(), yy - layout.size.height / 2))
        }

        // Goal line
        goal?.let {
            val gy = y(it)
            drawLine(
                goalColor, Offset(leftPad, gy), Offset(size.width - rightPad, gy),
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)),
            )
        }

        val offsets = points.map { Offset(x(it.epochMillis), y(it.value)) }
        if (offsets.size > 1) {
            val line = Path().apply {
                moveTo(offsets.first().x, offsets.first().y)
                offsets.drop(1).forEach { lineTo(it.x, it.y) }
            }
            val fill = Path().apply {
                addPath(line)
                lineTo(offsets.last().x, topPad + chartH)
                lineTo(offsets.first().x, topPad + chartH)
                close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.25f), lineColor.copy(alpha = 0f))))
            drawPath(
                line, lineColor,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
        if (offsets.size <= 60) {
            offsets.forEach { drawCircle(lineColor, radius = 3.5.dp.toPx(), center = it) }
        }

        // X labels
        if (startLabel.isNotEmpty()) {
            val l = textMeasurer.measure(startLabel, labelStyle)
            drawText(l, topLeft = Offset(leftPad, size.height - l.size.height))
        }
        if (endLabel.isNotEmpty() && points.size > 1) {
            val l = textMeasurer.measure(endLabel, labelStyle)
            drawText(l, topLeft = Offset(size.width - rightPad - l.size.width, size.height - l.size.height))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TrendChartPreview() {
    RenphoTheme {
        TrendChart(
            points = listOf(80f, 79.6f, 79.9f, 79.1f, 78.7f, 78.9f, 78.2f)
                .mapIndexed { i, v -> ChartPoint(i * 86_400_000L, v) },
            goal = 76f,
            startLabel = "1 Nov",
            endLabel = "7 Nov",
        )
    }
}
