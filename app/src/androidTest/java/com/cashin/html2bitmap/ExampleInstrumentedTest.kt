package com.cashin.html2bitmap

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {

    @Test
    fun renders_bitmap_with_requested_width() = runBlocking {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val widthPx = 576
        val bitmap = Html2Bitmap.Builder()
            .setContext(appContext)
            .setHtml(shortInvoiceHtml())
            .setBitmapWidth(widthPx)
            .build()
            .getBitmapAsync()

        assertEquals(widthPx, bitmap.width)
        assertTrue(bitmap.height > 50)
    }

    @Test
    fun long_invoice_is_taller_than_short_invoice() = runBlocking {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val widthPx = 576

        val shortBitmap = Html2Bitmap.Builder()
            .setContext(appContext)
            .setHtml(shortInvoiceHtml())
            .setBitmapWidth(widthPx)
            .build()
            .getBitmapAsync()

        val longBitmap = Html2Bitmap.Builder()
            .setContext(appContext)
            .setHtml(longInvoiceHtml())
            .setBitmapWidth(widthPx)
            .build()
            .getBitmapAsync()

        assertEquals(widthPx, longBitmap.width)
        assertTrue(longBitmap.height > shortBitmap.height)
        assertTrue(abs(longBitmap.height - shortBitmap.height) > 40)
    }

    private fun shortInvoiceHtml(): String = """
        <html><body style="font-family:sans-serif;padding:8px">
        <h3>Invoice #1</h3>
        <p>Milk x1 - 2.00</p>
        <p>Bread x1 - 1.50</p>
        <p>Total: 3.50</p>
        </body></html>
    """.trimIndent()

    private fun longInvoiceHtml(): String {
        val lines = buildString {
            repeat(40) { index ->
                append("<p>Item ${index + 1} - ${(index + 1) * 0.75}</p>")
            }
        }
        return """
            <html><body style="font-family:sans-serif;padding:8px">
            <h3>Invoice #2</h3>
            $lines
            <p><strong>Total: 615.00</strong></p>
            </body></html>
        """.trimIndent()
    }
}
