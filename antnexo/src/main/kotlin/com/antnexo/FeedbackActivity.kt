package com.antnexo

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.antnexo.core.FeedbackTheme
import java.lang.ref.WeakReference

/**
 * The sheet: a top bar with Done, the WebView below it, and a native retry view when the page can't load. System Back
 * is Done. It attaches to [Sessions.current]; without one (the process was killed while it was open) it just finishes.
 */
class FeedbackActivity : ComponentActivity() {
    private var session: Session? = null
    private var controller: FeedbackController? = null
    internal var webView: WebView? = null
        private set
    internal var doneButton: View? = null
        private set
    internal var failureView: View? = null
        private set
    private var content: FrameLayout? = null
    private var pendingFiles: ValueCallback<Array<Uri>>? = null
    private var colors = Colors(dark = false, accent = null)

    private val fileChooser = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val cb = pendingFiles
        pendingFiles = null
        cb?.onReceiveValue(chosenFiles(r.resultCode, r.data))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val s = Sessions.current
        if (s == null || s.ended) {
            finish()
            return
        }
        session = s
        s.activity = WeakReference(this)
        // recreated by the system: the old activity's page went with it
        s.controller?.tearDown()

        colors = Colors(dark = isDark(s.options.theme), accent = parseColor(s.options.color))
        val bar = if (colors.dark) SystemBarStyle.dark(Color.TRANSPARENT)
        else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bar, navigationBarStyle = bar)
        setContentView(buildViews())

        val c = FeedbackController(s)
        controller = c
        s.controller = c
        c.onLoadFailed = { showFailure(c.outdatedWebView) }
        c.onLoaded = { hideFailure() }
        c.onFileChooser = ::chooseFiles
        c.attach(webView!!)
        c.load()
        onBackPressedDispatcher.addCallback(this) { finish() }
    }

    override fun onDestroy() {
        val s = session
        if (s != null) {
            if (isFinishing) {
                // Done, Back, or logout: the sheet is gone
                s.end()
            } else {
                // recreated (a configuration change we don't handle): the session goes on with the next activity
                controller?.tearDown()
                if (s.controller === controller) s.controller = null
            }
            if (s.activity?.get() === this) s.activity = null
        }
        pendingFiles?.onReceiveValue(null)
        pendingFiles = null
        webView?.let {
            (it.parent as? ViewGroup)?.removeView(it)
            it.destroy()
        }
        webView = null
        super.onDestroy()
    }

    // views

    private data class Colors(val dark: Boolean, val accent: Int?) {
        val background get() = if (dark) 0xFF1C1C1E.toInt() else Color.WHITE
        val text get() = if (dark) Color.WHITE else 0xFF111111.toInt()
        val secondary get() = if (dark) 0xFFAEAEB2.toInt() else 0xFF6B6B70.toInt()
        val action get() = accent ?: text
    }

    private fun isDark(theme: FeedbackTheme?) = when (theme) {
        FeedbackTheme.LIGHT -> false
        FeedbackTheme.DARK -> true
        else -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    private fun parseColor(c: String?): Int? = c?.let { runCatching { it.toColorInt() }.getOrNull() }

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun buildViews(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(colors.background)
        }
        val bar = FrameLayout(this)
        val done = TextView(this).apply {
            text = getString(R.string.antnexo_done)
            setTextColor(colors.action)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            setPadding(dp(16), 0, dp(16), 0)
            minWidth = dp(48)
            isClickable = true
            isFocusable = true
            val ripple = TypedValue()
            if (context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true)) {
                setBackgroundResource(ripple.resourceId)
            }
            setOnClickListener { finish() }
        }
        bar.addView(done, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT,
            Gravity.END or Gravity.CENTER_VERTICAL))
        root.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))

        val content = FrameLayout(this)
        val web = WebView(this).apply { setBackgroundColor(colors.background) }
        content.addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            WindowInsetsCompat.CONSUMED
        }
        webView = web
        doneButton = done
        this.content = content
        return root
    }

    private fun showFailure(outdated: Boolean) {
        if (failureView != null) return
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(colors.background)
            isClickable = true
            setPadding(dp(32), 0, dp(32), 0)
        }
        box.addView(TextView(this).apply {
            text = getString(if (outdated) R.string.antnexo_outdated_webview else R.string.antnexo_load_failed)
            setTextColor(colors.secondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.CENTER
        })
        if (!outdated) {
            box.addView(TextView(this).apply {
                text = getString(R.string.antnexo_try_again)
                setTextColor(colors.action)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setPadding(dp(16), dp(12), dp(16), dp(12))
                isClickable = true
                setOnClickListener {
                    hideFailure()
                    controller?.retry()
                }
            })
        }
        content?.addView(box, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        failureView = box
    }

    private fun hideFailure() {
        failureView?.let { content?.removeView(it) }
        failureView = null
    }

    // files (the widget's attach button)

    private fun chooseFiles(cb: ValueCallback<Array<Uri>>, params: WebChromeClient.FileChooserParams): Boolean {
        // one chooser at a time; WebView needs an answer to the previous one
        pendingFiles?.onReceiveValue(null)
        pendingFiles = cb
        return try {
            fileChooser.launch(params.createIntent())
            true
        } catch (e: ActivityNotFoundException) {
            pendingFiles = null
            cb.onReceiveValue(null)
            true
        }
    }

    private fun chosenFiles(resultCode: Int, data: Intent?): Array<Uri>? {
        if (resultCode != RESULT_OK || data == null) return null
        val clip = data.clipData
        if (clip != null && clip.itemCount > 0) return Array(clip.itemCount) { clip.getItemAt(it).uri }
        return data.data?.let { arrayOf(it) }
    }
}
