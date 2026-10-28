package tms.driver.trip

import io.qameta.allure.Allure
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import tms.driver.backend.DriverApiClient
import tms.driver.backend.DriverApiConfig
import tms.driver.base.DriverExtension
import tms.driver.core.config.Credentials
import tms.driver.core.device.Adb
import tms.driver.core.driver.DriverSession
import tms.driver.fixtures.RideFixture
import tms.driver.fixtures.RideSpec
import tms.driver.fixtures.TestRide
import tms.driver.fixtures.TripFixtureExtension
import tms.driver.flows.LoginFlow
import tms.driver.screens.home.OdHomeScreen
import tms.driver.screens.requests.AssignmentBar
import tms.driver.screens.requests.CapacityDialog
import tms.driver.screens.requests.RequestCard
import tms.driver.screens.requests.RequestsTab
import tms.driver.screens.requests.RideRequestsScreen
import tms.driver.screens.trip.AssignDriverScreen
import tms.driver.screens.trip.GpsAlertDialog
import tms.driver.screens.trip.TripViewScreen
import java.time.Duration

/**
 * Phase T6: a driver takes a waiting ride from the Requests tab (INT-001 to 007, 012 to 014, 018, 019 and 021 of
 * docs/trip-lifecycle-test-plan.md). Each test leaves a ride UNASSIGNED in the queue (recipe S2) and the fixture removes it.
 * The queue is shared and holds dozens of rides, so the tab is always sorted "Newest First" to find the test ride at the top.
 * Not covered here: live arrival of new rides (T7), the largest party (INT-022) and an off-duty driver (INT-023), which need a decision.
 */
@Tag("trip")
@Tag("device")
@EnabledIfEnvironmentVariable(named = "TMS_TRIP_API_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TMS_RIDER_API_URL", matches = ".+")
@ExtendWith(DriverExtension::class, TripFixtureExtension::class)
@DisplayName("Driver takes a ride from the Requests tab")
class RequestsIntakeTest {
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

    private fun loginAndOpenQueue(): Pair<OdHomeScreen, RideRequestsScreen> {
        LoginFlow(creds).loginToHome()
        val home = OdHomeScreen()
        assertTrue(home.waitForEmptyState(Duration.ofSeconds(40)), "The driver should start with no trip")
        return home to RideRequestsScreen().also { it.openFromHome(); it.openQueue() }
    }

    private fun queueCard(screen: RideRequestsScreen, ride: TestRide): RequestCard =
        screen.card(ride.riderName, Duration.ofSeconds(30)) ?: throw AssertionError("The ride of ${ride.riderName} should be listed in the Requests tab")

    @Test
    @DisplayName("[INT-001 / 002 / 003 / 004] A waiting ride is listed with its details and the two assign buttons")
    fun waitingRideCard(rides: RideFixture) {
        step("Given a ride from $pickup to $dropoff for 5 guests with ADA, room 412, flight UA1234 and the note 'Hello' is waiting in the queue")
        val ride = rides.queuedRide(spec(101).copy(roomNo = "412", flightNo = "UA1234"))
        val (_, requests) = loginAndOpenQueue()

        step("When the driver opens the Requests tab, then the ride's card shows when it was requested, both stops and the rider's name in title case")
        val card = queueCard(requests, ride)
        assertTrue(Regex("Requested (Just now|\\d+ min ago)").matches(card.statusText().orEmpty()), "Status was '${card.statusText()}'")
        assertEquals(pickup, card.pickupName())
        assertEquals(dropoff, card.dropoffName())
        assertEquals(ride.request.pickup.address, card.pickupAddress())
        assertEquals(ride.riderName.replaceFirst("_Rider_", "_rider_"), card.passengerName())

        step("And it shows 5 guests, the ADA icon, the room, the flight and the note")
        assertEquals("5", card.partySize())
        assertTrue(card.isAdaShown())
        assertEquals("412", card.roomText())
        assertEquals("UA1234", card.flightText())
        assertEquals("Hello", card.notes())

        step("And the card offers ASSIGN TO DRIVER and ASSIGN TO SELF, and the Requests badge counts the waiting rides")
        assertEquals("ASSIGN TO DRIVER", card.assignButtonText())
        assertEquals("ASSIGN TO SELF", card.assignedToYouText())
        assertTrue(requests.tabCount(RequestsTab.REQUESTS) >= 1, "The Requests badge should count the waiting rides")
    }

