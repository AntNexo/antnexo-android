package com.antnexo

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class FeedbackActivityTest {
    private val t = TestSession()

    @After
    fun clear() {
        Sessions.current = null
    }

    private fun open() = Robolectric.buildActivity(FeedbackActivity::class.java).also {
        Sessions.current = t.session
        it.setup()
    }

    @Test
    fun opensWithAWebViewAndDone() {
        val a = open().get()
        assertNotNull(a.webView)
        assertNotNull(a.doneButton)
        assertEquals(splynxPage, shadowOf(a.webView).lastLoadedUrl)
        assertTrue(t.events.isEmpty())
    }

    @Test
    fun doneClosesOnce() {
        val c = open()
        c.get().doneButton!!.performClick()
        assertTrue(c.get().isFinishing)
        c.pause().stop().destroy()
        assertEquals(listOf<AntNexoEvent>(AntNexoEvent.Closed), t.events)
        assertEquals(1, t.ended)
        assertEquals(1, t.bridge.removed)
        assertNull(Sessions.current)
    }

    @Test
    fun backIsDone() {
        val c = open()
        c.get().onBackPressedDispatcher.onBackPressed()
        assertTrue(c.get().isFinishing)
        c.pause().stop().destroy()
        assertEquals(listOf<AntNexoEvent>(AntNexoEvent.Closed), t.events)
    }

    @Test
    fun aRecreatedActivityKeepsTheSession() {
        val c = open()
        val before = t.session.controller
        c.recreate()
        assertTrue(t.events.isEmpty())
        assertEquals(0, t.ended)
        assertNotNull(t.session.controller)
        assertNotSame(before, t.session.controller)
        assertEquals(splynxPage, shadowOf(c.get().webView).lastLoadedUrl)
    }

    @Test
    fun rotationDoesNotRecreate() {
        val app = ApplicationProvider.getApplicationContext<android.content.Context>()
        val info = app.packageManager.getActivityInfo(ComponentName(app, FeedbackActivity::class.java), 0)
        val needed = ActivityInfo.CONFIG_ORIENTATION or ActivityInfo.CONFIG_SCREEN_SIZE or ActivityInfo.CONFIG_SMALLEST_SCREEN_SIZE or
            ActivityInfo.CONFIG_SCREEN_LAYOUT or ActivityInfo.CONFIG_KEYBOARD_HIDDEN
        assertEquals(needed, info.configChanges and needed)
    }

    @Test
    fun withoutASessionItFinishesQuietly() {
        val c = Robolectric.buildActivity(FeedbackActivity::class.java).setup()
        assertTrue(c.get().isFinishing)
        c.destroy()
        assertTrue(t.events.isEmpty())
    }

    @Test
    fun aFailedLoadShowsTryAgain() {
        val a = open().get()
        t.session.controller!!.httpError(mainFrame = true, status = 500)
        assertNotNull(a.failureView)
        t.session.controller!!.retry()
        t.session.controller!!.pageFinished()
        assertNull(a.failureView)
    }

    @Test
    fun tryAgainIsCentred() {
        val a = open().get()
        t.session.controller!!.httpError(mainFrame = true, status = 500)
        val box = a.failureView as android.widget.LinearLayout
        for (i in 0 until box.childCount) {
            val v = box.getChildAt(i)
            assertEquals(android.view.ViewGroup.LayoutParams.WRAP_CONTENT, v.layoutParams.width)
        }
    }

    @Test
    fun anOutdatedWebViewSaysSo() {
        t.bridge.isSupported = false
        val a = open().get()
        assertNotNull(a.failureView)
        assertNull(t.bridge.script)
    }

    @Test
    fun theFileChooserAnswersEvenWhenCancelled() {
        val a = open().get()
        val got = mutableListOf<Array<Uri>?>()
        val cb = ValueCallback<Array<Uri>> { got += it }
        val params = object : WebChromeClient.FileChooserParams() {
            override fun getMode() = MODE_OPEN
            override fun getAcceptTypes() = arrayOf("image/*")
            override fun isCaptureEnabled() = false
            override fun getTitle(): CharSequence? = null
            override fun getFilenameHint(): String? = null
            override fun createIntent() = Intent(Intent.ACTION_GET_CONTENT).setType("image/*")
        }
        val chooser = t.session.controller!!
        val take = chooserOf(chooser)
        assertTrue(take(cb, params))
        val started = shadowOf(a).nextStartedActivityForResult
        assertEquals(Intent.ACTION_GET_CONTENT, started.intent.action)
        shadowOf(a).receiveResult(started.intent, Activity.RESULT_CANCELED, null)
        assertEquals(1, got.size)
        assertNull(got[0])

        // the next one works, and a picked image reaches the page
        assertTrue(take(cb, params))
        val again = shadowOf(a).nextStartedActivityForResult
        val image = Uri.parse("content://media/1")
        shadowOf(a).receiveResult(again.intent, Activity.RESULT_OK, Intent().setData(image))
        assertEquals(listOf(image), got[1]!!.toList())
    }

    private fun chooserOf(c: FeedbackController) = c.onFileChooser!!
}
