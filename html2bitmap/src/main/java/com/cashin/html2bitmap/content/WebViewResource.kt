package com.cashin.html2bitmap.content

import android.net.Uri

class WebViewResource(val uri: Uri) {
    var isLoaded: Boolean = false
        private set
    private var nativeLoad: Boolean = false
    var exception: Exception? = null
        private set

    fun setLoaded() {
        isLoaded = true
    }

    fun setNativeLoad() {
        setLoaded()
        nativeLoad = true
    }

    fun isNativeLoad(): Boolean = nativeLoad

    fun setException(exception: Exception) {
        setLoaded()
        this.exception = exception
    }

    override fun toString(): String {
        return "WebViewResource(uri=$uri, loaded=$isLoaded, nativeLoad=$nativeLoad, exception=$exception)"
    }
}
