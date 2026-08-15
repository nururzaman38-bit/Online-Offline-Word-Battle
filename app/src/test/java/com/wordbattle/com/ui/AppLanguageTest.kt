package com.wordbattle.com.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {

    @Test
    fun `supported tags resolve`() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("en"))
        assertEquals(AppLanguage.BANGLA, AppLanguage.fromTag("bn"))
    }

    @Test
    fun `region and casing are ignored`() {
        assertEquals(AppLanguage.BANGLA, AppLanguage.fromTag("bn-BD"))
        assertEquals(AppLanguage.BANGLA, AppLanguage.fromTag("BN"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("en-US"))
    }

    @Test
    fun `unknown or missing tags fall back to the default`() {
        assertEquals(AppLanguage.DEFAULT, AppLanguage.fromTag(null))
        assertEquals(AppLanguage.DEFAULT, AppLanguage.fromTag(""))
        assertEquals(AppLanguage.DEFAULT, AppLanguage.fromTag("fr-FR"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.DEFAULT)
    }

    @Test
    fun `every language has a tag that matches a translation folder`() {
        assertEquals(listOf("en", "bn"), AppLanguage.entries.map { it.tag })
    }
}
