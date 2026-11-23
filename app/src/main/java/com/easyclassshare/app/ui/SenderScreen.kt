package com.easyclassshare.app.ui

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easyclassshare.app.ClassShareViewModel
import com.easyclassshare.app.R
import com.easyclassshare.app.data.LinkUtils
import com.easyclassshare.app.data.ShareMessage
import com.easyclassshare.app.ui.components.BigButton
import com.easyclassshare.app.ui.components.KidTopBar
import com.easyclassshare.app.ui.theme.Berry
import com.easyclassshare.app.ui.theme.BerryLight
import com.easyclassshare.app.ui.theme.Ink
import com.easyclassshare.app.ui.theme.Leaf
import com.easyclassshare.app.ui.theme.Outline
import com.easyclassshare.app.ui.theme.SendOrange
import kotlinx.coroutines.delay

@Composable
fun SenderScreen(vm: ClassShareViewModel, onBack: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val room = vm.room
    val roomName = stringResource(room.label)
    val result = vm.sendResult

    // Hide the "Sent!" banner after a few seconds.
    LaunchedEffect(result) {
        if (result != null) {
            delay(3500)
            vm.clearSendResult(result)
        }
    }

    Scaffold(
        topBar = { KidTopBar(stringResource(R.string.send_title), room, SendOrange, Ink, onBack) },
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
                OutlinedTextField(
                    value = vm.senderName,
                    onValueChange = vm::updateSenderName,
                    label = { Text(stringResource(R.string.send_name_label)) },
                    placeholder = { Text(stringResource(R.string.send_name_hint)) },
                    leadingIcon = { Text("🧑‍🏫", fontSize = 24.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Text(stringResource(R.string.send_what_label), style = MaterialTheme.typography.titleLarge)
            }
            item {
                OutlinedTextField(
                    value = vm.draft,
                    onValueChange = vm::updateDraft,
                    placeholder = { Text(stringResource(R.string.send_what_hint)) },
                    minLines = 4,
                    shape = RoundedCornerShape(20.dp),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    supportingText = {
                        Text(
                            stringResource(
                                R.string.char_counter,
                                vm.draft.length,
                                ShareMessage.MAX_CONTENT_LENGTH,
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { clipboard.getText()?.text?.let(vm::updateDraft) },
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                    ) { Text(stringResource(R.string.paste), style = MaterialTheme.typography.titleMedium) }
                    OutlinedButton(
                        onClick = { vm.updateDraft("") },
                        enabled = vm.draft.isNotEmpty(),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                    ) { Text(stringResource(R.string.clear), style = MaterialTheme.typography.titleMedium) }
                }
            }
            if (vm.draft.isNotBlank()) {
                item {
                    val hint = if (LinkUtils.looksLikeLink(vm.draft)) R.string.detected_link else R.string.detected_text
                    Text(stringResource(hint), style = MaterialTheme.typography.bodyLarge)
                }
            }
            item {
                BigButton(
                    text = if (vm.isSending) {
                        stringResource(R.string.sending)
                    } else {
                        stringResource(R.string.send_button, room.emoji, roomName)
                    },
                    onClick = vm::sendDraft,
                    containerColor = Leaf,
                    contentColor = Color.White,
                    enabled = vm.draft.isNotBlank() && !vm.isSending,
                )
            }
            if (result != null) {
                item { ResultBanner(result.success, room.emoji, roomName) }
            }
            if (vm.sent.isNotEmpty()) {
                item {
                    Text(stringResource(R.string.sent_history_title), style = MaterialTheme.typography.titleLarge)
                }
                items(vm.sent, key = { it.id }) { message ->
                    SentCard(message, enabled = !vm.isSending, onResend = { vm.resend(message) })
                }
            }
        }
    }
}

@Composable
private fun ResultBanner(success: Boolean, emoji: String, roomName: String) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (success) Leaf else BerryLight,
            contentColor = if (success) Color.White else Berry,
        ),
    ) {
        Text(
            if (success) stringResource(R.string.send_ok, emoji, roomName) else stringResource(R.string.send_failed),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth().padding(18.dp),
        )
    }
}

@Composable
private fun SentCard(message: ShareMessage, enabled: Boolean, onResend: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White, contentColor = Ink),
        border = BorderStroke(1.dp, Outline),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                (if (message.isLink) "🔗 " else "💬 ") + message.content,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            OutlinedButton(onClick = onResend, enabled = enabled, shape = RoundedCornerShape(16.dp)) {
                Text(stringResource(R.string.send_again), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
