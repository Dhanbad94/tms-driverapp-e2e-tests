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
 * Developer tool (tag "explore"): looks at the three open Phase T5 items. It shows a ride with a room and flight number in the rider
 * sheet, chooses the "Other" cancel reason without text (and backs out without confirming), and opens the menu of a Completed card.
 */
@Tag("explore")
@Tag("device")
@EnabledIfEnvironmentVariable(named = "TMS_TRIP_API_URL", matches = ".+")
@ExtendWith(DriverExtension::class, TripFixtureExtension::class)
@DisplayName("Explore: rider sheet details, Other reason, Completed card menu")
class ExploreOpenT5Test {
    private val d get() = DriverSession.driver
    private val shots by lazy { Artifacts(d, DriverSession.config.artifactsDir.resolve("ExploreOpenT5")) }
    private fun tap(by: By): Boolean = runCatching { d.findElement(by).click(); true }.getOrDefault(false)
    private fun id(n: String) = Ids.by(n)

    private fun snap(label: String, waitMs: Long = 2_000) {
        Thread.sleep(waitMs); shots.screenshot(label); shots.pageSource(label)
        val seen = LinkedHashSet<String>()
        Regex("""<[^>]*resource-id="[^"]*:id/([^"]+)"[^>]*>""").findAll(d.pageSource).forEach { m ->
            val rid = m.groupValues[1]
            val text = Regex("""\btext="([^"]*)"""").find(m.value)?.groupValues?.get(1).orEmpty()
            val en = Regex("""\benabled="(true|false)"""").find(m.value)?.groupValues?.get(1)
            if (rid.startsWith("done") || rid.startsWith("btn_") || text.isNotBlank()) seen += "$rid${if (rid.contains("btn")) "[en=$en]" else ""}${if (text.isNotBlank()) "='" + text.take(40) + "'" else ""}"
        }
        println("EXPLORE [$label] ${seen.joinToString(" | ")}")
        println("EXPLORE [$label] edit-texts: ${d.findElements(By.className("android.widget.EditText")).size}")
    }

    @Test
    @DisplayName("[EXPLORE-007] Rider sheet details, Other reason and Completed menu")
    fun capture(rides: RideFixture) {
        val creds = Credentials.fromEnvironment(); assumeTrue(creds != null, "QA driver credentials not set")
        rides.assignedRide(RideSpec(partySize = 5, ada = true, note = "Hello", seed = 81, pickupName = "Terminal 5E", dropoffName = "Door 3 - Bus/Shuttle Ctr", flightNo = "UA1234", roomNo = "412"))
        LoginFlow(creds!!).loginToHome()
        val home = OdHomeScreen(); home.waitForTrip(Duration.ofSeconds(60))
        home.tapStartTrip(); ConfirmShuttleDialog().waitUntilShown().confirm()
        val trip = TripViewScreen().waitUntilShown().also { it.waitForEstimatedTimes() }
        trip.tapRider(0)
        snap("rider-sheet-room-flight")
        d.navigate().back(); Thread.sleep(1_500)
        d.navigate().back(); Thread.sleep(2_000)           // back to Home (trip stays started)

        tap(id("fl_home_ride_requests")); Thread.sleep(3_000); tap(id("in_progress".replace("_", ""))); Thread.sleep(2_000)
        snap("in-progress-card")
        println("EXPLORE more: ${tap(id("options_more"))}"); Thread.sleep(1_500)
        snap("in-progress-menu")
        println("EXPLORE cancel-dispatch: ${tap(id("tv_reject_req"))}"); Thread.sleep(1_500)
        snap("reasons")
        d.findElements(id("template_text")).firstOrNull { it.text == "Other" }?.click()
        snap("other-chosen")
        d.navigate().back(); Thread.sleep(1_000); d.navigate().back(); Thread.sleep(1_000)
        snap("backed-out")
    }
}
