package com.antnexo.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocaleTest {
    @Test
    fun bcp47() {
        assertEquals("en-CZ", bcp47("en_CZ"))
        assertEquals("zh-Hans-CN", bcp47("zh-Hans_CN"))
        assertEquals("de", bcp47("de"))
        assertEquals("de-DE", bcp47("de-DE"))
        assertEquals("en-US", bcp47("en-US-u-ca-japanese"))
        assertEquals("en-US", bcp47("en_US@calendar=japanese"))
        assertEquals("und", bcp47(""))
        // Locale.toString() shapes with a script or an extension
        assertEquals("sr-Latn-RS", bcp47("sr_RS_#Latn"))
        assertEquals("ja-JP-JP", bcp47("ja_JP_JP_#u-ca-japanese"))
        assertTrue(bcp47("a".repeat(8) + "-" + "b".repeat(8) + "-" + "c".repeat(8) + "-" + "d".repeat(8) + "-e").length <= 35)
    }
}
