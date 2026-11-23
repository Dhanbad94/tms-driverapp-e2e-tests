package tms.driver.explore

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import tms.driver.base.DriverExtension
import tms.driver.core.config.Credentials
import tms.driver.flows.LoginFlow
import tms.driver.screens.home.OdHomeScreen
import java.time.Duration

/** Developer tool (tag "explore"): signs the QA driver in and keeps the app on Home for a minute, so the server sees the driver active. */
@Tag("explore")
@Tag("device")
@ExtendWith(DriverExtension::class)
@DisplayName("Explore: keep the QA driver active for a minute")
class ExploreOnlineTest {
    @Test
    @DisplayName("[EXPLORE-022] Driver active for a minute")
    fun stayActive() {
        val creds = Credentials.fromEnvironment(); assumeTrue(creds != null, "QA driver credentials not set")
        LoginFlow(creds!!).loginToHome()
        OdHomeScreen().waitForEmptyState(Duration.ofSeconds(40))
        Thread.sleep(75_000)
    }
}
