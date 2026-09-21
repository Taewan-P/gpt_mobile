package dev.chungjungsoo.gptmobile.presentation.ui.chat

private const val INLINE_MATH_PLACEHOLDER_PREFIX = "CHAT_MATH_INLINE_"
private const val INLINE_MATH_PLACEHOLDER_SUFFIX = "_TOKEN"
private const val MAX_FENCE_INDENT = 3

data class ParsedChatMarkdown(
    val blocks: List<ChatMarkdownBlock>,
    val inlineMath: List<InlineMathToken>
)

sealed interface ChatMarkdownBlock {
    data class Markdown(val content: String) : ChatMarkdownBlock

    data class DisplayMath(val tex: String) : ChatMarkdownBlock
}

data class InlineMathToken(
    val placeholder: String,
    val tex: String
)

fun parseChatMarkdown(content: String): ParsedChatMarkdown {
    if ('$' !in content && "\\[" !in content && "\\(" !in content) {
        return ParsedChatMarkdown(
            blocks = if (content.isBlank()) emptyList() else listOf(ChatMarkdownBlock.Markdown(content)),
            inlineMath = emptyList()
        )
    }

    val blocks = mutableListOf<ChatMarkdownBlock>()
    val inlineMath = mutableListOf<InlineMathToken>()
    val markdownBuffer = StringBuilder()
    var index = 0

    while (index < content.length) {
        val fence = detectFenceDelimiter(content, index)
        if (fence != null && isFenceStart(content, index)) {
            val fenceEnd = findFenceBlockEnd(content, index, fence)
            markdownBuffer.append(content, index, fenceEnd)
            index = fenceEnd
            continue
        }

        val backtickCount = detectBacktickRun(content, index)
        if (backtickCount > 0) {
            val codeEnd = findInlineCodeEnd(content, index + backtickCount, backtickCount)
            if (codeEnd == -1) {
                markdownBuffer.append(content, index, index + backtickCount)
                index += backtickCount
            } else {
                markdownBuffer.append(content, index, codeEnd)
                index = codeEnd
            }
            continue
        }

        val displayMath = detectDisplayMath(content, index)
        if (displayMath != null) {
            flushMarkdownBuffer(markdownBuffer, blocks, inlineMath)
            blocks += ChatMarkdownBlock.DisplayMath(displayMath.tex)
            index = displayMath.endExclusive
            continue
        }

        markdownBuffer.append(content[index])
        index++
    }

    flushMarkdownBuffer(markdownBuffer, blocks, inlineMath)
    return ParsedChatMarkdown(blocks = blocks, inlineMath = inlineMath)
}

fun containsInlineMathPlaceholder(text: String): Boolean = text.contains(INLINE_MATH_PLACEHOLDER_PREFIX)

private data class DisplayMathMatch(
    val tex: String,
    val endExclusive: Int
)

private fun flushMarkdownBuffer(
    markdownBuffer: StringBuilder,
    blocks: MutableList<ChatMarkdownBlock>,
    inlineMath: MutableList<InlineMathToken>
) {
    if (markdownBuffer.isEmpty()) return

    val replacedContent = replaceInlineMath(
        content = markdownBuffer.toString(),
        startingIndex = inlineMath.size,
        tokens = inlineMath
    )
    if (replacedContent.isNotBlank()) {
        blocks += ChatMarkdownBlock.Markdown(replacedContent)
    }
    markdownBuffer.clear()
}

private fun replaceInlineMath(
    content: String,
    startingIndex: Int,
    tokens: MutableList<InlineMathToken>
): String {
    val output = StringBuilder()
    var inlineMathIndex = startingIndex
    var index = 0

    while (index < content.length) {
        val fence = detectFenceDelimiter(content, index)
        if (fence != null && isFenceStart(content, index)) {
            val fenceEnd = findFenceBlockEnd(content, index, fence)
            output.append(content, index, fenceEnd)
            index = fenceEnd
            continue
        }

        val backtickCount = detectBacktickRun(content, index)
        if (backtickCount > 0) {
            val codeEnd = findInlineCodeEnd(content, index + backtickCount, backtickCount)
            if (codeEnd == -1) {
                output.append(content, index, index + backtickCount)
                index += backtickCount
            } else {
                output.append(content, index, codeEnd)
                index = codeEnd
            }
            continue
        }

        if (!isEscaped(content, index) && content.startsWith("\\(", index)) {
            val end = findClosingDelimiter(content, index + 2, "\\)")
            if (end != -1) {
                val tex = content.substring(index + 2, end)
                val placeholder = createPlaceholder(inlineMathIndex++)
                tokens += InlineMathToken(placeholder = placeholder, tex = tex)
                output.append(placeholder)
                index = end + 2
                continue
            }
        }

        if (
            content[index] == '$' &&
            !isEscaped(content, index) &&
            content.getOrNull(index + 1) != '$'
        ) {
            val end = findClosingInlineDollar(content, index + 1)
            if (end != -1) {
                val tex = content.substring(index + 1, end)
                val placeholder = createPlaceholder(inlineMathIndex++)
                tokens += InlineMathToken(placeholder = placeholder, tex = tex)
                output.append(placeholder)
                index = end + 1
                continue
            }
        }

        output.append(content[index])
        index++
    }

    return output.toString()
}

