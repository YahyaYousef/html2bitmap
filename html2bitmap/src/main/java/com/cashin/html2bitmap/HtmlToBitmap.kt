package com.cashin.html2bitmap

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.withTimeout

object HtmlToBitmap {

    /**
     * Converts an HTML string to a Bitmap.
     *
     * Must be called from a coroutine. Internally switches to Main thread
     * for WebView operations and returns the result on the caller's dispatcher.
     *
     * @param context   Application or Activity context
     * @param html      Full HTML string to render
     * @param widthPx   Width of the output bitmap in pixels
     * @param config    Optional rendering configuration
     * @return          Rendered Bitmap
     */
    suspend fun from(
        context: Context,
        html: String,
        widthPx: Int,
        config: BitmapConfig = BitmapConfig()
    ): Bitmap = withTimeout(config.timeoutMs) {
        HtmlToBitmapRenderer.render(context, html, widthPx, config)
    }
}
