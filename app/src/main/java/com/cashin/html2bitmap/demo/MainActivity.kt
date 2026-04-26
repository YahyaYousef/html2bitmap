package com.cashin.html2bitmap.demo

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cashin.html2bitmap.Html2Bitmap
import com.cashin.html2bitmap.Html2BitmapConfigurator
import com.cashin.html2bitmap.content.WebViewContent
import com.cashin.html2bitmap.demo.ui.theme.Html2bitmapTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Html2bitmapTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    DemoScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun DemoScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isRendering by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val sampleHtml = """
        <html>
        <head>
            <style>
                body { font-family: sans-serif; padding: 20px; background-color: #f0f0f0; }
                h1 { color: #333; }
                .receipt { background: white; padding: 20px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
                .item { display: flex; justify-content: space-between; margin-bottom: 10px; border-bottom: 1px dashed #ccc; }
                .total { font-weight: bold; font-size: 1.2em; margin-top: 20px; text-align: right; }
            </style>
        </head>
        <body>
            <div class="receipt">
                <h1>Sample Receipt</h1>
                <div class="item"><span>Item 1</span><span>${'$'}10.00</span></div>
                <div class="item"><span>Item 2</span><span>${'$'}25.50</span></div>
                <div class="item"><span>Tax</span><span>${'$'}3.50</span></div>
                <div class="total">Total: ${'$'}39.00</div>
                <p style="text-align: center; margin-top: 20px; color: #666;">Thank you for your business!</p>
            </div>
        </body>
        </html>
    """.trimIndent()

    val html2BitmapConfigurator: Html2BitmapConfigurator = object : Html2BitmapConfigurator() {
        override fun configureWebView(webview: WebView) {
            webview.setBackgroundColor(Color.WHITE)
            webview.settings.textZoom = 150
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Button(
            onClick = {
                if (isRendering) return@Button
                isRendering = true
                errorMessage = null
                
                coroutineScope.launch {
                    try {
                        val resultBitmap = withContext(Dispatchers.Default) {
                            Html2Bitmap.Builder()
                                .setContext(context)
                                .setContent(WebViewContent.html(sampleHtml))
                                .setBitmapWidth(384)
                                .setMeasureDelay(100)
                                .setScreenshotDelay(100)
                                .setStrictMode(true)
                                .setTimeout(50)
                                .setTextZoom(150)
                                .setConfigurator(html2BitmapConfigurator)
                                .build()
                                .getBitmap()
                        }
                        bitmap = resultBitmap
                        if (resultBitmap == null) {
                            errorMessage = "Rendering failed. Check logs for details."
                        }
                    } catch (e: Exception) {
                        errorMessage = "Error: ${e.message}"
                    } finally {
                        isRendering = false
                    }
                }
            },
            enabled = !isRendering
        ) {
            Text(if (isRendering) "Rendering..." else "Render HTML to Bitmap")
        }

        if (errorMessage != null) {
            Text(text = errorMessage!!, color = MaterialTheme.colorScheme.error)
        }

        bitmap?.let { b ->
            Text("Result:", style = MaterialTheme.typography.titleMedium)
            Image(
                bitmap = b.asImageBitmap(),
                contentDescription = "Rendered HTML",
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }
}
