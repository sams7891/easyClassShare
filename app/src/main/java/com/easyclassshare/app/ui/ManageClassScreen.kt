package com.easyclassshare.app.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easyclassshare.app.ClassViewModel
import com.easyclassshare.app.R
import com.easyclassshare.app.online.Role
import com.easyclassshare.app.online.TeacherInfo
import com.easyclassshare.app.ui.components.AppTopBar
import com.easyclassshare.app.ui.components.Panel
import com.easyclassshare.app.ui.components.SoftButton
import com.easyclassshare.app.ui.theme.Brick
import com.easyclassshare.app.ui.theme.Chalk
import com.easyclassshare.app.ui.theme.InkSoft
import com.easyclassshare.app.ui.theme.Line

/** A pending "are you sure?" question. */
private sealed interface Confirm {
    data class RemoveTeacher(val teacher: TeacherInfo) : Confirm
    data object LeaveClass : Confirm
    data class RenewCode(val role: Role) : Confirm
}

/** Teachers only: join codes, co-teachers and leaving the class. Tablets are managed on the class screen. */
@Composable
fun ManageClassScreen(cvm: ClassViewModel, onBack: () -> Unit) {
    val className by cvm.className.collectAsState()
    val codes by cvm.codes.collectAsState()
    val teachers by cvm.teachers.collectAsState()
    val myUid by cvm.myUid.collectAsState()
    var confirm by remember { mutableStateOf<Confirm?>(null) }

    Scaffold(
        topBar = { AppTopBar(stringResource(R.string.class_settings), onBack, subtitle = className) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { ErrorText(cvm.error) }

            // ---- Codes
            item {
                Panel {
                    Text(stringResource(R.string.manage_codes), style = MaterialTheme.typography.titleLarge)
                    CodeBlock(
                        title = stringResource(R.string.manage_student_code),
                        hint = stringResource(R.string.manage_student_code_hint),
                        code = codes?.studentCode,
                        enabled = !cvm.busy,
                        onRenew = { confirm = Confirm.RenewCode(Role.STUDENT) },
                    )
                    HorizontalDivider(color = Line)
                    CodeBlock(
                        title = stringResource(R.string.manage_teacher_code),
                        hint = stringResource(R.string.manage_teacher_code_hint),
                        code = codes?.teacherCode,
                        enabled = !cvm.busy,
                        onRenew = { confirm = Confirm.RenewCode(Role.TEACHER) },
                    )
                }
            }

            // ---- Teachers
            item {
                Panel {
                    Text(stringResource(R.string.manage_teachers, teachers.size), style = MaterialTheme.typography.titleLarge)
                    teachers.forEachIndexed { index, teacher ->
                        if (index > 0) HorizontalDivider(color = Line)
                        val isMe = teacher.uid == myUid
                        PersonRow(
                            icon = { Icon(Icons.Filled.Person, contentDescription = null, tint = InkSoft) },
                            name = if (isMe) stringResource(R.string.manage_you, teacher.name) else teacher.name,
                        ) {
                            TextButton(
                                onClick = { confirm = if (isMe) Confirm.LeaveClass else Confirm.RemoveTeacher(teacher) },
                                enabled = !cvm.busy,
                            ) {
                                Text(stringResource(if (isMe) R.string.leave_button else R.string.remove_button), color = Brick)
                            }
                        }
                    }
                }
            }
        }
    }

    confirm?.let { pending ->
        val text = when (pending) {
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
internal fun CodeBlock(title: String, hint: String, code: String?, enabled: Boolean, onRenew: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            code ?: "…",
            fontSize = 36.sp,
            letterSpacing = 4.sp,
            fontFamily = FontFamily.Monospace,
            color = Chalk,
        )
        Text(hint, style = MaterialTheme.typography.bodyMedium, color = InkSoft)
        Row {
            SoftButton(stringResource(R.string.manage_new_code), onRenew, icon = Icons.Filled.Refresh, enabled = enabled && code != null)
        }
    }
}

@Composable
internal fun PersonRow(
    icon: @Composable () -> Unit,
    name: String,
    detail: String? = null,
    actions: @Composable () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        icon()
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodyMedium, color = InkSoft)
        }
        actions()
    }
}
