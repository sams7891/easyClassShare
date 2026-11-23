package com.easyclassshare.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.Composable
import com.easyclassshare.app.ui.HomeScreen
import com.easyclassshare.app.ui.ReceiverScreen
import com.easyclassshare.app.ui.SenderScreen
import com.easyclassshare.app.ui.theme.EasyClassShareTheme

class MainActivity : ComponentActivity() {
    private val vm: ClassShareViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Only on a fresh start - not again after a screen rotation.
        if (savedInstanceState == null) handleShareIntent(intent)
        setContent {
            EasyClassShareTheme { EasyClassShareApp(vm) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    /** Teacher tapped "Share → Easy Class Share" in another app: open Send with the text ready. */
    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        if (intent.type?.startsWith("text/") != true) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()
        if (text.isNullOrEmpty()) return
        vm.updateDraft(text)
        vm.screen = Screen.SEND
    }
}

@Composable
private fun EasyClassShareApp(vm: ClassShareViewModel) {
    val goHome = { vm.screen = Screen.HOME }
    Crossfade(targetState = vm.screen, label = "screen") { screen ->
        when (screen) {
            Screen.HOME -> HomeScreen(
                onSendClick = { vm.screen = Screen.SEND },
                onReceiveClick = { vm.screen = Screen.RECEIVE },
            )
            Screen.SEND -> {
                BackHandler(onBack = goHome)
                SenderScreen(vm, onBack = goHome)
            }
            Screen.RECEIVE -> {
                BackHandler(onBack = goHome)
                ReceiverScreen(vm, onBack = goHome)
            }
        }
    }
}
