package com.wordbattle.com.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * The languages the UI ships translations for.
 *
 * Switching is instant: [apply] hands the tag to [AppCompatDelegate.setApplicationLocales], which
 * recreates the activity with the new configuration. Persistence is handled by the platform on
 * API 33+ and by `AppLocalesMetadataHolderService` (declared in the manifest) below that.
 */
enum class AppLanguage(val tag: String) {
    ENGLISH("en"),
    BANGLA("bn");

    companion object {
        val DEFAULT = ENGLISH

        fun fromTag(tag: String?): AppLanguage {
            val normalized = tag?.substringBefore('-')?.lowercase().orEmpty()
            return entries.firstOrNull { it.tag == normalized } ?: DEFAULT
        }

        /** Language currently applied to the app, falling back to English. */
        fun current(): AppLanguage =
            fromTag(AppCompatDelegate.getApplicationLocales().toLanguageTags().takeIf { it.isNotBlank() })

        fun apply(language: AppLanguage) {
            val target = LocaleListCompat.forLanguageTags(language.tag)
            if (AppCompatDelegate.getApplicationLocales() != target) {
                AppCompatDelegate.setApplicationLocales(target)
            }
        }
    }
}
