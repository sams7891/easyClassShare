package com.easyclassshare.app.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.easyclassshare.app.ClassViewModel
import com.easyclassshare.app.R
import com.easyclassshare.app.online.Attention
import com.easyclassshare.app.service.AttentionOverlay
import com.easyclassshare.app.ui.components.AppTopBar
import com.easyclassshare.app.ui.components.Notice
import com.easyclassshare.app.ui.components.Panel
import com.easyclassshare.app.ui.components.SoftButton
import com.easyclassshare.app.ui.components.Tone
import com.easyclassshare.app.ui.components.drawableIcon
import com.easyclassshare.app.ui.theme.Brick
import com.easyclassshare.app.ui.theme.Ink
import com.easyclassshare.app.ui.theme.InkSoft
import kotlinx.coroutines.flow.distinctUntilChanged

/** What a student device shows after the one-time setup: one inbox for all its classes. */
@Composable
fun StudentClassScreen(cvm: ClassViewModel, onJoinAnother: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val classes by cvm.studentClasses.collectAsState()
    val myName by cvm.myDeviceName.collectAsState()
    val inbox by cvm.inbox.collectAsState()
    val soundAllowed by cvm.mySoundAllowed.collectAsState()
    val attention by cvm.studentAttention.collectAsState()
    val listState = rememberLazyListState()
    val copiedText = stringResource(R.string.copied)
    val openFailedText = stringResource(R.string.could_not_open)
    var showLeave by rememberSaveable { mutableStateOf(false) }
    var canLock by remember { mutableStateOf(AttentionOverlay.canShow(context)) }
    val severalClasses = classes.size > 1

    // Checked again when coming back from the "Display over other apps" settings page.
    LifecycleResumeEffect(Unit) {
        canLock = AttentionOverlay.canShow(context)
        onPauseOrDispose { }
    }

    // New links show up as notifications while the app is in the background.
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(inbox.firstOrNull()?.message?.id) {
        if (inbox.isNotEmpty()) listState.animateScrollToItem(0)
    }

    // A text message counts as seen once it has been on screen.
    LaunchedEffect(listState, inbox) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.map { it.key } }
            .distinctUntilChanged()
            .collect { keys ->
                inbox.filter { !it.message.isLink && itemKey(it.classId, it.message.id) in keys }
                    .forEach { cvm.markOpened(it.classId, it.message.id) }
            }
    }

    val title = when {
        severalClasses -> stringResource(R.string.student_classes_title)
        else -> classes.firstOrNull()?.className?.ifBlank { null } ?: stringResource(R.string.class_default_name)
    }
    val subtitle = if (severalClasses) {
        classes.joinToString(", ") { it.className.ifBlank { "…" } }
    } else {
        myName ?: classes.firstOrNull()?.name
    }
    Box(Modifier.fillMaxSize()) {
        Scaffold(topBar = { AppTopBar(title, onBack = null, subtitle = subtitle) }) { padding ->
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(key = "sound") {
                    if (soundAllowed) {
                        Notice(stringResource(R.string.student_sound_on), Tone.GOOD, drawableIcon(R.drawable.ic_volume_on))
                    } else {
                        Notice(stringResource(R.string.student_sound_off), Tone.BAD, drawableIcon(R.drawable.ic_volume_off))
                    }
                }
                if (!canLock) {
                    item(key = "lock") { LockPermissionPanel() }
                }
                if (inbox.isEmpty()) {
                    item(key = "empty") { EmptyInbox() }
                } else {
                    itemsIndexed(inbox, key = { _, m -> itemKey(m.classId, m.message.id) }) { index, entry ->
                        val message = entry.message
                        ReceivedCard(
                            message = message,
                            isNewest = index == 0,
                            onOpen = {
                                cvm.markOpened(entry.classId, message.id)
                                openLink(context, message.content, openFailedText)
                            },
                            onCopy = {
                                clipboard.setText(AnnotatedString(message.content))
                                Toast.makeText(context, copiedText, Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.animateItem(),
                            classLabel = if (severalClasses) entry.className.ifBlank { null } else null,
                        )
                    }
                }
                item(key = "actions") {
                    Row {
                        TextButton(onClick = { cvm.clearError(); onJoinAnother() }) {
                            Text(stringResource(R.string.student_join_another), style = MaterialTheme.typography.bodyMedium, color = InkSoft)
                        }
                        TextButton(onClick = { cvm.clearError(); showLeave = true }) {
                            Text(stringResource(R.string.student_leave), style = MaterialTheme.typography.bodyMedium, color = InkSoft)
                        }
                    }
                }
            }
        }

        // Inside the app "Eyes on me" always shows; over other apps it needs the overlay permission.
        attention?.let { AttentionScreen(it) }
    }

    if (showLeave && attention == null) {
        var code by rememberSaveable { mutableStateOf("") }
        var classId by rememberSaveable { mutableStateOf(classes.singleOrNull()?.classId) }
        AlertDialog(
            onDismissRequest = { showLeave = false },
            title = { Text(stringResource(R.string.student_leave_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (severalClasses) {
                        Text(stringResource(R.string.student_leave_which))
                        for (m in classes) {
                            Row(
                                Modifier.fillMaxWidth().clickable { classId = m.classId },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = classId == m.classId, onClick = { classId = m.classId })
                                Text(m.className.ifBlank { stringResource(R.string.class_default_name) })
                            }
                        }
                    }
                    Text(stringResource(R.string.student_leave_text))
                    CodeField(code, { code = it }, stringResource(R.string.setup_teacher_code))
                    ErrorText(cvm.error)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { classId?.let { cvm.leaveStudentClass(it, code) { showLeave = false } } },
                    enabled = !cvm.busy && code.isNotBlank() && classId != null,
                ) {
                    Text(stringResource(R.string.leave_button), color = Brick)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeave = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

private fun itemKey(classId: String, messageId: String) = "$classId/$messageId"

@Composable
private fun LockPermissionPanel() {
    val context = LocalContext.current
    Panel {
        Text(stringResource(R.string.lock_permission_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.lock_permission_text),
            style = MaterialTheme.typography.bodyMedium,
            color = InkSoft,
        )
        Row {
            SoftButton(
                stringResource(R.string.lock_permission_button),
                onClick = {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}"),
                    )
                    try {
                        context.startActivity(intent)
                    } catch (e: ActivityNotFoundException) {
                        context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                    }
                },
                icon = drawableIcon(R.drawable.ic_eye),
            )
        }
    }
}

/** Full-screen "Eyes on me" inside the app. Swallows taps and the back button. */
@Composable
private fun AttentionScreen(attention: Attention) {
    BackHandler { }
    Box(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                attention.text,
                color = Color.White,
                fontSize = 40.sp,
                lineHeight = 48.sp,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            if (attention.from.isNotBlank()) {
                Text(
                    attention.from,
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 22.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
