# HTML2Bitmap Kotlin — Agent Brief

> This document is a full specification for building a Kotlin-based Android library
> that converts HTML strings into Bitmaps, intended as a modern replacement for
> the archived iZettle `android-html2bitmap` library.

---

## 1. Reference: Original Library

- **Repo:** https://github.com/iZettle/android-html2bitmap
- **Status:** Archived (read-only) as of April 2025, no longer maintained
- **Language:** Java
- **Last version:** 1.10
- **License:** MIT

Study the original source code at the repo above to understand the rendering
pipeline before implementing the new version.

---

## 2. What the Original Library Does

The library renders an HTML string into an Android `Bitmap` by:

1. Creating an off-screen `WebView` (never attached to any visible layout)
2. Setting the WebView width to the requested pixel width
3. Loading the HTML content into the WebView
4. Waiting for `WebViewClient.onPageFinished()` to fire
5. Reading `WebView.getContentHeight()` to determine the full rendered height
6. Creating a `Bitmap` at `(width x height)` dimensions
7. Wrapping it in a `Canvas` and calling `WebView.draw(canvas)` to capture the content
8. Returning the final `Bitmap`

### Threading model in original

The original uses a `HandlerThread` + `Handler` to manage background work, and
posts specific WebView operations (which must run on the main thread) to
`Looper.getMainLooper()` via a `Handler`. It uses a `FutureTask` to block the
calling thread until rendering is complete.

Key files in the original source:
- `Html2Bitmap.java` — public builder API, manages the `FutureTask` and timeout
- `Html2BitmapWebView.java` — core rendering logic, HandlerThread, WebView setup
- `content/WebViewContent.java` — content abstraction (HTML string or URL)

---

## 3. The Bug: App Language Resets to Device Language

### Root Cause

This is a well-documented Android platform bug affecting **Android 7.0 (API 24)
and above**.

Starting with Android 7, the system WebView is backed by the **Chrome app**
running in a separate process. When a `WebView` is **first instantiated** in an
app session, Chrome is loaded into the app process. This causes Chrome's locale
list to overwrite the current `Activity`/`Application` locale configuration.

Because Chrome supports hundreds of locales, it almost always includes the
device's primary locale. The result: after the first `WebView` is created, the
app's resource configuration switches from the **app-set locale** to the
**device default locale**, and all subsequent string resource lookups return the
wrong language. This persists even after the WebView is destroyed.

**Official Google issue:** https://issuetracker.google.com/issues/37113860
(closed as "WorkingAsIntended" with no supported fix)

### Why the iZettle Library Makes It Worse

The original library creates the `WebView` using the raw `context` passed by the
caller (or the application context). There is **no locale capture or
re-application** step anywhere in the codebase. So:

1. Caller passes a `Context` (activity or application)
2. Library creates `new WebView(context)` on the main thread via Handler
3. Chrome process loads, overwrites the app's locale configuration
4. Library returns the `Bitmap` — job done
5. But now the entire app is running with the wrong locale/language

### Affected Files in Original Library

| File | Location of Issue |
|---|---|
| `Html2BitmapWebView.java` | The `WebView` is instantiated with the raw context passed in — no locale is captured or locked before this call |
| `Html2Bitmap.java` | The `context` field stored in the builder is passed directly to `Html2BitmapWebView` with no wrapping |

The specific problematic pattern (paraphrased from source):

```java
// Html2BitmapWebView.java — constructor / init
// context is passed raw, no locale configuration is locked first
webView = new WebView(context);
```

There is no `createConfigurationContext()` call, no `Locale` capture before
WebView creation, and no locale restoration after the WebView is done.

---

## 4. The Fix

### Step 1 — Capture the Locale Before Touching WebView

Before any `WebView` is instantiated, read and store the current locale from
the app's resource configuration:

```kotlin
val currentLocale = context.resources.configuration.locales[0]
```

### Step 2 — Create a Locale-Locked Context

Wrap the context with a `Configuration` that locks the locale, then use this
wrapped context for all WebView operations:

```kotlin
val lockedConfig = Configuration(context.resources.configuration).apply {
    setLocale(currentLocale)
}
val safeContext = context.createConfigurationContext(lockedConfig)
val webView = WebView(safeContext)
```

### Step 3 — Restore After WebView is Done

After the bitmap is captured and the WebView is destroyed, re-apply the locale
to the application context to undo any contamination:

```kotlin
val restoreConfig = Configuration(context.applicationContext.resources.configuration).apply {
    setLocale(currentLocale)
}
context.applicationContext.resources.updateConfiguration(
    restoreConfig,
    context.applicationContext.resources.displayMetrics
)
```

