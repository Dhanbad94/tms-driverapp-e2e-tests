package tms.driver.explore

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.extension.ExtendWith
import tms.driver.base.DriverExtension
import tms.driver.core.config.Credentials
import tms.driver.core.driver.DriverSession
import tms.driver.core.report.Artifacts
import tms.driver.core.wait.Toasts
import tms.driver.fixtures.RideFixture
import tms.driver.fixtures.RideSpec
import tms.driver.fixtures.TripFixtureExtension
import tms.driver.flows.LoginFlow
import tms.driver.screens.home.OdHomeScreen
import tms.driver.screens.requests.AssignmentBar
import tms.driver.screens.requests.RideRequestsScreen
import java.time.Duration

/**
 * Developer tool (tag "explore"): assigning a ride from a SEARCH RESULT. A normal ride (2 guests) is staged and confirmed from the results;
 * a 16-guest ride is staged and confirmed to see the capacity dialog. Records the bar, the toast and where the app ends up.
 */
@Tag("explore")
@Tag("device")
@EnabledIfEnvironmentVariable(named = "TMS_TRIP_API_URL", matches = ".+")
@ExtendWith(DriverExtension::class, TripFixtureExtension::class)
@DisplayName("Explore: assign from a search result")
class ExploreSearchAssignTest {
    private val d get() = DriverSession.driver
    private val shots by lazy { Artifacts(d, DriverSession.config.artifactsDir.resolve("ExploreSearchAssign")) }

    private fun snap(label: String, waitMs: Long = 1_500) {
        Thread.sleep(waitMs); shots.screenshot(label); shots.pageSource(label)
        val src = d.pageSource
        val texts = Regex("""<[^>]*\btext="([^"]+)"[^>]*>""").findAll(src).map { it.groupValues[1].take(44) }.toList().distinct()
        println("EXPLORE [$label] pkg=${d.currentPackage} texts=${texts.take(30)}")
    }

    @Test
    @DisplayName("[EXPLORE-027] Assign from a search result")
    fun capture(rides: RideFixture) {
        val creds = Credentials.fromEnvironment(); assumeTrue(creds != null, "QA driver credentials not set")
        val normal = rides.queuedRide(RideSpec(partySize = 2, ada = false, note = "Search assign", seed = 281, pickupName = "Terminal 5E", dropoffName = "Door 3 - Bus/Shuttle Ctr"))
        val big = rides.queuedRide(RideSpec(partySize = 16, ada = false, note = "Too big", seed = 282, pickupName = "Terminal 5E", dropoffName = "Mannheim Rd"))
        LoginFlow(creds!!).loginToHome()
        OdHomeScreen().waitForEmptyState(Duration.ofSeconds(40))
        val requests = RideRequestsScreen().also { it.openFromHome(); it.openQueue() }

        // 1. the big ride: capacity dialog
        var search = requests.openSearch(); search.type(big.riderName.takeLast(8))
        search.card(big.riderName)!!.tapAssignToSelf(); snap("big-staged")
        println("EXPLORE bar-shown: ${AssignmentBar().isShown(Duration.ofSeconds(4))}")
        AssignmentBar().confirm(); snap("big-after-confirm", 3_000)
        runCatching { d.findElement(tms.driver.screens.Ids.by("cancel")).click() }; Thread.sleep(1_000)
        snap("big-after-dialog-closed")
        search.close(); requests.waitUntilShown()

        // 2. the normal ride
        search = requests.openSearch(); search.type(normal.riderName.takeLast(8))
        search.card(normal.riderName)!!.tapAssignToSelf(); snap("normal-staged")
        Toasts.mark(d)
        AssignmentBar().confirm()
        println("EXPLORE toast-after-confirm: ${Toasts.read(d, Duration.ofSeconds(8))}")
        snap("normal-after-confirm", 3_000)
    }
}
