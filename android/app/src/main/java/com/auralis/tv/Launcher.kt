package com.auralis.tv

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------------------------------------
// Data
// ---------------------------------------------------------------------------------------------

class LApp(val pkg: String, val label: String, val intent: Intent, val ri: ResolveInfo)

private class TileArt(val banner: ImageBitmap?, val icon: ImageBitmap?)

/** Favorites, in the order the user arranged them. */
object Pins {
    private const val KEY = "pins"
    private const val SEEDED = "pins_seeded"
    private fun sp(c: Context) = c.getSharedPreferences("auralis", Context.MODE_PRIVATE)

    // Streaming apps pinned the first time the launcher opens (only the ones that are installed).
    private val starters = listOf(
        "com.google.android.youtube.tv",
        "com.netflix.ninja",
        "com.amazon.amazonvideo.livingroom",
        "com.disney.disneyplus",
        "com.spotify.tv.android",
        "com.plexapp.android",
        "org.videolan.vlc",
        "org.xbmc.kodi",
        "com.auralis.tv",
    )

    fun get(c: Context, available: List<LApp>): List<String> {
        val s = sp(c)
        if (!s.getBoolean(SEEDED, false) && available.isNotEmpty()) {
            val seed = starters.filter { p -> available.any { it.pkg == p } }
            s.edit().putString(KEY, seed.joinToString("\n")).putBoolean(SEEDED, true).apply()
        }
        val raw = s.getString(KEY, "") ?: ""
        return if (raw.isEmpty()) emptyList() else raw.split("\n")
    }

    fun set(c: Context, pins: List<String>) {
        sp(c).edit().putString(KEY, pins.joinToString("\n")).putBoolean(SEEDED, true).apply()
    }
}

private fun loadLauncherApps(ctx: Context): List<LApp> {
    val pm = ctx.packageManager
    val found = LinkedHashMap<String, LApp>()
    for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
        for (ri in pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(cat), 0)) {
            val pkg = ri.activityInfo.packageName
            if (found.containsKey(pkg)) continue
            val intent = Intent(Intent.ACTION_MAIN)
                .addCategory(cat)
                .setClassName(pkg, ri.activityInfo.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            found[pkg] = LApp(pkg, ri.loadLabel(pm).toString(), intent, ri)
        }
    }
    return found.values.sortedBy { it.label.lowercase() }
}

private fun loadArt(ctx: Context, app: LApp): TileArt {
    val pm = ctx.packageManager
    val banner = try {
        app.ri.activityInfo.loadBanner(pm)?.toBitmap(320, 180)?.asImageBitmap()
    } catch (_: Exception) {
        null
    }
    val icon = if (banner != null) {
        null
    } else {
        try {
            app.ri.loadIcon(pm).toBitmap(128, 128).asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }
    return TileArt(banner, icon)
}

private val tilePalette = listOf(
    Color(0xFF2B3A8F) to Color(0xFF6A3FC4),
    Color(0xFF0F5C7A) to Color(0xFF2B3A8F),
    Color(0xFF6A3FC4) to Color(0xFFB0408F),
    Color(0xFF14607A) to Color(0xFF4D7CFF),
    Color(0xFF3A2C8F) to Color(0xFF1E8FA8),
)

// ---------------------------------------------------------------------------------------------
// Activity
// ---------------------------------------------------------------------------------------------

/** Auralis home screen. Set it as the TV's home from Pro tools > Launcher. */
class LauncherActivity : ComponentActivity() {
    private var resumes by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AuralisTheme { LauncherScreen(resumes) } }
    }

    override fun onResume() {
        super.onResume()
        resumes++
    }
}

// ---------------------------------------------------------------------------------------------
// UI
// ---------------------------------------------------------------------------------------------

