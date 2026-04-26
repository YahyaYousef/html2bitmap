package com.cashin.html2bitmap

import android.graphics.Bitmap
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch

internal class BitmapCallable : Callable<Bitmap?>, BitmapCallback {
    private val latch = CountDownLatch(1)
    private var bitmap: Bitmap? = null

    override fun call(): Bitmap? {
        latch.await()
        return bitmap
    }

    override fun finished(bitmap: Bitmap) {
        this.bitmap = bitmap
        latch.countDown()
    }

    override fun error(error: Throwable) {
        latch.countDown()
    }
}
