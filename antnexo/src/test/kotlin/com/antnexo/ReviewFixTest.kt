package com.antnexo

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Looper
import android.view.WindowManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.test.core.app.ApplicationProvider
import com.antnexo.core.AppContext
import com.antnexo.core.PageMessage
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** Findings of the PR 2 reviews (security + /code-review), each pinned by a test. */
@RunWith(RobolectricTestRunner::class)
class ReviewFixTest {
    private val app: Context = ApplicationProvider.getApplicationContext()

    @After
    fun clear() {
        Sessions.current = null
        SignInActivity.pending = null
    }

    private fun engine(events: MutableList<AntNexoEvent> = mutableListOf()) = Engine().apply {
        seenStore = { MemorySeen() }
        context = { AppContext(app = "Test") }
        capture = { _, done -> done(null) }
        clearData = {}
        deps = SessionDeps(bridge = FakeBridge(), opener = { FakeOpener() }, signIn = { FakeSignIn() })
        onEvent = { events += it }
        identifyTimeoutMs = 2_000
        configure(app, "https://splynx.antnexo.com", "splynx")
    }

    // logout / close while the sheet is still being prepared must not open it with the previous person's token

    @Test
    fun logoutDuringPrepareDropsTheSheet() {
        val e = engine()
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        e.tokenProvider = TokenProvider { kotlinx.coroutines.delay(300); "token-of-A" }
        var closed = 0
        e.show(activity, null, screenshot = false) { closed++ }
        runBlocking { e.logout() }
        Thread.sleep(500)
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(shadowOf(activity).nextStartedActivity)
        assertNull(Sessions.current)
        assertEquals(1, closed)
        // and the next one opens
        e.tokenProvider = null
        e.show(activity, null, screenshot = false)
        shadowOf(Looper.getMainLooper()).idle()
        assertNotNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun closeDuringPrepareDropsTheSheet() {
        val e = engine()
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        e.tokenProvider = TokenProvider { kotlinx.coroutines.delay(300); "t" }
        e.show(activity, null, screenshot = false)
        e.close()
        Thread.sleep(500)
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    // logout: the widget's session is in localStorage, which deleteOrigin doesn't clear (checked on WebView 133)

    @Test
    fun logoutWipesTheBoardStorageOnTheNextOpening() = runBlocking {
        val prefs = MemoryWipe()
        val e = engine().apply { wipeStore = { prefs } }
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        assertFalse(e.prepare(activity, null, false)!!.also { it.end() }.state.wipe)
        e.logout()
        val s = e.prepare(activity, null, false)!!
        assertTrue(s.state.wipe)
        assertTrue(com.antnexo.core.bridgeScript(s.state).startsWith("try{localStorage.clear();sessionStorage.clear()}catch(e){}"))
        // dropped only once a page has loaded with it
        s.wiped()
        s.end()
        assertFalse(e.prepare(activity, null, false)!!.state.wipe)
    }

    // the engine never stays "presenting" after a failure

    @Test
    fun aThrowingSeamDoesNotWedgeTheEngine() {
        val e = engine()
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        e.context = { throw IllegalStateException("odd ROM") }
        var closed = 0
        e.show(activity, null, screenshot = false) { closed++ }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, closed)
        e.context = { AppContext() }
        e.show(activity, null, screenshot = false)
        shadowOf(Looper.getMainLooper()).idle()
        assertNotNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun aSheetThatNeverAttachesIsDropped() {
        val e = engine().apply { attachTimeoutMs = 50 }
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        var closed = 0
        e.show(activity, null, screenshot = false) { closed++ }
        shadowOf(Looper.getMainLooper()).idle()
        assertNotNull(Sessions.current)
        // FeedbackActivity never came (a blocked background start): after the timeout the next show works
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100))
        assertNull(Sessions.current)
        assertEquals(1, closed)
    }

