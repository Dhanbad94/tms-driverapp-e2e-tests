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
import tms.driver.fixtures.TestRide
import tms.driver.fixtures.TripFixtureExtension
import tms.driver.flows.LoginFlow
import tms.driver.screens.home.OdHomeScreen
import tms.driver.screens.requests.AssignmentBar
import tms.driver.screens.requests.RequestsTab
import tms.driver.screens.requests.RideRequestsScreen
import java.time.Duration

/**
 * Phase T7, part 1: things that happen to the driver's ride while the app is open (RT-001, 003 to 006, INT-009 to 011 and REQ-004 of
 * docs/trip-lifecycle-test-plan.md). The operator is the SECOND staging driver, who may cancel, complete and reassign a ride
 * of the QA driver through the Driver API, but ONLY while the trip has not started. Confirmed live: once the trip has started, the
 * second driver's calls AND the rider's cancel are all refused with 400, so only the trip's own driver can end it; operator and
 * rider events during a running trip therefore cannot be produced with the accounts we have. The QA driver's own API account cannot be the operator: its API login would
 * sign the app out. Every test records the texts that appear on screen while the app reacts.
 */
@Tag("trip")
@Tag("device")
@EnabledIfEnvironmentVariable(named = "TMS_TRIP_API_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TMS_RIDER_API_URL", matches = ".+")
@ExtendWith(DriverExtension::class, TripFixtureExtension::class)
@DisplayName("Driver app reacts to operator actions")
class OperatorEventsTest {
    private lateinit var creds: Credentials
    private lateinit var operator: DriverApiClient
    private val pickup = "Terminal 5E"
    private val dropoff = "Door 3 - Bus/Shuttle Ctr"

    @BeforeEach
    fun requireCredentials() {
        val c = Credentials.fromEnvironment()
        assumeTrue(c != null, "TMS_DRIVER_PHONE / TMS_DRIVER_PASSCODE are not set, so this test is skipped")
        creds = c!!
        val second = DriverApiConfig.forSecondDriverFromEnvironment()
        assumeTrue(second != null, "TMS_DRIVER2_PHONE / TMS_DRIVER2_PASSCODE are not set, so this test is skipped")
        operator = DriverApiClient.login(second!!)
    }

    private fun spec(seed: Long) = RideSpec(partySize = 5, ada = true, note = "Hello", seed = seed, pickupName = pickup, dropoffName = dropoff)

