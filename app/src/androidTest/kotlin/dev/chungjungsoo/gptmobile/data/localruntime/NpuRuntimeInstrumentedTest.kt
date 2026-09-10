package dev.chungjungsoo.gptmobile.data.localruntime

import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NpuRuntimeInstrumentedTest {
    @Test
    fun selectedDispatch_loadsFromInstalledApk() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val declared = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_CONFIGURATIONS)
            .reqFeatures.orEmpty().firstOrNull { it.name == "android.hardware.npu" }
        assertTrue("The APK must declare optional NPU access", declared != null && declared.flags and 1 == 0)
        val libraries = NpuRuntimeLibraries(context)
        assumeTrue("This device has no packaged NPU target", libraries.isAvailable(Build.SOC_MODEL))
        val directory = File(libraries.prepare(Build.SOC_MODEL))
        val dispatch = directory.listFiles().orEmpty().filter { it.name.startsWith("libLiteRtDispatch") }
        assertEquals(1, dispatch.size)
        System.load(dispatch.single().absolutePath)
        Log.i("NpuRuntimeTest", "Loaded ${dispatch.single().name} for ${Build.SOC_MODEL}")
    }

    @Test
    fun autoProfile_selectsNpuAndStreamsReply(): Unit = kotlinx.coroutines.runBlocking {
        var modelPath = InstrumentationRegistry.getArguments().getString("npuModelPath")
        val downloadModel = InstrumentationRegistry.getArguments().getString("npuDownload") == "true"
        assumeTrue(downloadModel || !modelPath.isNullOrBlank())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val entry = context.assets.open("model_catalog.json").bufferedReader().use {
            dev.chungjungsoo.gptmobile.data.catalog.ModelCatalogParser.parse(it.readText())
        }.models.first { it.id == "gemma-4-e2b-it" }
        val download = checkNotNull(dev.chungjungsoo.gptmobile.data.localmodel.SocVariantResolver.resolve(entry, Build.SOC_MODEL, "npu"))
        val installedModels = if (downloadModel) {
            dev.chungjungsoo.gptmobile.presentation.StartupRecoveryGate.await()
            val dependencies = dagger.hilt.android.EntryPointAccessors.fromApplication(
                context,
                dev.chungjungsoo.gptmobile.presentation.StartupDependencies::class.java
            )
            val models = dependencies.localModelRepository()
            val current = models.getById(entry.id)
            check(current == null || (current.commitHash == download.commitHash && current.fileName == download.fileName)) {
                "Test will not replace an existing user model"
            }
            models.startDownload(entry, download)
            modelPath = kotlinx.coroutines.withTimeout(600_000) {
                var ready: String? = null
                while (ready == null) {
                    ready = models.resolveDownloadedPath(entry.id)
                    if (ready == null) kotlinx.coroutines.delay(1000)
                }
                ready
            }
            assertTrue(checkNotNull(modelPath).startsWith(context.noBackupFilesDir.absolutePath))
            models
        } else {
            null
        }
        val retained = dev.chungjungsoo.gptmobile.data.database.entity.LocalModel(
            entry.id,
            download.commitHash,
            download.fileName,
            "unused",
            download.sizeInBytes,
            dev.chungjungsoo.gptmobile.data.localmodel.LocalModelStatus.READY
        )
        val repository = installedModels ?: object : dev.chungjungsoo.gptmobile.data.repository.LocalModelRepository {
            override fun observeAll() = kotlinx.coroutines.flow.flowOf(listOf(retained))
            override fun observeWorkInfos() = kotlinx.coroutines.flow.flowOf(emptyList<androidx.work.WorkInfo>())
            override suspend fun getById(catalogEntryId: String) = retained
            override suspend fun resolveDownloadedPath(catalogEntryId: String) = modelPath
            override suspend fun startDownload(entry: dev.chungjungsoo.gptmobile.data.catalog.CatalogEntry) = error("No test downloads")
            override suspend fun startDownload(entry: dev.chungjungsoo.gptmobile.data.catalog.CatalogEntry, resolved: dev.chungjungsoo.gptmobile.data.localmodel.ResolvedModelDownload) = error("No test downloads")
            override suspend fun cancelDownload(catalogEntryId: String) = Unit
            override suspend fun deleteModel(catalogEntryId: String) = error("No test deletion")
            override suspend fun totalStorageUsed() = retained.totalBytes
            override suspend fun reconcile() = Unit
        }
        val catalog = object : dev.chungjungsoo.gptmobile.data.repository.ModelCatalogRepository {
            override suspend fun getVisibleEntries() = listOf(entry)
        }
        val used = mutableListOf<String>()
        val realRuntime = LocalRuntimeImpl(context)
        val runtime = LocalEngineHolder(object : LocalRuntime by realRuntime {
            override suspend fun loadEngine(spec: LocalEngineSpec) {
                used += spec.accelerator
                realRuntime.loadEngine(spec)
            }
        })
        try {
            val adapter = dev.chungjungsoo.gptmobile.data.agent.provider.LiteRtLmAdapter(
                runtime,
                repository,
                "",
                "Model missing",
                modelCatalogRepository = catalog,
                deviceSocModel = Build.SOC_MODEL
            )
            val profile = dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2(
                name = "NPU test",
                compatibleType = dev.chungjungsoo.gptmobile.data.model.ClientType.LITERT_LM,
                apiUrl = "",
                model = entry.id,
                accelerator = "auto",
                maxTokens = 1024
            )
            val turns = listOf(
                dev.chungjungsoo.gptmobile.data.context.ConversationTurn(
                    dev.chungjungsoo.gptmobile.data.database.entity.MessageV2(content = "Reply with the word hello.", platformType = null),
                    null,
                    true
                )
            )
            val events = mutableListOf<dev.chungjungsoo.gptmobile.data.agent.ProviderEvent>()
            adapter.openSession(turns, profile).streamRound(emptyList(), emptyList()).collect { events += it }
            assertEquals(listOf("npu"), used)
            val errors = events.filterIsInstance<dev.chungjungsoo.gptmobile.data.agent.ProviderEvent.Failed>()
            assertTrue(errors.toString(), errors.isEmpty())
            val reply = events.filterIsInstance<dev.chungjungsoo.gptmobile.data.agent.ProviderEvent.TextDelta>().joinToString("") { it.text }
            assertTrue(reply.isNotBlank())
            Log.i("NpuRuntimeTest", "Auto profile selected $used and streamed: $reply")
        } finally {
            runtime.unloadEngine()
        }
    }

    @Test
    fun explicitNpuModel_generatesWithoutAppFallback() {
        val arguments = InstrumentationRegistry.getArguments()
        val modelPath = arguments.getString("npuModelPath")
        assumeTrue("Pass npuModelPath to run inference validation", !modelPath.isNullOrBlank())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val libraries = NpuRuntimeLibraries(context)
        assertTrue(libraries.isAvailable(Build.SOC_MODEL))
        val config = EngineConfig(
            modelPath = checkNotNull(modelPath),
            backend = Backend.NPU(libraries.prepare(Build.SOC_MODEL)),
            maxNumTokens = arguments.getString("npuMaxTokens")?.toIntOrNull() ?: 1024
        )
        Engine(config).use { engine ->
            engine.initialize()
            engine.createConversation().use { conversation ->
                val reply = conversation.sendMessage("Reply with the word hello.")
                    .contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
                assertTrue(reply.isNotBlank())
                Log.i("NpuRuntimeTest", "NPU inference completed on ${Build.SOC_MODEL}: $reply")
            }
        }
    }
}
