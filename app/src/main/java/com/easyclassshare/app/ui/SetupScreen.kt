package com.easyclassshare.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.easyclassshare.app.ClassViewModel
import com.easyclassshare.app.R
import com.easyclassshare.app.online.Role
import com.easyclassshare.app.ui.components.AppTopBar
import com.easyclassshare.app.ui.components.BigButton
import com.easyclassshare.app.ui.components.IconBadge
import com.easyclassshare.app.ui.components.Notice
import com.easyclassshare.app.ui.components.Panel
import com.easyclassshare.app.ui.components.Tone
import com.easyclassshare.app.ui.components.drawableIcon
import com.easyclassshare.app.ui.theme.Chalk
import com.easyclassshare.app.ui.theme.ChalkLight
import com.easyclassshare.app.ui.theme.Ink
import com.easyclassshare.app.ui.theme.InkSoft
import com.easyclassshare.app.ui.theme.Line
import com.easyclassshare.app.ui.theme.PencilLight
import com.easyclassshare.app.ui.theme.Sky
import com.easyclassshare.app.ui.theme.SkyLight

private enum class SetupStep { CHOOSE, STUDENT, TEACHER, CREATE, TEACHER_JOIN, ADMIN, CREATE_SCHOOL, ADMIN_JOIN }

/**
 * One-time setup: put this device into an online class as a student or a teacher,
 * or into a school as its admin.
 * [mode] says whether this is the first setup or adding one more class or school.
 */
/** Where the setup starts. */
enum class SetupMode {
    /** First setup of this device. */
    NEW,

    /** A teacher or admin adds another class or school. */
    ADD_STAFF,

    /** A student device joins one more class. */
    ADD_STUDENT,
}

@Composable
fun SetupScreen(cvm: ClassViewModel, mode: SetupMode, onBack: () -> Unit, onDone: () -> Unit) {
    val addingClass = mode == SetupMode.ADD_STAFF
    val firstStep = when (mode) {
        SetupMode.NEW -> SetupStep.CHOOSE
        SetupMode.ADD_STAFF -> SetupStep.TEACHER
        SetupMode.ADD_STUDENT -> SetupStep.STUDENT
    }
    var step by rememberSaveable { mutableStateOf(firstStep) }
    val back: () -> Unit = {
        cvm.clearError()
        if (step == firstStep) {
            onBack()
        } else {
            step = when (step) {
                SetupStep.CREATE, SetupStep.TEACHER_JOIN -> SetupStep.TEACHER
                SetupStep.CREATE_SCHOOL, SetupStep.ADMIN_JOIN -> if (addingClass) SetupStep.TEACHER else SetupStep.ADMIN
                else -> SetupStep.CHOOSE
            }
        }
    }
    BackHandler(onBack = back)

    val title = stringResource(
        when (mode) {
            SetupMode.NEW -> R.string.setup_title
            SetupMode.ADD_STAFF -> R.string.setup_add_class_title
            SetupMode.ADD_STUDENT -> R.string.student_join_another
        },
    )
    Scaffold(topBar = { AppTopBar(title, back) }) { padding ->
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
                    !cvm.isConfigured -> Notice(stringResource(R.string.setup_not_configured), Tone.WARN, Icons.Filled.Info)
                    step == SetupStep.CHOOSE -> {
                        Text(stringResource(R.string.setup_who), style = MaterialTheme.typography.titleLarge)
                        ChoiceCard(
                            Icons.Filled.Face, Sky, SkyLight,
                            R.string.setup_student_title, R.string.setup_student_subtitle,
                        ) { step = SetupStep.STUDENT }
                        ChoiceCard(
                            Icons.Filled.Person, Chalk, ChalkLight,
                            R.string.setup_teacher_title, R.string.setup_teacher_subtitle,
                        ) { step = SetupStep.TEACHER }
                        ChoiceCard(
                            drawableIcon(R.drawable.ic_school), Ink, PencilLight,
                            R.string.setup_admin_title, R.string.setup_admin_subtitle,
                        ) { step = SetupStep.ADMIN }
                        Text(stringResource(R.string.setup_once_tip), style = MaterialTheme.typography.bodyMedium, color = InkSoft)
                    }
                    step == SetupStep.TEACHER -> {
                        ChoiceCard(
                            Icons.Filled.Add, Chalk, ChalkLight,
                            R.string.setup_create_title, R.string.setup_create_subtitle,
                        ) { step = SetupStep.CREATE }
                        ChoiceCard(
                            drawableIcon(R.drawable.ic_group), Ink, PencilLight,
                            R.string.setup_cojoin_title, R.string.setup_cojoin_subtitle,
                        ) { step = SetupStep.TEACHER_JOIN }
                        if (addingClass) SchoolChoices { step = it }
                    }
                    step == SetupStep.ADMIN -> SchoolChoices { step = it }
                    step == SetupStep.CREATE_SCHOOL -> CreateSchoolForm(cvm, onDone)
                    step == SetupStep.ADMIN_JOIN -> JoinForm(cvm, Role.ADMIN, onDone)
                    step == SetupStep.STUDENT -> JoinForm(cvm, Role.STUDENT, onDone)
                    step == SetupStep.TEACHER_JOIN -> JoinForm(cvm, Role.TEACHER, onDone)
                    step == SetupStep.CREATE -> CreateForm(cvm, onDone)
                }
            }
        }
    }
}

