package dev.chungjungsoo.gptmobile.presentation.ui.mcp

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.catalog.McpCategory
import dev.chungjungsoo.gptmobile.data.catalog.McpPreset
import dev.chungjungsoo.gptmobile.data.catalog.McpPresetCatalog
import dev.chungjungsoo.gptmobile.data.catalog.McpPricingType
import dev.chungjungsoo.gptmobile.data.database.entity.ToolConnectionAuthType
import dev.chungjungsoo.gptmobile.presentation.ui.setting.ToolConnectionsViewModel

/**
 * Service icon descriptor with distinct brand colors and icons for each preset.
 */
data class ServiceBrand(
    val iconVector: ImageVector? = null,
    val iconResId: Int? = null,
    val brandColor: Color,
    val containerColor: Color
)

@Composable
fun getServiceBrand(iconName: String, category: McpCategory): ServiceBrand {
    val isDark = MaterialTheme.colorScheme.background.red < 0.5f

    McpBrandAssets.drawableFor(iconName)?.let { resource ->
        return ServiceBrand(
            iconResId = resource,
            brandColor = Color.Unspecified,
            containerColor = Color.White
        )
    }

    return when (iconName) {
        "online_search" -> ServiceBrand(
            iconVector = Icons.Default.TravelExplore,
            brandColor = Color(0xFF00B0FF),
            containerColor = Color(0xFF00B0FF).copy(alpha = 0.15f)
        )
        "github" -> ServiceBrand(
            iconResId = R.drawable.ic_github,
            brandColor = if (isDark) Color(0xFFF0F6FC) else Color(0xFF24292F),
            containerColor = if (isDark) Color(0xFF21262D) else Color(0xFFF6F8FA)
        )
        "brave" -> ServiceBrand(
            iconResId = R.drawable.ic_brave,
            brandColor = Color(0xFFFF5722),
            containerColor = Color(0xFFFF5722).copy(alpha = 0.15f)
        )
        "folder" -> ServiceBrand(
            iconVector = Icons.Default.Folder,
            brandColor = Color(0xFF0288D1),
            containerColor = Color(0xFF0288D1).copy(alpha = 0.15f)
        )
        "postgres" -> ServiceBrand(
            iconVector = Icons.Default.Dns,
            brandColor = Color(0xFF336791),
            containerColor = Color(0xFF336791).copy(alpha = 0.15f)
        )
        "puppeteer" -> ServiceBrand(
            iconVector = Icons.Default.Public,
            brandColor = Color(0xFF00D8A2),
            containerColor = Color(0xFF00D8A2).copy(alpha = 0.15f)
        )
        "fetch" -> ServiceBrand(
            iconVector = Icons.Default.Download,
            brandColor = Color(0xFF7C4DFF),
            containerColor = Color(0xFF7C4DFF).copy(alpha = 0.15f)
        )
        "pearls" -> ServiceBrand(
            iconVector = Icons.Default.AccountTree,
            brandColor = MaterialTheme.colorScheme.primary,
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
        "memory" -> ServiceBrand(
            iconVector = Icons.Default.Psychology,
            brandColor = Color(0xFFEC407A),
            containerColor = Color(0xFFEC407A).copy(alpha = 0.15f)
        )
        "exa" -> ServiceBrand(
            iconVector = Icons.Default.Search,
            brandColor = Color(0xFF6200EE),
            containerColor = Color(0xFF6200EE).copy(alpha = 0.15f)
        )
        "terminal" -> ServiceBrand(
            iconVector = Icons.Default.Terminal,
            brandColor = Color(0xFF00C853),
            containerColor = Color(0xFF00C853).copy(alpha = 0.15f)
        )
        else -> when (category) {
            McpCategory.SEARCH -> ServiceBrand(
                iconVector = Icons.Default.TravelExplore,
                brandColor = Color(0xFF0288D1),
                containerColor = Color(0xFF0288D1).copy(alpha = 0.15f)
            )
            McpCategory.DEVELOPMENT -> ServiceBrand(
                iconVector = Icons.Default.Code,
                brandColor = Color(0xFF6200EE),
                containerColor = Color(0xFF6200EE).copy(alpha = 0.15f)
            )
            McpCategory.DATABASE -> ServiceBrand(
                iconVector = Icons.Default.Dns,
                brandColor = Color(0xFF336791),
                containerColor = Color(0xFF336791).copy(alpha = 0.15f)
            )
            McpCategory.SYSTEM -> ServiceBrand(
                iconVector = Icons.Default.Terminal,
                brandColor = Color(0xFF00897B),
                containerColor = Color(0xFF00897B).copy(alpha = 0.15f)
            )
            McpCategory.BROWSER -> ServiceBrand(
                iconVector = Icons.Default.Public,
                brandColor = Color(0xFF00B0FF),
                containerColor = Color(0xFF00B0FF).copy(alpha = 0.15f)
            )
            McpCategory.MEMORY, McpCategory.THREADING, McpCategory.PRODUCTIVITY -> ServiceBrand(
                iconVector = Icons.Default.Psychology,
                brandColor = Color(0xFFFF6D00),
                containerColor = Color(0xFFFF6D00).copy(alpha = 0.15f)
            )
        }
    }
}

/**
 * Enhanced full-screen MCP Marketplace page with clean layout, brand service icons,
 * category filters, pricing tags, and robust connection configuration modal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McpMarketplaceScreen(
    installedAliases: Set<String>,
    onNavigationClick: () -> Unit,
    onInstallPresetWithConfig: (
        preset: McpPreset,
        name: String,
        alias: String,
        endpointUrl: String,
        authType: String,
        credential: String,
        allowCleartext: Boolean
    ) -> Unit,
    modifier: Modifier = Modifier,
    onOpenDelegation: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<McpCategory?>(null) }
    var selectedPricing by remember { mutableStateOf<McpPricingType?>(null) }
    var configuringPreset by remember { mutableStateOf<McpPreset?>(null) }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    val filteredPresets = remember(searchQuery, selectedCategory, selectedPricing) {
        var list = McpPresetCatalog.presets

        if (selectedCategory != null) {
            list = list.filter { it.category == selectedCategory }
        }
        if (selectedPricing != null) {
            list = list.filter { it.pricing == selectedPricing }
        }
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            list = list.filter {
                it.name.lowercase().contains(q) ||
                    it.description.lowercase().contains(q) ||
                    it.alias.lowercase().contains(q) ||
                    it.category.displayName.lowercase().contains(q) ||
                    it.pricing.displayName.lowercase().contains(q) ||
                    it.toolCapabilities.any { tool -> tool.lowercase().contains(q) }
            }
        }
        list
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            McpMarketplaceTopBar(
                scrollBehavior = scrollBehavior,
                onNavigationClick = onNavigationClick
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search Bar & Filter Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search MCP tools, capabilities, keywords…") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search"
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Category Filter Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { selectedCategory = null },
                        label = { Text("All Categories") },
                        shape = RoundedCornerShape(12.dp)
                    )

                    McpCategory.entries.forEach { category ->
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = {
                                selectedCategory = if (selectedCategory == category) null else category
                            },
                            label = { Text(category.displayName) },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Pricing Filter Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = selectedPricing == null,
                        onClick = { selectedPricing = null },
                        label = { Text("All Pricing") },
                        shape = RoundedCornerShape(12.dp)
                    )

                    McpPricingType.entries.forEach { pricing ->
                        FilterChip(
                            selected = selectedPricing == pricing,
                            onClick = {
                                selectedPricing = if (selectedPricing == pricing) null else pricing
                            },
                            label = { Text(pricing.displayName) },
                            shape = RoundedCornerShape(12.dp),
                            leadingIcon = {
                                PricingIcon(pricing = pricing)
                            }
                        )
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )

            // Results count
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${filteredPresets.size} available integration${if (filteredPresets.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Presets Cards List
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredPresets, key = { it.id }) { preset ->
                    val isInstalled = preset.isPreinstalled ||
                        installedAliases.contains(preset.alias) ||
                        installedAliases.contains(ToolConnectionsViewModel.normalizeAlias(preset.alias))

                    McpMarketplaceDetailCard(
                        preset = preset,
                        isInstalled = isInstalled,
                        onAddClick = {
                            if (preset.integratedTool == "delegation" && onOpenDelegation != null) {
                                onOpenDelegation()
                            } else if (preset.documentationOnly) {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(preset.websiteUrl))) }
                            } else {
                                configuringPreset = preset
                            }
                        }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(28.dp))
                }
            }
        }
    }

    // Modal Configuration Dialog to ensure all needed fields are filled in
    configuringPreset?.let { preset ->
        if (preset.integratedTool != null) {
            dev.chungjungsoo.gptmobile.presentation.ui.setting.LocalToolConfigurationDialog(preset.integratedTool) { configuringPreset = null }
        } else {
            McpPresetConfigureDialog(
                preset = preset,
                onDismissRequest = { configuringPreset = null },
                onConfirm = { name, alias, endpoint, authType, credential, allowCleartext ->
                    onInstallPresetWithConfig(
                        preset,
                        name,
                        alias,
                        endpoint,
                        authType,
                        credential,
                        allowCleartext
                    )
                    configuringPreset = null
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun McpMarketplaceTopBar(
    scrollBehavior: TopAppBarScrollBehavior,
    onNavigationClick: () -> Unit
) {
    LargeTopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground
        ),
        title = {
            Column {
                Text(
                    text = "MCP Marketplace",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Install built-ins and connect verified Streamable HTTP MCP servers",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onNavigationClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.go_back)
                )
            }
        },
        scrollBehavior = scrollBehavior
    )
}

@Composable
fun PricingBadge(pricing: McpPricingType) {
    val (bgColor, textColor) = when (pricing) {
        McpPricingType.FREE -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        McpPricingType.FREE_WITH_SIGNUP -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        McpPricingType.PAID -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            PricingIcon(pricing = pricing, modifier = Modifier.size(12.dp), tint = textColor)
            Text(
                text = pricing.displayName,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        }
    }
}

@Composable
fun PricingIcon(pricing: McpPricingType, modifier: Modifier = Modifier.size(16.dp), tint: Color = MaterialTheme.colorScheme.primary) {
    when (pricing) {
        McpPricingType.FREE -> Icon(Icons.Default.Check, contentDescription = null, modifier = modifier, tint = tint)
        McpPricingType.FREE_WITH_SIGNUP -> Icon(Icons.Default.Key, contentDescription = null, modifier = modifier, tint = tint)
        McpPricingType.PAID -> Icon(Icons.Default.Star, contentDescription = null, modifier = modifier, tint = tint)
    }
}

@Composable
fun ServiceIcon(
    iconName: String,
    category: McpCategory,
    modifier: Modifier = Modifier
) {
    val brand = getServiceBrand(iconName, category)

    Box(
        modifier = modifier
            .size(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(brand.containerColor),
        contentAlignment = Alignment.Center
    ) {
        if (brand.iconResId != null) {
            Icon(
                painter = painterResource(id = brand.iconResId),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(26.dp)
            )
        } else if (brand.iconVector != null) {
            Icon(
                imageVector = brand.iconVector,
                contentDescription = null,
                tint = brand.brandColor,
                modifier = Modifier.size(26.dp)
            )
        } else {
            Icon(
                imageVector = Icons.Default.Extension,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun McpMarketplaceDetailCard(
    preset: McpPreset,
    isInstalled: Boolean,
    onAddClick: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header Row: Service Icon + Title & Metadata + Action Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Service Icon with custom brand styling
                    ServiceIcon(
                        iconName = preset.iconName,
                        category = preset.category
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = preset.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = "by ${preset.author}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                if (preset.integratedTool != null) {
                    Button(onClick = onAddClick) { Text("Configure") }
                } else if (isInstalled) {
                    OutlinedButton(
                        onClick = { },
                        enabled = false,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = if (preset.isPreinstalled) "Built in" else "Installed",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (preset.isPreinstalled) "Built in" else "Installed")
                    }
                } else {
                    Button(
                        onClick = onAddClick,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            if (preset.documentationOnly) {
                                "Guide"
                            } else if (preset.commandOrUrl.isBlank()) {
                                "Set up"
                            } else {
                                "Add"
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Wrap badges so endpoint and setup labels fit narrow phones.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PricingBadge(pricing = preset.pricing)

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = preset.category.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (preset.isPreinstalled) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    }
                ) {
                    Text(
                        text = when {
                            preset.documentationOnly -> "Companion / guide"
                            preset.isPreinstalled -> "Integrated"
                            preset.commandOrUrl.isBlank() -> "Self-hosted"
                            else -> "Streamable HTTP"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (preset.isPreinstalled) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                if (preset.verifiedRemote) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF2E7D32).copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = "Documented endpoint",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF2E7D32),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Full Description
            Text(
                text = preset.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (preset.webSearchToolNames.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Enable this provider's search tools in an AI profile to include them in multi-engine web search.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (preset.setupInstructions.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = preset.setupInstructions,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Tool capabilities list
            if (preset.toolCapabilities.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Provided Tools & Functions:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        val displayCount = if (isExpanded) preset.toolCapabilities.size else 2
                        preset.toolCapabilities.take(displayCount).forEach { toolDesc ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "•",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(end = 6.dp)
                                )
                                Text(
                                    text = toolDesc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Expand / Collapse Footer & Documentation link
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (preset.websiteUrl.isNotBlank()) {
                    TextButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(preset.websiteUrl))
                            runCatching { context.startActivity(intent) }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Open docs",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Documentation",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                if (preset.toolCapabilities.size > 2) {
                    TextButton(onClick = { isExpanded = !isExpanded }) {
                        Text(
                            text = if (isExpanded) "Show Less" else "All ${preset.toolCapabilities.size} tools",
                            style = MaterialTheme.typography.labelSmall
                        )
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Ensures all required fields (Name, Alias, Endpoint URL, and API/Bearer Tokens) are validated
 * before an MCP preset can be added to the connection pool.
 */
@Composable
fun McpPresetConfigureDialog(
    preset: McpPreset,
    onDismissRequest: () -> Unit,
    onConfirm: (
        name: String,
        alias: String,
        endpoint: String,
        authType: String,
        credential: String,
        allowCleartext: Boolean
    ) -> Unit
) {
    var name by remember { mutableStateOf(preset.name) }
    var alias by remember { mutableStateOf(preset.alias) }
    var endpoint by remember { mutableStateOf(preset.defaultEndpoint) }
    var authType by remember { mutableStateOf(preset.suggestedAuthType) }
    var credential by remember { mutableStateOf("") }
    var allowCleartext by remember {
        mutableStateOf(preset.defaultEndpoint.startsWith("http://", ignoreCase = true))
    }

    val normalizedAlias = ToolConnectionsViewModel.normalizeAlias(alias)
    val isAliasValid = ToolConnectionsViewModel.isValidAlias(normalizedAlias)
    val isNameValid = name.isNotBlank()
    val isEndpointValid = ToolConnectionsViewModel.isValidMcpEndpoint(endpoint.trim(), allowCleartext) &&
        preset.hasRequiredEndpointParameters(endpoint)

    val isCredentialRequired = authType == ToolConnectionAuthType.BEARER

    val isCredentialValid = !isCredentialRequired || credential.isNotBlank()

    val canSave = isNameValid && isAliasValid && isEndpointValid && isCredentialValid

    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
                    .imePadding()
            ) {
                // Header with Service Icon
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ServiceIcon(
                            iconName = preset.iconName,
                            category = preset.category,
                            modifier = Modifier.size(40.dp)
                        )
                        Column {
                            Text(
                                text = "Configure ${preset.name}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Verify connection settings & credentials",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismissRequest) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (preset.setupInstructions.isNotBlank()) {
                    Text(
                        text = preset.setupInstructions,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Connection Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Connection Name *") },
                    isError = !isNameValid,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Stable Alias
                OutlinedTextField(
                    value = alias,
                    onValueChange = { alias = it },
                    label = { Text("Tool Alias * (lowercase letters/numbers/_)") },
                    isError = alias.isNotBlank() && !isAliasValid,
                    supportingText = {
                        if (alias.isNotBlank() && !isAliasValid) {
                            Text("Must start with a letter and contain only [a-z0-9_]")
                        } else {
                            Text("Used by AI agents as the tool namespace prefix")
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Endpoint URL
                OutlinedTextField(
                    value = endpoint,
                    onValueChange = { endpoint = it },
                    label = { Text("MCP Streamable HTTP URL *") },
                    isError = endpoint.isNotBlank() && !isEndpointValid,
                    visualTransformation = if (preset.requiredEndpointQueryParameter != null) {
                        PasswordVisualTransformation()
                    } else {
                        androidx.compose.ui.text.input.VisualTransformation.None
                    },
                    supportingText = {
                        if (!preset.hasRequiredEndpointParameters(endpoint)) {
                            Text("Enter the complete provider URL with a valid ${preset.requiredEndpointQueryParameter} parameter.")
                        } else if (endpoint.isNotBlank() && !isEndpointValid) {
                            Text("Must be a valid HTTP(S) Streamable HTTP endpoint. Cleartext requires explicit approval.")
                        } else {
                            Text("Remote MCP endpoint using Streamable HTTP. Legacy HTTP+SSE is not used for new connections.")
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                if (endpoint.startsWith("http://", ignoreCase = true)) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { allowCleartext = !allowCleartext }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = allowCleartext,
                            onCheckedChange = { allowCleartext = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Allow cleartext HTTP connection (Local loopback / private network)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Authentication section
                Text(
                    text = "Authentication",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = authType == ToolConnectionAuthType.NONE,
                        onClick = { authType = ToolConnectionAuthType.NONE },
                        label = { Text("None / Public") }
                    )
                    FilterChip(
                        selected = authType == ToolConnectionAuthType.BEARER,
                        onClick = { authType = ToolConnectionAuthType.BEARER },
                        label = { Text("Bearer / API Key") }
                    )
                }

                FilterChip(
                    selected = authType == ToolConnectionAuthType.OAUTH,
                    onClick = { authType = ToolConnectionAuthType.OAUTH },
                    label = { Text("Browser sign-in (OAuth)") }
                )
                if (authType == ToolConnectionAuthType.OAUTH) {
                    Text("Save, then use Authorize in the connection settings to sign in.", style = MaterialTheme.typography.bodySmall)
                }

                if (authType == ToolConnectionAuthType.BEARER) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = credential,
                        onValueChange = { credential = it },
                        label = {
                            Text(
                                if (preset.requiredFields.isNotEmpty()) {
                                    "${preset.requiredFields.first()} *"
                                } else {
                                    "API Key / Bearer Token *"
                                }
                            )
                        },
                        isError = isCredentialRequired && credential.isBlank(),
                        supportingText = {
                            if (isCredentialRequired && credential.isBlank()) {
                                Text("This integration requires an API key or token to authenticate.")
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                if (preset.websiteUrl.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    TextButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(preset.websiteUrl))
                            runCatching { context.startActivity(intent) }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "View setup documentation",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismissRequest) {
                        Text("Cancel")
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Button(
                        onClick = {
                            onConfirm(
                                name.trim(),
                                normalizedAlias,
                                endpoint.trim(),
                                authType,
                                credential.trim(),
                                allowCleartext
                            )
                        },
                        enabled = canSave,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add to Connections")
                    }
                }
            }
        }
    }
}
