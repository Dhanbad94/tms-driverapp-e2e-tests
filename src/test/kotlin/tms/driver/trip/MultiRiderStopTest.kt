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
import tms.driver.screens.trip.ArrivedScreen
import tms.driver.screens.trip.ConfirmShuttleDialog
import tms.driver.screens.trip.ReviewRequestsSheet
import tms.driver.screens.trip.TripViewScreen
import java.time.Duration

/**
 * Phase T7, part 4: two riders on one trip (ARR-012, ARR-015 and ARR-016 of docs/trip-lifecycle-test-plan.md; the seat limit turned out to be enforced when a ride is handed over, not at pick-up).
 * Ride A is assigned and its trip is started IN THE APP; ride B, with the same pickup stop, is waiting in the queue and is then
 * handed to the QA driver by the second driver while the trip runs (the QA driver's own API login would sign the app out).
 * A: Terminal 5E to Door 3. B: Terminal 5E to Mannheim Rd. The route the server builds is Terminal 5E, Mannheim Rd, Door 3.
 */
@Tag("trip")
@Tag("device")
@EnabledIfEnvironmentVariable(named = "TMS_TRIP_API_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TMS_RIDER_API_URL", matches = ".+")
@ExtendWith(DriverExtension::class, TripFixtureExtension::class)
@DisplayName("Driver with two riders at one stop")
class MultiRiderStopTest {
    private lateinit var creds: Credentials
    private lateinit var secondConfig: DriverApiConfig
    private val pickup = "Terminal 5E"
    private val middle = "Mannheim Rd"
    private val last = "Door 3 - Bus/Shuttle Ctr"

    @BeforeEach
    fun requireCredentials() {
        val c = Credentials.fromEnvironment()
        assumeTrue(c != null, "TMS_DRIVER_PHONE / TMS_DRIVER_PASSCODE are not set, so this test is skipped")
        creds = c!!
        val second = DriverApiConfig.forSecondDriverFromEnvironment()
        assumeTrue(second != null, "TMS_DRIVER2_PHONE / TMS_DRIVER2_PASSCODE are not set, so this test is skipped")
        secondConfig = second!!
    }

    /** Starts ride A in the app, then lets the second driver hand ride B to the QA driver; returns the refreshed trip view. */
    private fun twoRidersTrip(rides: RideFixture, partyA: Int, partyB: Int, seed: Long): TripViewScreen =
        twoRidersTripWithResult(rides, partyA, partyB, seed).also { assertTrue(it.second.ok, "The second driver should be able to hand ride B over: ${it.second}") }.first

    /** Same, but returns the result of handing ride B over instead of insisting that it works. */
    private fun twoRidersTripWithResult(rides: RideFixture, partyA: Int, partyB: Int, seed: Long): Pair<TripViewScreen, tms.driver.backend.AssignResult> {
        rides.assignedRide(RideSpec(partySize = partyA, ada = false, note = "First", seed = seed, pickupName = pickup, dropoffName = last))
        val b = rides.queuedRide(RideSpec(partySize = partyB, ada = false, note = "Second", seed = seed + 1, pickupName = pickup, dropoffName = middle))
        val qaId = rides.qaDriverId()
        val operator = DriverApiClient.login(secondConfig)

        LoginFlow(creds).loginToHome()
        val home = OdHomeScreen()
        assertTrue(home.waitForTrip(Duration.ofSeconds(60)), "Home should show ride A")
        home.tapStartTrip()
        ConfirmShuttleDialog().waitUntilShown().confirm()
        val trip = TripViewScreen().waitUntilShown().also { it.waitForEstimatedTimes() }
        assertEquals(listOf(pickup, last), trip.stopNames(), "Before ride B joins there are two stops")

        val assigned = operator.assign(b.dispatchId, qaId)
        Thread.sleep(6_000)
        trip.pullToRefresh()
        Thread.sleep(3_000)
        return trip to assigned
    }

    /** The trip view can still show the stop that was just left for a moment; wait until the next stop is first. */
    private fun waitForCurrentStop(trip: TripViewScreen, stop: String, timeout: Duration = Duration.ofSeconds(40)) {
        val end = System.nanoTime() + timeout.toNanos()
        while (System.nanoTime() < end) {
            if (runCatching { trip.currentStopName() }.getOrNull() == stop) return
            Thread.sleep(1_000)
            runCatching { trip.pullToRefresh() }
        }
    }

    private fun skipReviewSheet() {
        runCatching { ReviewRequestsSheet().waitUntilShown(Duration.ofSeconds(20)).later() }
    }

