package com.easyclassshare.app

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import com.easyclassshare.app.data.LinkUtils
import com.easyclassshare.app.online.LocalSetup
import com.easyclassshare.app.online.Role
import com.easyclassshare.app.service.ClassService
import com.easyclassshare.app.service.DevicePermissions
import com.easyclassshare.app.service.Notifications
import com.easyclassshare.app.settings.AppSettings
import com.easyclassshare.app.ui.HomeScreen
import com.easyclassshare.app.ui.ManageClassScreen
import com.easyclassshare.app.ui.PermissionsScreen
import com.easyclassshare.app.ui.ReceiverScreen
import com.easyclassshare.app.ui.SchoolScreen
import com.easyclassshare.app.ui.SenderScreen
import com.easyclassshare.app.ui.SettingsScreen
import com.easyclassshare.app.ui.SetupMode
import com.easyclassshare.app.ui.StartScreen
import com.easyclassshare.app.ui.SetupScreen
import com.easyclassshare.app.ui.StudentClassScreen
import com.easyclassshare.app.ui.TeacherClassScreen
import com.easyclassshare.app.ui.UpdatePrompt
import com.easyclassshare.app.ui.openLink
import com.easyclassshare.app.ui.theme.EasyClassShareTheme
import com.easyclassshare.app.ui.theme.Paper
import com.easyclassshare.app.ui.theme.isDark
import com.easyclassshare.app.update.AppUpdater
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: ClassShareViewModel by viewModels()
    private val cvm: ClassViewModel by viewModels()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppSettings.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // A new language or look needs the screens built again; the open screen stays.
        lifecycleScope.launch { merge(AppSettings.language.drop(1), AppSettings.theme.drop(1)).collect { recreate() } }
        // Only on a fresh start - not again after a screen rotation. Missing permissions are
        // shown on the student screen; the Permissions screen opens by itself only after joining.
        if (savedInstanceState == null) {
            vm.screen = when {
                LocalSetup.isStudentDevice -> Screen.STUDENT
                LocalSetup.memberships.value.isNotEmpty() -> Screen.START
                else -> Screen.HOME
            }
            handleShareIntent(intent)
            handleOpenMessage(intent)
        }
        setContent {
            val themeMode by AppSettings.theme.collectAsState()
            val dark = isDark(themeMode)
            // Status bar icons follow the app's own light/dark choice.
            LaunchedEffect(dark) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            EasyClassShareTheme(dark) {
                // Paper behind every screen: while one screen fades into the next, or the
                // keyboard opens, the window background would otherwise shine through.
                val paper = Paper
                LaunchedEffect(paper) { window.setBackgroundDrawable(ColorDrawable(paper.toArgb())) }
                Box(Modifier.fillMaxSize().background(paper)) { EasyClassShareApp(vm, cvm) }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        AppVisibility.isForeground = true
        if (LocalSetup.isStudentDevice) ClassService.start(this)
        AppUpdater.checkInBackgroundIfDue(this)
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
        if (LocalSetup.memberships.value.none { it.role == Role.STUDENT && it.classId == classId }) return
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
        when {
            LocalSetup.isStudentDevice -> Unit // students can't send
            cvm.current.value != null -> {
                cvm.updateDraft(text)
                vm.screen = Screen.CLASS
            }
            LocalSetup.memberships.value.isNotEmpty() -> {
                // The text waits in the send box until a class is opened.
                cvm.updateDraft(text)
                vm.screen = Screen.START
            }
            else -> {
                vm.updateDraft(text)
                vm.screen = Screen.SEND
            }
        }
    }
}

