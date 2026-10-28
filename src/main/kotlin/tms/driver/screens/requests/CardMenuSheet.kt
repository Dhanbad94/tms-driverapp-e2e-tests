package tms.driver.screens.requests

import org.openqa.selenium.By
import tms.driver.core.driver.DriverSession
import tms.driver.screens.BaseScreen
import tms.driver.screens.Ids
import java.time.Duration

/** The "more options" bottom sheet behind the three dots of a Requests card. */
class CardMenuSheet : BaseScreen() {
    private val moveToQueue: By = Ids.by("tv_back_queue")
    private val cancelDispatch: By = Ids.by("tv_reject_req")
    private val markCompleted: By = Ids.by("tv_complete_req")
    private val call: By = Ids.by("tv_call")
    private val changeDropoff: By = Ids.by("tv_change_drop_off")
    private val changePickup: By = Ids.by("tv_change_pickup")
    private val close: By = Ids.by("btn_cancel")

    fun waitUntilShown(timeout: Duration = Duration.ofSeconds(10)): CardMenuSheet { waits.anyVisible(call, cancelDispatch, markCompleted, moveToQueue, timeout = timeout); return this }
    fun isShown(timeout: Duration = Duration.ofSeconds(2)): Boolean = runCatching { waits.anyVisible(call, cancelDispatch, markCompleted, moveToQueue, timeout = timeout) }.isSuccess

    /** The visible entries, in order, as the driver reads them (the call entry shows the rider's number). */
    fun entries(): List<String> = listOf(moveToQueue, changePickup, changeDropoff, cancelDispatch, markCompleted, call)
        .mapNotNull { by -> driver.findElements(by).firstOrNull()?.takeIf { runCatching { it.isDisplayed }.getOrDefault(false) }?.text }

    fun callText(): String = textOf(call)
    fun moveToQueue() = tap(moveToQueue)
    fun markCompleted() = tap(markCompleted)
    fun cancelDispatch(): ReasonSheet { tap(cancelDispatch); return ReasonSheet().waitUntilShown() }

    /**
     * Taps the call entry. The app only opens the phone's dialer with the number filled in; nothing is dialled.
     * Returns the dialer's package and the number it shows, and brings the app back to the front.
     */
    fun tapCallAndReadDialer(): Pair<String, String> {
        tap(call)
        Thread.sleep(2_500)
        val d = DriverSession.driver
        val pkg = d.currentPackage.orEmpty()
        val shown = d.findElements(By.id("$pkg:id/digits")).firstOrNull()?.text.orEmpty()
        d.activateApp(DriverSession.config.appPackage)
        return pkg to shown
    }

    fun dismiss() { DriverSession.driver.navigate().back() }
}

/** The "Select Reason" sheet shown by Cancel Dispatch. */
class ReasonSheet : BaseScreen() {
    private val heading: By = Ids.by("select_reason_text")
    private val reason: By = Ids.by("template_text")
    private val done: By = Ids.by("done_btn")

    fun waitUntilShown(timeout: Duration = Duration.ofSeconds(10)): ReasonSheet { waits.visible(heading, timeout); return this }
    fun isShown(timeout: Duration = Duration.ofSeconds(2)): Boolean = waits.appears(heading, timeout)
    fun headingText(): String = textOf(heading)            // "Select Reason"
    fun reasons(): List<String> = driver.findElements(reason).map { it.text }
    private val customMessage: By = Ids.by("custom_msg_et")

    fun doneText(): String = textOf(done)                  // "DONE"
    /** After choosing "Other" the sheet is titled "Other Reason" and asks for text ("What prompted this action?"). */
    fun isCustomMessageShown(): Boolean = waits.isVisibleNow(customMessage)
    fun customMessageHint(): String = textOf(customMessage)
    fun typeCustomMessage(text: String) { waits.visible(customMessage).also { it.click(); it.sendKeys(text) }; runCatching { driver.hideKeyboard() } }
    fun isDoneEnabled(): Boolean = waits.visible(done).isEnabled
    fun choose(text: String) { driver.findElements(reason).first { it.text == text }.click() }
    fun done() = tap(done)
    fun dismiss() { driver.navigate().back() }
}