---

## 5. New Library Specification

### Language & Stack

- **Language:** Kotlin only (no Java)
- **Threading:** Kotlin Coroutines (no HandlerThread, no AsyncTask, no FutureTask)
- **Min SDK:** 21
- **Target SDK:** 34
- **Dependencies:** `kotlinx-coroutines-android`, nothing else

### Module Structure

```
html2bitmap/                        ← Android Library module
├── build.gradle.kts
└── src/main/
    └── java/com/[yourname]/html2bitmap/
        ├── HtmlToBitmap.kt         ← public API (object with suspend fun)
        ├── HtmlToBitmapRenderer.kt ← internal WebView rendering logic
        ├── LocaleHelper.kt         ← locale capture, lock, and restore
        └── BitmapConfig.kt         ← configuration data class

app/                                ← Demo app module (optional but recommended)
├── build.gradle.kts
└── src/main/
    └── ...

settings.gradle.kts
build.gradle.kts                    ← root
```

### Public API

```kotlin
// Main entry point — caller uses from their own coroutine scope
object HtmlToBitmap {

    /**
     * Converts an HTML string to a Bitmap.
     *
     * Must be called from a coroutine. Internally switches to Main thread
     * for WebView operations and returns the result on the caller's dispatcher.
     *
     * @param context   Application or Activity context
     * @param html      Full HTML string to render
     * @param widthPx   Width of the output bitmap in pixels
     * @param config    Optional rendering configuration
     * @return          Rendered Bitmap
     */
    suspend fun from(
        context: Context,
        html: String,
        widthPx: Int,
        config: BitmapConfig = BitmapConfig()
    ): Bitmap
}

data class BitmapConfig(
    val timeoutMs: Long = 30_000L,
    val backgroundColor: Int = Color.WHITE,
    val javascriptEnabled: Boolean = false,
    val textZoom: Int = 100
)
```

### Internal Rendering Implementation

```kotlin
// HtmlToBitmapRenderer.kt (internal)
internal object HtmlToBitmapRenderer {

    suspend fun render(
        context: Context,
        html: String,
        widthPx: Int,
        config: BitmapConfig
    ): Bitmap = withContext(Dispatchers.Main) {

        // STEP 1: Capture locale before ANY WebView interaction
        val locale = LocaleHelper.captureLocale(context)
        val safeContext = LocaleHelper.createLockedContext(context, locale)

        suspendCancellableCoroutine { continuation ->

            val webView = WebView(safeContext).apply {
                settings.apply {
                    javaScriptEnabled = config.javascriptEnabled
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    textZoom = config.textZoom
                    // disable image loading for faster render (thermal printer use case)
                    blockNetworkImage = false
                }
                setBackgroundColor(config.backgroundColor)
            }

            webView.webViewClient = object : WebViewClient() {

                override fun onPageFinished(view: WebView, url: String) {
                    try {
                        val contentHeight = (view.contentHeight * view.scale).toInt()
                            .coerceAtLeast(1)

                        view.layout(0, 0, widthPx, contentHeight)

                        val bitmap = Bitmap.createBitmap(
                            widthPx,
                            contentHeight,
                            Bitmap.Config.ARGB_8888
                        )
                        val canvas = Canvas(bitmap)
                        view.draw(canvas)

                        view.destroy()

                        // STEP 3: Restore locale after WebView is done
                        LocaleHelper.restoreLocale(context, locale)

                        continuation.resume(bitmap)

                    } catch (e: Exception) {
                        view.destroy()
                        LocaleHelper.restoreLocale(context, locale)
                        continuation.resumeWithException(e)
                    }
                }

                override fun onReceivedError(
                    view: WebView,
                    errorCode: Int,
                    description: String,
                    failingUrl: String
                ) {
                    // non-fatal for local HTML — only fail on main resource
                }
            }

            // Set width before loading
            webView.layout(0, 0, widthPx, 1)

            // Use loadDataWithBaseURL — NOT loadData (avoids Base64/charset issues)
            webView.loadDataWithBaseURL(
                null,
                html,
                "text/html",
                "UTF-8",
                null
            )

            // Clean up if coroutine is cancelled
            continuation.invokeOnCancellation {
                webView.stopLoading()
                webView.destroy()
                LocaleHelper.restoreLocale(context, locale)
            }
        }
    }
}
```

### LocaleHelper

