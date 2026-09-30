package dev.chungjungsoo.gptmobile.presentation.ui.chat

import android.content.ClipData
import android.util.LruCache
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.mikepenz.markdown.annotator.annotatorSettings
import com.mikepenz.markdown.compose.LocalMarkdownTypography
import com.mikepenz.markdown.compose.LocalReferenceLinkHandler
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownCodeBlock
import com.mikepenz.markdown.compose.elements.MarkdownCodeFence
import com.mikepenz.markdown.compose.elements.MarkdownParagraph
import com.mikepenz.markdown.compose.elements.MarkdownTable
import com.mikepenz.markdown.compose.elements.MarkdownTableHeader
import com.mikepenz.markdown.compose.elements.MarkdownTableRow
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.MarkdownAnnotator
import com.mikepenz.markdown.model.markdownAnimations
import com.mikepenz.markdown.model.markdownAnnotator
import com.mikepenz.markdown.model.markdownInlineContent
import com.mikepenz.markdown.model.rememberMarkdownState
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.ui.component.SandboxedArtifactView
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.BoldHighlight
import dev.snipme.highlights.model.ColorHighlight
import dev.snipme.highlights.model.SyntaxLanguage
import dev.snipme.highlights.model.SyntaxThemes
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import org.intellij.markdown.MarkdownTokenTypes
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val CLIPBOARD_LABEL_CODE = "code"
private const val DISPLAY_MATH_PLACEHOLDER_PREFIX = "CHAT_MATH_DISPLAY_"
private const val DISPLAY_MATH_PLACEHOLDER_SUFFIX = "_TOKEN"
private const val DISPLAY_MATH_PLACEHOLDER_TEST_NONCE = "test"

/**
 * LRU cache for syntax highlighted code spans.
 * Tokenizing and parsing abstract syntax trees for programming languages (Python, Kotlin, JSON, etc.)
 * during chat scrolling or recomposition is computationally heavy and causes frame drops.
 * Caching the formatted AnnotatedString prevents repetitive parsing of identical code blocks.
 */
private val codeHighlightCache = LruCache<String, AnnotatedString>(400)

