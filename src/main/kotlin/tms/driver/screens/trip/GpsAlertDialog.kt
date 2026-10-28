package tms.driver.screens.trip

import io.appium.java_client.AppiumBy
import org.openqa.selenium.By
import tms.driver.screens.BaseScreen
import java.time.Duration

/** The system-style alert "Provide GPS Access." shown when location services are off. Ids are `android:id/...`. */
class GpsAlertDialog : BaseScreen() {
    private val title: By = AppiumBy.id("android:id/alertTitle")
    private val message: By = AppiumBy.id("android:id/message")
    private val ok: By = AppiumBy.id("android:id/button1")

    fun waitUntilShown(timeout: Duration = Duration.ofSeconds(15)): GpsAlertDialog { waits.visible(title, timeout); return this }
    fun isShown(timeout: Duration = Duration.ofSeconds(2)): Boolean = waits.appears(title, timeout)
    fun titleText(): String = textOf(title)       // "Provide GPS Access."
    fun messageText(): String = textOf(message)   // "Location Data is required so that driver can see your location on map."
    fun ok() = tap(ok)
}
