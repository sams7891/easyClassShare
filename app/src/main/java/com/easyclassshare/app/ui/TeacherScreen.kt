package com.easyclassshare.app.ui

import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.easyclassshare.app.ClassViewModel
import com.easyclassshare.app.R
import com.easyclassshare.app.online.DeviceInfo
import com.easyclassshare.app.online.Recipients
import com.easyclassshare.app.online.SentMessage
import com.easyclassshare.app.online.SoundSettings
import com.easyclassshare.app.ui.components.AppTopBar
import com.easyclassshare.app.ui.components.BigButton
import com.easyclassshare.app.ui.components.Notice
import com.easyclassshare.app.ui.components.Panel
import com.easyclassshare.app.ui.components.Tone
import com.easyclassshare.app.ui.components.drawableIcon
import com.easyclassshare.app.ui.theme.Brick
import com.easyclassshare.app.ui.theme.BrickLight
import com.easyclassshare.app.ui.theme.Chalk
import com.easyclassshare.app.ui.theme.Ink
import com.easyclassshare.app.ui.theme.InkSoft
import com.easyclassshare.app.ui.theme.Line
import com.easyclassshare.app.ui.theme.Paper
import com.easyclassshare.app.ui.theme.Sky
import kotlinx.coroutines.delay
import java.util.Date

private val MUTE_MINUTES = listOf(10, 20, 30, 45, 60)

/**
 * A teacher's class: sound and "Eyes on me" at the top, then sharing and the device list.
 * School admins and the super admin see the same screen for any class of a school.
 */
@Composable
fun TeacherClassScreen(
    cvm: ClassViewModel,
    onSettings: () -> Unit,
    onWifiMode: () -> Unit,
    onBack: () -> Unit,
) {
    val membership by cvm.current.collectAsState()
    val className by cvm.className.collectAsState()
    val sound by cvm.sound.collectAsState()
    val devices by cvm.devices.collectAsState()
    val attention by cvm.attention.collectAsState()
    val offset by cvm.serverOffset.collectAsState()
    val now = rememberServerNow(offset)
    var showMore by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val attentionText = stringResource(R.string.attention_default_text)

    val title = className ?: membership?.className?.ifBlank { null } ?: stringResource(R.string.class_default_name)
    val online = stringResource(R.string.teacher_devices_summary, devices.count { it.online }, devices.size)
    val school = membership?.schoolName.orEmpty()
    Scaffold(
        topBar = {
            AppTopBar(
                title = title,
                onBack = onBack,
                subtitle = if (school.isEmpty()) online else "$school · $online",
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.class_settings))
                    }
                    Box {
                        IconButton(onClick = { showMore = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options))
                        }
                        DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.teacher_wifi_mode)) },
                                leadingIcon = { Icon(drawableIcon(R.drawable.ic_wifi), contentDescription = null) },
                                onClick = { showMore = false; onWifiMode() },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding(),
        ) {
            ControlStrip(
                sound = sound,
                now = now,
                offset = offset,
                attentionOn = attention != null,
                enabled = !cvm.busy,
                onSoundOn = { cvm.setClassSound(true) },
                onMute = { cvm.setClassSound(false) },
                onMuteFor = cvm::muteFor,
                onAttention = { on -> cvm.setAttention(on, attentionText) },
            )
            if (cvm.error != null) {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { ErrorText(cvm.error) }
            }
            TabRow(
                selectedTabIndex = tab,
                containerColor = Paper,
                contentColor = Ink,
                indicator = { positions ->
                    TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(positions[tab]), color = Chalk)
                },
                divider = { HorizontalDivider(color = Line) },
            ) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = {
                    Text(stringResource(R.string.tab_share), style = MaterialTheme.typography.titleMedium)
                })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = {
                    Text(
                        stringResource(R.string.tab_tablets, devices.size),
                        style = MaterialTheme.typography.titleMedium,
                    )
                })
            }
            when (tab) {
                0 -> ShareTab(cvm, devices)
                else -> TabletsTab(
                    cvm = cvm,
                    devices = devices,
                    sound = sound,
                    now = now,
                    onSendTo = { uid ->
                        cvm.updateRecipients(Recipients.Selected(setOf(uid)))
                        tab = 0
                    },
                )
            }
        }
    }
}

