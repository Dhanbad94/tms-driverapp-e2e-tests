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
import tms.driver.backend.DriverApiClient
import tms.driver.backend.DriverApiConfig
import tms.driver.base.DriverExtension
import tms.driver.core.config.Credentials
import tms.driver.core.driver.DriverSession
import tms.driver.fixtures.RideFixture
import tms.driver.fixtures.RideSpec
import tms.driver.fixtures.TripFixtureExtension
import tms.driver.flows.LoginFlow
import tms.driver.screens.home.OdHomeScreen
import tms.driver.screens.requests.RequestsTab
import tms.driver.screens.requests.RideRequestsScreen
import tms.driver.screens.trip.AssignDriverScreen
import tms.driver.screens.trip.ConfirmShuttleDialog
import tms.driver.screens.trip.RiderDetailsSheet
import tms.driver.screens.trip.TripViewScreen
import java.time.Duration

/**
 * Phase T5, part 2: what a driver can do with a rider from the trip view and from the Assigned card
 * (PAX-003, 008, 009, 010 of docs/trip-lifecycle-test-plan.md). Actually handing a ride to another driver is not automated:
 * the Assign Driver list did not offer the second QA driver (see the plan's Phase T5 findings).
 */
@Tag("trip")
@Tag("device")
@EnabledIfEnvironmentVariable(named = "TMS_TRIP_API_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TMS_RIDER_API_URL", matches = ".+")
@ExtendWith(DriverExtension::class, TripFixtureExtension::class)
@DisplayName("Driver actions on a rider")
class PassengerActionsTest {
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

    private fun startTrip(): TripViewScreen {
        LoginFlow(creds).loginToHome()
        val home = OdHomeScreen()
        assertTrue(home.waitForTrip(Duration.ofSeconds(60)), "A trip should be shown after logging in")
        home.tapStartTrip()
        ConfirmShuttleDialog().waitUntilShown().confirm()
        return TripViewScreen().waitUntilShown().also { it.waitForEstimatedTimes() }
    }

    @Test
    @DisplayName("[PAX-010] The rider menu offers Move to Request Queue, Change Drop-off, Assign to another Driver and the number, and nothing else")
    fun riderMenuEntries(rides: RideFixture) {
        step("Given a ride is assigned and the driver has started the trip")
        rides.assignedRide(spec(71))
        val trip = startTrip()

        step("When the driver opens the rider and then its three dots, then exactly those entries are offered (no Send Message, no Return to Dispatcher)")
        val menu = trip.tapRider(0).openMenu()
        val entries = menu.entries()
        assertEquals(listOf("Move to Request Queue", "Change Drop-off", "Assign to another Driver"), entries.take(3))
        assertEquals(4, entries.size, "Entries: $entries")
        assertTrue(entries[3].startsWith("+91-"), "The last entry should be the rider's number: ${entries[3]}")
    }

    @Test
    @DisplayName("[PAX-008] Change Drop-off lists the current stops, can be cancelled, and moves the drop-off to the stop the driver picks")
    fun changeDropoff(rides: RideFixture) {
        val newStop = "Mannheim Rd"
        step("Given a ride from $pickup to $dropoff is assigned and the trip has started")
        rides.assignedRide(spec(72))
        val trip = startTrip()

        step("When the driver opens Change Drop-off from the rider's menu, then the screen shows the current pickup and drop-off")
        val change = trip.tapRider(0).openMenu().changeDropoff()
        assertEquals("Change Drop-off", change.titleText())
        assertEquals(pickup, change.pickupName())
        assertEquals(dropoff, change.dropoffName())
        assertEquals("Select - Search Drop-off", change.searchHint())
        assertEquals("UPDATE", change.updateText())
        assertEquals("CANCEL", change.cancelText())

        step("When the driver cancels, then the trip view still has the old stops")
        change.cancel()
        assertTrue(trip.isShown(Duration.ofSeconds(15)), "The trip view should be shown")
        assertEquals(listOf(pickup, dropoff), trip.stopNames())

        step("When the driver changes the drop-off to '$newStop' and taps UPDATE, then the trip view lists the new drop-off")
        val again = trip.tapRider(0).openMenu().changeDropoff()
        val found = again.search("Mannheim")
        observe("search-results", found.joinToString(" | "))
        assertTrue(newStop in found, "The search should offer $newStop, but offered $found")
        again.choose(newStop)
        again.update()
        assertTrue(trip.isShown(Duration.ofSeconds(30)), "The trip view should be shown again")
        Thread.sleep(3_000)
        trip.pullToRefresh()
        Thread.sleep(3_000)
        assertEquals(listOf(pickup, newStop), trip.stopNames())
    }

