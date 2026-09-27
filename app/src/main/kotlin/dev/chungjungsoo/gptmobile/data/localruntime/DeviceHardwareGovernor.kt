package dev.chungjungsoo.gptmobile.data.localruntime

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log

/**
 * Normalized thermal throttling states reflecting device thermal headroom.
 */
enum class DeviceThermalState {
    NORMAL,
    LIGHT,
    MODERATE,
    SEVERE,
    CRITICAL
}

/**
 * Snapshot of device battery level, charging status, and thermal status.
 */
data class DeviceHardwareState(
    val thermalState: DeviceThermalState = DeviceThermalState.NORMAL,
    val batteryPct: Int = 100,
    val isCharging: Boolean = true,
    val isPowerSaveMode: Boolean = false,
    val thermalHeadroom: Float? = null,
    val moderateThermalThreshold: Float? = null
) {
    /**
     * Determines whether hardware is under severe thermal dissipation or low battery pressure.
     */
    val isThrottlingRequired: Boolean
        get() = thermalState == DeviceThermalState.SEVERE ||
            thermalState == DeviceThermalState.CRITICAL ||
            (thermalHeadroom?.let { it.isFinite() && it >= 1f } == true) ||
            isPowerSaveMode ||
            (!isCharging && batteryPct <= 15)

    /**
     * Determines whether hardware is under moderate pressure.
     */
    val isModeratePressure: Boolean
        get() = thermalState == DeviceThermalState.MODERATE ||
            (!isCharging && batteryPct <= 25) ||
            (
                thermalHeadroom?.let { headroom ->
                    moderateThermalThreshold?.let { threshold ->
                        headroom.isFinite() && threshold.isFinite() && threshold > 0f && threshold <= 1f && headroom >= threshold
                    }
                } == true
                )
}

/**
 * Adaptive execution policy adjustments recommended for local on-device inference
 * based on live device thermal and battery diagnostics.
 */
data class AdaptiveThrottlingPolicy(
    val streamPublishIntervalMillis: Long,
    val topKReductionRatio: Float = 1.0f,
    val maxTokensClamp: Int? = null,
    val isCooperativeYieldAggressive: Boolean = false
)

object DeviceHardwareGovernor {
    private const val TAG = "DeviceHardwareGov"
    private val forecastSampler = ThermalForecastSampler()

    fun inspectHardwareState(context: Context): DeviceHardwareState {
        val thermalState = inspectThermalState(context)
        val (batteryPct, isCharging) = inspectBattery(context)
        val isPowerSaveMode = inspectPowerSaveMode(context)
        val forecast = forecastSampler.read(SystemClock.elapsedRealtime()) {
            val manager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ThermalForecast(
                headroom = manager?.getThermalHeadroom(10),
                moderateThreshold = if (Build.VERSION.SDK_INT >= 35) {
                    runCatching { manager?.thermalHeadroomThresholds?.get(PowerManager.THERMAL_STATUS_MODERATE) }.getOrNull()
                } else {
                    null
                }
            )
        }

        return DeviceHardwareState(
            thermalState = thermalState,
            batteryPct = batteryPct,
            isCharging = isCharging,
            isPowerSaveMode = isPowerSaveMode,
            thermalHeadroom = forecast.headroom,
            moderateThermalThreshold = forecast.moderateThreshold
        )
    }

    fun computeThrottlingPolicy(
        hardwareState: DeviceHardwareState,
        isHighRamDevice: Boolean
    ): AdaptiveThrottlingPolicy = when {
        hardwareState.isThrottlingRequired -> {
            // Severe thermal or low battery: throttle dispatch interval to 250ms, clamp tokens, reduce top-k
            AdaptiveThrottlingPolicy(
                streamPublishIntervalMillis = 250L,
                topKReductionRatio = 0.5f,
                maxTokensClamp = 1024,
                isCooperativeYieldAggressive = true
            )
        }
        hardwareState.isModeratePressure -> {
            // Moderate thermal: throttle dispatch interval to 33ms (~30 FPS), mild top-k reduction
            AdaptiveThrottlingPolicy(
                streamPublishIntervalMillis = 33L,
                topKReductionRatio = 0.75f,
                maxTokensClamp = 2048,
                isCooperativeYieldAggressive = false
            )
        }
        else -> {
            // Normal conditions: use 8ms (120 FPS) for high RAM devices, 33ms standard otherwise
            AdaptiveThrottlingPolicy(
                streamPublishIntervalMillis = if (isHighRamDevice) 8L else 33L,
                topKReductionRatio = 1.0f,
                maxTokensClamp = null,
                isCooperativeYieldAggressive = false
            )
        }
    }

    private fun inspectThermalState(context: Context): DeviceThermalState {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                val status = powerManager?.currentThermalStatus ?: PowerManager.THERMAL_STATUS_NONE
                return when (status) {
                    PowerManager.THERMAL_STATUS_NONE -> DeviceThermalState.NORMAL
                    PowerManager.THERMAL_STATUS_LIGHT -> DeviceThermalState.LIGHT
                    PowerManager.THERMAL_STATUS_MODERATE -> DeviceThermalState.MODERATE
                    PowerManager.THERMAL_STATUS_SEVERE -> DeviceThermalState.SEVERE
                    PowerManager.THERMAL_STATUS_CRITICAL,
                    PowerManager.THERMAL_STATUS_EMERGENCY,
                    PowerManager.THERMAL_STATUS_SHUTDOWN -> DeviceThermalState.CRITICAL
                    else -> DeviceThermalState.NORMAL
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed reading thermal status: ${e.message}")
            }
        }
        return DeviceThermalState.NORMAL
    }

    private fun inspectBattery(context: Context): Pair<Int, Boolean> {
        try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val intent = context.registerReceiver(null, filter)
            if (intent != null) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
                val batteryPct = if (level >= 0 && scale > 0) (level * 100) / scale else 100
                return Pair(batteryPct, isCharging)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed reading battery state: ${e.message}")
        }
        return Pair(100, true)
    }

    private fun inspectPowerSaveMode(context: Context): Boolean = try {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        powerManager?.isPowerSaveMode ?: false
    } catch (e: Exception) {
        false
    }

    /**
     * Enhanced hardware state inspection with additional diagnostics for QNN performance
     */
    fun inspectHardwareStateForQnn(context: Context): DeviceHardwareState {
        val baseState = inspectHardwareState(context)

        // Additional QNN-specific diagnostics
        val qnnReady = QnnEnvironment.isEnvironmentConfigured()
        Log.d(TAG, "QNN environment configured: $qnnReady")

        return baseState.copy(
            // Add any QNN-specific state if needed
        )
    }
}
