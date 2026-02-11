package com.notify2discord.app.battery

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.notify2discord.app.data.BatterySnapshot
import kotlin.math.roundToInt

class BatteryInfoCollector(private val context: Context) {
    fun collect(history: List<BatterySnapshot>): BatterySnapshot? {
        // Stickyブロードキャストから最新のバッテリー状態を取得する
        val batteryIntent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            Context.RECEIVER_NOT_EXPORTED
        ) ?: return null

        val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val levelPercent = if (level >= 0 && scale > 0) {
            ((level * 100f) / scale.toFloat()).roundToInt().coerceIn(0, 100)
        } else {
            null
        }

        val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1).takeIf { it >= 0 }
        val health = batteryIntent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1).takeIf { it >= 0 }
        val technology = batteryIntent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY).orEmpty()

        val tempTenth = batteryIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        val temperatureC = if (tempTenth == Int.MIN_VALUE) null else tempTenth / 10f

        val voltage = batteryIntent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, Int.MIN_VALUE)
        val voltageMv = if (voltage == Int.MIN_VALUE) null else voltage

        val manager = context.getSystemService(BatteryManager::class.java)
        val chargeCounterUah = manager.intProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val currentNowUa = manager.intProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val currentAverageUa = manager.intProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
        val energyCounterNwh = manager.longProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER)
        // API差異で定数が無い場合があるため、存在時のみ反射で取得する
        val cycleCount = cycleCountPropertyId?.let { manager.intProperty(it) }

        // 現在残容量と残量%から満充電容量を推定する（端末依存で誤差あり）
        val estimatedFullChargeMah = if (chargeCounterUah != null && levelPercent != null && levelPercent > 0) {
            (chargeCounterUah / 1000f) / (levelPercent / 100f)
        } else {
            null
        }

        val historyMaxFullMah = history.asSequence()
            .mapNotNull { it.estimatedFullChargeMah }
            .maxOrNull()
        val baselineMah = listOfNotNull(historyMaxFullMah, estimatedFullChargeMah).maxOrNull()
        // 過去最大推定容量を100%基準に相対的な健康度を算出する
        val estimatedHealthPercent = if (baselineMah != null && baselineMah > 0f && estimatedFullChargeMah != null) {
            (estimatedFullChargeMah / baselineMah * 100f).coerceIn(0f, 120f)
        } else {
            null
        }

        return BatterySnapshot(
            capturedAt = System.currentTimeMillis(),
            levelPercent = levelPercent,
            status = status,
            health = health,
            isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL,
            temperatureC = temperatureC,
            voltageMv = voltageMv,
            technology = technology,
            chargeCounterUah = chargeCounterUah,
            currentNowUa = currentNowUa,
            currentAverageUa = currentAverageUa,
            energyCounterNwh = energyCounterNwh,
            cycleCount = cycleCount,
            estimatedFullChargeMah = estimatedFullChargeMah,
            estimatedHealthPercent = estimatedHealthPercent
        )
    }

    companion object {
        fun statusLabel(status: Int?): String {
            return when (status) {
                BatteryManager.BATTERY_STATUS_CHARGING -> "充電中"
                BatteryManager.BATTERY_STATUS_FULL -> "満充電"
                BatteryManager.BATTERY_STATUS_DISCHARGING -> "放電中"
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "未充電"
                BatteryManager.BATTERY_STATUS_UNKNOWN -> "不明"
                else -> "取得不可"
            }
        }

        fun healthLabel(health: Int?): String {
            return when (health) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "良好"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "高温"
                BatteryManager.BATTERY_HEALTH_DEAD -> "劣化"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "過電圧"
                BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "障害"
                BatteryManager.BATTERY_HEALTH_COLD -> "低温"
                BatteryManager.BATTERY_HEALTH_UNKNOWN -> "不明"
                else -> "取得不可"
            }
        }
    }
}

private val cycleCountPropertyId: Int? by lazy {
    runCatching {
        BatteryManager::class.java.getField("BATTERY_PROPERTY_CYCLE_COUNT").getInt(null)
    }.getOrNull()
}

private fun BatteryManager?.intProperty(id: Int): Int? {
    val value = this?.getIntProperty(id) ?: Int.MIN_VALUE
    return if (value == Int.MIN_VALUE) null else value
}

private fun BatteryManager?.longProperty(id: Int): Long? {
    val value = this?.getLongProperty(id) ?: Long.MIN_VALUE
    return if (value == Long.MIN_VALUE) null else value
}
