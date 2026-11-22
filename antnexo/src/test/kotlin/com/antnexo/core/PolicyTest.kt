package com.antnexo.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class PolicyTest {
    private val host = BoardHost.parse("https://splynx.antnexo.com")
    private fun p(json: String?) = parsePageMessage(json, host)

    @Test
    fun ready() = assertEquals(PageMessage.Ready, p("""{"type":"ready"}"""))

    @Test
    fun openTakesOnlyWebUrls() {
        assertEquals(PageMessage.Open("https://antnexo.com/x"), p("""{"type":"open","url":"https://antnexo.com/x"}"""))
        assertEquals(PageMessage.Open("http://example.com/"), p("""{"type":"open","url":"http://example.com/"}"""))
        for (u in listOf("javascript:alert(1)", "file:///etc/passwd", "data:text/html,x", "intent://x#Intent;end",
            "https://", "not a url")) {
            assertNull(u, p("""{"type":"open","url":"$u"}"""))
        }
        assertNull(p("""{"type":"open"}"""))
        // hosts java.net.URI reads only as a registry authority still count
        assertEquals(PageMessage.Open("https://my_site.example.com/"), p("""{"type":"open","url":"https://my_site.example.com/"}"""))
        assertEquals(PageMessage.Open("https://bücher.example/x"), p("""{"type":"open","url":"https://bücher.example/x"}"""))
        assertNull(p("""{"type":"open","url":3}"""))
    }

    @Test
    fun authTakesOnlyTheBoardsOwnOAuthStart() {
        val ok = "https://splynx.antnexo.com/api/v1/public/oauth/google/start?poll=abc"
        assertEquals(PageMessage.Auth(ok), p("""{"type":"auth","url":"$ok"}"""))
        assertEquals(PageMessage.Auth("https://SPLYNX.antnexo.com/api/v1/public/oauth/git_hub-2/start"),
            p("""{"type":"auth","url":"https://SPLYNX.antnexo.com/api/v1/public/oauth/git_hub-2/start"}"""))
        for (u in listOf(
            "https://evil.io/api/v1/public/oauth/google/start",
            "http://splynx.antnexo.com/api/v1/public/oauth/google/start",
            "https://splynx.antnexo.com:8443/api/v1/public/oauth/google/start",
            "https://splynx.antnexo.com.evil.io/api/v1/public/oauth/google/start",
            "https://splynx.antnexo.com/api/v1/public/oauth/goo%2Fgle/start",
            "https://splynx.antnexo.com/api/v1/public/oauth/../x/start",
            "https://splynx.antnexo.com/api/v1/public/oauth/google/start/extra",
            "https://splynx.antnexo.com/api/v1/public/oauth/Google/start",
            "https://u:p@splynx.antnexo.com/api/v1/public/oauth/google/start",
            "https://splynx.antnexo.com/app",
            "https://feedback_acme.example/api/v1/public/oauth/google/start",
        )) {
            assertNull(u, p("""{"type":"auth","url":"$u"}"""))
        }
    }

    @Test
    fun authWorksForABoardOnAnUnusualHost() {
        val h = BoardHost.parse("https://feedback_acme.example")
        val u = "https://feedback_acme.example/api/v1/public/oauth/google/start?poll=x"
        assertEquals(PageMessage.Auth(u), parsePageMessage("""{"type":"auth","url":"$u"}""", h))
        assertNull(parsePageMessage("""{"type":"auth","url":"https://feedback_acme.example:8443/api/v1/public/oauth/google/start"}""", h))
        val idn = BoardHost.parse("https://bücher.example")
        val v = "https://bücher.example/api/v1/public/oauth/github/start"
        assertEquals(PageMessage.Auth(v), parsePageMessage("""{"type":"auth","url":"$v"}""", idn))
        val w = "https://xn--bcher-kva.example/api/v1/public/oauth/github/start"
        assertEquals(PageMessage.Auth(w), parsePageMessage("""{"type":"auth","url":"$w"}""", idn))
    }

    @Test
    fun seenNeedsADate() {
        assertEquals(PageMessage.Seen(Instant.parse("2026-10-13T14:47:34.251Z").toEpochMilli(), "2026-10-13T14:47:34.251324Z"),
            p("""{"type":"seen","at":"2026-10-13T14:47:34.251324Z"}"""))
        assertEquals(PageMessage.Seen(Instant.parse("2026-10-13T14:47:34Z").toEpochMilli(), "2026-10-13T14:47:34Z"),
            p("""{"type":"seen","at":"2026-10-13T14:47:34Z"}"""))
        assertNull(p("""{"type":"seen","at":"yesterday"}"""))
        assertNull(p("""{"type":"seen","at":12}"""))
        assertNull(p("""{"type":"seen","at":"0000-01-01T00:00:00Z"}"""))
    }

    @Test
    fun unreadIsASmallInteger() {
        assertEquals(PageMessage.Unread(3), p("""{"type":"unread","count":3}"""))
        assertEquals(PageMessage.Unread(0), p("""{"type":"unread","count":0}"""))
        assertEquals(PageMessage.Unread(999), p("""{"type":"unread","count":999}"""))
        for (c in listOf("-1", "1000", "\"3\"", "2.5", "true", "null", "1e3")) {
            assertNull(c, p("""{"type":"unread","count":$c}"""))
        }
    }

    @Test
    fun sessionIsABoolean() {
        assertEquals(PageMessage.Session(true), p("""{"type":"session","signed_in":true}"""))
        assertEquals(PageMessage.Session(false), p("""{"type":"session","signed_in":false}"""))
        assertNull(p("""{"type":"session","signed_in":1}"""))
        assertNull(p("""{"type":"session","signed_in":"true"}"""))
    }

    @Test
    fun identifyFailedCodeIsCut() {
        assertEquals(PageMessage.IdentifyFailed("expired"), p("""{"type":"identify_failed","code":"expired"}"""))
        val long = "x".repeat(100)
        assertEquals(PageMessage.IdentifyFailed("x".repeat(64)), p("""{"type":"identify_failed","code":"$long"}"""))
        assertNull(p("""{"type":"identify_failed"}"""))
    }

    @Test
    fun postedNeedsAUuid() {
        val id = "0193a1b2-c3d4-7e5f-8a9b-0c1d2e3f4a5b"
        assertEquals(PageMessage.Posted(id), p("""{"type":"posted","id":"$id"}"""))
        assertNull(p("""{"type":"posted","id":"nope"}"""))
        assertNull(p("""{"type":"posted","id":"0193a1b2c3d47e5f8a9b0c1d2e3f4a5b0"}"""))
    }

    @Test
    fun junkIsIgnored() {
        for (j in listOf(null, "", "null", "[]", "\"ready\"", "not json", "{", """{"type":"nope"}""", """{"type":3}""",
            """{}""")) {
            assertNull(j, p(j))
        }
        assertNull(p("""{"type":"ready","pad":"${"x".repeat(70_000)}"}"""))
    }

    @Test
    fun gateAllowsOnlyTheFirstLoadOfThePage() {
        val page = "https://splynx.antnexo.com/app?workspace=splynx"
        val g = NavigationGate(page)
        assertFalse(g.allow(page, mainFrame = false))
        assertTrue(g.allow(page, mainFrame = true))
        assertFalse(g.allow(page, mainFrame = true))
        val other = NavigationGate(page)
        assertFalse(other.allow("https://splynx.antnexo.com/", mainFrame = true))
        assertFalse(other.allow("about:blank", mainFrame = true))
        assertTrue(other.allow(page, mainFrame = true))
    }

    @Test
    fun isoDates() {
        fun ms(s: String) = Instant.parse(s).toEpochMilli()
        assertEquals(ms("2026-10-13T14:47:34.251Z"), parseIsoDate("2026-10-13T14:47:34.251Z"))
        assertEquals(ms("2026-10-13T14:47:34.251Z"), parseIsoDate("2026-10-13T14:47:34.251999Z"))
        assertEquals(ms("2026-10-13T14:47:34.200Z"), parseIsoDate("2026-10-13T14:47:34.2Z"))
        assertEquals(ms("2026-10-13T12:47:34Z"), parseIsoDate("2026-10-13T14:47:34+02:00"))
        assertEquals(ms("2026-10-13T16:17:34Z"), parseIsoDate("2026-10-13T14:47:34-01:30"))
        assertEquals(ms("1970-01-01T00:00:00Z"), parseIsoDate("1970-01-01T00:00:00Z"))
        // proleptic Gregorian, like Instant: no Julian switch before 1582
        assertEquals(ms("1000-01-01T00:00:00Z"), parseIsoDate("1000-01-01T00:00:00Z"))
        assertEquals(ms("0001-01-01T00:00:00Z"), parseIsoDate("0001-01-01T00:00:00Z"))
        assertNull(parseIsoDate("1500-02-29T00:00:00Z"))
        assertEquals(ms("2024-02-29T23:59:59Z"), parseIsoDate("2024-02-29T23:59:59z"))
        for (bad in listOf("2026-10-13", "", "2026-13-01T00:00:00Z", "2026-02-30T00:00:00Z", "2026-10-13T24:00:00Z",
            "2026-10-13T14:47:34", "2026-10-13 14:47:34Z", "x2026-10-13T14:47:34Z", "2026-10-13T14:47:34.Z",
            "0000-01-01T00:00:00Z", "2026-10-13T14:47:34+99:99", "2026-10-13T14:47:34+24:00", "2026-10-13T14:47:34+01:60")) {
            assertNull(bad, parseIsoDate(bad))
        }
    }
}
