package com.cashin.html2bitmap

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PaintFlagsDrawFilter
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.cashin.html2bitmap.content.ProgressChangedListener
import com.cashin.html2bitmap.content.WebViewContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class Html2BitmapWebView constructor(
    private val context: Context,
    private val content: WebViewContent,
    private val bitmapWidth: Int,
    private val measureDelay: Int,
    private val screenshotDelay: Int,
    private val strictMode: Boolean,
    private val textZoom: Int?,
    private val html2BitmapConfigurator: Html2BitmapConfigurator?
) : ProgressChangedListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var measureJob: Job? = null
    private var screenshotJob: Job? = null

    private lateinit var callback: BitmapCallback
    private lateinit var webView: WebView
    private var progress = 0
    private var isCleanedUp = false

    fun load(callback: BitmapCallback) {
        this.callback = callback

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            WebView.enableSlowWholeDocumentDraw()
        }

        webView = WebView(context).apply {
            setInitialScale(100)
            isVerticalScrollBarEnabled = false
        }

        val settings: WebSettings = webView.settings
        settings.builtInZoomControls = false
        settings.setSupportZoom(false)

        textZoom?.let { settings.textZoom = it }
        html2BitmapConfigurator?.configureWebView(webView)

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                progress = newProgress
                progressChanged()
            }
        }

        content.setDoneListener(this)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, url: String): WebResourceResponse? {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                    val response = content.loadResource(view.context, Uri.parse(url))
                    return response ?: super.shouldInterceptRequest(view, url)
                }
                return super.shouldInterceptRequest(view, url)
            }

            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                val response = content.loadResource(view.context, request.url)
                return response ?: super.shouldInterceptRequest(view, request)
            }
        }

        val widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(bitmapWidth, View.MeasureSpec.EXACTLY)
        val heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(10, View.MeasureSpec.EXACTLY)
        webView.measure(widthMeasureSpec, heightMeasureSpec)
        webView.layout(0, 0, webView.measuredWidth, webView.measuredHeight)
        content.loadContent(webView)
    }

    fun cleanup() {
        isCleanedUp = true
        webView.stopLoading()
        measureJob?.cancel()
        screenshotJob?.cancel()
        scope.cancel()
    }

    private fun pageFinished(delay: Int) {
        screenshotJob?.cancel()
        measureJob?.cancel()
        measureJob = scope.launch {
            delay(delay.toLong())
            runMeasureAndScheduleScreenshot()
        }
    }

    private fun runMeasureAndScheduleScreenshot() {
        ensureNotStopped()
        if (!content.isDone()) {
            return
        }
        if (webView.contentHeight == 0) {
            pageFinished(measureDelay)
            return
        }

        val widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(bitmapWidth, View.MeasureSpec.EXACTLY)
        val heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(webView.contentHeight, View.MeasureSpec.EXACTLY)
        webView.measure(widthMeasureSpec, heightMeasureSpec)
        webView.layout(0, 0, webView.measuredWidth, webView.measuredHeight)

        screenshotJob?.cancel()
        screenshotJob = scope.launch {
            delay(screenshotDelay.toLong())
            runScreenshot()
        }
    }

    private fun runScreenshot() {
        ensureNotStopped()
        if (!content.isDone()) {
            return
        }
        if (webView.measuredHeight == 0) {
            pageFinished(measureDelay)
            return
        }
        try {
            val screenshot = screenshot(webView)
            callback.finished(screenshot)
        } catch (throwable: Throwable) {
            callback.error(throwable)
        }
    }

    private fun ensureNotStopped() {
        if (!isCleanedUp) {
            return
        }
        if (strictMode) {
            throw IllegalStateException("Html2BitmapWebView was already cleaned up")
        }
        Log.d(TAG, "stopped but received coroutine callback on main thread")
    }

    private fun screenshot(webView: WebView): Bitmap {
        val bitmap = Bitmap.createBitmap(webView.measuredWidth, webView.measuredHeight, Bitmap.Config.RGB_565)
        val canvas = Canvas(bitmap)
        canvas.drawFilter = PaintFlagsDrawFilter(Paint.ANTI_ALIAS_FLAG, 0)
        webView.draw(canvas)
        return bitmap
    }

    override fun progressChanged() {
        if (progress == 100 && content.isDone()) {
            pageFinished(measureDelay)
        }
    }

    private companion object {
        private const val TAG = "Html2Bitmap"
    }
}
