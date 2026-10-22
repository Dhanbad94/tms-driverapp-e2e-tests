package tms.driver.screens

import io.appium.java_client.android.AndroidDriver
import io.appium.java_client.android.nativekey.AndroidKey
import io.appium.java_client.android.nativekey.KeyEvent
import org.openqa.selenium.By
import org.openqa.selenium.WebElement
import tms.driver.core.config.EnvConfig
import tms.driver.core.driver.DriverSession
import tms.driver.core.wait.Waits

/** Base class for screen objects: shared driver, waits and intent-level primitives. */
abstract class BaseScreen(
    protected val driver: AndroidDriver = DriverSession.driver,
    protected val config: EnvConfig = DriverSession.config,
) {
    protected val waits: Waits = Waits.forDriver(driver, config)

    protected fun tap(by: By) = waits.clickable(by).click()

    protected fun type(by: By, text: String) {
        val el: WebElement = waits.visible(by)
        el.clear()
        el.sendKeys(text)
    }

    protected fun textOf(by: By): String = waits.visible(by).text

    /** Focuses a custom-drawn digit field and sends each digit as a key event. */
    protected fun typeDigitsByKeys(by: By, digits: String) {
        waits.clickable(by).click()
        digits.forEach { ch -> driver.pressKey(KeyEvent(AndroidKey.valueOf("DIGIT_$ch"))) }
    }

    /**
     * The loader may flash briefly; if it never appears this returns immediately. The app's own API
     * timeout is 60 s and staging calls were seen to take >20 s, so wait up to that long.
     */
    fun waitForLoaderToFinish(timeout: java.time.Duration = java.time.Duration.ofSeconds(60)) {
        waits.gone(Ids.LOADER, timeout)
    }
}
