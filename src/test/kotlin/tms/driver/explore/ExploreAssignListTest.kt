package tms.driver.explore

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.extension.ExtendWith
import org.openqa.selenium.By
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
import tms.driver.screens.requests.RideRequestsScreen
import tms.driver.screens.trip.AssignDriverScreen
import java.time.Duration

/**
 * Developer tool (tag "explore"): the Assign Driver screen opened from a WAITING ride (no assignment needed). Swipes up on the driver list
 * with a real gesture and records every driver name seen, to check whether "Automation Driver 2" is further down the list.
 */
@Tag("explore")
@Tag("device")
@EnabledIfEnvironmentVariable(named = "TMS_TRIP_API_URL", matches = ".+")
@ExtendWith(DriverExtension::class, TripFixtureExtension::class)
@DisplayName("Explore: Assign Driver list, scrolled to the bottom")
class ExploreAssignListTest {
    private val d get() = DriverSession.driver
    private val shots by lazy { Artifacts(d, DriverSession.config.artifactsDir.resolve("ExploreAssignList")) }

    @Test
    @DisplayName("[EXPLORE-023] Assign Driver list")
    fun capture(rides: RideFixture) {
        val creds = Credentials.fromEnvironment(); assumeTrue(creds != null, "QA driver credentials not set")
        val ride = rides.queuedRide(RideSpec(partySize = 2, ada = false, note = "Hello", seed = 251, pickupName = "Terminal 5E", dropoffName = "Door 3 - Bus/Shuttle Ctr"))
        LoginFlow(creds!!).loginToHome()
        OdHomeScreen().waitForEmptyState(Duration.ofSeconds(40))
        val requests = RideRequestsScreen().also { it.openFromHome(); it.openQueue() }
        requests.card(ride.riderName, Duration.ofSeconds(30))!!.tapAssignToDriver()
        val assign = AssignDriverScreen().waitUntilShown()
        Thread.sleep(3_000)
        shots.screenshot("assign-top"); shots.pageSource("assign-top")
        val seen = LinkedHashSet<String>(assign.visibleDrivers())
        println("EXPLORE first-view: ${assign.visibleDrivers()}")
        val list = d.findElements(Ids.by("rv_available_drivers")).firstOrNull()
        val rect = list?.rect
        println("EXPLORE list-rect: $rect  window=${d.manage().window().size}")
        repeat(8) { i ->
            val r = rect ?: return@repeat
            val moved = runCatching {
                d.executeScript("mobile: swipeGesture", mapOf("left" to r.x + 20, "top" to r.y + 10, "width" to (r.width - 40).coerceAtLeast(40), "height" to (r.height - 20).coerceAtLeast(40), "direction" to "up", "percent" to 0.9))
            }.isSuccess
            Thread.sleep(1_200)
            val now = assign.visibleDrivers()
            val newOnes = now.filter { it !in seen }
            seen += now
            println("EXPLORE swipe ${i + 1}: moved=$moved visible=$now new=$newOnes")
            if (i == 2) { shots.screenshot("assign-after-swipes"); shots.pageSource("assign-after-swipes") }
        }
        println("EXPLORE all-drivers-seen: $seen")
        println("EXPLORE driver2-listed: ${seen.any { it.contains("Automation Driver 2") }}")
    }
}
