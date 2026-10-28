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
import tms.driver.fixtures.TestRide
import tms.driver.fixtures.TripFixtureExtension
import tms.driver.flows.LoginFlow
import tms.driver.screens.home.OdHomeScreen
import tms.driver.screens.requests.RideRequestsScreen
import tms.driver.screens.trip.ArrivedScreen
import tms.driver.screens.trip.ConfirmShuttleDialog
import tms.driver.screens.trip.GoBackDialog
import tms.driver.screens.trip.ReviewRequestsSheet
import tms.driver.screens.trip.TripViewScreen
import java.time.Duration

/**
 * Phase T4: the arrived screen, picking up or marking a no-show, the next stop and completing the trip
 * (ARR-001 to ARR-010, 013, 014 and 017 to 019 of docs/trip-lifecycle-test-plan.md). Each test assigns its ride before the app
 * login; the fixture cleans up afterwards, including after a completed trip.
 *
 * Facts these tests rely on (all seen on the staging build): while rides are waiting in the shared queue, GO TO NEXT STOP
 * and the completed trip are followed by a "New Ride Request" sheet; marking the only rider a no-show completes the trip
 * straight away; and this build has no seat +/- buttons (so the plan's seat-counter limit scenario does not apply).
 * Every ride sends an SMS to the rider phone in the template.
 */
@Tag("trip")
@Tag("device")
@EnabledIfEnvironmentVariable(named = "TMS_TRIP_API_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TMS_RIDER_API_URL", matches = ".+")
@ExtendWith(DriverExtension::class, TripFixtureExtension::class)
@DisplayName("Driver picks up riders, moves to the next stop and completes the trip")
class ReachDestinationTest {
    private lateinit var creds: Credentials
    private val pickup = "Terminal 5E"
    private val dropoff = "Door 3 - Bus/Shuttle Ctr"

    @BeforeEach
    fun requireCredentials() {
        val c = Credentials.fromEnvironment()
        assumeTrue(c != null, "TMS_DRIVER_PHONE / TMS_DRIVER_PASSCODE are not set, so this test is skipped")
        creds = c!!
    }

    private fun spec(party: Int, ada: Boolean, seed: Long, note: String? = null) =
        RideSpec(partySize = party, ada = ada, note = note, seed = seed, pickupName = pickup, dropoffName = dropoff)

    /** Logs in, starts the trip and taps I've Arrived at the pickup. */
    private fun startTripAndArrive(): Pair<TripViewScreen, ArrivedScreen> {
        LoginFlow(creds).loginToHome()
        val home = OdHomeScreen()
        assertTrue(home.waitForTrip(Duration.ofSeconds(60)), "A trip should be shown after logging in")
        home.tapStartTrip()
        val dialog = ConfirmShuttleDialog()
        if (dialog.isShown(Duration.ofSeconds(8))) dialog.confirm()
        val trip = TripViewScreen().waitUntilShown()
        trip.waitForEstimatedTimes()
        return trip to trip.tapArrived()
    }

    private fun assertReviewSheet(sheet: ReviewRequestsSheet, withCount: Boolean) {
        assertEquals("New Ride Request", sheet.titleText())
        // The "N new" count is on the sheet shown mid-trip, not on the one that follows the completed trip.
        assertEquals(withCount, sheet.hasCount(), "Whether the sheet shows the count of waiting rides")
        if (withCount) assertTrue(Regex("\\d+ new").matches(sheet.countText()), "Count text was '${sheet.countText()}'")
        assertEquals("Would you like to review and take action now?", sheet.questionText())
        assertEquals("LATER", sheet.laterText())
        assertEquals("REVIEW", sheet.reviewText())
    }

