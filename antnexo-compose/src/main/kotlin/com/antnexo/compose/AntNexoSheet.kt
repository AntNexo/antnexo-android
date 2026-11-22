package com.antnexo.compose

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.antnexo.AntNexo
import com.antnexo.AntNexoEvent
import com.antnexo.core.FeedbackTab
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The feedback sheet, driven by state:
 *
 * ```kotlin
 * var feedback by rememberSaveable { mutableStateOf(false) }
 * Button(onClick = { feedback = true }) { Text("Send feedback") }
 * AntNexoSheet(visible = feedback, onDismiss = { feedback = false })
 * ```
 *
 * [onDismiss] runs once when the sheet closes — by the person, by [visible] turning false, or right away when it
 * couldn't open (not configured, another one is up). It survives the host activity being recreated under the
 * sheet: keep [visible] in `rememberSaveable`.
 */
@Composable
fun AntNexoSheet(visible: Boolean, onDismiss: () -> Unit, tab: FeedbackTab? = null, screenshot: Boolean = false) {
    val context = LocalContext.current
    val dismiss by rememberUpdatedState(onDismiss)
    // saved: after the host is recreated under the sheet, this composition still knows the sheet is its own
    var open by rememberSaveable { mutableStateOf(false) }
    // this composition only: a callback from an earlier one (before a recreation) must not dismiss twice
    val active = remember { AtomicBoolean(true) }

    fun ended() {
        if (open) {
            open = false
            dismiss()
        }
    }
    DisposableEffect(Unit) {
        // the end of the sheet reaches the composition that exists now, not the one that opened it
        val listener: (AntNexoEvent) -> Unit = { e -> if (e == AntNexoEvent.Closed) ended() }
        AntNexo.addEventListener(listener)
        onDispose {
            active.set(false)
            AntNexo.removeEventListener(listener)
        }
    }
    LaunchedEffect(visible) {
        // restored as open, but the sheet is gone (it never opened, or the process was restarted): start over
        if (open && !AntNexo.isShowing) ended()
        if (visible && !open) {
            val activity = context.findActivity()
            if (activity == null) {
                dismiss()
                return@LaunchedEffect
            }
            open = true
            // also when it couldn't open at all (then no Closed event comes)
            AntNexo.show(activity, tab, screenshot) { if (active.get()) ended() }
        } else if (!visible && open) {
            AntNexo.close()
            ended()
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
