package com.cashin.html2bitmap

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Bitmap.createBitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal object HtmlToBitmapRenderer {

    suspend fun render(
        context: Context,
        html: String,
        widthPx: Int,
        config: BitmapConfig,
        configurator: Html2BitmapConfigurator?
    ): Bitmap = withContext(Dispatchers.Main) {
        val locale = LocaleHelper.captureLocale(context)
        val safeContext = LocaleHelper.createLockedContext(context, locale)
        val mainHandler = Handler(Looper.getMainLooper())
        WebView.enableSlowWholeDocumentDraw()

        suspendCancellableCoroutine { continuation ->
            val webView = WebView(safeContext).apply {
                settings.apply {
                    javaScriptEnabled = config.javascriptEnabled
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    config.textZoom?.let { textZoom = it }
                    blockNetworkImage = false
                    builtInZoomControls = false
                    setSupportZoom(false)
                }
                setBackgroundColor(config.backgroundColor)
                isVerticalScrollBarEnabled = false
                configurator?.configureWebView(this)
            }

            val widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY)
            val heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(10, View.MeasureSpec.EXACTLY)
            webView.measure(widthMeasureSpec, heightMeasureSpec)
            webView.layout(0, 0, webView.measuredWidth, webView.measuredHeight)

            var progress = 0
            var hasScheduledCapture = false

            fun cleanupWebView() {
                webView.stopLoading()
                webView.destroy()
                mainHandler.removeCallbacksAndMessages(null)
            }

            fun captureBitmap() {
                if (!continuation.isActive) {
                    cleanupWebView()
                    return
                }
                try {
                    val contentHeight = webView.contentHeight.coerceAtLeast(1)
                    webView.measure(
                        View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(contentHeight, View.MeasureSpec.EXACTLY)
                    )
                    webView.layout(0, 0, webView.measuredWidth, webView.measuredHeight)

                    val bitmap = createBitmap(
                        webView.measuredWidth.coerceAtLeast(widthPx),
                        webView.measuredHeight.coerceAtLeast(1),
                        Bitmap.Config.ARGB_8888
                    )
                    val canvas = Canvas(bitmap)
                    webView.draw(canvas)
                    cleanupWebView()
                    continuation.resume(bitmap)
                } catch (error: Throwable) {
                    cleanupWebView()
                    continuation.resumeWithException(error)
                }
            }

            fun scheduleMeasureAndCapture() {
                if (hasScheduledCapture || !continuation.isActive) return
                hasScheduledCapture = true
                mainHandler.postDelayed(
                    {
                        if (!continuation.isActive) {
                            cleanupWebView()
                            return@postDelayed
                        }
                        if (webView.contentHeight == 0) {
                            hasScheduledCapture = false
                            scheduleMeasureAndCapture()
                            return@postDelayed
                        }
                        mainHandler.postDelayed(
                            { captureBitmap() },
                            config.screenshotDelayMs
                        )
                    },
                    config.measureDelayMs
                )
            }

            webView.webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    progress = newProgress
                    if (progress == 100) {
                        scheduleMeasureAndCapture()
                    }
                }
            }

            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    if (progress == 100) {
                        scheduleMeasureAndCapture()
                    }
                }

                override fun onReceivedError(
                    view: WebView,
                    errorCode: Int,
                    description: String,
                    failingUrl: String
                ) {
                    // Keep parity with iZettle behavior: non-fatal for raw HTML.
                }
            }

            webView.loadDataWithBaseURL(
                null,
                html,
                "text/html",
                "UTF-8",
                null
            )

            continuation.invokeOnCancellation {
                mainHandler.post {
                    cleanupWebView()
                }
            }
        }
    }
}