@Composable
fun ChatMarkdown(
    content: String,
    contentIdentity: Any = content,
    highlightSentence: String? = null,
    highlightProgress: Float = 0f,
    streaming: Boolean = false,
    modifier: Modifier = Modifier
) {
    val isDarkTheme = isSystemInDarkTheme()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val parsed = remember(content) { parseChatMarkdown(content) }
    val displayMathNonce = remember(content) { UUID.randomUUID().toString().replace("-", "") }
    val highlightsBuilder = remember(isDarkTheme) {
        Highlights.Builder().theme(SyntaxThemes.atom(isDarkTheme))
    }
    val combinedMarkdown = remember(parsed.blocks, displayMathNonce) {
        buildCombinedMarkdown(parsed.blocks, displayMathNonce)
    }
    val inlineMathByPlaceholder = remember(parsed.inlineMath) {
        parsed.inlineMath.associateBy { it.placeholder }
    }
    val displayMathByPlaceholder = remember(parsed.blocks, displayMathNonce) {
        parsed.blocks
            .filterIsInstance<ChatMarkdownBlock.DisplayMath>()
            .mapIndexed { index, block ->
                createDisplayMathPlaceholder(index, displayMathNonce) to block
            }
            .toMap()
    }
    val normalTextColor = MaterialTheme.colorScheme.onSurface
    val wordArrivalTimes = remember(contentIdentity) { mutableStateListOf<Long>() }
    var fadeClock by remember(contentIdentity) { mutableLongStateOf(android.os.SystemClock.uptimeMillis()) }
    LaunchedEffect(combinedMarkdown.length, streaming) {
        val now = android.os.SystemClock.uptimeMillis()
        while (wordArrivalTimes.size < combinedMarkdown.length) wordArrivalTimes.add(now)
        while (streaming || wordArrivalTimes.lastOrNull()?.let { fadeClock - it < 1500L } == true) {
            fadeClock = android.os.SystemClock.uptimeMillis()
            delay(50)
        }
    }
    val targetYellow = SuggestionHighlightManager.HIGHLIGHT_YELLOW
    val targetBg = SuggestionHighlightManager.HIGHLIGHT_TRANSLUCENT_BG

    val annotator = remember(inlineMathByPlaceholder, highlightSentence, highlightProgress, normalTextColor, streaming, fadeClock, wordArrivalTimes.size) {
        markdownAnnotator { source, child ->
            val text = source.substring(child.startOffset, child.endOffset)
            val hasMath = containsInlineMathPlaceholder(text)
            val hasSentenceHighlight = !highlightSentence.isNullOrBlank() &&
                highlightProgress > 0.01f &&
                text.contains(highlightSentence, ignoreCase = true)

            if (!hasMath && !hasSentenceHighlight && streaming && child.type == MarkdownTokenTypes.TEXT) {
                val segmentStart = length
                append(text)
                Regex("\\S+").findAll(text).forEach { match ->
                    val absoluteOffset = (child.startOffset + match.range.first).coerceAtMost(wordArrivalTimes.lastIndex)
                    val arrivedAt = wordArrivalTimes.getOrNull(absoluteOffset) ?: fadeClock
                    val alpha = ((fadeClock - arrivedAt).coerceAtLeast(0L) / 1500f).coerceIn(0f, 1f)
                    addStyle(
                        SpanStyle(color = normalTextColor.copy(alpha = alpha)),
                        segmentStart + match.range.first,
                        segmentStart + match.range.last + 1
                    )
                }
                true
            } else if (!hasMath && !hasSentenceHighlight) {
                false
            } else {
                if (hasSentenceHighlight && !hasMath) {
                    val sIndex = text.indexOf(highlightSentence!!, ignoreCase = true)
                    if (sIndex != -1) {
                        val interpolatedColor = lerp(normalTextColor, targetYellow, highlightProgress.coerceIn(0f, 1f))
                        val interpolatedBg = Color.Transparent.copy(alpha = 0f).let {
                            lerp(it, targetBg, highlightProgress.coerceIn(0f, 1f))
                        }
                        val interpolatedWeight = SuggestionHighlightManager.interpolateFontWeight(highlightProgress)

                        if (sIndex > 0) {
                            append(text.substring(0, sIndex))
                        }
                        val startSpan = length
                        append(text.substring(sIndex, sIndex + highlightSentence.length))
                        val endSpan = length
                        addStyle(
                            style = SpanStyle(
                                color = interpolatedColor,
                                background = interpolatedBg,
                                fontWeight = interpolatedWeight
                            ),
                            start = startSpan,
                            end = endSpan
                        )
                        if (sIndex + highlightSentence.length < text.length) {
                            append(text.substring(sIndex + highlightSentence.length))
                        }
                        true
                    } else {
                        false
                    }
                } else {
                    appendTextWithInlineMath(this, text, inlineMathByPlaceholder)
                    true
                }
            }
        }
    }
    val inlineContent = remember(parsed.inlineMath) {
        parsed.inlineMath.associate { token ->
            token.placeholder to InlineTextContent(
                placeholder = Placeholder(
                    width = inlineMathWidth(token.tex),
                    height = inlineMathHeight(token.tex),
                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                )
            ) {
                InlineMathView(token.tex)
            }
        }
    }
    val copyCodeToClipboard: (String) -> Unit = remember(clipboard, scope) {
        { code ->
            scope.launch {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(CLIPBOARD_LABEL_CODE, code)))
            }
        }
    }
    val components = remember(highlightsBuilder, copyCodeToClipboard, displayMathByPlaceholder, annotator) {
        markdownComponents(
            table = { model ->
                val settings = annotatorSettings(
                    LocalMarkdownTypography.current.textLink,
                    LocalMarkdownTypography.current.inlineCode.toSpanStyle(),
                    annotator,
                    LocalReferenceLinkHandler.current,
                    LocalUriHandler.current,
                    null
                )
                MarkdownTable(
                    content = model.content,
                    node = model.node,
                    style = model.typography.table,
                    annotatorSettings = settings,
                    headerBlock = { tableContent, header, tableWidth, style ->
                        MarkdownTableHeader(
                            tableContent,
                            header,
                            tableWidth,
                            style,
                            maxLines = Int.MAX_VALUE,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Clip,
                            annotatorSettings = settings
                        )
                    },
                    rowBlock = { tableContent, row, tableWidth, style ->
                        MarkdownTableRow(
                            tableContent,
                            row,
                            tableWidth,
                            style,
                            maxLines = Int.MAX_VALUE,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Clip,
                            annotatorSettings = settings
                        )
                    }
                )
            },
            codeBlock = {
                MarkdownCodeBlock(it.content, it.node, it.typography.code) { code, language, style ->
                    val cleanLang = language?.trim()?.lowercase()
                    val isHtmlOrSvg = cleanLang == "html" || cleanLang == "htm" || cleanLang == "svg"
                    if (isHtmlOrSvg) {
                        SandboxedArtifactView(
                            title = cleanLang.uppercase(),
                            content = code,
                            isHtmlOrSvg = true
                        )
                    } else {
                        CodeBlockWithCopy(
                            code = code,
                            language = language,
                            onCopyCode = copyCodeToClipboard
                        ) {
                            HighlightedCodeContent(
                                code = code,
                                language = language,
                                style = style,
                                highlightsBuilder = highlightsBuilder,
                                isDarkTheme = isDarkTheme
                            )
                        }
                    }
                }
            },
            codeFence = {
                MarkdownCodeFence(it.content, it.node, it.typography.code) { code, language, style ->
                    val cleanLang = language?.trim()?.lowercase()
                    val isHtmlOrSvg = cleanLang == "html" || cleanLang == "htm" || cleanLang == "svg"
                    if (isHtmlOrSvg) {
                        SandboxedArtifactView(
                            title = cleanLang.uppercase(),
                            content = code,
                            isHtmlOrSvg = true
                        )
                    } else {
                        CodeBlockWithCopy(
                            code = code,
                            language = language,
                            onCopyCode = copyCodeToClipboard
                        ) {
                            HighlightedCodeContent(
                                code = code,
                                language = language,
                                style = style,
                                highlightsBuilder = highlightsBuilder,
                                isDarkTheme = isDarkTheme
                            )
                        }
                    }
                }
            },
            paragraph = { model ->
                val paragraphText = extractNodeText(model.content, model.node).trim()
                val displayMathBlocks = resolveDisplayMathParagraph(
                    paragraphText = paragraphText,
                    displayMathByPlaceholder = displayMathByPlaceholder
                )
                if (displayMathBlocks != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                    ) {
                        displayMathBlocks.forEach { displayMath ->
                            DisplayMathView(
                                tex = displayMath.tex,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight()
                            )
                        }
                    }
                } else {
                    DefaultParagraph(model.content, model.node, model.typography.paragraph, annotator)
                }
            }
        )
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val externalHandler = LocalUriHandler.current
    val sourceHandler = remember(context, externalHandler) {
        object : androidx.compose.ui.platform.UriHandler {
            override fun openUri(uri: String) {
                val parsedUri = android.net.Uri.parse(uri)
                if (parsedUri.scheme == "gptmobile" && parsedUri.host == "knowledge") {
                    context.startActivity(android.content.Intent(context, dev.chungjungsoo.gptmobile.presentation.ui.setting.KnowledgeSourceActivity::class.java).setData(parsedUri))
                } else if (parsedUri.scheme == "gptmobile" && parsedUri.host == "media") {
                    context.startActivity(android.content.Intent(context, dev.chungjungsoo.gptmobile.presentation.ui.setting.McpMediaActivity::class.java).setData(parsedUri))
                } else if (parsedUri.scheme in setOf("https", "http", "mailto")) {
                    externalHandler.openUri(uri)
                }
            }
        }
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalUriHandler provides sourceHandler) {
        key(contentIdentity, highlightSentence, (highlightProgress * 100).toInt()) {
            val markdownState = rememberMarkdownState(
                content = combinedMarkdown,
                retainState = true
            )
            val animations = markdownAnimations(animateTextSize = { this })

            Markdown(
                markdownState = markdownState,
                inlineContent = markdownInlineContent(inlineContent),
                annotator = annotator,
                components = components,
                typography = chatMarkdownTypography(),
                animations = animations,
                modifier = modifier
            )
        }
    }
}

