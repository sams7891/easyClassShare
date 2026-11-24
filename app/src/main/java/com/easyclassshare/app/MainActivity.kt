package com.easyclassshare.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import com.easyclassshare.app.online.LocalSetup
import com.easyclassshare.app.online.Role
import com.easyclassshare.app.service.ClassService
import com.easyclassshare.app.ui.HomeScreen
import com.easyclassshare.app.ui.ManageClassScreen
import com.easyclassshare.app.ui.ReceiverScreen
import com.easyclassshare.app.ui.SenderScreen
import com.easyclassshare.app.ui.SetupScreen
import com.easyclassshare.app.ui.StudentClassScreen
import com.easyclassshare.app.ui.TeacherClassScreen
import com.easyclassshare.app.ui.theme.EasyClassShareTheme

class MainActivity : ComponentActivity() {
    private val vm: ClassShareViewModel by viewModels()
    private val cvm: ClassViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Only on a fresh start - not again after a screen rotation.
        if (savedInstanceState == null) {
            if (LocalSetup.membership.value != null) vm.screen = Screen.CLASS
            handleShareIntent(intent)
        }
        setContent {
            EasyClassShareTheme { EasyClassShareApp(vm, cvm) }
        }
    }

    override fun onStart() {
        super.onStart()
        AppVisibility.isForeground = true
        if (LocalSetup.membership.value?.role == Role.STUDENT) ClassService.start(this)
    }

    override fun onStop() {
        AppVisibility.isForeground = false
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    /** "Share → Easy Class Share" from another app: open a Send screen with the text ready. */
    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        if (intent.type?.startsWith("text/") != true) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()
        if (text.isNullOrEmpty()) return
        when (LocalSetup.membership.value?.role) {
            Role.TEACHER -> {
                cvm.updateDraft(text)
                vm.screen = Screen.CLASS
            }
            Role.STUDENT -> Unit // students can't send
            null -> {
                vm.updateDraft(text)
                vm.screen = Screen.SEND
            }
        }
    }
}

@Composable
private fun EasyClassShareApp(vm: ClassShareViewModel, cvm: ClassViewModel) {
    val membership by cvm.membership.collectAsState()
    val memberships by cvm.memberships.collectAsState()
    val removedFrom by cvm.removedFrom.collectAsState()
    val home = if (membership != null) Screen.CLASS else Screen.HOME
    val goHome = { vm.screen = home }

    // Follow joining/leaving the class. Student tablets stay on the class screen.
    LaunchedEffect(membership, vm.screen) {
        val m = membership
        val target = when {
            m == null && vm.screen in setOf(Screen.CLASS, Screen.MANAGE) -> Screen.HOME
            m != null && vm.screen == Screen.HOME -> Screen.CLASS
            m?.role == Role.STUDENT && vm.screen != Screen.CLASS -> Screen.CLASS
            else -> null
        }
        if (target != null) vm.screen = target
    }

    Crossfade(targetState = vm.screen, label = "screen") { screen ->
        when (screen) {
            Screen.HOME -> HomeScreen(
                showClassSetup = cvm.isConfigured,
                onSetupClick = { vm.screen = Screen.SETUP },
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
            Screen.SETUP -> SetupScreen(
                cvm,
                addingClass = memberships.any { it.role == Role.TEACHER },
                onBack = goHome,
                onDone = { vm.screen = Screen.CLASS },
            )
            Screen.CLASS -> when (membership?.role) {
                Role.STUDENT -> StudentClassScreen(cvm)
                Role.TEACHER -> TeacherClassScreen(
                    cvm,
                    onManage = { cvm.clearError(); vm.screen = Screen.MANAGE },
                    onAddClass = { cvm.clearError(); vm.screen = Screen.SETUP },
                    onWifiMode = { vm.screen = Screen.SEND },
                )
                null -> Unit
            }
            Screen.MANAGE -> {
                BackHandler(onBack = goHome)
                ManageClassScreen(cvm, onBack = goHome)
            }
        }
    }

    val removed = removedFrom
    if (removed != null) {
        AlertDialog(
            onDismissRequest = cvm::dismissRemovedNotice,
            text = {
                Text(
                    if (removed.isBlank()) {
                        stringResource(R.string.removed_notice_unknown)
                    } else {
                        stringResource(R.string.removed_notice, removed)
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = cvm::dismissRemovedNotice) { Text(stringResource(R.string.ok_button)) }
            },
        )
    }
}
