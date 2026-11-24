package com.easyclassshare.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.easyclassshare.app.ClassViewModel
import com.easyclassshare.app.R
import com.easyclassshare.app.data.LinkUtils
import com.easyclassshare.app.data.ShareMessage
import com.easyclassshare.app.ui.components.BigButton
import com.easyclassshare.app.ui.components.KidTopBar
import com.easyclassshare.app.ui.theme.Berry
import com.easyclassshare.app.ui.theme.BerryLight
import com.easyclassshare.app.ui.theme.ClassPurple
import com.easyclassshare.app.ui.theme.Ink
import com.easyclassshare.app.ui.theme.Leaf
import com.easyclassshare.app.ui.theme.Outline
import com.easyclassshare.app.ui.theme.PurpleLight
import com.easyclassshare.app.ui.theme.SkyLight
import kotlinx.coroutines.delay
import java.util.Date

/** What a student tablet shows after the one-time setup: the class inbox. */
@Composable
fun StudentClassScreen(cvm: ClassViewModel) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
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

    Scaffold(
        topBar = { KidTopBar("🏫 " + (className ?: stringResource(R.string.class_default_name)), ClassPurple, Color.White, null) },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "sound") {
                if (soundAllowed) {
                    InfoCard("🔊", stringResource(R.string.student_sound_on), SkyLight)
                } else {
                    InfoCard("🔇", stringResource(R.string.student_sound_off), BerryLight)
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
                    Text(stringResource(R.string.student_leave), style = MaterialTheme.typography.bodyMedium, color = Ink.copy(alpha = 0.6f))
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
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.uppercase().take(12) },
                        label = { Text(stringResource(R.string.setup_teacher_code)) },
                        singleLine = true,
                    )
                    ErrorText(cvm.error)
                }
            },
            confirmButton = {
                TextButton(onClick = { cvm.leave(code) }, enabled = !cvm.busy && code.isNotBlank()) {
                    Text(stringResource(R.string.leave_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeave = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

/** What a teacher sees after the one-time setup: send to the class, quick sound switch, manage. */
@Composable
fun TeacherClassScreen(cvm: ClassViewModel, onManage: () -> Unit, onWifiMode: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val className by cvm.className.collectAsState()
    val messages by cvm.messages.collectAsState()
    val sound by cvm.sound.collectAsState()
    val devices by cvm.devices.collectAsState()
    val result = cvm.sendResult

    LaunchedEffect(result) {
        if (result != null) {
            delay(3500)
            cvm.clearSendResult(result)
        }
    }

    Scaffold(
        topBar = { KidTopBar("🏫 " + (className ?: stringResource(R.string.class_default_name)), ClassPurple, Color.White, null) },
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
                val online = devices.count { it.online }
                Text(
                    stringResource(R.string.teacher_devices_summary, online, devices.size),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            item {
                OutlinedTextField(
                    value = cvm.draft,
                    onValueChange = cvm::updateDraft,
                    label = { Text(stringResource(R.string.send_what_label)) },
                    placeholder = { Text(stringResource(R.string.send_what_hint)) },
                    minLines = 3,
                    shape = RoundedCornerShape(20.dp),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { clipboard.getText()?.text?.let(cvm::updateDraft) },
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                    ) { Text(stringResource(R.string.paste), style = MaterialTheme.typography.titleMedium) }
                    OutlinedButton(
                        onClick = { cvm.updateDraft("") },
                        enabled = cvm.draft.isNotEmpty(),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                    ) { Text(stringResource(R.string.clear), style = MaterialTheme.typography.titleMedium) }
                }
            }
            item {
                BigButton(
                    text = stringResource(if (cvm.busy) R.string.sending else R.string.send_to_class),
                    onClick = cvm::send,
                    containerColor = Leaf,
                    contentColor = Color.White,
                    enabled = cvm.draft.isNotBlank() && !cvm.busy,
                )
            }
            if (result != null) {
                item {
                    InfoCard(
                        if (result.success) "🎉" else "😕",
                        stringResource(if (result.success) R.string.send_class_ok else R.string.send_class_failed),
                        if (result.success) SkyLight else BerryLight,
                    )
                }
            }
            item {
                SoundSwitchRow(
                    allowed = sound.classWideAllowed,
                    enabled = !cvm.busy,
                    onMuteAll = { cvm.setClassSound(false) },
                    onAllowAll = { cvm.setClassSound(true) },
                )
            }
            item { ErrorText(cvm.error) }
            item {
                BigButton(stringResource(R.string.manage_class), onManage, ClassPurple, Color.White)
            }
            item {
                OutlinedButton(
                    onClick = onWifiMode,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) { Text(stringResource(R.string.teacher_wifi_mode), style = MaterialTheme.typography.titleMedium) }
            }
            if (messages.isNotEmpty()) {
                item { Text(stringResource(R.string.sent_history_title), style = MaterialTheme.typography.titleLarge) }
                items(messages, key = { it.id }) { message -> ClassMessageCard(message) }
            }
        }
    }
}

/** "Mute everyone" / "Sound on for everyone" buttons with the current class-wide state. */
@Composable
internal fun SoundSwitchRow(allowed: Boolean, enabled: Boolean, onMuteAll: () -> Unit, onAllowAll: () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = PurpleLight, contentColor = Ink),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(if (allowed) R.string.sound_class_on else R.string.sound_class_off),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigButton(
                    text = stringResource(R.string.sound_mute_all),
                    onClick = onMuteAll,
                    containerColor = Berry,
                    contentColor = Color.White,
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                )
                BigButton(
                    text = stringResource(R.string.sound_allow_all),
                    onClick = onAllowAll,
                    containerColor = Leaf,
                    contentColor = Color.White,
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                )
            }
        }
    }
}

@Composable
private fun ClassMessageCard(message: ShareMessage) {
    val context = LocalContext.current
    val time = remember(message.timestamp) { DateFormat.getTimeFormat(context).format(Date(message.timestamp)) }
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White, contentColor = Ink),
        border = BorderStroke(1.dp, Outline),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${message.senderName} · $time", style = MaterialTheme.typography.bodyMedium, color = Ink.copy(alpha = 0.7f))
            Text(
                (if (LinkUtils.looksLikeLink(message.content)) "🔗 " else "💬 ") + message.content,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
