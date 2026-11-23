package tms.driver.explore

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import tms.driver.base.DriverExtension
import tms.driver.core.config.Credentials
import tms.driver.core.driver.DriverSession
import tms.driver.flows.LoginFlow
import tms.driver.screens.Ids
import tms.driver.screens.home.OdHomeScreen
import tms.driver.screens.requests.RideRequestsScreen
import java.time.Duration

/** Developer tool (tag "explore"): types different strings into the search box and prints what the box holds, to find what adds " ft". */
@Tag("explore")
@Tag("device")
@ExtendWith(DriverExtension::class)
@DisplayName("Explore: search box text")
class ExploreSearchFieldTest {
    @Test
    @DisplayName("[EXPLORE-025] Search box text")
    fun capture() {
        val creds = Credentials.fromEnvironment(); assumeTrue(creds != null, "QA driver credentials not set")
        LoginFlow(creds!!).loginToHome()
        OdHomeScreen().waitForEmptyState(Duration.ofSeconds(40))
        val requests = RideRequestsScreen().also { it.openFromHome() }
        val search = requests.openSearch()
        val d = DriverSession.driver
        for (q in listOf("18926334", "179542041", "1795", "17954204", "abc", "12345678", "123456789", "Appium", "Appium_ride", "9999")) {
            val box = d.findElement(Ids.by("etv_search"))
            box.clear(); box.sendKeys(q); Thread.sleep(1_800)
            println("EXPLORE typed='$q' box='${d.findElement(Ids.by("etv_search")).text}' hint/message='${d.findElements(Ids.by("no_ride_requests")).firstOrNull()?.text}'")
        }
        search.close()
    }
}
