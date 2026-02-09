package com.notify2discord.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun AdaptiveActionGroup(
    modifier: Modifier = Modifier,
    compactBreakpoint: Dp = 420.dp,
    maxItemsInRow: Int = Int.MAX_VALUE,
    horizontalSpacing: Dp = 8.dp,
    verticalSpacing: Dp = 8.dp,
    content: @Composable (Boolean) -> Unit
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val compact = maxWidth < compactBreakpoint
        if (compact) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(verticalSpacing)
            ) {
                content(true)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(horizontalSpacing)
            ) {
                // maxItemsInRow は将来の拡張用に保持
                @Suppress("UNUSED_VARIABLE")
                val ignoredMaxItems = maxItemsInRow
                content(false)
            }
        }
    }
}
