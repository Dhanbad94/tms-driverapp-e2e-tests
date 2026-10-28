package tms.driver.trip

import io.qameta.allure.Allure
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import tms.driver.base.DriverExtension
import tms.driver.core.config.Credentials
import tms.driver.fixtures.RideFixture
import tms.driver.fixtures.RideSpec
import tms.driver.fixtures.TestRide
import tms.driver.fixtures.TripFixtureExtension
import tms.driver.flows.LoginFlow
import tms.driver.screens.home.OdHomeScreen
import tms.driver.screens.requests.CardMenuSheet
import tms.driver.screens.requests.RequestCard
import tms.driver.screens.requests.RequestsTab
import tms.driver.screens.requests.RideRequestsScreen
import tms.driver.screens.trip.ConfirmShuttleDialog
import tms.driver.screens.trip.TripViewScreen
import java.time.Duration

/**
 * Phase T5, part 1: the Assigned, In-progress and Completed tabs of Ride Requests and the actions on a ride card's
 * "more options" menu (REQ-001 to 003, 005 to 007 and PAX-002, 004, 005, 006 of docs/trip-lifecycle-test-plan.md).
 * Each test assigns at most one ride before the app login and the fixture cleans up afterwards.
 */
@Tag("trip")
@Tag("device")
@EnabledIfEnvironmentVariable(named = "TMS_TRIP_API_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TMS_RIDER_API_URL", matches = ".+")
@ExtendWith(DriverExtension::class, TripFixtureExtension::class)
@DisplayName("Driver Requests tabs and ride card actions")
class RequestsTabsTest {
    private lateinit var creds: Credentials
    private val pickup = "Terminal 5E"
    private val dropoff = "Door 3 - Bus/Shuttle Ctr"

    @BeforeEach
    fun requireCredentials() {
        val c = Credentials.fromEnvironment()
        assumeTrue(c != null, "TMS_DRIVER_PHONE / TMS_DRIVER_PASSCODE are not set, so this test is skipped")
        creds = c!!
    }

    private fun spec(seed: Long) = RideSpec(partySize = 5, ada = true, note = "Hello", seed = seed, pickupName = pickup, dropoffName = dropoff)

    private fun loginAndOpenRequests(): Pair<OdHomeScreen, RideRequestsScreen> {
        LoginFlow(creds).loginToHome()
        val home = OdHomeScreen()
        home.waitForTrip(Duration.ofSeconds(60)) || home.waitForEmptyState(Duration.ofSeconds(5))
        return home to RideRequestsScreen().also { it.openFromHome() }
    }

    private fun cardOf(screen: RideRequestsScreen, tab: RequestsTab, ride: TestRide): RequestCard {
        screen.open(tab)
        return screen.card(ride.riderName) ?: throw AssertionError("The ride of ${ride.riderName} should be listed on the ${tab.title} tab")
    }

    @Test
    @DisplayName("[REQ-005] The Assigned and In-progress tabs say so when nothing is assigned")
    fun emptyTabs(rides: RideFixture) {
        step("Given the driver has no ride assigned")
        val (_, requests) = loginAndOpenRequests()

        step("When the driver opens the Assigned tab, then it reads 'No Assigned Rides' and its badge shows nothing")
        requests.open(RequestsTab.ASSIGNED)
        assertEquals("No Assigned Rides", requests.emptyMessageText())
        assertEquals(0, requests.tabCount(RequestsTab.ASSIGNED))

        step("When the driver opens the In-progress tab, then it reads 'No In-Progress Rides'")
        requests.open(RequestsTab.IN_PROGRESS)
        assertEquals("No In-Progress Rides", requests.emptyMessageText())
        assertEquals(0, requests.tabCount(RequestsTab.IN_PROGRESS))

        step("And the four tabs are named Requests, Assigned, In-progress and Completed")
        assertEquals(listOf("Requests", "Assigned", "In-progress", "Completed"), requests.tabTitles())
    }

