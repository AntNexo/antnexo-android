package com.antnexo.compose

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.antnexo.AntNexo
import com.antnexo.core.FeedbackTab

/**
 * The feedback sheet, driven by state:
 *
 * ```kotlin
 * var feedback by remember { mutableStateOf(false) }
 * Button(onClick = { feedback = true }) { Text("Send feedback") }
 * AntNexoSheet(visible = feedback, onDismiss = { feedback = false })
 * ```
 *
 * [onDismiss] runs when the sheet closes, or right away when it couldn't open (not configured, another one is up).
 * Setting [visible] to false closes a sheet this call opened.
 */
@Composable
fun AntNexoSheet(visible: Boolean, onDismiss: () -> Unit, tab: FeedbackTab? = null, screenshot: Boolean = false) {
    val context = LocalContext.current
    val dismiss by rememberUpdatedState(onDismiss)
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible && !open) {
            val activity = context.findActivity()
            if (activity == null) {
                dismiss()
                return@LaunchedEffect
            }
            open = true
            AntNexo.show(activity, tab, screenshot) {
                open = false
                dismiss()
            }
        } else if (!visible && open) {
            AntNexo.close()
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
