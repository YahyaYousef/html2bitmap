package com.cashin.html2bitmap.content

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceResponse
import android.webkit.WebView
import java.io.IOException
import java.io.InputStreamReader

internal class WebViewAssetContent(private val html: String) : WebViewContent() {
    private val baseUrl = "$HTML2BITMAP_PROTOCOL://android_asset/"

    override fun loadContent(webview: WebView) {
        webview.loadDataWithBaseURL(baseUrl, html, "text/html", "utf-8", null)
    }

    override fun loadResourceImpl(context: Context, webViewResource: WebViewResource): WebResourceResponse? {
        val protocol = webViewResource.uri.scheme
        return when (protocol) {
            HTML2BITMAP_PROTOCOL -> getAssetFile(context, webViewResource)
            "http", "https" -> getRemoteFile(webViewResource)
            else -> {
                webViewResource.setLoaded()
                null
            }
        }
    }

    private fun getAssetFile(context: Context, webViewResource: WebViewResource): WebResourceResponse? {
        val uri = webViewResource.uri
        if (uri.scheme == HTML2BITMAP_PROTOCOL) {
            try {
                val mimeType = context.contentResolver.getType(uri)
                val fileName = uri.lastPathSegment ?: return null
                val encoding = InputStreamReader(context.assets.open(fileName)).use { it.encoding }

                val inputStream = InputStreamWrapper(
                    {
                        webViewResource.setLoaded()
                        resourceLoaded()
                    },
                    context.assets.open(fileName)
                )
                return WebResourceResponse(mimeType, encoding, inputStream)
            } catch (exception: IOException) {
                exception.printStackTrace()
                webViewResource.setException(exception)
                resourceLoaded()
            }
        } else {
            webViewResource.setNativeLoad()
            resourceLoaded()
        }
        return null
    }

    companion object {
        private const val HTML2BITMAP_PROTOCOL = "html2bitmap"
    }
}