```kotlin
// LocaleHelper.kt (internal)
internal object LocaleHelper {

    fun captureLocale(context: Context): Locale {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.resources.configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale
        }
    }

    fun createLockedContext(context: Context, locale: Locale): Context {
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    fun restoreLocale(context: Context, locale: Locale) {
        val config = Configuration(context.applicationContext.resources.configuration)
        config.setLocale(locale)
        @Suppress("DEPRECATION")
        context.applicationContext.resources.updateConfiguration(
            config,
            context.applicationContext.resources.displayMetrics
        )
    }
}
```

### Main Entry Point with Timeout

```kotlin
// HtmlToBitmap.kt (public)
object HtmlToBitmap {

    suspend fun from(
        context: Context,
        html: String,
        widthPx: Int,
        config: BitmapConfig = BitmapConfig()
    ): Bitmap = withTimeout(config.timeoutMs) {
        HtmlToBitmapRenderer.render(context, html, widthPx, config)
    }
}
```

### Bonus: Grayscale Extension (useful for thermal printers)

```kotlin
// BitmapExtensions.kt (public)
fun Bitmap.toGrayscale(): Bitmap {
    val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    val paint = Paint().apply {
        colorFilter = ColorMatrixColorFilter(ColorMatrix().also { it.setSaturation(0f) })
    }
    canvas.drawBitmap(this, 0f, 0f, paint)
    return result
}
```

---

## 6. Library `build.gradle.kts`

```kotlin
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
}

android {
    namespace = "com.yourname.html2bitmap"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.yourname"       // replace with your identifier
                artifactId = "html2bitmap"
                version = "1.0.0"
            }
        }
    }
}
```

---

## 7. Root `settings.gradle.kts`

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "html2bitmap-kotlin"
include(":html2bitmap")
include(":app") // demo app, optional
```

---

## 8. Publishing via JitPack (Recommended First Step)

1. Push project to a **public GitHub repository**
2. Go to **GitHub → Releases → Create a new release**
3. Tag it `1.0.0` → click **Publish release**
4. Visit `https://jitpack.io` → paste the repo URL → click **Look up**
5. JitPack builds it automatically on first lookup

### How to consume in any project

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven("https://jitpack.io")
    }
}

// app/build.gradle.kts
dependencies {
    implementation("com.github.YourGithubUsername:html2bitmap-kotlin:1.0.0")
}
```

---

## 9. How the Caller Uses It

```kotlin
// In a ViewModel or coroutine scope
viewModelScope.launch {
    try {
        val bitmap = HtmlToBitmap.from(
            context = applicationContext,
            html = "<html><body><h1>Invoice #001</h1></body></html>",
            widthPx = 576, // standard 80mm thermal printer width at 203dpi
            config = BitmapConfig(
                timeoutMs = 15_000L,
                backgroundColor = Color.WHITE,
                javascriptEnabled = false
            )
        )

        // Optional: convert to grayscale for thermal printer
        val printBitmap = bitmap.toGrayscale()

        // Send printBitmap to thermal printer
    } catch (TimeoutCancellationException e) {
        // handle timeout
    }
}
```

---

## 10. Key Differences vs Original iZettle Library

| Aspect | iZettle original | New Kotlin library |
|---|---|---|
| Language | Java | Kotlin |
| Threading | HandlerThread + FutureTask | Kotlin Coroutines |
| API style | Blocking / synchronous | Suspend function |
| Locale bug | Not fixed | Fixed via LocaleHelper |
| Timeout | Manual via FutureTask | `withTimeout {}` |
| Cancellation | Not supported | `invokeOnCancellation` + WebView.destroy() |
| HTML loading | `loadData()` | `loadDataWithBaseURL()` (no charset bugs) |
| Maintenance | Archived | Active |
| Publishing | Maven Central | JitPack / GitHub Packages |
| Grayscale support | No | Yes (extension function) |

---

## 11. Important Notes for the Agent

- Do **not** use `WebView.loadData()` — it has known Base64 and charset encoding
  bugs with special characters. Always use `WebView.loadDataWithBaseURL()`.
- WebView must be created and all its methods called on the **main thread**.
  Use `withContext(Dispatchers.Main)` for the entire WebView lifecycle.
- The `WebView` must have `layout()` called with the correct dimensions
  **before** `draw()` is called, otherwise the bitmap will be blank or 1px tall.
- Do **not** attach the WebView to any window or ViewGroup. It must remain
  off-screen for the entire operation.
- Always call `webView.destroy()` after capturing the bitmap to free memory.
- The locale fix must happen **before** `new WebView(context)` — capturing it
  after is too late.
- Replace `com.yourname` with the actual package/group identifier chosen by
  the developer.
