package com.notify2discord.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.notify2discord.app.data.BatterySnapshot
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun BatteryHealthLineChart(
    snapshots: List<BatterySnapshot>,
    modifier: Modifier = Modifier
) {
    val points = remember(snapshots) {
        snapshots
            .sortedBy { it.capturedAt }
            .mapNotNull { snapshot ->
                val healthPercent = snapshot.estimatedHealthByDesignPercent ?: snapshot.estimatedHealthPercent
                healthPercent?.let { snapshot.capturedAt to it.coerceIn(0f, 100f) }
            }
    }
    val primaryColor = MaterialTheme.colorScheme.primary
    val axisLabels = listOf(100, 75, 50, 25, 0)
    val isWideScreen = LocalConfiguration.current.screenWidthDp >= 600
    val timeLabelFormatter = DateTimeFormatter.ofPattern("M/d HH:mm")

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(248.dp)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(14.dp)
                )
                .padding(horizontal = 8.dp, vertical = 10.dp)
        ) {
            if (points.isEmpty()) {
                Text(
                    text = "グラフを表示するには履歴データが不足しています",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .height(190.dp)
                            .padding(end = 6.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.End
                    ) {
                        axisLabels.forEach { value ->
                            Text(
                                text = "$value%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.End
                            )
                        }
                    }
                    Canvas(
                        modifier = Modifier
                            .weight(1f)
                            .height(190.dp)
                    ) {
                        val chartWidth = size.width
                        val chartHeight = size.height
                        val startTimestamp = points.first().first
                        val endTimestamp = points.last().first
                        val timeRange = (endTimestamp - startTimestamp).coerceAtLeast(1L)

                        axisLabels.forEach { value ->
                            val normalized = value / 100f
                            val y = chartHeight - chartHeight * normalized
                            drawLine(
                                color = primaryColor.copy(alpha = 0.18f),
                                start = Offset(0f, y),
                                end = Offset(chartWidth, y),
                                strokeWidth = 1f
                            )
                        }

                        if (points.size > 1) {
                            val path = Path()
                            points.forEachIndexed { index, (timestamp, value) ->
                                val x = ((timestamp - startTimestamp) / timeRange.toFloat()) * chartWidth
                                val y = chartHeight - (value / 100f * chartHeight)
                                if (index == 0) {
                                    path.moveTo(x, y)
                                } else {
                                    path.lineTo(x, y)
                                }
                            }
                            drawPath(
                                path = path,
                                color = primaryColor,
                                style = Stroke(width = 3f, cap = StrokeCap.Round)
                            )
                        }

                        points.forEachIndexed { index, (timestamp, value) ->
                            val x = if (points.size == 1) {
                                chartWidth / 2f
                            } else {
                                ((timestamp - startTimestamp) / timeRange.toFloat()) * chartWidth
                            }
                            val y = chartHeight - (value / 100f * chartHeight)
                            drawCircle(
                                color = primaryColor,
                                radius = if (index == points.lastIndex) 5f else 4f,
                                center = Offset(x, y)
                            )
                        }
                    }
                }
            }
        }

        if (points.isNotEmpty()) {
            val startLabel = Instant.ofEpochMilli(points.first().first)
                .atZone(ZoneId.systemDefault())
                .format(timeLabelFormatter)
            val middleLabel = Instant.ofEpochMilli(points[points.size / 2].first)
                .atZone(ZoneId.systemDefault())
                .format(timeLabelFormatter)
            val endLabel = Instant.ofEpochMilli(points.last().first)
                .atZone(ZoneId.systemDefault())
                .format(timeLabelFormatter)

            Text(
                text = "横軸: 日時 / 縦軸: バッテリー健全度(%)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isWideScreen) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = startLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = middleLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = endLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = startLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = endLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
