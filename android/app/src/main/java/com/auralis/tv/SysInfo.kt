package com.auralis.tv

import android.app.ActivityManager
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URL
import java.util.Collections
import java.util.Locale

object Sys {
    /** (available, total) RAM in bytes. */
    fun memory(ctx: Context): Pair<Long, Long> {
        val am = ctx.getSystemService(ActivityManager::class.java)
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return mi.availMem to mi.totalMem
    }

    /** (available, total) internal storage in bytes. */
    fun storage(): Pair<Long, Long> {
        val st = StatFs(Environment.getDataDirectory().path)
        return st.availableBytes to st.totalBytes
    }

    fun localIp(): String? {
        try {
            for (ni in Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (a in Collections.list(ni.inetAddresses)) {
                    if (!a.isLoopbackAddress && a is Inet4Address && a.isSiteLocalAddress) return a.hostAddress
                }
            }
        } catch (_: Exception) {
        }
        return null
    }

    fun uptime(): String {
        val total = SystemClock.elapsedRealtime() / 1000
        val d = total / 86400
        val h = (total % 86400) / 3600
        val m = (total % 3600) / 60
        return when {
            d > 0 -> "${d}d ${h}h"
            h > 0 -> "${h}h ${m}m"
            else -> "${m}m"
        }
    }

    fun resolution(ctx: Context): String {
        val dm = ctx.resources.displayMetrics
        return "${dm.widthPixels} x ${dm.heightPixels}"
    }

    fun device(): String {
        val maker = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        return if (Build.MODEL.startsWith(maker, ignoreCase = true)) Build.MODEL else "$maker ${Build.MODEL}"
    }

    /** "866 Mbps, -52 dBm" when on Wi-Fi, null otherwise (Ethernet or offline). */
    @Suppress("DEPRECATION")
    fun wifi(ctx: Context): String? {
        val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
        val info = wm.connectionInfo ?: return null
        if (info.networkId == -1) return null
        return "${info.linkSpeed} Mbps link, ${info.rssi} dBm"
    }

    /** Asks Android to stop the background processes of every other app. Works without ADB. */
    fun killBackground(ctx: Context) {
        val am = ctx.getSystemService(ActivityManager::class.java)
        for (ai in ctx.packageManager.getInstalledApplications(0)) {
            if (ai.packageName != ctx.packageName) am.killBackgroundProcesses(ai.packageName)
        }
    }

    fun gb(bytes: Long): String = String.format(Locale.US, "%.1f GB", bytes / 1073741824.0)
    fun mb(bytes: Long): String = String.format(Locale.US, "%d MB", bytes / 1048576)
}

object SpeedTest {
    private const val URL_DOWN = "https://speed.cloudflare.com/__down?bytes=%d"

    /** Round-trip time of a tiny request, in ms (best of 3). */
    suspend fun latencyMs(): Long? = withContext(Dispatchers.IO) {
        var best: Long? = null
        repeat(3) {
            try {
                val t0 = System.nanoTime()
                val c = URL(URL_DOWN.format(0)).openConnection() as HttpURLConnection
                c.connectTimeout = 5000
                c.readTimeout = 5000
                c.inputStream.use { it.readBytes() }
                c.disconnect()
                val ms = (System.nanoTime() - t0) / 1_000_000
                if (best == null || ms < best!!) best = ms
            } catch (_: Exception) {
            }
        }
        best
    }

    /** Downloads for up to [seconds] and reports the running speed in Mbps. Returns the final Mbps. */
    suspend fun download(seconds: Double = 8.0, onProgress: (Double) -> Unit): Double = withContext(Dispatchers.IO) {
        val c = URL(URL_DOWN.format(200_000_000)).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        c.setRequestProperty("User-Agent", "Auralis")
        val start = System.nanoTime()
        var total = 0L
        try {
            c.inputStream.use { ins ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    total += n
                    val el = (System.nanoTime() - start) / 1e9
                    if (el > 0.4) onProgress(total * 8 / el / 1e6)
                    if (el >= seconds) break
                }
            }
        } finally {
            c.disconnect()
        }
        val el = (System.nanoTime() - start) / 1e9
        if (el <= 0) 0.0 else total * 8 / el / 1e6
    }
}
