package com.cashin.html2bitmap.content

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceResponse
import android.webkit.WebView
import java.net.URL

internal class WebViewRemoteContent(private val url: URL) : WebViewContent() {
    override fun loadContent(webview: WebView) {
        webview.loadUrl(url.toString())
    }

    override fun loadResourceImpl(context: Context, webViewResource: WebViewResource): WebResourceResponse? {
        if (webViewResource.uri == Uri.parse(url.toString())) {
            webViewResource.setNativeLoad()
            resourceLoaded()
            return null
        }
        return getRemoteFile(webViewResource)
    }
}