@Composable
private fun EasyClassShareApp(vm: ClassShareViewModel, cvm: ClassViewModel) {
    val context = LocalContext.current
    val memberships by cvm.memberships.collectAsState()
    val removedFrom by cvm.removedFrom.collectAsState()
    val openClass by cvm.current.collectAsState()
    val openSchool by cvm.openSchool.collectAsState()
    val isSuperAdmin by cvm.isSuperAdmin.collectAsState()
    val studentAttention by cvm.studentAttention.collectAsState()
    var setupMode by rememberSaveable { mutableStateOf(SetupMode.NEW) }
    var settingsReturn by rememberSaveable { mutableStateOf(Screen.HOME) }

    val isStudent = memberships.any { it.role == Role.STUDENT }
    val isStaff = memberships.any { it.role != Role.STUDENT } || isSuperAdmin
    val home = when {
        isStudent -> Screen.STUDENT
        isStaff -> Screen.START
        else -> Screen.HOME
    }
    val goHome = { vm.screen = home }
    val goStart = {
        cvm.closeAll()
        vm.screen = Screen.START
    }
    val backFromClass = { vm.screen = if (cvm.closeClass()) Screen.SCHOOL else Screen.START }
    val openSettings = {
        settingsReturn = vm.screen
        vm.screen = Screen.SETTINGS
    }
    val openSetup = { mode: SetupMode ->
        cvm.clearError()
        setupMode = mode
        vm.screen = Screen.SETUP
    }

    // Follow joining, leaving and being removed. Student devices stay on their inbox.
    LaunchedEffect(memberships, vm.screen, openClass, openSchool, isSuperAdmin) {
        val screen = vm.screen
        val target = when {
            screen == Screen.HOME && home != Screen.HOME -> home
            screen == Screen.STUDENT && !isStudent -> home
            screen == Screen.START && !isStaff -> home
            isStudent && screen !in setOf(Screen.STUDENT, Screen.SETUP, Screen.SETTINGS, Screen.PERMISSIONS) ->
                Screen.STUDENT
            screen in setOf(Screen.CLASS, Screen.MANAGE) && openClass == null ->
                if (openSchool != null) Screen.SCHOOL else home
            screen == Screen.SCHOOL && openSchool == null -> home
            else -> null
        }
        if (target != null) vm.screen = target
    }

    Crossfade(targetState = vm.screen, label = "screen") { screen ->
        when (screen) {
            Screen.HOME -> HomeScreen(
                showClassSetup = cvm.isConfigured,
                onSetupClick = { openSetup(SetupMode.NEW) },
                onSettings = openSettings,
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
                mode = setupMode,
                onBack = goHome,
                onDone = {
                    vm.screen = when {
                        cvm.current.value != null -> Screen.CLASS
                        cvm.openSchool.value != null -> Screen.SCHOOL
                        LocalSetup.isStudentDevice && !DevicePermissions.allGranted(context) -> Screen.PERMISSIONS
                        LocalSetup.isStudentDevice -> Screen.STUDENT
                        else -> Screen.START
                    }
                },
            )
            Screen.STUDENT -> StudentClassScreen(
                cvm,
                onJoinAnother = { openSetup(SetupMode.ADD_STUDENT) },
                onSettings = openSettings,
                onPermissions = { vm.screen = Screen.PERMISSIONS },
            )
            Screen.SETTINGS -> {
                val back = { vm.screen = settingsReturn }
                BackHandler(onBack = back)
                SettingsScreen(
                    onBack = back,
                    onPermissions = if (isStudent) {
                        { vm.screen = Screen.PERMISSIONS }
                    } else {
                        null
                    },
                )
            }
            Screen.PERMISSIONS -> {
                BackHandler(onBack = goHome)
                PermissionsScreen(onDone = goHome, onBack = goHome)
            }
            Screen.START -> StartScreen(
                cvm,
                onOpenClass = { cvm.openClass(it); vm.screen = Screen.CLASS },
                onOpenSchool = { cvm.openSchool(it); vm.screen = Screen.SCHOOL },
                onAdd = { openSetup(SetupMode.ADD_STAFF) },
                onWifiMode = { vm.screen = Screen.SEND },
                onSettings = openSettings,
            )
            Screen.SCHOOL -> {
                BackHandler(onBack = goStart)
                SchoolScreen(
                    cvm,
                    onOpenClass = { cvm.openSchoolClass(it); vm.screen = Screen.CLASS },
                    onBack = goStart,
                    onWifiMode = { vm.screen = Screen.SEND },
                )
            }
            Screen.CLASS -> if (openClass != null) {
                BackHandler(onBack = backFromClass)
                TeacherClassScreen(
                    cvm,
                    onSettings = { cvm.clearError(); vm.screen = Screen.MANAGE },
                    onWifiMode = { vm.screen = Screen.SEND },
                    onBack = backFromClass,
                )
            } else {
                Unit
            }
            Screen.MANAGE -> {
                val backToClass = { vm.screen = Screen.CLASS }
                BackHandler(onBack = backToClass)
                ManageClassScreen(cvm, onBack = backToClass)
            }
        }
    }

    // Not on top of "Eyes on me".
    UpdatePrompt(allowed = studentAttention == null)

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
