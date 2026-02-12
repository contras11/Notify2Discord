package com.notify2discord.app.ui

import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.luminance

/**
 * 青系テーマに合わせたカード配色を一元管理する。
 * ダーク時はアルファを下げて可読性を保つ。
 */
object AppCardColors {
    @Composable
    fun normal(): CardColors {
        val alpha = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) 0.14f else 0.22f
        return CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = alpha)
        )
    }

    @Composable
    fun emphasized(): CardColors {
        val alpha = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) 0.2f else 0.32f
        return CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = alpha)
        )
    }
}
