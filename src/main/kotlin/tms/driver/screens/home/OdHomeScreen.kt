package tms.driver.screens.home

import org.openqa.selenium.By
import org.openqa.selenium.remote.RemoteWebElement
import tms.driver.screens.BaseScreen
import tms.driver.screens.Ids
import java.time.Duration

/**
 * Home dashboard of the on-demand (ASAP) driver (`MainFragment`).
 * All ids below were confirmed in a live UI dump of the staging build while the QA driver had one dispatch:
 * "You have 1 dispatch" / "5 Guests" / "ADA" / "2 Stops" / START TRIP (the button text is upper case).
 */
class OdHomeScreen : BaseScreen() {
    private val emptyState: By = Ids.by("atv_no_dispatch")
    private val summary: By = Ids.by("atv_label")
    private val guests: By = Ids.by("tv_guest")
    private val ada: By = Ids.by("tv_ada")
    private val stops: By = Ids.by("tv_stops")
    private val startButton: By = Ids.by("btn_go")
    private val organisation: By = Ids.by("tv_org")
    private val shuttle: By = Ids.by("tv_shuttle")
    private val emptyImage: By = Ids.by("aiv_no_dispatch")
    private val requestsBadge: By = Ids.by("atv_home_notification_count")
    private val newRequestsBanner: By = Ids.by("new_request_text")
    private val refreshArea: By = Ids.by("parentSRL")

    fun isEmptyStateShown(): Boolean = waits.isVisibleNow(emptyState)
    fun hasDispatchSummary(): Boolean = waits.isVisibleNow(summary)
    fun isStartButtonShown(): Boolean = waits.isVisibleNow(startButton)
    fun isAdaShown(): Boolean = waits.isVisibleNow(ada)

    /** Waits until the dashboard shows a trip (summary or start button); false if none appears in time. */
    fun waitForTrip(timeout: Duration): Boolean =
        runCatching { waits.anyVisible(summary, startButton, timeout = timeout) }.isSuccess

    fun summaryText(): String = textOf(summary)      // e.g. "You have 1 dispatch"
    fun guestsText(): String = textOf(guests)        // e.g. "5 Guests"
    fun stopsText(): String = textOf(stops)          // e.g. "2 Stops"
    fun startButtonText(): String = textOf(startButton)
    fun organisationText(): String = textOf(organisation)
    fun shuttleText(): String = textOf(shuttle)
    fun tapShuttle() = tap(shuttle)
    /** The organisation is a link only for accounts with several organisations; on a single-organisation account it is not enabled. */
    fun isOrganisationEnabled(): Boolean = driver.findElement(organisation).isEnabled

    fun emptyTitleText(): String = textOf(emptyState)             // "You have No Trips Assigned"
    fun isEmptyImageShown(): Boolean = waits.isVisibleNow(emptyImage)  // the illustration above the message

    /** Waits until the "no trips" message is shown; false if it does not appear in time. */
    fun waitForEmptyState(timeout: Duration): Boolean = waits.appears(emptyState, timeout)

    /** Number on the Requests button (rides waiting in the shared queue); null when it is not shown. */
    fun requestsBadgeCount(): Int? =
        driver.findElements(requestsBadge).firstOrNull()?.let { runCatching { it.text.trim().toInt() }.getOrNull() }

    fun newRequestsBannerText(): String? = driver.findElements(newRequestsBanner).firstOrNull()?.let { runCatching { it.text }.getOrNull() }

    /** Swipes down on the dashboard, like the pull-to-refresh gesture. */
    fun pullToRefresh() {
        val area = waits.visible(refreshArea) as RemoteWebElement
        driver.executeScript("mobile: swipeGesture", mapOf("elementId" to area.id, "direction" to "down", "percent" to 0.8))
    }

    fun tapStartTrip() = tap(startButton)

    /** Two quick taps on the same START TRIP button (a driver tapping twice). */
    fun doubleTapStartTrip() {
        val el = waits.clickable(startButton)
        el.click()
        runCatching { el.click() }
    }
}
