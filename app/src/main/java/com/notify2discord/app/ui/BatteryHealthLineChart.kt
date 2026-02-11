package com.notify2discord.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
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
    val points = snapshots
        .sortedBy { it.capturedAt }
        .mapNotNull { snapshot ->
            snapshot.estimatedHealthPercent?.let { health -> snapshot.capturedAt to health }
        }

    val dateFormatter = DateTimeFormatter.ofPattern("M/d")
    val values = points.map { it.second }
    val minValue = values.minOrNull()
    val maxValue = values.maxOrNull()
    val primaryColor = MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(14.dp)
                )
                .padding(12.dp)
        ) {
            if (points.size < 2 || minValue == null || maxValue == null) {
                Text(
                    text = "グラフを表示するには履歴データが不足しています",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                Canvas(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                    val width = size.width
                    val height = size.height
                    val xStep = if (points.size <= 1) width else width / (points.lastIndex.coerceAtLeast(1))
                    val range = (maxValue - minValue).takeIf { it > 0.01f } ?: 1f

                    // 背景グリッド
                    repeat(4) { index ->
                        val y = height * index / 3f
                        drawLine(
                            color = primaryColor.copy(alpha = 0.16f),
                            start = Offset(0f, y),
                            end = Offset(width, y),
                            strokeWidth = 1f
                        )
                    }

                    // 時系列を折れ線として描画する
                    val path = Path()
                    points.forEachIndexed { index, point ->
                        val normalized = ((point.second - minValue) / range).coerceIn(0f, 1f)
                        val x = xStep * index
                        val y = height - (height * normalized)
                        if (index == 0) {
                            path.moveTo(x, y)
                        } else {
                            path.lineTo(x, y)
                        }
                    }

                    drawPath(
                        path = path,
                        color = primaryColor,
                        style = Stroke(width = 4f, cap = StrokeCap.Round)
                    )
                }
            }
        }

        if (points.isNotEmpty()) {
            val start = Instant.ofEpochMilli(points.first().first)
                .atZone(ZoneId.systemDefault())
                .format(dateFormatter)
            val end = Instant.ofEpochMilli(points.last().first)
                .atZone(ZoneId.systemDefault())
                .format(dateFormatter)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "開始: $start",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "終了: $end",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (minValue != null && maxValue != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "最小 ${"%.1f".format(minValue)}%",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "最大 ${"%.1f".format(maxValue)}%",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
