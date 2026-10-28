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
import tms.driver.screens.requests.RequestsTab
import tms.driver.screens.requests.RideRequestsScreen
import java.time.Duration

/**
 * Developer tool (tag "explore"): the Requests queue. Stages a waiting ride with ASSIGN TO SELF, records the confirm bar, cancels it,
 * then stages and confirms it. Also records the sort dialog and the search screen. The ride is created by the Rider API and the
 * fixture cancels whatever is left.
 */
@Tag("explore")
@Tag("device")
@EnabledIfEnvironmentVariable(named = "TMS_TRIP_API_URL", matches = ".+")
@ExtendWith(DriverExtension::class, TripFixtureExtension::class)
@DisplayName("Explore: Requests queue, assign to self, sort, search")
class ExploreIntakeTest {
    private val d get() = DriverSession.driver
    private val shots by lazy { Artifacts(d, DriverSession.config.artifactsDir.resolve("ExploreIntake")) }
    private fun tap(by: By): Boolean = runCatching { d.findElement(by).click(); true }.getOrDefault(false)
    private fun id(n: String) = Ids.by(n)

    private fun snap(label: String, waitMs: Long = 2_000) {
        Thread.sleep(waitMs); shots.screenshot(label); shots.pageSource(label)
        val seen = LinkedHashSet<String>()
        Regex("""<[^>]*resource-id="[^"]*:id/([^"]+)"[^>]*>""").findAll(d.pageSource).forEach { m ->
            val rid = m.groupValues[1]
            val text = Regex("""\btext="([^"]*)"""").find(m.value)?.groupValues?.get(1).orEmpty()
            if (text.isNotBlank() && rid !in setOf("tv_nav_home", "tv_nav_profile", "tv_nav_help")) seen += "$rid='${text.take(34)}'"
        }
        println("EXPLORE [$label] ${seen.take(40).joinToString(" | ")}")
        println("EXPLORE [$label] ids: ${Regex("""resource-id="[^"]*:id/([^"]+)"""").findAll(d.pageSource).map { it.groupValues[1] }.toSet().joinToString(",")}")
    }

    @Test
    @DisplayName("[EXPLORE-008] Requests queue: card, assign to self, sort, search")
    fun capture(rides: RideFixture) {
        val creds = Credentials.fromEnvironment(); assumeTrue(creds != null, "QA driver credentials not set")
        val ride = rides.queuedRide(RideSpec(partySize = 5, ada = true, note = "Hello", seed = 91, pickupName = "Terminal 5E", dropoffName = "Door 3 - Bus/Shuttle Ctr"))
        LoginFlow(creds!!).loginToHome()
        OdHomeScreen().waitForEmptyState(Duration.ofSeconds(40))
        val requests = RideRequestsScreen().also { it.openFromHome() }
        requests.open(RequestsTab.REQUESTS)
        tap(id("tv_sort")); Thread.sleep(1_000)
        runCatching { d.findElement(By.xpath("//*[@text='Newest First']")).click() }; Thread.sleep(2_500)
        val card = requests.card(ride.riderName, Duration.ofSeconds(30))
        println("EXPLORE card-found: ${card != null}")
        snap("queue-with-ride")

        card?.let { runCatching { d.findElement(By.xpath("//*[@resource-id='${Ids.of("passenger_name")}' and contains(@text,'${ride.riderName.takeLast(13)}')]/ancestor::*[.//*[@resource-id='${Ids.of("add_to_trip")}']][1]//*[@resource-id='${Ids.of("add_to_trip")}']")).click() } }
        snap("assign-to-self-staged")
        println("EXPLORE bar-ids: ${Regex("""resource-id="[^"]*:id/([^"]+)"""").findAll(d.pageSource).map { it.groupValues[1] }.filter { it.startsWith("btn") || it.contains("confirm") || it.contains("cancel") }.toSet()}")
        println("EXPLORE tap-bar-cancel: ${tap(By.xpath("//*[@resource-id='${Ids.of("btn_container")}']//*[@text='CANCEL' or @text='Cancel']"))}")
        snap("after-bar-cancel")
        card?.let { runCatching { d.findElement(By.xpath("//*[@resource-id='${Ids.of("passenger_name")}' and contains(@text,'${ride.riderName.takeLast(13)}')]/ancestor::*[.//*[@resource-id='${Ids.of("add_to_trip")}']][1]//*[@resource-id='${Ids.of("add_to_trip")}']")).click() } }
        snap("assign-to-self-staged-again")
        println("EXPLORE tap-confirm: ${tap(id("btn_confirm"))}")
        snap("after-confirm", 5_000)
    }
}
