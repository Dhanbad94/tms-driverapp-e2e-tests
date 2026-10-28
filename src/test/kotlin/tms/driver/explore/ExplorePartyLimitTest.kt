package tms.driver.explore

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.extension.ExtendWith
import tms.driver.fixtures.RideFixture
import tms.driver.fixtures.RideSpec
import tms.driver.fixtures.TripFixtureExtension

/** Developer tool (tag "explore", no device): which party sizes the Rider API accepts. Every ride that gets created is cancelled at once. */
@Tag("explore")
@EnabledIfEnvironmentVariable(named = "TMS_RIDER_API_URL", matches = ".+")
@ExtendWith(TripFixtureExtension::class)
@DisplayName("Explore: largest party the Rider API accepts")
class ExplorePartyLimitTest {
    @Test
    @DisplayName("[EXPLORE-012] Party size limit")
    fun probe(rides: RideFixture) {
        // 50 was accepted and 99 refused in an earlier probe: search the exact limit.
        var ok = 50; var refused = 99
        while (refused - ok > 1) {
            val size = (ok + refused) / 2
            val result = runCatching { rides.liveRide(RideSpec(partySize = size, seed = 300L + size, pickupName = "Terminal 5E", dropoffName = "Door 3 - Bus/Shuttle Ctr")) }
            result.onSuccess { ride -> ok = size; println("EXPLORE party=$size -> created"); runCatching { rides.riderCancels(ride) } }
                .onFailure { refused = size; println("EXPLORE party=$size -> refused: ${it.message}") }
        }
        println("EXPLORE largest-accepted=$ok")
    }
}
