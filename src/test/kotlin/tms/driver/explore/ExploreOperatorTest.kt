package tms.driver.explore

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.extension.ExtendWith
import tms.driver.backend.DispatchAction
import tms.driver.backend.DriverApiClient
import tms.driver.backend.DriverApiConfig
import tms.driver.fixtures.RideFixture
import tms.driver.fixtures.RideSpec
import tms.driver.fixtures.TripFixtureExtension

/**
 * Developer tool (tag "explore", no device): what the SECOND driver's account may do to a ride assigned to the QA driver:
 * cancel it, complete it, take it over. Each probe uses its own test ride; the fixture removes whatever is left.
 */
@Tag("explore")
@EnabledIfEnvironmentVariable(named = "TMS_TRIP_API_URL", matches = ".+")
@ExtendWith(TripFixtureExtension::class)
@DisplayName("Explore: operator actions of the second driver")
class ExploreOperatorTest {
    private fun spec(seed: Long) = RideSpec(seed = seed, pickupName = "Terminal 5E", dropoffName = "Door 3 - Bus/Shuttle Ctr")

    @Test
    @DisplayName("[EXPLORE-009] Second driver cancels, completes and takes over rides of the QA driver")
    fun probe(rides: RideFixture) {
        val cfg = DriverApiConfig.forSecondDriverFromEnvironment(); assumeTrue(cfg != null, "second driver not configured")
        val second = DriverApiClient.login(cfg!!)
        val secondId = second.driverId()
        println("EXPLORE second-driver-id=$secondId")

        // The same three actions, now on rides whose trip has already been started by the QA driver.
        val a = rides.assignedRide(spec(121)); rides.startTripByApi()
        println("EXPLORE started-cancel-by-second: " + second.dispatchAction(a.dispatchId, DispatchAction.CANCEL, "E2E operator probe"))
        val b = rides.assignedRide(spec(122)); rides.startTripByApi()
        println("EXPLORE started-complete-by-second: " + second.dispatchAction(b.dispatchId, DispatchAction.COMPLETE, "E2E operator probe"))
        val c = rides.assignedRide(spec(123)); rides.startTripByApi()
        println("EXPLORE started-change-driver-by-second: " + second.changeDriver(c.dispatchId, secondId))
        println("EXPLORE second-active-rides: " + second.myActiveRides().map { it.name })
        second.myActiveRides().filter { it.name.equals(c.riderName, ignoreCase = true) }.forEach { println("EXPLORE cleanup-c: " + second.cancelRide(it.id, "E2E teardown")) }
    }
}
