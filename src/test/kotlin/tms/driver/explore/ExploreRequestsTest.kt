package tms.driver.explore

import io.appium.java_client.AppiumBy
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import tms.driver.base.DriverExtension
import tms.driver.core.config.Credentials
import tms.driver.core.driver.DriverSession
import tms.driver.core.report.Artifacts
import tms.driver.flows.LoginFlow
import tms.driver.screens.Ids

/**
 * Developer tool (tag "explore"): READ-ONLY tour of the Requests screen (all four tabs) and the shuttle picker,
 * saving a screenshot and UI dump of each. It taps only tabs and the back arrow; nothing is assigned or changed.
 */
@Tag("explore")
@Tag("device")
@ExtendWith(DriverExtension::class)
@DisplayName("Explore: Requests tabs and shuttle picker (read-only)")
class ExploreRequestsTest {
    @Test
    @DisplayName("[EXPLORE-002] Capture the Requests tabs and the shuttle picker")
    fun capture() {
        val creds = Credentials.fromEnvironment()
        assumeTrue(creds != null, "QA driver credentials not set")
        LoginFlow(creds!!).loginToHome()
        Thread.sleep(4_000)
        val driver = DriverSession.driver
        val shots = Artifacts(driver, DriverSession.config.artifactsDir.resolve("ExploreRequestsTest"))

        fun tap(name: String): Boolean = runCatching { driver.findElement(Ids.by(name)).click(); true }.getOrDefault(false)
        fun snap(label: String) { Thread.sleep(2_500); shots.screenshot(label); shots.pageSource(label) }

        println("EXPLORE open-requests: ${tap("fl_home_ride_requests")}")
        snap("requests-tab")
        for (tab in listOf("assigned", "inprogress", "completed", "requests")) {
            println("EXPLORE tab-$tab: ${tap(tab)}")
            snap("tab-$tab")
        }
        // Back to home (floating home button on the pager, else the system back)
        if (!tap("fab_home")) driver.navigate().back()
        Thread.sleep(2_000)
        println("EXPLORE open-shuttle-picker: ${tap("tv_shuttle")}")
        snap("shuttle-picker")
        driver.navigate().back()
        snap("home-after")
    }
}
