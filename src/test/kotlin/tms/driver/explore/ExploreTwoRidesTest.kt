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
import tms.driver.screens.trip.ConfirmShuttleDialog
import tms.driver.screens.trip.TripViewScreen
import java.time.Duration

/**
 * Developer tool (tag "explore"): two rides on one driver. Ride A is assigned and its trip started through the API, then ride B is
 * assigned (the staging server accepts that only after the trip has started). Records Home, the trip view and the Go-here-instead option.
 */
@Tag("explore")
@Tag("device")
@EnabledIfEnvironmentVariable(named = "TMS_TRIP_API_URL", matches = ".+")
@ExtendWith(DriverExtension::class, TripFixtureExtension::class)
@DisplayName("Explore: two rides on one driver")
class ExploreTwoRidesTest {
    private val d get() = DriverSession.driver
    private val shots by lazy { Artifacts(d, DriverSession.config.artifactsDir.resolve("ExploreTwoRides")) }
    private fun tap(by: By): Boolean = runCatching { d.findElement(by).click(); true }.getOrDefault(false)
    private fun id(n: String) = Ids.by(n)

    private fun snap(label: String, waitMs: Long = 2_500) {
        Thread.sleep(waitMs); shots.screenshot(label); shots.pageSource(label)
        val seen = LinkedHashSet<String>()
        Regex("""<[^>]*resource-id="[^"]*:id/([^"]+)"[^>]*>""").findAll(d.pageSource).forEach { m ->
            val rid = m.groupValues[1]
            val text = Regex("""\btext="([^"]*)"""").find(m.value)?.groupValues?.get(1).orEmpty()
            if (text.isNotBlank() && rid !in setOf("tv_nav_home", "tv_nav_profile", "tv_nav_help")) seen += "$rid='${text.take(34)}'"
        }
        println("EXPLORE [$label] ${seen.joinToString(" | ")}")
    }

    @Test
    @DisplayName("[EXPLORE-010] Two rides: Home, trip view, Go here instead")
    fun capture(rides: RideFixture) {
        val creds = Credentials.fromEnvironment(); assumeTrue(creds != null, "QA driver credentials not set")
        val a = rides.assignedRide(RideSpec(partySize = 3, ada = false, note = "First", seed = 141, pickupName = "Terminal 5E", dropoffName = "Door 3 - Bus/Shuttle Ctr"))
        rides.startTripByApi()
        val b = rides.assignedRide(RideSpec(partySize = 2, ada = false, note = "Second", seed = 142, pickupName = "Mannheim Rd", dropoffName = "Terminal 3 10000"))
        println("EXPLORE rides: A=${a.riderName} B=${b.riderName}")
        LoginFlow(creds!!).loginToHome()
        val home = OdHomeScreen(); home.waitForTrip(Duration.ofSeconds(60))
        snap("home-two-rides")
        home.tapStartTrip()
        if (!TripViewScreen().isShown(Duration.ofSeconds(8))) runCatching { ConfirmShuttleDialog().waitUntilShown().confirm() }
        val trip = TripViewScreen().waitUntilShown().also { it.waitForEstimatedTimes() }
        snap("trip-view-two-rides")
        println("EXPLORE stops: ${trip.stopNames()}")
        println("EXPLORE tap-go-here: ${tap(By.xpath("//*[@text='GO HERE INSTEAD' or @text='Go here instead' or @text='Go Here Instead']"))}")
        snap("after-go-here-tap")
        d.navigate().back(); Thread.sleep(1_500)
    }
}
