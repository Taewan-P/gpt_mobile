package dev.chungjungsoo.gptmobile.data.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class McpPresetCatalogTest {

    @Test
    fun presetsListIsNotEmpty() {
        assertTrue(McpPresetCatalog.presets.isNotEmpty())
    }

    @Test
    fun allPresetIdsAreUnique() {
        val ids = McpPresetCatalog.presets.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun allPresetAliasesAreUnique() {
        val aliases = McpPresetCatalog.presets.map { it.alias }
        assertEquals(aliases.size, aliases.distinct().size)
    }

    @Test
    fun findByAliasReturnsCorrectPreset() {
        val github = McpPresetCatalog.findByAlias("github")
        assertNotNull(github)
        assertEquals("github-all", github?.id)
        assertEquals(McpPresetCategory.DEVELOPMENT, github?.category)

        val nonExistent = McpPresetCatalog.findByAlias("non_existent_preset")
        assertNull(nonExistent)
    }

    @Test
    fun findByIdReturnsCorrectPreset() {
        val fetch = McpPresetCatalog.findById("firecrawl-mcp")
        assertNotNull(fetch)
        assertEquals("Firecrawl Web Research", fetch?.name)
        assertEquals(McpPresetCategory.SEARCH, fetch?.category)
    }

    @Test
    fun getByCategoryFiltersAccurately() {
        val searchPresets = McpPresetCatalog.getByCategory(McpPresetCategory.SEARCH)
        assertTrue(searchPresets.isNotEmpty())
        assertTrue(searchPresets.all { it.category == McpPresetCategory.SEARCH })

        val devPresets = McpPresetCatalog.getByCategory(McpPresetCategory.DEVELOPMENT)
        assertTrue(devPresets.isNotEmpty())
        assertTrue(devPresets.all { it.category == McpPresetCategory.DEVELOPMENT })
    }

    @Test
    fun presetsContainExpectedMajorServers() {
        val aliases = McpPresetCatalog.presets.map { it.alias }.toSet()
        assertTrue(aliases.containsAll(listOf("github", "context7", "tavily_mcp", "firecrawl_mcp", "jina_mcp", "huggingface", "microsoft_learn", "cloudflare_docs", "cloudflare_radar", "neon", "supabase", "stripe")))
        assertTrue(McpPresetCatalog.presets.count { it.isDirectlyInstallable } >= 10)
        assertTrue(
            McpPresetCatalog.presets.filter { it.isDirectlyInstallable }.all {
                it.commandOrUrl.startsWith("https://") &&
                    it.suggestedAuthType in setOf("NONE", "BEARER", "OAUTH") &&
                    it.websiteUrl.startsWith("https://")
            }
        )
        assertFalse(aliases.contains("filesystem"))
        assertFalse(aliases.contains("memory"))
    }

    @Test
    fun requestedProvidersArePresentExactlyOnce() {
        val ids = listOf(
            "mem0-hosted", "supermemory", "mnemoverse", "graphiti-memory", "pearls", "brave-search",
            "jina-mcp", "tavily-mcp", "stackoverflow", "huggingface", "semgrep",
            "deepwiki", "netlify", "supabase", "airtable", "prisma", "slack",
            "asana", "todoist", "google-drive", "google-sheets", "excalidraw", "bright-data"
        )
        ids.forEach { id ->
            assertEquals(id, 1, McpPresetCatalog.presets.count { it.id == id })
            assertFalse(id, McpPresetCatalog.findById(id)!!.iconName == "extension")
        }
    }

    @Test
    fun memoryAndThreadingCategoriesParticipateInSearchAndFiltering() {
        assertTrue(McpPresetCatalog.filterByCategory("memory").size >= 3)
        assertEquals(listOf("pearls"), McpPresetCatalog.getByCategory(McpCategory.THREADING).map { it.id })
        assertTrue(McpPresetCatalog.searchPresets("threading").any { it.id == "pearls" })
        assertTrue(McpPresetCatalog.categories.containsAll(listOf("MEMORY", "THREADING")))
    }

    @Test
    fun selfHostedPresetsNeverAdvertiseAnExampleEndpoint() {
        listOf("pearls", "brave-search", "graphiti-memory").forEach { id ->
            val preset = McpPresetCatalog.findById(id)!!
            assertTrue(preset.commandOrUrl.isBlank())
            assertFalse(preset.isDirectlyInstallable)
            assertFalse(preset.verifiedRemote)
            assertTrue(preset.setupInstructions.isNotBlank())
        }
        assertTrue(McpPresetCatalog.presets.none { it.commandOrUrl.contains("example.com") })
    }

    @Test
    fun hostedMemoryUsesSupportedAuthConstantsAndCurrentEndpoints() {
        val supermemory = McpPresetCatalog.findById("supermemory")!!
        assertEquals("https://mcp.supermemory.ai/mcp", supermemory.commandOrUrl)
        listOf("mem0-hosted", "supermemory", "mnemoverse").forEach { id ->
            assertEquals("OAUTH", McpPresetCatalog.findById(id)!!.suggestedAuthType)
        }
        assertEquals("https://mcp.jina.ai/v1", McpPresetCatalog.findById("jina-mcp")!!.commandOrUrl)
    }

    @Test
    fun urlTokenPresetsRejectMissingEmptyDuplicateAndPlaceholderTokens() {
        val preset = McpPresetCatalog.findById("bright-data")!!
        val endpoint = preset.commandOrUrl
        assertFalse(preset.isDirectlyInstallable)
        listOf("", "?token=", "?token=YOUR_API_TOKEN", "?token=%3Ctoken%3E", "?token=a&token=b", "?token=%ZZ").forEach {
            assertFalse(it, preset.hasRequiredEndpointParameters(endpoint + it))
        }
        assertTrue(preset.hasRequiredEndpointParameters("$endpoint?token=sample-test-token&groups=browser"))
        assertTrue(preset.hasRequiredEndpointParameters("$endpoint?token=encoded%2Btest%3D"))
        assertTrue(McpPresetCatalog.findById("deepwiki")!!.hasRequiredEndpointParameters("https://mcp.deepwiki.com/mcp"))
    }

    @Test
    fun restrictedServicesExplainSetupAndDoNotPromiseAnonymousAccess() {
        listOf("slack", "google-drive", "google-sheets", "semgrep").forEach { id ->
            val preset = McpPresetCatalog.findById(id)!!
            assertFalse(preset.suggestedAuthType == "NONE")
            assertTrue(preset.setupInstructions.isNotBlank())
        }
    }

    @Test
    fun restoredPresetsUseDocumentedHttpEndpointsAndHonestSetupStatus() {
        val endpoints = mapOf(
            "linear" to "https://mcp.linear.app/mcp",
            "sentry" to "https://mcp.sentry.dev/mcp",
            "vercel" to "https://mcp.vercel.com",
            "atlassian" to "https://mcp.atlassian.com/v2/mcp",
            "cloudflare-browser" to "https://browser.mcp.cloudflare.com/mcp"
        )
        endpoints.forEach { (id, endpoint) ->
            val preset = McpPresetCatalog.findById(id)!!
            assertEquals(endpoint, preset.commandOrUrl)
            assertEquals(McpTransportType.STREAMABLE_HTTP, preset.transportType)
            assertEquals("OAUTH", preset.suggestedAuthType)
            assertFalse(preset.verifiedRemote)
            assertTrue(preset.setupInstructions.isNotBlank())
        }
        assertTrue(McpPresetCatalog.findById("vercel")!!.setupInstructions.contains("approved"))
    }

    @Test
    fun localServicesHaveExplicitSetupAndCompanionsCannotBeInstalledAsServers() {
        assertEquals(31, LocalMcpPresets.presets.size)
        LocalMcpPresets.presets.filterNot { it.isPreinstalled }.forEach {
            assertTrue(it.commandOrUrl.isBlank())
            assertFalse(it.isDirectlyInstallable)
            assertFalse(it.verifiedRemote)
            assertTrue(it.setupInstructions.isNotBlank())
            assertTrue(it.websiteUrl.startsWith("https://github.com/"))
        }
        assertEquals("memory", McpPresetCatalog.findById("builtin-memory")!!.integratedTool)
        assertEquals("delegation", McpPresetCatalog.findById("builtin-model-delegation")!!.integratedTool)
        assertTrue(McpPresetCatalog.findById("deskdrop")!!.documentationOnly)
        assertFalse(McpPresetCatalog.findById("houtini-lm")!!.documentationOnly)
    }
}
