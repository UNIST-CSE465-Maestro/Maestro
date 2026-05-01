package com.maestro.app.data.local

import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager
import android.os.Process
import kotlinx.serialization.Serializable

@Serializable
data class DeviceResourceSnapshot(
    val batteryPercent: Int? = null,
    val appHeapUsedMb: Long = 0L,
    val appHeapMaxMb: Long = 0L,
    val systemAvailMb: Long? = null,
    val systemLowMemory: Boolean? = null,
    val appCpuTimeMs: Long = 0L,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toMetadata(prefix: String): Map<String, String> =
        mapOf(
            "${prefix}_battery_percent" to batteryPercent.toString(),
            "${prefix}_heap_used_mb" to appHeapUsedMb.toString(),
            "${prefix}_heap_max_mb" to appHeapMaxMb.toString(),
            "${prefix}_system_avail_mb" to systemAvailMb.toString(),
            "${prefix}_system_low_memory" to systemLowMemory.toString(),
            "${prefix}_app_cpu_time_ms" to appCpuTimeMs.toString()
        )
}

class DeviceResourceSampler(
    private val context: Context
) {
    fun sample(): DeviceResourceSnapshot {
        val runtime = Runtime.getRuntime()
        val heapUsed =
            runtime.totalMemory() - runtime.freeMemory()
        val activityManager = context.getSystemService(
            Context.ACTIVITY_SERVICE
        ) as? ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memoryInfo)
        return DeviceResourceSnapshot(
            batteryPercent = batteryPercent(),
            appHeapUsedMb = heapUsed / MB,
            appHeapMaxMb = runtime.maxMemory() / MB,
            systemAvailMb = activityManager?.let {
                memoryInfo.availMem / MB
            },
            systemLowMemory = activityManager?.let {
                memoryInfo.lowMemory
            },
            appCpuTimeMs = Process.getElapsedCpuTime()
        )
    }

    private fun batteryPercent(): Int? {
        val batteryManager = context.getSystemService(
            Context.BATTERY_SERVICE
        ) as? BatteryManager ?: return null
        val value = batteryManager.getIntProperty(
            BatteryManager.BATTERY_PROPERTY_CAPACITY
        )
        return value.takeIf { it >= 0 }
    }

    companion object {
        private const val MB = 1024L * 1024L
    }
}
