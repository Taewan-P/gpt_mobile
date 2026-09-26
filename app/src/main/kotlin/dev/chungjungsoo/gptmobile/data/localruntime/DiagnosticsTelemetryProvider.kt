package dev.chungjungsoo.gptmobile.data.localruntime

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager

/**
 * Diagnostics and hardware state inspector for debug mode.
 */
object DiagnosticsTelemetryProvider {

    data class DiagnosticsSnapshot(
        val backendName: String,
        val accelerator: String,
        val socModel: String,
        val totalRamGb: Long,
        val availableRamMb: Long,
        val thermalStatus: String,
        val batteryPct: Int,
        val isCharging: Boolean,
        val qnnReady: Boolean,
        val dispatchDir: String,
        val skelExists: Boolean,
        val processMemoryMb: Long = 0,
        val javaHeapMb: Long = 0,
        val network: String = "Unknown"
    )

    fun getSnapshot(context: Context, backendName: String, accelerator: String): DiagnosticsSnapshot {
        val hwState = DeviceHardwareGovernor.inspectHardwareState(context)
        val qnnProbe = QnnEnvironment.getProbeStatus(context)

        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) (level * 100) / scale else -1
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null) {
            when (powerManager.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE -> "Nominal"
                PowerManager.THERMAL_STATUS_LIGHT -> "Light"
                PowerManager.THERMAL_STATUS_MODERATE -> "Moderate"
                PowerManager.THERMAL_STATUS_SEVERE -> "Severe Throttling"
                PowerManager.THERMAL_STATUS_CRITICAL -> "Critical Throttling"
                PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergency"
                PowerManager.THERMAL_STATUS_SHUTDOWN -> "Shutdown"
                else -> "Unknown"
            }
        } else {
            if (hwState.isThrottlingRequired) "Throttled" else "Nominal"
        }

        val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MODEL.takeIf { !it.isNullOrBlank() } ?: Build.HARDWARE.orEmpty()
        } else {
            Build.HARDWARE.orEmpty()
        }

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memoryInfo)
        val totalRamGb = if (memoryInfo.totalMem > 0) memoryInfo.totalMem / (1024L * 1024L * 1024L) else 0L
        val availableRamMb = if (memoryInfo.availMem > 0) memoryInfo.availMem / (1024L * 1024L) else 0L

        return DiagnosticsSnapshot(
            backendName = backendName,
            accelerator = accelerator,
            socModel = soc,
            totalRamGb = totalRamGb,
            availableRamMb = availableRamMb,
            thermalStatus = thermalStatus,
            batteryPct = batteryPct,
            isCharging = isCharging,
            qnnReady = qnnProbe.isReady,
            dispatchDir = qnnProbe.dispatchDir,
            skelExists = qnnProbe.skelFileExists,
            processMemoryMb = android.os.Debug.getPss() / 1024,
            javaHeapMb = Runtime.getRuntime().let { (it.totalMemory() - it.freeMemory()) / (1024 * 1024) },
            network = runCatching {
                val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
                val capabilities = manager?.getNetworkCapabilities(manager.activeNetwork)
                when {
                    capabilities == null -> "Offline"
                    capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
                    capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                    capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
                    capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                    else -> "Connected"
                }
            }.getOrDefault("Unavailable")
        )
    }

    fun formatDiagnosticsText(snapshot: DiagnosticsSnapshot, telemetryNotice: String?): String = buildString {
        appendLine("=== Local Model Diagnostics ===")
        appendLine("Engine Backend: ${snapshot.backendName}")
        appendLine("Target Accelerator: ${snapshot.accelerator.uppercase()}")
        appendLine("SoC / Processor: ${snapshot.socModel}")
        appendLine("System RAM: Available ${snapshot.availableRamMb} MB / Total ${snapshot.totalRamGb} GB")
        appendLine("App memory: ${snapshot.processMemoryMb} MB PSS / ${snapshot.javaHeapMb} MB Java heap")
        appendLine("Network: ${snapshot.network}")
        appendLine("Thermal State: ${snapshot.thermalStatus}")
        appendLine("Battery: ${if (snapshot.batteryPct >= 0) "${snapshot.batteryPct}%" else "N/A"}${if (snapshot.isCharging) " (Charging)" else ""}")
        appendLine("QNN device/library prerequisites: ${if (snapshot.qnnReady) "Available (execution unverified)" else "Unavailable"}")
        appendLine("QNN Dispatch Dir: ${snapshot.dispatchDir}")
        appendLine("QNN Skel Present: ${if (snapshot.skelExists) "Yes" else "No"}")
        telemetryNotice?.takeIf { it.isNotBlank() }?.let {
            appendLine("Inference Telemetry: $it")
        }
    }
}
