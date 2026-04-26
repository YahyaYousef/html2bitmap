package com.cashin.html2bitmap

import android.webkit.WebView

internal interface Html2BitmapConfigurationCallback {
    fun configureWebView(webview: WebView)
}
