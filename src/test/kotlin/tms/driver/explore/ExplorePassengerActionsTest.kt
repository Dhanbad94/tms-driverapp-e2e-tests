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
import java.time.Duration

/**
 * Developer tool (tag "explore"): assigns one ride, opens Ride Requests > Assigned and records the card, its
 * "more options" menu and the passenger details. It only opens menus and closes them again; nothing is changed.
 */
@Tag("explore")
@Tag("device")
@EnabledIfEnvironmentVariable(named = "TMS_TRIP_API_URL", matches = ".+")
@ExtendWith(DriverExtension::class, TripFixtureExtension::class)
@DisplayName("Explore: Assigned tab, more options, passenger details (read-only)")
class ExplorePassengerActionsTest {
    private val d get() = DriverSession.driver
    private val shots by lazy { Artifacts(d, DriverSession.config.artifactsDir.resolve("ExplorePassengerActions")) }
    private fun tap(by: By): Boolean = runCatching { d.findElement(by).click(); true }.getOrDefault(false)
    private fun id(n: String) = Ids.by(n)

    private fun snap(label: String, waitMs: Long = 2_500) {
        Thread.sleep(waitMs); shots.screenshot(label); shots.pageSource(label)
        val seen = LinkedHashSet<String>()
        Regex("""<[^>]*resource-id="[^"]*:id/([^"]+)"[^>]*>""").findAll(d.pageSource).forEach { m ->
            val rid = m.groupValues[1]
            val text = Regex("""\btext="([^"]*)"""").find(m.value)?.groupValues?.get(1).orEmpty()
            if (text.isNotBlank() && rid !in setOf("tv_nav_home", "tv_nav_profile", "tv_nav_help")) seen += "$rid='${text.take(40)}'"
        }
        println("EXPLORE [$label] ${seen.joinToString(" | ")}")
        // Menu items often have text but no id
        val texts = Regex("""<android.widget.TextView[^>]*\btext="([^"]+)"""").findAll(d.pageSource).map { it.groupValues[1].take(40) }.toList()
        println("EXPLORE [$label] texts: ${texts.distinct().joinToString(" | ")}")
    }

    @Test
    @DisplayName("[EXPLORE-005] Capture the Assigned card, its menu and the passenger details")
    fun capture(rides: RideFixture) {
        val creds = Credentials.fromEnvironment(); assumeTrue(creds != null, "QA driver credentials not set")
        rides.assignedRide(RideSpec(partySize = 5, ada = true, note = "Hello", seed = 51, pickupName = "Terminal 5E", dropoffName = "Door 3 - Bus/Shuttle Ctr"))
        LoginFlow(creds!!).loginToHome()
        OdHomeScreen().waitForTrip(Duration.ofSeconds(60))
        println("EXPLORE open-requests: ${tap(id("fl_home_ride_requests"))}")
        Thread.sleep(3_000)
        println("EXPLORE tap-assigned: ${tap(id("assigned"))}")
        snap("assigned-tab")
        println("EXPLORE tap-more: ${tap(id("options_more"))}")
        snap("more-options")
        d.navigate().back()
        snap("after-menu-back")
        println("EXPLORE tap-passenger: ${tap(id("passenger_name"))}")
        snap("tap-passenger")
        d.navigate().back()
        snap("after-passenger-back")
    }
}
