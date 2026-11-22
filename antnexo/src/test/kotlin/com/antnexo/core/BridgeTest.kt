package com.antnexo.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeTest {
    private val ctx = AppContext(app = "Acme", appVersion = "2.3.0", appBuild = "45", os = "Android", osVersion = "16",
        device = "Pixel 9", locale = "de-DE")

    @Test
    fun scriptCarriesTheContract() {
        val s = bridgeScript(NativeState(BridgePlatform.ANDROID, ctx))
        assertTrue(s.startsWith("window.AntNexoApp="))
        for (part in listOf("\"v\":1", "\"platform\":\"android\"", "\"sdk\":\"$SDK_VERSION\"", "\"app_version\":\"2.3.0\"",
            "\"app_build\":\"45\"", "\"os_version\":\"16\"", "\"identify\":null", "\"seen\":null", "\"screenshot\":null")) {
            assertTrue(part, s.contains(part))
        }
        assertTrue(s.contains("Object.freeze(a.context)"))
        assertTrue(s.contains("return Object.freeze(a)"))
    }

    @Test
    fun aWrapperSetsPlatformAndSdk() {
        val s = bridgeScript(NativeState(BridgePlatform.REACT_NATIVE, ctx, sdk = "9.9.9"))
        assertTrue(s, s.contains("\"platform\":\"react-native\""))
        assertTrue(s, s.contains("\"sdk\":\"9.9.9\""))
        assertEquals(SDK_VERSION, NativeState(BridgePlatform.ANDROID, ctx).sdk)
    }

    @Test
    fun scriptCapturesTheListenerObjectAtInjection() {
        val s = bridgeScript(NativeState(BridgePlatform.ANDROID, ctx))
        // looked up once, not on window at call time (the page can't swap it later)
        assertTrue(s.contains("var n=window.antnexoNative;"))
        assertTrue(s.contains("n.postMessage(JSON.stringify(m))"))
        assertFalse(s.contains("window.antnexoNative.postMessage"))
    }

    @Test
    fun scriptLeavesOutNullContextFieldsAndKeepsValues() {
        val s = bridgeScript(NativeState(BridgePlatform.ANDROID, AppContext(app = "A"), identify = "jwt",
            seen = "2026-11-01T00:00:00Z", screenshot = "data:image/jpeg;base64,AAAA"))
        assertTrue(s.contains("\"context\":{\"app\":\"A\"}"))
        assertTrue(s.contains("\"identify\":\"jwt\""))
        assertTrue(s.contains("\"seen\":\"2026-11-01T00:00:00Z\""))
        assertTrue(s.contains("\"screenshot\":\"data:image/jpeg;base64,AAAA\""))
    }

    @Test
    fun scriptEscapesWhatCouldBreakOut() {
        val s = bridgeScript(NativeState(BridgePlatform.ANDROID, AppContext(app = "</script><b>", device = "a\u2028b\u2029c")))
        assertFalse(s.contains("<"))
        assertFalse(s.contains("\u2028"))
        assertFalse(s.contains("\u2029"))
        assertTrue(s.contains("\\u003c/script>\\u003cb>"))
        assertTrue(s.contains("a\\u2028b\\u2029c"))
    }

    @Test
    fun aWipeClearsTheOriginsStorageOncePerLogout() {
        val s = bridgeScript(NativeState(BridgePlatform.ANDROID, ctx, wipe = "n0nce"))
        // once: a reload, a rebuilt WebView or a same-origin frame of the same opening finds the marker and keeps the
        // new person's session
        assertTrue(s.startsWith("try{if(localStorage.getItem(\"antnexo:wiped\")!==\"n0nce\"){localStorage.clear();" +
            "sessionStorage.clear();localStorage.setItem(\"antnexo:wiped\",\"n0nce\")}}catch(e){}window.AntNexoApp="))
        assertFalse(bridgeScript(NativeState(BridgePlatform.ANDROID, ctx)).contains("clear()"))
        // the nonce is ours, but it still goes through JSON
        assertFalse(bridgeScript(NativeState(BridgePlatform.ANDROID, ctx, wipe = "a\"</script>")).contains("</script>"))
    }

    @Test
    fun stateToStringRedactsSecrets() {
        val s = NativeState(BridgePlatform.ANDROID, ctx, identify = "secret-jwt", screenshot = "data:image/jpeg;base64,QUJD")
        val text = s.toString()
        assertFalse(text.contains("secret-jwt"))
        assertFalse(text.contains("QUJD"))
        assertEquals(s.identify, "secret-jwt")
    }
}
