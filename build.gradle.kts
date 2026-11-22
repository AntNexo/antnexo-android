plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

// The SDK version, from the one place it is written (com.antnexo.core.SDK_VERSION); the artifacts use it unless JitPack
// sets VERSION (the tag). CI fails a v* tag that doesn't match it.
val sdkVersion: String = run {
    val src = rootProject.file("antnexo/src/main/kotlin/com/antnexo/core/Version.kt")
    val m = Regex("""SDK_VERSION(?:: String)? = "([^"]+)"""").find(src.readText())
    requireNotNull(m) { "SDK_VERSION not found in $src" }.groupValues[1]
}
extra["sdkVersion"] = sdkVersion

tasks.register("printSdkVersion") { doLast { println(sdkVersion) } }
