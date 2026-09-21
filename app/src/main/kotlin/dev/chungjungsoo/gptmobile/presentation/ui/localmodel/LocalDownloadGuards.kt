package dev.chungjungsoo.gptmobile.presentation.ui.localmodel

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.chungjungsoo.gptmobile.data.catalog.CatalogEntry
import javax.inject.Inject
import javax.inject.Singleton

interface LocalDownloadGuards {
    fun isMeteredConnection(): Boolean
    fun belowRamRequirement(entry: CatalogEntry): Boolean
}

@Singleton
class LocalDownloadGuardsImpl @Inject constructor(
    @param:ApplicationContext private val context: Context
) : LocalDownloadGuards {
    override fun isMeteredConnection(): Boolean {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        return connectivityManager.isActiveNetworkMetered
    }

    override fun belowRamRequirement(entry: CatalogEntry): Boolean {
        if (entry.minRamGb <= 0) return false
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return memInfo.totalMem < entry.minRamGb.toLong() * BYTES_PER_GB
    }

    private companion object {
        const val BYTES_PER_GB = 1024L * 1024L * 1024L
    }
}
