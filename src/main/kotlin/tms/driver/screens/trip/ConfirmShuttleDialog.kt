package tms.driver.screens.trip

import org.openqa.selenium.By
import tms.driver.screens.BaseScreen
import tms.driver.screens.Ids
import java.time.Duration

/** "Confirm Shuttle" dialog shown the first time START TRIP is tapped in a session. Ids confirmed in a live dump. */
class ConfirmShuttleDialog : BaseScreen() {
    private val title: By = Ids.by("tv_title")
    private val message: By = Ids.by("tv_message")
    private val confirm: By = Ids.by("btn_positive_vert")
    private val different: By = Ids.by("btn_negative")

    fun waitUntilShown(timeout: Duration = Duration.ofSeconds(15)): ConfirmShuttleDialog { waits.visible(title, timeout); return this }
    fun isShown(timeout: Duration = Duration.ofSeconds(2)): Boolean = waits.appears(title, timeout)
    fun count(): Int = driver.findElements(title).size
    fun titleText(): String = textOf(title)                 // "Confirm Shuttle"
    fun messageText(): String = textOf(message)             // "You are in Automated Shuttle-1."
    fun confirmText(): String = textOf(confirm)             // "CONFIRM"
    fun differentShuttleText(): String = textOf(different)  // "SELECT DIFFERENT SHUTTLE"
    fun confirm() = tap(confirm)
    fun selectDifferentShuttle(): ShuttlePickerScreen { tap(different); return ShuttlePickerScreen().waitUntilShown() }
}
