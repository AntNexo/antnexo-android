package com.antnexo

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebStorage
import com.antnexo.core.AntNexoException
import com.antnexo.core.AppContext
import com.antnexo.core.BoardHost
import com.antnexo.core.BridgePlatform
import com.antnexo.core.ChangelogClient
import com.antnexo.core.FeedbackOptions
import com.antnexo.core.FeedbackTab
import com.antnexo.core.NativeState
import com.antnexo.core.SeenStore
import com.antnexo.core.appPageUrl
import com.antnexo.core.parseIsoDate
import com.antnexo.core.unreadCount
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Your backend's identify token for the person using the app, or null to open the sheet signed out. */
fun interface TokenProvider {
    suspend fun token(): String?
}

/** The SDK's state behind the [AntNexo] facade, with seams for tests. Main thread, except where noted. */
internal class Engine {
    class Configuration(val host: BoardHost, val workspace: String, val options: FeedbackOptions, val app: Context)

    var configuration: Configuration? = null
        private set
    var tokenProvider: TokenProvider? = null
    var onEvent: ((AntNexoEvent) -> Unit)? = null
    var identifyTimeoutMs = 10_000L

    // seams
    var seenStore: (Context) -> SeenStore = { PrefsSeenStore(it) }
    var context: (Context) -> AppContext = { deviceContext(it) }
    var capture: (Activity, (String?) -> Unit) -> Unit = { a, done -> captureScreenshot(a, done) }
    var fetchDates: suspend (BoardHost) -> List<Long> = { ChangelogClient(it).publishedDates() }
    var clearData: suspend (BoardHost) -> Unit = { h -> WebStorage.getInstance().deleteOrigin(h.origin) }
    var now: () -> Long = { System.currentTimeMillis() }
    var deps = SessionDeps()

    /** A sheet is up or being prepared: one at a time. Lives only in memory, so a new process starts free. */
    private var presenting = false
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Runs on its own threads: a token provider that blocks can't hold the sheet past the timeout. */
    private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun configure(context: Context, host: String, workspace: String, options: FeedbackOptions = FeedbackOptions()) {
        val h = BoardHost.parse(host)
        val ws = workspace.trim()
        if (ws.isEmpty()) throw AntNexoException.InvalidWorkspace()
        configuration = Configuration(h, ws, options, context.applicationContext ?: context)
    }

    /** Events reach the app on the main thread. */
    fun dispatch(e: AntNexoEvent) {
        if (Looper.myLooper() == Looper.getMainLooper()) onEvent?.invoke(e) else main.post { onEvent?.invoke(e) }
    }

    /**
     * Everything a sheet needs, or null when it can't open (not configured, one already up). The screenshot is taken
     * here, before the sheet covers the app. The session's end frees the next one.
     */
    suspend fun prepare(activity: Activity, tab: FeedbackTab?, screenshot: Boolean, onClosed: (() -> Unit)? = null): Session? {
        val c = configuration
        if (c == null) {
            Log.e(TAG, "AntNexo.show() before AntNexo.configure(context, host, workspace)")
            return null
        }
        if (presenting) return null
        presenting = true
        val shot: String? = if (screenshot) suspendCancellableCoroutine<String?> { cont -> capture(activity) { cont.resume(it) } } else null
        val token = identifyToken()
        val options = if (tab != null) c.options.copy(tab = tab) else c.options
        val store = seenStore(c.app)
        val state = NativeState(BridgePlatform.ANDROID, context(activity), token, store.get(c.workspace), shot)
        return Session(
            host = c.host, workspace = c.workspace, page = appPageUrl(c.host, c.workspace, options), state = state,
            options = options, seenStore = store, onEvent = ::dispatch,
            onEnded = {
                presenting = false
                onClosed?.invoke()
            },
            deps = deps,
        )
    }

    /** The app's token, or null when it gives none, fails, or takes longer than the timeout. */
    private suspend fun identifyToken(): String? {
        val provider = tokenProvider ?: return null
        val work = background.async { provider.token() }
        val token = try {
            withTimeoutOrNull(identifyTimeoutMs) { work.await() }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException && !work.isCancelled) throw e
            Log.w(TAG, "the identify token provider failed; opening without identify")
            null
        }
        work.cancel()
        return token?.takeIf { it.isNotEmpty() }
    }

    /** Opens the sheet over activity; onClosed when it closes, or at once when it can't open. */
    fun show(activity: Activity, tab: FeedbackTab?, screenshot: Boolean, onClosed: (() -> Unit)? = null) {
        scope.launch {
            val s = prepare(activity, tab, screenshot, onClosed)
            if (s == null) {
                onClosed?.invoke()
                return@launch
            }
            if (activity.isFinishing || activity.isDestroyed) {
                Log.w(TAG, "AntNexo.show(): the activity went away before the sheet opened")
                return@launch discard(s)
            }
            Sessions.current = s
            try {
                activity.startActivity(Intent(activity, FeedbackActivity::class.java))
            } catch (e: RuntimeException) {
                Log.e(TAG, "AntNexo.show(): couldn't start the sheet", e)
                Sessions.current = null
                discard(s)
            }
        }
    }

    /** A sheet that never appeared: frees the next show() without a Closed event. */
    private fun discard(s: Session) {
        presenting = false
        s.onEnded()
    }

    fun close() {
        Sessions.current?.close()
    }

    suspend fun unreadCount(): Int {
        val c = configuration ?: throw AntNexoException.NotConfigured()
        val dates = fetchDates(c.host)
        val seen = seenStore(c.app).get(c.workspace)?.let(::parseIsoDate)
        return unreadCount(dates, seen, now())
    }

    suspend fun logout() {
        val c = configuration ?: return
        // an open page keeps its session in memory: close it first
        Sessions.current?.close()
        clearData(c.host)
    }

    companion object {
        val shared = Engine()
    }
}
