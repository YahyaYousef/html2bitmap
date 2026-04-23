package com.cashin.html2bitmap

import android.graphics.Color

/**
 * Configuration options for HTML to Bitmap rendering.
 *
 * @property timeoutMs Maximum time to wait for rendering to complete before throwing a TimeoutCancellationException.
 * @property backgroundColor Background color of the WebView. Defaults to Color.WHITE.
 * @property javascriptEnabled Whether to enable JavaScript execution in the WebView.
 * @property textZoom Text zoom percentage, defaults to 100%.
 */
data class BitmapConfig(
    val timeoutMs: Long = 30_000L,
    val backgroundColor: Int = Color.WHITE,
    val javascriptEnabled: Boolean = false,
    val textZoom: Int = 100
)
