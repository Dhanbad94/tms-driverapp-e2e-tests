package tms.driver.screens

import io.appium.java_client.AppiumBy
import io.appium.java_client.android.AndroidDriver
import org.openqa.selenium.By

/**
 * Handles Android runtime-permission dialogs when `autoGrantPermissions` is off.
 * The resource-ids are the AOSP permission-controller ids; they vary by OEM/API level and are
 * UNVERIFIED on the target devices.
 */
class SystemDialogs(private val driver: AndroidDriver) {
    private val allowButtons: List<By> = listOf(
        "com.android.permissioncontroller:id/permission_allow_foreground_only_button",
        "com.android.permissioncontroller:id/permission_allow_one_time_button",
        "com.android.permissioncontroller:id/permission_allow_button",
        "com.android.packageinstaller:id/permission_allow_button",
    ).map { AppiumBy.id(it) }

    /** Taps an "allow" button if a permission dialog is currently showing. Returns true if it did. */
    fun allowIfPresent(): Boolean {
        for (by in allowButtons) {
            val el = driver.findElements(by).firstOrNull() ?: continue
            el.click()
            return true
        }
        return false
    }

    /**
     * Android's "<app> isn't responding" dialog. Taps "Wait" and returns true if it was showing.
     * Callers must record the occurrence: an ANR is a quality signal, not noise.
     */
    fun waitOutAnrIfPresent(): Boolean {
        val wait = driver.findElements(AppiumBy.id("android:id/aerr_wait")).firstOrNull() ?: return false
        wait.click()
        return true
    }

    /** The system location permission prompt (seen on the staging build: "Allow ... to access this device's location?"). */
    fun isLocationPromptShown(): Boolean =
        driver.findElements(AppiumBy.id("com.android.permissioncontroller:id/permission_message")).isNotEmpty()

    /**
     * Declines every location prompt that appears: "Don't allow" on the standard prompt, or "Keep approximate location"
     * on Android's "change from approximate to precise" prompt (seen when only the precise permission was revoked).
     * An "isn't responding" dialog over the prompt is waited out first. Returns how many prompts were declined.
     */
    fun denyLocationPrompts(maxPrompts: Int = 5): Int {
        val decline = AppiumBy.androidUIAutomator(
            "new UiSelector().textMatches(\"(?i)(don.t allow|keep approximate location|deny)\")",
        )
        var denied = 0
        repeat(maxPrompts) {
            waitOutAnrIfPresent()
            var button = driver.findElements(decline).firstOrNull()
            if (button == null) { Thread.sleep(2_500); waitOutAnrIfPresent(); button = driver.findElements(decline).firstOrNull() }
            if (button == null) return denied
            button.click(); denied++; Thread.sleep(1_200)
        }
        return denied
    }
}
