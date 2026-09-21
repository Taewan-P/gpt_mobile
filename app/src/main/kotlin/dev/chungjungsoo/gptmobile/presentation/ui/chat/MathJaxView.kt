package dev.chungjungsoo.gptmobile.presentation.ui.chat

import android.annotation.SuppressLint
import android.content.Context
import android.util.LruCache
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onLayoutRectChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.max
import kotlin.math.roundToInt
import org.json.JSONObject

private const val MATH_JAX_BASE_URL = "file:///android_asset/mathjax/"
private const val MATH_JAX_RENDER_RETRY_DELAY_MILLIS = 50L
private const val MAX_MATH_JAX_RENDER_RETRIES = 60
private const val DISPLAY_MATH_HEIGHT_CACHE_SIZE = 128
private const val MATH_VIEWPORT_OVERSCAN_DP = 48

private const val MATH_JAX_HTML = """
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no" />
<style>
html, body {
  margin: 0;
  padding: 0;
  background: transparent;
  overflow: hidden;
}
body {
  display: inline-block;
}
body.display {
  display: block;
  width: 100%;
}
#root {
  display: inline-block;
  margin: 0;
  padding: 2px 0;
}
#root.display {
  display: block;
  width: 100%;
  text-align: center;
  padding: 8px 0;
}
#root pre {
  margin: 0;
  white-space: pre-wrap;
  font-family: monospace;
}
mjx-container[jax="SVG"] {
  display: inline-block;
  margin: 0 !important;
  max-width: 100%;
}
#root.display mjx-container[jax="SVG"] {
  display: block;
  text-align: center;
}
svg {
  overflow: visible;
  max-width: 100%;
}
</style>
<script>
(() => {
  const storageFactory = () => {
    const store = {};
    return {
      getItem(key) {
        return Object.prototype.hasOwnProperty.call(store, key) ? store[key] : null;
      },
      setItem(key, value) {
        store[key] = String(value);
      },
      removeItem(key) {
        delete store[key];
      },
      clear() {
        Object.keys(store).forEach((key) => delete store[key]);
      }
    };
  };
  try {
    if (!window.localStorage) {
      Object.defineProperty(window, 'localStorage', {value: storageFactory(), configurable: true});
    }
  } catch (error) {
    Object.defineProperty(window, 'localStorage', {value: storageFactory(), configurable: true});
  }
})();
window.mathJaxReady = false;
window.MathJax = {
  options: {
    enableMenu: false,
    enableEnrichment: false,
    enableComplexity: false,
    enableSpeech: false,
    enableBraille: false,
    renderActions: {
      addMenu: [],
      checkLoading: [],
      assistiveMml: []
    },
    menuOptions: {
      settings: {
        enrich: false,
        speech: false,
        braille: false,
        assistiveMml: false
      }
    }
  },
  startup: {
    typeset: false,
    ready: () => {
      MathJax.startup.defaultReady();
      window.mathJaxReady = true;
    }
  },
  svg: {
    fontCache: 'none'
  }
};
</script>
<script defer src="tex-svg.js"></script>
<script>
window.renderMath = function(expression, displayMode, textColor, fontSizePx) {
  if (!window.mathJaxReady || !window.MathJax || typeof MathJax.tex2svg !== 'function') {
    return 'loading';
  }

  const root = document.getElementById('root');
  document.body.className = displayMode ? 'display' : 'inline';
  document.body.style.color = textColor;
  document.body.style.fontSize = fontSizePx + 'px';
  root.className = displayMode ? 'display' : 'inline';

  try {
    const node = MathJax.tex2svg(expression, { display: displayMode });
    root.replaceChildren(node);
  } catch (error) {
    const fallback = document.createElement(displayMode ? 'pre' : 'span');
    fallback.textContent = displayMode ? '\\[' + expression + '\\]' : '\\(' + expression + '\\)';
    root.replaceChildren(fallback);
  }

  const measuredNode = root.firstElementChild || root;
  const rect = measuredNode.getBoundingClientRect();
  const width = Math.max(Math.ceil(rect.width), Math.ceil(root.scrollWidth), 1);
  const height = Math.max(Math.ceil(rect.height), Math.ceil(root.scrollHeight), 1);
  return {
    width,
    height,
    html: root.innerHTML
  };
};

window.applyCachedMath = function(html, displayMode, textColor, fontSizePx) {
  const root = document.getElementById('root');
  document.body.className = displayMode ? 'display' : 'inline';
  document.body.style.color = textColor;
  document.body.style.fontSize = fontSizePx + 'px';
  root.className = displayMode ? 'display' : 'inline';
  root.innerHTML = html;
  return true;
};
</script>
</head>
<body>
<div id="root" class="inline"></div>
</body>
</html>
"""

