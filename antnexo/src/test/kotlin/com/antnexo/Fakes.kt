package com.antnexo

import android.net.Uri
import android.webkit.WebView
import com.antnexo.core.BoardHost
import com.antnexo.core.BridgePlatform
import com.antnexo.core.AppContext
import com.antnexo.core.FeedbackOptions
import com.antnexo.core.NativeState
import com.antnexo.core.SeenStore
import com.antnexo.core.appPageUrl

internal class FakeOpener : LinkOpener {
    val opened = mutableListOf<String>()
    override fun open(url: String) {
        opened += url
    }
}

internal class FakeSignIn : SignInPresenter {
    val started = mutableListOf<String>()
    var onClosed: (() -> Unit)? = null
    var finished = 0
    override fun start(url: String, onClosed: () -> Unit) {
        started += url
        this.onClosed = onClosed
    }

    override fun finish() {
        finished++
    }
}

internal class FakeBridge(var isSupported: Boolean = true) : BridgeInstaller {
    var script: String? = null
    var origin: String? = null
    var onMessage: ((String?, Uri, Boolean) -> Unit)? = null
    var removed = 0
    override fun supported() = isSupported
    override fun install(web: WebView, script: String, origin: String, onMessage: (String?, Uri, Boolean) -> Unit) {
        this.script = script
        this.origin = origin
        this.onMessage = onMessage
    }

    override fun remove(web: WebView) {
        removed++
    }
}

internal class MemorySeen : SeenStore {
    val map = mutableMapOf<String, String>()
    override fun get(workspace: String) = map[workspace]
    override fun set(iso: String, workspace: String) {
        map[workspace] = iso
    }
}

internal val splynx = BoardHost.parse("https://splynx.antnexo.com")
internal val splynxPage = appPageUrl(splynx, "splynx", FeedbackOptions())
internal val testState = NativeState(BridgePlatform.ANDROID, AppContext(app = "Test"), identify = "jwt")

/** A session with fakes; events land in [events]. */
internal class TestSession(
    val events: MutableList<AntNexoEvent> = mutableListOf(),
    val opener: FakeOpener = FakeOpener(),
    val signIn: FakeSignIn = FakeSignIn(),
    val bridge: FakeBridge = FakeBridge(),
    val seen: MemorySeen = MemorySeen(),
    options: FeedbackOptions = FeedbackOptions(),
) {
    var ended = 0
    val session = Session(
        host = splynx, workspace = "splynx", page = splynxPage, state = testState, options = options, seenStore = seen,
        onEvent = { events += it }, onEnded = { ended++ },
        deps = SessionDeps(bridge = bridge, opener = { opener }, signIn = { signIn }),
    )
}
