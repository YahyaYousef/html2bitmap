package com.cashin.html2bitmap

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import android.os.Looper
import android.util.Log
import com.cashin.html2bitmap.content.WebViewContent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

class Html2Bitmap private constructor(
    private val context: Context,
    private val content: WebViewContent,
    private val bitmapWidth: Int,
    private val measureDelay: Int,
    private val screenshotDelay: Int,
    private val strictMode: Boolean,
    private val timeout: Long,
    private val textZoom: Int?,
    private val html2BitmapConfigurator: Html2BitmapConfigurator?
) {
    fun getBitmap(): Bitmap? = getBitmap(this)

    fun getWebViewContent(): WebViewContent = content

    class Builder() {
        private var context: Context? = null
        private var bitmapWidth = 480
        private var measureDelay = 300
        private var screenshotDelay = 300
        private var strictMode = false
        private var timeout: Long = 15
        private var content: WebViewContent? = null
        private var textZoom: Int? = null
        private var html2BitmapConfigurator: Html2BitmapConfigurator? = null

        constructor(context: Context, content: WebViewContent) : this() {
            setContext(context)
            setContent(content)
        }

        fun setContext(context: Context): Builder {
            this.context = context
            return this
        }

        fun setContent(content: WebViewContent): Builder {
            this.content = content
            return this
        }

        fun setBitmapWidth(bitmapWidth: Int): Builder {
            this.bitmapWidth = bitmapWidth
            return this
        }

        fun setMeasureDelay(measureDelay: Int): Builder {
            this.measureDelay = measureDelay
            return this
        }

        fun setScreenshotDelay(screenshotDelay: Int): Builder {
            this.screenshotDelay = screenshotDelay
            return this
        }

        fun setStrictMode(strictMode: Boolean): Builder {
            this.strictMode = strictMode
            return this
        }

        fun setTimeout(timeout: Long): Builder {
            this.timeout = timeout
            return this
        }

        fun setTextZoom(textZoom: Int?): Builder {
            this.textZoom = textZoom
            return this
        }

        fun setConfigurator(html2BitmapConfigurator: Html2BitmapConfigurator?): Builder {
            this.html2BitmapConfigurator = html2BitmapConfigurator
            return this
        }

        fun build(): Html2Bitmap {
            val checkedContext = checkNotNull(context)
            val checkedContent = checkNotNull(content)
            return Html2Bitmap(
                checkedContext,
                checkedContent,
                bitmapWidth,
                measureDelay,
                screenshotDelay,
                strictMode,
                timeout,
                textZoom,
                html2BitmapConfigurator
            )
        }
    }

    companion object {
        private const val TAG = "Html2Bitmap"

        private fun getBitmap(html2Bitmap: Html2Bitmap): Bitmap? {
            if (Looper.getMainLooper().thread === Thread.currentThread()) {
                Log.e(TAG, "getBitmap() must be called off the main thread")
                return null
            }

            return runBlocking {
                val renderResult = CompletableDeferred<Bitmap>()
                val webViewContext = createLocaleStableContext(html2Bitmap.context)
                val html2BitmapWebView = Html2BitmapWebView(
                    webViewContext,
                    html2Bitmap.content,
                    html2Bitmap.bitmapWidth,
                    html2Bitmap.measureDelay,
                    html2Bitmap.screenshotDelay,
                    html2Bitmap.strictMode,
                    html2Bitmap.textZoom,
                    html2Bitmap.html2BitmapConfigurator
                )

                try {
                    withContext(Dispatchers.Main.immediate) {
                        html2BitmapWebView.load(
                            object : BitmapCallback {
                                override fun finished(bitmap: Bitmap) {
                                    if (!renderResult.isCompleted) {
                                        renderResult.complete(bitmap)
                                    }
                                }

                                override fun error(error: Throwable) {
                                    if (!renderResult.isCompleted) {
                                        renderResult.completeExceptionally(error)
                                    }
                                }
                            }
                        )
                    }

                    withTimeout(TimeUnit.SECONDS.toMillis(html2Bitmap.timeout)) {
                        renderResult.await()
                    }
                } catch (exception: InterruptedException) {
                    Log.e(TAG, html2Bitmap.content.getRemoteResources().toString(), exception)
                    null
                } catch (exception: TimeoutCancellationException) {
                    Log.e(TAG, html2Bitmap.content.getRemoteResources().toString(), exception)
                    null
                } catch (exception: ExecutionException) {
                    Log.e(TAG, html2Bitmap.content.getRemoteResources().toString(), exception)
                    null
                } finally {
                    withContext(Dispatchers.Main.immediate) {
                        html2BitmapWebView.cleanup()
                    }
                }
            }
        }

        private fun createLocaleStableContext(baseContext: Context): Context {
            val stableConfiguration = Configuration(baseContext.resources.configuration)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stableConfiguration.setLocales(baseContext.resources.configuration.locales)
            } else {
                @Suppress("DEPRECATION")
                stableConfiguration.locale = baseContext.resources.configuration.locale
            }
            return baseContext.createConfigurationContext(stableConfiguration)
        }
    }
}
