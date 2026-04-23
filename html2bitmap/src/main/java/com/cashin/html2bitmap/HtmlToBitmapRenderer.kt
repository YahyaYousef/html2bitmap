package com.cashin.html2bitmap

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Bitmap.createBitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal object HtmlToBitmapRenderer {

    private const val CONTENT_HEIGHT_POLL_INTERVAL_MS = 100L
    private const val MAX_POLL_ATTEMPTS = 50 // 100ms * 50 = 5 seconds max polling

    suspend fun render(
        context: Context,
        html: String,
        widthPx: Int,
        config: BitmapConfig
    ): Bitmap = withContext(Dispatchers.Main) {

        // STEP 1: Capture locale before ANY WebView interaction
        val locale = LocaleHelper.captureLocale(context)
        val density = config.density ?: context.resources.displayMetrics.density
        val safeContext = LocaleHelper.createLockedContext(context, locale, density)
        val mainHandler = Handler(Looper.getMainLooper())

        // Must be called BEFORE any WebView instance is created (static method)
        WebView.enableSlowWholeDocumentDraw()

        suspendCancellableCoroutine { continuation ->

            val webView = WebView(safeContext).apply {
                settings.apply {
                    javaScriptEnabled = config.javascriptEnabled
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    textZoom = config.textZoom
                    blockNetworkImage = false
                }
                setBackgroundColor(config.backgroundColor)
                // Ensure WebView is visible for rendering purposes
                visibility = View.VISIBLE
            }

            // Proper measure & layout before loading content
            val widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY)
            val heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            webView.measure(widthMeasureSpec, heightMeasureSpec)
            val initialHeight = webView.measuredHeight.coerceAtLeast(1)
            webView.layout(0, 0, widthPx, initialHeight)

            /**
             * Polls contentHeight until it becomes > 0, then captures the bitmap.
             */
            fun pollAndCapture(attempt: Int) {
                if (!continuation.isActive) return

                val contentHeightCss = webView.contentHeight
                if (contentHeightCss > 0) {
                    try {
                        // webView.contentHeight is in CSS pixels (DP). 
                        // Convert to physical pixels based on the context density.
                        val contentHeightPx = (contentHeightCss * density).toInt().coerceAtLeast(1)

                        // Re-measure and layout with the real content height
                        webView.measure(
                            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(contentHeightPx, View.MeasureSpec.EXACTLY)
                        )
                        webView.layout(0, 0, widthPx, contentHeightPx)

                        val bitmap = createBitmap(
                            widthPx,
                            contentHeightPx,
                            Bitmap.Config.ARGB_8888
                        )
                        val canvas = Canvas(bitmap)
                        webView.draw(canvas)

                        webView.destroy()

                        // STEP 3: Restore locale after WebView is done
                        LocaleHelper.restoreLocale(context, locale)

                        if (continuation.isActive) continuation.resume(bitmap)
                    } catch (e: Exception) {
                        webView.destroy()
                        LocaleHelper.restoreLocale(context, locale)
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }
                } else if (attempt < MAX_POLL_ATTEMPTS) {
                    mainHandler.postDelayed(
                        { pollAndCapture(attempt + 1) },
                        CONTENT_HEIGHT_POLL_INTERVAL_MS
                    )
                } else {
                    webView.destroy()
                    LocaleHelper.restoreLocale(context, locale)
                    if (continuation.isActive) {
                        continuation.resumeWithException(
                            IllegalStateException(
                                "WebView contentHeight remained 0 after ${MAX_POLL_ATTEMPTS * CONTENT_HEIGHT_POLL_INTERVAL_MS}ms"
                            )
                        )
                    }
                }
            }

            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    // Start polling after a short delay to let layout settle
                    mainHandler.postDelayed(
                        { pollAndCapture(0) },
                        200L
                    )
                }

                override fun onReceivedError(
                    view: WebView,
                    errorCode: Int,
                    description: String,
                    failingUrl: String
                ) {
                    // non-fatal for local HTML
                }
            }

            // Use loadDataWithBaseURL — NOT loadData (avoids Base64/charset issues)
            webView.loadDataWithBaseURL(
                null,
                html,
                "text/html",
                "UTF-8",
                null
            )

            // Clean up if coroutine is cancelled — MUST post to main thread
            continuation.invokeOnCancellation {
                mainHandler.post {
                    webView.stopLoading()
                    webView.destroy()
                    LocaleHelper.restoreLocale(context, locale)
                }
            }
        }
    }
}
