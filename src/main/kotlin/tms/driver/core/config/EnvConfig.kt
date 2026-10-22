package tms.driver.core.config

import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.util.Properties

/**
 * Immutable test-run configuration.
 *
 * Resolution order for every setting: environment variable > JVM system property (`tms.<key>`)
 * > `config/environments/<env>.properties`. Only non-secret values live in the properties files.
 *
 * The app's backend URLs are baked into each flavor at build time, so "environment" here means
 * "which flavor's APK / package is under test".
 */
data class EnvConfig(
    val envName: String,
    val flavor: String,
    val appPackage: String,
    val appActivity: String,
    val apkPath: Path?,
    val appiumUrl: URL,
    val deviceName: String?,
    val udid: String?,
    val platformVersion: String?,
    val resetApp: Boolean,
    val autoGrantPermissions: Boolean,
    val defaultTimeout: Duration,
    val launchTimeout: Duration,
    val artifactsDir: Path,
) {
    companion object {
        /** R-class namespace of the app; resource-ids use this on every flavor (see readiness doc). */
        const val RESOURCE_NAMESPACE = "com.trackmyshuttle.driver"

        /** Flavor -> applicationId suffix, from app/build.gradle productFlavors. */
        private val FLAVOR_SUFFIX = mapOf(
            "development" to ".development",
            "staging" to ".staging",
            "pre_production" to ".pre_prod",
        )

        fun resolveEnvName(): String =
            System.getenv("TMS_ENV")?.takeIf { it.isNotBlank() }
                ?: System.getProperty("tms.env")?.takeIf { it.isNotBlank() }
                ?: "staging"

        fun load(envName: String = resolveEnvName(), configRoot: Path = Paths.get("config", "environments")): EnvConfig {
            require(envName.matches(Regex("[a-z0-9_-]+"))) { "Invalid environment name: '$envName'" }
            val file = configRoot.resolve("$envName.properties")
            require(Files.isRegularFile(file)) { "Environment file not found: $file" }
            val props = Properties().apply { Files.newBufferedReader(file).use { load(it) } }

            fun setting(key: String, envVar: String): String? =
                System.getenv(envVar)?.takeIf { it.isNotBlank() }
                    ?: System.getProperty("tms.$key")?.takeIf { it.isNotBlank() }
                    ?: props.getProperty(key)?.takeIf { it.isNotBlank() }

            val flavor = requireNotNull(setting("flavor", "TMS_FLAVOR")) { "'flavor' is not set in $file" }
            require(flavor != "production") {
                "Refusing to run against the 'production' flavor. Phase A tests must use development or staging."
            }
            val suffix = FLAVOR_SUFFIX[flavor]
                ?: error("Unknown flavor '$flavor'. Known: ${FLAVOR_SUFFIX.keys}")

            val appPackage = RESOURCE_NAMESPACE + suffix
            val apk = setting("apkPath", "TMS_APK_PATH")?.let { Paths.get(it) }

            return EnvConfig(
                envName = envName,
                flavor = flavor,
                appPackage = appPackage,
                appActivity = requireNotNull(setting("appActivity", "TMS_APP_ACTIVITY")) { "'appActivity' missing" },
                apkPath = apk,
                appiumUrl = URL(setting("appiumUrl", "TMS_APPIUM_URL") ?: "http://127.0.0.1:4723"),
                deviceName = setting("deviceName", "TMS_DEVICE_NAME"),
                udid = setting("udid", "TMS_UDID"),
                platformVersion = setting("platformVersion", "TMS_PLATFORM_VERSION"),
                resetApp = (setting("resetApp", "TMS_RESET_APP") ?: "true").toBoolean(),
                autoGrantPermissions = (setting("autoGrantPermissions", "TMS_AUTO_GRANT") ?: "true").toBoolean(),
                defaultTimeout = Duration.ofSeconds((setting("defaultTimeoutSeconds", "TMS_TIMEOUT_SECONDS") ?: "20").toLong()),
                launchTimeout = Duration.ofSeconds((setting("launchTimeoutSeconds", "TMS_LAUNCH_TIMEOUT_SECONDS") ?: "30").toLong()),
                artifactsDir = Paths.get(setting("artifactsDir", "TMS_ARTIFACTS_DIR") ?: "build/artifacts"),
            )
        }
    }
}
