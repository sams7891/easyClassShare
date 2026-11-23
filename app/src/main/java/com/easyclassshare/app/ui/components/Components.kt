package com.easyclassshare.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easyclassshare.app.R
import com.easyclassshare.app.data.ClassRoom
import com.easyclassshare.app.ui.theme.Ink
import com.easyclassshare.app.ui.theme.Outline
import com.easyclassshare.app.ui.theme.SendOrange
import com.easyclassshare.app.ui.theme.Sunny

/** Big, chunky, easy-to-hit button. */
@Composable
fun BigButton(
    text: String,
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 64.dp),
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

/** Coloured top bar with a big round back button and the current class badge. */
@Composable
fun KidTopBar(
    title: String,
    room: ClassRoom,
    color: Color,
    contentColor: Color,
    onBack: () -> Unit,
) {
    Surface(color = color, contentColor = contentColor, shadowElevation = 4.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val backLabel = stringResource(R.string.back)
            Surface(
                onClick = onBack,
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.9f),
                modifier = Modifier
                    .size(52.dp)
                    .semantics { contentDescription = backLabel },
            ) {
                Box(contentAlignment = Alignment.Center) { Text("⬅️", fontSize = 24.sp) }
            }
            Spacer(Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            RoomBadge(room)
        }
    }
}

@Composable
fun RoomBadge(room: ClassRoom) {
    Surface(shape = RoundedCornerShape(50), color = Color.White, contentColor = Ink) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(room.emoji, fontSize = 22.sp)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(room.label), style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Two rows of big animal tiles. */
@Composable
fun RoomPicker(selected: ClassRoom, onSelect: (ClassRoom) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ClassRoom.entries.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { room ->
                    RoomTile(room, room == selected, { onSelect(room) }, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun RoomTile(room: ClassRoom, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.semantics { selected = isSelected },
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) Sunny else Color.White,
        contentColor = Ink,
        border = BorderStroke(
            if (isSelected) 4.dp else 2.dp,
            if (isSelected) SendOrange else Outline,
        ),
        shadowElevation = if (isSelected) 6.dp else 1.dp,
    ) {
        Column(
            Modifier.padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(room.emoji, fontSize = 38.sp)
            Text(
                stringResource(room.label),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
            )
        }
    }
}

/** Gently grows and shrinks its content forever - used for "listening" feedback. */
@Composable
fun Pulsing(content: @Composable () -> Unit) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulseScale",
    )
    Box(Modifier.graphicsLayer { scaleX = scale; scaleY = scale }) { content() }
}
