package com.antnexo

import android.net.Uri
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import com.antnexo.core.PageMessage
import com.antnexo.core.bridgeScript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class FeedbackControllerTest {
    private val t = TestSession()
    private val js = mutableListOf<String>()
    private val c = FeedbackController(t.session).also { it.runJs = { s -> js += s } }
    private var failed = 0
    private var loaded = 0

    init {
        c.onLoadFailed = { failed++ }
        c.onLoaded = { loaded++ }
    }

    private fun web() = WebView(ApplicationProvider.getApplicationContext())

    @Test
    fun takesMessagesOnlyFromTheBoardsMainFrame() {
        val open = """{"type":"open","url":"https://antnexo.com/x"}"""
        c.receive(open, Uri.parse("https://splynx.antnexo.com"), isMainFrame = false)
        c.receive(open, Uri.parse("http://splynx.antnexo.com"), isMainFrame = true)
        c.receive(open, Uri.parse("https://splynx.antnexo.com:8443"), isMainFrame = true)
        c.receive(open, Uri.parse("https://splynx.antnexo.com.evil.io"), isMainFrame = true)
        c.receive("""{"type":"open","url":"javascript:alert(1)"}""", Uri.parse("https://splynx.antnexo.com"), true)
        assertTrue(t.opener.opened.isEmpty())
        c.receive(open, Uri.parse("https://splynx.antnexo.com"), isMainFrame = true)
        assertEquals(listOf("https://antnexo.com/x"), t.opener.opened)
    }

    @Test
    fun handlesEachMessage() {
        val auth = "https://splynx.antnexo.com/api/v1/public/oauth/google/start?poll=x"
        c.handle(PageMessage.Auth(auth))
        assertEquals(listOf(auth), t.signIn.started)
        t.signIn.onClosed!!()
        assertEquals(listOf("window.dispatchEvent(new CustomEvent('antnexo:native',{detail:{type:'auth_closed'}}))"), js)

        c.handle(PageMessage.Session(true))
        assertEquals(1, t.signIn.finished)
        c.handle(PageMessage.Seen(0, "2026-11-01T00:00:00Z"))
        assertEquals("2026-11-01T00:00:00Z", t.seen.get("splynx"))
        c.handle(PageMessage.Unread(4))
        c.handle(PageMessage.Posted("0193a1b2-c3d4-7e5f-8a9b-0c1d2e3f4a5b"))
        c.handle(PageMessage.IdentifyFailed("expired"))
        c.handle(PageMessage.Ready)
        assertEquals(
            listOf(AntNexoEvent.SignedIn(true), AntNexoEvent.Unread(4), AntNexoEvent.Posted("0193a1b2-c3d4-7e5f-8a9b-0c1d2e3f4a5b"),
                AntNexoEvent.IdentifyFailed("expired")),
            t.events,
        )
    }

    @Test
    fun attachSetsUpTheWebViewAndTheBridge() {
        val w = web()
        c.attach(w)
        assertEquals(bridgeScript(testState), t.bridge.script)
        assertEquals("https://splynx.antnexo.com", t.bridge.origin)
        val s = w.settings
        assertTrue(s.javaScriptEnabled)
        assertTrue(s.domStorageEnabled)
        assertFalse(s.allowFileAccess)
        assertFalse(s.allowContentAccess)
        assertFalse(s.supportMultipleWindows())
        assertEquals(WebSettings.MIXED_CONTENT_NEVER_ALLOW, s.mixedContentMode)
        c.load()
        assertEquals(splynxPage, shadowOf(w).lastLoadedUrl)
        // messages from the installed listener go through the same checks
        t.bridge.onMessage!!("""{"type":"unread","count":2}""", Uri.parse("https://splynx.antnexo.com"), true)
        assertEquals(listOf<AntNexoEvent>(AntNexoEvent.Unread(2)), t.events)
    }

    @Test
    fun anOldWebViewFailsInsteadOfLoadingWithoutTheBridge() {
        t.bridge.isSupported = false
        val w = web()
        c.attach(w)
        c.load()
        assertEquals(1, failed)
        assertNull(t.bridge.script)
        assertNull(shadowOf(w).lastLoadedUrl)
        assertTrue(c.outdatedWebView)
    }

    @Test
    fun onlyTheFirstLoadOfThePageIsAllowed() {
        c.attach(web())
        c.load()
        assertFalse(c.shouldOverride(splynxPage, mainFrame = true, isRedirect = false, hasGesture = false))
        c.pageFinished()
        assertEquals(1, loaded)
        // a link the person taps goes to the browser
        assertTrue(c.shouldOverride("https://antnexo.com/x", mainFrame = true, isRedirect = false, hasGesture = true))
        assertEquals(listOf("https://antnexo.com/x"), t.opener.opened)
        // the page itself, a second time, is refused too
        assertTrue(c.shouldOverride(splynxPage, mainFrame = true, isRedirect = false, hasGesture = false))
        // a script navigation, a subframe, an intent: refused, nothing opened
        assertTrue(c.shouldOverride("https://antnexo.com/y", mainFrame = true, isRedirect = false, hasGesture = false))
        assertTrue(c.shouldOverride("https://antnexo.com/z", mainFrame = false, isRedirect = false, hasGesture = true))
        assertTrue(c.shouldOverride("intent://x#Intent;end", mainFrame = true, isRedirect = false, hasGesture = true))
        assertEquals(listOf("https://antnexo.com/x"), t.opener.opened)
        assertEquals(0, failed)
    }

    @Test
    fun aRedirectOfThePageIsAFailedLoad() {
        c.attach(web())
        c.load()
        assertTrue(c.shouldOverride("https://other.antnexo.com/app", mainFrame = true, isRedirect = true, hasGesture = false))
        assertEquals(1, failed)
        assertTrue(t.opener.opened.isEmpty())
    }

    @Test
    fun errorsOfTheMainDocumentFailTheLoad() {
        c.attach(web())
        c.load()
        c.httpError(mainFrame = false, status = 404)
        c.receivedError(mainFrame = false)
        assertEquals(0, failed)
        c.httpError(mainFrame = true, status = 404)
        assertEquals(1, failed)
        // the error page finishing isn't a load
        c.pageFinished()
        assertEquals(0, loaded)
        // try again: a fresh gate and a fresh load
        c.retry()
        assertFalse(c.shouldOverride(splynxPage, mainFrame = true, isRedirect = false, hasGesture = false))
        c.pageFinished()
        assertEquals(1, loaded)
        c.receivedError(mainFrame = true)
        assertEquals(2, failed)
    }

    @Test
    fun aDeadRendererIsHandled() {
        c.attach(web())
        assertTrue(c.renderProcessGone())
        assertEquals(1, failed)
    }

    @Test
    fun tearDownCutsTheBridge() {
        val w = web()
        c.attach(w)
        c.load()
        c.tearDown()
        assertEquals(1, t.bridge.removed)
        // nothing reaches the app afterwards
        t.bridge.onMessage!!("""{"type":"unread","count":2}""", Uri.parse("https://splynx.antnexo.com"), true)
        assertTrue(t.events.isEmpty())
        assertEquals("about:blank", shadowOf(w).lastLoadedUrl)
    }
}
