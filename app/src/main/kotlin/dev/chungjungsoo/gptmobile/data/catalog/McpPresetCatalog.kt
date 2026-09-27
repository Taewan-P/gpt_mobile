package dev.chungjungsoo.gptmobile.data.catalog

import java.net.URI
import java.net.URLDecoder
import kotlinx.serialization.Serializable

/**
 * Transport types visible in the marketplace.
 *
 * GPT Mobile can directly connect to remote MCP servers using Streamable HTTP.
 * STDIO is retained only to describe app-integrated tools; Android does not pretend
 * that a remote STDIO package is installable when no local process host exists.
 */
@Serializable
enum class McpTransportType {
    STREAMABLE_HTTP,
    STDIO
}

@Serializable
enum class McpCategory(val displayName: String) {
    SEARCH("Search"),
    DEVELOPMENT("Development"),
    SYSTEM("System"),
    DATABASE("Database"),
    BROWSER("Browser"),
    PRODUCTIVITY("Productivity"),
    MEMORY("Memory"),
    THREADING("Threading")
}

@Serializable
enum class McpPricingType(val displayName: String) {
    FREE("Free"),
    FREE_WITH_SIGNUP("Free with sign up"),
    PAID("Paid")
}

typealias McpPresetCategory = McpCategory

@Serializable
data class McpPreset(
    val id: String,
    val name: String,
    val description: String,
    val category: McpCategory,
    val commandOrUrl: String,
    val transportType: McpTransportType = McpTransportType.STREAMABLE_HTTP,
    val headers: Map<String, String> = emptyMap(),
    val iconName: String = "extension",
    val author: String = "Community",
    val alias: String = id.replace("-", "_"),
    val suggestedAuthType: String = "NONE",
    val pricing: McpPricingType = McpPricingType.FREE,
    val requiredFields: List<String> = emptyList(),
    val toolCapabilities: List<String> = emptyList(),
    val websiteUrl: String = "",
    val isPreinstalled: Boolean = false,
    val verifiedRemote: Boolean = false,
    val setupInstructions: String = "",
    val requiredEndpointQueryParameter: String? = null,
    val integratedTool: String? = null,
    val documentationOnly: Boolean = false,
    /** General web search tools folded into the profile's shared web_search when enabled. */
    val webSearchToolNames: List<String> = emptyList()
) {
    fun hasRequiredEndpointParameters(endpoint: String): Boolean {
        val parameter = requiredEndpointQueryParameter ?: return true
        return runCatching {
            val values = URI(endpoint.trim()).rawQuery.orEmpty().split("&").map { part ->
                val pair = part.split("=", limit = 2)
                URLDecoder.decode(pair.first(), "UTF-8") to URLDecoder.decode(pair.getOrElse(1) { "" }, "UTF-8")
            }.filter { it.first == parameter }.map { it.second.trim() }
            values.size == 1 &&
                values.single().isNotBlank() &&
                !values.single().contains("YOUR_", ignoreCase = true) &&
                !values.single().contains("<") &&
                !values.single().contains("{")
        }.getOrDefault(false)
    }

    val url: String get() = commandOrUrl
    val defaultEndpoint: String get() = commandOrUrl
    val isDirectlyInstallable: Boolean
        get() = !isPreinstalled && !documentationOnly && transportType == McpTransportType.STREAMABLE_HTTP && commandOrUrl.startsWith("https://") && requiredEndpointQueryParameter == null
}

typealias McpServerPreset = McpPreset

/**
 * Curated marketplace catalog.
 *
 * Only presets that GPT Mobile can actually use are surfaced:
 * - app-integrated built-ins, or
 * - remote Streamable HTTP endpoints.
 *
 * We intentionally do not advertise arbitrary localhost /sse or STDIO package examples
 * as one-tap installs because the Android client does not launch Node/Python MCP servers.
 */
