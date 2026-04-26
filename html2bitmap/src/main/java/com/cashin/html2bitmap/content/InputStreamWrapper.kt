package com.cashin.html2bitmap.content

import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream

internal class InputStreamWrapper(
    private val callback: Callback,
    inputStream: InputStream
) : BufferedInputStream(inputStream) {
    @Throws(IOException::class)
    override fun close() {
        super.close()
        callback.onClose()
    }

    internal fun interface Callback {
        fun onClose()
    }
}
