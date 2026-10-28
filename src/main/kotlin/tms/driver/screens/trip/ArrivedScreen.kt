package tms.driver.screens.trip

import org.openqa.selenium.By
import tms.driver.screens.BaseScreen
import tms.driver.screens.Ids
import java.time.Duration

/**
 * The "Arrived At <stop>" screen opened by "I've Arrived": a PICK-UPS list and a DROP-OFFS list, the seat counter and
 * the BACK / GO TO NEXT STOP buttons (COMPLETE TRIP at the last stop). Every id was confirmed in live dumps.
 * Each pick-up row has two boxes: NO SHOW (`aiv_no_show`) and PICKED UP (`aiv_pick_up`); tapping the same box again
 * clears the choice and tapping the other switches it. This build has no seat +/- buttons.
 */
class ArrivedScreen : BaseScreen() {
    private val title: By = Ids.by("atv_toolbar_title")
    private val subtitle: By = Ids.by("atv_toolbar_sub_title")
    private val noShowHeader: By = Ids.by("atv_no_show")
    private val pickedUpHeader: By = Ids.by("atv_pickedup")
    private val seatsLabel: By = Ids.by("atv_seat_occupied")
    private val seats: By = Ids.by("atv_shuttle_seat_count")
    private val seatsTotal: By = Ids.by("atv_shuttle_total_seat_count")
    private val back: By = Ids.by("btn_button")
    private val proceed: By = Ids.by("btn_proceed_button")
    private val pickedUpBox: By = Ids.by("aiv_pick_up")
    private val noShowBox: By = Ids.by("aiv_no_show")
    private val pickupList: By = Ids.by("rv_pickups")
    private val dropoffList: By = Ids.by("rv_dropoff")

    fun waitUntilShown(timeout: Duration = Duration.ofSeconds(40)): ArrivedScreen { waits.visible(proceed, timeout); return this }
    fun isShown(timeout: Duration = Duration.ofSeconds(3)): Boolean = waits.appears(proceed, timeout)

    fun stopName(): String = textOf(title)
    fun subtitleText(): String = textOf(subtitle)            // "Arrived At"
    fun pickedUpHeader(): String = textOf(pickedUpHeader)    // "PICKED UP"
    fun noShowHeader(): String = textOf(noShowHeader)        // "NO SHOW"
    fun seatsLabelText(): String = textOf(seatsLabel)        // "Seats Occupied"
    fun seatsOccupied(): String = textOf(seats)              // "0", "5", ...
    fun seatsTotal(): String = textOf(seatsTotal)            // "/ 15"
    fun backText(): String = textOf(back)                    // "BACK"
    fun proceedText(): String = textOf(proceed)              // "GO TO NEXT STOP", or "COMPLETE TRIP" at the last stop
    fun isProceedEnabled(): Boolean = waits.visible(proceed).isEnabled

    /** Rider chips in the PICK-UPS list and in the DROP-OFFS list (name as shown, then party size). */
    fun pickupChips(): List<RiderChip> = chips(pickupList)
    fun dropoffChips(): List<RiderChip> = chips(dropoffList)
    fun noPickupsMessageShown(): Boolean = waits.isVisibleNow(By.xpath("//*[@text='No Pick-ups at this stop']"))
    fun noDropoffsMessageShown(): Boolean = waits.isVisibleNow(By.xpath("//*[@text='No Drop-offs at this stop']"))

    private fun chips(list: By): List<RiderChip> =
        driver.findElements(list).firstOrNull()?.findElements(By.className("android.widget.TextView"))?.map { it.text }
            ?.chunked(2)?.filter { it.size == 2 && it[1].trim().toIntOrNull() != null }?.map { RiderChip(it[0], it[1].trim().toInt()) }
            ?: emptyList()

    fun tapPickedUp() = tap(pickedUpBox)
    /** With several riders at a stop each row has its own PICKED UP box; this ticks the one in row [index] (0 = first). */
    fun tapPickedUpAt(index: Int) { driver.findElements(pickedUpBox)[index].click() }
    fun pickedUpBoxCount(): Int = driver.findElements(pickedUpBox).size
    fun tapNoShow() = tap(noShowBox)

    /** Opens the rider's detail sheet from the first row of the pick-up (or drop-off) list. */
    fun tapRider(inDropoffs: Boolean = false): RiderDetailsSheet {
        val list = driver.findElements(if (inDropoffs) dropoffList else pickupList).first()
        list.findElements(By.xpath(".//*[@clickable='true']")).first().click()
        return RiderDetailsSheet().waitUntilShown()
    }

    /** Taps BACK; with decisions made the app first asks "Go back?". */
    fun tapBack() = tap(back)

    /** Taps GO TO NEXT STOP (or COMPLETE TRIP). The app then waits about 3 seconds before moving on. */
    fun tapProceed() = tap(proceed)
}

/** The "Go back?" confirmation shown when BACK is tapped after decisions were made. */
class GoBackDialog : BaseScreen() {
    private val title: By = Ids.by("atv_go_back")
    private val message: By = Ids.by("atv_guest_message")
    private val goBack: By = Ids.by("btn_go_back")
    private val stay: By = Ids.by("btn_stay")

    fun waitUntilShown(timeout: Duration = Duration.ofSeconds(10)): GoBackDialog { waits.visible(title, timeout); return this }
    fun isShown(timeout: Duration = Duration.ofSeconds(2)): Boolean = waits.appears(title, timeout)
    fun titleText(): String = textOf(title)              // "Go back?"
    fun messageText(): String = textOf(message)          // "Pick-ups and Drop-offs will not be processed."
    fun goBackText(): String = textOf(goBack)            // "GO BACK"
    fun stayText(): String = textOf(stay)                // "STAY ON PAGE"
    fun goBack() = tap(goBack)
    fun stay() = tap(stay)
}

/**
 * The "New Ride Request" sheet that follows GO TO NEXT STOP (and the completed trip) while rides are waiting in the queue.
 * The count in `notification_count` ("30 new") is the number of waiting rides in the shared queue.
 */
class ReviewRequestsSheet : BaseScreen() {
    private val title: By = Ids.by("atv_new_ride_request")
    private val count: By = Ids.by("notification_count")
    private val action: By = Ids.by("atv_action_text")
    private val later: By = Ids.by("btn_later")
    private val review: By = Ids.by("btn_review_button")
    private val completedMessage: By = Ids.by("completed_msg")

    fun waitUntilShown(timeout: Duration = Duration.ofSeconds(40)): ReviewRequestsSheet { waits.visible(title, timeout); return this }
    fun isShown(timeout: Duration = Duration.ofSeconds(3)): Boolean = waits.appears(title, timeout)
    fun titleText(): String = textOf(title)                  // "New Ride Request"
    fun countText(): String = textOf(count)                  // "30 new" (only on the mid-trip sheet, not after the completed trip)
    fun hasCount(): Boolean = driver.findElements(count).isNotEmpty()
    fun questionText(): String = textOf(action)              // "Would you like to review and take action now?"
    fun laterText(): String = textOf(later)                  // "LATER"
    fun reviewText(): String = textOf(review)                // "REVIEW"

    /** "Trip completed. Great Job!" when this sheet comes with a completed trip; null after an ordinary stop. */
    fun tripCompletedMessage(): String? = driver.findElements(completedMessage).firstOrNull()?.text
    fun later() = tap(later)
    fun review() = tap(review)
}
