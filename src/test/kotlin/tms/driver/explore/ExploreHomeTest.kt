package tms.driver.explore

import io.qameta.allure.Allure
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import tms.driver.backend.DriverApiClient
import tms.driver.backend.DriverApiConfig
import tms.driver.base.DriverExtension
import tms.driver.core.config.Credentials
import tms.driver.core.driver.DriverSession
import tms.driver.core.report.Artifacts
import tms.driver.flows.LoginFlow
import tms.driver.screens.auth.LoginScreen
import tms.driver.screens.shell.HomeShell

/**
 * Developer tool (tag "explore", not in any suite): saves UI dumps of the home screen while the QA driver has a trip,
 * then logs the same driver in through the Driver API and records whether the app stays signed in.
 * Nothing is asserted; read build/artifacts/ExploreHomeTest.* and the printed lines.
 */
@Tag("explore")
@Tag("device")
@ExtendWith(DriverExtension::class)
@DisplayName("Explore: home screen with a trip, and what an API login does to the app session")
class ExploreHomeTest {
    @Test
    @DisplayName("[EXPLORE-001] Capture the home screen and the effect of an API login")
    fun capture() {
        val creds = Credentials.fromEnvironment()
        assumeTrue(creds != null, "QA driver credentials not set")
        LoginFlow(creds!!).loginToHome()
        Thread.sleep(6_000) // let the dashboard load
        val dir = DriverSession.config.artifactsDir.resolve("ExploreHomeTest")
        val shots = Artifacts(DriverSession.driver, dir)
        shots.screenshot("home-with-trip"); shots.pageSource("home-with-trip")

        val api = DriverApiConfig.fromEnvironment()
        assumeTrue(api != null, "Driver API settings not set")
        val client = DriverApiClient.login(api!!)
        println("EXPLORE api-login-as-qa-driver: ok, driver_id=${client.driverId()}, trip_started=${client.tripStarted()}")
        Thread.sleep(15_000)
        val stillHome = HomeShell().isShown()
        val onLogin = LoginScreen().isShown()
        println("EXPLORE app-after-api-login: stillOnHome=$stillHome onLogin=$onLogin")
        Allure.addAttachment("app-after-api-login", "text/plain", "stillOnHome=$stillHome onLogin=$onLogin")
        shots.screenshot("after-api-login"); shots.pageSource("after-api-login")
    }
}
