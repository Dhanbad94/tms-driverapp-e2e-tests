package tms.driver.trip

import io.qameta.allure.Allure
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import tms.driver.base.DriverExtension
import tms.driver.core.config.Credentials
import tms.driver.core.device.Adb
import tms.driver.core.driver.DriverSession
import tms.driver.core.wait.Toasts
import tms.driver.fixtures.RideFixture
import tms.driver.fixtures.RideSpec
import tms.driver.fixtures.TripFixtureExtension
import tms.driver.flows.LoginFlow
import tms.driver.screens.home.OdHomeScreen
import tms.driver.screens.requests.AssignmentBar
import tms.driver.screens.requests.CapacityDialog
import tms.driver.screens.requests.RequestsTab
import tms.driver.screens.requests.RideRequestsScreen
import java.time.Duration

/**
 * Phase T14 (SRCH-001, SRCH-004 and INT-020 of docs/next-features-plan.md): what the search shows for rides that need no assignment (a waiting
 * ride and one the rider cancelled), the menu on a search result, assigning from a search result (and its capacity dialog), and pull to
 * refresh. Assignment needs the driver inside the organisation's region: DriverExtension puts the emulator in Chicago.
 */
@Tag("trip")
@Tag("device")
@EnabledIfEnvironmentVariable(named = "TMS_TRIP_API_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TMS_RIDER_API_URL", matches = ".+")
@ExtendWith(DriverExtension::class, TripFixtureExtension::class)
@DisplayName("Driver search on the Requests screen")
class RequestsSearchTest {
    private lateinit var creds: Credentials
    private val pickup = "Terminal 5E"
    private val dropoff = "Door 3 - Bus/Shuttle Ctr"

    @BeforeEach
    fun requireCredentials() {
        val c = Credentials.fromEnvironment()
        assumeTrue(c != null, "TMS_DRIVER_PHONE / TMS_DRIVER_PASSCODE are not set, so this test is skipped")
        creds = c!!
    }

    private fun spec(seed: Long, party: Int = 2, note: String = "Hello") =
        RideSpec(partySize = party, ada = false, note = note, seed = seed, pickupName = pickup, dropoffName = dropoff)

    private fun openRequests(): RideRequestsScreen {
        LoginFlow(creds).loginToHome()
        assertTrue(OdHomeScreen().waitForEmptyState(Duration.ofSeconds(40)))
        return RideRequestsScreen().also { it.openFromHome(); it.openQueue() }
    }

