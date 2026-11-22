package com.antnexo.sample

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.antnexo.AntNexo
import com.antnexo.AntNexoEvent
import com.antnexo.TokenProvider
import com.antnexo.compose.AntNexoSheet
import com.antnexo.core.FeedbackOptions
import com.antnexo.core.FeedbackTab
import com.antnexo.core.FeedbackTheme
import kotlinx.coroutines.launch

/** One screen to try every part of the SDK. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // `adb shell am start -n com.antnexo.sample/.MainActivity --es host http://acme.localhost:8090 --es workspace acme`
        val host = intent.getStringExtra("host")
        val workspace = intent.getStringExtra("workspace")
        if (host != null && workspace != null) AntNexo.configure(this, host, workspace)
        // scripted checks: `--es identify <jwt>` (a token your backend signed; the sample never holds the secret)
        val launchToken = intent.getStringExtra("identify")
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    Screen(host ?: BuildConfig.ANTNEXO_HOST, workspace ?: BuildConfig.ANTNEXO_WORKSPACE, launchToken)
                }
            }
        }
    }
}

@Composable
private fun Screen(host: String, workspace: String, launchToken: String?) {
    val scope = rememberCoroutineScope()
    var feedback by remember { mutableStateOf(false) }
    var roadmap by remember { mutableStateOf(false) }
    var withScreenshot by remember { mutableStateOf(false) }
    var whatsNew by remember { mutableStateOf(false) }
    var unread by remember { mutableIntStateOf(0) }
    var theme by remember { mutableStateOf<FeedbackTheme?>(null) }
    var identify by remember { mutableStateOf(launchToken != null) }
    var token by remember { mutableStateOf(launchToken ?: "") }
    val context = androidx.compose.ui.platform.LocalContext.current

    suspend fun refresh() {
        unread = runCatching { AntNexo.unreadCount() }.onFailure { Log.w("AntNexoSample", "unread: $it") }.getOrDefault(0)
    }
    LaunchedEffect(Unit) {
        if (launchToken != null) AntNexo.identify { token.trim().ifEmpty { null } }
        AntNexo.onEvent = { e ->
            Log.i("AntNexoSample", "event: $e")
            if (e is AntNexoEvent.Unread) unread = e.count
        }
        refresh()
    }

    Column(
        Modifier.safeDrawingPadding().padding(24.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("AntNexo sample", style = MaterialTheme.typography.headlineSmall)
        Text("$host · $workspace", style = MaterialTheme.typography.bodySmall)
        Button(onClick = { feedback = true }, Modifier.fillMaxWidth()) { Text("Send feedback") }
        OutlinedButton(onClick = { roadmap = true }, Modifier.fillMaxWidth()) { Text("Roadmap") }
        OutlinedButton(onClick = { withScreenshot = true }, Modifier.fillMaxWidth()) { Text("Feedback with screenshot") }
        OutlinedButton(onClick = { whatsNew = true }, Modifier.fillMaxWidth()) {
            BadgedBox(badge = { if (unread > 0) Badge { Text("$unread") } }) { Text("What's new") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((label, t) in listOf("System" to null, "Light" to FeedbackTheme.LIGHT, "Dark" to FeedbackTheme.DARK)) {
                FilterChip(selected = theme == t, onClick = {
                    theme = t
                    AntNexo.configure(context, host, workspace, FeedbackOptions(theme = t))
                }, label = { Text(label) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Switch(checked = identify, onCheckedChange = {
                identify = it
                // a token your backend signed; the sample never holds the identify secret
                AntNexo.identify(if (it) TokenProvider { token.trim().ifEmpty { null } } else null)
            })
            Text("Identify with a pasted token")
        }
        if (identify) OutlinedTextField(token, { token = it }, Modifier.fillMaxWidth(), label = { Text("Identify token (JWT)") })
        OutlinedButton(onClick = { scope.launch { AntNexo.logout(); refresh() } }, Modifier.fillMaxWidth()) { Text("Log out") }
    }

    AntNexoSheet(feedback, onDismiss = { feedback = false })
    AntNexoSheet(roadmap, onDismiss = { roadmap = false }, tab = FeedbackTab.ROADMAP)
    AntNexoSheet(withScreenshot, onDismiss = { withScreenshot = false }, screenshot = true)
    AntNexoSheet(whatsNew, onDismiss = { whatsNew = false; scope.launch { refresh() } }, tab = FeedbackTab.CHANGELOG)
}
