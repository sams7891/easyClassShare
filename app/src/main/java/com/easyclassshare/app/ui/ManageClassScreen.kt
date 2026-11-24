package com.easyclassshare.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easyclassshare.app.ClassViewModel
import com.easyclassshare.app.R
import com.easyclassshare.app.online.DeviceInfo
import com.easyclassshare.app.online.Role
import com.easyclassshare.app.online.TeacherInfo
import com.easyclassshare.app.ui.components.KidTopBar
import com.easyclassshare.app.ui.theme.Berry
import com.easyclassshare.app.ui.theme.ClassPurple
import com.easyclassshare.app.ui.theme.Ink
import com.easyclassshare.app.ui.theme.Leaf
import com.easyclassshare.app.ui.theme.Outline

/** A pending "are you sure?" question. */
private sealed interface Confirm {
    data class RemoveDevice(val device: DeviceInfo) : Confirm
    data class RemoveTeacher(val teacher: TeacherInfo) : Confirm
    data object LeaveClass : Confirm
    data class RenewCode(val role: Role) : Confirm
}

/** Teachers only: join codes, sound per tablet, removing tablets and co-teachers. */
@Composable
fun ManageClassScreen(cvm: ClassViewModel, onBack: () -> Unit) {
    val codes by cvm.codes.collectAsState()
    val sound by cvm.sound.collectAsState()
    val devices by cvm.devices.collectAsState()
    val teachers by cvm.teachers.collectAsState()
    val myUid by cvm.myUid.collectAsState()
    var confirm by remember { mutableStateOf<Confirm?>(null) }

    Scaffold(
        topBar = { KidTopBar(stringResource(R.string.manage_class), ClassPurple, Color.White, onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { ErrorText(cvm.error) }

            // ---- Join codes
            item { SectionTitle(stringResource(R.string.manage_codes)) }
            item {
                CodeCard(
                    title = stringResource(R.string.manage_student_code),
                    hint = stringResource(R.string.manage_student_code_hint),
                    code = codes?.studentCode,
                    enabled = !cvm.busy,
                    onRenew = { confirm = Confirm.RenewCode(Role.STUDENT) },
                )
            }
            item {
                CodeCard(
                    title = stringResource(R.string.manage_teacher_code),
                    hint = stringResource(R.string.manage_teacher_code_hint),
                    code = codes?.teacherCode,
                    enabled = !cvm.busy,
                    onRenew = { confirm = Confirm.RenewCode(Role.TEACHER) },
                )
            }

            // ---- Sound + student tablets
            item { SectionTitle(stringResource(R.string.manage_students, devices.size)) }
            item {
                SoundSwitchRow(
                    allowed = sound.classWideAllowed,
                    enabled = !cvm.busy,
                    onMuteAll = { cvm.setClassSound(false) },
                    onAllowAll = { cvm.setClassSound(true) },
                )
            }
            if (devices.isEmpty()) {
                item { InfoCard("📭", stringResource(R.string.manage_no_students)) }
            }
            items(devices, key = { "d-" + it.uid }) { device ->
                DeviceRow(
                    device = device,
                    soundAllowed = sound.isAllowed(device.uid),
                    enabled = !cvm.busy,
                    onSoundChange = { cvm.setDeviceSound(device.uid, it) },
                    onRemove = { confirm = Confirm.RemoveDevice(device) },
                )
            }

            // ---- Teachers
            item { SectionTitle(stringResource(R.string.manage_teachers, teachers.size)) }
            items(teachers, key = { "t-" + it.uid }) { teacher ->
                val isMe = teacher.uid == myUid
                PersonRow(
                    emoji = "🧑‍🏫",
                    name = if (isMe) stringResource(R.string.manage_you, teacher.name) else teacher.name,
                ) {
                    if (isMe) {
                        TextButton(onClick = { confirm = Confirm.LeaveClass }, enabled = !cvm.busy) {
                            Text(stringResource(R.string.leave_button), color = Berry)
                        }
                    } else {
                        TextButton(onClick = { confirm = Confirm.RemoveTeacher(teacher) }, enabled = !cvm.busy) {
                            Text(stringResource(R.string.remove_button), color = Berry)
                        }
                    }
                }
            }
        }
    }

    confirm?.let { pending ->
        val text = when (pending) {
            is Confirm.RemoveDevice -> stringResource(R.string.confirm_remove_device, pending.device.name)
            is Confirm.RemoveTeacher -> stringResource(R.string.confirm_remove_teacher, pending.teacher.name)
            Confirm.LeaveClass -> stringResource(
                if (teachers.size <= 1) R.string.confirm_leave_last_teacher else R.string.confirm_leave_teacher,
            )
            is Confirm.RenewCode -> stringResource(
                if (pending.role == Role.STUDENT) R.string.confirm_renew_student else R.string.confirm_renew_teacher,
            )
        }
        AlertDialog(
            onDismissRequest = { confirm = null },
            text = { Text(text, style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                TextButton(onClick = {
                    when (pending) {
                        is Confirm.RemoveDevice -> cvm.removeDevice(pending.device.uid)
                        is Confirm.RemoveTeacher -> cvm.removeTeacher(pending.teacher.uid)
                        Confirm.LeaveClass -> cvm.leave()
                        is Confirm.RenewCode -> cvm.renewCode(pending.role)
                    }
                    confirm = null
                }) { Text(stringResource(R.string.yes_button)) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun CodeCard(title: String, hint: String, code: String?, enabled: Boolean, onRenew: () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White, contentColor = Ink),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                code ?: "…",
                fontSize = 40.sp,
                fontFamily = FontFamily.Monospace,
                color = ClassPurple,
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(hint, style = MaterialTheme.typography.bodyMedium, color = Ink.copy(alpha = 0.7f))
            OutlinedButton(onClick = onRenew, enabled = enabled && code != null) {
                Text(stringResource(R.string.manage_new_code))
            }
        }
    }
}

@Composable
private fun DeviceRow(
    device: DeviceInfo,
    soundAllowed: Boolean,
    enabled: Boolean,
    onSoundChange: (Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    PersonRow(emoji = "🧒", name = device.name, online = device.online) {
        Text(if (soundAllowed) "🔊" else "🔇", fontSize = 24.sp)
        Spacer(Modifier.width(4.dp))
        Switch(checked = soundAllowed, onCheckedChange = onSoundChange, enabled = enabled)
        TextButton(onClick = onRemove, enabled = enabled) {
            Text(stringResource(R.string.remove_button), color = Berry)
        }
    }
}

@Composable
private fun PersonRow(
    emoji: String,
    name: String,
    online: Boolean? = null,
    actions: @Composable () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White, contentColor = Ink),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(emoji, fontSize = 28.sp)
            Spacer(Modifier.width(12.dp))
            if (online != null) {
                Box(
                    Modifier
                        .size(12.dp)
                        .background(if (online) Leaf else Outline, CircleShape),
                )
                Spacer(Modifier.width(8.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                if (online != null) {
                    Text(
                        stringResource(if (online) R.string.manage_online else R.string.manage_offline),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Ink.copy(alpha = 0.7f),
                    )
                }
            }
            actions()
        }
    }
}