    @Test
    @DisplayName("[ARR-015 / ARR-016] Two riders at one stop are both listed and counted, and the stops are then visited in order until the trip is completed")
    fun twoRidersAtOneStop(rides: RideFixture) {
        step("Given ride A (3 guests, to $last) is running and ride B (2 guests, to $middle) joins at the same pickup")
        val trip = twoRidersTrip(rides, partyA = 3, partyB = 2, seed = 171)

        step("Then the route is $pickup, $middle, $last and the first card lists both riders")
        assertEquals(listOf(pickup, middle, last), trip.stopNames())
        assertEquals(setOf(3, 2), trip.riderChipsPerStop()[0].map { it.partySize }.toSet())

        step("When the driver arrives, then both riders are listed to pick up, no seats are taken and GO TO NEXT STOP is off")
        val arrived = trip.tapArrived()
        assertEquals(pickup, arrived.stopName())
        assertEquals(2, arrived.pickupChips().size)
        assertEquals(2, arrived.pickedUpBoxCount())
        assertEquals("0", arrived.seatsOccupied())
        assertFalse(arrived.isProceedEnabled())

        step("When the first rider is ticked, then their seats are counted but GO TO NEXT STOP stays off until the other rider is decided too")
        arrived.tapPickedUpAt(0)
        val afterFirst = arrived.seatsOccupied().toInt()
        assertTrue(afterFirst == 3 || afterFirst == 2, "Seats after the first rider: $afterFirst")
        assertFalse(arrived.isProceedEnabled(), "With one rider still undecided the button must stay off")

        step("When the second rider is ticked, then all 5 seats are counted and GO TO NEXT STOP is on")
        arrived.tapPickedUpAt(1)
        assertEquals("5", arrived.seatsOccupied())
        assertTrue(arrived.isProceedEnabled())

        step("When the driver goes to the next stop, then the next stop is $middle, where only ride B's rider gets off")
        arrived.tapProceed()
        skipReviewSheet()
        val toMiddle = TripViewScreen().waitUntilShown()
        waitForCurrentStop(toMiddle, middle)
        assertEquals(middle, toMiddle.currentStopName())
        assertEquals(listOf(middle, last), toMiddle.stopNames())
        val atMiddle = toMiddle.tapArrived()
        assertTrue(atMiddle.noPickupsMessageShown())
        assertEquals(listOf(2), atMiddle.dropoffChips().map { it.partySize })
        assertEquals("GO TO NEXT STOP", atMiddle.proceedText())
        assertTrue(atMiddle.isProceedEnabled())

        step("When the driver goes on, then the last stop is $last, where ride A's rider gets off and the button reads COMPLETE TRIP")
        atMiddle.tapProceed()
        skipReviewSheet()
        val toLast = TripViewScreen().waitUntilShown()
        waitForCurrentStop(toLast, last)
        assertEquals(last, toLast.currentStopName())
        val atLast = toLast.tapArrived()
        assertEquals(listOf(3), atLast.dropoffChips().map { it.partySize })
        assertEquals("COMPLETE TRIP", atLast.proceedText())

        step("And COMPLETE TRIP ends the trip and Home shows no trips")
        atLast.tapProceed()
        val done = ReviewRequestsSheet().waitUntilShown()
        assertEquals("Trip completed. Great Job!", done.tripCompletedMessage())
        done.later()
        assertTrue(OdHomeScreen().waitForEmptyState(Duration.ofSeconds(40)))
    }

    @Test
    @DisplayName("[ARR-012] A second party that would take the shuttle over its 15 seats is refused when it is handed over, and the trip is unchanged")
    fun partiesExceedSeats(rides: RideFixture) {
        step("Given ride A (8 guests) is running and ride B (8 guests, same pickup) is waiting: together 16 guests for a 15-seat shuttle")
        val (trip, assigned) = twoRidersTripWithResult(rides, partyA = 8, partyB = 8, seed = 181)

        step("Then the server refuses to hand ride B to the driver")
        observe("assign-result", assigned.toString())
        assertFalse(assigned.ok, "Handing over a party that exceeds the seats should be refused: $assigned")
        assertEquals(500, assigned.code)

        step("And the trip still has only ride A's two stops, and the arrived screen counts 0 of 15")
        assertEquals(listOf(pickup, last), trip.stopNames())
        val arrived = trip.tapArrived()
        assertEquals(1, arrived.pickupChips().size)
        assertEquals(8, arrived.pickupChips()[0].partySize)
        assertEquals("/ 15", arrived.seatsTotal())
    }

    private fun screenTexts(): Set<String> =
        Regex("""\btext="([^"]{2,90})"""").findAll(DriverSession.driver.pageSource).map { it.groupValues[1] }.toSet()

    private fun observe(name: String, value: String) {
        Allure.addAttachment(name, value)
        LoggerFactory.getLogger("observed").info("OBSERVED {} = {}", name, value)
    }

    private fun step(description: String) {
        Allure.step(description)
        LoggerFactory.getLogger("steps").info("STEP: {}", description)
    }
}
