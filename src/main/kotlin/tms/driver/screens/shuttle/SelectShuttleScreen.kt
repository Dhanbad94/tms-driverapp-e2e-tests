package tms.driver.screens.shuttle

import org.openqa.selenium.By
import org.openqa.selenium.WebElement
import tms.driver.core.driver.DriverSession
import tms.driver.screens.BaseScreen
import tms.driver.screens.Ids
import java.time.Duration

/** One row of the Select Shuttle list. */
data class ShuttleRow(val name: String, val seats: String, val driver: String?, val trackerState: String?, val selected: Boolean)

/**
 * "Select Shuttle", opened by tapping the shuttle on Home. The current shuttle is already selected, so CONFIRM is on at once.
 * Ids were confirmed in live dumps on the staging build.
 */
class SelectShuttleScreen : BaseScreen() {
    private val title: By = Ids.by("atv_toolbar_title")
    private val list: By = Ids.by("rv_select_shuttle")
    private val confirm: By = Ids.by("btn_apply")
    private val searchIcon: By = Ids.by("iv_search")
    private val searchField: By = Ids.by("etv_search")
    private val searchClose: By = Ids.by("atv_cancel_search_view")

    fun waitUntilShown(timeout: Duration = Duration.ofSeconds(30)): SelectShuttleScreen { waits.visible(confirm, timeout); return this }
    fun isShown(timeout: Duration = Duration.ofSeconds(3)): Boolean = waits.appears(confirm, timeout)
    fun titleText(): String = textOf(title)            // "Select Shuttle"
    fun confirmText(): String = textOf(confirm)        // "CONFIRM" (off a trip)
    fun isConfirmEnabled(): Boolean = waits.visible(confirm).isEnabled
    fun confirm() = tap(confirm)

    private fun rowElements(): List<WebElement> = driver.findElements(By.xpath("//*[@resource-id='${Ids.of("rv_select_shuttle")}']/*"))

    private fun WebElement.textOf(name: String): String? =
        findElements(Ids.by(name)).firstOrNull()?.let { runCatching { it.text }.getOrNull() }?.takeIf { it.isNotBlank() }

    /** The rows on screen, top to bottom. */
    fun rows(): List<ShuttleRow> = rowElements().mapNotNull { r ->
        val name = r.textOf("atv_name") ?: return@mapNotNull null
        ShuttleRow(
            name = name, seats = r.textOf("atv_top_seat").orEmpty(), driver = r.textOf("atv_driver_name"), trackerState = r.textOf("atv_tracker_state"),
            selected = r.findElements(Ids.by("aiv_check")).any { runCatching { it.isDisplayed }.getOrDefault(false) },
        )
    }

    fun rowNames(): List<String> = rows().map { it.name }

    fun tapRow(name: String) {
        rowElements().first { it.textOf("atv_name") == name }.findElement(Ids.by("atv_name")).click()
    }

    /** Taps the pencil of a row, which opens Rename Shuttle. */
    fun openRename(name: String): RenameShuttleDialog {
        rowElements().first { it.textOf("atv_name") == name }.findElement(Ids.by("aiv_edit")).click()
        return RenameShuttleDialog().waitUntilShown()
    }

    fun openSearch(): SelectShuttleScreen { tap(searchIcon); waits.visible(searchField); return this }
    fun search(text: String): SelectShuttleScreen {
        waits.visible(searchField).also { it.clear(); if (text.isNotEmpty()) it.sendKeys(text) }
        Thread.sleep(1_500)
        return this
    }
    fun closeSearch() { tap(searchClose); Thread.sleep(800) }
    fun noMatchShown(): Boolean = waits.appears(By.xpath("//*[@text='No match found']"), Duration.ofSeconds(4))
    fun back() { DriverSession.driver.navigate().back() }
}

/** "Belongs to another driver": shown when a shuttle held by someone else is chosen. */
class ShuttleOfAnotherDriverDialog : BaseScreen() {
    private val label: By = Ids.by("atv_label")
    private val message: By = Ids.by("atv_message")
    private val selectAnyway: By = Ids.by("btn_select_anyway")
    private val pickAnother: By = Ids.by("btn_pickup_another")

    fun waitUntilShown(timeout: Duration = Duration.ofSeconds(15)): ShuttleOfAnotherDriverDialog { waits.visible(label, timeout); return this }
    fun isShown(timeout: Duration = Duration.ofSeconds(2)): Boolean = waits.appears(label, timeout)
    fun labelText(): String = textOf(label)                 // "Belongs to another driver"
    fun messageText(): String = textOf(message)             // "This shuttle is assigned to:"
    /** The other driver's name sits in a text view without an id, right below the wheel icon. */
    fun driverName(): String {
        val known = setOf(labelText(), messageText(), selectAnywayText(), pickAnotherText(), "")
        return driver.findElements(By.className("android.widget.TextView")).map { it.text.orEmpty() }.first { it !in known }
    }
    fun selectAnywayText(): String = textOf(selectAnyway)   // "SELECT ANYWAY"
    fun pickAnotherText(): String = textOf(pickAnother)     // "PICK ANOTHER"
    fun pickAnother() = tap(pickAnother)
}

/** "Rename Shuttle": name field (30 characters at most), CANCEL and CHANGE (off until the name differs). */
class RenameShuttleDialog : BaseScreen() {
    private val dialogTitle: By = Ids.by("atv_title")
    private val name: By = Ids.by("etv_name")
    private val error: By = Ids.by("atv_error")
    private val cancel: By = Ids.by("btn_cancel")
    private val change: By = Ids.by("btn_change")

    fun waitUntilShown(timeout: Duration = Duration.ofSeconds(15)): RenameShuttleDialog { waits.visible(dialogTitle, timeout); return this }
    fun isShown(timeout: Duration = Duration.ofSeconds(2)): Boolean = waits.appears(dialogTitle, timeout)
    fun titleText(): String = textOf(dialogTitle)       // "Rename Shuttle"
    fun nameText(): String = driver.findElement(name).text
    fun cancelText(): String = textOf(cancel)            // "CANCEL"
    fun changeText(): String = textOf(change)            // "CHANGE"
    fun isChangeEnabled(): Boolean = driver.findElement(change).isEnabled
    fun errorText(): String? = driver.findElements(error).firstOrNull()?.takeIf { it.isDisplayed }?.text?.takeIf { it.isNotBlank() }

    /** Replaces the name with [text]. */
    fun setName(text: String): RenameShuttleDialog {
        waits.visible(name).also { it.click(); it.clear(); if (text.isNotEmpty()) it.sendKeys(text) }
        runCatching { driver.hideKeyboard() }
        return this
    }

    fun change() = tap(change)
    fun cancel() = tap(cancel)
}
