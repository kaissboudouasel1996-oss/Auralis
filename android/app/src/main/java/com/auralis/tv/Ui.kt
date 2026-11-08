package com.auralis.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object C {
    val Bg = Color(0xFF070B1E)
    val Surface = Color(0xFF111833)
    val SurfaceFocused = Color(0xFF1E2A5E)
    val Cyan = Color(0xFF2DE2E6)
    val Blue = Color(0xFF4D7CFF)
    val Violet = Color(0xFF9B5DE5)
    val Magenta = Color(0xFFF15BB5)
    val Text = Color(0xFFF2F5FF)
    val Muted = Color(0xFF9AA6D0)
    val Ok = Color(0xFF3DDC97)
    val Warn = Color(0xFFFFB84D)
    val Err = Color(0xFFFF5C7A)
    val Primary = Color(0xFF5B3FD6)
    val Aurora = Brush.horizontalGradient(listOf(Cyan, Blue, Violet, Magenta))
}

@Composable
fun AuralisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.Cyan,
            background = C.Bg,
            surface = C.Surface,
            onSurface = C.Text,
            onBackground = C.Text,
        ),
        content = content,
    )
}

/** Anything the D-pad can land on: glows and grows while focused, acts on OK / Enter. */
@Composable
fun Focusable(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    base: Color = C.Surface,
    onClick: () -> Unit,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.03f else 1f, label = "focusScale")
    Box(
        modifier = modifier
            .scale(scale)
            .background(if (focused) C.SurfaceFocused else base, shape)
            .then(if (focused) Modifier.border(3.dp, C.Cyan, shape) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .clickable(onClick = onClick),
    ) {
        content(focused)
    }
}

@Composable
fun TvButton(
    text: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    Focusable(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        base = if (primary) C.Primary else if (selected) Color(0xFF26346E) else C.Surface,
        onClick = onClick,
    ) {
        Text(
            text,
            color = C.Text,
            fontSize = 16.sp,
            fontWeight = if (primary || selected) FontWeight.SemiBold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
        )
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .background(C.Surface, RoundedCornerShape(20.dp))
            .padding(20.dp),
    ) {
        content()
    }
}

@Composable
fun Title(text: String, sub: String? = null) {
    Column(modifier = Modifier.padding(bottom = 4.dp)) {
        Text(text, color = C.Text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        if (sub != null) Text(sub, color = C.Muted, fontSize = 15.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
fun Heading(text: String) {
    Text(
        text,
        color = C.Cyan,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
fun Body(text: String, color: Color = C.Muted) {
    Text(text, color = color, fontSize = 15.sp, lineHeight = 21.sp)
}

@Composable
fun StatBar(fraction: Float, modifier: Modifier = Modifier) {
    val f = fraction.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .background(Color(0xFF232C57), CircleShape),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(f)
                .height(8.dp)
                .background(C.Aurora, CircleShape),
        )
    }
}

@Composable
fun Dot(color: Color) {
    Box(
        modifier = Modifier
            .size(12.dp)
            .background(color, CircleShape),
    )
}