    @Test
    @DisplayName("[ARR-001 / 002 / 003 / 004 / 005 / 006 / 008 / 009 / 017] A driver picks the rider up, moves to the drop-off and completes the trip")
    fun pickUpAndCompleteTheTrip(rides: RideFixture) {
        step("Given a ride from $pickup to $dropoff for 5 guests with ADA is assigned, and the driver has started the trip and tapped I've Arrived")
        val ride = rides.assignedRide(spec(party = 5, ada = true, seed = 51, note = "Hello"))
        val (trip, arrived) = startTripAndArrive()

        step("Then the screen reads 'Arrived At $pickup' with the rider and 5 guests in PICK-UPS, no drop-offs, no seats taken, and GO TO NEXT STOP switched off")
        assertEquals(pickup, arrived.stopName())
        assertEquals("Arrived At", arrived.subtitleText())
        assertEquals(1, arrived.pickupChips().size)
        assertEquals(5, arrived.pickupChips()[0].partySize)
        assertTrue(arrived.pickupChips()[0].name.trimEnd('.').let { it.length >= 12 && ride.riderName.startsWith(it, ignoreCase = true) })
        assertTrue(arrived.noDropoffsMessageShown(), "'No Drop-offs at this stop' should be shown")
        assertEquals("0", arrived.seatsOccupied())
        assertEquals("/ 15", arrived.seatsTotal())
        assertEquals("GO TO NEXT STOP", arrived.proceedText())
        assertFalse(arrived.isProceedEnabled(), "Nothing is decided yet, so GO TO NEXT STOP should be off")

        step("When the driver ticks PICKED UP, then 5 seats are occupied and GO TO NEXT STOP switches on")
        arrived.tapPickedUp()
        assertEquals("5", arrived.seatsOccupied())
        assertTrue(arrived.isProceedEnabled())

        step("And ticking it again clears the choice: 0 seats and GO TO NEXT STOP off again")
        arrived.tapPickedUp()
        assertEquals("0", arrived.seatsOccupied())
        assertFalse(arrived.isProceedEnabled())

        step("And marking NO SHOW switches GO TO NEXT STOP on with no seats taken, and marking it again clears it")
        arrived.tapNoShow()
        assertEquals("0", arrived.seatsOccupied())
        assertTrue(arrived.isProceedEnabled())
        arrived.tapNoShow()
        assertFalse(arrived.isProceedEnabled())

        step("And changing from PICKED UP to NO SHOW takes the seats back, and changing back puts them on again")
        arrived.tapPickedUp(); assertEquals("5", arrived.seatsOccupied())
        arrived.tapNoShow(); assertEquals("0", arrived.seatsOccupied()); assertTrue(arrived.isProceedEnabled())
        arrived.tapPickedUp(); assertEquals("5", arrived.seatsOccupied())

        step("When the driver taps BACK after deciding, then 'Go back?' warns that pick-ups and drop-offs will not be processed")
        arrived.tapBack()
        val goBack = GoBackDialog().waitUntilShown()
        assertEquals("Go back?", goBack.titleText())
        assertEquals("Pick-ups and Drop-offs will not be processed.", goBack.messageText().trim())
        assertEquals("GO BACK", goBack.goBackText())
        assertEquals("STAY ON PAGE", goBack.stayText())

        step("And STAY ON PAGE keeps the screen and the 5 seats")
        goBack.stay()
        assertTrue(arrived.isShown())
        assertEquals("5", arrived.seatsOccupied())

        step("And the rider's details open from this screen without a PICK UP button")
        val sheet = arrived.tapRider()
        assertEquals(ride.riderName, sheet.riderName())
        assertEquals("5", sheet.partySize())
        assertFalse(sheet.isActionShown(), "PICK UP is offered from the trip view, not from the arrived screen")
        sheet.close()

        step("When the driver taps BACK and chooses GO BACK, then the trip view is shown and tapping I've Arrived again finds nothing decided")
        arrived.tapBack()
        GoBackDialog().waitUntilShown().goBack()
        assertTrue(trip.isShown(Duration.ofSeconds(20)), "GO BACK should return to the trip view")
        val again = trip.tapArrived()
        assertEquals("0", again.seatsOccupied())
        assertFalse(again.isProceedEnabled(), "The discarded decision must not be remembered")

        step("When the driver ticks PICKED UP and taps GO TO NEXT STOP, then the 'New Ride Request' sheet asks to review the waiting rides")
        again.tapPickedUp()
        again.tapProceed()
        val review = ReviewRequestsSheet().waitUntilShown()
        assertReviewSheet(review, withCount = true)
        assertEquals(null, review.tripCompletedMessage(), "The trip is not finished yet")

        step("And LATER shows the trip view for the drop-off")
        review.later()
        val toDropoff = TripViewScreen().waitUntilShown()
        assertEquals(dropoff, toDropoff.currentStopName())
        assertEquals(1, toDropoff.stopNames().size, "Only the drop-off is left")

        step("When the driver arrives at the drop-off, then the rider is listed to drop off, there is nobody to pick up, and the button reads COMPLETE TRIP")
        val atDropoff = toDropoff.tapArrived()
        assertEquals(dropoff, atDropoff.stopName())
        assertTrue(atDropoff.noPickupsMessageShown(), "'No Pick-ups at this stop' should be shown")
        assertEquals(1, atDropoff.dropoffChips().size)
        assertEquals(5, atDropoff.dropoffChips()[0].partySize)
        assertEquals("COMPLETE TRIP", atDropoff.proceedText())
        assertTrue(atDropoff.isProceedEnabled(), "With only drop-offs the button is on straight away")

        step("And COMPLETE TRIP finishes the trip: 'Trip completed. Great Job!' with the new-request question")
        atDropoff.tapProceed()
        val done = ReviewRequestsSheet().waitUntilShown()
        assertEquals("Trip completed. Great Job!", done.tripCompletedMessage())
        assertReviewSheet(done, withCount = false)

        step("And LATER returns Home, which shows no trips")
        done.later()
        val home = OdHomeScreen()
        assertTrue(home.waitForEmptyState(Duration.ofSeconds(40)), "Home should show the empty state after the trip")
    }

