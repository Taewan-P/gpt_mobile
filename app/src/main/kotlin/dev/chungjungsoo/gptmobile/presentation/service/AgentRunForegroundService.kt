package dev.chungjungsoo.gptmobile.presentation.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.agent.AgentRunCoordinator
import dev.chungjungsoo.gptmobile.presentation.AppForegroundTracker
import dev.chungjungsoo.gptmobile.presentation.ui.main.MainActivity
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AgentRunForegroundService : Service() {
    @Inject
    lateinit var coordinator: AgentRunCoordinator

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var stoppedBecauseInactive = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        var wasActive = coordinator.activeRuns.value.isNotEmpty()
        if (!showNotification(coordinator.activeRuns.value.size)) {
            coordinator.interruptAll()
            stopSelf()
            return
        }
        serviceScope.launch {
            coordinator.activeRuns.collectLatest { activeRuns ->
                val isActive = activeRuns.isNotEmpty()
                if (!isActive) {
                    stoppedBecauseInactive = true
                    ServiceCompat.stopForeground(this@AgentRunForegroundService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    if (shouldNotifyAgentRunsCompleted(wasActive, isActive, AppForegroundTracker.isBackgrounded)) {
                        showCompletionNotification()
                    }
                    stopSelf()
                } else {
                    if (!showNotification(activeRuns.size)) {
                        coordinator.interruptAll()
                        stopSelf()
                    }
                }
                wasActive = isActive
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL_ALL) {
            coordinator.cancelAll()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (shouldInterruptAgentRunsOnDestroy(stoppedBecauseInactive, coordinator.activeRuns.value.isNotEmpty())) {
            coordinator.interruptAll()
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        coordinator.interruptAll()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showNotification(activeCount: Int): Boolean = runCatching {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(activeCount.coerceAtLeast(1)),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }.isSuccess

    private fun buildNotification(activeCount: Int): Notification {
        val openApp = buildOpenAppPendingIntent(0)
        val cancelRuns = PendingIntent.getService(
            this,
            1,
            Intent(this, AgentRunForegroundService::class.java).setAction(ACTION_CANCEL_ALL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_gpt_mobile_monochrome_foreground)
            .setContentTitle(getString(R.string.agent_notification_title))
            .setContentText(resources.getQuantityString(R.plurals.agent_runs_active, activeCount, activeCount))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setProgress(0, 0, true)
            .addAction(0, getString(R.string.cancel_agent_runs), cancelRuns)
            .build()
    }

    private fun showCompletionNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildCompletionNotification())
    }

    private fun buildCompletionNotification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_gpt_mobile_monochrome_foreground)
        .setContentTitle(getString(R.string.agent_completion_notification_title))
        .setContentText(getString(R.string.agent_completion_notification_text))
        .setContentIntent(buildOpenAppPendingIntent(2))
        .setAutoCancel(true)
        .setCategory(NotificationCompat.CATEGORY_STATUS)
        .build()

    private fun buildOpenAppPendingIntent(requestCode: Int): PendingIntent {
        val openAppIntent = Intent().setClass(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            this,
            requestCode,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.agent_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    companion object {
        private const val CHANNEL_ID = "agent_runs"
        private const val NOTIFICATION_ID = 8001
        private const val ACTION_CANCEL_ALL = "dev.chungjungsoo.gptmobile.action.CANCEL_AGENT_RUNS"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, AgentRunForegroundService::class.java)
            )
        }
    }
}

internal fun shouldNotifyAgentRunsCompleted(
    wasActive: Boolean,
    isActive: Boolean,
    isAppBackground: Boolean
): Boolean = wasActive && !isActive && isAppBackground

internal fun shouldInterruptAgentRunsOnDestroy(stoppedBecauseInactive: Boolean, hasActiveRuns: Boolean): Boolean = !stoppedBecauseInactive && hasActiveRuns
