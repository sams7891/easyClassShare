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
import com.easyclassshare.app.data.LinkUtils
import com.easyclassshare.app.online.LocalSetup
import com.easyclassshare.app.online.Role
import com.easyclassshare.app.service.ClassService
import com.easyclassshare.app.service.Notifications
import com.easyclassshare.app.ui.HomeScreen
import com.easyclassshare.app.ui.ManageClassScreen
import com.easyclassshare.app.ui.ReceiverScreen
import com.easyclassshare.app.ui.SchoolScreen
import com.easyclassshare.app.ui.SenderScreen
import com.easyclassshare.app.ui.SetupScreen
import com.easyclassshare.app.ui.StudentClassScreen
import com.easyclassshare.app.ui.TeacherClassScreen
import com.easyclassshare.app.ui.openLink
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
            handleOpenMessage(intent)
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
        handleOpenMessage(intent)
    }

    /** A student tapped a "new link" notification: open the link and tell the teacher. */
    private fun handleOpenMessage(intent: Intent?) {
        if (intent?.action != Notifications.ACTION_OPEN_MESSAGE) return
        val classId = intent.getStringExtra(Notifications.EXTRA_CLASS_ID) ?: return
        val messageId = intent.getStringExtra(Notifications.EXTRA_MESSAGE_ID) ?: return
        val content = intent.getStringExtra(Notifications.EXTRA_CONTENT).orEmpty()
        // Handle it only once, not again after a rotation.
        intent.action = null
        if (LocalSetup.membership.value?.classId != classId) return
        if (LinkUtils.looksLikeLink(content)) {
            ClassViewModel.reportOpened(classId, messageId)
            openLink(this, content, getString(R.string.could_not_open))
        }
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
            Role.ADMIN, null -> {
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
    val adminView by cvm.isAdminView.collectAsState()
    val home = when (membership?.role) {
        null -> Screen.HOME
        Role.ADMIN -> Screen.SCHOOL
        else -> Screen.CLASS
    }
    val goHome = { vm.screen = home }
    val backToClass = { vm.screen = Screen.CLASS }
    val backToSchool = {
        cvm.closeSchoolClass()
        vm.screen = Screen.SCHOOL
    }

    // Follow joining/leaving and switching between classes and schools.
    // Student tablets stay on the class screen.
    LaunchedEffect(membership, vm.screen, adminView) {
        val role = membership?.role
        val screen = vm.screen
        val target = when {
            role == null && screen in setOf(Screen.CLASS, Screen.MANAGE, Screen.SCHOOL) -> Screen.HOME
            role != null && screen == Screen.HOME -> home
            role == Role.STUDENT && screen != Screen.CLASS -> Screen.CLASS
            role == Role.TEACHER && screen == Screen.SCHOOL -> Screen.CLASS
            role == Role.ADMIN && !adminView && screen in setOf(Screen.CLASS, Screen.MANAGE) -> Screen.SCHOOL
            role == Role.ADMIN && adminView && screen == Screen.SCHOOL -> Screen.CLASS
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
                addingClass = memberships.any { it.role != Role.STUDENT },
                onBack = goHome,
                // The navigation effect above moves admins on to their school.
                onDone = { vm.screen = Screen.CLASS },
            )
            Screen.CLASS -> when (membership?.role) {
                Role.STUDENT -> StudentClassScreen(cvm)
                Role.TEACHER -> TeacherClassScreen(
                    cvm,
                    onSettings = { cvm.clearError(); vm.screen = Screen.MANAGE },
                    onAddClass = { cvm.clearError(); vm.screen = Screen.SETUP },
                    onWifiMode = { vm.screen = Screen.SEND },
                )
                Role.ADMIN -> if (adminView) {
                    BackHandler(onBack = backToSchool)
                    TeacherClassScreen(
                        cvm,
                        onSettings = { cvm.clearError(); vm.screen = Screen.MANAGE },
                        onAddClass = { cvm.clearError(); vm.screen = Screen.SETUP },
                        onWifiMode = { vm.screen = Screen.SEND },
                        onBack = backToSchool,
                    )
                } else {
                    Unit
                }
                null -> Unit
            }
            Screen.MANAGE -> {
                BackHandler(onBack = backToClass)
                ManageClassScreen(cvm, onBack = backToClass)
            }
            Screen.SCHOOL -> SchoolScreen(
                cvm,
                onOpenClass = { cvm.openSchoolClass(it); vm.screen = Screen.CLASS },
                onAddClassOrSchool = { cvm.clearError(); vm.screen = Screen.SETUP },
                onWifiMode = { vm.screen = Screen.SEND },
            )
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
