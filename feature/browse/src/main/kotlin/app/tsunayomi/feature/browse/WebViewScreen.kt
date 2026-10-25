package app.tsunayomi.feature.browse

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.util.system.setDefaultSettings
import eu.kanade.tachiyomi.util.system.setUserAgent

@Composable
internal fun WebViewScreen(
    url: String,
    sourceId: Long,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WebViewViewModel = hiltViewModel(),
) {
    val headers by produceState<Map<String, String>?>(null, sourceId) {
        value = viewModel.loadHeaders(sourceId)
    }
    // The User-Agent has to be set before the first request, so the page waits for the headers.
    val loadedHeaders = headers ?: return
    WebViewScreenContent(
        url = url,
        headers = loadedHeaders,
        onClose = onClose,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WebViewScreenContent(
    url: String,
    headers: Map<String, String>,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var webView: WebView? by remember { mutableStateOf(null) }
    var currentUrl by remember { mutableStateOf(url) }
    var pageTitle by remember { mutableStateOf("") }
    var progress by remember { mutableFloatStateOf(0f) }

    BackHandler {
        if (webView?.canGoBack() == true) {
            webView?.goBack()
        } else {
            onClose()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            CookieManager.getInstance().flush()
            webView?.destroy()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = pageTitle.ifEmpty { currentUrl },
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (pageTitle.isNotEmpty()) {
                                Text(
                                    text = currentUrl,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Outlined.Close, stringResource(R.string.feature_browse_webview_close))
                        }
                    },
                    actions = {
                        IconButton(onClick = { webView?.reload() }) {
                            Icon(Icons.Outlined.Refresh, stringResource(R.string.feature_browse_webview_refresh))
                        }
                        IconButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                runCatching { context.startActivity(intent) }
                            },
                        ) {
                            Icon(Icons.Outlined.OpenInBrowser, stringResource(R.string.feature_browse_webview_open_in_browser))
                        }
                    },
                )
                if (progress in 0.01f..0.99f) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        setDefaultSettings()
                        val userAgent = headers["User-Agent"] ?: NetworkHelper.defaultUserAgent()
                        setUserAgent(userAgent)
                        webChromeClient = object : WebChromeClient() {
                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                pageTitle = title.orEmpty()
                            }

                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = newProgress / 100f
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, startedUrl: String?, favicon: Bitmap?) {
                                currentUrl = startedUrl ?: currentUrl
                            }

                            override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                                currentUrl = finishedUrl ?: currentUrl
                                pageTitle = view?.title ?: pageTitle
                                CookieManager.getInstance().flush()
                            }
                        }
                        val extraHeaders = headers - "User-Agent"
                        if (extraHeaders.isNotEmpty()) {
                            loadUrl(url, extraHeaders)
                        } else {
                            loadUrl(url)
                        }
                        webView = this
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
