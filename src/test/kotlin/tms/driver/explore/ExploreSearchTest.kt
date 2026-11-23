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
import tms.driver.screens.requests.RequestsTab
import tms.driver.screens.requests.RideRequestsScreen
import java.time.Duration

/**
 * Developer tool (tag "explore"): the search screen with a WAITING ride and a ride the rider CANCELLED (no assignment needed), the menu on a
 * search result, and pull to refresh on the Requests tab. Nothing is changed in the app.
 */
@Tag("explore")
@Tag("device")
@EnabledIfEnvironmentVariable(named = "TMS_TRIP_API_URL", matches = ".+")
@ExtendWith(DriverExtension::class, TripFixtureExtension::class)
@DisplayName("Explore: search results and pull to refresh")
class ExploreSearchTest {
    private val d get() = DriverSession.driver
    private val shots by lazy { Artifacts(d, DriverSession.config.artifactsDir.resolve("ExploreSearch")) }
    private fun tap(by: By): Boolean = runCatching { d.findElement(by).click(); true }.getOrDefault(false)
    private fun id(n: String) = Ids.by(n)

    private fun snap(label: String, waitMs: Long = 2_000) {
        Thread.sleep(waitMs); shots.screenshot(label); shots.pageSource(label)
        val src = d.pageSource
        val texts = Regex("""<[^>]*\btext="([^"]+)"[^>]*>""").findAll(src).map { it.groupValues[1].take(44) }.toList().distinct()
        println("EXPLORE [$label] texts=${texts.take(34)}")
        println("EXPLORE [$label] ids=${Regex("""resource-id="[^"]*:id/([^"]+)"""").findAll(src).map { it.groupValues[1] }.toSet().filter { !it.startsWith("tv_nav") }.take(40)}")
    }

    @Test
    @DisplayName("[EXPLORE-024] Search results")
    fun capture(rides: RideFixture) {
        val creds = Credentials.fromEnvironment(); assumeTrue(creds != null, "QA driver credentials not set")
        val waiting = rides.queuedRide(RideSpec(partySize = 2, ada = false, note = "Waiting", seed = 261, pickupName = "Terminal 5E", dropoffName = "Door 3 - Bus/Shuttle Ctr"))
        val cancelled = rides.queuedRide(RideSpec(partySize = 3, ada = true, note = "Cancelled", seed = 262, pickupName = "Terminal 5E", dropoffName = "Mannheim Rd"))
        rides.riderCancels(cancelled)
        LoginFlow(creds!!).loginToHome()
        OdHomeScreen().waitForEmptyState(Duration.ofSeconds(40))
        val requests = RideRequestsScreen().also { it.openFromHome() }
        requests.openQueue()
        val search = requests.openSearch()

        search.type(waiting.riderName.takeLast(8)); snap("search-waiting")
        println("EXPLORE tap-more-on-result: ${tap(id("options_more"))}"); snap("search-result-menu", 1_500)
        d.navigate().back(); Thread.sleep(1_000)
        search.type(cancelled.riderName.takeLast(8)); snap("search-cancelled")
        search.type("Appium_ride"); snap("search-both")
        search.close()

        // pull to refresh on the Requests tab
        requests.waitUntilShown(); requests.openQueue()
        val rect = d.findElement(id("rv_ride_requests")).rect
        println("EXPLORE pull-to-refresh: " + runCatching { d.executeScript("mobile: swipeGesture", mapOf("left" to rect.x + 40, "top" to rect.y + 10, "width" to rect.width - 80, "height" to rect.height / 2, "direction" to "down", "percent" to 0.9)) }.isSuccess)
        snap("after-pull", 1_000)
        val live = rides.liveRide(RideSpec(partySize = 1, ada = false, note = "Live", seed = 263, pickupName = "Terminal 5E", dropoffName = "Door 3 - Bus/Shuttle Ctr"))
        try {
            Thread.sleep(8_000)
            println("EXPLORE live-ride-listed-without-refresh: ${requests.hasCard(live.riderName)}")
        } finally { rides.riderCancels(live) }
    }
}