object McpPresetCatalog {
    val presets = LocalMcpPresets.presets + listOf(
        McpPreset(
            id = "linear",
            name = "Linear",
            description = "Search and manage issues, projects and comments in your Linear workspace.",
            category = McpCategory.PRODUCTIVITY,
            commandOrUrl = "https://mcp.linear.app/mcp",
            iconName = "linear",
            author = "Linear",
            suggestedAuthType = "OAUTH",
            pricing = McpPricingType.FREE_WITH_SIGNUP,
            requiredFields = listOf("Linear account and authorized access"),
            toolCapabilities = listOf("Search issues", "Manage projects", "Create and update work items"),
            websiteUrl = "https://linear.app/docs/mcp",
            verifiedRemote = false,
            setupInstructions = "Save, then Authorize in connection settings. Alternatively select Bearer / API Key and use a Linear API key. For read-only tools, change the endpoint to https://mcp.linear.app/mcp/readonly."
        ),
        McpPreset(
            id = "sentry",
            name = "Sentry",
            description = "Investigate application errors and performance with context from Sentry.",
            category = McpCategory.DEVELOPMENT,
            commandOrUrl = "https://mcp.sentry.dev/mcp",
            iconName = "sentry",
            author = "Sentry",
            suggestedAuthType = "OAUTH",
            pricing = McpPricingType.FREE_WITH_SIGNUP,
            requiredFields = listOf("Sentry account and authorized access"),
            toolCapabilities = listOf("Find issues", "Analyze errors and performance", "Inspect project context"),
            websiteUrl = "https://mcp.sentry.dev/",
            verifiedRemote = false,
            setupInstructions = "Requires a Sentry account with project access. Save, then Authorize in connection settings. Optionally append /{organizationSlug}/{projectSlug} using your actual organization and project to limit scope."
        ),
        McpPreset(
            id = "vercel",
            name = "Vercel",
            description = "Explore documentation, projects, deployments and logs through the official Vercel MCP.",
            category = McpCategory.DEVELOPMENT,
            commandOrUrl = "https://mcp.vercel.com",
            iconName = "vercel",
            author = "Vercel",
            suggestedAuthType = "OAUTH",
            pricing = McpPricingType.FREE_WITH_SIGNUP,
            requiredFields = listOf("Vercel account and authorized access"),
            toolCapabilities = listOf("Search documentation", "Inspect deployments", "Read deployment logs"),
            websiteUrl = "https://vercel.com/docs/agent-resources/vercel-mcp",
            verifiedRemote = false,
            setupInstructions = "Vercel restricts authenticated access to approved clients. GPT Mobile approval and Android sign-in are not verified. Obtain an approved public OAuth client registration before authorizing; configure its client ID in connection settings. Availability and usage depend on your Vercel plan."
        ),
        McpPreset(
            id = "atlassian",
            name = "Atlassian Rovo",
            description = "Search and manage Jira work items and Confluence content with existing workspace permissions.",
            category = McpCategory.PRODUCTIVITY,
            commandOrUrl = "https://mcp.atlassian.com/v2/mcp",
            iconName = "atlassian",
            author = "Atlassian",
            suggestedAuthType = "OAUTH",
            pricing = McpPricingType.FREE_WITH_SIGNUP,
            requiredFields = listOf("Atlassian account and authorized access"),
            toolCapabilities = listOf("Search Jira and Confluence", "Create and update work items", "Retrieve workspace context"),
            websiteUrl = "https://developer.atlassian.com/cloud/rovo-mcp/",
            verifiedRemote = false,
            setupInstructions = "Requires an Atlassian Cloud account and workspace access; your administrator may restrict MCP access. Save, then Authorize in connection settings. Some tools consume Rovo credits. Android OAuth redirect acceptance has not been verified."
        ),
        McpPreset(
            id = "cloudflare-browser",
            name = "Cloudflare Browser Rendering",
            description = "Render web pages, extract content and capture screenshots through Cloudflare.",
            category = McpCategory.BROWSER,
            commandOrUrl = "https://browser.mcp.cloudflare.com/mcp",
            iconName = "cloudflare",
            author = "Cloudflare",
            suggestedAuthType = "OAUTH",
            pricing = McpPricingType.FREE_WITH_SIGNUP,
            requiredFields = listOf("Cloudflare account and authorized access"),
            toolCapabilities = listOf("Render web pages", "Extract page content", "Capture screenshots"),
            websiteUrl = "https://github.com/cloudflare/mcp-server-cloudflare",
            verifiedRemote = false,
            setupInstructions = "Requires Cloudflare account access to Browser Rendering. Save, then Authorize in connection settings, or select Bearer / API Key with a suitably scoped Cloudflare API token. Usage limits and charges depend on your Cloudflare plan."
        ),
        McpPreset(
            id = "builtin-web",
            webSearchToolNames = listOf("web_search"),
            name = "Web Search & URL Reader",
            description = "Integrated search and webpage reading tools available to AI profiles without installing a separate MCP server.",
            category = McpCategory.SEARCH,
            commandOrUrl = "builtin://web",
            transportType = McpTransportType.STDIO,
            iconName = "online_search",
            author = "GPT Mobile AI",
            alias = "builtin_web",
            suggestedAuthType = "NONE",
            pricing = McpPricingType.FREE,
            toolCapabilities = listOf(
                "web_search: Search the web through the configured search backend",
                "read_url: Retrieve and normalize supported web content"
            ),
            isPreinstalled = true
        ),
        McpPreset(
            id = "device-location",
            name = "Device Location",
            description = "Native Android location tool. Profiles that enable it can request the phone's current coordinates after Android location permission is granted.",
            category = McpCategory.SYSTEM,
            commandOrUrl = "builtin://device_location",
            transportType = McpTransportType.STDIO,
            iconName = "location",
            author = "GPT Mobile AI",
            alias = "device_location",
            suggestedAuthType = "NONE",
            pricing = McpPricingType.FREE,
            toolCapabilities = listOf(
                "device_location: Current latitude, longitude, accuracy, altitude and provider metadata"
            ),
            isPreinstalled = true
        ),
        McpPreset(
            id = "github-all",
            name = "GitHub MCP — All Toolsets",
            description = "GitHub's hosted remote MCP server with all available toolsets. Supports repository, issue, pull request, Actions and other GitHub operations allowed by your token.",
            category = McpCategory.DEVELOPMENT,
            commandOrUrl = "https://api.githubcopilot.com/mcp/x/all",
            transportType = McpTransportType.STREAMABLE_HTTP,
            iconName = "github",
            author = "GitHub",
            alias = "github",
            suggestedAuthType = "BEARER",
            pricing = McpPricingType.FREE_WITH_SIGNUP,
            requiredFields = listOf("GitHub Personal Access Token"),
            toolCapabilities = listOf(
                "Repository and code operations",
                "Issues, pull requests and reviews",
                "GitHub Actions and workflow operations",
                "Additional GitHub MCP toolsets exposed by the hosted service"
            ),
            websiteUrl = "https://github.com/github/github-mcp-server/blob/main/docs/remote-server.md",
            verifiedRemote = true
        ),
        McpPreset(
            id = "github-readonly",
            name = "GitHub MCP — Read Only",
            description = "GitHub's hosted remote MCP server with all toolsets constrained to read-only operations.",
            category = McpCategory.DEVELOPMENT,
            commandOrUrl = "https://api.githubcopilot.com/mcp/x/all/readonly",
            transportType = McpTransportType.STREAMABLE_HTTP,
            iconName = "github",
            author = "GitHub",
            alias = "github_readonly",
            suggestedAuthType = "BEARER",
            pricing = McpPricingType.FREE_WITH_SIGNUP,
            requiredFields = listOf("GitHub Personal Access Token"),
            toolCapabilities = listOf(
                "Repository inspection and code search",
                "Read-only issues and pull request access",
                "Read-only workflow and Actions visibility"
            ),
            websiteUrl = "https://github.com/github/github-mcp-server/blob/main/docs/remote-server.md",
            verifiedRemote = true
        ),
        McpPreset(
            id = "exa-mcp",
            webSearchToolNames = listOf("web_search_exa", "web_search_advanced_exa", "deep_search_exa"),
            name = "Exa MCP Search",
            description = "Exa's hosted remote MCP endpoint for web search, code search, research and webpage retrieval. The hosted MCP service can be used without a separate local server.",
            category = McpCategory.SEARCH,
            commandOrUrl = "https://mcp.exa.ai/mcp",
            transportType = McpTransportType.STREAMABLE_HTTP,
            iconName = "exa",
            author = "Exa",
            alias = "exa_mcp",
            suggestedAuthType = "NONE",
            pricing = McpPricingType.FREE,
            toolCapabilities = listOf(
                "Web and news search",
                "Code search",
                "Research workflows",
                "Webpage content retrieval"
            ),
            websiteUrl = "https://exa.ai/mcp",
            verifiedRemote = true
        ),
        McpPreset(
            id = "context7", iconName = "context7", name = "Context7 Documentation", description = "Current library documentation and code examples. Public access has rate limits; choose Bearer to add a Context7 API key.",
            category = McpCategory.DEVELOPMENT, commandOrUrl = "https://mcp.context7.com/mcp", author = "Upstash",
            alias = "context7", suggestedAuthType = "NONE", pricing = McpPricingType.FREE,
            toolCapabilities = listOf("Resolve library identifiers", "Search current library documentation"),
            websiteUrl = "https://github.com/upstash/context7", verifiedRemote = true
        ),
        McpPreset(
            id = "tavily-mcp", iconName = "tavily", name = "Tavily Search & Crawl", description = "Search, extract, crawl and map websites through Tavily. Requires a Tavily API key; usage is subject to your plan.",
            webSearchToolNames = listOf("tavily_search"),
            category = McpCategory.SEARCH, commandOrUrl = "https://mcp.tavily.com/mcp/", author = "Tavily",
            alias = "tavily_mcp", suggestedAuthType = "BEARER", pricing = McpPricingType.FREE_WITH_SIGNUP,
            toolCapabilities = listOf("Search web", "Extract and crawl webpages", "Map website URLs"),
            websiteUrl = "https://github.com/tavily-ai/tavily-mcp", verifiedRemote = true
        ),
        McpPreset(
            id = "firecrawl-mcp", iconName = "firecrawl", name = "Firecrawl Web Research", description = "Hosted scrape, search and parse tools with a limited keyless tier. Choose Bearer for your Firecrawl API key and account limits.",
            webSearchToolNames = listOf("firecrawl_search"),
            category = McpCategory.SEARCH, commandOrUrl = "https://mcp.firecrawl.dev/v2/mcp", author = "Firecrawl",
            alias = "firecrawl_mcp", suggestedAuthType = "NONE", pricing = McpPricingType.FREE,
            toolCapabilities = listOf("Search web", "Scrape webpages", "Parse documents"),
            websiteUrl = "https://github.com/firecrawl/firecrawl-mcp-server", verifiedRemote = true
        ),
        McpPreset(
            id = "jina-mcp", iconName = "jina", name = "Jina Reader & Search", description = "Read webpages and retrieve content. Some tools, including search, require a Jina API key; choose Bearer to enable them.",
            webSearchToolNames = listOf("search_web"),
            category = McpCategory.SEARCH, commandOrUrl = "https://mcp.jina.ai/v1", author = "Jina AI",
            alias = "jina_mcp", suggestedAuthType = "NONE", pricing = McpPricingType.FREE,
            toolCapabilities = listOf("Read URLs", "Search with a Jina key", "Rerank results"),
            websiteUrl = "https://github.com/jina-ai/MCP", verifiedRemote = true
        ),
        McpPreset(
            id = "huggingface", iconName = "huggingface", name = "Hugging Face Hub", description = "Search models, datasets, papers and Spaces. Use a Hugging Face token and configure tools in your account.",
            category = McpCategory.DEVELOPMENT, commandOrUrl = "https://huggingface.co/mcp", author = "Hugging Face",
            alias = "huggingface", suggestedAuthType = "BEARER", pricing = McpPricingType.FREE_WITH_SIGNUP,
            toolCapabilities = listOf("Search models and datasets", "Find papers and Spaces", "Read model cards"),
            websiteUrl = "https://github.com/huggingface/hf-mcp-server", verifiedRemote = true
        ),
        McpPreset(
            id = "microsoft-learn", iconName = "microsoft", name = "Microsoft Learn", description = "Search official Microsoft documentation and code samples without an account.",
            category = McpCategory.DEVELOPMENT, commandOrUrl = "https://learn.microsoft.com/api/mcp", author = "Microsoft",
            alias = "microsoft_learn", suggestedAuthType = "NONE", pricing = McpPricingType.FREE,
            toolCapabilities = listOf("Search documentation", "Fetch documentation", "Search code samples"),
            websiteUrl = "https://github.com/MicrosoftDocs/mcp", verifiedRemote = true
        ),
        McpPreset(
            id = "cloudflare-docs", iconName = "cloudflare", name = "Cloudflare Documentation", description = "Search current Cloudflare product documentation using its public remote server.",
            category = McpCategory.DEVELOPMENT, commandOrUrl = "https://docs.mcp.cloudflare.com/mcp", author = "Cloudflare",
            alias = "cloudflare_docs", suggestedAuthType = "NONE", pricing = McpPricingType.FREE,
            toolCapabilities = listOf("Search Cloudflare documentation"),
            websiteUrl = "https://github.com/cloudflare/mcp-server-cloudflare", verifiedRemote = true
        ),
        McpPreset(
            id = "cloudflare-radar", iconName = "cloudflare", name = "Cloudflare Radar", description = "Explore internet traffic, outages and security insights. Requires a Cloudflare token with Radar permissions.",
            category = McpCategory.SEARCH, commandOrUrl = "https://radar.mcp.cloudflare.com/mcp", author = "Cloudflare",
            alias = "cloudflare_radar", suggestedAuthType = "BEARER", pricing = McpPricingType.FREE_WITH_SIGNUP,
            toolCapabilities = listOf("Internet traffic insights", "Outage and security trends"),
            websiteUrl = "https://github.com/cloudflare/mcp-server-cloudflare", verifiedRemote = true
        ),
        McpPreset(
            id = "neon", iconName = "neon", name = "Neon Postgres", description = "Inspect Neon projects and query Postgres in read-only mode. Requires a Neon API key. Scope projects with the endpoint query options.",
            category = McpCategory.DATABASE, commandOrUrl = "https://mcp.neon.tech/mcp?readonly=true", author = "Neon",
            alias = "neon", suggestedAuthType = "BEARER", pricing = McpPricingType.FREE_WITH_SIGNUP,
            toolCapabilities = listOf("Inspect projects and schemas", "Read-only SQL queries"),
            websiteUrl = "https://github.com/neondatabase/mcp-server-neon", verifiedRemote = true
        ),
        McpPreset(
            id = "supabase", iconName = "supabase", name = "Supabase Database", description = "Inspect Supabase projects, schema and data. Read-only SQL is selected by default; add project_ref to scope one project. Requires a personal access token.",
            category = McpCategory.DATABASE, commandOrUrl = "https://mcp.supabase.com/mcp?read_only=true", author = "Supabase",
            alias = "supabase", suggestedAuthType = "BEARER", pricing = McpPricingType.FREE_WITH_SIGNUP,
            toolCapabilities = listOf("Inspect projects and schema", "Read-only database queries", "Search documentation"),
            websiteUrl = "https://github.com/supabase/mcp", verifiedRemote = true
        ),
        McpPreset(
            id = "stripe", iconName = "stripe", name = "Stripe Account Tools", description = "Inspect and manage Stripe resources allowed by your Agent API key. Select only the tools your AI profile needs.",
            category = McpCategory.PRODUCTIVITY, commandOrUrl = "https://mcp.stripe.com", author = "Stripe",
            alias = "stripe", suggestedAuthType = "BEARER", pricing = McpPricingType.FREE_WITH_SIGNUP,
            toolCapabilities = listOf("Customer and payment resources", "Billing and product information", "Search Stripe documentation"),
            websiteUrl = "https://github.com/stripe/ai", verifiedRemote = true
        ),
        McpPreset(
            id = "cloudflare", iconName = "cloudflare", name = "Cloudflare API", description = "Search and call Cloudflare APIs with the permissions of your Cloudflare API token.",
            category = McpCategory.DEVELOPMENT, commandOrUrl = "https://mcp.cloudflare.com/mcp", author = "Cloudflare",
            alias = "cloudflare", suggestedAuthType = "BEARER", pricing = McpPricingType.FREE_WITH_SIGNUP,
            toolCapabilities = listOf("Search Cloudflare APIs", "Execute authorized API operations"),
            websiteUrl = "https://github.com/cloudflare/mcp", verifiedRemote = true
        ),
        McpPreset(
            id = "graphiti-memory",
            name = "Graphiti Memory",
            description = "Self-hosted temporal knowledge graph with hybrid search, source episodes and changing facts. Requires a reachable server, graph database and configured models.",
            category = McpCategory.MEMORY,
            commandOrUrl = "",
            iconName = "memory",
            author = "Zep",
            suggestedAuthType = "BEARER",
            pricing = McpPricingType.FREE,
            toolCapabilities = listOf("Search memory facts and entities", "Store episodes with provenance", "Track changing facts", "Automatic selected-connection recall"),
            websiteUrl = "https://github.com/getzep/graphiti/tree/main/mcp_server",
            setupInstructions = "Deploy the experimental Graphiti MCP server with HTTP transport and enter its phone-reachable /mcp/ endpoint. Configure authentication at your server or reverse proxy. Graphiti needs FalkorDB or Neo4j and an LLM/embedder; local compatible models are supported. Select search_memory_facts in the AI profile, then opt in under Memory → Controls. Use your configured group ID as the memory scope. Installing does not upload local memories or chat history."
        ),
        McpPreset(
            id = "mem0-hosted",
            name = "Mem0 Memory",
            description = "Store and recall account-backed memories across conversations and compatible clients. Usage depends on your Mem0 plan.",
            category = McpCategory.MEMORY,
            commandOrUrl = "https://mcp.mem0.ai/mcp",
            iconName = "mem0",
            author = "Mem0",
            suggestedAuthType = "OAUTH",
            pricing = McpPricingType.FREE_WITH_SIGNUP,
            toolCapabilities = listOf("Save, search and update memories", "Manage memory entities and events"),
            websiteUrl = "https://docs.mem0.ai/platform/mem0-mcp",
            verifiedRemote = true,
            setupInstructions = "Sign in to Mem0 in your browser, or select Bearer and enter a Mem0 API key. Enable search_memories in the AI profile, then opt in to automatic recall under Memory → Controls and set your user ID if required. Installing does not automatically upload local memories or chat history."
        ),
        McpPreset(
            id = "supermemory",
            name = "Supermemory",
            description = "Persistent memory shared across compatible AI clients, with account-controlled access to memory spaces.",
            category = McpCategory.MEMORY,
            commandOrUrl = "https://mcp.supermemory.ai/mcp",
            iconName = "supermemory",
            author = "Supermemory",
            suggestedAuthType = "OAUTH",
            pricing = McpPricingType.FREE_WITH_SIGNUP,
            toolCapabilities = listOf("Store and retrieve memories", "Search context across authorized spaces"),
            websiteUrl = "https://supermemory.ai/mcp/",
            verifiedRemote = true,
            setupInstructions = "Sign in and choose the spaces this connection may access. Enable search_memory in the AI profile, then opt in to automatic recall under Memory → Controls. Use a space key as the optional memory scope. Service limits and charges depend on your plan; installing does not upload local memories or chat history."
        ),
        McpPreset(
            id = "mnemoverse",
            name = "Mnemoverse",
            description = "Shared persistent memory with semantic recall and feedback-based ranking.",
            category = McpCategory.MEMORY,
            commandOrUrl = "https://mcp.mnemoverse.com/mcp",
            iconName = "mnemoverse",
            author = "Mnemoverse",
            suggestedAuthType = "OAUTH",
            pricing = McpPricingType.FREE_WITH_SIGNUP,
            toolCapabilities = listOf("Store and recall memories", "Share context across connected clients"),
            websiteUrl = "https://mnemoverse.com/docs/api/integrations",
            verifiedRemote = true,
            setupInstructions = "Browser sign-in uses OAuth with PKCE. Enable the tools you want in your AI profile."
        ),
        McpPreset(
            id = "pearls",
            name = "Pearls",
            description = "Self-hosted AI continuity server with transmissions organized into access-controlled threads. Hosting may incur costs.",
            category = McpCategory.THREADING,
            commandOrUrl = "",
            iconName = "pearls",
            author = "Garblesnarff",
            suggestedAuthType = "OAUTH",
            pricing = McpPricingType.FREE,
            toolCapabilities = listOf("Create and search pearls", "List and create threads", "Retrieve recent transmissions and handshake context"),
            websiteUrl = "https://github.com/Garblesnarff/pearls",
            verifiedRemote = false,
            setupInstructions = "Deploy Pearls on a server reachable from your phone, then enter its HTTPS /mcp URL. Configure OAuth for your deployment, or select Bearer for a pearl_ API key. No public hosted endpoint is supplied."
        ),
        McpPreset(
            id = "brave-search",
            webSearchToolNames = listOf("brave_web_search"),
            name = "Brave Search",
            description = "Web, local, image, video and news search through a self-hosted Brave MCP server. API usage depends on your Brave plan.",
            category = McpCategory.SEARCH,
            commandOrUrl = "",
            iconName = "brave",
            author = "Brave",
            suggestedAuthType = "NONE",
            pricing = McpPricingType.FREE_WITH_SIGNUP,
            toolCapabilities = listOf("Search web and local places", "Search images, videos and news"),
            websiteUrl = "https://github.com/brave/brave-search-mcp-server",
            verifiedRemote = false,
            setupInstructions = "For direct web search, choose Add connection → Web search → Brave Search and enter your Brave API key. For MCP, run @brave/brave-search-mcp-server with --transport http and BRAVE_API_KEY configured on the server. Enter its phone-reachable /mcp URL and select your deployment authentication. Enable brave_web_search in your AI profile to include it in integrated web search. The Brave API key belongs on the MCP server, not in its bearer credential field."
        ),
        McpPreset(
            id = "stackoverflow",
            name = "Stack Overflow",
            description = "Access Stack Overflow knowledge through its remote MCP service. Availability and capabilities depend on your account.",
            category = McpCategory.DEVELOPMENT,
            commandOrUrl = "https://mcp.stackoverflow.com",
            iconName = "stackoverflow",
            author = "Stack Overflow",
            suggestedAuthType = "OAUTH",
            pricing = McpPricingType.FREE_WITH_SIGNUP,
            toolCapabilities = listOf("Search developer questions and answers"),
            websiteUrl = "https://stackoverflow.com/help/mcp-server",
            verifiedRemote = true,
            setupInstructions = "Sign in with a Stack Overflow account. Review the service documentation for current access requirements."
        ),
        McpPreset(
            id = "semgrep",
            name = "Semgrep",
            description = "Security analysis through Semgrep's hosted MCP service. Hosted capabilities and access requirements can change.",
            category = McpCategory.DEVELOPMENT,
            commandOrUrl = "https://mcp.semgrep.ai/mcp",
            iconName = "semgrep",
            author = "Semgrep",
            suggestedAuthType = "OAUTH",
            pricing = McpPricingType.FREE_WITH_SIGNUP,
            toolCapabilities = listOf("Analyze code for security issues", "Retrieve supported security findings"),
            websiteUrl = "https://github.com/semgrep/mcp",
            verifiedRemote = true,
            setupInstructions = "The hosted service currently requires authentication. Use browser sign-in; the older unauthenticated setup is no longer sufficient. The standalone repository has moved into the main Semgrep project."
        ),
        McpPreset(
            id = "deepwiki",
            name = "DeepWiki",
            description = "Read AI-generated documentation and ask questions about indexed public GitHub repositories.",
            category = McpCategory.DEVELOPMENT,
            commandOrUrl = "https://mcp.deepwiki.com/mcp",
            iconName = "deepwiki",
            author = "Cognition",
            suggestedAuthType = "NONE",
            pricing = McpPricingType.FREE,
            toolCapabilities = listOf("Read repository documentation", "Ask questions about a public codebase"),
            websiteUrl = "https://docs.devin.ai/work-with-devin/deepwiki-mcp",
            verifiedRemote = true,
            setupInstructions = "Public repositories only; private repository access uses a separate authenticated service."
        ),
        McpPreset(
            id = "netlify",
            name = "Netlify",
            description = "Manage Netlify sites and deployments using your account permissions.",
            category = McpCategory.DEVELOPMENT,
            commandOrUrl = "https://netlify-mcp.netlify.app/mcp",
            iconName = "netlify",
            author = "Netlify",
            suggestedAuthType = "OAUTH",
            pricing = McpPricingType.FREE_WITH_SIGNUP,
            toolCapabilities = listOf("Inspect sites and deployments", "Manage authorized site resources"),
            websiteUrl = "https://docs.netlify.com/build/build-with-ai/agent-setup-guides/set-up-claude-code-for-netlify/",
            verifiedRemote = true,
            setupInstructions = "Sign in to Netlify. Site hosting and resource usage are subject to your account plan."
        ),
        McpPreset(
            id = "airtable",
            name = "Airtable",
            description = "Work with Airtable bases, records and other resources available to your account.",
            category = McpCategory.DATABASE,
            commandOrUrl = "https://mcp.airtable.com/mcp",
            iconName = "airtable",
            author = "Airtable",
            suggestedAuthType = "OAUTH",
            pricing = McpPricingType.FREE_WITH_SIGNUP,
            toolCapabilities = listOf("Read and update records", "Explore authorized bases and schemas"),
            websiteUrl = "https://support.airtable.com/articles/9897799762-using-the-airtable-mcp-server",
            verifiedRemote = true,
            setupInstructions = "Sign in to Airtable and select permitted resources. Your account permissions and API limits apply."
        ),
        McpPreset(
            id = "prisma",
            name = "Prisma Postgres",
            description = "Manage Prisma Postgres databases and related workspace resources.",
            category = McpCategory.DATABASE,
            commandOrUrl = "https://mcp.prisma.io/mcp",
            iconName = "prisma",
            author = "Prisma",
            suggestedAuthType = "OAUTH",
            pricing = McpPricingType.FREE_WITH_SIGNUP,
            toolCapabilities = listOf("Inspect database schemas and queries", "Manage databases and deployments"),
            websiteUrl = "https://www.prisma.io/docs/ai/tools/mcp-server",
            verifiedRemote = true,
            setupInstructions = "Sign in and choose a Prisma workspace. Database and infrastructure usage depend on your plan."
        ),
        McpPreset(
            id = "slack",
            name = "Slack",
            description = "Search and work with Slack conversations and resources using an approved Slack app.",
            category = McpCategory.PRODUCTIVITY,
            commandOrUrl = "https://mcp.slack.com/mcp",
            iconName = "slack",
            author = "Slack",
            suggestedAuthType = "BEARER",
            pricing = McpPricingType.FREE_WITH_SIGNUP,
            toolCapabilities = listOf("Search messages and channels", "Read threads and authorized workspace resources"),
            websiteUrl = "https://docs.slack.dev/ai/slack-mcp-server/",
            verifiedRemote = true,
            setupInstructions = "Requires a registered internal or directory-published Slack app, workspace approval, and a user token with MCP tool scopes. Dynamic client registration is unsupported. Use an authorized user bearer token, or configure your own OAuth client ID in connection settings.",
            requiredFields = listOf("Authorized Slack user token")
        ),
        McpPreset(
            id = "asana",
            name = "Asana",
            description = "Read and manage Asana projects and tasks with the permissions of your connected account.",
            category = McpCategory.PRODUCTIVITY,
            commandOrUrl = "https://mcp.asana.com/v2/mcp",
            iconName = "asana",
            author = "Asana",
            suggestedAuthType = "OAUTH",
            pricing = McpPricingType.FREE_WITH_SIGNUP,
            toolCapabilities = listOf("Search projects and tasks", "Create and update authorized work items"),
            websiteUrl = "https://developers.asana.com/docs/using-asanas-mcp-server",
            verifiedRemote = true,
            setupInstructions = "Sign in with Asana. Workspace policies and your account plan determine access."
        ),
        McpPreset(
            id = "todoist",
            name = "Todoist",
            description = "Manage tasks and projects through Todoist's official hosted server.",
            category = McpCategory.PRODUCTIVITY,
            commandOrUrl = "https://ai.todoist.net/mcp",
            iconName = "todoist",
            author = "Doist",
            suggestedAuthType = "OAUTH",
            pricing = McpPricingType.FREE_WITH_SIGNUP,
            toolCapabilities = listOf("Find and organize tasks", "Create and update tasks and projects"),
            websiteUrl = "https://developer.todoist.com/",
            verifiedRemote = true,
            setupInstructions = "Sign in with Todoist to authorize the connection."
        ),
        McpPreset(
            id = "google-drive",
            name = "Google Drive",
            description = "Access Google Drive files through the Workspace MCP developer preview.",
            category = McpCategory.PRODUCTIVITY,
            commandOrUrl = "https://drivemcp.googleapis.com/mcp/v1",
            iconName = "googledrive",
            author = "Google",
            suggestedAuthType = "BEARER",
            pricing = McpPricingType.FREE_WITH_SIGNUP,
            toolCapabilities = listOf("Search and read files", "Inspect file metadata and permissions"),
            websiteUrl = "https://developers.google.com/workspace/guides/configure-mcp-servers",
            verifiedRemote = true,
            setupInstructions = "Requires Workspace Developer Preview access, the MCP API enabled in a Google Cloud project, and a scoped Google OAuth access token. Paste the access token as Bearer; refresh expired tokens yourself. Generic MCP browser sign-in is not configured for Google.",
            requiredFields = listOf("OAuth access token")
        ),
        McpPreset(
            id = "google-sheets",
            name = "Google Sheets",
            description = "Read and update spreadsheets through the Workspace MCP developer preview.",
            category = McpCategory.PRODUCTIVITY,
            commandOrUrl = "https://sheetsmcp.googleapis.com/mcp/v1",
            iconName = "googlesheets",
            author = "Google",
            suggestedAuthType = "BEARER",
            pricing = McpPricingType.FREE_WITH_SIGNUP,
            toolCapabilities = listOf("Read sheets and cell values", "Update values and spreadsheet properties"),
            websiteUrl = "https://developers.google.com/workspace/sheets/api/guides/configure-mcp-server",
            verifiedRemote = true,
            setupInstructions = "Requires Workspace Developer Preview access, the MCP API enabled in a Google Cloud project, and a scoped Google OAuth access token. Paste the access token as Bearer; refresh expired tokens yourself.",
            requiredFields = listOf("OAuth access token")
        ),
        McpPreset(
            id = "excalidraw",
            name = "Excalidraw",
            description = "Generate hand-drawn diagrams with the official public Excalidraw MCP server.",
            category = McpCategory.BROWSER,
            commandOrUrl = "https://mcp.excalidraw.com",
            iconName = "excalidraw",
            author = "Excalidraw",
            suggestedAuthType = "NONE",
            pricing = McpPricingType.FREE,
            toolCapabilities = listOf("Create diagram content", "Retrieve diagram results"),
            websiteUrl = "https://github.com/excalidraw/excalidraw-mcp",
            verifiedRemote = true,
            setupInstructions = "This app can call MCP tools, but does not render the interactive MCP Apps canvas. Use returned links or supported tool content; embedded editing requires a client with MCP Apps support."
        ),
        McpPreset(
            id = "bright-data",
            webSearchToolNames = listOf("search_engine", "search_engine_batch"),
            name = "Bright Data",
            description = "Search and retrieve public web data through Bright Data's hosted MCP service. Paid usage may apply beyond plan allowances.",
            category = McpCategory.BROWSER,
            commandOrUrl = "https://mcp.brightdata.com/mcp",
            iconName = "brightdata",
            author = "Bright Data",
            suggestedAuthType = "NONE",
            pricing = McpPricingType.FREE_WITH_SIGNUP,
            toolCapabilities = listOf("Search and retrieve webpages", "Access enabled web data tools"),
            websiteUrl = "https://github.com/brightdata/brightdata-mcp",
            verifiedRemote = true,
            setupInstructions = "Copy your complete hosted MCP URL from Bright Data, including its token query parameter. This service documents URL-token authentication, not generic OAuth. Treat the URL as a secret and do not share it.",
            requiredEndpointQueryParameter = "token"
        )
    )

    val categories = listOf("All") + McpCategory.entries.map { it.name }

    fun findById(id: String): McpPreset? = presets.find { it.id == id }

    fun findByAlias(alias: String): McpPreset? = presets.find { it.alias.equals(alias, ignoreCase = true) }

    fun getByCategory(category: McpCategory): List<McpPreset> = presets.filter { it.category == category }

    fun filterByCategory(category: McpCategory): List<McpPreset> = presets.filter { it.category == category }

    fun filterByCategory(categoryName: String): List<McpPreset> {
        if (categoryName.equals("All", ignoreCase = true)) return presets
        return presets.filter { it.category.name.equals(categoryName, ignoreCase = true) }
    }

    fun searchPresets(query: String): List<McpPreset> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return presets
        return presets.filter {
            it.name.lowercase().contains(q) ||
                it.description.lowercase().contains(q) ||
                it.alias.lowercase().contains(q) ||
                it.category.name.lowercase().contains(q) ||
                it.pricing.displayName.lowercase().contains(q) ||
                it.toolCapabilities.any { tool -> tool.lowercase().contains(q) }
        }
    }
}