/** Server time, updated every few seconds, so timed mutes and "last seen" stay current. */
@Composable
private fun rememberServerNow(offset: Long): Long {
    val now by produceState(System.currentTimeMillis() + offset, offset) {
        while (true) {
            value = System.currentTimeMillis() + offset
            delay(5_000)
        }
    }
    return now
}

// ---------------------------------------------------------------- top controls

@Composable
private fun ControlStrip(
    sound: SoundSettings,
    now: Long,
    offset: Long,
    attentionOn: Boolean,
    enabled: Boolean,
    onSoundOn: () -> Unit,
    onMute: () -> Unit,
    onMuteFor: (Int) -> Unit,
    onAttention: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val allowed = sound.classWideAllowed(now)
    val timerEnd = sound.timerEnd(now)
    var showSoundMenu by remember { mutableStateOf(false) }

    val soundValue = when {
        allowed -> stringResource(R.string.sound_state_on)
        timerEnd != null -> stringResource(
            R.string.sound_state_muted_until,
            DateFormat.getTimeFormat(context).format(Date(timerEnd - offset)),
        )
        else -> stringResource(R.string.sound_state_muted)
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.weight(1f)) {
            ControlTile(
                icon = drawableIcon(if (allowed) R.drawable.ic_volume_on else R.drawable.ic_volume_off),
                label = stringResource(R.string.sound_label),
                value = soundValue,
                active = !allowed,
                activeColor = Brick,
                enabled = enabled,
                showArrow = true,
                onClick = { showSoundMenu = true },
            )
            DropdownMenu(expanded = showSoundMenu, onDismissRequest = { showSoundMenu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sound_menu_on)) },
                    leadingIcon = { Icon(drawableIcon(R.drawable.ic_volume_on), contentDescription = null) },
                    onClick = { showSoundMenu = false; onSoundOn() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sound_menu_mute)) },
                    leadingIcon = { Icon(drawableIcon(R.drawable.ic_volume_off), contentDescription = null, tint = Brick) },
                    onClick = { showSoundMenu = false; onMute() },
                )
                HorizontalDivider(color = Line)
                for (minutes in MUTE_MINUTES) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.sound_menu_mute_for, minutes)) },
                        onClick = { showSoundMenu = false; onMuteFor(minutes) },
                    )
                }
            }
        }
        Box(Modifier.weight(1f)) {
            ControlTile(
                icon = drawableIcon(R.drawable.ic_eye),
                label = stringResource(R.string.attention_label),
                value = stringResource(if (attentionOn) R.string.attention_state_on else R.string.attention_state_off),
                active = attentionOn,
                activeColor = Ink,
                enabled = enabled,
                showArrow = false,
                onClick = { onAttention(!attentionOn) },
            )
        }
    }
}

/** A status tile that is also a button: "Sound / On", "Eyes on me / Off". Filled in when active. */
@Composable
private fun ControlTile(
    icon: ImageVector,
    label: String,
    value: String,
    active: Boolean,
    activeColor: Color,
    enabled: Boolean,
    showArrow: Boolean,
    onClick: () -> Unit,
) {
    val background = if (active) activeColor else Color.White
    val content = if (active) Color.White else Ink
    val soft = if (active) Color.White.copy(alpha = 0.8f) else InkSoft
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        color = background,
        contentColor = content,
        border = if (active) null else BorderStroke(1.dp, Line),
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = soft, maxLines = 1)
                Text(
                    value,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (showArrow) Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = soft)
        }
    }
}

// ---------------------------------------------------------------- Share tab