    @Test
    @DisplayName("[INT-018] The sort sheet offers Oldest First, Newest First and Rider Name, and reorders the list")
    fun sorting(rides: RideFixture) {
        step("Given a ride is waiting in the queue")
        val ride = rides.queuedRide(spec(102))
        LoginFlow(creds).loginToHome()
        OdHomeScreen().waitForEmptyState(Duration.ofSeconds(40))
        val requests = RideRequestsScreen().also { it.openFromHome(); it.open(RequestsTab.REQUESTS) }

        step("Then the list starts oldest first and the sort sheet offers three orders")
        assertEquals("Oldest", requests.sortLabelText())
        assertEquals(listOf("Oldest First", "Newest First", "Rider Name (A-Z)"), requests.openSortOptions())
        requests.closeSortOptions()

        step("When the driver chooses Newest First, then the new ride is the first card")
        requests.sortBy(RideRequestsScreen.Sort.NEWEST)
        assertEquals("Newest", requests.sortLabelText())
        assertEquals(ride.riderName.replaceFirst("_Rider_", "_rider_"), requests.cardNames().first())

        step("When the driver chooses Rider Name, then the visible riders are in alphabetical order")
        requests.sortBy(RideRequestsScreen.Sort.RIDER)
        observe("rider-sort-label", requests.sortLabelText())
        val names = requests.cardNames()
        assertTrue(names.size >= 2, "At least two cards are needed: $names")
        assertEquals(names.sortedBy { it.lowercase() }, names, "Riders should be A to Z")
    }

    @Test
    @DisplayName("[INT-019] Search needs two characters, finds the ride by its name and says 'No Results Found' otherwise")
    fun searching(rides: RideFixture) {
        step("Given a ride is waiting in the queue")
        val ride = rides.queuedRide(spec(103))
        val (_, requests) = loginAndOpenQueue()

        step("When the driver opens the search, then it asks for a driver or rider")
        val search = requests.openSearch()
        assertEquals("Search Driver or Rider", search.hintText())

        step("When one character is typed, then it still shows the hint")
        search.type("a")
        assertEquals("Search Driver or Rider", search.messageOrNull())

        step("When the end of the rider's name is typed, then the ride is listed under 'Request Queue'")
        search.type(ride.riderName.takeLast(8))
        assertTrue(search.hasSectionTitle("Request Queue"), "The results should have a 'Request Queue' section")
        assertTrue(search.resultNames().any { it.endsWith(ride.riderName.takeLast(8)) }, "Results: ${search.resultNames()}")

        step("When something that matches nobody is typed, then 'No Results Found' is shown")
        search.type("zzqxjw")
        assertEquals("No Results Found", search.messageOrNull())

        step("And closing the search returns to the Requests tabs")
        search.close()
        assertTrue(requests.waitUntilShown().isShown(Duration.ofSeconds(10)))
    }

    @Test
    @DisplayName("[INT-012 / INT-006] ASSIGN TO SELF stages the ride with a CONFIRM & START TRIP bar, and CANCEL assigns nothing")
    fun stageAndCancel(rides: RideFixture) {
        step("Given a ride is waiting in the queue")
        val ride = rides.queuedRide(spec(104))
        val (home, requests) = loginAndOpenQueue()

        step("When the driver taps ASSIGN TO SELF, then the button reads ASSIGNED TO YOU and a bar offers CANCEL and CONFIRM & START TRIP")
        queueCard(requests, ride).tapAssignToSelf()
        val bar = AssignmentBar().waitUntilShown()
        assertEquals("ASSIGNED TO YOU", queueCard(requests, ride).assignedToYouText())
        assertEquals("CANCEL", bar.cancelText())
        assertEquals("CONFIRM & START TRIP", bar.confirmText())

        step("When the driver taps CANCEL, then the bar goes, the button reads ASSIGN TO SELF again and the ride is still in the queue")
        bar.cancel()
        assertTrue(bar.isGone(), "The bar should disappear")
        assertEquals("ASSIGN TO SELF", queueCard(requests, ride).assignedToYouText())
        assertTrue(requests.open(RequestsTab.ASSIGNED).emptyMessageText(Duration.ofSeconds(10)) != null, "Nothing should be assigned")

        step("And Home still shows no trip")
        requests.goHome()
        assertTrue(home.waitForEmptyState(Duration.ofSeconds(30)))
    }

