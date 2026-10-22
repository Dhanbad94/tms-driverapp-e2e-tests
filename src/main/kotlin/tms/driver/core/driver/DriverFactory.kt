package tms.driver.core.driver

import io.appium.java_client.android.AndroidDriver
import io.appium.java_client.android.options.UiAutomator2Options
import org.slf4j.LoggerFactory
import tms.driver.core.config.EnvConfig
import java.nio.file.Files
import java.time.Duration

/** Builds [AndroidDriver] sessions (UiAutomator2) from an [EnvConfig]. */
object DriverFactory {
    private val log = LoggerFactory.getLogger(DriverFactory::class.java)

    fun buildOptions(config: EnvConfig): UiAutomator2Options {
        val options = UiAutomator2Options()
            .setAppPackage(config.appPackage)
            .setAppActivity(config.appActivity)
            .setAppWaitActivity("*")
            .setAppWaitDuration(config.launchTimeout)
            .setAutoGrantPermissions(config.autoGrantPermissions)
            .setNoReset(!config.resetApp)
            .setNewCommandTimeout(Duration.ofSeconds(120))
            .setDisableWindowAnimation(true)

        config.apkPath?.let { apk ->
            require(Files.isRegularFile(apk)) { "APK not found at configured path: $apk" }
            options.setApp(apk.toAbsolutePath().toString())
        }
        config.deviceName?.let { options.setDeviceName(it) }
        config.udid?.let { options.setUdid(it) }
        config.platformVersion?.let { options.setPlatformVersion(it) }
        return options
    }

    fun create(config: EnvConfig): AndroidDriver {
        log.info(
            "Starting session: package={} flavor={} server={} apk={}",
            config.appPackage, config.flavor, config.appiumUrl, config.apkPath ?: "(pre-installed)",
        )
        val driver = AndroidDriver(config.appiumUrl, buildOptions(config))
        return driver
    }
}
