package tms.driver.core.wait

import io.appium.java_client.android.AndroidDriver
import org.openqa.selenium.By
import org.openqa.selenium.NoSuchElementException
import org.openqa.selenium.StaleElementReferenceException
import org.openqa.selenium.WebElement
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.FluentWait
import org.openqa.selenium.support.ui.WebDriverWait
import tms.driver.core.config.EnvConfig
import java.time.Duration

/**
 * Condition-based waits. There are intentionally no sleeps: the app imposes fixed delays
 * (3 s splash, 3 s after `proceed`, ...) so callers pass a timeout above those values.
 */
class Waits(private val driver: AndroidDriver, private val defaultTimeout: Duration) {

    private fun waiter(timeout: Duration): WebDriverWait =
        WebDriverWait(driver, timeout, Duration.ofMillis(250))
            .ignoring(NoSuchElementException::class.java)
            .ignoring(StaleElementReferenceException::class.java) as WebDriverWait

    fun visible(by: By, timeout: Duration = defaultTimeout): WebElement =
        waiter(timeout).until(ExpectedConditions.visibilityOfElementLocated(by))

    fun clickable(by: By, timeout: Duration = defaultTimeout): WebElement =
        waiter(timeout).until(ExpectedConditions.elementToBeClickable(by))

    fun gone(by: By, timeout: Duration = defaultTimeout): Boolean =
        waiter(timeout).until(ExpectedConditions.invisibilityOfElementLocated(by))

    fun present(by: By, timeout: Duration = defaultTimeout): WebElement =
        waiter(timeout).until(ExpectedConditions.presenceOfElementLocated(by))

    /** Returns the first locator that becomes visible, or throws on timeout. */
    fun anyVisible(vararg locators: By, timeout: Duration = defaultTimeout): By {
        require(locators.isNotEmpty()) { "at least one locator required" }
        val wait = FluentWait(driver)
            .withTimeout(timeout)
            .pollingEvery(Duration.ofMillis(250))
            .ignoring(NoSuchElementException::class.java)
            .ignoring(StaleElementReferenceException::class.java)
        return wait.until { d ->
            locators.firstOrNull { by ->
                d.findElements(by).any { runCatching { it.isDisplayed }.getOrDefault(false) }
            }
        } ?: error("none of ${locators.toList()} became visible")
    }

    /** True if the locator is visible right now (no waiting). */
    fun isVisibleNow(by: By): Boolean =
        driver.findElements(by).any { runCatching { it.isDisplayed }.getOrDefault(false) }

    /** True if the locator becomes visible within [timeout]; never throws on timeout. */
    fun appears(by: By, timeout: Duration): Boolean =
        runCatching { visible(by, timeout) }.isSuccess

    companion object {
        fun forDriver(driver: AndroidDriver, config: EnvConfig) = Waits(driver, config.defaultTimeout)
    }
}
