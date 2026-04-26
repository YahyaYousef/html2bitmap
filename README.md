# HTML2Bitmap Kotlin

[![](https://jitpack.io/v/YahyaYousef/html2bitmap.svg)](https://jitpack.io/#YahyaYousef/html2bitmap)

A Kotlin rewrite of the archived iZettle `android-html2bitmap` library.
This package renders HTML (string or URL) into a `Bitmap` using an off-screen `WebView`.

## Features

- **Off-screen WebView rendering**: no visible UI required; render directly to `Bitmap`.
- **HTML string and remote URL support**:
  - `WebViewContent.html(htmlString)` for inline HTML and asset-backed resources.
  - `WebViewContent.url(url)` for remote pages.
- **Resource interception pipeline**:
  - Handles `html2bitmap://android_asset/...` resources for local assets.
  - Loads HTTP/HTTPS resources with tracked completion and error state.
- **Deterministic width-first sizing**:
  - Fixed bitmap width.
  - Two-phase measure/layout to compute final content height.
- **Configurable rendering timing**:
  - `setMeasureDelay(...)` to wait before measure.
  - `setScreenshotDelay(...)` to wait before capture.
- **Configurable WebView behavior**:
  - `setTextZoom(...)`.
  - `setConfigurator(...)` for custom WebView configuration.
- **Strict mode support**:
  - `setStrictMode(true)` throws if callbacks occur after cleanup.
- **Locale-safe WebView context**:
  - Uses configuration-stable context to reduce language/locale side effects on Android WebView init.
- **Coroutine-powered internals**:
  - Rendering scheduling uses Kotlin coroutines.
  - Public API remains simple and builder-based.
- **Timeout protection**:
  - `setTimeout(seconds)` limits max render time.
- **Thermal-printer friendly output**:
  - Pixel-perfect width control for receipt/invoice printing.

## Installation

### Step 1. Add the JitPack repository to your build file

Add it in your root `settings.gradle` (Groovy) or `settings.gradle.kts` (Kotlin DSL) at the end of repositories:

#### Kotlin DSL (`settings.gradle.kts`)
```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

#### Groovy (`settings.gradle`)
```groovy
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

### Step 2. Add the dependency

Add the dependency to your app-level `build.gradle` or `build.gradle.kts`:

#### Kotlin DSL (`build.gradle.kts`)
```kotlin
dependencies {
    implementation("com.github.YahyaYousef:html2bitmap:1.0.0")
}
```

#### Groovy (`build.gradle`)
```groovy
dependencies {
    implementation 'com.github.YahyaYousef:html2bitmap:1.0.0'
}
```

## Usage

### Render HTML string

```kotlin
val bitmap: Bitmap? = Html2Bitmap.Builder()
    .setContext(context)
    .setContent(
        WebViewContent.html("<html><body><h1>Hello World</h1></body></html>")
    )
    .setBitmapWidth(576)
    .setTimeout(15) // seconds
    .build()
    .getBitmap()
```

### Render from URL

```kotlin
val bitmap: Bitmap? = Html2Bitmap.Builder()
    .setContext(context)
    .setContent(WebViewContent.url(URL("https://example.com/invoice/123")))
    .setBitmapWidth(576)
    .build()
    .getBitmap()
```

### Full customization example

```kotlin
val customConfigurator = object : Html2BitmapConfigurator() {
    override fun configureWebView(webview: WebView) {
        webview.setBackgroundColor(Color.WHITE)
        webview.settings.textZoom = 120
    }
}

val bitmap: Bitmap? = Html2Bitmap.Builder()
    .setContext(context)
    .setContent(WebViewContent.html(invoiceHtml))
    .setBitmapWidth(576)
    .setMeasureDelay(150)
    .setScreenshotDelay(150)
    .setStrictMode(false)
    .setTimeout(20) // seconds
    .setTextZoom(120)
    .setConfigurator(customConfigurator)
    .build()
    .getBitmap()
```

## Builder API Reference

- `setContext(context: Context)`
  - Required. Use your localized `Activity` context when possible.
- `setContent(content: WebViewContent)`
  - Required. Use `WebViewContent.html(...)` or `WebViewContent.url(...)`.
- `setBitmapWidth(bitmapWidth: Int)`
  - Output bitmap width in pixels.
- `setMeasureDelay(measureDelay: Int)`
  - Delay (ms) before measuring final height.
- `setScreenshotDelay(screenshotDelay: Int)`
  - Delay (ms) before drawing into bitmap after layout.
- `setStrictMode(strictMode: Boolean)`
  - Throws on unexpected callbacks after cleanup when `true`.
- `setTimeout(timeout: Long)`
  - Max total render time in seconds.
- `setTextZoom(textZoom: Int?)`
  - Optional WebView text zoom percentage.
- `setConfigurator(configurator: Html2BitmapConfigurator?)`
  - Optional hook for advanced WebView settings.

## Threading

- `getBitmap()` is a blocking call and **must not be called on the main thread**.
- Run it on `Dispatchers.Default`/`Dispatchers.IO` or another worker thread.

Example:

```kotlin
val bitmap = withContext(Dispatchers.Default) {
    Html2Bitmap.Builder()
        .setContext(context)
        .setContent(WebViewContent.html(invoiceHtml))
        .build()
        .getBitmap()
}
```

## Locale and language behavior

- WebView initialization can be sensitive to locale configuration on Android 7+.
- The library creates a locale-stable context before instantiating `WebView`.
- For best results, pass the currently localized activity/context to `setContext(...)`.

## Thermal printer tips

- 58mm paper (203 DPI): start with `384px`.
- 80mm paper (203 DPI): start with `576px`.
- If you use printer SDK scaling, keep bitmap width fixed and let the printer handle final scaling.

## Error handling

- `getBitmap()` returns `null` when rendering fails or timeout is reached.
- Check logcat for details (`Html2Bitmap` tag) and inspect your HTML/resources.

## License

```
MIT License

Copyright (c) 2024 Yahya Yousef
...
```
