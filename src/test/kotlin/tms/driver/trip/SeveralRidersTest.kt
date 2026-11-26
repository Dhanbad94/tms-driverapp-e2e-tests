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
import tms.driver.fixtures.RideFixture
import tms.driver.fixtures.RideSpec
import tms.driver.fixtures.TripFixtureExtension
import tms.driver.flows.LoginFlow
import tms.driver.screens.home.OdHomeScreen
import tms.driver.screens.trip.ConfirmShuttleDialog
import tms.driver.screens.trip.ReviewRequestsSheet
import tms.driver.screens.trip.TripViewScreen
import java.time.Duration

/**
 * Phase T16 (MULTI-001 to MULTI-003 of docs/next-features-plan.md): several riders assigned BEFORE the trip starts, so no second driver is
 * needed. Pick-up stop for all: Terminal 5E. Drop-offs: Mannheim Rd and Door 3 - Bus/Shuttle Ctr.
 */
@Tag("trip")
@Tag("device")
@EnabledIfEnvironmentVariable(named = "TMS_TRIP_API_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TMS_RIDER_API_URL", matches = ".+")
@ExtendWith(DriverExtension::class, TripFixtureExtension::class)
@DisplayName("Driver with several riders on one trip")
class SeveralRidersTest {
    private lateinit var creds: Credentials
    private val pickup = "Terminal 5E"
    private val middle = "Mannheim Rd"
    private val last = "Door 3 - Bus/Shuttle Ctr"

    @BeforeEach
    fun requireCredentials() {
        val c = Credentials.fromEnvironment()
        assumeTrue(c != null, "TMS_DRIVER_PHONE / TMS_DRIVER_PASSCODE are not set, so this test is skipped")
        creds = c!!
    }

    private fun ride(rides: RideFixture, party: Int, dropoff: String, seed: Long) =
        rides.assignedRide(RideSpec(partySize = party, ada = false, note = "Multi $party", seed = seed, pickupName = pickup, dropoffName = dropoff))

    private fun startTrip(): TripViewScreen {
        LoginFlow(creds).loginToHome()
        val home = OdHomeScreen()
        assertTrue(home.waitForTrip(Duration.ofSeconds(60)), "A trip should be shown after logging in")
        home.tapStartTrip()
        ConfirmShuttleDialog().waitUntilShown().confirm()
        return TripViewScreen().waitUntilShown().also { it.waitForEstimatedTimes() }
    }

    private fun skipReviewSheet() {
        runCatching { ReviewRequestsSheet().waitUntilShown(Duration.ofSeconds(20)).later() }
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

    @Tag("smoke")
    @Test
    @DisplayName("[MULTI-001] Two riders at one stop, one picked up and one a no-show: only the picked-up rider's seats count, GO TO NEXT STOP needs both decided, and the no-show's drop-off is skipped")
    fun pickedUpAndNoShow(rides: RideFixture) {
        step("Given ride A (3 guests, to $last) and ride B (2 guests, to $middle) are assigned and the trip has started")
        ride(rides, 3, last, 301); ride(rides, 2, middle, 302)
        val trip = startTrip()
        assertEquals(listOf(pickup, middle, last), trip.stopNames())

        step("When the driver arrives, then both riders are listed to pick up and GO TO NEXT STOP is off")
        val arrived = trip.tapArrived()
        val sizes = arrived.pickupChips().map { it.partySize }
        assertEquals(setOf(3, 2), sizes.toSet())
        assertFalse(arrived.isProceedEnabled())

        step("When the 3-guest rider is picked up, then 3 seats count but the button stays off while the other rider is undecided")
        arrived.tapPickedUpAt(sizes.indexOf(3))
        assertEquals("3", arrived.seatsOccupied())
        assertFalse(arrived.isProceedEnabled())

        step("When the 2-guest rider is marked NO SHOW, then still only 3 seats count and GO TO NEXT STOP is on")
        arrived.tapNoShowAt(sizes.indexOf(2))
        assertEquals("3", arrived.seatsOccupied())
        assertTrue(arrived.isProceedEnabled())

        step("When the driver goes on, then the no-show's drop-off at $middle is skipped and the only stop left is $last")
        arrived.tapProceed()
        skipReviewSheet()
        val toLast = TripViewScreen().waitUntilShown()
        waitForCurrentStop(toLast, last)
        assertEquals(listOf(last), toLast.stopNames())
        val atLast = toLast.tapArrived()
        assertEquals(listOf(3), atLast.dropoffChips().map { it.partySize })
        assertEquals("COMPLETE TRIP", atLast.proceedText())
    }

    @Test
    @DisplayName("[MULTI-002 / MULTI-003] Three rides on one trip: all are counted at the pick-up, the middle stop lists only its two riders and is passable at once, and the last stop lists the third")
    fun threeRides(rides: RideFixture) {
        step("Given three rides from $pickup are assigned: A (3 guests to $last), B (2 to $middle) and C (2 to $middle)")
        ride(rides, 3, last, 311); ride(rides, 2, middle, 312); ride(rides, 2, middle, 313)
        val trip = startTrip()
        assertEquals(listOf(pickup, middle, last), trip.stopNames())

        step("When the driver arrives, then three riders are listed, and picking them all up counts 7 seats")
        val arrived = trip.tapArrived()
        assertEquals(3, arrived.pickupChips().size)
        assertEquals("0", arrived.seatsOccupied())
        for (i in 0 until 3) arrived.tapPickedUpAt(i)
        assertEquals("7", arrived.seatsOccupied())
        assertTrue(arrived.isProceedEnabled())

        step("When the driver goes on, then the middle stop $middle lists only the two riders who get off there and GO TO NEXT STOP is on at once")
        arrived.tapProceed()
        skipReviewSheet()
        val toMiddle = TripViewScreen().waitUntilShown()
        waitForCurrentStop(toMiddle, middle)
        val atMiddle = toMiddle.tapArrived()
        assertTrue(atMiddle.noPickupsMessageShown())
        assertEquals(listOf(2, 2), atMiddle.dropoffChips().map { it.partySize })
        assertEquals("GO TO NEXT STOP", atMiddle.proceedText())
        assertTrue(atMiddle.isProceedEnabled(), "Drop-offs need no decision")

        step("When the driver goes on, then the last stop lists the 3-guest rider and the trip can be completed")
        atMiddle.tapProceed()
        skipReviewSheet()
        val toLast = TripViewScreen().waitUntilShown()
        waitForCurrentStop(toLast, last)
        val atLast = toLast.tapArrived()
        assertEquals(listOf(3), atLast.dropoffChips().map { it.partySize })
        assertEquals("COMPLETE TRIP", atLast.proceedText())
        atLast.tapProceed()
        val done = ReviewRequestsSheet().waitUntilShown()
        assertEquals("Trip completed. Great Job!", done.tripCompletedMessage())
        done.later()
        assertTrue(OdHomeScreen().waitForEmptyState(Duration.ofSeconds(40)))
    }

    private fun step(description: String) {
        Allure.step(description)
        LoggerFactory.getLogger("steps").info("STEP: {}", description)
    }
}
