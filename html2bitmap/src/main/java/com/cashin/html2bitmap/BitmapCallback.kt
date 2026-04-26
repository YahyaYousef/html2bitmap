package com.cashin.html2bitmap

import android.graphics.Bitmap

interface BitmapCallback {
    fun finished(bitmap: Bitmap)

    fun error(error: Throwable)
}