    @Test
    @DisplayName("[ARR-007 / ARR-019] Marking the only rider a no-show completes the trip, and REVIEW opens the Ride Requests screen")
    fun noShowCompletesTheTrip(rides: RideFixture) {
        step("Given a ride is assigned, and the driver has started the trip and tapped I've Arrived")
        rides.assignedRide(spec(party = 5, ada = false, seed = 52))
        val (_, arrived) = startTripAndArrive()

        step("When the driver marks the rider NO SHOW, then no seats are taken and GO TO NEXT STOP switches on")
        arrived.tapNoShow()
        assertEquals("0", arrived.seatsOccupied())
        assertTrue(arrived.isProceedEnabled())

        step("And GO TO NEXT STOP completes the trip at once, because nobody is on board for a drop-off")
        arrived.tapProceed()
        val done = ReviewRequestsSheet().waitUntilShown()
        assertEquals("Trip completed. Great Job!", done.tripCompletedMessage())
        assertReviewSheet(done, withCount = false)

        step("When the driver chooses REVIEW, then the Ride Requests screen opens")
        done.review()
        val requests = RideRequestsScreen().waitUntilShown()
        assertEquals("Ride Requests", requests.titleText())
        assertTrue(requests.hasAssignedTab())

        step("And the Home button returns to a Home with no trips")
        requests.goHome()
        assertTrue(OdHomeScreen().waitForEmptyState(Duration.ofSeconds(40)))
    }

