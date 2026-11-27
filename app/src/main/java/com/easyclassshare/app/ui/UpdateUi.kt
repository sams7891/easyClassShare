package com.easyclassshare.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.easyclassshare.app.R
import com.easyclassshare.app.ui.components.SoftButton
import com.easyclassshare.app.ui.components.Panel
import com.easyclassshare.app.ui.components.drawableIcon
import com.easyclassshare.app.ui.theme.Chalk
import com.easyclassshare.app.ui.theme.InkSoft
import com.easyclassshare.app.ui.theme.Line
import com.easyclassshare.app.update.AppUpdater
import com.easyclassshare.app.update.UpdateState

/**
 * Asks once per new version whether to update, and then follows the download and the
 * installer. Hidden while [allowed] is false (e.g. while "Eyes on me" covers the screen).
 */
@Composable
fun UpdatePrompt(allowed: Boolean) {
    val context = LocalContext.current
    val state by AppUpdater.state.collectAsState()
    // The version the dialog is open for (null = closed).
    var openFor by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(state) {
        val s = state
        if (s is UpdateState.Available && !AppUpdater.isSnoozed(context, s.info)) openFor = s.info.version
    }
    if (!allowed || openFor == null) return
    val close = { openFor = null }

    when (val s = state) {
        is UpdateState.Available -> AlertDialog(
            onDismissRequest = close,
            title = { Text(stringResource(R.string.update_available_title, s.info.version)) },
            text = { Text(stringResource(R.string.update_available_text, AppUpdater.currentVersion)) },
            confirmButton = {
                TextButton(onClick = { AppUpdater.downloadAndInstall(context, s.info) }) {
                    Text(stringResource(R.string.update_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { AppUpdater.snooze(context, s.info); close() }) {
                    Text(stringResource(R.string.update_later))
                }
            },
        )
        is UpdateState.Downloading -> AlertDialog(
            onDismissRequest = close,
            title = { Text(stringResource(R.string.update_downloading)) },
            text = { UpdateProgress(s.progress) },
            confirmButton = { TextButton(onClick = close) { Text(stringResource(R.string.update_hide)) } },
        )
        is UpdateState.ReadyToInstall -> AlertDialog(
            onDismissRequest = close,
            title = { Text(stringResource(R.string.update_ready_title, s.info.version)) },
            text = { Text(stringResource(R.string.update_ready_text)) },
            confirmButton = {
                TextButton(onClick = { AppUpdater.install(context, s.file) }) {
                    Text(stringResource(R.string.update_install))
                }
            },
            dismissButton = { TextButton(onClick = close) { Text(stringResource(R.string.update_later)) } },
        )
        UpdateState.Failed -> AlertDialog(
            onDismissRequest = close,
            text = { Text(stringResource(R.string.update_failed)) },
            confirmButton = { TextButton(onClick = close) { Text(stringResource(R.string.ok_button)) } },
        )
        else -> Unit
    }
}

/** Settings: the installed version and a button to look for a newer one. */
@Composable
fun UpdatePanel() {
    val context = LocalContext.current
    val state by AppUpdater.state.collectAsState()

    Panel {
        Text(stringResource(R.string.update_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.update_installed, AppUpdater.currentVersion),
            style = MaterialTheme.typography.bodyMedium,
            color = InkSoft,
        )
        when (val s = state) {
            UpdateState.Checking -> Text(stringResource(R.string.update_checking), style = MaterialTheme.typography.bodyLarge)
            UpdateState.UpToDate -> Text(stringResource(R.string.update_up_to_date), style = MaterialTheme.typography.bodyLarge)
            UpdateState.Failed -> Text(stringResource(R.string.update_failed), style = MaterialTheme.typography.bodyLarge)
            is UpdateState.Available -> Text(
                stringResource(R.string.update_available_title, s.info.version),
                style = MaterialTheme.typography.bodyLarge,
            )
            is UpdateState.Downloading -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.update_downloading), style = MaterialTheme.typography.bodyLarge)
                UpdateProgress(s.progress)
            }
            is UpdateState.ReadyToInstall -> Text(
                stringResource(R.string.update_ready_title, s.info.version),
                style = MaterialTheme.typography.bodyLarge,
            )
            UpdateState.Idle -> Unit
        }
        Row {
            when (val s = state) {
                is UpdateState.Available -> SoftButton(
                    stringResource(R.string.update_button),
                    onClick = { AppUpdater.downloadAndInstall(context, s.info) },
                    icon = drawableIcon(R.drawable.ic_download),
                )
                is UpdateState.ReadyToInstall -> SoftButton(
                    stringResource(R.string.update_install),
                    onClick = { AppUpdater.install(context, s.file) },
                    icon = drawableIcon(R.drawable.ic_download),
                )
                else -> SoftButton(
                    stringResource(R.string.update_check),
                    onClick = AppUpdater::checkInBackground,
                    icon = Icons.Filled.Refresh,
                    enabled = s != UpdateState.Checking && s !is UpdateState.Downloading,
                )
            }
        }
    }
}

@Composable
private fun UpdateProgress(progress: Float?) {
    if (progress == null) {
        LinearProgressIndicator(Modifier.fillMaxWidth(), color = Chalk, trackColor = Line)
    } else {
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = Chalk, trackColor = Line)
    }
}
