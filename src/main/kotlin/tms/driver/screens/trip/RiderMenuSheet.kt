package tms.driver.screens.trip

import org.openqa.selenium.By
import tms.driver.core.driver.DriverSession
import tms.driver.screens.BaseScreen
import tms.driver.screens.Ids
import java.time.Duration

/** The menu behind the three dots of the rider details sheet (trip view and arrived screen). Ids confirmed live. */
class RiderMenuSheet : BaseScreen() {
    private val moveToQueue: By = Ids.by("atv_back_queue")
    private val changeDropoff: By = Ids.by("atv_change_drop_off")
    private val assignAnother: By = Ids.by("atv_assign_another_driver")
    private val sendMessage: By = Ids.by("atv_send_message")
    private val returnToDispatcher: By = Ids.by("atv_return_to_dispatcher")
    private val number: By = Ids.by("atv_passenger_number")

    fun waitUntilShown(timeout: Duration = Duration.ofSeconds(10)): RiderMenuSheet { waits.visible(number, timeout); return this }
    fun isShown(timeout: Duration = Duration.ofSeconds(2)): Boolean = waits.appears(number, timeout)

    /** The entries the driver can see, in order. */
    fun entries(): List<String> = listOf(sendMessage, returnToDispatcher, moveToQueue, changeDropoff, assignAnother, number)
        .mapNotNull { by -> driver.findElements(by).firstOrNull()?.takeIf { runCatching { it.isDisplayed }.getOrDefault(false) }?.text }

    fun numberText(): String = textOf(number)
    fun moveToQueue() = tap(moveToQueue)
    fun changeDropoff(): ChangeDropoffScreen { tap(changeDropoff); return ChangeDropoffScreen().waitUntilShown() }
    fun assignToAnotherDriver(): AssignDriverScreen { tap(assignAnother); return AssignDriverScreen().waitUntilShown() }
    fun dismiss() { DriverSession.driver.navigate().back() }
}

/** "Change Drop-off": search the stops, pick one and UPDATE. */
class ChangeDropoffScreen : BaseScreen() {
    private val title: By = Ids.by("atv_toolbar_title")
    private val currentDropoff: By = Ids.by("drop_off_name")
    private val currentPickup: By = Ids.by("pickup_name")
    private val searchField: By = Ids.by("search_drop_off")
    private val searchInput: By = Ids.by("search_stops")
    private val resultName: By = Ids.by("stop_name")
    private val update: By = Ids.by("change_drop_off_update_btn")
    private val cancel: By = Ids.by("cancel_btn_change_drop_off")

    fun waitUntilShown(timeout: Duration = Duration.ofSeconds(15)): ChangeDropoffScreen { waits.visible(update, timeout); return this }
    fun isShown(timeout: Duration = Duration.ofSeconds(2)): Boolean = waits.appears(update, timeout)
    fun titleText(): String = textOf(title)                 // "Change Drop-off"
    fun pickupName(): String = textOf(currentPickup)
    fun dropoffName(): String = textOf(currentDropoff)
    fun searchHint(): String = textOf(searchField)          // "Select - Search Drop-off"
    fun updateText(): String = textOf(update)               // "UPDATE"
    fun cancelText(): String = textOf(cancel)               // "CANCEL"

    /** Opens the search and types [query]; returns the names of the stops offered. */
    fun search(query: String): List<String> {
        tap(searchField)
        waits.visible(searchInput).also { it.click(); it.sendKeys(query) }
        Thread.sleep(2_000)
        return driver.findElements(resultName).map { it.text }
    }

    fun choose(stop: String) { driver.findElements(resultName).first { it.text == stop }.click() }
    fun update() = tap(update)
    fun cancel() = tap(cancel)
}

/** "Assign Driver": the list of available drivers and CONFIRM. */
class AssignDriverScreen : BaseScreen() {
    private val title: By = Ids.by("atv_toolbar_title")
    private val driverName: By = Ids.by("driver_name")
    private val confirm: By = Ids.by("continue_btn")

    fun waitUntilShown(timeout: Duration = Duration.ofSeconds(20)): AssignDriverScreen { waits.visible(driverName, timeout); return this }
    fun titleText(): String = textOf(title)                 // "Assign Driver"
    fun confirmText(): String = textOf(confirm)             // "CONFIRM"; shown once a driver is picked
    fun isConfirmShown(): Boolean = waits.isVisibleNow(confirm)

    /** Names of the drivers currently on screen. */
    fun visibleDrivers(): List<String> = driver.findElements(driverName).map { it.text }

    /** Every driver the list offers, collected by scrolling it to the end (the list is longer than the screen). */
    fun allDrivers(maxSwipes: Int = 12): List<String> {
        val seen = LinkedHashSet<String>(visibleDrivers())
        val list = driver.findElements(Ids.by("rv_available_drivers")).firstOrNull() ?: return seen.toList()
        repeat(maxSwipes) {
            val moved = runCatching {
                driver.executeScript("mobile: scrollGesture", mapOf("elementId" to (list as org.openqa.selenium.remote.RemoteWebElement).id, "direction" to "down", "percent" to 0.7)) as? Boolean
            }.getOrNull() ?: false
            seen += visibleDrivers()
            if (!moved) return seen.toList()
        }
        return seen.toList()
    }

    /** Scrolls the list until [name] is on screen; false when the list does not offer it. */
    fun showDriver(name: String): Boolean {
        if (visibleDrivers().contains(name)) return true
        val list = driver.findElements(Ids.by("rv_available_drivers")).firstOrNull() ?: return false
        repeat(12) {
            val moved = runCatching {
                driver.executeScript("mobile: scrollGesture", mapOf("elementId" to (list as org.openqa.selenium.remote.RemoteWebElement).id, "direction" to "down", "percent" to 0.7)) as? Boolean
            }.getOrNull() ?: false
            if (visibleDrivers().contains(name)) return true
            if (!moved) return false
        }
        return false
    }

    fun choose(name: String) { driver.findElements(driverName).first { it.text == name }.click() }
    fun confirm() = tap(confirm)
}
