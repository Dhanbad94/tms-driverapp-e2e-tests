package tms.driver.fixtures

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import tms.driver.backend.DriverApiConfig
import tms.driver.backend.RiderApiConfig

/**
 * Gives a test a [RideFixture] (declare it as a test method parameter) and guarantees cleanup afterwards.
 * Register it AFTER DriverExtension so cleanup runs before the Appium session is closed.
 * Tests are skipped (not failed) when the Rider or Driver API settings are not present.
 */
class TripFixtureExtension : ParameterResolver, AfterEachCallback {
    private val ns = ExtensionContext.Namespace.create(TripFixtureExtension::class.java)

    override fun supportsParameter(p: ParameterContext, e: ExtensionContext) = p.parameter.type == RideFixture::class.java

    override fun resolveParameter(p: ParameterContext, e: ExtensionContext): Any {
        val driver = DriverApiConfig.fromEnvironment()
        val rider = RiderApiConfig.fromEnvironment()
        assumeTrue(driver != null, "Driver API settings are not set, so this test is skipped")
        assumeTrue(rider != null, "Rider API settings are not set, so this test is skipped")
        return e.getStore(ns).getOrComputeIfAbsent("fixture", { RideFixture(driver!!, rider!!).begin() }, RideFixture::class.java)
    }

    override fun afterEach(context: ExtensionContext) {
        context.getStore(ns).remove("fixture", RideFixture::class.java)?.finish()
    }
}
