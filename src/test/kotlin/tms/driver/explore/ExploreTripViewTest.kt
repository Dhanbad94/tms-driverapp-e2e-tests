package tms.driver.explore

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.extension.ExtendWith
import org.openqa.selenium.By
import org.openqa.selenium.remote.RemoteWebElement
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
 * Developer tool (tag "explore"): walks one ride through the trip view and records the stop card, the stop list,
 * the rider rows and their detail dialog, "Go here instead", pull to refresh and the arrived screen.
 * It never confirms "Go here instead" and never marks anyone picked up. Nothing is asserted.
 */
@Tag("explore")
@Tag("device")
@EnabledIfEnvironmentVariable(named = "TMS_TRIP_API_URL", matches = ".+")
@ExtendWith(DriverExtension::class, TripFixtureExtension::class)
@DisplayName("Explore: trip view and arrival")
class ExploreTripViewTest {
    private val d get() = DriverSession.driver
    private val shots by lazy { Artifacts(d, DriverSession.config.artifactsDir.resolve("ExploreTripView")) }
    private fun tap(by: By): Boolean = runCatching { d.findElement(by).click(); true }.getOrDefault(false)

    /** Saves a screenshot and UI dump, and prints every id that carries text so the log is readable. */
    private fun snap(label: String, waitMs: Long = 2_500) {
        Thread.sleep(waitMs); shots.screenshot(label); shots.pageSource(label)
        val xml = d.pageSource
        val seen = LinkedHashSet<String>()
        Regex("""<[^>]*resource-id="[^"]*:id/([^"]+)"[^>]*>""").findAll(xml).forEach { m ->
            val id = m.groupValues[1]
            val text = Regex("""\btext="([^"]*)"""").find(m.value)?.groupValues?.get(1).orEmpty()
            if (text.isNotBlank() && id !in setOf("tv_nav_home", "tv_nav_profile", "tv_nav_help")) seen += "$id='${text.take(40)}'"
        }
        println("EXPLORE [$label] ${seen.joinToString(" | ")}")
    }

    @Test
    @DisplayName("[EXPLORE-007] Trip view, rider rows, Go here instead, refresh, arrived")
    fun capture(rides: RideFixture) {
        val creds = Credentials.fromEnvironment(); assumeTrue(creds != null)
        rides.assignedRide(RideSpec(partySize = 5, ada = true, note = "Hello", seed = 41, pickupName = "Terminal 5E", dropoffName = "Door 3 - Bus/Shuttle Ctr"))
        LoginFlow(creds!!).loginToHome()
        val home = OdHomeScreen(); home.waitForTrip(Duration.ofSeconds(60))
        home.tapStartTrip(); ConfirmShuttleDialog().waitUntilShown().confirm()
        TripViewScreen().waitUntilShown(); Thread.sleep(3_000)

        snap("T1-list-view")
        // rider row at the current stop: tap the rider name text
        val rider = runCatching { d.findElements(By.xpath("//*[contains(@resource-id,':id/atv_name') or contains(@resource-id,':id/atv_rider') or contains(@resource-id,':id/tv_name')]")).map { it.text } }.getOrNull()
        println("EXPLORE rider-ish texts: $rider")

        // find the first row inside the riders list (rv_stops) and tap it
        val riderRow = d.findElements(Ids.by("rv_stops")).firstOrNull()?.findElements(By.xpath(".//*[@clickable='true']"))?.firstOrNull()
        println("EXPLORE tap first rider row: ${runCatching { riderRow?.click(); riderRow != null }.getOrDefault(false)}")
        snap("T2-rider-details", 3_500)
        println("EXPLORE tap more (iv_more): ${tap(Ids.by("iv_more"))}"); snap("T3-more-options", 2_500)
        d.navigate().back(); Thread.sleep(1_200)
        if (!tap(Ids.by("btn_cancel"))) tap(Ids.by("cancel")); Thread.sleep(1_200)
        snap("T4-after-closing-details")

        // Go here instead on the upcoming stop
        println("EXPLORE tap Go here instead (tv_action_btn): ${tap(Ids.by("tv_action_btn"))}"); snap("T5-go-here-dialog", 3_000)
        println("EXPLORE tap dialog cancel: ${tap(Ids.by("btn_cancel"))}"); snap("T6-after-cancel", 2_000)

        // pull to refresh
        val swipe = d.findElements(Ids.by("swipeRL")).firstOrNull() as? RemoteWebElement
        println("EXPLORE swipe refresh: ${runCatching { d.executeScript("mobile: swipeGesture", mapOf("elementId" to swipe!!.id, "direction" to "down", "percent" to 0.8)); true }.getOrDefault(false)}")
        snap("T7-after-refresh", 3_500)

        // Map tab
        println("EXPLORE tap map: ${tap(Ids.by("tv_map"))}"); snap("T8-map", 4_000)
        println("EXPLORE tap list: ${tap(Ids.by("tv_list"))}"); Thread.sleep(1_500)

        // I've Arrived, tapped twice quickly
        runCatching { val el = d.findElement(Ids.by("atv_mark_arrived")); el.click(); runCatching { el.click() } }
        snap("T9-arrived-screen", 5_000)
        d.navigate().back(); snap("T10-back-from-arrived", 4_000)
        d.navigate().back(); snap("T11-second-back", 3_000)
    }
}
