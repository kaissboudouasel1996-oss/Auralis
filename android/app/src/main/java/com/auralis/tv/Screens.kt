package com.auralis.tv

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

typealias Toaster = (String) -> Unit

// ---------------------------------------------------------------------------------------------
// Shared pieces
// ---------------------------------------------------------------------------------------------

@Composable
fun ChoiceDialog(
    title: String,
    message: String,
    choices: List<Pair<String, () -> Unit>>,
    onDismiss: () -> Unit,
) {
    val cancelFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        LaunchedEffect(Unit) { runCatching { cancelFocus.requestFocus() } }
        Panel(Modifier.fillMaxWidth()) {
            Text(title, color = C.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Body(message)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                choices.forEach { (label, action) ->
                    TvButton(label, primary = true) {
                        onDismiss()
                        action()
                    }
                }
                TvButton("Cancel", modifier = Modifier.focusRequester(cancelFocus)) { onDismiss() }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ButtonRow(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

/** A button that runs one or more shell commands on the TV, then reports back. */
@Composable
private fun AdbButton(
    label: String,
    cmds: List<String>,
    ok: String,
    toast: Toaster,
    primary: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    TvButton(label, primary = primary) {
        scope.launch {
            var err: String? = null
            for (c in cmds) {
                val r = AdbManager.run(c)
                if (r.isFailure) {
                    err = r.exceptionOrNull()?.message ?: "ADB error"
                    break
                }
            }
            toast(err ?: ok)
        }
    }
}

@Composable
private fun AdbWarning() {
    if (!AdbManager.connected) {
        Panel(Modifier.fillMaxWidth()) {
            Text("ADB is not connected", color = C.Warn, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Body("These tools need it. Open Home and press Connect (steps are shown there).")
        }
    }
}

@Composable
private fun Stat(modifier: Modifier, label: String, value: String, sub: String, fraction: Float? = null) {
    Panel(modifier) {
        Text(label, color = C.Muted, fontSize = 13.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(value, color = C.Text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(sub, color = C.Muted, fontSize = 14.sp)
        if (fraction != null) {
            Spacer(Modifier.height(12.dp))
            StatBar(fraction)
        }
    }
}

private fun openSettings(ctx: Context, action: String, toast: Toaster) {
    try {
        ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        toast("This screen is not available on your TV")
    }
}

// ---------------------------------------------------------------------------------------------
// Home
// ---------------------------------------------------------------------------------------------

@Composable
fun HomeScreen(toast: Toaster) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var port by remember { mutableIntStateOf(Prefs.port(ctx)) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(4000)
            tick++
        }
    }
    val mem = remember(tick) { Sys.memory(ctx) }
    val sto = remember(tick) { Sys.storage() }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Title(
            Sys.device(),
            "Android ${Build.VERSION.RELEASE}   |   ${Sys.resolution(ctx)}   |   on for ${Sys.uptime()}",
        )

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            Stat(
                Modifier.weight(1f), "MEMORY", "${Sys.mb(mem.first)} free",
                "of ${Sys.gb(mem.second)}", (mem.second - mem.first).toFloat() / mem.second,
            )
            Stat(
                Modifier.weight(1f), "STORAGE", "${Sys.gb(sto.first)} free",
                "of ${Sys.gb(sto.second)}", (sto.second - sto.first).toFloat() / sto.second,
            )
            Stat(Modifier.weight(1f), "NETWORK", Sys.localIp() ?: "Offline", Sys.wifi(ctx) ?: "Ethernet or no Wi-Fi")
        }

        Panel(Modifier.fillMaxWidth()) {
            Heading("ADB CONNECTION")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Dot(
                    when (AdbManager.state) {
                        AdbManager.State.CONNECTED -> C.Ok
                        AdbManager.State.CONNECTING -> C.Warn
                        AdbManager.State.FAILED -> C.Err
                        AdbManager.State.DISCONNECTED -> C.Muted
                    },
                )
                Text(AdbManager.message, color = C.Text, fontSize = 16.sp)
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton(if (AdbManager.connected) "Reconnect" else "Connect", primary = true) {
                    scope.launch { AdbManager.connect(ctx) }
                }
                TvButton("-") {
                    port = (port - 1).coerceAtLeast(1024)
                    Prefs.setPort(ctx, port)
                }
                Text("Port $port", color = C.Text, fontSize = 16.sp)
                TvButton("+") {
                    port = (port + 1).coerceAtMost(65535)
                    Prefs.setPort(ctx, port)
                }
            }
            Spacer(Modifier.height(14.dp))
            Body(
                "To unlock the Pro tools: Settings > Device Preferences > About > press OK on Build 7 times. " +
                    "Then in Developer options turn on USB debugging (and network / ADB debugging if listed). " +
                    "Press Connect and choose \"Always allow\" in the prompt on the TV.",
            )
        }

        ButtonRow {
            TvButton("Ambient clock") { ctx.startActivity(Intent(ctx, AmbientActivity::class.java)) }
            TvButton("Developer options") {
                openSettings(ctx, Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, toast)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Boost
// ---------------------------------------------------------------------------------------------

@Composable
fun BoostScreen(toast: Toaster) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var lastMb by remember { mutableLongStateOf(Prefs.lastBoostMb(ctx)) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(3000)
            tick++
        }
    }
    val mem = remember(tick) { Sys.memory(ctx) }

    fun boost() {
        if (busy) return
        busy = true
        scope.launch {
            val before = Sys.memory(ctx).first
            withContext(Dispatchers.IO) { Sys.killBackground(ctx) }
            var extra = ""
            if (AdbManager.connected) {
                AdbManager.run("am kill-all")
                AdbManager.run("pm trim-caches 999G")
                extra = " and caches cleared"
            }
            delay(1500)
            val freed = ((Sys.memory(ctx).first - before) / 1048576).coerceAtLeast(0)
            lastMb = freed
            Prefs.setLastBoostMb(ctx, freed)
            toast("Freed $freed MB$extra")
            tick++
            busy = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Title("Boost", "Close background apps so the TV feels fresh again")
        Stat(
            Modifier.fillMaxWidth(), "FREE MEMORY", Sys.mb(mem.first), "of ${Sys.gb(mem.second)}",
            (mem.second - mem.first).toFloat() / mem.second,
        )
        if (lastMb >= 0) Body("Last boost freed $lastMb MB.")
        ButtonRow {
            TvButton(if (busy) "Boosting..." else "Boost now", primary = true) { boost() }
            AdbButton("Clear all app caches", listOf("pm trim-caches 999G"), "Caches cleared", toast)
        }
        Body(
            "Boost works on its own. With ADB connected it also stops everything running in the background " +
                "and clears every app's cache. Android refills memory with cached apps on purpose, so the free " +
                "number will drift back up and down; that is normal.",
        )
        AdbWarning()
    }
}

// ---------------------------------------------------------------------------------------------
// Apps (debloat)
// ---------------------------------------------------------------------------------------------

private data class AppItem(val label: String, val pkg: String, val size: Long, val system: Boolean)

// Packages the TV needs to work: never offered for removal.
private val protectedParts = listOf(
    "launcher", "tvhome", "settings", "systemui", "update", "updater", "ota",
    "provision", "inputmethod", "bluetooth", "permissioncontroller", "packageinstaller",
    "webview", "gms", "gsf", "vending", "tv.provider", "tvprovider", "keychain",
    "certinstaller", "externalstorage", "mediaprovider", "documentsui", "telephony",
    "com.auralis.tv", "wifi", "network", "tts", "setupwraith", "remotecontrol", "mitv.service",
)

private fun loadApps(ctx: Context): List<AppItem> {
    val pm = ctx.packageManager
    return pm.getInstalledApplications(0).mapNotNull { ai ->
        val p = ai.packageName.lowercase()
        if (!ai.enabled || p == "android" || protectedParts.any { p.contains(it) }) return@mapNotNull null
        val size = try {
            File(ai.sourceDir).length()
        } catch (_: Exception) {
            0L
        }
        AppItem(
            pm.getApplicationLabel(ai).toString(), ai.packageName, size,
            (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
        )
    }.sortedByDescending { it.size }
}

@Composable
private fun ListRow(title: String, sub: String, trailing: String, onClick: () -> Unit) {
    Focusable(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = C.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(sub, color = C.Muted, fontSize = 13.sp, maxLines = 1)
            }
            Text(trailing, color = C.Muted, fontSize = 14.sp)
        }
    }
}

@Composable
fun AppsScreen(toast: Toaster) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf<List<AppItem>?>(null) }
    var removed by remember { mutableStateOf(Removed.all(ctx)) }
    var tab by remember { mutableIntStateOf(0) } // 0 all, 1 installed, 2 system, 3 restore
    var selected by remember { mutableStateOf<AppItem?>(null) }
    var restoring by remember { mutableStateOf<RemovedApp?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(reload) {
        apps = withContext(Dispatchers.IO) { loadApps(ctx) }
        removed = Removed.all(ctx)
    }

    fun remove(app: AppItem, disable: Boolean) {
        scope.launch {
            val cmd = if (disable) "pm disable-user --user 0 ${app.pkg}" else "pm uninstall --user 0 ${app.pkg}"
            val r = AdbManager.run(cmd)
            if (r.isFailure) {
                toast(r.exceptionOrNull()?.message ?: "ADB error")
            } else {
                val out = r.getOrDefault("")
                if (out.contains("Success") || out.contains("disabled")) {
                    Removed.add(ctx, app.pkg, app.label, disable)
                    toast("${app.label}: ${if (disable) "disabled" else "removed"}")
                    reload++
                } else {
                    toast("${app.label}: ${out.ifBlank { "refused by the TV" }}")
                }
            }
        }
    }

    fun restore(app: RemovedApp) {
        scope.launch {
            val cmd = if (app.disabled) "pm enable --user 0 ${app.pkg}" else "pm install-existing --user 0 ${app.pkg}"
            val r = AdbManager.run(cmd)
            if (r.isFailure) {
                toast(r.exceptionOrNull()?.message ?: "ADB error")
            } else {
                val out = r.getOrDefault("").lowercase()
                if (out.contains("installed") || out.contains("enabled")) {
                    Removed.remove(ctx, app.pkg)
                    toast("${app.label}: restored")
                    reload++
                } else {
                    toast("${app.label}: ${out.ifBlank { "could not restore" }}")
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(8.dp)) {
        Title("Apps", "Remove apps you never use. Everything here can be restored.")
        Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvButton("All", selected = tab == 0) { tab = 0 }
            TvButton("Installed", selected = tab == 1) { tab = 1 }
            TvButton("System", selected = tab == 2) { tab = 2 }
            TvButton("Restore (${removed.size})", selected = tab == 3) { tab = 3 }
        }
        if (!AdbManager.connected) {
            Body("ADB is not connected. Open Home and press Connect before removing apps.", C.Warn)
            Spacer(Modifier.height(8.dp))
        }

        if (tab == 3) {
            if (removed.isEmpty()) {
                Body("Nothing removed yet.")
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(removed, key = { it.pkg }) { a ->
                        ListRow(a.label, a.pkg, if (a.disabled) "disabled" else "removed") { restoring = a }
                    }
                }
            }
        } else {
            val shown = apps?.filter {
                when (tab) {
                    0 -> true
                    1 -> !it.system
                    else -> it.system
                }
            }
            if (shown == null) {
                Body("Loading...")
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(shown, key = { it.pkg }) { a ->
                        ListRow(
                            a.label,
                            a.pkg + "   |   " + if (a.system) "system" else "installed",
                            String.format(Locale.US, "%.1f MB", a.size / 1048576.0),
                        ) { selected = a }
                    }
                }
            }
        }
    }

    selected?.let { app ->
        ChoiceDialog(
            title = app.label,
            message = "${app.pkg}\n\nDisable hides it and stops it running. Uninstall removes it for this user. " +
                "Both can be undone from Restore.",
            choices = listOf(
                "Disable" to { remove(app, true) },
                "Uninstall" to { remove(app, false) },
            ),
            onDismiss = { selected = null },
        )
    }
    restoring?.let { app ->
        ChoiceDialog(
            title = "Restore ${app.label}?",
            message = app.pkg,
            choices = listOf("Restore" to { restore(app) }),
            onDismiss = { restoring = null },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Pro tools
// ---------------------------------------------------------------------------------------------

@Composable
fun ToolsScreen(toast: Toaster) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var askReboot by remember { mutableStateOf(false) }

    fun anim(scale: String) = listOf(
        "settings put global window_animation_scale $scale; " +
            "settings put global transition_animation_scale $scale; " +
            "settings put global animator_duration_scale $scale",
    )

    fun dns(host: String) = listOf(
        "settings put global private_dns_specifier $host; settings put global private_dns_mode hostname",
    )

    fun sleepTimer(minutes: Int) {
        scope.launch {
            AdbManager.run("pkill -f '[a]uralis-sleep'")
            val r = AdbManager.run(
                "nohup sh -c 'sleep ${minutes * 60}; input keyevent 223 # auralis-sleep' >/dev/null 2>&1 &",
            )
            toast(if (r.isSuccess) "The TV will go to sleep in $minutes minutes" else r.exceptionOrNull()?.message ?: "ADB error")
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Title("Pro tools", "Things that normally hide behind Developer options")
        AdbWarning()

        Heading("SPEED: SYSTEM ANIMATIONS")
        ButtonRow {
            AdbButton("Off (fastest)", anim("0"), "Animations off", toast)
            AdbButton("0.5x (snappy)", anim("0.5"), "Animations at 0.5x", toast)
            AdbButton("1x (default)", anim("1"), "Animations back to normal", toast)
        }

        Heading("HDMI-CEC (TV controls your box, soundbar and console)")
        ButtonRow {
            AdbButton("Turn on", listOf("settings put global hdmi_control_enabled 1"), "HDMI-CEC on", toast)
            AdbButton("Turn off", listOf("settings put global hdmi_control_enabled 0"), "HDMI-CEC off", toast)
        }

        Heading("PRIVATE DNS (blocks ads and trackers system-wide with AdGuard)")
        ButtonRow {
            AdbButton("AdGuard", dns("dns.adguard-dns.com"), "Private DNS: AdGuard", toast)
            AdbButton("Cloudflare", dns("one.one.one.one"), "Private DNS: Cloudflare", toast)
            AdbButton("Google", dns("dns.google"), "Private DNS: Google", toast)
            AdbButton("Automatic", listOf("settings put global private_dns_mode opportunistic"), "Private DNS: automatic", toast)
            AdbButton("Off", listOf("settings put global private_dns_mode off"), "Private DNS off", toast)
        }

        Heading("SCREENSAVER")
        ButtonRow {
            AdbButton(
                "Use Aurora screensaver",
                listOf(
                    "settings put secure screensaver_components com.auralis.tv/com.auralis.tv.AuroraDream; " +
                        "settings put secure screensaver_enabled 1",
                ),
                "Aurora is now your screensaver", toast, primary = true,
            )
            AdbButton(
                "Back to default",
                listOf("settings delete secure screensaver_components"),
                "Default screensaver restored", toast,
            )
            TvButton("Screensaver settings") { openSettings(ctx, Settings.ACTION_DREAM_SETTINGS, toast) }
        }

        Heading("SLEEP TIMER")
        ButtonRow {
            listOf(15, 30, 60, 90).forEach { m -> TvButton("$m min") { sleepTimer(m) } }
            AdbButton("Cancel timer", listOf("pkill -f '[a]uralis-sleep'"), "Sleep timer cancelled", toast)
        }

        Heading("CAPTURE AND POWER")
        ButtonRow {
            AdbButton(
                "Screenshot",
                listOf("mkdir -p /sdcard/Pictures; screencap -p /sdcard/Pictures/Auralis_$(date +%Y%m%d_%H%M%S).png"),
                "Screenshot saved in Pictures", toast,
            )
            TvButton("Restart TV") { askReboot = true }
        }

        Heading("SHORTCUTS")
        ButtonRow {
            TvButton("Developer options") { openSettings(ctx, Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, toast) }
            TvButton("Wi-Fi") { openSettings(ctx, Settings.ACTION_WIFI_SETTINGS, toast) }
            TvButton("Display") { openSettings(ctx, Settings.ACTION_DISPLAY_SETTINGS, toast) }
            TvButton("Storage") { openSettings(ctx, Settings.ACTION_INTERNAL_STORAGE_SETTINGS, toast) }
            TvButton("Apps") { openSettings(ctx, Settings.ACTION_APPLICATION_SETTINGS, toast) }
            TvButton("Accessibility") { openSettings(ctx, Settings.ACTION_ACCESSIBILITY_SETTINGS, toast) }
            TvButton("Date and time") { openSettings(ctx, Settings.ACTION_DATE_SETTINGS, toast) }
        }
    }

    if (askReboot) {
        ChoiceDialog(
            title = "Restart the TV?",
            message = "It will turn off and start again in about a minute.",
            choices = listOf(
                "Restart" to {
                    scope.launch { AdbManager.run("reboot") }
                    Unit
                },
            ),
            onDismiss = { askReboot = false },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Network
// ---------------------------------------------------------------------------------------------

@Composable
fun NetworkScreen(toast: Toaster) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var live by remember { mutableStateOf<Double?>(null) }
    var result by remember { mutableStateOf<Double?>(null) }
    var ping by remember { mutableStateOf<Long?>(null) }

    fun start() {
        if (running) return
        running = true
        live = null
        result = null
        ping = null
        scope.launch {
            try {
                ping = SpeedTest.latencyMs()
                result = SpeedTest.download { live = it }
            } catch (e: Exception) {
                toast("Speed test failed. Check the TV's internet connection.")
            }
            running = false
        }
    }

    val shown = result ?: live
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Title("Network", "Is your connection good enough for 4K?")
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            Stat(Modifier.weight(1f), "IP ADDRESS", Sys.localIp() ?: "Offline", Sys.wifi(ctx) ?: "Ethernet or no Wi-Fi")
            Stat(
                Modifier.weight(1f), "DOWNLOAD",
                if (shown != null) String.format(Locale.US, "%.0f Mbps", shown) else "-",
                when {
                    running -> "Measuring..."
                    ping != null -> "Latency ${ping} ms"
                    else -> "Not tested yet"
                },
            )
        }
        ButtonRow {
            TvButton(if (running) "Testing..." else "Start speed test", primary = true) { start() }
        }
        result?.let {
            val verdict = when {
                it >= 50 -> "Excellent: 4K HDR and Dolby Vision streaming will be smooth."
                it >= 25 -> "Good: enough for 4K streaming (Netflix and YouTube ask for about 25 Mbps)."
                it >= 10 -> "OK for Full HD, but 4K may buffer. Try a closer router, 5 GHz Wi-Fi or an Ethernet cable."
                else -> "Slow: even HD may buffer. Move the router closer or use an Ethernet cable."
            }
            Body(verdict, C.Text)
        }
        Body("The test downloads for about 8 seconds from Cloudflare's speed-test server, so it uses roughly 50 to 150 MB.")
    }
}
