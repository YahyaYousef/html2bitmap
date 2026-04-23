package com.cashin.html2bitmap

import android.content.Context
import android.graphics.Bitmap
import android.os.Looper
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class Html2Bitmap private constructor(
    private val context: Context,
    private val html: String,
    private val bitmapWidth: Int,
    private val config: BitmapConfig,
    private val configurator: Html2BitmapConfigurator?
) {

    suspend fun getBitmapAsync(): Bitmap = withTimeout(config.timeoutMs) {
        HtmlToBitmapRenderer.render(
            context = context,
            html = html,
            widthPx = bitmapWidth,
            config = config,
            configurator = configurator
        )
    }

    /**
     * Blocking compatibility API similar to iZettle's getBitmap().
     *
     * Returns null on failure/timeout or when called from main thread in non-strict mode.
     */
    fun getBitmap(): Bitmap? {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            if (config.strictMode) {
                throw IllegalStateException("Html2Bitmap.getBitmap() must not run on the main thread.")
            }
            return null
        }
        return runBlocking {
            runCatching { getBitmapAsync() }.getOrNull()
        }
    }

    class Builder {
        private var context: Context? = null
        private var html: String? = null
        private var bitmapWidth: Int = 480
        private var config: BitmapConfig = BitmapConfig()
        private var configurator: Html2BitmapConfigurator? = null

        fun setContext(context: Context): Builder = apply {
            this.context = context
        }

        fun setHtml(html: String): Builder = apply {
            this.html = html
        }

        fun setBitmapWidth(bitmapWidth: Int): Builder = apply {
            this.bitmapWidth = bitmapWidth
        }

        fun setTimeout(timeoutMs: Long): Builder = apply {
            config = config.copy(timeoutMs = timeoutMs)
        }

        fun setMeasureDelay(measureDelayMs: Long): Builder = apply {
            config = config.copy(measureDelayMs = measureDelayMs)
        }

        fun setScreenshotDelay(screenshotDelayMs: Long): Builder = apply {
            config = config.copy(screenshotDelayMs = screenshotDelayMs)
        }

        fun setStrictMode(strictMode: Boolean): Builder = apply {
            config = config.copy(strictMode = strictMode)
        }

        fun setTextZoom(textZoom: Int?): Builder = apply {
            config = config.copy(textZoom = textZoom)
        }

        fun setJavascriptEnabled(enabled: Boolean): Builder = apply {
            config = config.copy(javascriptEnabled = enabled)
        }

        fun setBackgroundColor(color: Int): Builder = apply {
            config = config.copy(backgroundColor = color)
        }

        fun setConfig(config: BitmapConfig): Builder = apply {
            this.config = config
        }

        fun setConfigurator(configurator: Html2BitmapConfigurator?): Builder = apply {
            this.configurator = configurator
        }

        fun build(): Html2Bitmap {
            val requiredContext = requireNotNull(context) { "Context is required." }
            val requiredHtml = requireNotNull(html) { "HTML content is required." }
            return Html2Bitmap(
                context = requiredContext,
                html = requiredHtml,
                bitmapWidth = bitmapWidth,
                config = config,
                configurator = configurator
            )
        }
    }
}

fun interface Html2BitmapConfigurator {
    fun configureWebView(webView: android.webkit.WebView)
}

object HtmlToBitmap {
    /**
     * Coroutine-first API retained for existing consumers.
     */
    suspend fun from(
        context: Context,
        html: String,
        widthPx: Int,
        config: BitmapConfig = BitmapConfig()
    ): Bitmap {
        return Html2Bitmap.Builder()
            .setContext(context)
            .setHtml(html)
            .setBitmapWidth(widthPx)
            .setConfig(config)
            .build()
            .getBitmapAsync()
    }
}