    @Test
    @DisplayName("[INT-005 / INT-006] Confirming ASSIGN TO SELF assigns the ride, starts the trip and opens the trip view")
    fun stageAndConfirm(rides: RideFixture) {
        step("Given a ride from $pickup to $dropoff is waiting in the queue")
        val ride = rides.queuedRide(spec(105))
        val (home, requests) = loginAndOpenQueue()

        step("When the driver taps ASSIGN TO SELF and then CONFIRM & START TRIP")
        queueCard(requests, ride).tapAssignToSelf()
        AssignmentBar().waitUntilShown().confirm()

        step("Then the trip view opens with the pickup and the drop-off")
        val trip = TripViewScreen().waitUntilShown()
        assertEquals("Driving to", trip.titleText())
        assertTrue(trip.waitForEstimatedTimes())
        assertEquals(listOf(pickup, dropoff), trip.stopNames())

        step("And going back shows the ride under In-progress and Home as the current trip")
        DriverSession.driver.navigate().back()
        Thread.sleep(1_500)
        assertTrue(home.waitForTrip(Duration.ofSeconds(30)), "Home should show the trip")
        assertEquals("Current Trip", home.summaryText())
        RideRequestsScreen().also { it.openFromHome() }.open(RequestsTab.IN_PROGRESS).let {
            assertTrue(it.card(ride.riderName, Duration.ofSeconds(20)) != null, "The ride should be In-progress")
        }
    }

    @Test
    @DisplayName("[INT-013] With location services off, assigning a ride asks for GPS access and assigns nothing")
    fun gpsOffBlocksAssigning(rides: RideFixture) {
        step("Given a ride is waiting in the queue")
        val ride = rides.queuedRide(spec(106))
        val (home, requests) = loginAndOpenQueue()
        val adb = Adb()
        try {
            step("When location is switched off and the driver stages the ride and taps CONFIRM & START TRIP")
            assertTrue(adb.setLocationEnabled(false).ok, "Could not switch location off through adb")
            Thread.sleep(2_000)
            queueCard(requests, ride).tapAssignToSelf()
            AssignmentBar().waitUntilShown().confirm()

            step("Then 'Provide GPS Access.' explains that location data is needed")
            val gps = GpsAlertDialog().waitUntilShown()
            assertEquals("Provide GPS Access.", gps.titleText())
            assertEquals("Location Data is required so that driver can see your location on map.", gps.messageText())
        } finally {
            adb.setLocationEnabled(true)
        }
        step("And once the driver is back in the app, no trip was started")
        val driver = DriverSession.driver
        runCatching { driver.activateApp(DriverSession.config.appPackage) }
        Thread.sleep(2_000)
        runCatching { GpsAlertDialog().ok(); Thread.sleep(2_000); driver.activateApp(DriverSession.config.appPackage) }
        Thread.sleep(2_000)
        assertFalse(TripViewScreen().isShown(Duration.ofSeconds(3)), "The trip view must not open without GPS")
    }

