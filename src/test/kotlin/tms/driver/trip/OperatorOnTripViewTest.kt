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
import tms.driver.backend.DispatchAction
import tms.driver.backend.DriverApiClient
import tms.driver.backend.DriverApiConfig
import tms.driver.base.DriverExtension
import tms.driver.core.config.Credentials
import tms.driver.core.driver.DriverSession
import tms.driver.fixtures.RideFixture
import tms.driver.fixtures.RideSpec
import tms.driver.fixtures.TestRide
import tms.driver.fixtures.TripFixtureExtension
import tms.driver.flows.LoginFlow
import tms.driver.screens.home.OdHomeScreen
import tms.driver.screens.trip.ConfirmShuttleDialog
import tms.driver.screens.trip.TripViewScreen
import java.time.Duration

/**
 * Phase T12a (DISP-001, 003 and 004 of docs/additional-coverage-plan.md): while ride A's trip runs and the driver is on the trip view, the
 * second driver cancels, completes or moves ride B (not started yet, so the server allows it). The app's code has "Dispatch canceled /
 * completed / has changed for:" sheets for socket alerts, but they did NOT appear for any of these three actions on staging: the trip view
 * just refreshes and drops ride B's stop. These tests pin that behaviour and fail if the sheets start to appear (then they are the scenarios).
 * A: Terminal 5E to Door 3 (3 guests). B: Terminal 5E to Mannheim Rd (2 guests), so the route is Terminal 5E, Mannheim Rd, Door 3.
 */
@Tag("trip")
@Tag("operator")
@Tag("device")
@EnabledIfEnvironmentVariable(named = "TMS_TRIP_API_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TMS_RIDER_API_URL", matches = ".+")
@ExtendWith(DriverExtension::class, TripFixtureExtension::class)
@DisplayName("Operator changes ride B while the driver is on the trip view")
class OperatorOnTripViewTest {
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

    /** Ride A runs (started in the app) and ride B has been handed to the QA driver; returns the trip view and the operator. */
    private fun runningTripWithRideB(rides: RideFixture, seed: Long): Triple<TripViewScreen, TestRide, DriverApiClient> {
        rides.assignedRide(RideSpec(partySize = 3, ada = false, note = "First", seed = seed, pickupName = pickup, dropoffName = last))
        val b = rides.queuedRide(RideSpec(partySize = 2, ada = false, note = "Second", seed = seed + 1, pickupName = pickup, dropoffName = middle))
        val qaId = rides.qaDriverId()
        val operator = DriverApiClient.login(secondConfig)
        LoginFlow(creds).loginToHome()
        val home = OdHomeScreen()
        assertTrue(home.waitForTrip(Duration.ofSeconds(60)))
        home.tapStartTrip()
        ConfirmShuttleDialog().waitUntilShown().confirm()
        val trip = TripViewScreen().waitUntilShown().also { it.waitForEstimatedTimes() }
        assertTrue(operator.assign(b.dispatchId, qaId).ok, "The second driver should be able to hand ride B over")
        Thread.sleep(6_000)
        trip.pullToRefresh()
        Thread.sleep(3_000)
        assertEquals(listOf(pickup, middle, last), trip.stopNames(), "Both rides should be on the route")
        return Triple(trip, b, operator)
    }

    private fun screenTexts(): Set<String> =
        Regex("""\btext="([^"]{2,90})"""").findAll(DriverSession.driver.pageSource).map { it.groupValues[1] }.toSet()

    /** Waits until the route is [expected] and returns the seconds taken plus every text that appeared on the way. */
    private fun waitForRoute(trip: TripViewScreen, expected: List<String>): Pair<Long, Set<String>> {
        val baseline = screenTexts()
        val seen = LinkedHashSet<String>()
        val start = System.nanoTime()
        val end = start + Duration.ofSeconds(60).toNanos()
        while (System.nanoTime() < end) {
            runCatching { seen += screenTexts() - baseline }
            if (runCatching { trip.stopNames() }.getOrNull() == expected) break
            Thread.sleep(700)
        }
        return Duration.ofNanos(System.nanoTime() - start).seconds to seen
    }

    private fun assertNoSheet(seen: Set<String>) {
        val sheet = seen.filter { it.contains("Dispatch", ignoreCase = true) || it.contains("Your new stop", ignoreCase = true) || it.contains("Back To Home", ignoreCase = true) }
        assertTrue(sheet.isEmpty(), "No dispatch sheet was expected, but these texts appeared: $sheet")
    }

    @Test
    @DisplayName("[DISP-001] When the operator cancels ride B, the route drops its stop within seconds and no 'Dispatch canceled' sheet appears")
    fun operatorCancels(rides: RideFixture) {
        step("Given ride A's trip is running and ride B is on the route")
        val (trip, b, operator) = runningTripWithRideB(rides, 241)

        step("When the second driver cancels ride B, then the route is back to $pickup and $last")
        assertTrue(operator.dispatchAction(b.dispatchId, DispatchAction.CANCEL, "E2E operator cancel").ok)
        val (seconds, seen) = waitForRoute(trip, listOf(pickup, last))
        observe("seconds-until-route-updated", seconds.toString())
        assertEquals(listOf(pickup, last), trip.stopNames())
        assertTrue(seconds <= 30, "The route should update within seconds, but took ${seconds}s")

        step("And no sheet told the driver about it")
        assertNoSheet(seen)
        assertTrue(trip.isShown(Duration.ofSeconds(3)))
    }

    @Test
    @DisplayName("[DISP-003] When the operator completes ride B, the route drops its stop and no 'Dispatch completed' sheet appears")
    fun operatorCompletes(rides: RideFixture) {
        step("Given ride A's trip is running and ride B is on the route")
        val (trip, b, operator) = runningTripWithRideB(rides, 243)

        step("When the second driver marks ride B complete, then the route is back to $pickup and $last without a sheet")
        assertTrue(operator.dispatchAction(b.dispatchId, DispatchAction.COMPLETE, "E2E operator complete").ok)
        val (seconds, seen) = waitForRoute(trip, listOf(pickup, last))
        observe("seconds-until-route-updated", seconds.toString())
        assertEquals(listOf(pickup, last), trip.stopNames())
        assertNoSheet(seen)
    }

    @Test
    @DisplayName("[DISP-004] When the operator moves ride B to another driver, the route drops its stop and no 'Dispatch has changed' sheet appears")
    fun operatorMovesRide(rides: RideFixture) {
        step("Given ride A's trip is running and ride B is on the route")
        val (trip, b, operator) = runningTripWithRideB(rides, 245)
        try {
            step("When the second driver takes ride B over with change-driver, then the route is back to $pickup and $last without a sheet")
            val moved = operator.changeDriver(b.dispatchId, operator.driverId())
            assertTrue(moved.ok, "The change of driver should work: $moved")
            val (seconds, seen) = waitForRoute(trip, listOf(pickup, last))
            observe("seconds-until-route-updated", seconds.toString())
            assertEquals(listOf(pickup, last), trip.stopNames())
            assertNoSheet(seen)
        } finally {
            // Ride B now belongs to the second driver: end it there (only the ride this test created).
            runCatching { operator.myActiveRides().filter { it.name.equals(b.riderName, ignoreCase = true) }.forEach { operator.cancelRide(it.id, "E2E teardown") } }
                .onFailure { LoggerFactory.getLogger("cleanup").warn("Could not clean the second driver: {}", it.message) }
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
