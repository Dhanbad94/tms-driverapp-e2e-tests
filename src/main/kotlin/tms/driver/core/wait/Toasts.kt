package tms.driver.core.wait

import io.appium.java_client.android.AndroidDriver
import org.openqa.selenium.By
import java.time.Duration

/**
 * Reads the text of a Toast shown by the app. Most failures in the app surface as Toasts (server message),
 * so negative tests assert on this.
 *
 * Confirmed on the staging build / UiAutomator2: a toast is exposed as `android.widget.Toast` nodes for only
 * ~0.4 s and appears ~3 s after the tap, so polling the hierarchy misses it. Appium also writes every toast
 * to logcat ("Adding toast message to root: <text>"), which is read here instead, with the hierarchy poll
 * as a fallback. Call [mark] right before the action that triggers the toast so older entries are drained.
 */
object Toasts {
    private val LOG_LINE = Regex("Adding toast message to root: (.+)$")

    /** Drains buffered logcat so only entries produced after this call are considered. */
    fun mark(driver: AndroidDriver) {
        runCatching { driver.manage().logs().get("logcat") }
    }

    fun read(driver: AndroidDriver, timeout: Duration = Duration.ofSeconds(10)): String? {
        val end = System.nanoTime() + timeout.toNanos()
        while (System.nanoTime() < end) {
            // 1) logcat (deterministic)
            val lines = runCatching {
                driver.manage().logs().get("logcat").all.mapNotNull { LOG_LINE.find(it.message)?.groupValues?.get(1)?.trim() }
            }.getOrDefault(emptyList())
            if (lines.isNotEmpty()) {
                // Each toast is logged as the message followed by the app label (e.g. "Stag:TrackMyShuttle").
                return lines.first()
            }
            // 2) hierarchy fallback
            val texts = runCatching {
                driver.findElements(By.xpath("//android.widget.Toast")).mapNotNull { el ->
                    runCatching { el.text }.getOrNull()?.takeIf { it.isNotBlank() }
                }
            }.getOrDefault(emptyList())
            if (texts.isNotEmpty()) return texts.first()
            Thread.sleep(150)
        }
        return null
    }
}
