package tms.driver.core.driver

import io.appium.java_client.android.AndroidDriver
import tms.driver.core.config.EnvConfig

/** Thread-local holder so screen objects and the JUnit extension share one session per test thread. */
object DriverSession {
    private val driverHolder = ThreadLocal<AndroidDriver?>()
    private val configHolder = ThreadLocal<EnvConfig?>()

    val driver: AndroidDriver
        get() = driverHolder.get() ?: error("No active driver session on this thread")

    val config: EnvConfig
        get() = configHolder.get() ?: error("No active configuration on this thread")

    val isActive: Boolean get() = driverHolder.get() != null

    fun start(config: EnvConfig): AndroidDriver {
        check(!isActive) { "A driver session is already active on this thread" }
        val driver = DriverFactory.create(config)
        driverHolder.set(driver)
        configHolder.set(config)
        return driver
    }

    fun stop() {
        try {
            driverHolder.get()?.quit()
        } finally {
            driverHolder.remove()
            configHolder.remove()
        }
    }
}
