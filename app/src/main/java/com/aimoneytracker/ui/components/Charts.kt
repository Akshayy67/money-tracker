package com.aimoneytracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

/** A single series point for the projection chart. */
data class ChartPoint(val x: Float, val y: Float)

/**
 * Balance projection chart (§15): a solid actual-to-date line, a dashed forecast line, and a shaded
 * confidence band. All values are pre-scaled by the caller into [0..1] within the drawing area is
 * handled internally from raw values.
 */
@Composable
fun ProjectionLineChart(
    actual: List<ChartPoint>,
    forecast: List<ChartPoint>,
    bandLow: List<ChartPoint>,
    bandHigh: List<ChartPoint>,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val bandColor = primary.copy(alpha = 0.15f)
    val all = actual + forecast + bandLow + bandHigh
    if (all.isEmpty()) {
        Box(modifier.height(180.dp)) { Text("No data yet", Modifier.align(Alignment.Center)) }
        return
    }
    val minX = all.minOf { it.x }
    val maxX = max(all.maxOf { it.x }, minX + 1f)
    val minY = min(0f, all.minOf { it.y })
    val maxY = max(all.maxOf { it.y }, minY + 1f)

    Canvas(modifier = modifier.fillMaxWidth().height(180.dp).padding(8.dp)) {
        val w = size.width
        val h = size.height
        fun sx(x: Float) = (x - minX) / (maxX - minX) * w
        fun sy(y: Float) = h - (y - minY) / (maxY - minY) * h

        // Confidence band as a filled path (high forward, low backward).
        if (bandHigh.isNotEmpty() && bandLow.isNotEmpty()) {
            val path = Path()
            bandHigh.forEachIndexed { i, p ->
                if (i == 0) path.moveTo(sx(p.x), sy(p.y)) else path.lineTo(sx(p.x), sy(p.y))
            }
            bandLow.reversed().forEach { p -> path.lineTo(sx(p.x), sy(p.y)) }
            path.close()
            drawPath(path, color = bandColor)
        }

        // Actual line (solid).
        if (actual.size >= 2) {
            val path = Path()
            actual.forEachIndexed { i, p ->
                if (i == 0) path.moveTo(sx(p.x), sy(p.y)) else path.lineTo(sx(p.x), sy(p.y))
            }
            drawPath(path, color = primary, style = Stroke(width = 5f))
        }

        // Forecast line (dashed).
        if (forecast.size >= 2) {
            val path = Path()
            forecast.forEachIndexed { i, p ->
                if (i == 0) path.moveTo(sx(p.x), sy(p.y)) else path.lineTo(sx(p.x), sy(p.y))
            }
            drawPath(
                path, color = primary,
                style = Stroke(width = 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f))),
            )
        }

        // Zero baseline.
        drawLine(Color.Gray.copy(alpha = 0.4f), Offset(0f, sy(0f)), Offset(w, sy(0f)), strokeWidth = 1.5f)
    }
}

data class DonutSlice(val label: String, val value: Long, val color: Color)

/** A donut chart with a legend, used for category breakdown (§14). */
@Composable
fun DonutChart(slices: List<DonutSlice>, modifier: Modifier = Modifier) {
    val total = slices.sumOf { it.value }.coerceAtLeast(1)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(140.dp).padding(8.dp)) {
            var startAngle = -90f
            val stroke = Stroke(width = size.minDimension * 0.22f)
            slices.forEach { slice ->
                val sweep = slice.value.toFloat() / total * 360f
                drawArc(
                    color = slice.color,
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = stroke,
                )
                startAngle += sweep
            }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            slices.take(6).forEach { slice ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(slice.color))
                    Spacer(Modifier.width(6.dp))
                    Text(slice.label, style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.width(6.dp))
                    val pct = (slice.value * 100 / total)
                    Text("$pct%", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
            }
        }
    }
}

/** A simple vertical bar chart (e.g. monthly trend). */
@Composable
fun BarChart(values: List<Pair<String, Long>>, modifier: Modifier = Modifier) {
    val maxV = (values.maxOfOrNull { it.second } ?: 1L).coerceAtLeast(1)
    val color = MaterialTheme.colorScheme.primary
    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(140.dp).padding(8.dp)) {
            val barWidth = size.width / (values.size * 1.5f).coerceAtLeast(1f)
            values.forEachIndexed { i, (_, v) ->
                val barHeight = v.toFloat() / maxV * size.height
                val x = i * barWidth * 1.5f
                drawRect(
                    color = color,
                    topLeft = Offset(x, size.height - barHeight),
                    size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            values.forEach { Text(it.first, style = MaterialTheme.typography.labelSmall, color = Color.Gray) }
        }
    }
}
