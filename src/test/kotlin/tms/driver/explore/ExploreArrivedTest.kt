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
 * Developer tool (tag "explore"): walks rides through the arrived screen, pick-up, no-show, seat counter, next stop and
 * trip completion, recording every screen. Nothing is asserted.
 */
@Tag("explore")
@Tag("device")
@EnabledIfEnvironmentVariable(named = "TMS_TRIP_API_URL", matches = ".+")
@ExtendWith(DriverExtension::class, TripFixtureExtension::class)
@DisplayName("Explore: arrived screen, pick-up, no-show, completion")
class ExploreArrivedTest {
    private val d get() = DriverSession.driver
    private val shots by lazy { Artifacts(d, DriverSession.config.artifactsDir.resolve("ExploreArrived")) }
    private fun tap(by: By): Boolean = runCatching { d.findElement(by).click(); true }.getOrDefault(false)
    private fun id(n: String) = Ids.by(n)

    private fun snap(label: String, waitMs: Long = 2_500) {
        Thread.sleep(waitMs); shots.screenshot(label); shots.pageSource(label)
        val xml = d.pageSource
        val seen = LinkedHashSet<String>()
        Regex("""<[^>]*resource-id="[^"]*:id/([^"]+)"[^>]*>""").findAll(xml).forEach { m ->
            val rid = m.groupValues[1]
            val text = Regex("""\btext="([^"]*)"""").find(m.value)?.groupValues?.get(1).orEmpty()
            val enabled = Regex("""\benabled="(true|false)"""").find(m.value)?.groupValues?.get(1)
            val flag = if (rid.startsWith("btn_") || rid.startsWith("ic_")) "[en=$enabled]" else ""
            if ((text.isNotBlank() || flag.isNotEmpty()) && rid !in setOf("tv_nav_home", "tv_nav_profile", "tv_nav_help", "atv_notification_count"))
                seen += "$rid$flag${if (text.isNotBlank()) "='" + text.take(34) + "'" else ""}"
        }
        println("EXPLORE [$label] ${seen.joinToString(" | ")}")
    }

    private fun toTripView(creds: Credentials): TripViewScreen {
        LoginFlow(creds).loginToHome()
        val home = OdHomeScreen(); home.waitForTrip(Duration.ofSeconds(60))
        home.tapStartTrip(); ConfirmShuttleDialog().waitUntilShown().confirm()
        return TripViewScreen().waitUntilShown().also { it.waitForEstimatedTimes() }
    }

    @Test
    @DisplayName("[EXPLORE-008] Happy path: pick up, counter, next stop, drop-off, complete")
    fun happyPath(rides: RideFixture) {
        val creds = Credentials.fromEnvironment(); assumeTrue(creds != null)
        rides.assignedRide(RideSpec(partySize = 5, ada = true, note = "Hello", seed = 51, pickupName = "Terminal 5E", dropoffName = "Door 3 - Bus/Shuttle Ctr"))
        toTripView(creds!!).tapArrived()
        snap("A1-arrived-initial", 3_000)
        println("EXPLORE tap pick-up tick: ${tap(id("aiv_pick_up"))}"); snap("A2-after-tick")
        println("EXPLORE tap tick again: ${tap(id("aiv_pick_up"))}"); snap("A3-tick-twice")
        println("EXPLORE tap no-show cross: ${tap(id("aiv_no_show"))}"); snap("A4-cross")
        println("EXPLORE tap cross again: ${tap(id("aiv_no_show"))}"); snap("A5-cross-twice")
        println("EXPLORE tap tick: ${tap(id("aiv_pick_up"))}"); snap("A6-tick-again")
        println("EXPLORE tap cross while ticked: ${tap(id("aiv_no_show"))}"); snap("A6b-cross-while-ticked")
        println("EXPLORE tap tick again: ${tap(id("aiv_pick_up"))}"); snap("A6c-ticked")

        d.navigate().back(); snap("A7-back-with-decisions", 2_500)
        println("EXPLORE tap STAY: ${tap(id("btn_stay"))}"); snap("A8-after-stay")

        // seat counter
        println("EXPLORE tap minus: ${tap(id("ic_subtract"))}"); snap("A9-minus", 3_000)
        println("EXPLORE tap plus: ${tap(id("ic_add"))}"); snap("A10-plus", 3_000)
        var count = 0
        while (count < 14) { if (!tap(id("ic_add"))) break; count++; Thread.sleep(900) }
        snap("A11-after-many-plus", 3_000)
        println("EXPLORE plus taps made: $count")
        repeat(count + 1) { tap(id("ic_subtract")); Thread.sleep(900) }
        snap("A12-after-minus-back", 3_000)

        println("EXPLORE tap GO TO NEXT STOP: ${tap(id("btn_proceed_button"))}"); snap("A13-after-proceed-4s", 4_000)
        snap("A14-after-proceed-9s", 5_000)
        // review sheet?
        println("EXPLORE tap Later (if review sheet): ${tap(id("btn_later"))}"); snap("A15-after-later", 3_000)

        // drop-off stop
        println("EXPLORE trip view shown: ${TripViewScreen().isShown(Duration.ofSeconds(20))}")
        snap("B1-trip-view-dropoff")
        println("EXPLORE tap I've Arrived: ${tap(id("atv_mark_arrived"))}"); snap("B2-arrived-dropoff", 4_000)
        println("EXPLORE tap proceed (complete): ${tap(id("btn_proceed_button"))}"); snap("B3-after-complete-2s", 2_000)
        snap("B4-after-complete-6s", 5_000)
        println("EXPLORE tap Back To Home: ${tap(id("btn_back_to_home"))}"); snap("B5-after-back-to-home", 6_000)
        println("EXPLORE tap Later: ${tap(id("btn_later"))}"); snap("B6-final", 4_000)
    }

    @Test
    @DisplayName("[EXPLORE-009] No-show path")
    fun noShowPath(rides: RideFixture) {
        val creds = Credentials.fromEnvironment(); assumeTrue(creds != null)
        rides.assignedRide(RideSpec(partySize = 5, ada = false, note = null, seed = 52, pickupName = "Terminal 5E", dropoffName = "Door 3 - Bus/Shuttle Ctr"))
        toTripView(creds!!).tapArrived()
        snap("N1-arrived", 3_000)
        println("EXPLORE tap cross: ${tap(id("aiv_no_show"))}"); snap("N2-after-cross")
        println("EXPLORE tap GO TO NEXT STOP: ${tap(id("btn_proceed_button"))}"); snap("N3-after-proceed-4s", 4_000)
        snap("N4-after-proceed-9s", 5_000)
        println("EXPLORE tap Later: ${tap(id("btn_later"))}"); snap("N5-after-later", 3_000)
        println("EXPLORE tap Back To Home: ${tap(id("btn_back_to_home"))}"); snap("N6-after-home", 6_000)
        snap("N7-final", 3_000)
    }
}