    @Test
    @DisplayName("[INT-014] A party larger than the shuttle's 15 seats cannot be assigned")
    fun partyLargerThanSeats(rides: RideFixture) {
        step("Given a ride for 16 guests is waiting in the queue")
        val ride = rides.queuedRide(spec(107).copy(partySize = 16))
        val (home, requests) = loginAndOpenQueue()

        step("When the driver stages the ride and confirms")
        queueCard(requests, ride).tapAssignToSelf()
        AssignmentBar().waitUntilShown().confirm()

        step("Then a dialog says the shuttle does not have enough capacity, and no trip opens")
        val dialog = CapacityDialog().waitUntilShown()
        assertEquals("Shuttle doesn't have enough capacity, please select another.", dialog.messageText())
        assertFalse(TripViewScreen().isShown(Duration.ofSeconds(2)))

        step("When the driver closes the dialog, then nothing is assigned and the ride is still waiting")
        dialog.close()
        assertTrue(requests.waitUntilShown().open(RequestsTab.ASSIGNED).emptyMessageText(Duration.ofSeconds(10)) != null, "Nothing should be assigned")
        requests.goHome()
        assertTrue(home.waitForEmptyState(Duration.ofSeconds(30)))
    }

    @Test
    @DisplayName("[INT-021] A long rider name with accents and punctuation is shown without hiding the assign buttons")
    fun longSpecialName(rides: RideFixture) {
        step("Given a ride for 'Appium_Zoë_O'Brien-Smith_Maximilian_Bartholomew_Fitzgerald' is waiting in the queue")
        rides.queuedRide(spec(108).copy(namePrefix = "Appium_Zoë_O'Brien-Smith_Maximilian_Bartholomew_Fitzgerald"))
        val (_, requests) = loginAndOpenQueue()

        step("Then the first card shows the name and both assign buttons are on screen")
        val card = requests.firstCard() ?: throw AssertionError("A card should be listed")
        observe("long-name-shown", card.passengerName().orEmpty())
        // The whole name is shown, not cut off; the app lower-cases everything after the first letter.
        assertEquals("Appium_zoë_o'brien-smith_maximilian_bartholomew_fitzgerald_", card.passengerName().orEmpty().dropLast(13))
        assertTrue(card.areAssignButtonsDisplayed(), "ASSIGN TO DRIVER and ASSIGN TO SELF should both be visible")
    }

    @Test
    @Disabled("Waiting on the developer / a signed-in second driver: the Assign Driver list does not offer 'Automation Driver 2' (id 3355) even with the account on duty and (reported) open in another app, so a ride cannot be handed to it. Enable when it is listed.")
    @DisplayName("[INT-007] ASSIGN TO DRIVER hands a waiting ride to another driver, and it leaves the queue")
    fun assignToAnotherDriver(rides: RideFixture) {
        val secondConfig = DriverApiConfig.forSecondDriverFromEnvironment()
        assumeTrue(secondConfig != null, "TMS_DRIVER2_PHONE / TMS_DRIVER2_PASSCODE are not set, so this test is skipped")
        val otherDriver = "Automation Driver 2"
        step("Given a ride is waiting in the queue")
        val ride = rides.queuedRide(spec(109))
        val (home, requests) = loginAndOpenQueue()
        try {
            step("When the driver taps ASSIGN TO DRIVER, picks $otherDriver and confirms")
            queueCard(requests, ride).tapAssignToDriver()
            val assign = AssignDriverScreen().waitUntilShown()
            assertTrue(assign.showDriver(otherDriver), "$otherDriver should be offered, but the list shows ${assign.visibleDrivers()}")
            assign.choose(otherDriver)
            assign.confirm()

            step("Then the ride leaves the queue, Home shows no trip, and the other driver has the ride")
            assertTrue(requests.waitUntilShown().open(RequestsTab.ASSIGNED).emptyMessageText(Duration.ofSeconds(30)) != null)
            requests.goHome()
            assertTrue(home.waitForEmptyState(Duration.ofSeconds(30)))
            val theirs = DriverApiClient.login(secondConfig!!).myActiveRides().map { it.name }
            assertTrue(theirs.any { it.equals(ride.riderName, ignoreCase = true) }, "The second driver should have '${ride.riderName}', has $theirs")
        } finally {
            runCatching {
                val client = DriverApiClient.login(secondConfig!!)
                client.myActiveRides().filter { it.name.equals(ride.riderName, ignoreCase = true) }.forEach { client.cancelRide(it.id, "E2E teardown") }
            }.onFailure { LoggerFactory.getLogger("cleanup").warn("Could not clean the second driver: {}", it.message) }
        }
    }

