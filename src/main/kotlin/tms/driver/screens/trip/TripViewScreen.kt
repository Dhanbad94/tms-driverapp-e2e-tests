package tms.driver.screens.trip

import org.openqa.selenium.By
import org.openqa.selenium.remote.RemoteWebElement
import tms.driver.screens.BaseScreen
import tms.driver.screens.Ids
import java.time.Duration

/** A rider chip on a stop card: the rider's name (title-cased by the app) and the party size. */
data class RiderChip(val name: String, val partySize: Int)

/**
 * The trip view opened by START TRIP: "Driving to", LIST VIEW / MAP VIEW tabs and, in the list, one card per stop
 * (stop name, address, estimated time and a chip per rider). The current stop's card also has "I've Arrived".
 * All ids were confirmed in live dumps on the staging build.
 */
class TripViewScreen : BaseScreen() {
    private val title: By = Ids.by("atv_toolbar_title")
    private val stopName: By = Ids.by("atv_stop_name")
    private val stopAddress: By = Ids.by("atv_stop_address")
    private val eta: By = Ids.by("atv_estimated_time")
    private val arrived: By = Ids.by("atv_mark_arrived")
    private val listTab: By = Ids.by("tv_list")
    private val mapTab: By = Ids.by("tv_map")
    private val destination: By = Ids.by("atv_destination")
    private val riderLists: By = Ids.by("rv_stops")
    private val refreshArea: By = Ids.by("swipeRL")

    fun waitUntilShown(timeout: Duration = Duration.ofSeconds(40)): TripViewScreen { waits.visible(arrived, timeout); return this }
    fun isShown(timeout: Duration = Duration.ofSeconds(3)): Boolean = waits.appears(arrived, timeout)

    fun titleText(): String = textOf(title)                   // "Driving to"
    fun arrivedButtonText(): String = textOf(arrived)         // "I've Arrived"
    fun listTabText(): String = textOf(listTab)               // "LIST VIEW"
    fun mapTabText(): String = textOf(mapTab)                 // "MAP VIEW"

    /** Stop cards in the list, top to bottom: the current stop first. */
    fun stopNames(): List<String> = driver.findElements(stopName).map { it.text }
    fun stopAddresses(): List<String> = driver.findElements(stopAddress).map { it.text }
    fun estimatedTimes(): List<String> = driver.findElements(eta).map { it.text }
    /**
     * The estimated times appear a moment after the cards (they are calculated after the screen opens), so wait until every
     * stop card has one. Returns false if they have not all appeared in time.
     */
    fun waitForEstimatedTimes(timeout: Duration = Duration.ofSeconds(40)): Boolean {
        val end = System.nanoTime() + timeout.toNanos()
        while (System.nanoTime() < end) {
            val stops = stopNames().size
            val times = estimatedTimes()
            if (stops > 0 && times.size >= stops && times.all { it.isNotBlank() }) return true
            Thread.sleep(500)
        }
        return false
    }

    fun currentStopName(): String = stopNames().first()
    fun estimatedTimeText(): String = estimatedTimes().first()

    /** Rider chips per stop card, in card order. Each chip holds two texts: the name, then the party size. */
    fun riderChipsPerStop(): List<List<RiderChip>> = driver.findElements(riderLists).map { list ->
        val texts = list.findElements(By.className("android.widget.TextView")).map { it.text }
        texts.chunked(2).filter { it.size == 2 }.map { RiderChip(it[0], it[1].trim().toInt()) }
    }

    // --- Map tab: a header card at the top (destination, address, estimate, riders, I've Arrived) over the Google map
    fun mapAddress(): String = textOf(Ids.by("atv_stop_address"))
    /** "123 hr 56 min", "45 min" or "Arrived" under a minute. */
    fun mapEstimatedTime(): String = textOf(Ids.by("atv_estimated_time"))
    fun mapRiderChips(): List<RiderChip> = driver.findElements(Ids.by("rv_riders")).firstOrNull()?.findElements(By.className("android.widget.TextView"))?.map { it.text }
        ?.chunked(2)?.filter { it.size == 2 && it[1].trim().toIntOrNull() != null }?.map { RiderChip(it[0], it[1].trim().toInt()) } ?: emptyList()
    fun tapMapRider(): RiderDetailsSheet { tap(Ids.by("rv_riders")); return RiderDetailsSheet().waitUntilShown() }

    /** Drags the map sideways, which makes the RE-CENTER button appear. */
    fun dragMap() {
        val size = driver.manage().window().size
        driver.executeScript("mobile: swipeGesture", mapOf("left" to size.width / 4, "top" to size.height / 2, "width" to size.width / 2, "height" to size.height / 6, "direction" to "left", "percent" to 0.6))
        Thread.sleep(1_500)
    }
    fun isRecenterShown(): Boolean = waits.appears(Ids.by("atv_reset"), Duration.ofSeconds(4))
    fun recenterText(): String = textOf(Ids.by("atv_reset"))        // "RE-CENTER"
    fun tapRecenter() = tap(Ids.by("atv_reset"))
    fun waitUntilRecenterGone(): Boolean = waits.gone(Ids.by("atv_reset"), Duration.ofSeconds(8))

