package com.easyclassshare.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.easyclassshare.app.ClassShareViewModel
import com.easyclassshare.app.R
import com.easyclassshare.app.data.ShareMessage
import com.easyclassshare.app.ui.components.AppTopBar
import com.easyclassshare.app.ui.components.BigButton
import com.easyclassshare.app.ui.components.Notice
import com.easyclassshare.app.ui.components.Panel
import com.easyclassshare.app.ui.components.SoftButton
import com.easyclassshare.app.ui.components.Tone
import kotlinx.coroutines.delay

/** Same-Wi-Fi mode: send a link to every tablet on the network. */
@Composable
fun SenderScreen(vm: ClassShareViewModel, onBack: () -> Unit) {
    val result = vm.sendResult

    // Hide the "Sent" message after a few seconds.
    LaunchedEffect(result) {
        if (result != null) {
            delay(3500)
            vm.clearSendResult(result)
        }
    }

    Scaffold(topBar = { AppTopBar(stringResource(R.string.send_title), onBack) }) { padding ->
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
                KidTextField(
                    value = vm.senderName,
                    onValueChange = vm::updateSenderName,
                    label = stringResource(R.string.send_name_label),
                    icon = Icons.Filled.Person,
                    placeholder = stringResource(R.string.send_name_hint),
                )
            }
            item {
                ShareComposer(
                    draft = vm.draft,
                    onDraftChange = vm::updateDraft,
                    sendLabel = stringResource(if (vm.isSending) R.string.sending else R.string.send_button),
                    sending = vm.isSending,
                    onSend = vm::sendDraft,
                )
            }
            if (result != null) {
                item {
                    if (result.success) {
                        Notice(stringResource(R.string.send_ok), Tone.GOOD, Icons.Filled.CheckCircle)
                    } else {
                        Notice(stringResource(R.string.send_failed), Tone.BAD, Icons.Filled.Warning)
                    }
                }
            }
            if (vm.sent.isNotEmpty()) {
                item { Text(stringResource(R.string.sent_history_title), style = MaterialTheme.typography.titleLarge) }
                items(vm.sent, key = { it.id }) { message ->
                    SentCard(message, enabled = !vm.isSending, onResend = { vm.resend(message) })
                }
            }
        }
    }
}

@Composable
private fun SentCard(message: ShareMessage, enabled: Boolean, onResend: () -> Unit) {
    Panel {
        Text(
            message.content,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Row {
            SoftButton(stringResource(R.string.send_again), onResend, icon = Icons.Filled.Refresh, enabled = enabled)
        }
    }
}

/** Text box with Paste/Clear and the big send button; shared by both send screens. */
@Composable
internal fun ShareComposer(
    draft: String,
    onDraftChange: (String) -> Unit,
    sendLabel: String,
    sending: Boolean,
    onSend: () -> Unit,
) {
    Panel {
        Text(stringResource(R.string.send_what_label), style = MaterialTheme.typography.titleMedium)
        MessageField(draft, onDraftChange)
        PasteClearRow(draft, onDraftChange)
        BigButton(
            text = sendLabel,
            onClick = onSend,
            icon = Icons.AutoMirrored.Filled.Send,
            enabled = draft.isNotBlank() && !sending,
        )
    }
}
