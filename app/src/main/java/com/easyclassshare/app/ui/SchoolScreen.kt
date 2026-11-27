package com.easyclassshare.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.easyclassshare.app.ClassViewModel
import com.easyclassshare.app.R
import com.easyclassshare.app.online.CodeKind
import com.easyclassshare.app.online.SchoolClass
import com.easyclassshare.app.online.TeacherInfo
import com.easyclassshare.app.ui.components.AppTopBar
import com.easyclassshare.app.ui.components.BigButton
import com.easyclassshare.app.ui.components.IconBadge
import com.easyclassshare.app.ui.components.Panel
import com.easyclassshare.app.ui.components.drawableIcon
import com.easyclassshare.app.ui.theme.CardBg
import com.easyclassshare.app.ui.theme.Brick
import com.easyclassshare.app.ui.theme.Chalk
import com.easyclassshare.app.ui.theme.ChalkLight
import com.easyclassshare.app.ui.theme.Ink
import com.easyclassshare.app.ui.theme.InkSoft
import com.easyclassshare.app.ui.theme.Line
import com.easyclassshare.app.ui.theme.Paper

private sealed interface SchoolConfirm {
    data class RenewCode(val kind: CodeKind) : SchoolConfirm
    data class RemoveAdmin(val admin: TeacherInfo) : SchoolConfirm
    data object Leave : SchoolConfirm
}

/** A school admin's view of a school: every class, the school codes and the admins. */
@Composable
fun SchoolScreen(
    cvm: ClassViewModel,
    onOpenClass: (SchoolClass) -> Unit,
    onBack: () -> Unit,
    onWifiMode: () -> Unit,
) {
    val school by cvm.openSchool.collectAsState()
    val schoolName by cvm.schoolName.collectAsState()
    val classes by cvm.schoolClasses.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showMore by remember { mutableStateOf(false) }
    var newClass by remember { mutableStateOf(false) }

    val title = schoolName ?: school?.name?.ifBlank { null } ?: stringResource(R.string.school_default_name)
    val subtitle = stringResource(
        if (school?.asSuperAdmin == true) R.string.school_subtitle_super else R.string.school_subtitle,
        classes.size,
    )
    Scaffold(
        topBar = {
            AppTopBar(
                title = title,
                onBack = onBack,
                subtitle = subtitle,
                actions = {
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
        Column(Modifier.fillMaxSize().padding(padding)) {
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
                    Text(stringResource(R.string.school_tab_classes, classes.size), style = MaterialTheme.typography.titleMedium)
                })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = {
                    Text(stringResource(R.string.school_tab_settings), style = MaterialTheme.typography.titleMedium)
                })
            }
            if (cvm.error != null) {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { ErrorText(cvm.error) }
            }
            when (tab) {
                0 -> ClassesTab(cvm, classes, onOpenClass, onNewClass = { cvm.clearError(); newClass = true })
                else -> SchoolSettingsTab(cvm)
            }
        }
    }

    if (newClass) {
        var name by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { newClass = false },
            title = { Text(stringResource(R.string.school_new_class)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.school_new_class_text), style = MaterialTheme.typography.bodyMedium, color = InkSoft)
                    KidTextField(name, { name = it }, stringResource(R.string.setup_class_name), drawableIcon(R.drawable.ic_school))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { cvm.createSchoolClass(name) { newClass = false } },
                    enabled = name.isNotBlank() && !cvm.busy,
                ) { Text(stringResource(if (cvm.busy) R.string.setup_working else R.string.setup_create_button)) }
            },
            dismissButton = {
                TextButton(onClick = { newClass = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun ClassesTab(
    cvm: ClassViewModel,
    classes: List<SchoolClass>,
    onOpenClass: (SchoolClass) -> Unit,
    onNewClass: () -> Unit,
) {
    val codes by cvm.schoolCodes.collectAsState()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "new") {
            BigButton(stringResource(R.string.school_new_class), onNewClass, icon = Icons.Filled.Add, enabled = !cvm.busy)
        }
        if (classes.isEmpty()) {
            item(key = "empty") {
                Panel {
                    Text(stringResource(R.string.school_no_classes_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.school_no_classes_text),
                        style = MaterialTheme.typography.bodyLarge,
                        color = InkSoft,
                    )
                    CopyableCode(codes?.schoolCode)
                }
            }
        }
        items(classes, key = { it.classId }) { schoolClass ->
            Surface(
                onClick = { onOpenClass(schoolClass) },
                shape = RoundedCornerShape(16.dp),
                color = CardBg,
                border = BorderStroke(1.dp, Line),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(drawableIcon(R.drawable.ic_group), Chalk, ChalkLight, size = 40)
                    Spacer(Modifier.width(14.dp))
                    Text(
                        schoolClass.name.ifBlank { stringResource(R.string.class_default_name) },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = InkSoft)
                }
            }
        }
    }
}

