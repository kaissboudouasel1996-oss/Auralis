package com.auralis.tv

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dadb.AdbKeyPair
import dadb.Dadb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("auralis", Context.MODE_PRIVATE)

    fun port(c: Context): Int = sp(c).getInt("port", 5555)
    fun setPort(c: Context, port: Int) = sp(c).edit().putInt("port", port).apply()

    fun lastBoostMb(c: Context): Long = sp(c).getLong("last_boost_mb", -1L)
    fun setLastBoostMb(c: Context, mb: Long) = sp(c).edit().putLong("last_boost_mb", mb).apply()
}

/**
 * One shared connection to the TV's own ADB daemon (network debugging, 127.0.0.1).
 * The "Pro" tools (uninstall, settings, screenshots...) all go through [run].
 */
object AdbManager {
    enum class State { DISCONNECTED, CONNECTING, CONNECTED, FAILED }

    var state by mutableStateOf(State.DISCONNECTED)
        private set
    var message by mutableStateOf("Not connected")
        private set

    private var dadb: Dadb? = null

    val connected: Boolean get() = state == State.CONNECTED

    private fun keys(ctx: Context): AdbKeyPair {
        val priv = File(ctx.filesDir, "adbkey")
        val pub = File(ctx.filesDir, "adbkey.pub")
        if (!priv.exists() || !pub.exists()) AdbKeyPair.generate(priv, pub)
        return AdbKeyPair.read(priv, pub)
    }

    suspend fun connect(ctx: Context) {
        val app = ctx.applicationContext
        val port = Prefs.port(app)
        state = State.CONNECTING
        message = "Connecting to port $port. Accept the prompt on the TV and tick \"Always allow\"."
        withContext(Dispatchers.IO) {
            try {
                runCatching { dadb?.close() }
                val d = Dadb.create("127.0.0.1", port, keys(app))
                d.shell("echo ok")
                dadb = d
                state = State.CONNECTED
                message = "Connected to the TV on port $port"
            } catch (e: Exception) {
                dadb = null
                state = State.FAILED
                message = "Could not connect on port $port. Turn on USB / network debugging in Developer options."
            }
        }
    }

    /** Runs one shell command on the TV. Fails (Result.failure) if ADB is not connected. */
    suspend fun run(command: String): Result<String> = withContext(Dispatchers.IO) {
        val d = dadb ?: return@withContext Result.failure(IllegalStateException("Not connected. Open Home and press Connect."))
        try {
            Result.success(d.shell(command).allOutput.trim())
        } catch (e: Exception) {
            dadb = null
            state = State.FAILED
            message = "Connection lost. Press Connect to reconnect."
            Result.failure(e)
        }
    }
}

/** Makes Auralis the TV's home screen (and puts the original one back), through the TV's own ADB. */
object HomeSetter {
    private const val OURS = "com.auralis.tv/com.auralis.tv.LauncherActivity"
    private const val KEY = "orig_home"

    private fun sp(c: Context) = c.getSharedPreferences("auralis", Context.MODE_PRIVATE)
    private fun homeIntent() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

    /** The home screen currently in charge, unless it is already Auralis. */
    private fun currentHome(ctx: Context): String? {
        val ai = ctx.packageManager
            .resolveActivity(homeIntent(), PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo ?: return null
        if (ai.packageName == ctx.packageName || ai.packageName == "android") return null
        return ComponentName(ai.packageName, ai.name).flattenToShortString()
    }

    private fun anyOtherHome(ctx: Context): String? =
        ctx.packageManager.queryIntentActivities(homeIntent(), 0)
            .map { it.activityInfo }
            .firstOrNull { it.packageName != ctx.packageName }
            ?.let { ComponentName(it.packageName, it.name).flattenToShortString() }

    suspend fun apply(ctx: Context): Result<String> {
        currentHome(ctx)?.let { sp(ctx).edit().putString(KEY, it).apply() }
        return AdbManager.run("cmd package set-home-activity --user 0 $OURS")
    }

    suspend fun restore(ctx: Context): Result<String> {
        val orig = sp(ctx).getString(KEY, null) ?: anyOtherHome(ctx)
            ?: return Result.failure(IllegalStateException("No original home screen found"))
        return AdbManager.run("cmd package set-home-activity --user 0 $orig")
    }
}

/** Apps removed or disabled through Auralis, so they can be restored. */
data class RemovedApp(val pkg: String, val label: String, val disabled: Boolean)

object Removed {
    private const val KEY = "removed"
    private fun sp(c: Context) = c.getSharedPreferences("auralis", Context.MODE_PRIVATE)

    fun all(c: Context): List<RemovedApp> {
        val set = sp(c).getStringSet(KEY, emptySet()) ?: emptySet()
        return set.mapNotNull {
            val p = it.split("|", limit = 3)
            if (p.size < 3) null else RemovedApp(p[0], p[2], p[1] == "d")
        }.sortedBy { it.label.lowercase() }
    }

    fun add(c: Context, pkg: String, label: String, disabled: Boolean) {
        val s = HashSet(sp(c).getStringSet(KEY, emptySet()) ?: emptySet())
        s.removeAll { it.startsWith("$pkg|") }
        s.add("$pkg|${if (disabled) "d" else "u"}|$label")
        sp(c).edit().putStringSet(KEY, s).apply()
    }

    fun remove(c: Context, pkg: String) {
        val s = HashSet(sp(c).getStringSet(KEY, emptySet()) ?: emptySet())
        s.removeAll { it.startsWith("$pkg|") }
        sp(c).edit().putStringSet(KEY, s).apply()
    }
}