    fun openMap() = tap(mapTab)
    fun openList() = tap(listTab)
    fun isMapShown(): Boolean = waits.isVisibleNow(destination)
    fun mapDestinationName(): String = textOf(destination)

    /** Swipes down like the pull-to-refresh gesture. */
    fun pullToRefresh() {
        val area = waits.visible(refreshArea) as RemoteWebElement
        driver.executeScript("mobile: swipeGesture", mapOf("elementId" to area.id, "direction" to "down", "percent" to 0.8))
    }

    /** Opens the rider details sheet for the first rider chip on the given stop card (0 = current stop). */
    fun tapRider(stopIndex: Int = 0): RiderDetailsSheet {
        val chip = driver.findElements(riderLists)[stopIndex].findElements(By.xpath(".//*[@clickable='true']")).first()
        chip.click()
        return RiderDetailsSheet().waitUntilShown()
    }

    /** "Go here instead" buttons: later stops offer one so the driver can skip ahead (they appear only with more than one ride). */
    fun goHereInsteadCount(): Int = driver.findElements(Ids.by("tv_action_btn")).count { it.text.equals("Go here instead", ignoreCase = true) }

    /** Taps the n-th "Go here instead" button (0 = the first one on screen) and returns the confirmation. */
    fun tapGoHereInstead(n: Int = 0): GoHereDialog {
        driver.findElements(Ids.by("tv_action_btn")).filter { it.text.equals("Go here instead", ignoreCase = true) }[n].click()
        return GoHereDialog().waitUntilShown()
    }

    fun tapArrived(): ArrivedScreen { tap(arrived); return ArrivedScreen().waitUntilShown() }

    /** Two quick taps on I've Arrived, like a driver tapping twice. */
    fun doubleTapArrived(): ArrivedScreen {
        val el = waits.clickable(arrived)
        el.click()
        runCatching { el.click() }
        return ArrivedScreen().waitUntilShown()
    }
}

/** The bottom sheet with a rider's details, opened by tapping a rider chip. */
class RiderDetailsSheet : BaseScreen() {
    private val name: By = Ids.by("passenger_name")
    private val count: By = Ids.by("passenger_count")
    private val pickupName: By = Ids.by("atv_name_pickup")
    private val dropoffName: By = Ids.by("atv_name_dropoff")
    private val notes: By = Ids.by("atv_guest_notes")
    private val cancel: By = Ids.by("btn_cancel")
    private val action: By = Ids.by("btn_action_button")

    fun waitUntilShown(timeout: Duration = Duration.ofSeconds(15)): RiderDetailsSheet { waits.visible(name, timeout); return this }
    fun isShown(timeout: Duration = Duration.ofSeconds(2)): Boolean = waits.appears(name, timeout)
    fun riderName(): String = textOf(name)             // shown exactly as typed by the rider
    fun partySize(): String = textOf(count)
    fun pickupStop(): String = textOf(pickupName)
    fun dropoffStop(): String = textOf(dropoffName)
    fun notesText(): String = textOf(notes)
    fun cancelText(): String = textOf(cancel)          // "CANCEL"
    /** Room and flight number of the ride, or null when the sheet does not show them. */
    fun roomText(): String? = driver.findElements(Ids.by("tv_room")).firstOrNull()?.takeIf { it.isDisplayed }?.text
    fun flightText(): String? = driver.findElements(Ids.by("tv_flight")).firstOrNull()?.takeIf { it.isDisplayed }?.text
    fun actionText(): String = textOf(action)          // "PICK UP"
    /** True when the sheet offers an action button (PICK UP is offered from the trip view, not from the arrived screen). */
    fun isActionShown(): Boolean = driver.findElements(action).any { runCatching { it.isDisplayed }.getOrDefault(false) }
    fun cancel() = tap(cancel)
    /** Taps the action button (PICK UP, then CONFIRM). */
    fun tapAction() = tap(action)

    /** The three dots at the top of the sheet: Move to Request Queue, Change Drop-off, Assign to another Driver and the number. */
    fun openMenu(): RiderMenuSheet { tap(Ids.by("iv_more")); return RiderMenuSheet().waitUntilShown() }

    /**
     * Closes the sheet whichever way it was opened: from the trip view it has CANCEL; from the arrived screen it has only the
     * X at the top right (`cancel`), because there is no action to cancel.
     */
    fun close() {
        val x = driver.findElements(Ids.by("cancel")).firstOrNull()
        if (x != null) x.click() else if (driver.findElements(cancel).isNotEmpty()) cancel() else driver.navigate().back()
    }
}

/** The confirmation shown by "Go here instead": CANCEL and CONFIRM. */
class GoHereDialog : BaseScreen() {
    private val cancel: By = Ids.by("btn_cancel")
    private val confirm: By = Ids.by("btn_confirm")

    fun waitUntilShown(timeout: Duration = Duration.ofSeconds(10)): GoHereDialog { waits.visible(confirm, timeout); return this }
    fun cancelText(): String = textOf(cancel)       // "CANCEL"
    fun confirmText(): String = textOf(confirm)     // "CONFIRM"
    fun cancel() = tap(cancel)
    fun confirm() = tap(confirm)
}