    @Test
    @DisplayName("[ARR-014] GO TO NEXT STOP while offline keeps the screen and the choice, and works once the network is back")
    fun proceedWhileOffline(rides: RideFixture) {
        step("Given a ride is assigned, the driver is at the pickup and has ticked PICKED UP")
        rides.assignedRide(spec(party = 5, ada = false, seed = 53))
        val (_, arrived) = startTripAndArrive()
        arrived.tapPickedUp()
        assertEquals("5", arrived.seatsOccupied())
        val adb = Adb()

        try {
            step("When the device goes offline and the driver taps GO TO NEXT STOP, then after the retries the driver is still on the screen with the choice kept")
            assertTrue(adb.setOffline(true), "Could not switch the device offline")
            Thread.sleep(3_000)
            Toasts.mark(DriverSession.driver)
            arrived.tapProceed()
            // Offline, the app shows a spinner over the screen while it retries (up to about a minute), then gives up.
            val started = System.nanoTime()
            arrived.waitForLoaderToFinish(Duration.ofSeconds(100))
            Allure.addAttachment("seconds-spent-retrying-offline", "text/plain", ((System.nanoTime() - started) / 1_000_000_000).toString())
            Allure.addAttachment("message-shown-offline", "text/plain", Toasts.read(DriverSession.driver, Duration.ofSeconds(5)) ?: "(no message captured)")
            assertTrue(arrived.isShown(Duration.ofSeconds(10)), "The arrived screen should still be shown")
            assertEquals("5", arrived.seatsOccupied())
            assertTrue(arrived.isProceedEnabled())
            assertFalse(ReviewRequestsSheet().isShown(Duration.ofSeconds(2)), "The trip must not have moved on")
        } finally {
            adb.setOffline(false)
        }

        step("When the network is back and the driver taps GO TO NEXT STOP again, then the trip moves on to the drop-off")
        Thread.sleep(6_000)
        arrived.tapProceed()
        val review = ReviewRequestsSheet().waitUntilShown()
        review.later()
        assertEquals(dropoff, TripViewScreen().waitUntilShown().currentStopName())
    }

    @Test
    @DisplayName("[ARR-013] At the last stop the trip finishes by itself after about 10 seconds if the driver does nothing")
    fun lastStopFinishesByItself(rides: RideFixture) {
        step("Given a ride is assigned, the rider has been picked up and the driver has arrived at the drop-off")
        rides.assignedRide(spec(party = 5, ada = false, seed = 54))
        val (_, arrived) = startTripAndArrive()
        arrived.tapPickedUp(); arrived.tapProceed()
        ReviewRequestsSheet().waitUntilShown().later()
        val atDropoff = TripViewScreen().waitUntilShown().tapArrived()
        assertEquals("COMPLETE TRIP", atDropoff.proceedText())
        val arrivedAt = System.nanoTime()

        step("When the driver does nothing, then the trip is not finished straight away")
        assertFalse(ReviewRequestsSheet().isShown(Duration.ofSeconds(3)), "The trip should not finish within the first few seconds")

        step("And it finishes by itself: 'Trip completed. Great Job!' appears without a tap")
        val done = ReviewRequestsSheet().waitUntilShown(Duration.ofSeconds(40))
        val seconds = (System.nanoTime() - arrivedAt) / 1_000_000_000
        Allure.addAttachment("seconds-until-the-trip-finished-by-itself", "text/plain", seconds.toString())
        assertEquals("Trip completed. Great Job!", done.tripCompletedMessage())
        assertTrue(seconds in 5..35, "The trip finished after $seconds seconds; about 10 were expected")
        done.later()
        assertTrue(OdHomeScreen().waitForEmptyState(Duration.ofSeconds(40)))
    }

    @Test
    @DisplayName("[ARR-018] After a completed trip a new ride starts fresh, with no seats left over from the last one")
    fun secondTripStartsFresh(rides: RideFixture) {
        step("Given the first ride is completed through a no-show")
        rides.assignedRide(spec(party = 5, ada = false, seed = 55))
        val (_, first) = startTripAndArrive()
        first.tapNoShow(); first.tapProceed()
        ReviewRequestsSheet().waitUntilShown().later()
        assertTrue(OdHomeScreen().waitForEmptyState(Duration.ofSeconds(40)))

        step("When a second ride is assigned and the driver logs in again, starts the trip and arrives")
        val second: TestRide = rides.assignedRide(spec(party = 3, ada = false, seed = 56))
        val (_, arrived) = startTripAndArrive()

        step("Then the second trip is for the new rider with 3 guests, no seats taken and GO TO NEXT STOP off")
        assertEquals(pickup, arrived.stopName())
        assertEquals(3, arrived.pickupChips()[0].partySize)
        assertEquals("0", arrived.seatsOccupied())
        assertFalse(arrived.isProceedEnabled())
        val sheet = arrived.tapRider()
        assertEquals(second.riderName, sheet.riderName())
        sheet.close()
    }

    private fun step(description: String) {
        Allure.step(description)
        LoggerFactory.getLogger("steps").info("STEP: {}", description)
    }
}
