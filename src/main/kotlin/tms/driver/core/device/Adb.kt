package tms.driver.core.device

import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

/**
 * Thin wrapper over the `adb` CLI for device control that Appium does not expose directly.
 * Phase A uses it for nothing beyond a connectivity check; the helpers are here for Phase B/D
 * (permissions, GPS, network). They shell out and require `adb` on PATH.
 */
class Adb(private val udid: String? = null, private val adbBinary: String = defaultBinary()) {
    private val log = LoggerFactory.getLogger(Adb::class.java)

    data class Result(val exitCode: Int, val output: String) {
        val ok: Boolean get() = exitCode == 0
    }

    fun run(vararg args: String, timeoutSeconds: Long = 30): Result {
        val cmd = buildList {
            add(adbBinary)
            if (udid != null) { add("-s"); add(udid) }
            addAll(args)
        }
        log.debug("adb: {}", cmd.joinToString(" "))
        val process = ProcessBuilder(cmd).redirectErrorStream(true).start()
        val out = process.inputStream.bufferedReader().readText()
        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return Result(-1, "timeout after ${timeoutSeconds}s: $out")
        }
        return Result(process.exitValue(), out.trim())
    }

    /** Airplane mode via the connectivity service (API 30+). */
    fun setAirplaneMode(enabled: Boolean) =
        run("shell", "cmd", "connectivity", "airplane-mode", if (enabled) "enable" else "disable")

    /**
     * Cuts (or restores) connectivity. Tries airplane mode first; on devices where the shell may not
     * toggle it (common on real devices) falls back to `svc wifi` + `svc data`. Restoring always
     * re-enables both radios, which is harmless when they were never disabled.
     */
    fun setOffline(offline: Boolean): Boolean {
        val airplane = setAirplaneMode(offline).ok && isAirplaneModeOn() == offline
        if (!offline) {
            setWifi(true); setMobileData(true)
            return airplane || !isAirplaneModeOn()
        }
        if (airplane) return true
        val wifi = setWifi(false).ok
        val data = setMobileData(false).ok
        return wifi || data
    }

    fun isAirplaneModeOn(): Boolean =
        run("shell", "settings", "get", "global", "airplane_mode_on").output.trim() == "1"

    fun grant(appPackage: String, permission: String) = run("shell", "pm", "grant", appPackage, permission)
    fun revoke(appPackage: String, permission: String) = run("shell", "pm", "revoke", appPackage, permission)
    fun clearAppData(appPackage: String) = run("shell", "pm", "clear", appPackage)
    /**
     * Clears the app's data AND re-grants the permissions the tests rely on. `pm clear` also resets runtime
     * permissions, which would put system permission prompts over the Login screen.
     */
    fun resetAppKeepingPermissions(appPackage: String): Boolean {
        val cleared = clearAppData(appPackage).ok
        listOf("android.permission.ACCESS_FINE_LOCATION", "android.permission.POST_NOTIFICATIONS", "android.permission.CAMERA")
            .forEach { grant(appPackage, it) }
        return cleared
    }

    /** Switches the device's location services on or off (API 28+). */
    fun setLocationEnabled(enabled: Boolean) = run("shell", "cmd", "location", "set-location-enabled", enabled.toString())

    /** Moves the EMULATOR's GPS to a point (emulator console `geo fix`; it takes longitude first). Real devices need a mock-location app instead. */
    fun setEmulatorLocation(latitude: Double, longitude: Double) = run("emu", "geo", "fix", longitude.toString(), latitude.toString())

    fun setWifi(enabled: Boolean) = run("shell", "svc", "wifi", if (enabled) "enable" else "disable")
    fun setMobileData(enabled: Boolean) = run("shell", "svc", "data", if (enabled) "enable" else "disable")
    fun isPackageInstalled(appPackage: String): Boolean =
        run("shell", "pm", "list", "packages", appPackage).output.lineSequence().any { it.trim() == "package:$appPackage" }

    companion object {
        /** `adb` from ANDROID_HOME/platform-tools when present, otherwise whatever is on PATH. */
        fun defaultBinary(): String {
            val home = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
            val candidate = home?.let { java.io.File(it, "platform-tools/adb") }
            return if (candidate != null && candidate.canExecute()) candidate.path else "adb"
        }
    }
}