    @Test
    @DisplayName("[REQ-001 / REQ-007 / PAX-002] The Assigned tab shows the ride with its details and a menu that opens the dialer")
    fun assignedTab(rides: RideFixture) {
        step("Given a ride from $pickup to $dropoff for 5 guests with ADA and the note 'Hello' is assigned to the driver")
        val ride = rides.assignedRide(spec(61))
        val (_, requests) = loginAndOpenRequests()

        step("When the driver opens the Assigned tab, then its badge shows 1 and the ride's card lists the stops, the rider, 5 guests, ADA and the note")
        val card = cardOf(requests, RequestsTab.ASSIGNED, ride)
        assertEquals(1, requests.tabCount(RequestsTab.ASSIGNED))
        assertEquals("Trip not Started by Driver", card.statusText())
        assertEquals(pickup, card.pickupName())
        assertEquals(dropoff, card.dropoffName())
        assertEquals(ride.request.pickup.address, card.pickupAddress())
        assertTrue(card.passengerName().orEmpty().equals(ride.riderName, ignoreCase = true), "Card rider was '${card.passengerName()}'")
        assertEquals("5", card.partySize())
        assertTrue(card.isAdaShown(), "The ADA icon should be shown")
        assertEquals("Hello", card.notes())
        assertEquals("ASSIGNED TO YOU", card.assignedToYouText())
        assertEquals("ASSIGN TO DRIVER", card.assignButtonText())

        step("And the In-progress tab has nothing yet")
        requests.open(RequestsTab.IN_PROGRESS)
        assertEquals("No In-Progress Rides", requests.emptyMessageText())

        step("When the driver opens the card's three dots, then the menu offers Move to Request Queue, Cancel Dispatch, Mark as Completed and the rider's number")
        requests.open(RequestsTab.ASSIGNED)
        val menu = cardOf(requests, RequestsTab.ASSIGNED, ride).openMenu()
        val entries = menu.entries()
        assertEquals(listOf("Move to Request Queue", "Cancel Dispatch", "Mark as Completed"), entries.take(3))
        assertEquals(4, entries.size, "Entries: $entries")
        assertTrue(entries[3].startsWith("+91-"), "The last entry should be the rider's number: ${entries[3]}")

        step("When the driver taps the rider's number, then the phone's dialer opens with that number, and coming back leaves the menu open")
        val (dialerPackage, dialled) = menu.tapCallAndReadDialer()
        observe("dialer-package", dialerPackage)
        assertTrue(dialerPackage.contains("dialer"), "The dialer should open, but the front app was $dialerPackage")
        assertEquals(entries[3].filter { it.isDigit() }, dialled.filter { it.isDigit() })
        assertTrue(menu.isShown(Duration.ofSeconds(10)), "The menu should still be open after returning from the dialer")
        menu.dismiss()
        assertTrue(requests.isShown(Duration.ofSeconds(10)), "The Requests screen should be there once the menu is closed")
    }

    @Test
    @DisplayName("[REQ-002] The In-progress tab shows a ride once the trip has started")
    fun inProgressTab(rides: RideFixture) {
        step("Given a ride is assigned and the driver has started the trip")
        val ride = rides.assignedRide(spec(62))
        val (home, requests) = loginAndOpenRequests()
        requests.goHome()
        assertTrue(home.waitForTrip(Duration.ofSeconds(30)))
        home.tapStartTrip()
        ConfirmShuttleDialog().waitUntilShown().confirm()
        TripViewScreen().waitUntilShown()

        step("When the driver opens Ride Requests > In-progress, then the card is there and the Assigned tab no longer lists it")
        DriverBack.toHome()
        val screen = RideRequestsScreen().also { it.openFromHome() }
        val card = cardOf(screen, RequestsTab.IN_PROGRESS, ride)
        observe("in-progress-status", card.statusText().orEmpty())
        assertEquals(1, screen.tabCount(RequestsTab.IN_PROGRESS))
        assertEquals("Shuttle on the way to Pick-up", card.statusText())
        assertEquals(pickup, card.pickupName())
        assertEquals(dropoff, card.dropoffName())
        screen.open(RequestsTab.ASSIGNED)
        assertFalse(screen.hasCard(ride.riderName), "The started ride should have left the Assigned tab")
    }

    @Test
    @DisplayName("[PAX-005 / REQ-003 / REQ-007] Mark as Completed finishes the ride at once and lists it under Completed")
    fun markAsCompleted(rides: RideFixture) {
        step("Given a ride is assigned to the driver")
        val ride = rides.assignedRide(spec(63))
        val (home, requests) = loginAndOpenRequests()
        cardOf(requests, RequestsTab.ASSIGNED, ride).openMenu().markCompleted()

        step("Then the Assigned tab is empty again")
        assertTrue(requests.open(RequestsTab.ASSIGNED).emptyMessageText(Duration.ofSeconds(20)) != null, "The Assigned tab should be empty")
        assertEquals(0, requests.tabCount(RequestsTab.ASSIGNED))

        step("And the Completed tab lists the ride under 'Last 24 Hours' with the driver's name")
        val card = cardOf(requests, RequestsTab.COMPLETED, ride)
        assertTrue(Regex("Drop-off Completed \\(at \\d{2}:\\d{2} [AP]M\\)").matches(card.statusText().orEmpty()), "Status was '${card.statusText()}'")
        assertTrue(requests.isTextShown("Last 24 Hours"))
        assertEquals("Automation Driver (You)", card.driverName())

        step("And Home shows no trip")
        requests.goHome()
        assertTrue(home.waitForEmptyState(Duration.ofSeconds(30)), "Home should show the empty state")
    }

