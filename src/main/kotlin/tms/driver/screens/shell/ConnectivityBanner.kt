package tms.driver.screens.shell

import io.appium.java_client.AppiumBy
import org.openqa.selenium.By
import tms.driver.screens.BaseScreen
import tms.driver.screens.Ids
import java.time.Duration

/** Network-state banner shown on every shell (`internet_indicator` + `internet_text`). */
class ConnectivityBanner : BaseScreen() {
    private val indicator: By = Ids.by("internet_indicator")
    private val text: By = Ids.by("internet_text")

    fun isShown(): Boolean = waits.isVisibleNow(indicator)
    fun waitUntilShown(timeout: Duration = Duration.ofSeconds(20)): String {
        waits.visible(indicator, timeout)
        return runCatching { waits.visible(text, timeout).text }.getOrDefault("")
    }

    /**
     * Polls quickly for the transient green banner ("Back online", shown about 2 s).
     * Returns its text, or null if it was never observed within [timeout].
     */
    fun waitForBackOnline(timeout: Duration = Duration.ofSeconds(40)): String? {
        val end = System.nanoTime() + timeout.toNanos()
        while (System.nanoTime() < end) {
            // The banner node is transient and goes stale between find and read; treat that as "not seen".
            val seen = runCatching { driver.findElements(text).firstOrNull()?.text }.getOrNull()
            if (seen != null && Regex("(?i).*back.*online.*").matches(seen)) return seen
            Thread.sleep(100)
        }
        return null
    }

    fun waitUntilHidden(timeout: Duration = Duration.ofSeconds(20)): Boolean = waits.gone(indicator, timeout)
}