@Composable
private fun ShareTab(cvm: ClassViewModel, devices: List<DeviceInfo>) {
    val sent by cvm.sent.collectAsState()
    val result = cvm.sendResult
    var pickRecipients by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<SentMessage?>(null) }
    val names = remember(devices) { devices.associate { it.uid to it.name } }

    LaunchedEffect(result) {
        if (result != null) {
            delay(3500)
            cvm.clearSendResult(result)
        }
    }

    val recipients = cvm.recipients
    val onlineCount = devices.count { it.online }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "composer") {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.send_to_label),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(end = 10.dp),
                    )
                    Surface(
                        onClick = { pickRecipients = true },
                        shape = RoundedCornerShape(50),
                        color = Paper,
                        border = BorderStroke(1.dp, Line),
                        modifier = Modifier.weight(1f, fill = false),
                    ) {
                        Row(
                            Modifier.padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                recipientsText(recipients, names, onlineCount),
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = InkSoft)
                        }
                    }
                }
                MessageField(cvm.draft, cvm::updateDraft)
                PasteClearRow(cvm.draft, cvm::updateDraft)
                BigButton(
                    text = when {
                        cvm.sending -> stringResource(R.string.sending)
                        recipients is Recipients.Selected ->
                            pluralStringResource(R.plurals.send_to_tablets, recipients.uids.size, recipients.uids.size)
                        recipients == Recipients.OnlineOnly ->
                            pluralStringResource(R.plurals.send_to_online, onlineCount, onlineCount)
                        else -> stringResource(R.string.send_to_class)
                    },
                    onClick = cvm::send,
                    icon = Icons.AutoMirrored.Filled.Send,
                    enabled = cvm.draft.isNotBlank() && !cvm.sending,
                )
            }
        }
        if (result != null) {
            item(key = "result") {
                if (result.success) {
                    Notice(stringResource(R.string.send_class_ok), Tone.GOOD, Icons.Filled.CheckCircle)
                } else {
                    Notice(stringResource(R.string.send_class_failed), Tone.BAD, Icons.Filled.Warning)
                }
            }
        }
        if (sent.isNotEmpty()) {
            item(key = "sent-title") {
                Text(
                    stringResource(R.string.teacher_recent),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(sent, key = { it.id }) { message ->
                SentCard(
                    message = message,
                    devices = devices,
                    names = names,
                    onDelete = { deleting = message },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }

    if (pickRecipients) {
        RecipientDialog(
            devices = devices,
            initial = recipients,
            onDismiss = { pickRecipients = false },
            onDone = { cvm.updateRecipients(it); pickRecipients = false },
        )
    }

    deleting?.let { message ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            text = { Text(stringResource(R.string.confirm_delete_message), style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                TextButton(onClick = { cvm.deleteMessage(message); deleting = null }) {
                    Text(stringResource(R.string.delete_button), color = Brick)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun recipientsText(recipients: Recipients, names: Map<String, String>, onlineCount: Int): String =
    when (recipients) {
        Recipients.Everyone -> stringResource(R.string.audience_class)
        Recipients.OnlineOnly -> stringResource(R.string.audience_online, onlineCount)
        is Recipients.Selected -> audienceText(recipients.uids, names)
    }

@Composable
private fun audienceText(targets: Set<String>?, names: Map<String, String>): String = when {
    targets == null -> stringResource(R.string.audience_class)
    targets.size <= 2 -> targets.map { names[it] ?: stringResource(R.string.audience_removed_tablet) }.joinToString(", ")
    else -> pluralStringResource(R.plurals.audience_tablets, targets.size, targets.size)
}

/** One shared item with who it went to, when, and how many tablets opened it. */
@Composable
private fun SentCard(
    message: SentMessage,
    devices: List<DeviceInfo>,
    names: Map<String, String>,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val time = remember(message.timestamp) {
        val date = Date(message.timestamp)
        if (DateUtils.isToday(message.timestamp)) {
            DateFormat.getTimeFormat(context).format(date)
        } else {
            DateFormat.getMediumDateFormat(context).format(date) + " " + DateFormat.getTimeFormat(context).format(date)
        }
    }
    val audience = message.targets ?: devices.map { it.uid }.toSet()
    val opened = message.openedBy.intersect(audience)
    val missing = audience.filter { it !in opened }.mapNotNull { names[it] }.sorted()
    var expanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Line),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 16.dp, top = 8.dp, end = 4.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${audienceText(message.targets, names)} · $time",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete_button), tint = InkSoft)
                }
            }
            Column(Modifier.padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (message.isLink) Sky else Ink,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (audience.isNotEmpty()) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = missing.isNotEmpty()) { expanded = !expanded },
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(
                                    if (message.isLink) R.string.receipt_opened else R.string.receipt_seen,
                                    opened.size,
                                    audience.size,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (opened.size == audience.size) Chalk else Ink,
                                modifier = Modifier.weight(1f),
                            )
                            if (missing.isNotEmpty()) {
                                Text(
                                    stringResource(if (expanded) R.string.receipt_hide else R.string.receipt_who),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Chalk,
                                )
                            }
                        }
                        LinearProgressIndicator(
                            progress = { opened.size.toFloat() / audience.size },
                            color = Chalk,
                            trackColor = Line,
                            drawStopIndicator = {},
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (expanded && missing.isNotEmpty()) {
                            Text(
                                stringResource(R.string.receipt_missing, missing.joinToString(", ")),
                                style = MaterialTheme.typography.bodyMedium,
                                color = InkSoft,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Pick the whole class, only the online devices, or some devices. */
@Composable
private fun RecipientDialog(
    devices: List<DeviceInfo>,
    initial: Recipients,
    onDismiss: () -> Unit,
    onDone: (Recipients) -> Unit,
) {
    var choice by remember { mutableStateOf(initial) }
    val selected = (choice as? Recipients.Selected)?.uids.orEmpty()
    val onlineCount = devices.count { it.online }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.recipients_title)) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                ChoiceRow(
                    text = stringResource(R.string.audience_class),
                    selected = choice == Recipients.Everyone,
                    onClick = { choice = Recipients.Everyone },
                )
                ChoiceRow(
                    text = stringResource(R.string.audience_online, onlineCount),
                    selected = choice == Recipients.OnlineOnly,
                    onClick = { choice = Recipients.OnlineOnly },
                )
                if (devices.isNotEmpty()) {
                    HorizontalDivider(color = Line, modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        stringResource(R.string.recipients_some),
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkSoft,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                for (device in devices) {
                    val checked = device.uid in selected
                    val toggle = {
                        val uids = if (checked) selected - device.uid else selected + device.uid
                        choice = if (uids.isEmpty()) Recipients.Everyone else Recipients.Selected(uids)
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(onClick = toggle)
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { toggle() },
                            colors = CheckboxDefaults.colors(checkedColor = Chalk),
                        )
                        Text(device.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        OnlineDot(device.online)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDone(choice) }) { Text(stringResource(R.string.done_button)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun ChoiceRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = Chalk),
        )
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

// ---------------------------------------------------------------- Tablets tab

private sealed interface TabletDialog {
    data class Rename(val device: DeviceInfo) : TabletDialog
    data class Remove(val device: DeviceInfo) : TabletDialog
}

@Composable
private fun TabletsTab(
    cvm: ClassViewModel,
    devices: List<DeviceInfo>,
    sound: SoundSettings,
    now: Long,
    onSendTo: (String) -> Unit,
) {
    val codes by cvm.codes.collectAsState()
    var dialog by remember { mutableStateOf<TabletDialog?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (devices.isEmpty()) {
            item(key = "empty") {
                Panel {
                    Text(stringResource(R.string.tablets_empty_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.tablets_empty_text), style = MaterialTheme.typography.bodyLarge, color = InkSoft)
                    CopyableCode(codes?.studentCode)
                }
            }
        } else {
            item(key = "summary") { TabletSummary(devices, sound, now) }
            items(devices, key = { it.uid }) { device ->
                TabletRow(
                    device = device,
                    soundAllowed = sound.isAllowed(device.uid, now),
                    now = now,
                    enabled = !cvm.busy,
                    onSoundChange = { cvm.setDeviceSound(device.uid, it) },
                    onRename = { dialog = TabletDialog.Rename(device) },
                    onSendTo = { onSendTo(device.uid) },
                    onRemove = { dialog = TabletDialog.Remove(device) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }

    when (val d = dialog) {
        is TabletDialog.Rename -> {
            var name by rememberSaveable(d.device.uid) { mutableStateOf(d.device.name) }
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text(stringResource(R.string.rename_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.rename_text), style = MaterialTheme.typography.bodyMedium, color = InkSoft)
                        KidTextField(name, { name = it }, stringResource(R.string.rename_label), Icons.Filled.Face)
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = { cvm.renameDevice(d.device.uid, name); dialog = null },
                        enabled = name.isNotBlank(),
                    ) { Text(stringResource(R.string.save_button)) }
                },
                dismissButton = {
                    TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.cancel)) }
                },
            )
        }
        is TabletDialog.Remove -> AlertDialog(
            onDismissRequest = { dialog = null },
            text = {
                Text(stringResource(R.string.confirm_remove_device, d.device.name), style = MaterialTheme.typography.bodyLarge)
            },
            confirmButton = {
                TextButton(onClick = { cvm.removeDevice(d.device.uid); dialog = null }) {
                    Text(stringResource(R.string.remove_button), color = Brick)
                }
            },
            dismissButton = {
                TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
        null -> Unit
    }
}

/** Counts above the list: online, muted, low battery. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TabletSummary(devices: List<DeviceInfo>, sound: SoundSettings, now: Long) {
    val online = devices.count { it.online }
    val muted = devices.count { !sound.isAllowed(it.uid, now) }
    val lowBattery = devices.count { it.batteryLow }
    val noLock = devices.count { it.canLock == false }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        SummaryChip(stringResource(R.string.summary_online, online, devices.size), Chalk)
        if (muted > 0) SummaryChip(stringResource(R.string.summary_muted, muted), Brick)
        if (lowBattery > 0) SummaryChip(stringResource(R.string.summary_low_battery, lowBattery), Brick)
        if (noLock > 0) SummaryChip(stringResource(R.string.summary_no_lock, noLock), InkSoft)
    }
}

@Composable
private fun SummaryChip(text: String, color: Color) {
    Row(
        Modifier
            .background(Color.White, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
    }
}

@Composable
private fun OnlineDot(online: Boolean) {
    Box(
        Modifier
            .size(10.dp)
            .background(if (online) Chalk else Line, CircleShape),
    )
}

@Composable
private fun TabletRow(
    device: DeviceInfo,
    soundAllowed: Boolean,
    now: Long,
    enabled: Boolean,
    onSoundChange: (Boolean) -> Unit,
    onRename: () -> Unit,
    onSendTo: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }
    val status = when {
        device.online -> stringResource(R.string.manage_online)
        device.lastSeen != null -> stringResource(
            R.string.last_seen,
            DateUtils.getRelativeTimeSpanString(minOf(device.lastSeen, now), now, DateUtils.MINUTE_IN_MILLIS).toString(),
        )
        else -> stringResource(R.string.manage_offline)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, if (soundAllowed) Line else BrickLight),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OnlineDot(device.online)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(device.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(status, style = MaterialTheme.typography.bodyMedium, color = InkSoft)
                    if (device.battery != null) {
                        Text("  ·  ", style = MaterialTheme.typography.bodyMedium, color = InkSoft)
                        Icon(
                            drawableIcon(R.drawable.ic_battery),
                            contentDescription = null,
                            tint = if (device.batteryLow) Brick else InkSoft,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            stringResource(
                                if (device.charging) R.string.battery_charging else R.string.battery_level,
                                device.battery,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (device.batteryLow) Brick else InkSoft,
                        )
                    }
                }
                if (device.canLock == false) {
                    Text(
                        stringResource(R.string.tablet_no_lock),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Brick,
                    )
                }
            }
            Icon(
                drawableIcon(if (soundAllowed) R.drawable.ic_volume_on else R.drawable.ic_volume_off),
                contentDescription = null,
                tint = if (soundAllowed) InkSoft else Brick,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(6.dp))
            Switch(
                checked = soundAllowed,
                onCheckedChange = onSoundChange,
                enabled = enabled,
                colors = SwitchDefaults.colors(checkedTrackColor = Chalk),
            )
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options))
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.tablet_rename)) },
                        onClick = { showMenu = false; onRename() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.tablet_send_to)) },
                        onClick = { showMenu = false; onSendTo() },
                    )
                    HorizontalDivider(color = Line)
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.tablet_remove), color = Brick) },
                        onClick = { showMenu = false; onRemove() },
                        enabled = enabled,
                    )
                }
            }
        }
    }
}
