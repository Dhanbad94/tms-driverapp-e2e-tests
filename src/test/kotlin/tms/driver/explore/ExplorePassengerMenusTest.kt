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
 * Developer tool (tag "explore"): opens the dialogs behind the Assigned card (Cancel Dispatch reasons, ASSIGN TO DRIVER)
 * and behind the rider sheet of the trip view (its menu, Change Drop-off, Call Guest) and records them. Every dialog is
 * left again with Back; nothing is confirmed.
 */
@Tag("explore")
@Tag("device")
@EnabledIfEnvironmentVariable(named = "TMS_TRIP_API_URL", matches = ".+")
@ExtendWith(DriverExtension::class, TripFixtureExtension::class)
@DisplayName("Explore: reasons, driver list, rider menu (read-only)")
class ExplorePassengerMenusTest {
    private val d get() = DriverSession.driver
    private val shots by lazy { Artifacts(d, DriverSession.config.artifactsDir.resolve("ExplorePassengerMenus")) }
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
        val ids = Regex("""resource-id="[^"]*:id/([^"]+)"""").findAll(d.pageSource).map { it.groupValues[1] }.toSet()
        println("EXPLORE [$label] ids: ${ids.joinToString(",")}")
        println("EXPLORE [$label] package: ${d.currentPackage}")
    }

    @Test
    @DisplayName("[EXPLORE-006] Assigned card dialogs, then the trip view rider menu")
    fun capture(rides: RideFixture) {
        val creds = Credentials.fromEnvironment(); assumeTrue(creds != null, "QA driver credentials not set")
        rides.assignedRide(RideSpec(partySize = 5, ada = true, note = "Hello", seed = 52, pickupName = "Terminal 5E", dropoffName = "Door 3 - Bus/Shuttle Ctr"))
        LoginFlow(creds!!).loginToHome()
        val home = OdHomeScreen(); home.waitForTrip(Duration.ofSeconds(60))

        tap(id("fl_home_ride_requests")); Thread.sleep(3_000); tap(id("assigned")); Thread.sleep(2_000)
        println("EXPLORE assign-to-driver: ${tap(id("assign_other_driver"))}")
        snap("assign-driver-list")
        d.navigate().back(); snap("after-assign-back")
        println("EXPLORE call-from-menu: ${tap(id("options_more"))}")
        Thread.sleep(1_500)
        println("EXPLORE tap-call: ${tap(id("tv_call"))}")
        snap("after-call")
        d.navigate().back(); Thread.sleep(1_500); d.navigate().back(); Thread.sleep(1_500)
        snap("back-from-dialer")
        if (!tap(id("fab_home"))) d.navigate().back()
        Thread.sleep(2_000)

        home.waitForTrip(Duration.ofSeconds(30)); home.tapStartTrip(); ConfirmShuttleDialog().waitUntilShown().confirm()
        TripViewScreen().waitUntilShown().also { it.waitForEstimatedTimes() }.tapRider(0)
        snap("rider-sheet")
        println("EXPLORE rider-more: ${tap(id("iv_more"))}")
        snap("rider-menu")
        println("EXPLORE change-drop-off: ${tap(id("atv_change_drop_off"))}")
        snap("change-drop-off")
        println("EXPLORE search-drop-off: ${tap(id("search_drop_off"))}")
        snap("drop-off-search")
        runCatching { d.findElement(By.className("android.widget.EditText")).sendKeys("Door") }
        snap("drop-off-search-door")
        d.navigate().back(); snap("after-change-drop-off-back")
    }
}
