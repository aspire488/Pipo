package com.pipo.robot.ui.home

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.graphics.Color as AColor
import android.os.Handler
import android.os.Looper
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.pipo.robot.BuildConfig

/**
 * Pipo's 3D room: the stage in assets/stage (Three.js + the Blender models) inside a WebView.
 * The stage only renders. HomeViewModel stays the source of truth: it streams Pipo's pose and
 * state in ([HomeViewModel.stageState]) and gets taps, messages and quick actions back.
 * Files are served from the APK under a fake https origin, so ES modules load and nothing
 * else on the device or the network is reachable from the page.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun Stage3D(vm: HomeViewModel, onMenu: () -> Unit, onMic: () -> Unit, onGames: () -> Unit, onFailed: (String) -> Unit) {
    val main = remember { Handler(Looper.getMainLooper()) }
    val bridge = remember {
        StageBridge(
            onSend = { t -> main.post { vm.sendChat(t) } },
            onAction = { a ->
                main.post {
                    when {
                        a == "menu" -> onMenu()
                        a == "mic" -> onMic()
                        a == "play" -> onGames()
                        a.startsWith("stage-error:") -> onFailed(a.removePrefix("stage-error:"))
                        else -> vm.stageAction(a)
                    }
                }
            },
            onTap = { t -> main.post { vm.stageTap(t) } },
            onReady = { main.post { vm.stageReady = true } },
        )
    }
    val web = remember { mutableListOf<WebView>() }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(AColor.rgb(13, 17, 28))
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.mediaPlaybackRequiresUserGesture = true
                isVerticalScrollBarEnabled = false
                overScrollMode = WebView.OVER_SCROLL_NEVER
                addJavascriptInterface(bridge, "PipoAndroid")
                bridge.isLowEnd = lowEnd(ctx)
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView, req: WebResourceRequest): WebResourceResponse? =
                        if (req.url.host == HOST) serveAsset(ctx, req.url.path.orEmpty()) else blocked()
                    override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest) = req.url.host != HOST
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                        if (BuildConfig.DEBUG) android.util.Log.d("PipoStage", "${m.messageLevel()}: ${m.message()}")
                        return true
                    }
                }
                loadUrl("https://$HOST/stage/index.html")
                web += this
            }
        },
    )

    // Stream the engine's truth into the stage, every other frame (~30 Hz).
    LaunchedEffect(Unit) {
        var n = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { }
            if (++n % 2L != 0L || !vm.stageReady) continue
            val js = "PipoStage.apply(${vm.stageState()})"
            web.firstOrNull()?.evaluateJavascript(js, null)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            vm.stageReady = false
            web.forEach { it.removeJavascriptInterface("PipoAndroid"); it.destroy() }
            web.clear()
        }
    }
}

private const val HOST = "pipo.app.local"

private fun mime(path: String) = when (path.substringAfterLast('.')) {
    "html" -> "text/html"; "js" -> "text/javascript"; "css" -> "text/css"; "glb" -> "model/gltf-binary"
    "png" -> "image/png"; "json" -> "application/json"; else -> "application/octet-stream"
}

private fun serveAsset(ctx: Context, path: String): WebResourceResponse {
    val clean = path.trimStart('/')
    // only the stage folder, never anything else in the APK
    if (!clean.startsWith("stage/") || ".." in clean) return blocked()
    return runCatching { WebResourceResponse(mime(clean), "utf-8", ctx.assets.open(clean)) }.getOrElse { blocked() }
}

private fun blocked() = WebResourceResponse("text/plain", "utf-8", 404, "Not Found", emptyMap(), null)

private fun lowEnd(ctx: Context): Boolean {
    val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    return am.isLowRamDevice || Runtime.getRuntime().availableProcessors() <= 4
}

/** What the page can call. Every call is posted to the main thread before touching the engine. */
class StageBridge(
    private val onSend: (String) -> Unit,
    private val onAction: (String) -> Unit,
    private val onTap: (String) -> Unit,
    private val onReady: () -> Unit,
) {
    @Volatile var isLowEnd = false
    @JavascriptInterface fun send(text: String) { text.trim().take(500).takeIf { it.isNotEmpty() }?.let(onSend) }
    @JavascriptInterface fun action(id: String) { onAction(id.take(320)) }
    @JavascriptInterface fun tap(target: String) { onTap(target.take(64)) }
    @JavascriptInterface fun ready() = onReady()
    @JavascriptInterface fun lowEnd() = isLowEnd
}
