package com.easyclassshare.app.ui

import android.widget.Toast
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.easyclassshare.app.ClassViewModel
import com.easyclassshare.app.R
import com.easyclassshare.app.online.Membership
import com.easyclassshare.app.online.Role
import com.easyclassshare.app.online.SchoolRef
import com.easyclassshare.app.ui.components.AppTopBar
import com.easyclassshare.app.ui.components.BigButton
import com.easyclassshare.app.ui.components.IconBadge
import com.easyclassshare.app.ui.components.drawableIcon
import com.easyclassshare.app.ui.theme.CardBg
import com.easyclassshare.app.ui.theme.Chalk
import com.easyclassshare.app.ui.theme.ChalkLight
import com.easyclassshare.app.ui.theme.Ink
import com.easyclassshare.app.ui.theme.InkSoft
import com.easyclassshare.app.ui.theme.Line
import com.easyclassshare.app.ui.theme.PencilLight
import com.easyclassshare.app.ui.theme.Sky
import com.easyclassshare.app.ui.theme.SkyLight

/** First screen for teachers and admins: all their schools and classes (and for the super admin, every school). */
@Composable
fun StartScreen(
    cvm: ClassViewModel,
    onOpenClass: (Membership) -> Unit,
    onOpenSchool: (SchoolRef) -> Unit,
    onAdd: () -> Unit,
    onWifiMode: () -> Unit,
    onSettings: () -> Unit,
) {
    val memberships by cvm.memberships.collectAsState()
    val isSuperAdmin by cvm.isSuperAdmin.collectAsState()
    val allSchools by cvm.allSchools.collectAsState()
    var showMore by remember { mutableStateOf(false) }
    var showDeviceId by remember { mutableStateOf(false) }

    val schools = memberships.filter { it.role == Role.ADMIN }.sortedBy { it.className.lowercase() }
    val classes = memberships.filter { it.role == Role.TEACHER }
    // Classes grouped by school; classes without a school last.
    val groups = classes.groupBy { it.schoolName }.toSortedMap(compareBy<String>({ it.isEmpty() }, { it.lowercase() }, { it }))
    val otherClassesTitle = stringResource(if (groups.keys.any { it.isNotEmpty() }) R.string.start_other_classes else R.string.start_classes)

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.app_name),
                onBack = null,
                subtitle = stringResource(R.string.start_subtitle),
                actions = {
                    SettingsButton(onSettings)
                    Box {
                        IconButton(onClick = { showMore = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options))
                        }
                        DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.start_add)) },
                                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                                onClick = { showMore = false; onAdd() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.teacher_wifi_mode)) },
                                leadingIcon = { Icon(drawableIcon(R.drawable.ic_wifi), contentDescription = null) },
                                onClick = { showMore = false; onWifiMode() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.device_id_menu)) },
                                leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                                onClick = { showMore = false; showDeviceId = true },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (schools.isNotEmpty()) {
                item(key = "schools-title") { SectionTitle(stringResource(R.string.start_schools)) }
                items(schools, key = { "school-" + it.classId }) { m ->
                    EntryCard(
                        icon = drawableIcon(R.drawable.ic_school),
                        tint = Ink,
                        background = PencilLight,
                        title = m.className.ifBlank { stringResource(R.string.school_default_name) },
                        subtitle = stringResource(R.string.start_admin_role),
                        onClick = { onOpenSchool(SchoolRef(m.classId, m.className)) },
                    )
                }
            }
            for ((schoolName, inSchool) in groups) {
                item(key = "classes-title-$schoolName") {
                    SectionTitle(schoolName.ifEmpty { otherClassesTitle })
                }
                items(inSchool.sortedBy { it.className.lowercase() }, key = { "class-" + it.classId }) { m ->
                    EntryCard(
                        icon = drawableIcon(R.drawable.ic_group),
                        tint = Chalk,
                        background = ChalkLight,
                        title = m.className.ifBlank { stringResource(R.string.class_default_name) },
                        subtitle = stringResource(R.string.start_teacher_role, m.name),
                        onClick = { onOpenClass(m) },
                    )
                }
            }
            if (isSuperAdmin) {
                item(key = "all-title") { SectionTitle(stringResource(R.string.start_all_schools)) }
                if (allSchools.isEmpty()) {
                    item(key = "all-empty") {
                        Text(stringResource(R.string.start_no_schools), style = MaterialTheme.typography.bodyLarge, color = InkSoft)
                    }
                }
                items(allSchools, key = { "all-" + it.schoolId }) { school ->
                    EntryCard(
                        icon = drawableIcon(R.drawable.ic_school),
                        tint = Sky,
                        background = SkyLight,
                        title = school.name.ifBlank { stringResource(R.string.school_default_name) },
                        subtitle = stringResource(R.string.start_class_count, school.classCount),
                        onClick = { onOpenSchool(school) },
                    )
                }
            }
            item(key = "add") {
                Box(Modifier.padding(top = 8.dp)) {
                    BigButton(stringResource(R.string.start_add), onAdd, icon = Icons.Filled.Add)
                }
            }
        }
    }

    if (showDeviceId) DeviceIdDialog(cvm) { showDeviceId = false }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun EntryCard(
    icon: ImageVector,
    tint: Color,
    background: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = CardBg,
        border = BorderStroke(1.dp, Line),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, tint, background, size = 42)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = InkSoft, maxLines = 1)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = InkSoft)
        }
    }
}

/** Shows this device's id, needed to make it the super admin in the Firebase console. */
@Composable
private fun DeviceIdDialog(cvm: ClassViewModel, onDismiss: () -> Unit) {
    val uid by cvm.myUid.collectAsState()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val copiedText = stringResource(R.string.copied)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.device_id_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.device_id_text), style = MaterialTheme.typography.bodyMedium, color = InkSoft)
                Text(uid ?: "…", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyLarge)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    uid?.let {
                        clipboard.setText(AnnotatedString(it))
                        Toast.makeText(context, copiedText, Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = uid != null,
            ) { Text(stringResource(R.string.copy)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok_button)) }
        },
    )
}
