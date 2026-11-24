package com.easyclassshare.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easyclassshare.app.ClassViewModel
import com.easyclassshare.app.R
import com.easyclassshare.app.online.Role
import com.easyclassshare.app.ui.components.BigButton
import com.easyclassshare.app.ui.components.KidTopBar
import com.easyclassshare.app.ui.theme.Berry
import com.easyclassshare.app.ui.theme.BerryLight
import com.easyclassshare.app.ui.theme.ClassPurple
import com.easyclassshare.app.ui.theme.Ink
import com.easyclassshare.app.ui.theme.Leaf
import com.easyclassshare.app.ui.theme.ReceiveBlue
import com.easyclassshare.app.ui.theme.SendOrange

private enum class SetupStep { CHOOSE, STUDENT, TEACHER, CREATE, TEACHER_JOIN }

/** One-time setup: put this tablet into an online class as a student or a teacher. */
@Composable
fun SetupScreen(cvm: ClassViewModel, onBack: () -> Unit) {
    var step by rememberSaveable { mutableStateOf(SetupStep.CHOOSE) }
    val back: () -> Unit = {
        cvm.clearError()
        if (step == SetupStep.CHOOSE) {
            onBack()
        } else {
            val previous = if (step == SetupStep.CREATE || step == SetupStep.TEACHER_JOIN) SetupStep.TEACHER else SetupStep.CHOOSE
            step = previous
        }
    }
    BackHandler(onBack = back)

    Scaffold(
        topBar = { KidTopBar(stringResource(R.string.setup_title), ClassPurple, Color.White, back) },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                Modifier
                    .widthIn(max = 560.dp)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when {
                    !cvm.isConfigured -> InfoCard("🛠️", stringResource(R.string.setup_not_configured))
                    step == SetupStep.CHOOSE -> {
                        Text(stringResource(R.string.setup_who), style = MaterialTheme.typography.titleLarge)
                        RoleCard("🧒", R.string.setup_student_title, R.string.setup_student_subtitle, ReceiveBlue, Color.White) {
                            step = SetupStep.STUDENT
                        }
                        RoleCard("🧑‍🏫", R.string.setup_teacher_title, R.string.setup_teacher_subtitle, SendOrange, Ink) {
                            step = SetupStep.TEACHER
                        }
                        InfoCard("💡", stringResource(R.string.setup_once_tip))
                    }
                    step == SetupStep.TEACHER -> {
                        RoleCard("✨", R.string.setup_create_title, R.string.setup_create_subtitle, Leaf, Color.White) {
                            step = SetupStep.CREATE
                        }
                        RoleCard("🤝", R.string.setup_cojoin_title, R.string.setup_cojoin_subtitle, SendOrange, Ink) {
                            step = SetupStep.TEACHER_JOIN
                        }
                    }
                    step == SetupStep.STUDENT -> JoinForm(
                        cvm = cvm,
                        role = Role.STUDENT,
                        nameLabel = R.string.setup_student_name,
                        codeLabel = R.string.setup_class_code,
                        button = R.string.setup_join_button,
                    )
                    step == SetupStep.TEACHER_JOIN -> JoinForm(
                        cvm = cvm,
                        role = Role.TEACHER,
                        nameLabel = R.string.send_name_label,
                        codeLabel = R.string.setup_teacher_code,
                        button = R.string.setup_cojoin_button,
                    )
                    step == SetupStep.CREATE -> CreateForm(cvm)
                }
            }
        }
    }
}

@Composable
private fun JoinForm(cvm: ClassViewModel, role: Role, nameLabel: Int, codeLabel: Int, button: Int) {
    var name by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    KidTextField(name, { name = it }, stringResource(nameLabel), if (role == Role.STUDENT) "🧒" else "🧑‍🏫")
    KidTextField(
        value = code,
        onValueChange = { code = it.uppercase().take(12) },
        label = stringResource(codeLabel),
        emoji = "🔑",
        capitalize = true,
    )
    ErrorText(cvm.error)
    BigButton(
        text = stringResource(if (cvm.busy) R.string.setup_working else button),
        onClick = { cvm.join(code, name, role) },
        containerColor = Leaf,
        contentColor = Color.White,
        enabled = !cvm.busy && name.isNotBlank() && code.isNotBlank(),
    )
}

@Composable
private fun CreateForm(cvm: ClassViewModel) {
    var teacherName by rememberSaveable { mutableStateOf("") }
    var className by rememberSaveable { mutableStateOf("") }
    KidTextField(teacherName, { teacherName = it }, stringResource(R.string.send_name_label), "🧑‍🏫")
    KidTextField(className, { className = it }, stringResource(R.string.setup_class_name), "🏫")
    ErrorText(cvm.error)
    BigButton(
        text = stringResource(if (cvm.busy) R.string.setup_working else R.string.setup_create_button),
        onClick = { cvm.createClass(className, teacherName) },
        containerColor = Leaf,
        contentColor = Color.White,
        enabled = !cvm.busy && teacherName.isNotBlank() && className.isNotBlank(),
    )
}

@Composable
internal fun KidTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    emoji: String,
    capitalize: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.take(40)) },
        label = { Text(label) },
        leadingIcon = { Text(emoji, fontSize = 24.sp) },
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        textStyle = MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(
            capitalization = if (capitalize) KeyboardCapitalization.Characters else KeyboardCapitalization.Words,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun ErrorText(error: Int?) {
    if (error == null) return
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = BerryLight, contentColor = Berry),
    ) {
        Text(
            stringResource(error),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
    }
}

@Composable
internal fun InfoCard(emoji: String, text: String, container: Color = Color.White) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = Ink),
    ) {
        Text(
            "$emoji  $text",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
    }
}
