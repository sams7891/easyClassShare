package com.easyclassshare.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.easyclassshare.app.R
import com.easyclassshare.app.data.LinkUtils
import com.easyclassshare.app.data.ShareMessage
import com.easyclassshare.app.ui.components.Notice
import com.easyclassshare.app.ui.components.SoftButton
import com.easyclassshare.app.ui.components.Tone
import com.easyclassshare.app.ui.components.drawableIcon
import com.easyclassshare.app.ui.theme.InkSoft

/** Single-line field for names and codes. */
@Composable
internal fun KidTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    placeholder: String? = null,
    capitalize: Boolean = false,
    maxLength: Int = 40,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.take(maxLength)) },
        label = { Text(label) },
        placeholder = if (placeholder != null) {
            { Text(placeholder) }
        } else {
            null
        },
        leadingIcon = { Icon(icon, contentDescription = null) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        textStyle = MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(
            capitalization = if (capitalize) KeyboardCapitalization.Characters else KeyboardCapitalization.Words,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Field for a class or teacher code: upper case, no spaces. */
@Composable
internal fun CodeField(value: String, onValueChange: (String) -> Unit, label: String) {
    KidTextField(
        value = value,
        onValueChange = { onValueChange(it.uppercase().filter { c -> !c.isWhitespace() }) },
        label = label,
        icon = drawableIcon(R.drawable.ic_school),
        capitalize = true,
        maxLength = 12,
    )
}

/** Multi-line box for the link or message, with a character counter and a hint about links. */
@Composable
internal fun MessageField(draft: String, onDraftChange: (String) -> Unit) {
    OutlinedTextField(
        value = draft,
        onValueChange = onDraftChange,
        placeholder = { Text(stringResource(R.string.send_what_hint)) },
        minLines = 3,
        shape = RoundedCornerShape(14.dp),
        textStyle = MaterialTheme.typography.bodyLarge,
        supportingText = {
            val hint = when {
                draft.isBlank() -> ""
                LinkUtils.looksLikeLink(draft) -> stringResource(R.string.detected_link)
                else -> stringResource(R.string.detected_text)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(hint, color = InkSoft, modifier = Modifier.weight(1f))
                Text(
                    stringResource(R.string.char_counter, draft.length, ShareMessage.MAX_CONTENT_LENGTH),
                    color = InkSoft,
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun PasteClearRow(draft: String, onDraftChange: (String) -> Unit) {
    val clipboard = LocalClipboardManager.current
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SoftButton(
            stringResource(R.string.paste),
            onClick = { clipboard.getText()?.text?.let(onDraftChange) },
            modifier = Modifier.weight(1f),
            icon = drawableIcon(R.drawable.ic_paste),
        )
        SoftButton(
            stringResource(R.string.clear),
            onClick = { onDraftChange("") },
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.Clear,
            enabled = draft.isNotEmpty(),
        )
    }
}

/** Shows the view model's current error, if there is one. */
@Composable
internal fun ErrorText(error: Int?) {
    if (error == null) return
    Notice(stringResource(error), Tone.BAD, Icons.Filled.Warning)
}
