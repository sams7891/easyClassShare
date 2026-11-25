package com.easyclassshare.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.easyclassshare.app.R
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

@Composable
fun HomeScreen(
    showClassSetup: Boolean,
    onSetupClick: () -> Unit,
    onSendClick: () -> Unit,
    onReceiveClick: () -> Unit,
    onSettings: () -> Unit,
) {
    Scaffold { padding ->
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
                    .padding(horizontal = 20.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
                        Text(stringResource(R.string.home_tagline), style = MaterialTheme.typography.bodyLarge, color = InkSoft)
                    }
                    SettingsButton(onSettings)
                }

                if (showClassSetup) {
                    Panel {
                        PanelHeader(drawableIcon(R.drawable.ic_school), R.string.home_class_title, Chalk, ChalkLight)
                        Text(stringResource(R.string.home_class_text), style = MaterialTheme.typography.bodyLarge)
                        BigButton(stringResource(R.string.home_class_button), onSetupClick)
                    }
                }

                Panel {
                    PanelHeader(drawableIcon(R.drawable.ic_wifi), R.string.home_wifi_title, Ink, PencilLight)
                    Text(
                        stringResource(if (showClassSetup) R.string.home_wifi_text else R.string.home_wifi_only_text),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SoftButton(
                            stringResource(R.string.role_send_title),
                            onSendClick,
                            modifier = Modifier.weight(1f),
                            icon = Icons.AutoMirrored.Filled.Send,
                        )
                        SoftButton(
                            stringResource(R.string.role_receive_title),
                            onReceiveClick,
                            modifier = Modifier.weight(1f),
                            icon = Icons.Filled.Notifications,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun PanelHeader(icon: ImageVector, title: Int, tint: Color, background: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        IconBadge(icon, tint, background)
        Spacer(Modifier.width(12.dp))
        Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
    }
}