    /** All texts currently on screen. */
    private fun screenTexts(): Set<String> =
        Regex("""\btext="([^"]{2,80})"""").findAll(DriverSession.driver.pageSource).map { it.groupValues[1] }.toSet()

    /**
     * Waits until [done] holds, remembering every text that appeared on the way (the notice sheets the app shows).
     * Returns the new texts and how many seconds it took.
     */
    private fun waitWatchingTexts(timeout: Duration, done: () -> Boolean): Pair<Set<String>, Long> {
        val baseline = runCatching { screenTexts() }.getOrDefault(emptySet())
        val seen = LinkedHashSet<String>()
        val start = System.nanoTime()
        val end = start + timeout.toNanos()
        while (System.nanoTime() < end) {
            runCatching { seen += screenTexts() - baseline }
            if (runCatching(done).getOrDefault(false)) break
            Thread.sleep(700)
        }
        val seconds = Duration.ofNanos(System.nanoTime() - start).seconds
        return seen to seconds
    }

    private fun cancelledByOperatorRideList(ride: TestRide) = operator.myActiveRides().filter { it.name.equals(ride.riderName, ignoreCase = true) }

    @Test
    @DisplayName("[RT-001] When an operator cancels the ride while the driver is on Home, the ride disappears and Home shows no trip")
    fun cancelOnHome(rides: RideFixture) {
        step("Given a ride is assigned and the driver is on Home looking at it")
        val ride = rides.assignedRide(spec(131))
        LoginFlow(creds).loginToHome()
        val home = OdHomeScreen()
        assertTrue(home.waitForTrip(Duration.ofSeconds(60)))

        step("When the operator cancels the ride, then Home goes back to 'no trips' without the driver doing anything")
        val result = operator.dispatchAction(ride.dispatchId, DispatchAction.CANCEL, "E2E operator cancel")
        assertTrue(result.ok, "The operator's cancel should work: $result")
        val (texts, seconds) = waitWatchingTexts(Duration.ofSeconds(60)) { home.isEmptyStateShown() }
        observe("rt001-seconds-until-home-empty", seconds.toString())
        observe("rt001-texts-seen", texts.joinToString(" | "))
        assertTrue(home.waitForEmptyState(Duration.ofSeconds(5)), "Home should show the empty state")
    }

    @Test
    @DisplayName("[RT-004] When an operator completes the ride while the driver is on Home, Home goes back to 'no trips'")
    fun completeOnHome(rides: RideFixture) {
        step("Given a ride is assigned and the driver is on Home looking at it")
        val ride = rides.assignedRide(spec(133))
        LoginFlow(creds).loginToHome()
        val home = OdHomeScreen()
        assertTrue(home.waitForTrip(Duration.ofSeconds(60)))

        step("When the operator marks the ride complete, then Home shows no trip without the driver doing anything")
        assertTrue(operator.dispatchAction(ride.dispatchId, DispatchAction.COMPLETE, "E2E operator complete").ok, "The operator's complete should work")
        val (texts, seconds) = waitWatchingTexts(Duration.ofSeconds(60)) { home.isEmptyStateShown() }
        observe("rt004-seconds-until-home-empty", seconds.toString())
        observe("rt004-texts-seen", texts.joinToString(" | "))
        assertTrue(home.waitForEmptyState(Duration.ofSeconds(5)), "Home should show the empty state")
    }

    @Test
    @DisplayName("[RT-005] When an operator moves the ride to another driver while the driver is on Home, Home goes back to 'no trips'")
    fun reassignOnHome(rides: RideFixture) {
        step("Given a ride is assigned and the driver is on Home looking at it")
        val ride = rides.assignedRide(spec(134))
        LoginFlow(creds).loginToHome()
        val home = OdHomeScreen()
        assertTrue(home.waitForTrip(Duration.ofSeconds(60)))

        step("When the operator moves the ride to the second driver, then the QA driver's Home shows no trip")
        try {
            val result = operator.changeDriver(ride.dispatchId, operator.driverId())
            assertTrue(result.ok, "The operator's change of driver should work: $result")
            val (texts, seconds) = waitWatchingTexts(Duration.ofSeconds(60)) { home.isEmptyStateShown() }
            observe("rt005-seconds-until-home-empty", seconds.toString())
            observe("rt005-texts-seen", texts.joinToString(" | "))
            assertTrue(home.waitForEmptyState(Duration.ofSeconds(5)), "Home should show the empty state")
        } finally {
            // The ride now belongs to the second driver: end it there.
            runCatching { cancelledByOperatorRideList(ride).forEach { operator.cancelRide(it.id, "E2E teardown") } }
        }
    }

    @Test
    @DisplayName("[RT-003 / REQ-004] When the rider cancels an assigned ride, Home goes back to 'no trips' and the ride shows as cancelled by the guest")
    fun riderCancelsAssignedRide(rides: RideFixture) {
        step("Given a ride is assigned and the driver is on Home looking at it")
        val ride = rides.assignedRide(spec(132))
        LoginFlow(creds).loginToHome()
        val home = OdHomeScreen()
        assertTrue(home.waitForTrip(Duration.ofSeconds(60)))

        step("When the rider cancels the ride, then Home shows no trip without the driver doing anything")
        rides.riderCancels(ride)
        val (texts, seconds) = waitWatchingTexts(Duration.ofSeconds(60)) { home.isEmptyStateShown() }
        observe("rt003-seconds-until-home-empty", seconds.toString())
        observe("rt003-texts-seen", texts.joinToString(" | "))
        assertTrue(home.waitForEmptyState(Duration.ofSeconds(5)), "Home should show the empty state")

        step("And the Completed tab lists the ride as cancelled by the guest")
        val requests = RideRequestsScreen().also { it.openFromHome() }
        val card = requests.open(RequestsTab.COMPLETED).card(ride.riderName, Duration.ofSeconds(30)) ?: throw AssertionError("The ride should be under Completed")
        observe("rt003-completed-status", card.statusText().orEmpty())
        assertTrue(Regex("Canceled By Guest \\(at \\d{2}:\\d{2} [AP]M\\)").matches(card.statusText().orEmpty()), "Status was '${card.statusText()}'")
    }

    @Test
    @DisplayName("[INT-015] Confirming a ride that another driver has just taken does not assign it to the QA driver")
    fun rideTakenByAnotherDriverFirst(rides: RideFixture) {
        step("Given a ride is waiting and the driver has staged it with ASSIGN TO SELF")
        val ride = rides.queuedRide(spec(138))
        LoginFlow(creds).loginToHome()
        OdHomeScreen().waitForEmptyState(Duration.ofSeconds(40))
        val requests = RideRequestsScreen().also { it.openFromHome(); it.openQueue() }
        requests.card(ride.riderName, Duration.ofSeconds(30))!!.tapAssignToSelf()
        val bar = AssignmentBar().waitUntilShown()

        step("When the second driver takes the ride first and the QA driver then taps CONFIRM")
        try {
            val taken = operator.assign(ride.dispatchId, operator.driverId())
            assertTrue(taken.ok, "The second driver should be able to take the ride: $taken")
            val (shown, _) = waitWatchingTexts(Duration.ofSeconds(8)) { false }
            observe("int015-texts-after-take", shown.joinToString(" | "))
            if (bar.isShown(Duration.ofSeconds(2))) runCatching { bar.confirm() }
            val (afterConfirm, _) = waitWatchingTexts(Duration.ofSeconds(10)) { false }
            observe("int015-texts-after-confirm", afterConfirm.joinToString(" | "))

            step("Then no trip opens for the QA driver, and the Assigned tab does not hold the ride")
            assertTrue(!tms.driver.screens.trip.TripViewScreen().isShown(Duration.ofSeconds(3)), "The trip view must not open")
        } finally {
            runCatching { cancelledByOperatorRideList(ride).forEach { operator.cancelRide(it.id, "E2E teardown") } }
        }
    }

    @Test
    @DisplayName("[INT-016] Confirming a ride that the rider has just cancelled does not assign it and does not crash the app")
    fun rideCancelledByRiderFirst(rides: RideFixture) {
        step("Given a ride is waiting and the driver has staged it with ASSIGN TO SELF")
        val ride = rides.queuedRide(spec(139))
        LoginFlow(creds).loginToHome()
        val home = OdHomeScreen()
        home.waitForEmptyState(Duration.ofSeconds(40))
        val requests = RideRequestsScreen().also { it.openFromHome(); it.openQueue() }
        requests.card(ride.riderName, Duration.ofSeconds(30))!!.tapAssignToSelf()
        val bar = AssignmentBar().waitUntilShown()

        step("When the rider cancels and the QA driver then taps CONFIRM")
        rides.riderCancels(ride)
        val (shown, _) = waitWatchingTexts(Duration.ofSeconds(8)) { false }
        observe("int016-texts-after-cancel", shown.joinToString(" | "))
        if (bar.isShown(Duration.ofSeconds(2))) runCatching { bar.confirm() }
        val (afterConfirm, _) = waitWatchingTexts(Duration.ofSeconds(10)) { false }
        observe("int016-texts-after-confirm", afterConfirm.joinToString(" | "))

        step("Then no trip opens and the app is still usable: Home shows no trip")
        assertTrue(!tms.driver.screens.trip.TripViewScreen().isShown(Duration.ofSeconds(3)), "The trip view must not open")
        runCatching { requests.goHome() }
        assertTrue(home.waitForEmptyState(Duration.ofSeconds(30)), "Home should show no trip")
    }

    @Test
    @DisplayName("[INT-009] A ride that arrives while the driver is on Home raises the Requests badge without a reload")
    fun newRideOnHome(rides: RideFixture) {
        step("Given the driver is on Home with no trip")
        LoginFlow(creds).loginToHome()
        val home = OdHomeScreen()
        assertTrue(home.waitForEmptyState(Duration.ofSeconds(40)))
        val before = home.requestsBadgeCount() ?: 0
        observe("badge-before", before.toString())

        step("When a rider requests a ride, then the Requests badge on Home goes up by itself")
        val ride = rides.liveRide(spec(135))
        try {
            val (_, seconds) = waitWatchingTexts(Duration.ofSeconds(60)) { (home.requestsBadgeCount() ?: 0) > before }
            observe("int009-seconds-until-badge", seconds.toString())
            observe("badge-after", (home.requestsBadgeCount() ?: 0).toString())
            assertTrue((home.requestsBadgeCount() ?: 0) > before, "The badge should go above $before")
            assertTrue(home.newRequestsBannerText().orEmpty().contains("new ride requests", ignoreCase = true), "Banner was '${home.newRequestsBannerText()}'")
        } finally {
            rides.riderCancels(ride)
        }
    }

    @Test
    @DisplayName("[INT-010] A ride that arrives while the driver is on the Requests tab appears in the list without a reload")
    fun newRideOnRequestsTab(rides: RideFixture) {
        step("Given the driver is looking at the Requests tab, newest first")
        LoginFlow(creds).loginToHome()
        OdHomeScreen().waitForEmptyState(Duration.ofSeconds(40))
        val requests = RideRequestsScreen().also { it.openFromHome(); it.openQueue() }

        step("When a rider requests a ride, then its card appears at the top by itself")
        val ride = rides.liveRide(spec(136))
        try {
            val shown = waitWatchingTexts(Duration.ofSeconds(60)) { requests.hasCard(ride.riderName) }
            observe("int010-seconds-until-card", shown.second.toString())
            assertTrue(requests.hasCard(ride.riderName), "The new ride should be listed without pulling to refresh")
        } finally {
            rides.riderCancels(ride)
        }
    }

    @Test
    @DisplayName("[INT-011 / REQ-004] When the rider cancels a waiting ride it leaves the Requests tab and shows as cancelled by the guest")
    fun riderCancelsWaitingRide(rides: RideFixture) {
        step("Given a ride is waiting and the driver is looking at it on the Requests tab")
        val ride = rides.queuedRide(spec(137))
        LoginFlow(creds).loginToHome()
        OdHomeScreen().waitForEmptyState(Duration.ofSeconds(40))
        val requests = RideRequestsScreen().also { it.openFromHome(); it.openQueue() }
        assertTrue(requests.card(ride.riderName, Duration.ofSeconds(30)) != null, "The ride should be listed")

        step("When the rider cancels it, then its card disappears from the list")
        rides.riderCancels(ride)
        val (_, seconds) = waitWatchingTexts(Duration.ofSeconds(60)) { !requests.hasCard(ride.riderName) }
        observe("int011-seconds-until-card-gone", seconds.toString())
        assertTrue(!requests.hasCard(ride.riderName), "The cancelled ride should leave the list")

        step("And the Completed tab lists it as cancelled by the guest")
        val card = requests.open(RequestsTab.COMPLETED).card(ride.riderName, Duration.ofSeconds(30)) ?: throw AssertionError("The ride should be under Completed")
        observe("int011-completed-status", card.statusText().orEmpty())
        assertTrue(card.statusText().orEmpty().startsWith("Canceled By Guest"), "Status was '${card.statusText()}'")
    }

    @Test
    @DisplayName("[RT-006] When the QA driver signs in somewhere else, the app is signed out and shows the Login screen")
    fun signedInElsewhere(rides: RideFixture) {
        val qaConfig = DriverApiConfig.fromEnvironment()
        assumeTrue(qaConfig != null, "Driver API settings for the QA driver are not set, so this test is skipped")
        step("Given the driver is signed in and looking at Home")
        LoginFlow(creds).loginToHome()
        val home = OdHomeScreen()
        assertTrue(home.waitForEmptyState(Duration.ofSeconds(40)))

        step("When the same driver signs in through the API (another device), then the app returns to the Login screen")
        DriverApiClient.login(qaConfig!!)
        val login = tms.driver.screens.auth.LoginScreen()
        val (texts, seconds) = waitWatchingTexts(Duration.ofSeconds(90)) {
            runCatching { home.pullToRefresh() }
            login.isShown(Duration.ofSeconds(1))
        }
        observe("rt006-seconds-until-login", seconds.toString())
        observe("rt006-texts-seen", texts.joinToString(" | "))
        assertTrue(login.isShown(Duration.ofSeconds(5)), "The Login screen should be shown")
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
