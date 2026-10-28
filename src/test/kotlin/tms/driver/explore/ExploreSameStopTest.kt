package tms.driver.explore

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.extension.ExtendWith
import org.openqa.selenium.By
import tms.driver.backend.DriverApiClient
import tms.driver.backend.DriverApiConfig
import tms.driver.base.DriverExtension
import tms.driver.core.config.Credentials
import tms.driver.core.driver.DriverSession
import tms.driver.core.report.Artifacts
import tms.driver.fixtures.RideFixture
import tms.driver.fixtures.RideSpec
import tms.driver.fixtures.TripFixtureExtension
import tms.driver.flows.LoginFlow
import tms.driver.screens.Ids
import tms.driver.screens.home.OdHomeScreen
import tms.driver.screens.trip.ConfirmShuttleDialog
import tms.driver.screens.trip.TripViewScreen
import java.time.Duration

/**
 * Developer tool (tag "explore"): two riders at the same pickup stop. Ride A is started in the app; ride B (same pickup) is then
 * handed to the QA driver by the second driver while the trip runs. Records the trip view and the arrived screen with two riders.
 */
@Tag("explore")
@Tag("device")
@EnabledIfEnvironmentVariable(named = "TMS_TRIP_API_URL", matches = ".+")
@ExtendWith(DriverExtension::class, TripFixtureExtension::class)
@DisplayName("Explore: two riders at one stop")
class ExploreSameStopTest {
    private val d get() = DriverSession.driver
    private val shots by lazy { Artifacts(d, DriverSession.config.artifactsDir.resolve("ExploreSameStop")) }
    private fun tap(by: By): Boolean = runCatching { d.findElement(by).click(); true }.getOrDefault(false)
    private fun id(n: String) = Ids.by(n)

    private fun snap(label: String, waitMs: Long = 2_500) {
        Thread.sleep(waitMs); shots.screenshot(label); shots.pageSource(label)
        val seen = LinkedHashSet<String>()
        Regex("""<[^>]*resource-id="[^"]*:id/([^"]+)"[^>]*>""").findAll(d.pageSource).forEach { m ->
            val rid = m.groupValues[1]
            val text = Regex("""\btext="([^"]*)"""").find(m.value)?.groupValues?.get(1).orEmpty()
            val en = Regex("""\benabled="(true|false)"""").find(m.value)?.groupValues?.get(1)
            if (text.isNotBlank() && rid !in setOf("tv_nav_home", "tv_nav_profile", "tv_nav_help")) seen += "$rid${if (rid.startsWith("btn_")) "[en=$en]" else ""}='${text.take(30)}'"
            else if (rid in setOf("aiv_pick_up", "aiv_no_show")) seen += rid
        }
        println("EXPLORE [$label] ${seen.joinToString(" | ")}")
    }

    @Test
    @DisplayName("[EXPLORE-011] Two riders at one stop")
    fun capture(rides: RideFixture) {
        val creds = Credentials.fromEnvironment(); assumeTrue(creds != null, "QA driver credentials not set")
        val second = DriverApiConfig.forSecondDriverFromEnvironment(); assumeTrue(second != null, "second driver not configured")
        val a = rides.assignedRide(RideSpec(partySize = 3, ada = false, note = "First", seed = 171, pickupName = "Terminal 5E", dropoffName = "Door 3 - Bus/Shuttle Ctr"))
        val b = rides.queuedRide(RideSpec(partySize = 2, ada = false, note = "Second", seed = 172, pickupName = "Terminal 5E", dropoffName = "Mannheim Rd"))
        val qaId = rides.qaDriverId()
        val operator = DriverApiClient.login(second!!)
        println("EXPLORE rides: A=${a.riderName} B=${b.riderName}")
        LoginFlow(creds!!).loginToHome()
        val home = OdHomeScreen(); home.waitForTrip(Duration.ofSeconds(60))
        home.tapStartTrip(); ConfirmShuttleDialog().waitUntilShown().confirm()
        val trip = TripViewScreen().waitUntilShown().also { it.waitForEstimatedTimes() }
        println("EXPLORE stops-before: ${trip.stopNames()}")
        println("EXPLORE assign-b-by-second: ${operator.assign(b.dispatchId, qaId)}")
        Thread.sleep(6_000)
        trip.pullToRefresh(); Thread.sleep(3_000)
        snap("trip-view-after-b")
        println("EXPLORE stops-after: ${trip.stopNames()}")
        println("EXPLORE chips: ${trip.riderChipsPerStop()}")
        trip.tapArrived()
        snap("arrived-two-riders")
        val boxes = d.findElements(id("aiv_pick_up"))
        println("EXPLORE pickup-boxes: ${boxes.size}")
        boxes.firstOrNull()?.click(); Thread.sleep(1_000); snap("after-first-pickup", 1_000)
        d.findElements(id("aiv_pick_up")).firstOrNull()?.click(); Thread.sleep(1_000); snap("after-second-pickup", 1_000)
        println("EXPLORE tap-proceed: ${tap(id("btn_proceed_button"))}")
        snap("after-proceed", 6_000)
    }
}
