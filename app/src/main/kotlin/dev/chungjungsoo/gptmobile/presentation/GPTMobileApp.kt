package dev.chungjungsoo.gptmobile.presentation

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.backup.SanitizedChatBackup
import dev.chungjungsoo.gptmobile.data.database.dao.AgentPersistenceDao
import dev.chungjungsoo.gptmobile.data.database.dao.AgentRunDao
import dev.chungjungsoo.gptmobile.data.localmodel.PendingLocalPlatformActivator
import dev.chungjungsoo.gptmobile.data.localruntime.LocalRuntime
import dev.chungjungsoo.gptmobile.data.repository.LocalModelRepository
import dev.chungjungsoo.gptmobile.data.repository.SecretMigrationError
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltAndroidApp
class GPTMobileApp :
    Application(),
    Configuration.Provider {
    // TODO Delete when https://github.com/google/dagger/issues/3601 is resolved.
    @Inject
    @ApplicationContext
    lateinit var context: Context

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Volatile
    var secretMigrationErrors: List<SecretMigrationError> = emptyList()
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        SanitizedChatBackup.restoreIfPresent(this)
        super.onCreate()
        registerActivityLifecycleCallbacks(AppForegroundTracker)
        StartupRecoveryGate.start(applicationScope) {
            val startup = startupDependencies()
            startup.pendingLocalPlatformActivator().start()
            secretMigrationErrors = runStartupMaintenance(
                interruptPersistedWork = {
                    val interruptedAt = System.currentTimeMillis() / 1000
                    startup.agentRunDao().interruptActiveRuns(interruptedAt)
                    startup.agentPersistenceDao().cancelInterruptedToolEvents(interruptedAt)
                },
                migrateSecrets = startup.settingRepository()::migrateSecrets
            )
            if (secretMigrationErrors.isNotEmpty()) {
                runCatching {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@GPTMobileApp,
                            R.string.credential_migration_warning,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }.onFailure { error ->
                    Log.e(TAG, "Unable to show credential migration warning.", error)
                }
            }
            startup.localModelRepository().reconcile()
            startup.localModelRepository().awaitActiveDownloadScheduling()
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            applicationScope.launch {
                startupDependencies().localRuntime().unloadEngine()
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    private fun startupDependencies(): StartupDependencies = EntryPointAccessors.fromApplication(
        this,
        StartupDependencies::class.java
    )

    private companion object {
        const val TAG = "GPTMobileApp"
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface StartupDependencies {
    fun pendingLocalPlatformActivator(): PendingLocalPlatformActivator
    fun localModelRepository(): LocalModelRepository
    fun settingRepository(): SettingRepository
    fun agentRunDao(): AgentRunDao
    fun agentPersistenceDao(): AgentPersistenceDao
    fun localRuntime(): LocalRuntime
}

object StartupRecoveryGate {
    @Volatile
    private var job: Job? = null

    fun start(scope: CoroutineScope, block: suspend () -> Unit) {
        job = scope.launch { block() }
    }

    suspend fun await() {
        job?.join()
    }
}

internal suspend fun runStartupMaintenance(
    interruptPersistedWork: suspend () -> Unit,
    migrateSecrets: suspend () -> List<SecretMigrationError>
): List<SecretMigrationError> {
    interruptPersistedWork()
    return try {
        migrateSecrets()
    } catch (error: Exception) {
        listOf(SecretMigrationError("startup", error.message ?: "Credential migration failed."))
    }
}
