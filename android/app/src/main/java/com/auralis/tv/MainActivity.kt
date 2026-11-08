package com.auralis.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

enum class Section(val title: String) {
    HOME("Home"),
    BOOST("Boost"),
    APPS("Apps"),
    TOOLS("Pro tools"),
    NETWORK("Network"),
}

private class ToastMsg(val text: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AuralisTheme { Root() }
        }
    }
}

@Composable
private fun Root() {
    val ctx = LocalContext.current
    var section by remember { mutableStateOf(Section.HOME) }
    var toast by remember { mutableStateOf<ToastMsg?>(null) }
    val requesters = remember { List(Section.values().size) { FocusRequester() } }

    LaunchedEffect(Unit) { runCatching { requesters[0].requestFocus() } }
    LaunchedEffect(Unit) { AdbManager.connect(ctx) }
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(3500)
            toast = null
        }
    }
    val show: Toaster = { toast = ToastMsg(it) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(Color(0xFF17205E), C.Bg), radius = 1600f)),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 27.dp)) {
            // Navigation rail. Moving the focus onto an item opens that section.
            Column(
                modifier = Modifier
                    .width(230.dp)
                    .fillMaxHeight()
                    .focusProperties { enter = { requesters[section.ordinal] } }
                    .focusGroup(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 20.dp),
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_fg),
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                    )
                    Text("Auralis", color = C.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
                Section.values().forEachIndexed { i, s ->
                    Focusable(
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(requesters[i])
                            .onFocusChanged { if (it.isFocused) section = s },
                        shape = RoundedCornerShape(14.dp),
                        base = if (s == section) Color(0xFF1A2354) else Color.Transparent,
                        onClick = { section = s },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        ) {
                            Box(
                                Modifier
                                    .width(4.dp)
                                    .height(22.dp)
                                    .background(
                                        if (s == section) C.Cyan else Color.Transparent,
                                        RoundedCornerShape(2.dp),
                                    ),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                s.title,
                                color = if (s == section) C.Text else C.Muted,
                                fontSize = 18.sp,
                                fontWeight = if (s == section) FontWeight.SemiBold else FontWeight.Medium,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.width(32.dp))

            Box(Modifier.weight(1f).fillMaxHeight()) {
                when (section) {
                    Section.HOME -> HomeScreen(show)
                    Section.BOOST -> BoostScreen(show)
                    Section.APPS -> AppsScreen(show)
                    Section.TOOLS -> ToolsScreen(show)
                    Section.NETWORK -> NetworkScreen(show)
                }
            }
        }

        toast?.let {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 40.dp)
                    .background(Color(0xFF26346E), RoundedCornerShape(24.dp))
                    .padding(horizontal = 28.dp, vertical = 14.dp),
            ) {
                Text(it.text, color = C.Text, fontSize = 17.sp)
            }
        }
    }
}