@Composable
private fun SchoolSettingsTab(cvm: ClassViewModel) {
    val codes by cvm.schoolCodes.collectAsState()
    val admins by cvm.schoolAdmins.collectAsState()
    val myUid by cvm.myUid.collectAsState()
    var confirm by remember { mutableStateOf<SchoolConfirm?>(null) }
    val school by cvm.openSchool.collectAsState()
    val schoolName by cvm.schoolName.collectAsState()
    var renaming by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "name") {
            NamePanel(
                title = stringResource(R.string.rename_school_panel),
                name = schoolName ?: school?.name?.ifBlank { null } ?: stringResource(R.string.school_default_name),
                enabled = !cvm.busy && schoolName != null,
                onRename = { cvm.clearError(); renaming = true },
            )
        }
        item(key = "codes") {
            Panel {
                Text(stringResource(R.string.manage_codes), style = MaterialTheme.typography.titleLarge)
                CodeBlock(
                    title = stringResource(R.string.school_code),
                    hint = stringResource(R.string.school_code_hint),
                    code = codes?.schoolCode,
                    enabled = !cvm.busy,
                    onRenew = { confirm = SchoolConfirm.RenewCode(CodeKind.SCHOOL) },
                )
                HorizontalDivider(color = Line)
                CodeBlock(
                    title = stringResource(R.string.admin_code),
                    hint = stringResource(R.string.admin_code_hint),
                    code = codes?.adminCode,
                    enabled = !cvm.busy,
                    onRenew = { confirm = SchoolConfirm.RenewCode(CodeKind.ADMIN) },
                )
            }
        }
        item(key = "admins") {
            Panel {
                Text(stringResource(R.string.school_admins, admins.size), style = MaterialTheme.typography.titleLarge)
                admins.forEachIndexed { index, admin ->
                    if (index > 0) HorizontalDivider(color = Line)
                    val isMe = admin.uid == myUid
                    PersonRow(
                        icon = { Icon(Icons.Filled.Person, contentDescription = null, tint = InkSoft) },
                        name = if (isMe) stringResource(R.string.manage_you, admin.name) else admin.name,
                    ) {
                        TextButton(
                            onClick = { confirm = if (isMe) SchoolConfirm.Leave else SchoolConfirm.RemoveAdmin(admin) },
                            enabled = !cvm.busy,
                        ) {
                            Text(stringResource(if (isMe) R.string.leave_school_button else R.string.remove_button), color = Brick)
                        }
                    }
                }
            }
        }
    }

    if (renaming) {
        RenameDialog(
            title = stringResource(R.string.rename_school_title),
            text = stringResource(R.string.rename_school_text),
            label = stringResource(R.string.setup_school_name),
            icon = drawableIcon(R.drawable.ic_school),
            initial = schoolName.orEmpty(),
            maxLength = 60,
            onSave = { cvm.renameSchool(it); renaming = false },
            onDismiss = { renaming = false },
        )
    }

    confirm?.let { pending ->
        val text = when (pending) {
            is SchoolConfirm.RenewCode -> stringResource(
                if (pending.kind == CodeKind.SCHOOL) R.string.confirm_renew_school else R.string.confirm_renew_admin,
            )
            is SchoolConfirm.RemoveAdmin -> stringResource(R.string.confirm_remove_admin, pending.admin.name)
            SchoolConfirm.Leave -> stringResource(
                if (admins.size <= 1) R.string.confirm_leave_last_admin else R.string.confirm_leave_school,
            )
        }
        AlertDialog(
            onDismissRequest = { confirm = null },
            text = { Text(text, style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                TextButton(onClick = {
                    when (pending) {
                        is SchoolConfirm.RenewCode -> cvm.renewSchoolCode(pending.kind)
                        is SchoolConfirm.RemoveAdmin -> cvm.removeAdmin(pending.admin.uid)
                        SchoolConfirm.Leave -> cvm.leaveOpenSchool()
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
