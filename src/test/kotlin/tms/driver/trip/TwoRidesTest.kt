package tms.driver.trip

import io.qameta.allure.Allure
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import tms.driver.backend.DispatchAction
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
import tms.driver.screens.trip.ConfirmShuttleDialog
import tms.driver.screens.trip.TripViewScreen
import java.time.Duration

/**
 * Phase T7, part 2: two rides on one driver (HOME-004 re-test, HOME-011 and TRIPV-010 of docs/trip-lifecycle-test-plan.md).
 * Staging refuses a second ride while the first is waiting to start, but accepts one once the first trip has started, so the first
 * trip is started through the API before the app login and the second ride is assigned after that.
 * Ride A: Terminal 5E to Door 3 (3 guests). Ride B: Mannheim Rd to Terminal 3 10000 (2 guests).
 */
@Tag("trip")
@Tag("device")
@EnabledIfEnvironmentVariable(named = "TMS_TRIP_API_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TMS_RIDER_API_URL", matches = ".+")
@ExtendWith(DriverExtension::class, TripFixtureExtension::class)
@DisplayName("Driver with two rides")
class TwoRidesTest {
    private lateinit var creds: Credentials

    @BeforeEach
    fun requireCredentials() {
        val c = Credentials.fromEnvironment()
        assumeTrue(c != null, "TMS_DRIVER_PHONE / TMS_DRIVER_PASSCODE are not set, so this test is skipped")
        creds = c!!
    }

    private fun rideA(seed: Long) = RideSpec(partySize = 3, ada = false, note = "First", seed = seed, pickupName = "Terminal 5E", dropoffName = "Door 3 - Bus/Shuttle Ctr")
    private fun rideB(seed: Long) = RideSpec(partySize = 2, ada = false, note = "Second", seed = seed, pickupName = "Mannheim Rd", dropoffName = "Terminal 3 10000")

    private fun openTripView(): TripViewScreen {
        LoginFlow(creds).loginToHome()
        val home = OdHomeScreen()
        assertTrue(home.waitForTrip(Duration.ofSeconds(60)), "Home should show the trip")
        home.tapStartTrip()
        val trip = TripViewScreen()
        if (!trip.isShown(Duration.ofSeconds(8))) ConfirmShuttleDialog().waitUntilShown().confirm()
        return trip.waitUntilShown().also { it.waitForEstimatedTimes() }
    }

    @Test
    @DisplayName("[HOME-004] A second ride can be added once the trip has started; Home then counts the stops of both rides")
    fun secondRideJoinsRunningTrip(rides: RideFixture) {
        step("Given ride A is assigned and its trip has started, and then ride B is assigned")
        rides.assignedRide(rideA(151))
        rides.startTripByApi()
        rides.assignedRide(rideB(152))

        step("When the driver logs in, then Home shows the running trip with 3 stops to go and CONTINUE TRIP")
        LoginFlow(creds).loginToHome()
        val home = OdHomeScreen()
        assertTrue(home.waitForTrip(Duration.ofSeconds(60)))
        assertEquals("Current Trip", home.summaryText())
        assertEquals("3 Stops to go", home.stopsText())
        assertTrue(home.startButtonText().equals("continue trip", ignoreCase = true), "Button was '${home.startButtonText()}'")

        step("And the trip view lists the stops of both rides on one route")
        home.tapStartTrip()
        val trip = TripViewScreen()
        if (!trip.isShown(Duration.ofSeconds(8))) ConfirmShuttleDialog().waitUntilShown().confirm()
        trip.waitUntilShown().waitForEstimatedTimes()
        observe("stops-on-the-route", trip.stopNames().joinToString(" > "))
        assertEquals(3, trip.stopNames().size, "Stops: ${trip.stopNames()}")
        assertTrue(trip.stopNames().containsAll(listOf("Mannheim Rd", "Terminal 3 10000", "Door 3 - Bus/Shuttle Ctr")), "Stops: ${trip.stopNames()}")
    }

    @Test
    @DisplayName("[TRIPV-010] With two rides, 'Go here instead' on a later stop asks for confirmation; CANCEL changes nothing and CONFIRM makes it the next stop")
    fun goHereInstead(rides: RideFixture) {
        step("Given two rides are on the running trip and the driver is looking at the trip view")
        rides.assignedRide(rideA(153))
        rides.startTripByApi()
        rides.assignedRide(rideB(154))
        val trip = openTripView()
        val before = trip.stopNames()
        observe("stops-before", before.joinToString(" > "))
        assertTrue(trip.goHereInsteadCount() >= 1, "A later stop should offer 'Go here instead'")

        step("When the driver taps 'Go here instead' and then CANCEL, then the route is unchanged")
        val dialog = trip.tapGoHereInstead(0)
        assertEquals("CANCEL", dialog.cancelText())
        assertEquals("CONFIRM", dialog.confirmText())
        dialog.cancel()
        assertEquals(before, stopsWhenLoaded(trip))

        step("When the driver taps it again and confirms, then the chosen stop moves to the top of the route")
        val chosen = before.last()
        trip.tapGoHereInstead(0).confirm()
        val after = stopsWhenLoaded(trip)
        observe("stops-after", after.joinToString(" > "))
        assertEquals(chosen, after.first(), "The chosen stop should be next, but the route is $after")
    }

    @Test
    @DisplayName("[HOME-011] When one of two rides is cancelled, Home counts only the stops left")
    fun oneRideCancelled(rides: RideFixture) {
        val secondConfig = DriverApiConfig.forSecondDriverFromEnvironment()
        assumeTrue(secondConfig != null, "TMS_DRIVER2_PHONE / TMS_DRIVER2_PASSCODE are not set, so this test is skipped")
        step("Given two rides are on the running trip and Home shows 3 stops to go")
        rides.assignedRide(rideA(155))
        rides.startTripByApi()
        val b = rides.assignedRide(rideB(156))
        LoginFlow(creds).loginToHome()
        val home = OdHomeScreen()
        assertTrue(home.waitForTrip(Duration.ofSeconds(60)))
        assertEquals("3 Stops to go", home.stopsText())

        step("When ride B is cancelled, then Home counts only ride A's drop-off")
        val operatorResult = DriverApiClient.login(secondConfig!!).dispatchAction(b.dispatchId, DispatchAction.CANCEL, "E2E operator cancel")
        observe("operator-cancel-of-second-ride", operatorResult.toString())
        if (!operatorResult.ok) rides.riderCancels(b)
        val end = System.nanoTime() + Duration.ofSeconds(60).toNanos()
        while (System.nanoTime() < end && home.stopsText() == "3 Stops to go") Thread.sleep(1_000)
        Thread.sleep(1_000)
        observe("home-after-cancel", "${runCatching { home.summaryText() }.getOrNull()} / ${runCatching { home.stopsText() }.getOrNull()}")
        // Ride A's pickup was done when its trip started, so only its drop-off is left.
        assertEquals("1 Stop to go", home.stopsText())
    }

    /** The list is empty for a moment after a dialog closes or the route changes; wait until the stops are back. */
    private fun stopsWhenLoaded(trip: TripViewScreen, timeout: Duration = Duration.ofSeconds(30)): List<String> {
        val end = System.nanoTime() + timeout.toNanos()
        var stops = emptyList<String>()
        while (System.nanoTime() < end) {
            stops = runCatching { trip.stopNames() }.getOrDefault(emptyList())
            if (stops.isNotEmpty()) { Thread.sleep(1_500); return runCatching { trip.stopNames() }.getOrDefault(stops).ifEmpty { stops } }
            Thread.sleep(700)
        }
        return stops
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