    @Test
    @DisplayName("[PAX-006 / REQ-003] Cancel Dispatch asks for a reason and then shows the ride as cancelled by the driver")
    fun cancelDispatch(rides: RideFixture) {
        step("Given a ride is assigned to the driver")
        val ride = rides.assignedRide(spec(64))
        val (home, requests) = loginAndOpenRequests()

        step("When the driver chooses Cancel Dispatch, then the reasons are offered and DONE waits for a choice")
        val reasons = cardOf(requests, RequestsTab.ASSIGNED, ride).openMenu().cancelDispatch()
        assertEquals("Select Reason", reasons.headingText())
        assertEquals(listOf("Duplicate Entry", "Can't fulfill Special Request", "Service Temporarily Unavailable", "Other"), reasons.reasons())
        assertEquals("DONE", reasons.doneText())
        assertFalse(reasons.isDoneEnabled(), "DONE should wait for a reason")

        step("When the driver backs out of the reasons, then the menu is still open and the ride is still assigned")
        reasons.dismiss()
        assertFalse(reasons.isShown(Duration.ofSeconds(1)), "The reasons should close")
        val menu = CardMenuSheet()
        assertTrue(menu.isShown(Duration.ofSeconds(3)), "The menu should still be open")

        step("When the driver cancels again with 'Duplicate Entry' and taps DONE, then the ride leaves Assigned and shows as cancelled by the driver under Completed")
        val sheet = menu.cancelDispatch()
        sheet.choose("Duplicate Entry")
        sheet.done()
        assertTrue(requests.open(RequestsTab.ASSIGNED).emptyMessageText(Duration.ofSeconds(20)) != null, "The Assigned tab should be empty")
        val card = cardOf(requests, RequestsTab.COMPLETED, ride)
        assertTrue(Regex("Canceled By Driver \\(at \\d{2}:\\d{2} [AP]M\\)").matches(card.statusText().orEmpty()), "Status was '${card.statusText()}'")

        step("And Home shows no trip")
        requests.goHome()
        assertTrue(home.waitForEmptyState(Duration.ofSeconds(30)))
    }

    @Test
    @DisplayName("[PAX-004 / REQ-007] Move to Request Queue puts the ride back among the unassigned requests")
    fun moveToQueue(rides: RideFixture) {
        step("Given a ride is assigned to the driver")
        val ride = rides.assignedRide(spec(65))
        val (home, requests) = loginAndOpenRequests()
        cardOf(requests, RequestsTab.ASSIGNED, ride).openMenu().moveToQueue()

        step("Then the Assigned tab is empty")
        assertTrue(requests.open(RequestsTab.ASSIGNED).emptyMessageText(Duration.ofSeconds(20)) != null, "The Assigned tab should be empty")

        step("And the ride is back in the Requests tab")
        requests.open(RequestsTab.REQUESTS)
        assertTrue(requests.card(ride.riderName, Duration.ofSeconds(30)) != null, "The ride should be listed among the requests")

        step("And Home shows no trip")
        requests.goHome()
        assertTrue(home.waitForEmptyState(Duration.ofSeconds(30)))
    }

    @Test
    @DisplayName("[PAX-001] The Assigned card shows the room number, the flight number and the note")
    fun cardShowsRoomAndFlight(rides: RideFixture) {
        step("Given a ride with room 412, flight UA1234 and the note 'Hello' is assigned to the driver")
        val ride = rides.assignedRide(spec(66).copy(roomNo = "412", flightNo = "UA1234"))
        val (_, requests) = loginAndOpenRequests()

        step("When the driver opens the Assigned tab, then the card shows the room, the flight and the note")
        val card = cardOf(requests, RequestsTab.ASSIGNED, ride)
        assertEquals("412", card.roomText())
        assertEquals("UA1234", card.flightText())
        assertEquals("Hello", card.notes())
    }

