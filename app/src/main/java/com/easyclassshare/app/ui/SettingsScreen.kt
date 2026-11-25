package com.easyclassshare.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.easyclassshare.app.R
import com.easyclassshare.app.settings.AppLanguage
import com.easyclassshare.app.settings.AppSettings
import com.easyclassshare.app.settings.ThemeMode
import com.easyclassshare.app.ui.components.AppTopBar
import com.easyclassshare.app.ui.components.Panel
import com.easyclassshare.app.ui.components.SoftButton
import com.easyclassshare.app.ui.theme.InkSoft

/** Gear button for the top bar of the main screens. */
@Composable
fun SettingsButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title))
    }
}

/** Language and light/dark look. Student devices also get a shortcut to their permissions. */
@Composable
fun SettingsScreen(onBack: () -> Unit, onPermissions: (() -> Unit)?) {
    val context = LocalContext.current
    val language by AppSettings.language.collectAsState()
    val theme by AppSettings.theme.collectAsState()

    Scaffold(topBar = { AppTopBar(stringResource(R.string.settings_title), onBack) }) { padding ->
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
                Panel {
                    Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium)
                    Column(Modifier.selectableGroup()) {
                        for (option in AppLanguage.entries) {
                            OptionRow(
                                text = when (option) {
                                    AppLanguage.SYSTEM -> stringResource(R.string.settings_language_system)
                                    // Each language in its own words, so it can be found in any language.
                                    AppLanguage.ENGLISH -> "English"
                                    AppLanguage.LATVIAN -> "Latviešu"
                                    AppLanguage.RUSSIAN -> "Русский"
                                },
                                selected = language == option,
                                onClick = { if (language != option) AppSettings.setLanguage(context, option) },
                            )
                        }
                    }
                }
                Panel {
                    Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleMedium)
                    Column(Modifier.selectableGroup()) {
                        for (option in ThemeMode.entries) {
                            OptionRow(
                                text = stringResource(
                                    when (option) {
                                        ThemeMode.SYSTEM -> R.string.settings_theme_system
                                        ThemeMode.LIGHT -> R.string.settings_theme_light
                                        ThemeMode.DARK -> R.string.settings_theme_dark
                                    },
                                ),
                                selected = theme == option,
                                onClick = { AppSettings.setTheme(context, option) },
                            )
                        }
                    }
                }
                if (onPermissions != null) {
                    Panel {
                        Text(stringResource(R.string.permissions_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.settings_permissions_text),
                            style = MaterialTheme.typography.bodyMedium,
                            color = InkSoft,
                        )
                        Row {
                            SoftButton(
                                stringResource(R.string.settings_permissions_button),
                                onClick = onPermissions,
                                icon = Icons.Filled.Lock,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
