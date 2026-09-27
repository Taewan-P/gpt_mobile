package dev.chungjungsoo.gptmobile.ui.component

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.chungjungsoo.gptmobile.R
import java.io.ByteArrayInputStream

/**
 * Sandboxed live artifact preview pane for interactive HTML, CSS, SVG, and code outputs.
 * Network access is denied at both WebView and content-policy boundaries.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SandboxedArtifactView(
    title: String,
    content: String,
    isHtmlOrSvg: Boolean = true,
    modifier: Modifier = Modifier
) {
    var showPreview by remember { mutableStateOf(isHtmlOrSvg) }
    var scriptsEnabled by remember(content) { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )

                if (isHtmlOrSvg) {
                    FilterChip(
                        selected = showPreview,
                        onClick = { showPreview = true },
                        label = { Text(stringResource(R.string.artifact_preview)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = null,
                                modifier = Modifier.padding(2.dp)
                            )
                        }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    FilterChip(
                        selected = !showPreview,
                        onClick = { showPreview = false },
                        label = { Text(stringResource(R.string.artifact_code)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = null,
                                modifier = Modifier.padding(2.dp)
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            if (showPreview && isHtmlOrSvg) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.artifact_scripts), modifier = Modifier.weight(1f))
                    Switch(checked = scriptsEnabled, onCheckedChange = { scriptsEnabled = it })
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                if (showPreview && isHtmlOrSvg) {
                    AndroidView(
                        factory = { ctx ->
                            ArtifactPreviewWebView(ctx).apply {
                                settings.apply {
                                    javaScriptEnabled = false
                                    blockNetworkLoads = true
                                    blockNetworkImage = true
                                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                    javaScriptCanOpenWindowsAutomatically = false
                                    setSupportMultipleWindows(false)
                                    allowFileAccess = false
                                    allowContentAccess = false
                                    domStorageEnabled = false
                                    cacheMode = WebSettings.LOAD_NO_CACHE
                                }
                                webViewClient = object : WebViewClient() {
                                    override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                                        (view as? ArtifactPreviewWebView)?.dispose()
                                        showPreview = false
                                        return true
                                    }

                                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = true

                                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                        // Block all outward navigation from sandbox
                                        return true
                                    }

                                    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                                        if (request?.url?.scheme in setOf("data", "blob", "about")) return null
                                        return WebResourceResponse(
                                            "text/plain",
                                            "UTF-8",
                                            403,
                                            "Blocked",
                                            emptyMap(),
                                            ByteArrayInputStream(ByteArray(0))
                                        )
                                    }
                                }
                            }
                        },
                        onReset = null,
                        onRelease = { view -> view.dispose() },
                        update = { view ->
                            val document = wrapInSandboxHtml(content, scriptsEnabled)
                            if (!view.disposed && view.tag != document) {
                                view.stopLoading()
                                view.settings.javaScriptEnabled = scriptsEnabled
                                view.tag = document
                                view.loadDataWithBaseURL(null, document, "text/html", "UTF-8", null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(280.dp)
                    )
                } else {
                    Text(
                        text = content,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}

internal fun wrapInSandboxHtml(raw: String, scriptsEnabled: Boolean = false): String {
    val scriptPolicy = if (scriptsEnabled) "'unsafe-inline'" else "'none'"
    return """
        <!DOCTYPE html>
        <html>
        <head>
          <meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src $scriptPolicy; style-src 'unsafe-inline'; img-src data: blob:; media-src data: blob:; font-src data:; connect-src 'none'; frame-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'">
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
          <style>
            body { margin: 0; padding: 12px; font-family: sans-serif; background-color: transparent; }
          </style>
        </head>
        <body>
          $raw
        </body>
        </html>
    """.trimIndent()
}

private class ArtifactPreviewWebView(context: android.content.Context) : WebView(context) {
    var disposed = false
        private set

    fun dispose() {
        if (disposed) return
        disposed = true
        (parent as? android.view.ViewGroup)?.removeView(this)
        destroy()
    }
}
