package dev.chungjungsoo.gptmobile.data.catalog

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogParserTest {

    @Test
    fun `parse reads schema version entries and nested catalog fields`() {
        val catalog = ModelCatalogParser.parse(FULL_CATALOG_JSON)

        assertEquals(1, catalog.schemaVersion)
        assertEquals(2, catalog.models.size)

        val gemma = catalog.models[0]
        assertEquals("gemma3-1b-it", gemma.id)
        assertEquals("Gemma3-1B-IT", gemma.displayName)
        assertEquals(
            "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/42d538a932e8d5b12e6b3b455f5572560bd60b2c/gemma3-1b-it-int4.litertlm?download=true",
            gemma.downloadUrl
        )
        assertEquals(584417280L, gemma.sizeInBytes)
        assertEquals(6, gemma.minRamGb)
        assertTrue(gemma.isGated)
        assertFalse(gemma.capabilities.vision)
        assertFalse(gemma.capabilities.tools)
        assertFalse(gemma.capabilities.thinking)
        assertEquals(listOf("gpu", "cpu"), gemma.supportedAccelerators)
        assertEquals(64, gemma.defaultConfig.topK)
        assertEquals(0.95f, gemma.defaultConfig.topP)
        assertEquals(1.0f, gemma.defaultConfig.temperature)
        assertEquals(1024, gemma.defaultConfig.maxTokens)
        assertEquals("0.8.0", gemma.minAppVersion)
        assertTrue(gemma.socToModelFiles.isEmpty())

        val qwen = catalog.models[1]
        assertEquals("qwen2.5-1.5b-instruct", qwen.id)
        assertFalse(qwen.isGated)
        assertTrue(qwen.capabilities.thinking)
        assertEquals(1, qwen.socToModelFiles.size)
        assertEquals("qwen-sm8650.litertlm", qwen.socToModelFiles.getValue("SM8650").modelFile)
        assertEquals(1597931520L, qwen.socToModelFiles.getValue("SM8650").sizeInBytes)
        assertEquals(1280, qwen.socToModelFiles.getValue("SM8650").contextSize)
        assertEquals("q8", qwen.socToModelFiles.getValue("SM8650").quantization)
    }

    @Test
    fun `parse ignores unknown fields and fills missing ones with defaults`() {
        val catalog = ModelCatalogParser.parse(
            """
            {
              "schemaVersion": 1,
              "futureTopLevel": { "ignored": true },
              "models": [
                {
                  "id": "smollm",
                  "displayName": "SmolLM",
                  "unexpected": "skip me",
                  "capabilities": {
                    "vision": true,
                    "futureCapability": "audio"
                  }
                }
              ]
            }
            """.trimIndent()
        )

        val entry = catalog.models.single()
        assertEquals("smollm", entry.id)
        assertEquals("SmolLM", entry.displayName)
        assertEquals("", entry.downloadUrl)
        assertEquals(0L, entry.sizeInBytes)
        assertEquals(0, entry.minRamGb)
        assertFalse(entry.isGated)
        assertTrue(entry.capabilities.vision)
        assertFalse(entry.capabilities.tools)
        assertFalse(entry.capabilities.thinking)
        assertTrue(entry.supportedAccelerators.isEmpty())
        assertEquals(40, entry.defaultConfig.topK)
        assertEquals(0.95f, entry.defaultConfig.topP)
        assertEquals(1.0f, entry.defaultConfig.temperature)
        assertEquals(1024, entry.defaultConfig.maxTokens)
        assertEquals("0.0.0", entry.minAppVersion)
        assertTrue(entry.socToModelFiles.isEmpty())
    }

    @Test
    fun `parse fills missing SOC variant context and quantization with defaults`() {
        val catalog = ModelCatalogParser.parse(
            """
            {
              "schemaVersion": 1,
              "models": [
                {
                  "id": "legacy-npu",
                  "socToModelFiles": {
                    "SM8650": {
                      "modelFile": "npu.litertlm",
                      "sizeInBytes": 100
                    }
                  }
                }
              ]
            }
            """.trimIndent()
        )

        val variant = catalog.models.single().socToModelFiles.getValue("SM8650")
        assertEquals("npu.litertlm", variant.modelFile)
        assertEquals(100L, variant.sizeInBytes)
        assertEquals(0, variant.contextSize)
        assertEquals("", variant.quantization)
    }

    @Test
    fun `visible entries hide unknown schema versions`() {
        val unknown = ModelCatalogParser.parse(
            """
            {
              "schemaVersion": 2,
              "models": [
                {
                  "id": "future-model",
                  "displayName": "Future",
                  "minAppVersion": "0.1.0"
                }
              ]
            }
            """.trimIndent()
        )
        val missing = ModelCatalogParser.parse(
            """
            {
              "models": [
                {
                  "id": "legacy-model",
                  "displayName": "Legacy",
                  "minAppVersion": "0.1.0"
                }
              ]
            }
            """.trimIndent()
        )

        assertTrue(ModelCatalogParser.visibleEntries(unknown, "0.8.0").isEmpty())
        assertTrue(ModelCatalogParser.visibleEntries(missing, "0.8.0").isEmpty())
    }

    @Test
    fun `visible entries hide models whose min app version exceeds the running app`() {
        val catalog = ModelCatalogParser.parse(
            """
            {
              "schemaVersion": 1,
              "models": [
                {
                  "id": "current",
                  "displayName": "Current",
                  "minAppVersion": "0.8.0"
                },
                {
                  "id": "older",
                  "displayName": "Older",
                  "minAppVersion": "0.7.0"
                },
                {
                  "id": "newer",
                  "displayName": "Newer",
                  "minAppVersion": "0.9.0"
                },
                {
                  "id": "padded",
                  "displayName": "Padded",
                  "minAppVersion": "0.8"
                },
                {
                  "id": "minor-ten",
                  "displayName": "1.10",
                  "minAppVersion": "1.10.0"
                }
              ]
            }
            """.trimIndent()
        )

        assertEquals(
            listOf("current", "older", "padded"),
            ModelCatalogParser.visibleEntries(catalog, "0.8.0").map { it.id }
        )
        assertEquals(
            listOf("current", "older", "newer", "padded"),
            ModelCatalogParser.visibleEntries(catalog, "0.9.0").map { it.id }
        )
        assertEquals(
            listOf("current", "older", "newer", "padded", "minor-ten"),
            ModelCatalogParser.visibleEntries(catalog, "1.10.0").map { it.id }
        )
        assertEquals(
            listOf("current", "older", "newer", "padded"),
            ModelCatalogParser.visibleEntries(catalog, "1.9.0").map { it.id }
        )
    }

    @Test
    fun `visible entries hide models with a blank id`() {
        val catalog = ModelCatalogParser.parse(
            """
            {
              "schemaVersion": 1,
              "models": [
                {
                  "displayName": "Missing id",
                  "minAppVersion": "0.1.0"
                }
              ]
            }
            """.trimIndent()
        )

        assertTrue(ModelCatalogParser.visibleEntries(catalog, "0.8.0").isEmpty())
    }

    @Test
    fun `formatDownloadSize uses binary units with a stable locale`() {
        assertEquals("557 MB", ModelCatalogParser.formatDownloadSize(584417280L))
        assertEquals("2.4 GB", ModelCatalogParser.formatDownloadSize(2588147712L))
        assertEquals("1 KB", ModelCatalogParser.formatDownloadSize(1024L))
        assertEquals("0 KB", ModelCatalogParser.formatDownloadSize(0L))
    }

    @Test
    fun `hosted catalog snapshot parses to visible local models`() {
        val hostedJson = readCatalogFile("../model_catalog.json", "model_catalog.json")
        val bundledJson = readCatalogFile(
            "src/main/assets/model_catalog.json",
            "app/src/main/assets/model_catalog.json"
        )
        assertEquals(hostedJson, bundledJson)

        val catalog = ModelCatalogParser.parse(hostedJson)
        val visible = ModelCatalogParser.visibleEntries(catalog, "0.8.0")

        assertEquals(1, catalog.schemaVersion)
        assertTrue(visible.size in 4..6)
        assertTrue(visible.any { it.isGated && it.displayName.contains("Gemma", ignoreCase = true) })
        assertTrue(visible.any { !it.isGated })
        visible.forEach { entry ->
            assertTrue(entry.id.isNotBlank())
            assertTrue(entry.displayName.isNotBlank())
            assertTrue(entry.downloadUrl.contains("/resolve/"))
            assertTrue(entry.sizeInBytes > 0)
            assertTrue(entry.minRamGb > 0)
            assertTrue(entry.supportedAccelerators.isNotEmpty())
            assertTrue(entry.minAppVersion.isNotBlank())
        }

        val gemma3 = visible.single { it.id == "gemma3-1b-it" }
        assertTrue(gemma3.supportedAccelerators.contains("npu"))
        assertEquals(8, gemma3.socToModelFiles.size)
        assertEquals(690143232L, gemma3.socToModelFiles.getValue("SM8550").sizeInBytes)
        assertEquals(1280, gemma3.socToModelFiles.getValue("SM8550").contextSize)
        assertEquals("q4", gemma3.socToModelFiles.getValue("SM8550").quantization)
        assertEquals(1678542365L, gemma3.socToModelFiles.getValue("Tensor G5").sizeInBytes)
        assertEquals("q8", gemma3.socToModelFiles.getValue("Tensor G5").quantization)

        val gemma4 = visible.single { it.id == "gemma-4-e2b-it" }
        assertTrue(gemma4.supportedAccelerators.contains("npu"))
        assertTrue(gemma4.socToModelFiles.containsKey("SM8750"))
        assertTrue(gemma4.socToModelFiles.containsKey("Tensor G5"))
        assertEquals(3016294400L, gemma4.socToModelFiles.getValue("SM8750").sizeInBytes)
        assertEquals(1280, gemma4.socToModelFiles.getValue("SM8750").contextSize)
        assertEquals(8, gemma4.minRamGb)
    }

    companion object {
        private val FULL_CATALOG_JSON = """
            {
              "schemaVersion": 1,
              "models": [
                {
                  "id": "gemma3-1b-it",
                  "displayName": "Gemma3-1B-IT",
                  "downloadUrl": "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/42d538a932e8d5b12e6b3b455f5572560bd60b2c/gemma3-1b-it-int4.litertlm?download=true",
                  "sizeInBytes": 584417280,
                  "minRamGb": 6,
                  "isGated": true,
                  "capabilities": {
                    "vision": false,
                    "tools": false,
                    "thinking": false
                  },
                  "supportedAccelerators": ["gpu", "cpu"],
                  "defaultConfig": {
                    "topK": 64,
                    "topP": 0.95,
                    "temperature": 1.0,
                    "maxTokens": 1024
                  },
                  "minAppVersion": "0.8.0"
                },
                {
                  "id": "qwen2.5-1.5b-instruct",
                  "displayName": "Qwen2.5-1.5B-Instruct",
                  "downloadUrl": "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/19edb84c69a0212f29a6ef17ba0d6f278b6a1614/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm?download=true",
                  "sizeInBytes": 1597931520,
                  "minRamGb": 6,
                  "isGated": false,
                  "capabilities": {
                    "vision": false,
                    "tools": false,
                    "thinking": true
                  },
                  "supportedAccelerators": ["gpu", "cpu"],
                  "defaultConfig": {
                    "topK": 20,
                    "topP": 0.8,
                    "temperature": 0.7,
                    "maxTokens": 4096
                  },
                  "minAppVersion": "0.8.0",
                  "socToModelFiles": {
                    "SM8650": {
                      "modelFile": "qwen-sm8650.litertlm",
                      "downloadUrl": "https://huggingface.co/example/qwen/resolve/abc/qwen-sm8650.litertlm?download=true",
                      "commitHash": "abc",
                      "sizeInBytes": 1597931520,
                      "contextSize": 1280,
                      "quantization": "q8"
                    }
                  }
                }
              ]
            }
        """.trimIndent()

        private fun readCatalogFile(vararg candidates: String): String {
            val file = candidates.map(::File).firstOrNull { it.exists() }
            checkNotNull(file) { "Missing catalog file. Tried: ${candidates.joinToString()}" }
            return file.readText()
        }
    }
}
