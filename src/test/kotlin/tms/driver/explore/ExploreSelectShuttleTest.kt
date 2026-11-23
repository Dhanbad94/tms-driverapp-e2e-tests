package tms.driver.explore

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
import tms.driver.screens.home.OdHomeScreen
import tms.driver.screens.shuttle.SelectShuttleScreen
import java.time.Duration

/**
 * Developer tool (tag "explore"): a fresh login, then Shuttle-1 is chosen for the QA driver through Select Shuttle and confirmed.
 * Used to put the QA driver on its shuttle again after the shuttles were unassigned. Only Shuttle-1 is touched.
 */
@Tag("explore")
@Tag("device")
@ExtendWith(DriverExtension::class)
@DisplayName("Explore: select Shuttle-1 for the QA driver")
class ExploreSelectShuttleTest {
    @Test
    @DisplayName("[EXPLORE-026] Select Shuttle-1")
    fun select() {
        val creds = Credentials.fromEnvironment(); assumeTrue(creds != null, "QA driver credentials not set")
        LoginFlow(creds!!).loginToHome()
        val home = OdHomeScreen()
        home.waitForEmptyState(Duration.ofSeconds(40))
        val shots = Artifacts(DriverSession.driver, DriverSession.config.artifactsDir.resolve("ExploreSelectShuttle"))
        println("EXPLORE home-shuttle-before: '${home.shuttleText()}'")
        shots.screenshot("home-before")
        home.tapShuttle()
        val list = SelectShuttleScreen().waitUntilShown()
        println("EXPLORE rows: ${list.rows()}")
        println("EXPLORE confirm: '${list.confirmText()}' enabled=${list.isConfirmEnabled()}")
        list.tapRow("Automated Shuttle-1")
        Thread.sleep(2_000)
        shots.screenshot("row-tapped")
        println("EXPLORE rows-after-tap: ${list.rows()}")
        list.confirm()
        Thread.sleep(4_000)
        shots.screenshot("after-confirm")
        println("EXPLORE home-shuttle-after: '${runCatching { home.shuttleText() }.getOrNull()}'")
    }
}
