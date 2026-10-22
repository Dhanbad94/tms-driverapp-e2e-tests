package tms.driver.base

import io.qameta.allure.Allure
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.AfterTestExecutionCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.slf4j.LoggerFactory
import tms.driver.core.config.EnvConfig
import tms.driver.core.driver.DriverSession
import tms.driver.core.report.Artifacts

/**
 * Per-test Appium session lifecycle plus failure diagnostics.
 * Start the session before each test, capture screenshot/page source/logcat if the test failed
 * (and a screenshot on success as run evidence), then always quit the session.
 */
class DriverExtension : BeforeEachCallback, AfterTestExecutionCallback, AfterEachCallback {
    private val log = LoggerFactory.getLogger(DriverExtension::class.java)

    override fun beforeEach(context: ExtensionContext) {
        val config = EnvConfig.load()
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
