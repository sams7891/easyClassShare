package com.easyclassshare.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.easyclassshare.app.ClassViewModel
import com.easyclassshare.app.R
import com.easyclassshare.app.data.ShareMessage
import com.easyclassshare.app.online.Membership
import com.easyclassshare.app.ui.components.AppTopBar
import com.easyclassshare.app.ui.components.BigButton
import com.easyclassshare.app.ui.components.Notice
import com.easyclassshare.app.ui.components.Panel
import com.easyclassshare.app.ui.components.SoftButton
import com.easyclassshare.app.ui.components.Tone
import com.easyclassshare.app.ui.components.drawableIcon
import com.easyclassshare.app.ui.theme.Brick
import com.easyclassshare.app.ui.theme.Chalk
import com.easyclassshare.app.ui.theme.InkSoft
import kotlinx.coroutines.delay
import java.util.Date

/** What a student tablet shows after the one-time setup: the class inbox. */
@Composable
fun StudentClassScreen(cvm: ClassViewModel) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val membership by cvm.membership.collectAsState()
    val className by cvm.className.collectAsState()
    val messages by cvm.messages.collectAsState()
    val soundAllowed by cvm.mySoundAllowed.collectAsState()
    val listState = rememberLazyListState()
    val copiedText = stringResource(R.string.copied)
    val openFailedText = stringResource(R.string.could_not_open)
    var showLeave by rememberSaveable { mutableStateOf(false) }

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

    LaunchedEffect(messages.firstOrNull()?.id) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(0)
    }

    val title = className ?: membership?.className?.ifBlank { null } ?: stringResource(R.string.class_default_name)
    Scaffold(topBar = { AppTopBar(title, onBack = null, subtitle = membership?.name) }) { padding ->
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
            if (messages.isEmpty()) {
                item(key = "empty") { EmptyInbox() }
            } else {
                itemsIndexed(messages, key = { _, m -> m.id }) { index, message ->
                    ReceivedCard(
                        message = message,
                        isNewest = index == 0,
                        onOpen = { openLink(context, message.content, openFailedText) },
                        onCopy = {
                            clipboard.setText(AnnotatedString(message.content))
                            Toast.makeText(context, copiedText, Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            item(key = "leave") {
                TextButton(onClick = { cvm.clearError(); showLeave = true }) {
                    Text(stringResource(R.string.student_leave), style = MaterialTheme.typography.bodyMedium, color = InkSoft)
                }
            }
        }
    }

    if (showLeave) {
        var code by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showLeave = false },
            title = { Text(stringResource(R.string.student_leave_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.student_leave_text))
                    CodeField(code, { code = it }, stringResource(R.string.setup_teacher_code))
                    ErrorText(cvm.error)
                }
            },
            confirmButton = {
                TextButton(onClick = { cvm.leave(code) }, enabled = !cvm.busy && code.isNotBlank()) {
                    Text(stringResource(R.string.leave_button), color = Brick)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeave = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

/** A teacher's class: share a link, switch sound, manage the class, switch to another class. */
@Composable
fun TeacherClassScreen(
    cvm: ClassViewModel,
    onManage: () -> Unit,
    onAddClass: () -> Unit,
    onWifiMode: () -> Unit,
) {
    val membership by cvm.membership.collectAsState()
    val memberships by cvm.memberships.collectAsState()
    val className by cvm.className.collectAsState()
    val messages by cvm.messages.collectAsState()
    val sound by cvm.sound.collectAsState()
    val devices by cvm.devices.collectAsState()
    val result = cvm.sendResult
    var showClasses by remember { mutableStateOf(false) }

    LaunchedEffect(result) {
        if (result != null) {
            delay(3500)
            cvm.clearSendResult(result)
        }
    }

    val title = className ?: membership?.className?.ifBlank { null } ?: stringResource(R.string.class_default_name)
    Scaffold(
        topBar = {
            Box {
                AppTopBar(
                    title = title,
                    onBack = null,
                    subtitle = stringResource(R.string.teacher_devices_summary, devices.count { it.online }, devices.size),
                    onTitleClick = { showClasses = true },
                )
                ClassMenu(
                    expanded = showClasses,
                    classes = memberships,
                    activeClassId = membership?.classId,
                    onDismiss = { showClasses = false },
                    onPick = { showClasses = false; cvm.switchClass(it) },
                    onAdd = { showClasses = false; onAddClass() },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                ShareComposer(
                    draft = cvm.draft,
                    onDraftChange = cvm::updateDraft,
                    sendLabel = stringResource(if (cvm.busy) R.string.sending else R.string.send_to_class),
                    sending = cvm.busy,
                    onSend = cvm::send,
                )
            }
            if (result != null) {
                item {
                    if (result.success) {
                        Notice(stringResource(R.string.send_class_ok), Tone.GOOD, Icons.Filled.CheckCircle)
                    } else {
                        Notice(stringResource(R.string.send_class_failed), Tone.BAD, Icons.Filled.Warning)
                    }
                }
            }
            item {
                SoundPanel(
                    allowed = sound.classWideAllowed,
                    enabled = !cvm.busy,
                    onMuteAll = { cvm.setClassSound(false) },
                    onAllowAll = { cvm.setClassSound(true) },
                )
            }
            item { ErrorText(cvm.error) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SoftButton(
                        stringResource(R.string.manage_class),
                        onManage,
                        modifier = Modifier.weight(1f),
                        icon = drawableIcon(R.drawable.ic_group),
                    )
                }
            }
            if (messages.isNotEmpty()) {
                item { Text(stringResource(R.string.teacher_recent), style = MaterialTheme.typography.titleLarge) }
                items(messages.take(RECENT_COUNT), key = { it.id }) { message -> SentLine(message) }
            }
            item {
                TextButton(onClick = onWifiMode) {
                    Text(stringResource(R.string.teacher_wifi_mode), color = InkSoft)
                }
            }
        }
    }
}

private const val RECENT_COUNT = 10

/** Drop-down list of the teacher's classes, plus "Add a class". */
@Composable
private fun ClassMenu(
    expanded: Boolean,
    classes: List<Membership>,
    activeClassId: String?,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    onAdd: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        Text(
            stringResource(R.string.classes_title),
            style = MaterialTheme.typography.bodyMedium,
            color = InkSoft,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        for (m in classes) {
            DropdownMenuItem(
                text = {
                    Text(
                        m.className.ifBlank { stringResource(R.string.class_default_name) },
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                leadingIcon = {
                    if (m.classId == activeClassId) Icon(Icons.Filled.Check, contentDescription = null, tint = Chalk)
                },
                onClick = { onPick(m.classId) },
            )
        }
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(stringResource(R.string.classes_add), style = MaterialTheme.typography.titleMedium) },
            leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
            onClick = onAdd,
        )
    }
}

/** Current class-wide sound state with "Mute all" / "Sound on". */
@Composable
internal fun SoundPanel(allowed: Boolean, enabled: Boolean, onMuteAll: () -> Unit, onAllowAll: () -> Unit) {
    Panel {
        Text(stringResource(R.string.sound_title), style = MaterialTheme.typography.titleMedium)
        if (allowed) {
            Notice(stringResource(R.string.sound_class_on), Tone.GOOD, drawableIcon(R.drawable.ic_volume_on))
        } else {
            Notice(stringResource(R.string.sound_class_off), Tone.BAD, drawableIcon(R.drawable.ic_volume_off))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigButton(
                text = stringResource(R.string.sound_mute_all),
                onClick = onMuteAll,
                modifier = Modifier.weight(1f),
                icon = drawableIcon(R.drawable.ic_volume_off),
                containerColor = Brick,
                enabled = enabled,
            )
            BigButton(
                text = stringResource(R.string.sound_allow_all),
                onClick = onAllowAll,
                modifier = Modifier.weight(1f),
                icon = drawableIcon(R.drawable.ic_volume_on),
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun SentLine(message: ShareMessage) {
    val context = LocalContext.current
    val time = remember(message.timestamp) { DateFormat.getTimeFormat(context).format(Date(message.timestamp)) }
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text("${message.senderName} · $time", style = MaterialTheme.typography.bodyMedium, color = InkSoft)
        Text(
            message.content,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