@Composable
private fun CodeBlockWithCopy(
    code: String,
    language: String?,
    onCopyCode: (String) -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    modifier = Modifier.padding(end = 12.dp),
                    text = language?.trim()?.takeIf { it.isNotEmpty() } ?: stringResource(R.string.code),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    modifier = Modifier.heightIn(min = 32.dp),
                    onClick = { onCopyCode(code) },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = stringResource(R.string.copy_code),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )
            Box(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

@Composable
private fun HighlightedCodeContent(
    code: String,
    language: String?,
    style: TextStyle,
    highlightsBuilder: Highlights.Builder,
    isDarkTheme: Boolean
) {
    val cacheKey = "$isDarkTheme:$language:$code"
    val cached = codeHighlightCache.get(cacheKey)

    val highlightedCode by produceState(
        initialValue = cached ?: AnnotatedString(code),
        key1 = cacheKey
    ) {
        if (cached == null) {
            val result = withContext(Dispatchers.Default) {
                buildHighlightedAnnotatedString(code, language, highlightsBuilder)
            }
            codeHighlightCache.put(cacheKey, result)
            value = result
        }
    }

    Text(
        text = highlightedCode,
        style = style,
        softWrap = false,
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

private fun buildHighlightedAnnotatedString(
    code: String,
    language: String?,
    highlightsBuilder: Highlights.Builder
): AnnotatedString {
    val syntaxLanguage = language?.let { SyntaxLanguage.getByName(it) }
    val codeHighlights = highlightsBuilder
        .code(code)
        .let { if (syntaxLanguage != null) it.language(syntaxLanguage) else it }
        .build()
        .getHighlights()

    return AnnotatedString.Builder(code).apply {
        codeHighlights.forEach { highlight ->
            val spanStyle = when (highlight) {
                is ColorHighlight -> SpanStyle(color = androidx.compose.ui.graphics.Color(highlight.rgb).copy(alpha = 1f))
                is BoldHighlight -> SpanStyle(fontWeight = FontWeight.Bold)
            }
            addStyle(
                style = spanStyle,
                start = highlight.location.start,
                end = highlight.location.end
            )
        }
    }.toAnnotatedString()
}

private fun appendTextWithInlineMath(
    builder: androidx.compose.ui.text.AnnotatedString.Builder,
    text: String,
    inlineMathByPlaceholder: Map<String, InlineMathToken>
) {
    var cursor = 0
    while (cursor < text.length) {
        val nextToken = inlineMathByPlaceholder.keys
            .mapNotNull { placeholder ->
                val start = text.indexOf(placeholder, cursor)
                if (start == -1) null else placeholder to start
            }
            .minByOrNull { it.second }

        if (nextToken == null) {
            builder.append(text.substring(cursor))
            return
        }

        val (placeholder, start) = nextToken
        if (start > cursor) {
            builder.append(text.substring(cursor, start))
        }
        builder.appendInlineContent(placeholder, "[math]")
        cursor = start + placeholder.length
    }
}

private fun inlineMathWidth(tex: String) = (tex.length.coerceIn(2, 24) * 0.55f).em

private fun inlineMathHeight(tex: String) = when {
    tex.containsDisplaySizedMath() -> 3.2.em
    tex.containsSuperscriptOrSubscriptMath() -> 2.1.em
    else -> 1.6.em
}

private fun resolveDisplayMathParagraph(
    paragraphText: String,
    displayMathByPlaceholder: Map<String, ChatMarkdownBlock.DisplayMath>
): List<ChatMarkdownBlock.DisplayMath>? {
    if (paragraphText.isEmpty()) return null

    val placeholders = displayMathByPlaceholder.keys
        .filter(paragraphText::contains)
    if (placeholders.isEmpty()) return null

    val nonPlaceholderText = placeholders.fold(paragraphText) { current, placeholder ->
        current.replace(placeholder, " ")
    }
    if (nonPlaceholderText.isNotBlank()) return null

    return placeholders.mapNotNull(displayMathByPlaceholder::get)
}

private fun String.containsDisplaySizedMath(): Boolean = listOf(
    "\\frac",
    "\\dfrac",
    "\\tfrac",
    "\\sum",
    "\\prod",
    "\\int",
    "\\oint",
    "\\lim",
    "\\begin",
    "\\left",
    "\\right",
    "\\over"
).any(::contains)

private fun String.containsSuperscriptOrSubscriptMath(): Boolean = contains('^') || contains('_')

internal fun buildCombinedMarkdown(
    blocks: List<ChatMarkdownBlock>,
    nonce: String = DISPLAY_MATH_PLACEHOLDER_TEST_NONCE
): String = buildString {
    var displayMathIndex = 0
    blocks.forEach { block ->
        when (block) {
            is ChatMarkdownBlock.Markdown -> append(block.content)

            is ChatMarkdownBlock.DisplayMath -> appendDisplayMathPlaceholder(
                createDisplayMathPlaceholder(displayMathIndex++, nonce)
            )
        }
    }
}

private fun StringBuilder.appendDisplayMathPlaceholder(placeholder: String) {
    val linePrefix = currentLinePrefix().orEmpty()
    if (!isEmpty() && !endsWithBlankLine()) {
        if (last() != '\n') {
            appendLine()
        }
        appendLine()
    }
    if (linePrefix.isNotEmpty()) {
        append(linePrefix)
    }
    append(placeholder)
    appendLine()
    appendLine()
}

private fun StringBuilder.endsWithBlankLine(): Boolean = length >= 2 && this[length - 1] == '\n' && this[length - 2] == '\n'

private fun StringBuilder.currentLinePrefix(): String? {
    val lineStart = lastIndexOf('\n').let { if (it == -1) 0 else it + 1 }
    if (lineStart == length) return ""

    val currentLine = substring(lineStart, length)
    return currentLine.takeIf { line ->
        line.all { character ->
            character == ' ' || character == '\t' || character == '>'
        }
    }
}

private fun createDisplayMathPlaceholder(
    index: Int,
    nonce: String
): String = "\uE000$DISPLAY_MATH_PLACEHOLDER_PREFIX${nonce}_$index$DISPLAY_MATH_PLACEHOLDER_SUFFIX\uE001"

@Composable
private fun chatMarkdownTypography() = markdownTypography(
    h1 = MaterialTheme.typography.headlineMedium,
    h2 = MaterialTheme.typography.headlineSmall,
    h3 = MaterialTheme.typography.titleLarge,
    h4 = MaterialTheme.typography.titleMedium,
    h5 = MaterialTheme.typography.titleSmall,
    h6 = MaterialTheme.typography.labelLarge,
    text = MaterialTheme.typography.bodyMedium,
    paragraph = MaterialTheme.typography.bodyMedium,
    ordered = MaterialTheme.typography.bodyMedium,
    bullet = MaterialTheme.typography.bodyMedium,
    list = MaterialTheme.typography.bodyMedium
)

@Composable
private fun DefaultParagraph(
    content: String,
    node: org.intellij.markdown.ast.ASTNode,
    style: TextStyle,
    annotator: MarkdownAnnotator
) {
    MarkdownParagraph(
        content,
        node,
        Modifier,
        style,
        annotatorSettings(
            LocalMarkdownTypography.current.textLink,
            LocalMarkdownTypography.current.inlineCode.toSpanStyle(),
            annotator,
            LocalReferenceLinkHandler.current,
            LocalUriHandler.current,
            null
        )
    )
}

private fun extractNodeText(
    content: String,
    node: org.intellij.markdown.ast.ASTNode
): String = content.substring(node.startOffset, node.endOffset)
