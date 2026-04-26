package com.cashin.html2bitmap.content

import android.content.Context
import android.net.Uri
import android.os.Build
import android.webkit.WebResourceResponse
import android.webkit.WebView
import java.lang.ref.WeakReference
import java.net.URL
import java.net.URLConnection
import java.util.concurrent.CopyOnWriteArrayList

abstract class WebViewContent {
    private val webViewResources = CopyOnWriteArrayList<WebViewResource>()
    private var doneListenerWeakReference: WeakReference<ProgressChangedListener>? = null

    abstract fun loadContent(webview: WebView)

    fun isDone(): Boolean = webViewResources.all { it.isLoaded }

    internal abstract fun loadResourceImpl(
        context: Context,
        webViewResource: WebViewResource
    ): WebResourceResponse?

    fun loadResource(context: Context, uri: Uri): WebResourceResponse? {
        val webViewResource = WebViewResource(uri)
        webViewResources.add(webViewResource)
        return loadResourceImpl(context, webViewResource)
    }

    protected fun getRemoteFile(webViewResource: WebViewResource): WebResourceResponse? {
        val uri = webViewResource.uri
        val protocol = uri.scheme
        if (protocol == "http" || protocol == "https") {
            try {
                val url = URL(uri.toString())
                val urlConnection: URLConnection = url.openConnection()
                val inputStream = InputStreamWrapper(
                    { ->
                        webViewResource.setLoaded()
                        resourceLoaded()
                    },
                    urlConnection.getInputStream()
                )

                val webResourceResponse = WebResourceResponse(
                    urlConnection.contentType,
                    urlConnection.contentEncoding,
                    inputStream
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val responseHeaders = mutableMapOf<String, String>()
                    for ((key, values) in urlConnection.headerFields) {
                        if (key != null && !values.isNullOrEmpty()) {
                            responseHeaders[key] = values[0]
                        }
                    }
                    webResourceResponse.responseHeaders = responseHeaders
                }
                return webResourceResponse
            } catch (exception: Exception) {
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

    fun getRemoteResources(): List<WebViewResource> = webViewResources

    internal fun resourceLoaded() {
        val progressChangedListener = doneListenerWeakReference?.get()
        if (isDone() && progressChangedListener != null) {
            progressChangedListener.progressChanged()
        }
    }

    fun setDoneListener(progressChangedListener: ProgressChangedListener) {
        doneListenerWeakReference = WeakReference(progressChangedListener)
    }

    companion object {
        fun html(html: String): WebViewContent = WebViewAssetContent(html)

        fun url(url: URL): WebViewContent = WebViewRemoteContent(url)
    }
}
