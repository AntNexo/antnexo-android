package com.antnexo.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigTest {
    private val splynx = BoardHost.parse("https://splynx.antnexo.com")

    @Test
    fun parsesABareOrigin() {
        assertEquals("https://splynx.antnexo.com", splynx.origin)
        assertNull(splynx.port)
        assertEquals(splynx, BoardHost.parse("https://splynx.antnexo.com/"))
        assertNull(BoardHost.parse("https://splynx.antnexo.com:443").port)
        val local = BoardHost.parse("http://acme.localhost:8090")
        assertEquals("http://acme.localhost:8090", local.origin)
        assertEquals(8090, local.port)
    }

    @Test
    fun hostsAreAsciiAndLowercase() {
        assertEquals("xn--bcher-kva.example", BoardHost.parse("https://BÜCHER.example").host)
        assertEquals("splynx.antnexo.com", BoardHost.parse("https://SPLYNX.antnexo.com").host)
        // java.net.URI reads these only as a registry authority: the port must survive
        assertEquals("https://xn--bcher-kva.example:8443", BoardHost.parse("https://bücher.example:8443").origin)
        assertEquals("https://feedback_acme.example:8443", BoardHost.parse("https://feedback_acme.example:8443").origin)
        assertEquals("https://feedback_acme.example", BoardHost.parse("https://feedback_acme.example:443").origin)
        assertTrue(splynx.isAntNexoSubdomain)
        assertFalse(BoardHost.parse("https://feedback.acme.com").isAntNexoSubdomain)
    }

    @Test
    fun refusesAnythingButABareOrigin() {
        for (bad in listOf(
            "ftp://x", "https://x.com/path", "https://x.com?q=1", "https://x.com#f", "https://u:p@x.com", "",
            "splynx.antnexo.com", "https://", "https://a.com:abc", "https://a.com:99999", "https://a.com:0",
            "https://bücher.example:x", "https://bücher.example:70000",
        )) {
            assertThrows(bad, AntNexoException.InvalidHost::class.java) { BoardHost.parse(bad) }
        }
    }

    @Test
    fun plainHttpOnlyForThisMachineOrTheEmulatorsHost() {
        for (ok in listOf("http://acme.localhost:8090", "http://localhost:8090", "http://127.0.0.1:8090", "http://10.0.2.2:8090")) {
            BoardHost.parse(ok)
        }
        // the identify token and the screenshot must never go to a plaintext page on the network
        for (bad in listOf("http://splynx.antnexo.com", "http://feedback.acme.com", "http://192.168.1.10:8090", "http://localhost.evil.io")) {
            assertThrows(bad, AntNexoException.InvalidHost::class.java) { BoardHost.parse(bad) }
        }
    }

    @Test
    fun matchesOnlyTheExactOrigin() {
        assertTrue(splynx.matches("https", "splynx.antnexo.com", -1))
        assertTrue(splynx.matches("https", "splynx.antnexo.com", 0))
        assertTrue(splynx.matches("https", "splynx.antnexo.com", 443))
        assertTrue(splynx.matches("HTTPS", "SPLYNX.antnexo.com", -1))
        assertFalse(splynx.matches("http", "splynx.antnexo.com", -1))
        assertFalse(splynx.matches("https", "splynx.antnexo.com", 8443))
        assertFalse(splynx.matches("https", "splynx.antnexo.com.evil.io", -1))
    }

    @Test
    fun appPageUrlWithoutOptions() {
        assertEquals("https://splynx.antnexo.com/app?workspace=splynx", appPageUrl(splynx, "splynx", FeedbackOptions()))
    }

    @Test
    fun appPageUrlWithOptionsInOrder() {
        val o = FeedbackOptions(
            tab = FeedbackTab.ROADMAP, tabs = listOf(FeedbackTab.NEW, FeedbackTab.ROADMAP), theme = FeedbackTheme.DARK,
            prompts = false, whatsNew = WhatsNewMode.OFF, language = "de",
        )
        assertEquals(
            "https://splynx.antnexo.com/app?workspace=splynx&tab=roadmap&tabs=new,roadmap&theme=dark&prompts=off&whats-new=off&lang=de",
            appPageUrl(splynx, "splynx", o),
        )
        assertTrue(appPageUrl(splynx, "splynx", FeedbackOptions(prompts = true)).endsWith("&prompts=on"))
    }

    @Test
    fun appPageUrlEncodesValues() {
        assertEquals("https://splynx.antnexo.com/app?workspace=a%26b", appPageUrl(splynx, "a&b", FeedbackOptions()))
        assertTrue(appPageUrl(splynx, "s", FeedbackOptions(boards = listOf("x,y", "z"))).endsWith("&boards=x%2Cy,z"))
        assertTrue(appPageUrl(splynx, "s", FeedbackOptions(color = "#4f46e5")).endsWith("&color=%234f46e5"))
        assertTrue(appPageUrl(splynx, "s", FeedbackOptions(boards = listOf("it's"))).endsWith("&boards=it%27s"))
        assertTrue(appPageUrl(splynx, "s", FeedbackOptions(boards = listOf("a b+c"))).endsWith("&boards=a%20b%2Bc"))
    }
}