private data class MathRenderRequest(
    val tex: String,
    val displayMode: Boolean,
    val fontSizePx: Int,
    val textColorCss: String,
    val minimumHeightPx: Int
)

private data class MathRenderResult(
    val widthPx: Int,
    val heightPx: Int,
    val html: String
)

private object MathJaxDisplayHeightCache {
    private val cache = LruCache<String, Int>(DISPLAY_MATH_HEIGHT_CACHE_SIZE)

    fun get(request: MathRenderRequest): Int? = cache.get(request.cacheKey())

    fun put(
        request: MathRenderRequest,
        heightPx: Int
    ) {
        cache.put(request.cacheKey(), heightPx)
    }
}

private object MathJaxRenderCache {
    private val cache = LruCache<String, MathRenderResult>(DISPLAY_MATH_HEIGHT_CACHE_SIZE)

    fun get(request: MathRenderRequest): MathRenderResult? = cache.get(request.cacheKey())

    fun put(
        request: MathRenderRequest,
        result: MathRenderResult
    ) {
        cache.put(request.cacheKey(), result)
    }
}

@Composable
internal fun InlineMathView(tex: String) {
    val density = LocalDensity.current
    val textColor = LocalContentColor.current
    val fontSize = MaterialTheme.typography.bodyMedium.fontSize
        .takeIf { it.type != TextUnitType.Unspecified }
        ?: 16.sp
    val fontSizeCssPx = remember(fontSize, density.fontScale) {
        (fontSize.value * density.fontScale).roundToInt()
    }
    val minimumHeightCssPx = remember(fontSizeCssPx) {
        (fontSizeCssPx * 1.4f).roundToInt()
    }
    val textColorCss = remember(textColor) {
        formatCssColor(textColor)
    }
    val request = remember(tex, fontSizeCssPx, textColorCss, minimumHeightCssPx) {
        MathRenderRequest(
            tex = tex,
            displayMode = false,
            fontSizePx = fontSizeCssPx,
            textColorCss = textColorCss,
            minimumHeightPx = minimumHeightCssPx
        )
    }

    MathJaxFormulaView(
        request = request,
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
internal fun DisplayMathView(
    tex: String,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val textColor = LocalContentColor.current
    val fontSize = MaterialTheme.typography.bodyMedium.fontSize
        .takeIf { it.type != TextUnitType.Unspecified }
        ?: 16.sp
    val fontSizeCssPx = remember(fontSize, density.fontScale) {
        (fontSize.value * density.fontScale).roundToInt()
    }
    val minimumHeightCssPx = remember(tex, fontSizeCssPx) {
        estimateDisplayMathMinimumHeightPx(tex, fontSizeCssPx)
    }
    val textColorCss = remember(textColor) {
        formatCssColor(textColor)
    }
    val request = remember(tex, fontSizeCssPx, textColorCss, minimumHeightCssPx) {
        MathRenderRequest(
            tex = tex,
            displayMode = true,
            fontSizePx = fontSizeCssPx,
            textColorCss = textColorCss,
            minimumHeightPx = minimumHeightCssPx
        )
    }
    var measuredHeightCssPx by remember(request) {
        mutableIntStateOf(MathJaxDisplayHeightCache.get(request) ?: minimumHeightCssPx)
    }

    MathJaxFormulaView(
        request = request,
        modifier = modifier.height(measuredHeightCssPx.dp),
        onMeasured = { heightPx ->
            val resolvedHeightPx = heightPx.coerceAtLeast(minimumHeightCssPx)
            measuredHeightCssPx = resolvedHeightPx
            MathJaxDisplayHeightCache.put(request, resolvedHeightPx)
        }
    )
}

@Composable
private fun MathJaxFormulaView(
    request: MathRenderRequest,
    modifier: Modifier = Modifier,
    onMeasured: (Int) -> Unit = {}
) {
    val windowSize = LocalWindowInfo.current.containerSize
    val overscanPx = with(LocalDensity.current) { MATH_VIEWPORT_OVERSCAN_DP.dp.toPx() }
    var visibleInWindow by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .semantics { contentDescription = request.tex }
            .onLayoutRectChanged(throttleMillis = 16L, debounceMillis = 0L) { bounds ->
                val windowBounds = bounds.boundsInWindow
                val nextVisible = formulaVisibleInWindow(
                    boundsInWindow = Rect(
                        windowBounds.left.toFloat(),
                        windowBounds.top.toFloat(),
                        windowBounds.right.toFloat(),
                        windowBounds.bottom.toFloat()
                    ),
                    windowSize = windowSize,
                    overscanPx = overscanPx
                )
                if (visibleInWindow != nextVisible) {
                    visibleInWindow = nextVisible
                }
            }
    ) {
        if (!visibleInWindow) return@Box
        AndroidView(
            factory = { context -> MathJaxWebView(context) },
            onReset = { webView -> webView.prepareForReuse() },
            onRelease = { webView -> webView.releaseFromComposition() },
            update = { webView ->
                webView.contentDescription = request.tex
                webView.setRenderRequest(request, onMeasured)
            }
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
private class MathJaxWebView(context: Context) : WebView(context) {
    private var pageLoaded = false
    private var renderedRequest: MathRenderRequest? = null
    private var pendingRequest: MathRenderRequest? = null
    private var onMeasured: ((Int) -> Unit)? = null
    private var renderRetryCount = 0
    private val renderRetryRunnable = Runnable { renderPendingRequest() }
    private var released = false

    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        isClickable = false
        isFocusable = false
        isFocusableInTouchMode = false
        isHorizontalScrollBarEnabled = false
        isLongClickable = false
        isVerticalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        setOnLongClickListener { true }

        settings.allowContentAccess = false
        settings.allowFileAccess = true
        settings.blockNetworkLoads = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.javaScriptEnabled = true

        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                pageLoaded = true
                renderRetryCount = 0
                renderPendingRequest()
            }
        }

        loadDataWithBaseURL(
            MATH_JAX_BASE_URL,
            MATH_JAX_HTML,
            "text/html",
            "utf-8",
            null
        )
    }

    fun setRenderRequest(
        request: MathRenderRequest,
        onMeasured: (Int) -> Unit
    ) {
        if (released) return
        this.onMeasured = onMeasured
        if (request == renderedRequest && pendingRequest == null) {
            return
        }

        if (applyCachedResult(request)) {
            return
        }
        clearRenderedContent()
        pendingRequest = request
        renderRetryCount = 0
        renderPendingRequest()
    }

    fun prepareForReuse() {
        if (released) return
        removeCallbacks(renderRetryRunnable)
        clearRenderedContent()
        renderedRequest = null
        pendingRequest = null
        onMeasured = null
        renderRetryCount = 0
    }

    fun releaseFromComposition() {
        if (released) return
        prepareForReuse()
        released = true
        destroy()
    }

    private fun renderPendingRequest() {
        val request = pendingRequest ?: return
        if (!pageLoaded) return
        if (applyCachedResult(request)) {
            return
        }

        evaluateJavascript(buildRenderScript(request)) { rawResult ->
            if (rawResult == "\"loading\"") {
                scheduleRenderRetry()
                return@evaluateJavascript
            }

            val renderResult = parseRenderResult(rawResult)
            if (renderResult == null) {
                scheduleRenderRetry()
                return@evaluateJavascript
            }

            if (
                request.displayMode &&
                renderResult.heightPx <= request.minimumHeightPx &&
                renderRetryCount < MAX_MATH_JAX_RENDER_RETRIES
            ) {
                scheduleRenderRetry()
                return@evaluateJavascript
            }

            MathJaxRenderCache.put(request, renderResult)
            renderedRequest = request
            pendingRequest = null
            renderRetryCount = 0

            onMeasured?.invoke(max(renderResult.heightPx, request.minimumHeightPx))
        }
    }

    private fun scheduleRenderRetry() {
        if (renderRetryCount >= MAX_MATH_JAX_RENDER_RETRIES) return

        renderRetryCount += 1
        removeCallbacks(renderRetryRunnable)
        postDelayed(renderRetryRunnable, MATH_JAX_RENDER_RETRY_DELAY_MILLIS)
    }

    private fun clearRenderedContent() {
        if (!pageLoaded) return

        evaluateJavascript(
            """
            (function() {
              const root = document.getElementById('root');
              if (root) {
                root.replaceChildren();
              }
              return true;
            })();
            """.trimIndent(),
            null
        )
    }

    private fun applyCachedResult(request: MathRenderRequest): Boolean {
        val cachedResult = MathJaxRenderCache.get(request) ?: return false
        if (!pageLoaded) return false

        evaluateJavascript(buildApplyCachedScript(request, cachedResult), null)
        renderedRequest = request
        pendingRequest = null
        renderRetryCount = 0
        onMeasured?.invoke(max(cachedResult.heightPx, request.minimumHeightPx))
        return true
    }
}

private fun MathRenderRequest.cacheKey(): String = buildString(tex.length + 32) {
    append(displayMode)
    append('|')
    append(fontSizePx)
    append('|')
    append(textColorCss)
    append('|')
    append(tex)
}

internal fun formulaVisibleInWindow(
    boundsInWindow: Rect,
    windowSize: IntSize,
    overscanPx: Float
): Boolean {
    if (boundsInWindow.width <= 0f || boundsInWindow.height <= 0f) return false
    if (windowSize.width <= 0 || windowSize.height <= 0) return false
    val window = Rect(
        left = -overscanPx,
        top = -overscanPx,
        right = windowSize.width.toFloat() + overscanPx,
        bottom = windowSize.height.toFloat() + overscanPx
    )
    return boundsInWindow.overlaps(window)
}

private fun buildRenderScript(request: MathRenderRequest): String = """
    (function() {
      if (typeof window.renderMath !== 'function') {
        return null;
      }
      return window.renderMath(
        "${escapeJavaScriptString(request.tex)}",
        ${request.displayMode},
        "${request.textColorCss}",
        ${request.fontSizePx}
      );
    })();
""".trimIndent()

private fun buildApplyCachedScript(
    request: MathRenderRequest,
    result: MathRenderResult
): String = """
    (function() {
      if (typeof window.applyCachedMath !== 'function') {
        return null;
      }
      return window.applyCachedMath(
        "${escapeJavaScriptString(result.html)}",
        ${request.displayMode},
        "${request.textColorCss}",
        ${request.fontSizePx}
      );
    })();
""".trimIndent()

private fun parseRenderResult(rawResult: String?): MathRenderResult? {
    val result = rawResult
        ?.takeIf { it.isNotBlank() && it != "null" }
        ?: return null
    val json = JSONObject(result)
    return MathRenderResult(
        widthPx = json.getInt("width"),
        heightPx = json.getInt("height"),
        html = json.getString("html")
    )
}

private fun formatCssColor(color: Color): String {
    val red = (color.red * 255).roundToInt()
    val green = (color.green * 255).roundToInt()
    val blue = (color.blue * 255).roundToInt()
    return "rgba($red, $green, $blue, ${color.alpha})"
}

private fun estimateDisplayMathMinimumHeightPx(
    tex: String,
    fontSizePx: Int
): Int {
    val lineCount = tex.split("\\\\").size.coerceAtLeast(1)
    val containsTallOperators = listOf(
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
    ).any(tex::contains)
    val baseEmHeight = when {
        lineCount >= 3 -> 7.5f
        lineCount == 2 -> 5.8f
        containsTallOperators -> 4.8f
        else -> 3.2f
    }
    return (fontSizePx * baseEmHeight).roundToInt()
}

private fun escapeJavaScriptString(text: String): String = buildString(text.length) {
    text.forEach { character ->
        append(
            when (character) {
                '\\' -> "\\\\"
                '"' -> "\\\""
                '\'' -> "\\'"
                '\n' -> "\\n"
                '\r' -> "\\r"
                '\t' -> "\\t"
                '\u2028' -> "\\u2028"
                '\u2029' -> "\\u2029"
                else -> character
            }
        )
    }
}