    @Tag("smoke")
    @Test
    @DisplayName("[SRCH-001] Search lists a waiting ride under 'Request Queue' with the assign buttons, and a ride the rider cancelled under 'Completed' without them")
    fun searchShowsEachRideInItsSection(rides: RideFixture) {
        step("Given one ride is waiting and one was cancelled by the rider")
        val waiting = rides.queuedRide(spec(271, party = 2, note = "Waiting"))
        val cancelled = rides.queuedRide(spec(272, party = 3, note = "Cancelled"))
        rides.riderCancels(cancelled)
        val requests = openRequests()
        fun fresh() = requests.also { if (!it.isShown(Duration.ofSeconds(1))) { runCatching { tms.driver.screens.requests.SearchScreen().close() }; it.waitUntilShown() } }.openSearch()
        var search = fresh()

        step("When the driver searches for the waiting ride, then it is listed under 'Request Queue' with both assign buttons")
        search.type(waiting.riderName.takeLast(8))
        assertEquals(listOf("Request Queue"), search.sectionTitles())
        val waitingCard = search.card(waiting.riderName) ?: throw AssertionError("The waiting ride should be listed")
        assertTrue(Regex("Requested (Just now|\\d+ min ago)").matches(waitingCard.statusText().orEmpty()), "Status was '${waitingCard.statusText()}'")
        assertEquals("2", waitingCard.partySize())
        assertEquals("Waiting", waitingCard.notes())
        assertTrue(waitingCard.areAssignButtonsDisplayed())

        step("When the driver searches for the cancelled ride, then it is listed under 'Completed' as cancelled by the guest, without assign buttons")
        search.close(); search = fresh()
        search.type(cancelled.riderName.takeLast(8))
        assertEquals(listOf("Completed"), search.sectionTitles())
        val cancelledCard = search.card(cancelled.riderName) ?: throw AssertionError("The cancelled ride should be listed")
        assertTrue(Regex("Canceled By Guest \\(at \\d{2}:\\d{2} [AP]M\\)").matches(cancelledCard.statusText().orEmpty()), "Status was '${cancelledCard.statusText()}'")
        assertEquals("3", cancelledCard.partySize())
        assertFalse(cancelledCard.areAssignButtonsDisplayed(), "A finished ride must not offer the assign buttons")

        step("And searching the start of both names lists both, the queue first")
        // The server matches the start of a name. The two rides were created seconds apart, so the start of their names (name and the first
        // digits of the number) is shared and picks out only these rides, however many other test rides the last 24 hours hold.
        val shared = waiting.riderName.commonPrefixWith(cancelled.riderName)
        assertTrue(shared.length >= "Appium_Rider_".length + 6, "The two names should share the start of their numbers: $shared")
        search.close(); search = fresh()
        search.type(shared)
        assertTrue(search.sectionTitles().containsAll(listOf("Request Queue", "Completed")), "Sections: ${search.sectionTitles()}")
        assertTrue(search.sectionTitles().indexOf("Request Queue") < search.sectionTitles().indexOf("Completed"))
        assertTrue(search.card(waiting.riderName) != null && search.card(cancelled.riderName) != null)
    }

    @Tag("smoke")
    @Test
    @DisplayName("[SRCH-002] ASSIGN TO SELF on a search result stages the ride, CANCEL undoes it, and CONFIRM assigns it with a toast and returns to Ride Requests")
    fun assignFromSearch(rides: RideFixture) {
        step("Given a ride is waiting and the driver has found it through the search")
        val ride = rides.queuedRide(spec(276, note = "Search assign"))
        val requests = openRequests()
        val search = requests.openSearch().type(ride.riderName.takeLast(8))
        val card = search.card(ride.riderName) ?: throw AssertionError("The ride should be listed")

        step("When the driver taps ASSIGN TO SELF, then the card reads ASSIGNED TO YOU and a bar offers CANCEL and CONFIRM")
        card.tapAssignToSelf()
        val bar = AssignmentBar().waitUntilShown()
        assertEquals("CANCEL", bar.cancelText())
        assertEquals("CONFIRM", bar.confirmText())
        assertEquals("ASSIGNED TO YOU", search.card(ride.riderName)!!.assignedToYouText())

        step("When the driver taps CANCEL, then the bar goes and the card is no longer staged")
        bar.cancel()
        assertTrue(bar.isGone())
        assertTrue(search.card(ride.riderName)!!.areAssignButtonsDisplayed(), "The assign buttons should be offered again")

        step("When the driver stages it again and taps CONFIRM, then a toast confirms and the app returns to Ride Requests")
        search.card(ride.riderName)!!.tapAssignToSelf()
        AssignmentBar().waitUntilShown()
        Toasts.mark(DriverSession.driver)
        AssignmentBar().confirm()
        assertEquals("Assigned Driver Successfully.", Toasts.read(DriverSession.driver, Duration.ofSeconds(10)))
        assertTrue(requests.waitUntilShown().isShown(Duration.ofSeconds(20)), "The Ride Requests screen should be shown again")

        step("And Home shows the ride as the driver's dispatch")
        requests.goHome()
        val home = OdHomeScreen()
        assertTrue(home.waitForTrip(Duration.ofSeconds(30)), "Home should show the ride")
        assertEquals("You have 1 dispatch", home.summaryText())
    }

