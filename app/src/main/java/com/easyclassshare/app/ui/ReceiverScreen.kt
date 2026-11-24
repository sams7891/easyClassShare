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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.easyclassshare.app.ClassShareViewModel
import com.easyclassshare.app.R
import com.easyclassshare.app.data.LinkUtils
import com.easyclassshare.app.data.ShareMessage
import com.easyclassshare.app.ui.components.AppTopBar
import com.easyclassshare.app.ui.components.BigButton
import com.easyclassshare.app.ui.components.IconBadge
import com.easyclassshare.app.ui.components.Notice
import com.easyclassshare.app.ui.components.Pulsing
import com.easyclassshare.app.ui.components.SoftButton
import com.easyclassshare.app.ui.components.Tone
import com.easyclassshare.app.ui.components.drawableIcon
import com.easyclassshare.app.ui.theme.Ink
import com.easyclassshare.app.ui.theme.InkSoft
import com.easyclassshare.app.ui.theme.Line
import com.easyclassshare.app.ui.theme.Pencil
import com.easyclassshare.app.ui.theme.Sky
import com.easyclassshare.app.ui.theme.SkyLight
import java.util.Date

/** Same-Wi-Fi mode: wait for links from a teacher on the same network. */
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

    Scaffold(topBar = { AppTopBar(stringResource(R.string.receive_title), onBack) }) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "status") {
                ListeningBanner(vm.isListening, vm.receiveError, onRetry = vm::startListening)
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

internal fun openLink(context: Context, text: String, errorText: String) {
    val url = LinkUtils.normalize(text) ?: return
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, errorText, Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun ListeningBanner(isListening: Boolean, hasError: Boolean, onRetry: () -> Unit) {
    if (hasError) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Notice(stringResource(R.string.listen_error), Tone.BAD, Icons.Filled.Warning)
            SoftButton(stringResource(R.string.try_again), onRetry)
        }
        return
    }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Pulsing { IconBadge(Icons.Filled.Notifications, Sky, SkyLight) }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                stringResource(if (isListening) R.string.listening_title else R.string.getting_ready),
                style = MaterialTheme.typography.titleMedium,
            )
            if (isListening) {
                Text(stringResource(R.string.listening_subtitle), style = MaterialTheme.typography.bodyMedium, color = InkSoft)
            }
        }
    }
}

@Composable
internal fun EmptyInbox() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconBadge(drawableIcon(R.drawable.ic_link), InkSoft, Line, size = 72)
        Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.empty_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = InkSoft,
            textAlign = TextAlign.Center,
        )
    }
}

/** One received link or message. The newest one is highlighted and has a "New" label. */
@Composable
internal fun ReceivedCard(
    message: ShareMessage,
    isNewest: Boolean,
    onOpen: () -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val time = remember(message.timestamp) { DateFormat.getTimeFormat(context).format(Date(message.timestamp)) }
    val sender = message.senderName.ifBlank { stringResource(R.string.default_sender) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White, contentColor = Ink),
        border = BorderStroke(if (isNewest) 2.dp else 1.dp, if (isNewest) Pencil else Line),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$sender · $time",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSoft,
                    modifier = Modifier.weight(1f),
                )
                if (isNewest) {
                    Surface(shape = RoundedCornerShape(50), color = Pencil, contentColor = Ink) {
                        Text(
                            stringResource(R.string.new_badge),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
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
                BigButton(stringResource(R.string.open_link), onOpen, icon = drawableIcon(R.drawable.ic_link), containerColor = Sky)
            } else {
                SoftButton(stringResource(R.string.copy), onCopy, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
