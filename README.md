# HTML2Bitmap Kotlin

[![](https://jitpack.io/v/YahyaYousef/html2bitmap.svg)](https://jitpack.io/#YahyaYousef/html2bitmap)

A modern, Kotlin-first Android library that converts HTML strings into Bitmaps. This library is designed to be a replacement for the archived iZettle library, with a focus on Kotlin Coroutines and fixing the common Android 7.0+ WebView locale bug.

## Features

- **Coroutine-first API**: No more complex Handler/Callback chains.
- **Locale Bug Fix**: Automatically handles the Android system bug that resets your app's language when a WebView is instantiated.
- **Grayscale Support**: Includes an extension to convert results to grayscale, perfect for thermal printers.
- **Modern Rendering**: Uses off-screen WebView rendering with proper layout measurement.

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

### Simple Conversion

```kotlin
lifecycleScope.launch {
    try {
        val bitmap = HtmlToBitmap.from(
            context = context,
            html = "<html><body><h1>Hello World</h1></body></html>",
            widthPx = 800
        )
        // Display the bitmap or send it to a printer
        imageView.setImageBitmap(bitmap)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
```

### Advanced Configuration

```kotlin
val config = BitmapConfig(
    timeoutMs = 15_000L,
    backgroundColor = Color.WHITE,
    javascriptEnabled = true,
    textZoom = 120
)

val bitmap = HtmlToBitmap.from(context, html, 800, config)
```

### Grayscale for Thermal Printers

```kotlin
val printBitmap = bitmap.toGrayscale()
```

## License

```
MIT License

Copyright (c) 2024 Yahya Yousef
...
```