    @Tag("smoke")
    @Test
    @DisplayName("[SRCH-003] A party larger than the shuttle's seats, staged from a search result, gets the capacity dialog and stays unassigned")
    fun capacityFromSearch(rides: RideFixture) {
        step("Given a ride for 16 guests is waiting and the driver has found it through the search")
        val ride = rides.queuedRide(spec(277, party = 16, note = "Too big"))
        val requests = openRequests()
        val search = requests.openSearch().type(ride.riderName.takeLast(8))
        val card = search.card(ride.riderName) ?: throw AssertionError("The ride should be listed")

        step("When the driver stages it and taps CONFIRM, then a dialog says the shuttle does not have enough capacity")
        card.tapAssignToSelf()
        AssignmentBar().waitUntilShown().confirm()
        val dialog = CapacityDialog().waitUntilShown()
        assertTrue(dialog.messageText().startsWith("Shuttle doesn't have enough capacity"), dialog.messageText())

        step("And closing the dialog leaves the ride unassigned on the server")
        dialog.close()
        val bar = AssignmentBar()
        if (bar.isShown(Duration.ofSeconds(3))) bar.cancel()
        search.close()
        requests.waitUntilShown()
        assertTrue(requests.open(RequestsTab.ASSIGNED).card(ride.riderName, Duration.ofSeconds(3)) == null, "The ride must not be under Assigned")
    }

    @Tag("smoke")
    @Test
    @DisplayName("[SRCH-004] The menu on a waiting ride's search result is the same as on its Requests card: Reject Request, Change Pick-up, Change Drop-off, Mark as Completed and the number")
    fun searchResultMenu(rides: RideFixture) {
        step("Given a ride is waiting")
        val ride = rides.queuedRide(spec(273))
        val requests = openRequests()
        val expected = listOf("Reject Request", "Change Pick-up", "Change Drop-off", "Mark as Completed")

        step("When the driver opens the three dots on the ride's card in the Requests tab, then those entries and the rider's number are offered")
        val cardMenu = requests.card(ride.riderName, Duration.ofSeconds(30))!!.openMenu()
        val onCard = cardMenu.entries()
        assertEquals(expected, onCard.take(4))
        assertTrue(onCard.last().startsWith("+91-"), "The last entry should be the rider's number: ${onCard.last()}")
        cardMenu.dismiss()

        step("And on the same ride found through the search the menu is identical")
        val search = requests.waitUntilShown().openSearch().type(ride.riderName.takeLast(8))
        val onResult = (search.card(ride.riderName) ?: throw AssertionError("The ride should be listed")).openMenu().entries()
        assertEquals(onCard, onResult)
    }

    @Test
    @DisplayName("[INT-020] A ride that arrived while the app had no network is on the Requests tab after pulling to refresh")
    fun pullToRefreshAfterOffline(rides: RideFixture) {
        step("Given a ride is waiting (so the list is not empty) and the driver is on the Requests tab")
        rides.queuedRide(spec(274, note = "Anchor"))
        val requests = openRequests()
        val adb = Adb()
        try {
            step("When a ride is requested while the device is offline and the network then returns")
            assertTrue(adb.setOffline(true), "Could not switch the network off")
            Thread.sleep(4_000)
            val missed = rides.liveRide(spec(275, note = "Missed"))
            try {
                Thread.sleep(3_000)
                adb.setOffline(false)
                Thread.sleep(8_000)
                observe("listed-before-refresh", requests.hasCard(missed.riderName).toString())

                step("Then pulling the list down shows the ride")
                requests.pullToRefresh()
                assertTrue(requests.card(missed.riderName, Duration.ofSeconds(20)) != null, "The ride should be listed after pulling to refresh")
            } finally {
                rides.riderCancels(missed)
            }
        } finally {
            adb.setOffline(false)
        }
    }

    private fun observe(name: String, value: String) {
        Allure.addAttachment(name, value)
        LoggerFactory.getLogger("observed").info("OBSERVED {} = {}", name, value)
    }

    private fun step(description: String) {
        Allure.step(description)
        LoggerFactory.getLogger("steps").info("STEP: {}", description)
    }
}
