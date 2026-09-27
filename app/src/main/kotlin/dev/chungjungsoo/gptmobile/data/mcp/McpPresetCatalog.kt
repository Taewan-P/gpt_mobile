package dev.chungjungsoo.gptmobile.data.mcp

/**
 * Data class representing an MCP (Model Context Protocol) Server preset in the marketplace hub.
 */
data class McpPreset(
    val id: String,
    val name: String,
    val description: String,
    val category: McpCategory,
    val transportType: McpTransportType,
    val commandOrUrl: String,
    val args: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap(),
    val iconUrl: String? = null,
    val requiredEnvKeys: List<String> = emptyList(),
    val builtInTools: List<McpBuiltinTool> = emptyList(),
    val isPreinstalled: Boolean = false
)

enum class McpCategory {
    SEARCH,
    DEVELOPMENT,
    DATABASE,
    PRODUCTIVITY,
    SYSTEM,
    BROWSER
}

enum class McpTransportType {
    STDIO,
    SSE,
    STREAMABLE_HTTP
}

/**
 * Curated marketplace catalog of popular community MCP presets.
 */
object McpPresetCatalog {
    const val GITHUB_COPILOT_MCP_DEFAULT_URL = "https://api.githubcopilot.com/mcp/"

    val presets: List<McpPreset> = listOf(
        McpPreset(
            id = McpSearchToolSet.PRESET_ID,
            name = McpSearchToolSet.PRESET_NAME,
            description = "Integrated online web search and webpage text extractor powered by droid-mcp. Preinstalled and enabled by default across all models.",
            category = McpCategory.SEARCH,
            transportType = McpTransportType.STDIO,
            commandOrUrl = McpSearchToolSet.DEFAULT_LAUNCHER_PATH,
            args = emptyList(),
            builtInTools = McpSearchToolSet.tools,
            isPreinstalled = McpSearchToolSet.IS_PREINSTALLED
        ),
        McpPreset(
            id = McpLocationToolSet.PRESET_ID,
            name = McpLocationToolSet.PRESET_NAME,
            description = "Native Android device coordinates, altitude and accuracy. Enable Device location in the AI profile and grant Android location permission. No separate MCP server is required.",
            category = McpCategory.SYSTEM,
            transportType = McpTransportType.STDIO,
            commandOrUrl = McpLocationToolSet.DEFAULT_LAUNCHER_PATH,
            args = emptyList(),
            builtInTools = McpLocationToolSet.tools,
            isPreinstalled = McpLocationToolSet.IS_PREINSTALLED
        ),
        McpPreset(
            id = "brave-search",
            name = "Brave Search",
            description = "Web and local search capability using Brave Search API.",
            category = McpCategory.SEARCH,
            transportType = McpTransportType.STDIO,
            commandOrUrl = "npx",
            args = listOf("-y", "@brave/brave-search-mcp-server"),
            requiredEnvKeys = listOf("BRAVE_API_KEY")
        ),
        McpPreset(
            id = "github",
            name = "GitHub Copilot MCP",
            description = "Direct remote GitHub Copilot MCP endpoint. Search repos, read/write issues, PRs, and batch-commit changes efficiently.",
            category = McpCategory.DEVELOPMENT,
            transportType = McpTransportType.STREAMABLE_HTTP,
            commandOrUrl = GITHUB_COPILOT_MCP_DEFAULT_URL,
            requiredEnvKeys = listOf("GITHUB_PERSONAL_ACCESS_TOKEN")
        ),
        McpPreset(
            id = "filesystem",
            name = "Local Filesystem",
            description = "Read, write, and inspect local directory files securely.",
            category = McpCategory.SYSTEM,
            transportType = McpTransportType.STDIO,
            commandOrUrl = "npx",
            args = listOf("-y", "@modelcontextprotocol/server-filesystem", "/path/to/allowed/directory")
        ),
        McpPreset(
            id = "postgres",
            name = "PostgreSQL Database",
            description = "Read-only access and schema inspection for PostgreSQL databases.",
            category = McpCategory.DATABASE,
            transportType = McpTransportType.STDIO,
            commandOrUrl = "npx",
            args = listOf("-y", "@modelcontextprotocol/server-postgres"),
            requiredEnvKeys = listOf("POSTGRES_CONNECTION_STRING")
        ),
        McpPreset(
            id = "puppeteer",
            name = "Puppeteer Browser",
            description = "Headless browser automation to scrape dynamic pages and take screenshots.",
            category = McpCategory.BROWSER,
            transportType = McpTransportType.STDIO,
            commandOrUrl = "npx",
            args = listOf("-y", "@modelcontextprotocol/server-puppeteer")
        ),
        McpPreset(
            id = "fetch",
            name = "Fetch & Scrape",
            description = "Convert HTML web pages into readable Markdown for LLMs.",
            category = McpCategory.SEARCH,
            transportType = McpTransportType.STDIO,
            commandOrUrl = "uvx",
            args = listOf("mcp-server-fetch")
        ),
        McpPreset(
            id = "memory",
            name = "Knowledge Graph Memory",
            description = "Graph-based long-term persistent memory across chat sessions.",
            category = McpCategory.PRODUCTIVITY,
            transportType = McpTransportType.STDIO,
            commandOrUrl = "npx",
            args = listOf("-y", "@modelcontextprotocol/server-memory")
        )
    )
}