    @Test
    fun closeFromAnotherThreadRunsOnMain() {
        val t = TestSession()
        Sessions.current = t.session
        val e = engine()
        val th = Thread { e.close() }
        th.start()
        th.join()
        assertTrue(t.events.isEmpty())
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf<AntNexoEvent>(AntNexoEvent.Closed), t.events)
    }

    // a hostile or buggy page can't launch things while the person isn't looking at the sheet

    @Test
    fun openAndAuthNeedTheSheetInFront() {
        val t = TestSession()
        val c = FeedbackController(t.session)
        t.session.resumed = false
        c.handle(PageMessage.Open("https://antnexo.com/x"))
        c.handle(PageMessage.Auth("https://splynx.antnexo.com/api/v1/public/oauth/google/start"))
        assertTrue(t.opener.opened.isEmpty())
        assertTrue(t.signIn.started.isEmpty())
        t.session.resumed = true
        c.handle(PageMessage.Open("https://antnexo.com/x"))
        assertEquals(1, t.opener.opened.size)
    }

    @Test
    fun oneSignInAtATime() {
        val t = TestSession()
        val c = FeedbackController(t.session)
        t.session.resumed = true
        val u = "https://splynx.antnexo.com/api/v1/public/oauth/google/start"
        c.handle(PageMessage.Auth(u))
        t.session.resumed = true
        c.handle(PageMessage.Auth(u))
        assertEquals(1, t.signIn.started.size)
        t.signIn.onClosed!!()
        c.handle(PageMessage.Auth(u))
        assertEquals(2, t.signIn.started.size)
    }

    // the sign-in callback never outlives the sheet, and reaches whichever page is current

    @Test
    fun endingTheSessionDropsThePendingSignIn() {
        val t = TestSession()
        SignInActivity.pending = { error("must not run") }
        t.session.end()
        assertNull(SignInActivity.pending)
    }

    @Test
    fun runJsAfterTearDownDoesNothing() {
        val t = TestSession()
        val c = FeedbackController(t.session)
        var ran = 0
        c.attach(android.webkit.WebView(app))
        c.tearDown()
        c.evaluate = { ran++ }
        c.runJs("x")
        assertEquals(0, ran)
    }

    @Test
    fun signInActivityOpensOnlyTheBoardsOAuthStart() {
        Sessions.current = TestSession().session
        val bad = Intent(app, SignInActivity::class.java).putExtra(SignInActivity.EXTRA_URL, "https://evil.io/x")
        var closed = 0
        SignInActivity.pending = { closed++ }
        val c = Robolectric.buildActivity(SignInActivity::class.java, bad).setup()
        assertNull(shadowOf(c.get()).nextStartedActivity)
        assertEquals(1, closed)
        assertTrue(c.get().isFinishing)
    }

    // files: every accepted image type, several at once, and only content the person picked

    @Test
    fun theChooserAsksForEveryAcceptedType() {
        val i = chooserIntent(arrayOf("image/png", "image/jpeg,image/webp", "image/gif"), multiple = true)
        assertEquals(Intent.ACTION_GET_CONTENT, i.action)
        assertTrue(i.hasCategory(Intent.CATEGORY_OPENABLE))
        assertEquals("image/*", i.type)
        assertEquals(listOf("image/png", "image/jpeg", "image/webp", "image/gif"), i.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)!!.toList())
        assertTrue(i.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false))
        assertEquals("*/*", chooserIntent(arrayOf(""), multiple = false).type)
    }

    @Test
    fun onlyForeignContentUrisAreUploaded() {
        val ok = Uri.parse("content://com.android.providers.media.documents/document/image%3A1")
        val picked = arrayOf(ok, Uri.parse("file:///data/data/com.antnexo.test/shared_prefs/x.xml"),
            Uri.parse("content://${app.packageName}.provider/x"))
        assertEquals(listOf(ok), safeUris(app, picked)!!.toList())
        assertNull(safeUris(app, arrayOf(Uri.parse("file:///sdcard/x.png"))))
    }

    // the screen keeps the page across dark mode, and a dead renderer gets a new WebView

    @Test
    fun darkModeDoesNotRecreate() {
        val info = app.packageManager.getActivityInfo(ComponentName(app, FeedbackActivity::class.java), 0)
        assertEquals(ActivityInfo.CONFIG_UI_MODE, info.configChanges and ActivityInfo.CONFIG_UI_MODE)
    }

    @Test
    fun tryAgainAfterADeadRendererUsesANewWebView() {
        val t = TestSession()
        Sessions.current = t.session
        val c = Robolectric.buildActivity(FeedbackActivity::class.java).setup()
        val a = c.get()
        val first = a.webView
        val ctl = t.session.controller!!
        assertTrue(ctl.renderProcessGone())
        assertNotNull(a.failureView)
        a.retry()
        assertNotSame(first, a.webView)
        assertNotSame(ctl, t.session.controller)
        assertEquals(splynxPage, shadowOf(a.webView).lastLoadedUrl)
    }

    // screenshots never include a window the app marked secure

    @Test
    fun aSecureWindowIsNotCaptured() {
        val a = Robolectric.buildActivity(Activity::class.java).setup().get()
        a.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        var got: String? = "unset"
        captureScreenshot(a) { got = it }
        assertNull(got)
    }

    @Suppress("unused")
    private fun params(types: Array<String>, mode: Int) = object : WebChromeClient.FileChooserParams() {
        override fun getMode() = mode
        override fun getAcceptTypes() = types
        override fun isCaptureEnabled() = false
        override fun getTitle(): CharSequence? = null
        override fun getFilenameHint(): String? = null
        override fun createIntent() = Intent()
    }

    @Suppress("unused")
    private val unusedCallback = ValueCallback<Array<Uri>> { }
}

/** In-memory wipe flags. */
internal class MemoryWipe : WipeStore {
    private val set = mutableSetOf<String>()
    override fun pending(origin: String) = origin in set
    override fun mark(origin: String) {
        set += origin
    }

    override fun done(origin: String) {
        set -= origin
    }
}
