package com.antnexo

import android.app.Activity
import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.antnexo.core.AntNexoException
import com.antnexo.core.AppContext
import com.antnexo.core.BoardHost
import com.antnexo.core.BridgePlatform
import com.antnexo.core.SDK_VERSION
import com.antnexo.core.FeedbackTab
import com.antnexo.core.unreadCount
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class EngineTest {
    private val app: Context = ApplicationProvider.getApplicationContext()
    private val events = mutableListOf<AntNexoEvent>()
    private val seen = MemorySeen()
    private val bridge = FakeBridge()
    private var cleared = mutableListOf<BoardHost>()
    private var captures = 0
    private val e = Engine().apply {
        seenStore = { seen }
        context = { AppContext(app = "Test") }
        capture = { _, done -> captures++; done("data:image/jpeg;base64,QUJD") }
        clearData = { cleared += it }
        deps = SessionDeps(bridge = bridge, opener = { FakeOpener() }, signIn = { FakeSignIn() })
        onEvent = { events += it }
        identifyTimeoutMs = 100
    }
    private lateinit var activity: Activity

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    }

    @After
    fun tearDown() {
        Sessions.current = null
    }

    private fun configure() = e.configure(app, "https://splynx.antnexo.com", "splynx")

    @Test
    fun prepareBeforeConfigureIsANoOp() = runBlocking {
        assertNull(e.prepare(activity, null, screenshot = false))
    }

    @Test
    fun configureValidates() {
        assertThrows(AntNexoException.InvalidWorkspace::class.java) { e.configure(app, "https://splynx.antnexo.com", "  ") }
        assertThrows(AntNexoException.InvalidHost::class.java) { e.configure(app, "splynx.antnexo.com", "splynx") }
    }

    @Test
    fun aWrapperSetsPlatformAndSdk() = runBlocking {
        configure()
        val first = e.prepare(activity, null, screenshot = false)!!
        assertEquals(BridgePlatform.ANDROID, first.state.platform)
        assertEquals(SDK_VERSION, first.state.sdk)
        first.onEnded()
        e.wrapper = BridgePlatform.REACT_NATIVE to "9.9.9"
        val s = e.prepare(activity, null, screenshot = false)!!
        assertEquals(BridgePlatform.REACT_NATIVE, s.state.platform)
        assertEquals("9.9.9", s.state.sdk)
    }

    @Test
    fun theTokenGoesToThePage() = runBlocking {
        configure()
        e.tokenProvider = TokenProvider { "jwt" }
        val s = e.prepare(activity, FeedbackTab.ROADMAP, screenshot = false)!!
        assertEquals("jwt", s.state.identify)
        assertTrue(s.page.endsWith("&tab=roadmap"))
        assertNull(s.state.screenshot)
        assertEquals(0, captures)
    }

    @Test
    fun aFailingSlowOrEmptyTokenOpensWithoutIdentify() = runBlocking {
        configure()
        for (p in listOf(
            TokenProvider { throw IllegalStateException("backend down") },
            TokenProvider { "" },
            TokenProvider { null },
            TokenProvider { delay(5_000); "late" },
            TokenProvider { Thread.sleep(1_000); "blocking" },
        )) {
            e.tokenProvider = p
            val started = System.nanoTime()
            val s = e.prepare(activity, null, screenshot = false)
            assertNotNull(s)
            assertNull(s!!.state.identify)
            assertTrue((System.nanoTime() - started) / 1_000_000 < 900)
            s.end()
        }
    }

    @Test
    fun seenAndScreenshotGoToThePage() = runBlocking {
        configure()
        seen.set("2026-11-01T00:00:00Z", "splynx")
        val s = e.prepare(activity, null, screenshot = true)!!
        assertEquals("2026-11-01T00:00:00Z", s.state.seen)
        assertEquals("data:image/jpeg;base64,QUJD", s.state.screenshot)
    }

    @Test
    fun oneSheetAtATime() = runBlocking {
        configure()
        val first = e.prepare(activity, null, screenshot = false)!!
        assertNull(e.prepare(activity, null, screenshot = false))
        first.end()
        assertNotNull(e.prepare(activity, null, screenshot = false))
    }

    @Test
    fun showStartsTheSheetAndReportsItsEnd() {
        configure()
        var closed = 0
        e.show(activity, null, screenshot = false) { closed++ }
        shadowOf(Looper.getMainLooper()).idle()
        val started = shadowOf(activity).nextStartedActivity
        assertEquals(FeedbackActivity::class.java.name, started.component!!.className)
        assertNotNull(Sessions.current)
        // a second show while it is up does nothing but tell its caller
        var refused = 0
        e.show(activity, null, screenshot = false) { refused++ }
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(shadowOf(activity).nextStartedActivity)
        assertEquals(1, refused)
        Sessions.current!!.end()
        assertEquals(1, closed)
        assertEquals(listOf<AntNexoEvent>(AntNexoEvent.Closed), events)
    }

    @Test
    fun showBeforeConfigureTellsTheCaller() {
        var closed = 0
        e.show(activity, null, screenshot = false) { closed++ }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, closed)
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun unreadCountUsesTheChangelogAndSeen() = runBlocking {
        assertThrows(AntNexoException.NotConfigured::class.java) { runBlocking { e.unreadCount() } }
        configure()
        val now = Instant.parse("2026-11-22T12:00:00Z").toEpochMilli()
        val dates = listOf(Instant.parse("2026-11-20T00:00:00Z").toEpochMilli(), Instant.parse("2026-10-01T00:00:00Z").toEpochMilli())
        e.fetchDates = { dates }
        e.now = { now }
        assertEquals(unreadCount(dates, null, now), e.unreadCount())
        seen.set("2026-11-21T00:00:00Z", "splynx")
        assertEquals(0, e.unreadCount())
    }

    @Test
    fun logoutClosesTheSheetAndClearsOnlyTheBoard() = runBlocking {
        configure()
        seen.set("2026-11-01T00:00:00Z", "splynx")
        val s = e.prepare(activity, null, screenshot = false)!!
        Sessions.current = s
        e.logout()
        assertEquals(listOf<AntNexoEvent>(AntNexoEvent.Closed), events)
        assertEquals(listOf(BoardHost.parse("https://splynx.antnexo.com")), cleared)
        assertEquals("2026-11-01T00:00:00Z", seen.get("splynx"))
    }

    @Test
    fun eventsArriveOnTheMainThread() {
        configure()
        var thread: Thread? = null
        e.onEvent = { thread = Thread.currentThread() }
        val t = Thread { e.dispatch(AntNexoEvent.Unread(1)) }
        t.start()
        t.join()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(Looper.getMainLooper().thread, thread)
    }
}
