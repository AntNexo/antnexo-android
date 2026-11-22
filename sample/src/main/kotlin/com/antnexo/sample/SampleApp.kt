package com.antnexo.sample

import android.app.Application
import android.content.pm.ApplicationInfo
import android.util.Log
import android.webkit.WebView
import com.antnexo.AntNexo

class SampleApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // debug builds: the sheet's WebView can be inspected from chrome://inspect (and scripted over CDP)
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) WebView.setWebContentsDebuggingEnabled(true)
        AntNexo.configure(this, BuildConfig.ANTNEXO_HOST, BuildConfig.ANTNEXO_WORKSPACE)
        AntNexo.onEvent = { Log.i("AntNexoSample", "event: $it") }
    }
}
