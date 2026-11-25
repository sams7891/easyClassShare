package com.easyclassshare.app.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easyclassshare.app.R
import com.easyclassshare.app.data.LinkUtils
import com.easyclassshare.app.data.ShareMessage
import com.easyclassshare.app.ui.components.Notice
import com.easyclassshare.app.ui.components.SoftButton
import com.easyclassshare.app.ui.components.Tone
import com.easyclassshare.app.ui.components.drawableIcon
import com.easyclassshare.app.ui.theme.Chalk
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

/**
 * A join code in large letters with a Copy button. Tapping the code copies it too.
 * Shown in two groups ("ABCD EFGH") and made smaller when it doesn't fit next to the button,
 * so the longer teacher and admin codes are never cut off. Copying gives the code without the space.
 */
@Composable
internal fun CopyableCode(code: String?) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val copiedText = stringResource(R.string.copied)
    val copy = {
        if (code != null) {
            clipboard.setText(AnnotatedString(code))
            Toast.makeText(context, copiedText, Toast.LENGTH_SHORT).show()
        }
    }
    val shown = code?.let(::groupCode) ?: "…"
    Row(verticalAlignment = Alignment.CenterVertically) {
        BoxWithConstraints(Modifier.weight(1f)) {
            // A monospace letter plus its spacing is about 0.75 of the font size wide.
            val fontScale = LocalDensity.current.fontScale
            val fitting = maxWidth.value / (shown.length.coerceAtLeast(4) * 0.75f) / fontScale
            val size = fitting.coerceIn(MIN_CODE_SIZE, MAX_CODE_SIZE)
            Text(
                shown,
                fontSize = size.sp,
                letterSpacing = (size * 0.12f).sp,
                fontFamily = FontFamily.Monospace,
                color = Chalk,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.clickable(enabled = code != null, onClick = copy),
            )
        }
        Spacer(Modifier.width(12.dp))
        SoftButton(
            stringResource(R.string.copy),
            onClick = copy,
            icon = drawableIcon(R.drawable.ic_copy),
            enabled = code != null,
        )
    }
}

/** "ABCDEFGH" -> "ABCD EFGH": two halves are easier to read out and type. */
internal fun groupCode(code: String): String {
    if (code.length < 6) return code
    val half = (code.length + 1) / 2
    return code.substring(0, half) + " " + code.substring(half)
}

private const val MAX_CODE_SIZE = 34f
private const val MIN_CODE_SIZE = 18f

/** Shows the view model's current error, if there is one. */
@Composable
internal fun ErrorText(error: Int?) {
    if (error == null) return
    Notice(stringResource(error), Tone.BAD, Icons.Filled.Warning)
}