    @Test
    @DisplayName("[PAX-003] PICK UP from the rider sheet needs a second tap to confirm")
    fun pickUpFromTripView(rides: RideFixture) {
        step("Given a ride is assigned and the trip has started")
        rides.assignedRide(spec(73))
        val trip = startTrip()

        step("When the driver opens the rider and taps PICK UP, then the button asks for a confirmation")
        val sheet = trip.tapRider(0)
        assertEquals("PICK UP", sheet.actionText())
        sheet.tapAction()
        observe("action-after-first-tap", sheet.actionText())
        assertEquals("CONFIRM", sheet.actionText())

        step("When the driver confirms, then the sheet closes")
        sheet.tapAction()
        assertFalse(sheet.isShown(Duration.ofSeconds(10)), "The sheet should close after CONFIRM")
        Thread.sleep(3_000)
        assertEquals("Driving to", trip.titleText())
    }

    @Test
    @DisplayName("[PAX-009] ASSIGN TO DRIVER opens the Assign Driver screen with the available drivers, and leaving it keeps the ride with this driver")
    fun assignDriverScreen(rides: RideFixture) {
        step("Given a ride from $pickup to $dropoff is assigned to the QA driver")
        val ride = rides.assignedRide(spec(74))
        LoginFlow(creds).loginToHome()
        val home = OdHomeScreen(); home.waitForTrip(Duration.ofSeconds(60))
        val requests = RideRequestsScreen().also { it.openFromHome() }
        requests.open(RequestsTab.ASSIGNED)
        val card = requests.card(ride.riderName) ?: throw AssertionError("The ride should be on the Assigned tab")

        step("When the driver taps ASSIGN TO DRIVER, then the Assign Driver screen opens with a list of available drivers")
        card.tapAssignToDriver()
        val assign = AssignDriverScreen().waitUntilShown()
        assertEquals("Assign Driver", assign.titleText())
        // Only the drivers on screen are read: scrolling this screen (it has a map) can make the emulator drop its UI server.
        val offered = assign.visibleDrivers()
        observe("drivers-on-screen", offered.joinToString(" | "))
        assertTrue(offered.isNotEmpty(), "At least one available driver should be listed")
        assertFalse(assign.isConfirmShown(), "CONFIRM should wait for a driver to be picked")

        step("When the driver picks a driver, then CONFIRM appears; backing out without confirming changes nothing")
        assign.choose(offered.first())
        assertEquals("CONFIRM", assign.confirmText())
        DriverSession.driver.navigate().back()
        assertTrue(requests.waitUntilShown().isShown(Duration.ofSeconds(10)))
        requests.open(RequestsTab.ASSIGNED)
        assertTrue(requests.hasCard(ride.riderName) || requests.card(ride.riderName) != null, "The ride should still be assigned to the QA driver")
        assertEquals(1, requests.tabCount(RequestsTab.ASSIGNED))
    }

    @Test
    @Disabled("Waiting on the developer / a signed-in second driver: the Assign Driver list does not offer 'Automation Driver 2' (id 3355) even with the account on duty and (reported) open in another app, so a ride cannot be handed to it. Enable when it is listed.")
    @DisplayName("[PAX-009] A ride assigned to another driver leaves this driver and shows up for the other one")
    fun assignToAnotherDriver(rides: RideFixture) {
        val secondConfig = DriverApiConfig.forSecondDriverFromEnvironment()
        assumeTrue(secondConfig != null, "TMS_DRIVER2_PHONE / TMS_DRIVER2_PASSCODE are not set, so this test is skipped")
        val otherDriver = "Automation Driver 2"
        step("Given a ride is assigned to the QA driver")
        val ride = rides.assignedRide(spec(75))
        LoginFlow(creds).loginToHome()
        val home = OdHomeScreen(); home.waitForTrip(Duration.ofSeconds(60))
        val requests = RideRequestsScreen().also { it.openFromHome() }
        requests.open(RequestsTab.ASSIGNED)
        val card = requests.card(ride.riderName) ?: throw AssertionError("The ride should be on the Assigned tab")
        try {
            step("When the driver taps ASSIGN TO DRIVER, picks $otherDriver and confirms")
            card.tapAssignToDriver()
            val assign = AssignDriverScreen().waitUntilShown()
            assertTrue(assign.showDriver(otherDriver), "$otherDriver should be offered, but the list shows ${assign.visibleDrivers()}")
            assign.choose(otherDriver)
            assertEquals("CONFIRM", assign.confirmText())
            assign.confirm()

            step("Then the ride leaves the Assigned tab and the other driver has it")
            assertTrue(requests.waitUntilShown().open(RequestsTab.ASSIGNED).emptyMessageText(Duration.ofSeconds(30)) != null, "The Assigned tab should be empty")
            val theirs = DriverApiClient.login(secondConfig!!).myActiveRides().map { it.name }
            assertTrue(theirs.any { it.equals(ride.riderName, ignoreCase = true) }, "The second driver should have '${ride.riderName}', has $theirs")
        } finally {
            // The ride may now sit with the second driver, so it is cancelled from that account (only the ride this test created).
            runCatching {
                val client = DriverApiClient.login(secondConfig!!)
                client.myActiveRides().filter { it.name.equals(ride.riderName, ignoreCase = true) }.forEach { client.cancelRide(it.id, "E2E teardown") }
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