    @Test
    @Disabled("Known issue, the developer is fixing it: the rider details sheet in the trip view does not show the room and flight numbers although the ride has them (the Requests card does). Enable when fixed.")
    @DisplayName("[PAX-001] The rider details sheet shows the room number and the flight number")
    fun riderSheetShowsRoomAndFlight(rides: RideFixture) {
        step("Given a ride with room 412, flight UA1234 and the note 'Hello' is assigned and the trip has started")
        rides.assignedRide(spec(67).copy(roomNo = "412", flightNo = "UA1234"))
        LoginFlow(creds).loginToHome()
        val home = OdHomeScreen(); home.waitForTrip(Duration.ofSeconds(60))
        home.tapStartTrip(); ConfirmShuttleDialog().waitUntilShown().confirm()

        step("When the driver opens the rider, then the sheet shows the room and the flight number next to the note")
        val sheet = TripViewScreen().waitUntilShown().also { it.waitForEstimatedTimes() }.tapRider(0)
        assertEquals("Hello", sheet.notesText())
        assertEquals("412", sheet.roomText())
        assertEquals("UA1234", sheet.flightText())
    }

    @Test
    @DisplayName("[PAX-007] Cancelling with the reason 'Other' asks for text, and DONE stays off until some is typed")
    fun cancelWithOtherReason(rides: RideFixture) {
        step("Given a ride is assigned to the driver and its Cancel Dispatch reasons are open")
        val ride = rides.assignedRide(spec(68))
        val (_, requests) = loginAndOpenRequests()
        val reasons = cardOf(requests, RequestsTab.ASSIGNED, ride).openMenu().cancelDispatch()

        step("When the driver chooses 'Other', then the sheet is titled 'Other Reason' with a text box, and DONE is disabled")
        reasons.choose("Other")
        assertEquals("Other Reason", reasons.headingText())
        assertTrue(reasons.isCustomMessageShown(), "A text box should appear")
        assertEquals("What prompted this action?", reasons.customMessageHint())
        assertFalse(reasons.isDoneEnabled(), "DONE should stay disabled with no text")

        step("When the driver types a reason, then DONE is enabled and the ride is cancelled by the driver")
        reasons.typeCustomMessage("Rider asked to change the plan")
        assertTrue(reasons.isDoneEnabled(), "DONE should be enabled once there is text")
        reasons.done()
        assertTrue(requests.open(RequestsTab.ASSIGNED).emptyMessageText(Duration.ofSeconds(20)) != null, "The Assigned tab should be empty")
        val card = cardOf(requests, RequestsTab.COMPLETED, ride)
        assertTrue(Regex("Canceled By Driver \\(at \\d{2}:\\d{2} [AP]M\\)").matches(card.statusText().orEmpty()), "Status was '${card.statusText()}'")
    }

    @Test
    @DisplayName("[REQ-006] A finished ride on the Completed tab offers only the rider's number in its menu")
    fun completedCardMenu(rides: RideFixture) {
        step("Given a ride was assigned and then completed")
        val ride = rides.assignedRide(spec(69))
        val (_, requests) = loginAndOpenRequests()
        cardOf(requests, RequestsTab.ASSIGNED, ride).openMenu().markCompleted()
        assertTrue(requests.open(RequestsTab.ASSIGNED).emptyMessageText(Duration.ofSeconds(20)) != null)

        step("When the driver opens the menu of its card on the Completed tab, then only the rider's number is offered")
        val menu = cardOf(requests, RequestsTab.COMPLETED, ride).openMenu()
        val entries = menu.entries()
        assertEquals(1, entries.size, "Entries: $entries")
        assertTrue(entries[0].startsWith("+91-"), "The only entry should be the rider's number: ${entries[0]}")
    }

    @Test
    @DisplayName("[PAX-005 extra] Once the trip has started, the card menu no longer offers Move to Request Queue")
    fun startedRideMenuHasNoQueueEntry(rides: RideFixture) {
        step("Given a ride is assigned and the driver has started the trip")
        val ride = rides.assignedRide(spec(70))
        val (home, requests) = loginAndOpenRequests()
        requests.goHome()
        assertTrue(home.waitForTrip(Duration.ofSeconds(30)))
        home.tapStartTrip(); ConfirmShuttleDialog().waitUntilShown().confirm()
        TripViewScreen().waitUntilShown()
        DriverBack.toHome()
        val screen = RideRequestsScreen().also { it.openFromHome() }

        step("When the driver opens the In-progress card's menu, then it offers Cancel Dispatch, Mark as Completed and the number only")
        val entries = cardOf(screen, RequestsTab.IN_PROGRESS, ride).openMenu().entries()
        assertEquals(listOf("Cancel Dispatch", "Mark as Completed"), entries.take(2))
        assertEquals(3, entries.size, "Entries: $entries")
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

/** Back out of the trip view to Home (the trip stays started). */
private object DriverBack {
    fun toHome() {
        tms.driver.core.driver.DriverSession.driver.navigate().back()
        Thread.sleep(1_500)
    }
}