    @Test
    @DisplayName("[INT-022] The largest party the rider app allows (75) is shown correctly, and one more (76) is refused by the Rider API")
    fun largestParty(rides: RideFixture) {
        step("Given a rider asks for 76 guests, then, the Rider API refuses it")
        val refused = runCatching { rides.liveRide(spec(111).copy(partySize = 76)) }
        assertTrue(refused.isFailure, "76 guests should be refused")
        observe("party-76", refused.exceptionOrNull()?.message.orEmpty())
        assertTrue(refused.exceptionOrNull()?.message.orEmpty().contains("400"), "Expected a 400, got ${refused.exceptionOrNull()?.message}")

        step("And a ride for 75 guests is waiting in the queue")
        val ride = rides.queuedRide(spec(112).copy(partySize = 75, ada = false))
        val (_, requests) = loginAndOpenQueue()

        step("Then its card shows 75 guests and both assign buttons")
        val card = queueCard(requests, ride)
        assertEquals("75", card.partySize())
        assertTrue(card.areAssignButtonsDisplayed())

        step("And trying to take it is answered with the capacity message, as 75 is more than the 15 seats")
        card.tapAssignToSelf()
        AssignmentBar().waitUntilShown().confirm()
        assertEquals("Shuttle doesn't have enough capacity, please select another.", CapacityDialog().waitUntilShown().messageText())
    }

