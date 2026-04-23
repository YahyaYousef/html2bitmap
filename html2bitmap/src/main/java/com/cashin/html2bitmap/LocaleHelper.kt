package com.cashin.html2bitmap

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

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