@Composable
private fun JoinForm(cvm: ClassViewModel, role: Role, onDone: () -> Unit) {
    val isStudent = role == Role.STUDENT
    var name by rememberSaveable { mutableStateOf(if (isStudent) cvm.knownStudentName else cvm.knownTeacherName) }
    var code by rememberSaveable { mutableStateOf("") }
    Panel {
        KidTextField(
            name, { name = it },
            stringResource(if (isStudent) R.string.setup_student_name else R.string.setup_name),
            if (isStudent) Icons.Filled.Face else Icons.Filled.Person,
        )
        CodeField(
            code,
            { code = it },
            stringResource(
                when (role) {
                    Role.STUDENT -> R.string.setup_class_code
                    Role.TEACHER -> R.string.setup_teacher_code
                    Role.ADMIN -> R.string.admin_code
                },
            ),
        )
        ErrorText(cvm.error)
        BigButton(
            text = stringResource(
                when {
                    cvm.busy -> R.string.setup_working
                    isStudent -> R.string.setup_join_button
                    role == Role.ADMIN -> R.string.setup_admin_join_button
                    else -> R.string.setup_cojoin_button
                },
            ),
            onClick = { cvm.join(code, name, role, onDone) },
            enabled = !cvm.busy && name.isNotBlank() && code.isNotBlank(),
        )
    }
}

@Composable
private fun CreateForm(cvm: ClassViewModel, onDone: () -> Unit) {
    var teacherName by rememberSaveable { mutableStateOf(cvm.knownTeacherName) }
    var className by rememberSaveable { mutableStateOf("") }
    var schoolCode by rememberSaveable { mutableStateOf("") }
    Panel {
        KidTextField(teacherName, { teacherName = it }, stringResource(R.string.setup_name), Icons.Filled.Person)
        KidTextField(className, { className = it }, stringResource(R.string.setup_class_name), drawableIcon(R.drawable.ic_school))
        CodeField(schoolCode, { schoolCode = it }, stringResource(R.string.setup_school_code_optional))
        Text(stringResource(R.string.setup_school_code_hint), style = MaterialTheme.typography.bodyMedium, color = InkSoft)
        ErrorText(cvm.error)
        BigButton(
            text = stringResource(if (cvm.busy) R.string.setup_working else R.string.setup_create_button),
            onClick = { cvm.createClass(className, teacherName, schoolCode, onDone) },
            enabled = !cvm.busy && teacherName.isNotBlank() && className.isNotBlank(),
        )
    }
}

@Composable
private fun SchoolChoices(goTo: (SetupStep) -> Unit) {
    ChoiceCard(
        drawableIcon(R.drawable.ic_school), Chalk, ChalkLight,
        R.string.setup_create_school_title, R.string.setup_create_school_subtitle,
    ) { goTo(SetupStep.CREATE_SCHOOL) }
    ChoiceCard(
        Icons.Filled.Person, Ink, PencilLight,
        R.string.setup_admin_join_title, R.string.setup_admin_join_subtitle,
    ) { goTo(SetupStep.ADMIN_JOIN) }
}

@Composable
private fun CreateSchoolForm(cvm: ClassViewModel, onDone: () -> Unit) {
    var adminName by rememberSaveable { mutableStateOf(cvm.knownTeacherName) }
    var schoolName by rememberSaveable { mutableStateOf("") }
    Panel {
        KidTextField(adminName, { adminName = it }, stringResource(R.string.setup_name), Icons.Filled.Person)
        KidTextField(
            schoolName,
            { schoolName = it },
            stringResource(R.string.setup_school_name),
            drawableIcon(R.drawable.ic_school),
            maxLength = 60,
        )
        Text(stringResource(R.string.setup_create_school_hint), style = MaterialTheme.typography.bodyMedium, color = InkSoft)
        ErrorText(cvm.error)
        BigButton(
            text = stringResource(if (cvm.busy) R.string.setup_working else R.string.setup_create_school_button),
            onClick = { cvm.createSchool(schoolName, adminName, onDone) },
            enabled = !cvm.busy && adminName.isNotBlank() && schoolName.isNotBlank(),
        )
    }
}

/** A big tappable option with an icon, a title and one line of explanation. */
@Composable
internal fun ChoiceCard(
    icon: ImageVector,
    tint: Color,
    background: Color,
    title: Int,
    subtitle: Int,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White, contentColor = Ink),
        border = BorderStroke(1.dp, Line),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, tint, background, size = 52)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(subtitle), style = MaterialTheme.typography.bodyLarge, color = InkSoft)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = InkSoft)
        }
    }
}