private fun detectDisplayMath(content: String, index: Int): DisplayMathMatch? {
    if (isEscaped(content, index)) return null

    if (content.startsWith("\\[", index)) {
        val end = findClosingDelimiter(content, index + 2, "\\]")
        if (end != -1) {
            return DisplayMathMatch(
                tex = content.substring(index + 2, end).trim(),
                endExclusive = end + 2
            )
        }
    }

    if (content.startsWith("$$", index)) {
        val end = findClosingDelimiter(content, index + 2, "$$")
        if (end != -1) {
            return DisplayMathMatch(
                tex = content.substring(index + 2, end).trim(),
                endExclusive = end + 2
            )
        }
    }

    return null
}

private fun detectFenceDelimiter(content: String, index: Int): String? {
    if (index >= content.length) return null
    if (content[index] != '`' && content[index] != '~') return null

    val marker = content[index]
    var end = index
    while (end < content.length && content[end] == marker) {
        end++
    }

    val fenceLength = end - index
    return if (fenceLength >= 3) content.substring(index, end) else null
}

private fun findFenceBlockEnd(content: String, start: Int, fence: String): Int {
    var index = content.indexOf('\n', start)
    if (index == -1) return content.length
    index++

    while (index < content.length) {
        val fenceStart = findFenceStartInLine(content, index, fence)
        if (fenceStart != -1) {
            val lineEnd = content.indexOf('\n', fenceStart).let { if (it == -1) content.length else it + 1 }
            return lineEnd
        }
        index = content.indexOf('\n', index).let { if (it == -1) content.length else it + 1 }
    }

    return content.length
}

private fun detectBacktickRun(content: String, index: Int): Int {
    if (content.getOrNull(index) != '`') return 0
    var end = index
    while (end < content.length && content[end] == '`') {
        end++
    }
    return end - index
}

private fun findInlineCodeEnd(content: String, start: Int, backtickCount: Int): Int {
    var index = start
    while (index < content.length) {
        if (content[index] == '`') {
            val runLength = detectBacktickRun(content, index)
            if (runLength == backtickCount) {
                return index + runLength
            }
            index += runLength
        } else {
            index++
        }
    }
    return -1
}

private fun findClosingDelimiter(content: String, start: Int, delimiter: String): Int {
    var index = start
    while (index <= content.length - delimiter.length) {
        if (!isEscaped(content, index) && content.startsWith(delimiter, index)) {
            return index
        }
        index++
    }
    return -1
}

private fun findClosingInlineDollar(content: String, start: Int): Int {
    if (content.getOrNull(start)?.isWhitespace() == true) return -1

    var index = start
    while (index < content.length) {
        val char = content[index]
        if (char == '\n') return -1
        if (
            char == '$' &&
            !isEscaped(content, index) &&
            content.getOrNull(index - 1)?.isWhitespace() != true
        ) {
            return index
        }
        index++
    }
    return -1
}

private fun createPlaceholder(index: Int): String = "$INLINE_MATH_PLACEHOLDER_PREFIX$index$INLINE_MATH_PLACEHOLDER_SUFFIX"

private fun isFenceStart(content: String, index: Int): Boolean {
    val lineStart = content.lastIndexOf('\n', index - 1).let { if (it == -1) 0 else it + 1 }
    val leadingSpaces = index - lineStart
    if (leadingSpaces > MAX_FENCE_INDENT) return false
    for (current in lineStart until index) {
        if (content[current] != ' ') return false
    }
    return true
}

private fun findFenceStartInLine(content: String, lineStart: Int, fence: String): Int {
    var index = lineStart
    var leadingSpaces = 0
    while (index < content.length && content[index] == ' ' && leadingSpaces < MAX_FENCE_INDENT) {
        index++
        leadingSpaces++
    }

    val marker = fence.first()
    var fenceEnd = index
    while (fenceEnd < content.length && content[fenceEnd] == marker) {
        fenceEnd++
    }

    val fenceLength = fenceEnd - index
    if (fenceLength < fence.length) return -1

    val lineEnd = content.indexOf('\n', fenceEnd).let { if (it == -1) content.length else it }
    for (current in fenceEnd until lineEnd) {
        val character = content[current]
        if (character != ' ' && character != '\t') {
            return -1
        }
    }

    return index
}

private fun isEscaped(content: String, index: Int): Boolean {
    var backslashCount = 0
    var current = index - 1
    while (current >= 0 && content[current] == '\\') {
        backslashCount++
        current--
    }
    return backslashCount % 2 == 1
}
