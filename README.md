# HTML2Bitmap Kotlin

[![](https://jitpack.io/v/YahyaYousef/html2bitmap.svg)](https://jitpack.io/#YahyaYousef/html2bitmap)

A Kotlin replacement for the archived iZettle `android-html2bitmap` library.
It keeps an iZettle-like builder API while also providing coroutine-first usage.

## Features

- **iZettle-compatible API shape**: `Builder` + blocking `getBitmap()`.
- **Coroutine API**: `suspend` rendering without blocking caller threads.
- **Locale-safe WebView context**: avoids app language side effects caused by WebView initialization.
- **Stable sizing flow**: width-first off-screen measure/layout, then bitmap capture.
- **Thermal-printer friendly**: deterministic pixel width configuration.

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

### Compatibility API (iZettle style)

```kotlin
val bitmap: Bitmap? = Html2Bitmap.Builder()
    .setContext(context)
    .setHtml("<html><body><h1>Hello World</h1></body></html>")
    .setBitmapWidth(576)
    .setTimeout(15_000L)
    .build()
    .getBitmap()
```

### Coroutine API

```kotlin
lifecycleScope.launch {
    val bitmap = Html2Bitmap.Builder()
        .setContext(context)
        .setHtml(invoiceHtml)
        .setBitmapWidth(576)
        .build()
        .getBitmapAsync()
}
```

### Existing `HtmlToBitmap.from(...)` API

```kotlin
val bitmap = HtmlToBitmap.from(
    context = context,
    html = invoiceHtml,
    widthPx = 576,
    config = BitmapConfig(
        timeoutMs = 15_000L,
        javascriptEnabled = false,
        textZoom = 100
    )
)
```

## Thermal Printer Width Tips

- 58mm paper (203 DPI): start with `384px`.
- 80mm paper (203 DPI): start with `576px`.
- If you use printer SDK scaling, keep bitmap width fixed and let the printer handle final scaling.

## License

```
MIT License

Copyright (c) 2024 Yahya Yousef
...
```
