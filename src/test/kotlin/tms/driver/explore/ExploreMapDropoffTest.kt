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
import tms.driver.core.device.Adb
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
 * Developer tool (tag "explore"): the Map tab of the trip view (header card, chips, Re-center) and the Change Drop-off screen
 * (UPDATE state, no-match text, what happens offline). Nothing is confirmed: UPDATE is never tapped online.
 */
@Tag("explore")
@Tag("device")
@EnabledIfEnvironmentVariable(named = "TMS_TRIP_API_URL", matches = ".+")
@ExtendWith(DriverExtension::class, TripFixtureExtension::class)
@DisplayName("Explore: Map tab and Change Drop-off")
class ExploreMapDropoffTest {
    private val d get() = DriverSession.driver
    private val shots by lazy { Artifacts(d, DriverSession.config.artifactsDir.resolve("ExploreMapDropoff")) }
    private fun tap(by: By): Boolean = runCatching { d.findElement(by).click(); true }.getOrDefault(false)
    private fun id(n: String) = Ids.by(n)

    private fun snap(label: String, waitMs: Long = 2_000) {
        Thread.sleep(waitMs); shots.screenshot(label); shots.pageSource(label)
        val src = d.pageSource
        val seen = LinkedHashSet<String>()
        Regex("""<[^>]*resource-id="[^"]*:id/([^"]+)"[^>]*>""").findAll(src).forEach { m ->
            val rid = m.groupValues[1]
            val text = Regex("""\btext="([^"]*)"""").find(m.value)?.groupValues?.get(1).orEmpty()
            val en = Regex("""\benabled="(true|false)"""").find(m.value)?.groupValues?.get(1)
            if (text.isNotBlank() && rid !in setOf("tv_nav_home", "tv_nav_profile", "tv_nav_help")) seen += "$rid${if (rid.contains("btn")) "[en=$en]" else ""}='${text.take(34)}'"
        }
        println("EXPLORE [$label] ${seen.take(40).joinToString(" | ")}")
        println("EXPLORE [$label] noMatch=${src.contains("No match found")} ids=${Regex("""resource-id="[^"]*:id/([^"]+)"""").findAll(src).map { it.groupValues[1] }.toSet().take(40)}")
    }

    @Test
    @DisplayName("[EXPLORE-017] Map tab and Change Drop-off")
    fun capture(rides: RideFixture) {
        val creds = Credentials.fromEnvironment(); assumeTrue(creds != null, "QA driver credentials not set")
        rides.assignedRide(RideSpec(partySize = 4, ada = true, note = "Hello", seed = 211, pickupName = "Terminal 5E", dropoffName = "Door 3 - Bus/Shuttle Ctr"))
        LoginFlow(creds!!).loginToHome()
        val home = OdHomeScreen(); home.waitForTrip(Duration.ofSeconds(60))
        home.tapStartTrip(); ConfirmShuttleDialog().waitUntilShown().confirm()
        val trip = TripViewScreen().waitUntilShown().also { it.waitForEstimatedTimes() }

        trip.openMap(); snap("map-tab", 3_000)
        // drag the map to make the Re-center button appear
        val size = d.manage().window().size
        runCatching { d.executeScript("mobile: swipeGesture", mapOf("left" to size.width / 4, "top" to size.height / 2, "width" to size.width / 2, "height" to size.height / 6, "direction" to "left", "percent" to 0.6)) }
        snap("map-after-drag", 2_000)
        println("EXPLORE tap-recenter: ${tap(id("atv_reset"))}")
        snap("map-after-recenter", 2_000)
        println("EXPLORE tap-chip: ${tap(id("rv_riders"))}")
        snap("map-chip-sheet", 2_500)
        d.navigate().back(); Thread.sleep(1_500)

        trip.openList()
        val change = trip.tapRider(0).openMenu().changeDropoff()
        snap("change-dropoff-open", 2_000)
        println("EXPLORE update-enabled-at-open: ${runCatching { d.findElement(id("change_drop_off_update_btn")).isEnabled }.getOrNull()}")
        println("EXPLORE search-nomatch: ${change.search("zzqq")}")
        snap("change-dropoff-nomatch", 1_500)
        println("EXPLORE update-enabled-after-nomatch: ${runCatching { d.findElement(id("change_drop_off_update_btn")).isEnabled }.getOrNull()}")
        d.navigate().back(); Thread.sleep(1_000)
        snap("after-search-back")
        println("EXPLORE tap-cancel: ${tap(id("cancel_btn_change_drop_off"))}")
        Thread.sleep(2_000)
        println("EXPLORE trip-view-after-cancel: ${TripViewScreen().isShown(Duration.ofSeconds(8))}")

        // offline: open Change Drop-off
        val adb = Adb()
        try {
            adb.setOffline(true); Thread.sleep(4_000)
            runCatching { trip.tapRider(0).openMenu().changeDropoff() }
            Thread.sleep(8_000)
            snap("change-dropoff-offline", 500)
            println("EXPLORE offline-trip-view-shown: ${TripViewScreen().isShown(Duration.ofSeconds(3))}")
        } finally { adb.setOffline(false) }
    }
}