@Composable
fun LauncherScreen(resumes: Int) {
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf<List<LApp>>(emptyList()) }
    var pins by remember { mutableStateOf<List<String>>(emptyList()) }
    var menuFor by remember { mutableStateOf<LApp?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val firstFocus = remember { FocusRequester() }

    BackHandler(enabled = true) { /* a home screen has nowhere to go back to */ }

    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            now = System.currentTimeMillis()
        }
    }
    LaunchedEffect(resumes) {
        val list = withContext(Dispatchers.IO) { loadLauncherApps(ctx) }
        apps = list
        pins = Pins.get(ctx, list)
    }
    LaunchedEffect(apps.isNotEmpty()) {
        if (apps.isNotEmpty()) {
            delay(250)
            runCatching { firstFocus.requestFocus() }
        }
    }

    val byPkg = apps.associateBy { it.pkg }
    val pinned = pins.mapNotNull { byPkg[it] }

    fun open(app: LApp) {
        try {
            ctx.startActivity(app.intent)
        } catch (e: Exception) {
            Toast.makeText(ctx, "${app.label} could not be opened", Toast.LENGTH_SHORT).show()
        }
    }

    fun savePins(list: List<String>) {
        pins = list
        Pins.set(ctx, list)
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { AuroraView(it, showClock = false) }, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x66070B1E), Color(0xCC070B1E)))))

        LazyVerticalGrid(
            columns = GridCells.Adaptive(170.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 27.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { Header(ctx, now) }

            item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel("FAVORITES") }
            item(span = { GridItemSpan(maxLineSpan) }) {
                if (pinned.isEmpty()) {
                    Body("Hold OK on any app below to pin it here.")
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        items(pinned, key = { it.pkg }) { app ->
                            val first = app === pinned.first()
                            AppTile(
                                app = app,
                                modifier = Modifier
                                    .width(250.dp)
                                    .aspectRatio(16f / 9f)
                                    .then(if (first) Modifier.focusRequester(firstFocus) else Modifier),
                                onOpen = { open(app) },
                                onMenu = { menuFor = app },
                            )
                        }
                    }
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel("ALL APPS") }
            items(apps, key = { it.pkg }) { app ->
                val first = pinned.isEmpty() && app === apps.first()
                AppTile(
                    app = app,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .then(if (first) Modifier.focusRequester(firstFocus) else Modifier),
                    onOpen = { open(app) },
                    onMenu = { menuFor = app },
                )
            }
        }
    }

    menuFor?.let { app ->
        val isPinned = app.pkg in pins
        val idx = pins.indexOf(app.pkg)
        val choices = buildList<Pair<String, () -> Unit>> {
            if (isPinned) {
                add("Unpin" to { savePins(pins - app.pkg) })
                if (idx > 0) {
                    add("Move left" to {
                        val l = pins.toMutableList()
                        l.removeAt(idx)
                        l.add(idx - 1, app.pkg)
                        savePins(l)
                    })
                }
                if (idx < pins.size - 1) {
                    add("Move right" to {
                        val l = pins.toMutableList()
                        l.removeAt(idx)
                        l.add(idx + 1, app.pkg)
                        savePins(l)
                    })
                }
            } else {
                add("Pin to favorites" to { savePins(pins + app.pkg) })
            }
            add("App info" to {
                try {
                    ctx.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                } catch (e: Exception) {
                    Toast.makeText(ctx, "App info is not available on this TV", Toast.LENGTH_SHORT).show()
                }
            })
        }
        ChoiceDialog(
            title = app.label,
            message = app.pkg,
            choices = choices,
            onDismiss = { menuFor = null },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = C.Cyan,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 2.sp,
        modifier = Modifier.padding(start = 14.dp, top = 6.dp),
    )
}

@Composable
private fun Header(ctx: Context, now: Long) {
    val date = Date(now)
    val hour = SimpleDateFormat("H", Locale.getDefault()).format(date).toInt()
    val greeting = when {
        hour < 5 -> "Good night"
        hour < 12 -> "Good morning"
        hour < 18 -> "Good afternoon"
        else -> "Good evening"
    }
    val mem = remember(now) { Sys.memory(ctx) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(greeting, color = C.Cyan, fontSize = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp)
            Text(
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(date),
                style = TextStyle(brush = C.Aurora, fontSize = 76.sp, fontWeight = FontWeight.Light),
            )
            Text(
                SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(date),
                color = C.Muted,
                fontSize = 18.sp,
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "${Sys.mb(mem.first)} free   |   ${Sys.localIp() ?: "Offline"}",
                color = C.Muted,
                fontSize = 14.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton("Auralis tools") { ctx.startActivity(Intent(ctx, MainActivity::class.java)) }
                TvButton("Settings") {
                    try {
                        ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (e: Exception) {
                        Toast.makeText(ctx, "Settings could not be opened", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}

/**
 * One app. OK / Enter opens it; holding OK opens the pin menu. Tap and long-press work with a mouse too.
 * Uses the app's TV banner when it has one, otherwise its icon on an aurora gradient.
 */
@Composable
private fun AppTile(
    app: LApp,
    modifier: Modifier,
    onOpen: () -> Unit,
    onMenu: () -> Unit,
) {
    val ctx = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    var longFired by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.1f else 1f, label = "tileScale")
    val art by produceState<TileArt?>(null, app.pkg) {
        value = withContext(Dispatchers.IO) { loadArt(ctx, app) }
    }
    val shape = RoundedCornerShape(14.dp)
    val colors = tilePalette[(app.pkg.hashCode() and 0x7fffffff) % tilePalette.size]

    Box(
        modifier = modifier
            .scale(scale)
            .shadow(if (focused) 28.dp else 0.dp, shape, ambientColor = C.Cyan, spotColor = C.Violet)
            .then(if (focused) Modifier.border(3.dp, C.Aurora, shape) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { ev ->
                val k = ev.key
                if (k == Key.DirectionCenter || k == Key.Enter || k == Key.NumPadEnter) {
                    when (ev.type) {
                        KeyEventType.KeyDown -> {
                            if (ev.nativeKeyEvent.repeatCount == 0) {
                                longFired = false
                            } else if (!longFired) {
                                longFired = true
                                onMenu()
                            }
                            true
                        }
                        KeyEventType.KeyUp -> {
                            if (!longFired) onOpen()
                            longFired = false
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
            .pointerInput(Unit) { detectTapGestures(onTap = { onOpen() }, onLongPress = { onMenu() }) }
            .focusable(),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(shape)
                .background(Brush.linearGradient(listOf(colors.first, colors.second))),
        ) {
            val a = art
            if (a?.banner != null) {
                Image(
                    bitmap = a.banner,
                    contentDescription = app.label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (a?.icon != null) {
                Image(
                    bitmap = a.icon,
                    contentDescription = app.label,
                    modifier = Modifier.align(Alignment.Center).size(56.dp),
                )
            }

            // Label: always for icon-only tiles, only while focused for banner tiles.
            if (focused || a?.banner == null) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Text(
                        app.label,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
