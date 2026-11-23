package com.easyclassshare.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easyclassshare.app.ClassShareViewModel
import com.easyclassshare.app.R
import com.easyclassshare.app.data.ClassRoom
import com.easyclassshare.app.data.LinkUtils
import com.easyclassshare.app.data.ShareMessage
import com.easyclassshare.app.ui.components.BigButton
import com.easyclassshare.app.ui.components.KidTopBar
import com.easyclassshare.app.ui.components.Pulsing
import com.easyclassshare.app.ui.theme.Berry
import com.easyclassshare.app.ui.theme.BerryLight
import com.easyclassshare.app.ui.theme.Ink
import com.easyclassshare.app.ui.theme.Leaf
import com.easyclassshare.app.ui.theme.Outline
import com.easyclassshare.app.ui.theme.ReceiveBlue
import com.easyclassshare.app.ui.theme.SendOrange
import com.easyclassshare.app.ui.theme.SkyLight
import com.easyclassshare.app.ui.theme.SunnyLight
import java.util.Date

@Composable
fun ReceiverScreen(vm: ClassShareViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val view = LocalView.current
    val listState = rememberLazyListState()
    val copiedText = stringResource(R.string.copied)
    val openFailedText = stringResource(R.string.could_not_open)

    // Listen (and keep the screen awake) only while this screen is shown.
    DisposableEffect(Unit) {
        vm.startListening()
        view.keepScreenOn = true
        onDispose {
            vm.stopListening()
            view.keepScreenOn = false
        }
    }

    // Jump to the newest message when one arrives.
    LaunchedEffect(vm.received.size) {
        if (vm.received.isNotEmpty()) listState.animateScrollToItem(0)
    }

    Scaffold(
        topBar = { KidTopBar(stringResource(R.string.receive_title), vm.room, ReceiveBlue, Color.White, onBack) },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "status") {
                ListeningBanner(vm.isListening, vm.receiveError, vm.room, onRetry = vm::startListening)
            }
            if (vm.received.isEmpty()) {
                item(key = "empty") { EmptyInbox() }
            } else {
                itemsIndexed(vm.received, key = { _, m -> m.id }) { index, message ->
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
        }
    }
}

private fun openLink(context: Context, text: String, errorText: String) {
    val url = LinkUtils.normalize(text) ?: return
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, errorText, Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun ListeningBanner(isListening: Boolean, hasError: Boolean, room: ClassRoom, onRetry: () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (hasError) BerryLight else SkyLight,
            contentColor = if (hasError) Berry else Ink,
        ),
    ) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            if (hasError) Text("😕", fontSize = 40.sp) else Pulsing { Text("👂", fontSize = 40.sp) }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                when {
                    hasError -> {
                        Text(stringResource(R.string.listen_error), style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = onRetry) {
                            Text(stringResource(R.string.try_again), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    isListening -> {
                        Text(stringResource(R.string.listening_title), style = MaterialTheme.typography.titleLarge)
                        Text(
                            stringResource(R.string.listening_subtitle, room.emoji, stringResource(room.label)),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    else -> Text(stringResource(R.string.getting_ready), style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}

@Composable
private fun EmptyInbox() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("📭", fontSize = 88.sp)
        Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(R.string.empty_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ReceivedCard(
    message: ShareMessage,
    isNewest: Boolean,
    onOpen: () -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val time = remember(message.timestamp) { DateFormat.getTimeFormat(context).format(Date(message.timestamp)) }
    val defaultSender = stringResource(R.string.default_sender)

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isNewest) SunnyLight else Color.White,
            contentColor = Ink,
        ),
        border = if (isNewest) BorderStroke(3.dp, SendOrange) else BorderStroke(1.dp, Outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (message.isLink) "🔗" else "💬", fontSize = 32.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(message.senderName.ifBlank { defaultSender }, style = MaterialTheme.typography.titleMedium)
                    Text(time, style = MaterialTheme.typography.bodyMedium, color = Ink.copy(alpha = 0.7f))
                }
                if (isNewest) {
                    Surface(shape = RoundedCornerShape(50), color = SendOrange, contentColor = Ink) {
                        Text(
                            stringResource(R.string.new_badge),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            Text(
                message.content,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 8,
                overflow = TextOverflow.Ellipsis,
            )
            if (message.isLink) {
                BigButton(stringResource(R.string.open_link), onOpen, ReceiveBlue, Color.White)
            } else {
                BigButton(stringResource(R.string.copy), onCopy, Leaf, Color.White)
            }
        }
    }
}
