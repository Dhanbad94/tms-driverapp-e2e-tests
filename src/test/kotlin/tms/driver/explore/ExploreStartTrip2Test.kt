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

/** Developer tool (tag "explore"): second pass for Phase T2 - small-shuttle alert, permission denied, offline, double tap. */
@Tag("explore")
@Tag("device")
@EnabledIfEnvironmentVariable(named = "TMS_TRIP_API_URL", matches = ".+")
@ExtendWith(DriverExtension::class, TripFixtureExtension::class)
@DisplayName("Explore: start trip blockers, second pass")
class ExploreStartTrip2Test {
    private val d get() = DriverSession.driver
    private val shots by lazy { Artifacts(d, DriverSession.config.artifactsDir.resolve("ExploreStartTrip2")) }
    private val pkg get() = DriverSession.config.appPackage
    private fun tap(by: By): Boolean = runCatching { d.findElement(by).click(); true }.getOrDefault(false)
    private fun byText(t: String): By = By.xpath("//*[@text='$t']")

    private fun snap(label: String, waitMs: Long = 2_500) {
        Thread.sleep(waitMs); shots.screenshot(label); shots.pageSource(label)
        val ids = listOf("tv_title", "tv_message", "tv_msg", "atv_label", "atv_belongs_to_another", "atv_requiresAda", "atv_shuttleSmall",
            "atv_tracker_unplugged", "atv_requiresAda", "btn_positive_vert", "btn_negative", "btn_select_anyway", "btn_pickup_another",
            "btn_apply", "btn_go", "atv_stop_name", "alertTitle", "message", "button1", "button2", "atv_seat_count", "atv_seat_type")
        val seen = ids.distinct().mapNotNull { id ->
            val by = if (id in setOf("alertTitle", "message", "button1", "button2")) AppiumBy.id("android:id/$id").let { _ -> Ids.by(id) } else Ids.by(id)
            runCatching { d.findElement(by).text.takeIf { it.isNotBlank() }?.let { "$id='$it'" } }.getOrNull()
        }
        val sys = listOf("permission_message", "permission_deny_button").mapNotNull { id ->
            runCatching { d.findElement(AppiumBy.id("com.android.permissioncontroller:id/$id")).text.let { "$id='$it'" } }.getOrNull()
        }
        println("EXPLORE [$label] ${(seen + sys).joinToString(" | ")}")
    }

    private fun home(creds: Credentials) { LoginFlow(creds).loginToHome(); OdHomeScreen().waitForTrip(Duration.ofSeconds(60)); Thread.sleep(2_500) }

    @Test
    @DisplayName("[EXPLORE-006] Small shuttle alert, permission denied, offline and double tap")
    fun capture(rides: RideFixture) {
        val creds = Credentials.fromEnvironment(); assumeTrue(creds != null)
        rides.assignedRide(RideSpec(partySize = 12, ada = true, seed = 22))
        val adb = Adb()

        // Seg 1: alerts for a shuttle that is too small, belongs to another driver and has no ADA
        home(creds!!); tap(Ids.by("btn_go")); snap("S1-confirm", 3_000)
        tap(Ids.by("btn_negative")); snap("S1-picker", 3_000)
        tap(byText("Automated Shuttle-2")); snap("S1-alerts-shuttle2", 3_000)
        tap(Ids.by("btn_select_anyway")); snap("S1-after-select-anyway", 2_500)
        d.navigate().back(); Thread.sleep(1_500)

        // Seg 2: permission denied, then start trip
        adb.resetAppKeepingPermissions(pkg); d.activateApp(pkg); home(creds)
        adb.revoke(pkg, "android.permission.ACCESS_FINE_LOCATION"); Thread.sleep(1_500)
        d.activateApp(pkg); snap("P1-after-relaunch", 4_000)
        tap(AppiumBy.id("com.android.permissioncontroller:id/permission_deny_button")); snap("P2-after-deny", 3_000)
        println("EXPLORE tap START TRIP (denied): ${tap(Ids.by("btn_go"))}"); snap("P3-start-denied", 4_000)
        tap(AppiumBy.id("com.android.permissioncontroller:id/permission_deny_button")); snap("P4-after-second-deny", 3_000)

        // Seg 3: offline start
        adb.resetAppKeepingPermissions(pkg); d.activateApp(pkg); home(creds)
        try {
            adb.setOffline(true); Thread.sleep(3_000)
            println("EXPLORE tap START TRIP (offline): ${tap(Ids.by("btn_go"))}"); snap("O1-offline-start", 6_000)
            tap(Ids.by("btn_positive_vert")); snap("O2-offline-confirm", 7_000)
        } finally { adb.setOffline(false); Thread.sleep(3_000) }

        // Seg 4: double tap START TRIP
        adb.resetAppKeepingPermissions(pkg); d.activateApp(pkg); home(creds)
        runCatching { val el = d.findElement(Ids.by("btn_go")); el.click(); el.click() }
        snap("D1-double-tap", 4_000)
        println("EXPLORE confirm dialogs on screen: ${d.findElements(Ids.by("tv_title")).size}")
        tap(Ids.by("btn_positive_vert")); snap("D2-after-confirm", 6_000)
        println("EXPLORE stop-name views: ${d.findElements(Ids.by("atv_stop_name")).size}")
        d.navigate().back(); snap("D3-back-once", 3_000)
    }
}
