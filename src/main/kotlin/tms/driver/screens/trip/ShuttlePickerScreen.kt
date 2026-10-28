package tms.driver.screens.trip

import org.openqa.selenium.By
import tms.driver.screens.BaseScreen
import tms.driver.screens.Ids
import java.time.Duration
import kotlin.math.abs

/**
 * "Select Shuttle" screen reached through "Select Different Shuttle". Ids confirmed in live dumps.
 * The header repeats what the ride needs ("5 Seats", "ADA"); each row shows name, seats, driver and tracker state.
 */
class ShuttlePickerScreen : BaseScreen() {
    private val toolbarTitle: By = Ids.by("atv_toolbar_title")
    private val needs: By = Ids.by("atv_select_shuttle")
    private val seatCount: By = Ids.by("atv_seat_count")
    private val seatType: By = Ids.by("atv_seat_type")
    private val rowName: By = Ids.by("atv_name")
    private val check: By = Ids.by("aiv_check")
    private val apply: By = Ids.by("btn_apply")

    fun waitUntilShown(timeout: Duration = Duration.ofSeconds(20)): ShuttlePickerScreen { waits.visible(rowName, timeout); return this }
    fun titleText(): String = textOf(toolbarTitle)       // "Select Shuttle"
    fun needsText(): String = textOf(needs)              // "Select a shuttle with at least"
    fun seatsNeededText(): String = textOf(seatCount)    // e.g. "12 Seats"
    fun seatTypeNeededText(): String? = driver.findElements(seatType).firstOrNull()?.text   // "ADA" when the ride needs it
    fun applyText(): String = textOf(apply)              // "START TRIP" here, "CONFIRM" from the Home chip
    fun shuttleNames(): List<String> = driver.findElements(rowName).map { it.text }

    fun selectedShuttleName(): String? {
        val ticks = driver.findElements(check).map { it.location.y }
        return driver.findElements(rowName).firstOrNull { name -> ticks.any { abs(it - name.location.y) < 40 } }?.text
    }

    /** Taps a shuttle row; the app may then open the "Alerts" sheet. */
    fun tapShuttle(name: String): ShuttleAlertsSheet {
        tap(By.xpath("//*[@resource-id='${Ids.of("atv_name")}' and @text='$name']"))
        return ShuttleAlertsSheet()
    }
}

/** The "Alerts" bottom sheet listing why a shuttle is a poor choice. */
class ShuttleAlertsSheet : BaseScreen() {
    private val label: By = Ids.by("atv_label")
    private val belongsToAnother: By = Ids.by("atv_belongs_to_another")
    private val requiresAda: By = Ids.by("atv_requiresAda")
    private val tooSmall: By = Ids.by("atv_shuttleSmall")
    private val selectAnyway: By = Ids.by("btn_select_anyway")
    private val pickAnother: By = Ids.by("btn_pickup_another")

    fun waitUntilShown(timeout: Duration = Duration.ofSeconds(10)): ShuttleAlertsSheet { waits.visible(label, timeout); return this }
    fun isShown(timeout: Duration = Duration.ofSeconds(2)): Boolean = waits.appears(label, timeout)
    fun titleText(): String = textOf(label)   // "Alerts"

    /** The bullet texts currently listed, in the order the app lists them. */
    fun alerts(): List<String> = listOf(belongsToAnother, tooSmall, requiresAda)
        .mapNotNull { by -> driver.findElements(by).firstOrNull()?.text?.takeIf { it.isNotBlank() } }

    fun selectAnywayText(): String = textOf(selectAnyway)
    fun pickAnotherText(): String = textOf(pickAnother)
    fun selectAnyway() = tap(selectAnyway)
    fun pickAnother() = tap(pickAnother)
}
