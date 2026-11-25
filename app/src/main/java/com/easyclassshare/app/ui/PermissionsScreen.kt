package com.easyclassshare.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.easyclassshare.app.R
import com.easyclassshare.app.service.DevicePermissions
import com.easyclassshare.app.service.LockService
import com.easyclassshare.app.ui.components.AppTopBar
import com.easyclassshare.app.ui.components.BigButton
import com.easyclassshare.app.ui.components.IconBadge
import com.easyclassshare.app.ui.components.Panel
import com.easyclassshare.app.ui.components.SoftButton
import com.easyclassshare.app.ui.components.drawableIcon
import com.easyclassshare.app.ui.theme.Chalk
import com.easyclassshare.app.ui.theme.ChalkLight
import com.easyclassshare.app.ui.theme.Ink
import com.easyclassshare.app.ui.theme.InkSoft
import com.easyclassshare.app.ui.theme.PencilLight

/**
 * Everything a student device has to allow, in one list: shown right after the student
 * setup and each time the app opens while something is still missing.
 */
@Composable
fun PermissionsScreen(onDone: () -> Unit, onBack: (() -> Unit)?) {
    val context = LocalContext.current
    // Bumped when coming back from a settings page, so every row is checked again.
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    // Read again on every refresh.
    val notifications = remember(refresh) { DevicePermissions.notificationsAllowed(context) }
    val overlay = remember(refresh) { DevicePermissions.overlayAllowed(context) }
    val lock = remember(refresh) { DevicePermissions.lockEnabled(context) || LockService.instance.value != null }
    val battery = remember(refresh) { DevicePermissions.batteryUnrestricted(context) }
    val allDone = notifications && overlay && lock && battery

    Scaffold(topBar = { AppTopBar(stringResource(R.string.permissions_title), onBack) }) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                Modifier
                    .widthIn(max = 560.dp)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(stringResource(R.string.permissions_intro), style = MaterialTheme.typography.bodyLarge, color = InkSoft)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    PermissionRow(
                        icon = Icons.Filled.Notifications,
                        title = R.string.permission_notifications_title,
                        text = R.string.permission_notifications_text,
                        granted = notifications,
                        onAllow = { askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    )
                }
                PermissionRow(
                    icon = drawableIcon(R.drawable.ic_eye),
                    title = R.string.permission_overlay_title,
                    text = R.string.permission_overlay_text,
                    granted = overlay,
                    onAllow = { DevicePermissions.openOverlaySettings(context) },
                )
                PermissionRow(
                    icon = Icons.Filled.Lock,
                    title = R.string.permission_lock_title,
                    text = R.string.permission_lock_text,
                    granted = lock,
                    onAllow = { DevicePermissions.openAccessibilitySettings(context) },
                ) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Text(
                            stringResource(R.string.permission_lock_restricted),
                            style = MaterialTheme.typography.bodyMedium,
                            color = InkSoft,
                        )
                        TextButton(onClick = { DevicePermissions.openAppInfo(context) }) {
                            Text(stringResource(R.string.permission_open_app_info))
                        }
                    }
                }
                PermissionRow(
                    icon = drawableIcon(R.drawable.ic_battery),
                    title = R.string.permission_battery_title,
                    text = R.string.permission_battery_text,
                    granted = battery,
                    onAllow = { DevicePermissions.openBatterySettings(context) },
                )

                BigButton(
                    stringResource(if (allDone) R.string.permissions_done else R.string.permissions_later),
                    onClick = onDone,
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: Int,
    text: Int,
    granted: Boolean,
    onAllow: () -> Unit,
    extra: @Composable () -> Unit = {},
) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, if (granted) Chalk else Ink, if (granted) ChalkLight else PencilLight)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (granted) {
                Icon(Icons.Filled.CheckCircle, contentDescription = stringResource(R.string.permission_allowed), tint = Chalk)
            }
        }
        Text(stringResource(text), style = MaterialTheme.typography.bodyMedium, color = InkSoft)
        if (!granted) {
            Row { SoftButton(stringResource(R.string.permission_allow), onClick = onAllow) }
            extra()
        }
    }
}
