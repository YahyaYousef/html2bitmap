package com.cashin.html2bitmap

import android.graphics.Color

/**
 * Configuration options for HTML to Bitmap rendering.
 *
 * @property timeoutMs Maximum time to wait for rendering to complete before throwing a TimeoutCancellationException.
 * @property backgroundColor Background color of the WebView. Defaults to Color.WHITE.
 * @property javascriptEnabled Whether to enable JavaScript execution in the WebView.
 * @property textZoom Optional text zoom percentage. If null, WebView default is used.
 * @property measureDelayMs Delay after page progress indicates completion before measuring content.
 * @property screenshotDelayMs Delay after measure/layout settles before drawing to bitmap.
 * @property strictMode If true, throws when blocking API is used from the main thread.
 */
data class BitmapConfig(
    val timeoutMs: Long = 30_000L,
    val backgroundColor: Int = Color.WHITE,
    val javascriptEnabled: Boolean = false,
    val textZoom: Int? = null,
    val measureDelayMs: Long = 300L,
    val screenshotDelayMs: Long = 300L,
    val strictMode: Boolean = false
)
