package tms.driver.explore

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.extension.ExtendWith
import tms.driver.backend.DispatchAction
import tms.driver.backend.DriverApiClient
import tms.driver.backend.DriverApiConfig
import tms.driver.backend.ActionResult
import tms.driver.base.DriverExtension
import tms.driver.core.config.Credentials
import tms.driver.core.driver.DriverSession
import tms.driver.core.report.Artifacts
import tms.driver.fixtures.RideFixture
import tms.driver.fixtures.RideSpec
import tms.driver.fixtures.TripFixtureExtension
import tms.driver.flows.LoginFlow
import tms.driver.screens.home.OdHomeScreen
import tms.driver.screens.trip.ConfirmShuttleDialog
import tms.driver.screens.trip.TripViewScreen
import java.time.Duration

/**
 * Developer tool (tag "explore"): the sheet the app shows when the second driver cancels ride B while the QA driver is on the trip view.
 * Ride A (Terminal 5E to Door 3) is started in the app, ride B (Mannheim Rd to Terminal 3 10000) is handed over by the second driver.
 */
@Tag("explore")
@Tag("device")
@EnabledIfEnvironmentVariable(named = "TMS_TRIP_API_URL", matches = ".+")
@ExtendWith(DriverExtension::class, TripFixtureExtension::class)
@DisplayName("Explore: dispatch canceled sheet on the trip view")
class ExploreDispatchSheetTest {
    private val d get() = DriverSession.driver
    private val shots by lazy { Artifacts(d, DriverSession.config.artifactsDir.resolve("ExploreDispatchSheet")) }

    private fun texts(): List<String> = Regex("""<[^>]*\btext="([^"]+)"[^>]*>""").findAll(d.pageSource).map { it.groupValues[1].take(60) }.toList().distinct()
    private fun ids(): Set<String> = Regex("""resource-id="[^"]*:id/([^"]+)"""").findAll(d.pageSource).map { it.groupValues[1] }.toSet()

    @Test
    @DisplayName("[EXPLORE-019] Dispatch canceled sheet")
    fun capture(rides: RideFixture) = run(rides, "cancel")

    @Test
    @DisplayName("[EXPLORE-020] Dispatch completed sheet")
    fun captureComplete(rides: RideFixture) = run(rides, "complete")

    @Test
    @DisplayName("[EXPLORE-021] Dispatch changed sheet")
    fun captureChange(rides: RideFixture) = run(rides, "change")

    private fun run(rides: RideFixture, action: String) {
        val creds = Credentials.fromEnvironment(); assumeTrue(creds != null, "QA driver credentials not set")
        val second = DriverApiConfig.forSecondDriverFromEnvironment(); assumeTrue(second != null, "second driver not configured")
        rides.assignedRide(RideSpec(partySize = 3, ada = false, note = "First", seed = 231, pickupName = "Terminal 5E", dropoffName = "Door 3 - Bus/Shuttle Ctr"))
        val b = rides.queuedRide(RideSpec(partySize = 2, ada = false, note = "Second", seed = 232, pickupName = "Terminal 5E", dropoffName = "Mannheim Rd"))
        val qaId = rides.qaDriverId()
        val operator = DriverApiClient.login(second!!)
        LoginFlow(creds!!).loginToHome()
        val home = OdHomeScreen(); home.waitForTrip(Duration.ofSeconds(60))
        home.tapStartTrip(); ConfirmShuttleDialog().waitUntilShown().confirm()
        val trip = TripViewScreen().waitUntilShown().also { it.waitForEstimatedTimes() }
        println("EXPLORE hand-over: ${operator.assign(b.dispatchId, qaId)}")
        Thread.sleep(6_000); trip.pullToRefresh(); Thread.sleep(3_000)
        println("EXPLORE stops-before: ${trip.stopNames()}")
        val baseline = texts().toSet()
        val result = when (action) {
            "complete" -> operator.dispatchAction(b.dispatchId, DispatchAction.COMPLETE, "E2E operator complete").toString()
            "change" -> operator.changeDropoffPlaceholder(b.dispatchId)
            else -> operator.dispatchAction(b.dispatchId, DispatchAction.CANCEL, "E2E operator cancel").toString()
        }
        println("EXPLORE operator-$action-b: $result")
        val start = System.nanoTime()
        var shotDone = false
        repeat(16) {
            Thread.sleep(1_000)
            val sec = Duration.ofNanos(System.nanoTime() - start).seconds
            val now = texts()
            val newTexts = now.filter { it !in baseline }
            println("EXPLORE t=${sec}s new=$newTexts ids=${ids().filter { i -> i.startsWith("btn") || i.startsWith("atv_label") || i == "cancel" }}")
            if (newTexts.isNotEmpty() && !shotDone) { shots.screenshot("sheet"); shots.pageSource("sheet"); shotDone = true }
        }
        runCatching { DriverApiClient.login(second).myActiveRides().forEach { r -> if (r.name.startsWith("Appium_", ignoreCase = true)) DriverApiClient.login(second).cancelQuietly(r.id) } }
        println("EXPLORE stops-after: ${runCatching { trip.stopNames() }.getOrNull()} tripShown=${trip.isShown(Duration.ofSeconds(2))}")
    }
}

/** The second driver moves ride B to itself with change-driver (any driver of the organisation may send it). */
private fun DriverApiClient.changeDropoffPlaceholder(dispatchId: Long): String = changeDriver(dispatchId, driverId()).toString()
