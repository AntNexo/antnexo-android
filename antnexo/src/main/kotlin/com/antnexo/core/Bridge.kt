package com.antnexo.core

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** The platform the page is told it runs on (`AntNexoApp.platform`). */
enum class BridgePlatform(val value: String) { ANDROID("android") }

/** The app's context the page sends with each post. Every field is optional; null fields are left out. */
data class AppContext(
    val app: String? = null,
    val appVersion: String? = null,
    val appBuild: String? = null,
    val os: String? = null,
    val osVersion: String? = null,
    val device: String? = null,
    val locale: String? = null,
)

/** What the app hands the page when the sheet opens. */
class NativeState(
    val platform: BridgePlatform,
    val context: AppContext,
    /** A fresh identify token from the app's backend; never logged, never in a URL. */
    val identify: String? = null,
    /** What's new: the newest entry seen (ISO 8601), as the page last reported it. */
    val seen: String? = null,
    /** The app's screen as a `data:image/jpeg;base64,…` URL, when the app asked for one. */
    val screenshot: String? = null,
    /** Clear the board origin's storage before the page runs (the first opening after a logout). */
    val wipe: Boolean = false,
) {
    override fun toString() =
        "NativeState(platform=$platform, context=$context, identify=${identify?.let { "<redacted>" }}, seen=$seen, " +
            "screenshot=${screenshot?.let { "<${it.length} chars>" }}, wipe=$wipe)"
}

/**
 * The script injected at document start on the board origin: `window.AntNexoApp`, frozen, its `post` sending to the
 * object `addWebMessageListener` injects as [listener] (captured here, so the page can't swap it later). Everything the
 * app provides goes through JSON, with `<` and U+2028/U+2029 escaped.
 */
fun bridgeScript(s: NativeState, listener: String = "antnexoNative"): String {
    val c = s.context
    val json = buildJsonObject {
        put("v", 1)
        put("platform", s.platform.value)
        put("sdk", SDK_VERSION)
        put("context", buildJsonObject {
            c.app?.let { put("app", it) }
            c.appVersion?.let { put("app_version", it) }
            c.appBuild?.let { put("app_build", it) }
            c.os?.let { put("os", it) }
            c.osVersion?.let { put("os_version", it) }
            c.device?.let { put("device", it) }
            c.locale?.let { put("locale", it) }
        })
        // explicit nulls: the page reads them as "none"
        put("identify", s.identify?.let(::JsonPrimitive) ?: JsonNull)
        put("seen", s.seen?.let(::JsonPrimitive) ?: JsonNull)
        put("screenshot", s.screenshot?.let(::JsonPrimitive) ?: JsonNull)
    }.toString()
    val safe = json.replace("<", "\\u003c").replace("\u2028", "\\u2028").replace("\u2029", "\\u2029")
    // the widget keeps its session in localStorage, which WebStorage.deleteOrigin doesn't clear (WebView 133)
    val wipe = if (s.wipe) "try{localStorage.clear();sessionStorage.clear()}catch(e){}" else ""
    return wipe + "window.AntNexoApp=(function(){var n=window.$listener;var a=$safe;a.context=Object.freeze(a.context);" +
        "a.post=function(m){n.postMessage(JSON.stringify(m))};return Object.freeze(a)})();"
}
