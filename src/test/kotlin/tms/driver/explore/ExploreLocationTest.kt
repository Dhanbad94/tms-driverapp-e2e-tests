package tms.driver.explore

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.extension.ExtendWith
import tms.driver.backend.RideTestData
import tms.driver.base.DriverExtension
import tms.driver.core.config.Credentials
import tms.driver.core.device.Adb
import tms.driver.core.driver.DriverSession
import tms.driver.fixtures.RideFixture
import tms.driver.fixtures.RideSpec
import tms.driver.fixtures.TripFixtureExtension
import tms.driver.flows.LoginFlow
import tms.driver.screens.home.OdHomeScreen
import tms.driver.screens.trip.ArrivedScreen
import tms.driver.screens.trip.ConfirmShuttleDialog
import tms.driver.screens.trip.TripViewScreen
import java.time.Duration

/**
 * Developer tool (tag "explore"): what moving the emulator's GPS next to the pickup does. Records the estimated time every few
 * seconds, whether the arrived screen opens by itself, the location service and its notification, and location lines in logcat.
 * The emulator's location is put back far away at the end.
 */
@Tag("explore")
@Tag("device")
@EnabledIfEnvironmentVariable(named = "TMS_TRIP_API_URL", matches = ".+")
@ExtendWith(DriverExtension::class, TripFixtureExtension::class)
@DisplayName("Explore: location, auto-arrival, location service")
class ExploreLocationTest {
    @Test
    @DisplayName("[EXPLORE-013] Mock location near the pickup")
    fun capture(rides: RideFixture) {
        val creds = Credentials.fromEnvironment(); assumeTrue(creds != null, "QA driver credentials not set")
        val pickup = RideTestData.stops().first { it.name == "Terminal 5E" }
        rides.assignedRide(RideSpec(partySize = 2, ada = false, note = "Hello", seed = 191, pickupName = "Terminal 5E", dropoffName = "Door 3 - Bus/Shuttle Ctr"))
        val adb = Adb()
        val pkg = DriverSession.config.appPackage
        LoginFlow(creds!!).loginToHome()
        val home = OdHomeScreen(); home.waitForTrip(Duration.ofSeconds(60))
        home.tapStartTrip(); ConfirmShuttleDialog().waitUntilShown().confirm()
        val trip = TripViewScreen().waitUntilShown().also { it.waitForEstimatedTimes() }
        println("EXPLORE eta-start: ${trip.estimatedTimes()}")

        println("EXPLORE services: " + adb.run("shell", "dumpsys", "activity", "services", pkg).output.lines().filter { it.contains("ServiceRecord") || it.contains("isForeground") || it.contains("foregroundId") }.take(8))
        println("EXPLORE notifications: " + adb.run("shell", "dumpsys", "notification", "--noredact").output.lines().filter { it.contains("pkg=$pkg") }.take(4))

        try {
            println("EXPLORE geo-fix-pickup: " + adb.setEmulatorLocation(pickup.latitude, pickup.longitude))
            val start = System.nanoTime()
            val arrived = ArrivedScreen()
            repeat(24) {
                Thread.sleep(5_000)
                val secs = Duration.ofNanos(System.nanoTime() - start).seconds
                val onArrived = arrived.isShown(Duration.ofSeconds(1))
                val etas = if (onArrived) emptyList() else runCatching { trip.estimatedTimes() }.getOrDefault(emptyList())
                println("EXPLORE t=${secs}s arrivedScreen=$onArrived eta=$etas")
                if (onArrived) return@repeat
            }
            println("EXPLORE logcat-location: " + adb.run("logcat", "-d", "-t", "600").output.lines().filter { it.contains("ocation", ignoreCase = true) && it.contains("driver", ignoreCase = true) }.takeLast(6).map { it.take(160) })
        } finally {
            adb.setEmulatorLocation(37.4220, -122.0841)
        }
    }
}
