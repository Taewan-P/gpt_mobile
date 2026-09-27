package dev.chungjungsoo.gptmobile.data.catalog

/** Host services remain optional: installing a connection never launches a desktop runtime on Android. */
object LocalMcpPresets {
    val presets = listOf(
        McpPreset(
            id = "builtin-memory", name = "Local Memory Capture & Recall",
            description = "Encrypted on-device memory. Capture supported facts from your messages and recall relevant facts across conversations.",
            category = McpCategory.MEMORY, commandOrUrl = "builtin://memory",
            transportType = McpTransportType.STDIO, iconName = "memory",
            author = "GPT Mobile", isPreinstalled = true, integratedTool = "memory",
            toolCapabilities = listOf("Capture user preferences and relationships", "Recall matching facts", "Review, disable and delete saved facts"),
            setupInstructions = "Enabled for a new Memory. Existing saved choices are preserved. Configure or disable in Settings → Tool connections. Cloud recall, chat scope, review and retention are configurable."
        ),
        McpPreset(
            id = "builtin-model-delegation", name = "Built-in Model Delegation",
            description = "Let your AI ask another model for help. Works with downloaded models, Ollama and llama servers.",
            category = McpCategory.DEVELOPMENT, commandOrUrl = "builtin://delegation",
            transportType = McpTransportType.STDIO, iconName = "delegation",
            author = "GPT Mobile", isPreinstalled = true, integratedTool = "delegation",
            toolCapabilities = listOf("Delegate a bounded text task", "Choose a target AI profile", "Limit input, output, duration and calls"),
            setupInstructions = "Disabled until you enable it and select a target in Configure. Uses existing provider credentials. It is a native implementation, not the upstream Houtini package; the separate Houtini LM card connects to that package."
        ),
        McpPreset(
            id = "ollama-mcp-bridge", name = "Ollama MCP Bridge", description = "Desktop orchestration bridge for Ollama and MCP servers.",
            category = McpCategory.DEVELOPMENT, commandOrUrl = "",
            iconName = "ollama-mcp-bridge", author = "patruff",
            pricing = McpPricingType.FREE,
            requiredFields = listOf("Your host MCP endpoint and any required credentials"),
            toolCapabilities = listOf("Desktop orchestration bridge for Ollama and MCP servers."),
            websiteUrl = "https://github.com/patruff/ollama-mcp-bridge",
            documentationOnly = true,
            setupInstructions = "This is an MCP client/orchestrator, not an endpoint for GPT Mobile. The app already connects directly to Ollama. Follow the project guide for a separate desktop client."
        ),
        McpPreset(
            id = "mcp-client-for-ollama", name = "MCP Client for Ollama", description = "Terminal MCP client for Ollama.",
            category = McpCategory.DEVELOPMENT, commandOrUrl = "",
            iconName = "mcp-client-for-ollama", author = "jonigl",
            pricing = McpPricingType.FREE,
            requiredFields = listOf("Your host MCP endpoint and any required credentials"),
            toolCapabilities = listOf("Terminal MCP client for Ollama."),
            websiteUrl = "https://github.com/jonigl/mcp-client-for-ollama",
            documentationOnly = true,
            setupInstructions = "Install this terminal application on a computer. It consumes MCP servers; it cannot be installed as a tool server in this Android app."
        ),
        McpPreset(
            id = "houtini-lm", name = "Houtini LM", description = "Delegate bounded tasks to models served by LM Studio, Ollama, llama.cpp or cloud APIs.",
            category = McpCategory.DEVELOPMENT, commandOrUrl = "",
            iconName = "houtini-lm", author = "houtini-ai",
            pricing = McpPricingType.FREE,
            requiredFields = listOf("Your host MCP endpoint and any required credentials"),
            toolCapabilities = listOf("Delegate bounded tasks to models served by LM Studio, Ollama, llama.cpp or cloud APIs."),
            websiteUrl = "https://github.com/houtini-ai/houtini-lm",
            documentationOnly = false,
            setupInstructions = "Optional external Houtini server. Install @houtini/lm on your computer and set HOUTINI_LM_ENDPOINT_URL to the inference server base URL. Its standard launch uses STDIO; expose it through the HTTP MCP Gateway described in its Docker guide, then paste that Streamable HTTP URL here. Do not enter the llama /v1 API URL as an MCP endpoint. For a phone-native alternative, configure Built-in Model Delegation."
        ),
        McpPreset(
            id = "claude-lmstudio-bridge", name = "LM Studio Bridge", description = "Use an LM Studio model through an external MCP bridge.",
            category = McpCategory.DEVELOPMENT, commandOrUrl = "",
            iconName = "claude-lmstudio-bridge", author = "infinitimeless",
            pricing = McpPricingType.FREE,
            requiredFields = listOf("Your host MCP endpoint and any required credentials"),
            toolCapabilities = listOf("Use an LM Studio model through an external MCP bridge."),
            websiteUrl = "https://github.com/infinitimeless/claude-lmstudio-bridge",
            documentationOnly = false,
            setupInstructions = "Run the bridge and LM Studio on your computer. Expose the bridge through a Streamable HTTP adapter; enter the adapter URL, not the LM Studio inference URL."
        ),
        McpPreset(
            id = "comfyui-mcp", name = "ComfyUI MCP (legacy)", description = "Create images, video and audio through a workstation running ComfyUI.",
            category = McpCategory.PRODUCTIVITY, commandOrUrl = "",
            iconName = "comfyui-mcp", author = "artokun",
            pricing = McpPricingType.FREE,
            requiredFields = listOf("Your host MCP endpoint and any required credentials"),
            toolCapabilities = listOf("Create images, video and audio through a workstation running ComfyUI."),
            websiteUrl = "https://github.com/artokun/comfyui-mcp",
            documentationOnly = false,
            setupInstructions = "This community project is no longer maintained and recommends official Comfy tooling. If using the legacy server, configure its authenticated HTTP mode and paste its reachable MCP URL. ComfyUI and generation models run on your computer, not in this APK. Select only the tools you need."
        ),
        McpPreset(
            id = "graphiti-memory", name = "Graphiti Memory", description = "Temporal knowledge-graph memory with entity and relationship recall.",
            category = McpCategory.MEMORY, commandOrUrl = "",
            iconName = "memory", author = "getzep",
            pricing = McpPricingType.FREE,
            requiredFields = listOf("Your Graphiti MCP Streamable HTTP endpoint"),
            toolCapabilities = listOf("Search facts and entities", "Recall temporal relationships", "Add graph memories"),
            websiteUrl = "https://github.com/getzep/graphiti",
            documentationOnly = false,
            setupInstructions = "Run Graphiti with an MCP-compatible HTTP gateway on your own host, then paste the reachable Streamable HTTP endpoint here. Keep automatic recall opt-in per AI profile; Graphiti remains separate from the encrypted on-device Memory."
        ),
        McpPreset(
            id = "marm-memory", name = "MARM Memory", description = "Shared session history, code indexing and concept-graph memory.",
            category = McpCategory.MEMORY, commandOrUrl = "",
            iconName = "marm-memory", author = "Lyellr88",
            pricing = McpPricingType.FREE,
            requiredFields = listOf("Your host MCP endpoint and any required credentials"),
            toolCapabilities = listOf("Shared session history, code indexing and concept-graph memory."),
            websiteUrl = "https://github.com/Lyellr88/marm-memory",
            documentationOnly = false,
            setupInstructions = "Install marm-mcp-server on your computer. Start its HTTP server, configure network access and authentication, and enter its reachable /mcp endpoint. The default localhost:8001 address only works on that computer. Use Bearer auth for a network-exposed server. External memory is separate from the built-in Memory."
        ),
        McpPreset(
            id = "truememory", name = "TrueMemory", description = "External automatic capture and recall backed by local SQLite.",
            category = McpCategory.MEMORY, commandOrUrl = "",
            iconName = "truememory", author = "buildingjoshbetter",
            pricing = McpPricingType.FREE,
            requiredFields = listOf("Your host MCP endpoint and any required credentials"),
            toolCapabilities = listOf("External automatic capture and recall backed by local SQLite."),
            websiteUrl = "https://github.com/buildingjoshbetter/TrueMemory",
            documentationOnly = false,
            setupInstructions = "Install and configure TrueMemory on the host computer. Expose its MCP server through Streamable HTTP. Desktop client hooks do not automatically capture GPT Mobile conversations; enable the discovered tools explicitly. External memory does not share the phone Memory."
        ),
        McpPreset(
            id = "clawmem", name = "ClawMem", description = "Hybrid search and memory for desktop AI agents.",
            category = McpCategory.MEMORY, commandOrUrl = "",
            iconName = "clawmem", author = "yoloshii",
            pricing = McpPricingType.FREE,
            requiredFields = listOf("Your host MCP endpoint and any required credentials"),
            toolCapabilities = listOf("Hybrid search and memory for desktop AI agents."),
            websiteUrl = "https://github.com/yoloshii/ClawMem",
            documentationOnly = false,
            setupInstructions = "Run ClawMem and its indexes on the computer. Publish its MCP server through a Streamable HTTP adapter. Automatic desktop hooks are not Android hooks; connect and choose the tools explicitly."
        ),
        McpPreset(
            id = "uteke", name = "uteke", description = "Local-first memory engine with semantic search.",
            category = McpCategory.MEMORY, commandOrUrl = "",
            iconName = "uteke", author = "codecoradev",
            pricing = McpPricingType.FREE,
            requiredFields = listOf("Your host MCP endpoint and any required credentials"),
            toolCapabilities = listOf("Local-first memory engine with semantic search."),
            websiteUrl = "https://github.com/codecoradev/uteke",
            documentationOnly = false,
            setupInstructions = "Install the supported host binary and initialize the memory store on your computer. Expose its MCP interface through Streamable HTTP. A desktop Rust binary is not an Android library."
        ),
        McpPreset(
            id = "sibyl-memory", name = "Sibyl Memory", description = "File-based long-term agent memory.",
            category = McpCategory.MEMORY, commandOrUrl = "",
            iconName = "sibyl-memory", author = "Sibyl-Labs",
            pricing = McpPricingType.FREE,
            requiredFields = listOf("Your host MCP endpoint and any required credentials"),
            toolCapabilities = listOf("File-based long-term agent memory."),
            websiteUrl = "https://github.com/Sibyl-Labs/Sibyl-Memory",
            documentationOnly = false,
            setupInstructions = "Configure the memory directory and MCP server on your computer; expose Streamable HTTP through an adapter if required. Grant access only to the intended memory directory."
        ),
        McpPreset(
            id = "agentset", name = "Agentset", description = "Document retrieval with citations through an external RAG deployment.",
            category = McpCategory.PRODUCTIVITY, commandOrUrl = "",
            iconName = "agentset", author = "agentset-ai",
            pricing = McpPricingType.FREE,
            requiredFields = listOf("Your host MCP endpoint and any required credentials"),
            toolCapabilities = listOf("Document retrieval with citations through an external RAG deployment."),
            websiteUrl = "https://github.com/agentset-ai/agentset",
            documentationOnly = false,
            setupInstructions = "Deploy Agentset or use your account deployment. Configure document ingestion and its MCP endpoint, then paste the endpoint and credentials here. Storage and model processing follow that deployment, not the phone Memory settings."
        ),
        McpPreset(
            id = "dbhub", name = "DBHub", description = "Inspect schemas and query databases through a compact MCP server.",
            category = McpCategory.DATABASE, commandOrUrl = "",
            iconName = "dbhub", author = "bytebase",
            pricing = McpPricingType.FREE,
            requiredFields = listOf("Your host MCP endpoint and any required credentials"),
            toolCapabilities = listOf("Inspect schemas and query databases through a compact MCP server."),
            websiteUrl = "https://github.com/bytebase/dbhub",
            documentationOnly = false,
            setupInstructions = "Run DBHub on your computer with --transport http, your database DSN and read-only credentials. Configure Bearer authentication for network access and enter its /mcp URL. Database credentials stay on the host. Limit query duration and rows there."
        ),
        McpPreset(
            id = "chat2db", name = "Chat2DB", description = "Desktop database application with AI and MCP client features.",
            category = McpCategory.DATABASE, commandOrUrl = "",
            iconName = "chat2db", author = "OtterMind",
            pricing = McpPricingType.FREE,
            requiredFields = listOf("Your host MCP endpoint and any required credentials"),
            toolCapabilities = listOf("Desktop database application with AI and MCP client features."),
            websiteUrl = "https://github.com/OtterMind/Chat2DB",
            documentationOnly = true,
            setupInstructions = "This is a separate database application, not a verified server endpoint for Android. Use its desktop guide, or choose DBHub for a documented MCP database server."
        ),
        McpPreset(
            id = "dbx", name = "DBX", description = "Connect to the MCP server of a DBX database workspace.",
            category = McpCategory.DATABASE, commandOrUrl = "",
            iconName = "dbx", author = "t8y2",
            pricing = McpPricingType.FREE,
            requiredFields = listOf("Your host MCP endpoint and any required credentials"),
            toolCapabilities = listOf("Connect to the MCP server of a DBX database workspace."),
            websiteUrl = "https://github.com/t8y2/dbx",
            documentationOnly = false,
            setupInstructions = "Install DBX on your computer, configure database access and enable its MCP server. Use a Streamable HTTP adapter if its enabled transport is STDIO. Paste the MCP URL, not a SQL database connection string."
        ),
        McpPreset(
            id = "mcp-sqlite", name = "MCP SQLite", description = "Inspect and query a SQLite file on your computer.",
            category = McpCategory.DATABASE, commandOrUrl = "",
            iconName = "mcp-sqlite", author = "jparkerweb",
            pricing = McpPricingType.FREE,
            requiredFields = listOf("Your host MCP endpoint and any required credentials"),
            toolCapabilities = listOf("Inspect and query a SQLite file on your computer."),
            websiteUrl = "https://github.com/jparkerweb/mcp-sqlite",
            documentationOnly = false,
            setupInstructions = "Run mcp-sqlite on the host with access to the chosen database file. Expose its STDIO server through a Streamable HTTP adapter. Use a read-only database or restricted copy unless writes are intended. This does not grant access to GPT Mobile internal databases."
        ),
        McpPreset(
            id = "mcp-alchemy", name = "MCP Alchemy", description = "SQLAlchemy-based access to databases on a host computer.",
            category = McpCategory.DATABASE, commandOrUrl = "",
            iconName = "mcp-alchemy", author = "runekaagaard",
            pricing = McpPricingType.FREE,
            requiredFields = listOf("Your host MCP endpoint and any required credentials"),
            toolCapabilities = listOf("SQLAlchemy-based access to databases on a host computer."),
            websiteUrl = "https://github.com/runekaagaard/mcp-alchemy",
            documentationOnly = false,
            setupInstructions = "Install the server and database driver on the host and configure its database URL there. Bridge its MCP transport to Streamable HTTP. Use restricted database credentials; do not paste database passwords into the MCP endpoint."
        ),
        McpPreset(
            id = "tabularis", name = "Tabularis", description = "Access the built-in MCP server of a desktop SQL workspace.",
            category = McpCategory.DATABASE, commandOrUrl = "",
            iconName = "tabularis", author = "TabularisDB",
            pricing = McpPricingType.FREE,
            requiredFields = listOf("Your host MCP endpoint and any required credentials"),
            toolCapabilities = listOf("Access the built-in MCP server of a desktop SQL workspace."),
            websiteUrl = "https://github.com/TabularisDB/tabularis",
            documentationOnly = false,
            setupInstructions = "Install Tabularis on your computer and enable its MCP server. Expose a Streamable HTTP endpoint directly or with an adapter. Select the intended database connections and restrict write access on the host."
        ),
        McpPreset(
            id = "filesystem-mcp", name = "Filesystem MCP", description = "File and development operations on an explicitly configured host workspace.",
            category = McpCategory.SYSTEM, commandOrUrl = "",
            iconName = "filesystem-mcp", author = "sandraschi",
            pricing = McpPricingType.FREE,
            requiredFields = listOf("Your host MCP endpoint and any required credentials"),
            toolCapabilities = listOf("File and development operations on an explicitly configured host workspace."),
            websiteUrl = "https://github.com/sandraschi/filesystem-mcp",
            documentationOnly = false,
            setupInstructions = "Run the server on your computer with a restricted workspace and expose Streamable HTTP. Android cannot launch Docker or arbitrary host processes. Review write, delete and terminal tools before enabling them."
        ),
        McpPreset(
            id = "mcp-workspace-server", name = "MCP Workspace Server", description = "External development workspace for code and application tasks.",
            category = McpCategory.DEVELOPMENT, commandOrUrl = "",
            iconName = "mcp-workspace-server", author = "answerlink",
            pricing = McpPricingType.FREE,
            requiredFields = listOf("Your host MCP endpoint and any required credentials"),
            toolCapabilities = listOf("External development workspace for code and application tasks."),
            websiteUrl = "https://github.com/answerlink/MCP-Workspace-Server",
            documentationOnly = false,
            setupInstructions = "Deploy the workspace on your computer or server and expose its MCP interface over Streamable HTTP. Configure filesystem boundaries and execution permissions on the host. Runtime execution is not bundled into this APK."
        ),
        McpPreset(
            id = "terminal-guardian-mcp", name = "Terminal Guardian", description = "Host terminal access with configurable execution boundaries.",
            category = McpCategory.SYSTEM, commandOrUrl = "",
            iconName = "terminal-guardian-mcp", author = "7Majesty-M",
            pricing = McpPricingType.FREE,
            requiredFields = listOf("Your host MCP endpoint and any required credentials"),
            toolCapabilities = listOf("Host terminal access with configurable execution boundaries."),
            websiteUrl = "https://github.com/7Majesty-M/terminal-guardian-mcp",
            documentationOnly = false,
            setupInstructions = "Install on the host and configure the allowed commands and filesystem sandbox before exposing its MCP server through Streamable HTTP. Terminal commands run on that host, not on the phone."
        ),
        McpPreset(
            id = "flyenv", name = "FlyEnv", description = "Connect selected capabilities of a FlyEnv development environment.",
            category = McpCategory.DEVELOPMENT, commandOrUrl = "",
            iconName = "flyenv", author = "xpf0000",
            pricing = McpPricingType.FREE,
            requiredFields = listOf("Your host MCP endpoint and any required credentials"),
            toolCapabilities = listOf("Connect selected capabilities of a FlyEnv development environment."),
            websiteUrl = "https://github.com/xpf0000/FlyEnv",
            documentationOnly = false,
            setupInstructions = "Install FlyEnv on your computer and configure its built-in MCP server. If it launches over STDIO, use a Streamable HTTP adapter. Share only the intended local environment capabilities."
        ),
        McpPreset(
            id = "smart-connections-mcp", name = "Smart Connections", description = "Semantic search over an Obsidian vault with existing embeddings.",
            category = McpCategory.PRODUCTIVITY, commandOrUrl = "",
            iconName = "smart-connections-mcp", author = "msdanyg",
            pricing = McpPricingType.FREE,
            requiredFields = listOf("Your host MCP endpoint and any required credentials"),
            toolCapabilities = listOf("Semantic search over an Obsidian vault with existing embeddings."),
            websiteUrl = "https://github.com/msdanyg/smart-connections-mcp",
            documentationOnly = false,
            setupInstructions = "On your computer install Node and the Smart Connections Obsidian plugin, then generate vault embeddings. Run smart-connections-mcp and bridge its STDIO server to Streamable HTTP. The phone connects to that server; it does not scan your desktop vault directly."
        ),
        McpPreset(
            id = "code-memory", name = "Code Memory", description = "Search indexed source code and Git history on a host computer.",
            category = McpCategory.DEVELOPMENT, commandOrUrl = "",
            iconName = "code-memory", author = "kapillamba4",
            pricing = McpPricingType.FREE,
            requiredFields = listOf("Your host MCP endpoint and any required credentials"),
            toolCapabilities = listOf("Search indexed source code and Git history on a host computer."),
            websiteUrl = "https://github.com/kapillamba4/code-memory",
            documentationOnly = false,
            setupInstructions = "Index the intended repository on your computer and expose the project MCP server through Streamable HTTP. Configure embedding models and repository access on the host."
        ),
        McpPreset(
            id = "unreal-mcp", name = "Unreal MCP", description = "Control Unreal Editor through its workstation plugin and MCP server.",
            category = McpCategory.DEVELOPMENT, commandOrUrl = "",
            iconName = "unreal-mcp", author = "GenOrca",
            pricing = McpPricingType.FREE,
            requiredFields = listOf("Your host MCP endpoint and any required credentials"),
            toolCapabilities = listOf("Control Unreal Editor through its workstation plugin and MCP server."),
            websiteUrl = "https://github.com/GenOrca/unreal-mcp",
            documentationOnly = false,
            setupInstructions = "Install the matching Unreal plugin and Python MCP server on your workstation; keep the editor open. Bridge the documented STDIO server to Streamable HTTP and paste that adapter URL here. Review scene-editing and Python execution tools before enabling them."
        ),
        McpPreset(
            id = "arcade-mcp", name = "Arcade MCP Framework", description = "Build and expose custom MCP capabilities with Arcade.",
            category = McpCategory.DEVELOPMENT, commandOrUrl = "",
            iconName = "arcade-mcp", author = "ArcadeAI",
            pricing = McpPricingType.FREE,
            requiredFields = listOf("Your host MCP endpoint and any required credentials"),
            toolCapabilities = listOf("Build and expose custom MCP capabilities with Arcade."),
            websiteUrl = "https://github.com/ArcadeAI/arcade-mcp",
            documentationOnly = false,
            setupInstructions = "This framework needs a deployed server containing your chosen tools. Build and configure that server on a host, expose Streamable HTTP and enter its URL. Installing a framework alone does not provide tools."
        ),
        McpPreset(
            id = "llm-server-docs", name = "Private LLM Server Guide", description = "Documentation for a private LLM, RAG and media server.",
            category = McpCategory.DEVELOPMENT, commandOrUrl = "",
            iconName = "llm-server-docs", author = "varunvasudeva1",
            pricing = McpPricingType.FREE,
            requiredFields = listOf("Your host MCP endpoint and any required credentials"),
            toolCapabilities = listOf("Documentation for a private LLM, RAG and media server."),
            websiteUrl = "https://github.com/varunvasudeva1/llm-server-docs",
            documentationOnly = true,
            setupInstructions = "This is a deployment guide, not an MCP server. Follow it on your computer and add the resulting inference API as an AI profile; add any separately deployed MCP endpoint as a tool connection."
        ),
        McpPreset(
            id = "decisionnode", name = "DecisionNode", description = "Shared structured memory across MCP clients.",
            category = McpCategory.MEMORY, commandOrUrl = "",
            iconName = "decisionnode", author = "decisionnode",
            pricing = McpPricingType.FREE,
            requiredFields = listOf("Your host MCP endpoint and any required credentials"),
            toolCapabilities = listOf("Shared structured memory across MCP clients."),
            websiteUrl = "https://github.com/decisionnode/DecisionNode",
            documentationOnly = false,
            setupInstructions = "Configure DecisionNode storage and its MCP server on your host. Expose Streamable HTTP through an adapter if needed, then connect using the host endpoint. This memory store is independent of the phone Memory."
        ),
        McpPreset(
            id = "qodex", name = "QodeX", description = "Standalone local-first CLI agent.",
            category = McpCategory.DEVELOPMENT, commandOrUrl = "",
            iconName = "qodex", author = "QodeXcli",
            pricing = McpPricingType.FREE,
            requiredFields = listOf("Your host MCP endpoint and any required credentials"),
            toolCapabilities = listOf("Standalone local-first CLI agent."),
            websiteUrl = "https://github.com/QodeXcli/QodeX",
            documentationOnly = true,
            setupInstructions = "This is a separate CLI agent. Use its desktop setup guide; a CLI agent is not automatically a callable MCP server. GPT Mobile already supplies its own chat and agent runtime."
        ),
        McpPreset(
            id = "deskdrop", name = "Deskdrop", description = "Android AI keyboard with local-server and cloud integrations.",
            category = McpCategory.PRODUCTIVITY, commandOrUrl = "",
            iconName = "deskdrop", author = "SvReenen",
            pricing = McpPricingType.FREE,
            requiredFields = listOf("Your host MCP endpoint and any required credentials"),
            toolCapabilities = listOf("Android AI keyboard with local-server and cloud integrations."),
            websiteUrl = "https://github.com/SvReenen/Deskdrop",
            documentationOnly = true,
            setupInstructions = "Install Deskdrop separately as an Android keyboard. It is not an MCP tool endpoint. Configure its inference/Whisper server independently; keyboard access is managed by Android Settings."
        )
    )
}
