package tms.driver.explore

import io.appium.java_client.AppiumBy
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
import java.time.Duration

/**
 * Developer tool (tag "explore"): walks ONE ride through the start-trip flow in three segments (app data is cleared
 * in between) and records every screen and dialog: (A) the confirm path and trip view, (B) "Select Different Shuttle",
 * (C) location permission and GPS blockers. It never confirms a different shuttle. Nothing is asserted.
 */
@Tag("explore")
@Tag("device")
@EnabledIfEnvironmentVariable(named = "TMS_TRIP_API_URL", matches = ".+")
@ExtendWith(DriverExtension::class, TripFixtureExtension::class)
@DisplayName("Explore: start trip, shuttle dialogs and blockers")
class ExploreStartTripTest {
    private val d get() = DriverSession.driver
    private val shots by lazy { Artifacts(d, DriverSession.config.artifactsDir.resolve("ExploreStartTrip")) }
    private val pkg get() = DriverSession.config.appPackage

    private fun tap(by: By): Boolean = runCatching { d.findElement(by).click(); true }.getOrDefault(false)
    private fun byText(t: String): By = By.xpath("//*[@text='$t']")

    private fun snap(label: String, waitMs: Long = 2_500) {
        Thread.sleep(waitMs)
        shots.screenshot(label); shots.pageSource(label)
        val ids = listOf("tv_title", "tv_msg", "atv_label", "atv_message", "btn_positive", "btn_positive_vert", "btn_negative", "btn_continue",
            "btn_select_anyway", "btn_pickup_another", "btn_apply", "btn_go", "atv_stop_name", "atv_estimated_time", "atv_mark_arrived", "tv_list", "tv_map")
        val seen = ids.mapNotNull { id -> runCatching { d.findElement(Ids.by(id)).text.takeIf { it.isNotBlank() }?.let { "$id='$it'" } }.getOrNull() }
        println("EXPLORE [$label] ${seen.joinToString(" | ")}")
    }

    private fun home(creds: Credentials): OdHomeScreen {
        LoginFlow(creds).loginToHome()
        return OdHomeScreen().also { it.waitForTrip(Duration.ofSeconds(60)); Thread.sleep(2_500) }
    }

    @Test
    @DisplayName("[EXPLORE-005] Start trip: confirm path, shuttle picker and blockers")
    fun capture(rides: RideFixture) {
        val creds = Credentials.fromEnvironment(); assumeTrue(creds != null)
        rides.assignedRide(RideSpec(partySize = 5, ada = true, seed = 21))
        val adb = Adb()

        // ---- Segment A: confirm path
        val h = home(creds!!)
        snap("A1-home"); println("EXPLORE A1 button=${h.startButtonText()}")
        println("EXPLORE tap START TRIP: ${tap(Ids.by("btn_go"))}")
        snap("A2-after-start", 4_000)
        println("EXPLORE tap Confirm: ${tap(Ids.by("btn_positive_vert"))}")
        snap("A3-trip-view", 6_000)
        println("EXPLORE tap Map: ${tap(Ids.by("tv_map"))}"); snap("A4-map", 4_000)
        println("EXPLORE tap List: ${tap(Ids.by("tv_list"))}"); snap("A4b-list", 2_000)
        d.navigate().back(); snap("A5-after-back", 4_000)
        println("EXPLORE A5 button=${runCatching { OdHomeScreen().startButtonText() }.getOrNull()}")
        println("EXPLORE tap START TRIP again: ${tap(Ids.by("btn_go"))}"); snap("A6-second-start", 4_000)
        d.navigate().back(); Thread.sleep(2_000)

        // ---- Segment B: first-time dialog again, "Select Different Shuttle"
        adb.resetAppKeepingPermissions(pkg); d.activateApp(pkg)
        val h2 = home(creds)
        println("EXPLORE B1 button=${h2.startButtonText()} (after a trip was opened once and data cleared)")
        println("EXPLORE tap START TRIP: ${tap(Ids.by("btn_go"))}"); snap("B1-confirm-shuttle", 4_000)
        println("EXPLORE tap Select Different Shuttle: ${tap(Ids.by("btn_negative"))}"); snap("B2-picker", 4_000)
        println("EXPLORE tap Shuttle-2 row: ${tap(byText("Automated Shuttle-2"))}"); snap("B3-after-pick-shuttle2", 3_000)
        d.navigate().back(); snap("B4-after-back", 3_000)

        // ---- Segment C: location permission and GPS
        adb.resetAppKeepingPermissions(pkg); d.activateApp(pkg)
        home(creds)
        adb.revoke(pkg, "android.permission.ACCESS_FINE_LOCATION"); Thread.sleep(2_000)
        d.activateApp(pkg); Thread.sleep(4_000)
        println("EXPLORE C0 after revoke, app state=${runCatching { d.queryAppState(pkg) }.getOrNull()} home=${runCatching { OdHomeScreen().hasDispatchSummary() }.getOrNull()}")
        println("EXPLORE tap START TRIP (permission revoked): ${tap(Ids.by("btn_go"))}"); snap("C1-permission", 4_000)
        // Try the system dialog buttons, then whatever dialog shows
        val deny = listOf("com.android.permissioncontroller:id/permission_deny_button", "com.android.permissioncontroller:id/permission_deny_and_dont_ask_again_button")
            .firstOrNull { tap(AppiumBy.id(it)) }
        println("EXPLORE denied via: $deny"); snap("C2-after-deny", 3_000)
        try {
            adb.grant(pkg, "android.permission.ACCESS_FINE_LOCATION")
            adb.setLocationEnabled(false); Thread.sleep(2_000)
            d.activateApp(pkg); Thread.sleep(3_000)
            println("EXPLORE tap START TRIP (GPS off): ${tap(Ids.by("btn_go"))}"); snap("C3-gps-off", 4_000)
        } finally {
            adb.setLocationEnabled(true)
        }
    }
}