    @Test
    @DisplayName("[INT-023] An off-duty driver can see and take a waiting ride, and starting the trip puts the driver back on duty")
    fun offDutyDriver(rides: RideFixture) {
        step("Given a ride is waiting and the driver is on Home")
        val ride = rides.queuedRide(spec(113))
        LoginFlow(creds).loginToHome()
        val shell = tms.driver.screens.shell.HomeShell()
        val home = OdHomeScreen()
        assertTrue(home.waitForEmptyState(Duration.ofSeconds(40)))
        val wasOn = shell.dutyState() == "ON"
        try {
            step("When the driver switches duty OFF, then Home says so")
            if (wasOn) { shell.toggleDuty(); assertTrue(shell.waitForDuty("OFF"), "Duty should switch to OFF") }
            observe("home-texts-off-duty", Regex("""\btext="([^"]{2,60})"""").findAll(DriverSession.driver.pageSource).map { it.groupValues[1] }.toSet().joinToString(" | "))

            step("And the Requests tab is still reachable and lists the waiting ride")
            val requests = RideRequestsScreen()
            val reachable = runCatching { requests.openFromHome(); requests.openQueue(); true }.getOrDefault(false)
            observe("requests-reachable-off-duty", reachable.toString())
            assertTrue(reachable, "The Requests tab should open while off duty")
            val card = requests.card(ride.riderName, Duration.ofSeconds(30))
            observe("ride-listed-off-duty", (card != null).toString())
            assertTrue(card != null, "The waiting ride should be listed while off duty")

            step("When the driver takes it, then duty does not stop it: the ride is staged, confirmed and the trip view opens")
            card!!.tapAssignToSelf()
            assertTrue(AssignmentBar().isShown(Duration.ofSeconds(6)), "The ride should be staged even while off duty")
            AssignmentBar().confirm()
            assertTrue(TripViewScreen().isShown(Duration.ofSeconds(30)), "The trip view should open even while off duty")

            step("And once the trip has started, Home shows the driver ON duty again")
            DriverSession.driver.navigate().back()
            Thread.sleep(2_000)
            assertTrue(shell.waitForDuty("ON", Duration.ofSeconds(30)), "Starting the trip should mark the driver ON duty, but duty reads '${runCatching { shell.dutyState() }.getOrNull()}'")
        } finally {
            // Always get the QA driver back on duty: go back to Home first if a trip view opened.
            repeat(4) { if (!shell.isShown(Duration.ofSeconds(2))) { runCatching { DriverSession.driver.navigate().back() }; Thread.sleep(1_500) } }
            runCatching { RideRequestsScreen().goHome() }
            if (wasOn && runCatching { shell.dutyState() }.getOrNull() != "ON") { shell.toggleDuty(); shell.waitForDuty("ON") }
        }
        step("Then the driver is on duty")
        assertEquals("ON", shell.dutyState())
    }

    @Test
    @DisplayName("[INT-017] A ride that belongs to another driver can be taken over with ASSIGN TO SELF and CONFIRM, and CANCEL leaves it where it was")
    fun takeRideOfAnotherDriver(rides: RideFixture) {
        val qaConfig = DriverApiConfig.fromEnvironment()
        val secondConfig = DriverApiConfig.forSecondDriverFromEnvironment()
        assumeTrue(qaConfig != null && secondConfig != null, "Driver API settings for both drivers are not set, so this test is skipped")
        step("Given a ride is waiting and is then assigned to Automation Driver 2 through the API")
        val ride = rides.queuedRide(spec(110))
        val second = DriverApiClient.login(secondConfig!!)
        try {
            val result = DriverApiClient.login(qaConfig!!).assign(ride.dispatchId, second.driverId())
            observe("assign-to-driver-2", "ok=${result.ok} code=${result.code} message=${result.message}")
            assumeTrue(result.ok, "The ride could not be assigned to the second driver (${result.message}), so the scenario cannot be set up")

            LoginFlow(creds).loginToHome()
            val home = OdHomeScreen()
            assertTrue(home.waitForEmptyState(Duration.ofSeconds(40)), "The QA driver should have no trip")
            val requests = RideRequestsScreen().also { it.openFromHome() }

            step("When the driver looks for the ride under Assigned, then it is listed, its first button names the other driver and the second offers ASSIGN TO SELF")
            requests.open(RequestsTab.ASSIGNED)
            val card = requests.card(ride.riderName, Duration.ofSeconds(30)) ?: throw AssertionError("The ride of the other driver should be listed under Assigned")
            observe("card-buttons", "${card.assignButtonText()} / ${card.assignedToYouText()}")
            observe("card-status", card.statusText().orEmpty())
            assertTrue(card.assignButtonText().orEmpty().startsWith("AUTOMATION"), "The first button should name the other driver, but reads '${card.assignButtonText()}'")
            assertEquals("ASSIGN TO SELF", card.assignedToYouText())

            step("When the driver taps ASSIGN TO SELF, then the card turns to ASSIGNED TO YOU and a bar offers CANCEL and CONFIRM")
            card.tapAssignToSelf()
            val bar = AssignmentBar().waitUntilShown()
            assertEquals("CANCEL", bar.cancelText())
            assertEquals("CONFIRM", bar.confirmText())
            assertEquals("ASSIGNED TO YOU", requests.card(ride.riderName)!!.assignedToYouText())

            step("When the driver taps CANCEL, then the ride stays with the other driver")
            bar.cancel()
            assertTrue(bar.isGone())
            assertTrue(requests.card(ride.riderName)!!.assignButtonText().orEmpty().startsWith("AUTOMATION"))
            requests.goHome()
            assertTrue(home.waitForEmptyState(Duration.ofSeconds(30)), "The QA driver still has no trip")

            step("When the driver stages it again and taps CONFIRM, then the ride becomes the QA driver's (there is no extra 'Assign to Self?' question)")
            requests.openFromHome().open(RequestsTab.ASSIGNED)
            requests.card(ride.riderName, Duration.ofSeconds(20))!!.tapAssignToSelf()
            AssignmentBar().waitUntilShown().confirm()
            requests.goHome()
            assertTrue(home.waitForTrip(Duration.ofSeconds(40)), "Home should now show the ride")
            observe("home-after-take-over", home.summaryText())
        } finally {
            runCatching {
                second.myActiveRides().filter { it.name.equals(ride.riderName, ignoreCase = true) }.forEach { second.cancelRide(it.id, "E2E teardown") }
            }.onFailure { LoggerFactory.getLogger("cleanup").warn("Could not clean the second driver: {}", it.message) }
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
