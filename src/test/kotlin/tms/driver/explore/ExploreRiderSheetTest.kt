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
import tms.driver.fixtures.RideFixture
import tms.driver.fixtures.RideSpec
import tms.driver.fixtures.TripFixtureExtension
import tms.driver.flows.LoginFlow
import tms.driver.screens.home.OdHomeScreen
import tms.driver.screens.trip.ConfirmShuttleDialog
import tms.driver.screens.trip.TripViewScreen
import java.time.Duration

/** Developer tool (tag "explore"): the rider sheet from the trip view and from the Arrived screen, before and after PICKED UP. */
@Tag("explore")
@Tag("device")
@EnabledIfEnvironmentVariable(named = "TMS_TRIP_API_URL", matches = ".+")
@ExtendWith(DriverExtension::class, TripFixtureExtension::class)
@DisplayName("Explore: rider sheet")
class ExploreRiderSheetTest {
    private val d get() = DriverSession.driver
    private val shots by lazy { Artifacts(d, DriverSession.config.artifactsDir.resolve("ExploreRiderSheet")) }

    private fun snap(label: String, waitMs: Long = 1_200) {
        Thread.sleep(waitMs); shots.screenshot(label); shots.pageSource(label)
        val texts = Regex("""<[^>]*\btext="([^"]+)"[^>]*>""").findAll(d.pageSource).map { it.groupValues[1].take(60) }.toList().distinct()
        println("EXPLORE [$label] texts=${texts.take(40)}")
    }

    @Test
    @DisplayName("[EXPLORE-028] Rider sheet")
    fun capture(rides: RideFixture) {
        val creds = Credentials.fromEnvironment(); assumeTrue(creds != null, "QA driver credentials not set")
        val note = "O'Brien \"VIP\" <b>bold</b> café ñ & more — " + "x".repeat(250)
        rides.assignedRide(RideSpec(partySize = 3, ada = false, note = note, seed = 291, pickupName = "Terminal 5E", dropoffName = "Door 3 - Bus/Shuttle Ctr", flightNo = "UA1234", roomNo = "412"))
        LoginFlow(creds!!).loginToHome()
        val home = OdHomeScreen(); home.waitForTrip(Duration.ofSeconds(60))
        snap("home")
        home.tapStartTrip(); ConfirmShuttleDialog().waitUntilShown().confirm()
        val trip = TripViewScreen().waitUntilShown().also { it.waitForEstimatedTimes() }
        snap("trip-view")
        val s1 = trip.tapRider(0)
        s1.tapAction(); snap("after-pickup-tap")
        println("EXPLORE pickup-tap buttons=" + d.findElements(tms.driver.screens.Ids.by("btn_action_button")).map { it.text })
        runCatching { d.findElement(org.openqa.selenium.By.xpath("//*[@text='CONFIRM']")).click() }; snap("after-confirm", 2500)
        val again = trip.takeIf { it.isShown(Duration.ofSeconds(5)) }
        println("EXPLORE trip-shown=${again != null}")
        val arrived = trip.tapArrived(); snap("arrived-after-pickup")
        println("EXPLORE seats=${arrived.seatsOccupied()} proceedEnabled=${arrived.isProceedEnabled()}")
        val s2 = arrived.tapRider(true); snap("sheet-arrived-after-pickup")
        println("EXPLORE sheet2 room=${s2.roomText()} actionShown=${s2.isActionShown()}")
        s2.close(); Thread.sleep(600)
        val s3 = trip.also { arrived.tapBack(); tms.driver.screens.trip.GoBackDialog().waitUntilShown().goBack(); it.waitUntilShown() }.tapRider(0); snap("sheet-trip-after-pickup")
        println("EXPLORE sheet3 action=${s3.isActionShown()}")
        s3.close()
    }
}
