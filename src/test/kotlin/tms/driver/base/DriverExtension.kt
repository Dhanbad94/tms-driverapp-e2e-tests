package tms.driver.base

import io.qameta.allure.Allure
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.AfterTestExecutionCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.slf4j.LoggerFactory
import tms.driver.core.config.EnvConfig
import tms.driver.core.device.Adb
import tms.driver.core.driver.DriverSession
import tms.driver.core.report.Artifacts

/**
 * Per-test Appium session lifecycle plus failure diagnostics.
 * Start the session before each test, capture screenshot/page source/logcat if the test failed
 * (and a screenshot on success as run evidence), then always quit the session.
 */
class DriverExtension : BeforeEachCallback, AfterTestExecutionCallback, AfterEachCallback {
    private val log = LoggerFactory.getLogger(DriverExtension::class.java)

    /**
     * Staging refuses to assign a ride (answers 500 "Something went wrong") to a driver whose position is outside the organisation's region,
     * which is Chicago. The emulator starts in Mountain View, so every test puts it in downtown Chicago first: inside the region and about
     * 25 km from the O'Hare stops, so no ride arrives by itself. Override with TMS_DEVICE_LATITUDE / TMS_DEVICE_LONGITUDE. Real devices
     * have no emulator console, so the call just fails there and is ignored.
     */
    private fun putEmulatorInRegion(config: EnvConfig) {
        if (config.udid != null && !config.udid.startsWith("emulator")) return
        val lat = System.getenv("TMS_DEVICE_LATITUDE")?.toDoubleOrNull() ?: 41.8781
        val lon = System.getenv("TMS_DEVICE_LONGITUDE")?.toDoubleOrNull() ?: -87.6298
        runCatching { Adb(config.udid).setEmulatorLocation(lat, lon) }.onFailure { log.debug("Could not set the emulator location: {}", it.message) }
    }

    override fun beforeEach(context: ExtensionContext) {
        val config = EnvConfig.load()
        putEmulatorInRegion(config)
        Allure.parameter("flavor", config.flavor)
        Allure.parameter("appPackage", config.appPackage)
        DriverSession.start(config)
    }

    override fun afterTestExecution(context: ExtensionContext) {
        if (!DriverSession.isActive) return
        val failed = context.executionException.isPresent
        val name = context.requiredTestClass.simpleName + "." + context.requiredTestMethod.name
        val dir = DriverSession.config.artifactsDir.resolve(name)
        val artifacts = Artifacts(DriverSession.driver, dir)
        if (failed) {
            log.warn("Test failed, capturing diagnostics for {}", name)
            artifacts.captureAll("failure")
        } else {
            artifacts.screenshot("final-state")
        }
    }

    override fun afterEach(context: ExtensionContext) {
        DriverSession.stop()
    }
}
