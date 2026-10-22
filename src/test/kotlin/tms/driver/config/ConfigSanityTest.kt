package tms.driver.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tms.driver.core.config.EnvConfig
import tms.driver.screens.Ids
import java.nio.file.Files
import java.nio.file.Path

/** Runs without a device or Appium server (tag "config"). */
@Tag("config")
class ConfigSanityTest {

    @Test
    fun `staging resolves the suffixed package and splash activity`() {
        val cfg = EnvConfig.load("staging")
        assertEquals("com.trackmyshuttle.driver.staging", cfg.appPackage)
        assertEquals("com.trackmyshuttle.driver.ui.splash.SplashActivity", cfg.appActivity)
    }

    @Test
    fun `development resolves its package`() {
        assertEquals("com.trackmyshuttle.driver.development", EnvConfig.load("development").appPackage)
    }

    @Test
    fun `production flavor is rejected`(@TempDir dir: Path) {
        Files.writeString(
            dir.resolve("prod.properties"),
            "flavor=production\nappActivity=x.y.Z\n",
        )
        val ex = assertThrows(IllegalArgumentException::class.java) { EnvConfig.load("prod", dir) }
        assertTrue(ex.message!!.contains("production"))
    }

    @Test
    fun `unknown environment file fails clearly`() {
        assertThrows(IllegalArgumentException::class.java) { EnvConfig.load("does-not-exist") }
    }

    @Test
    fun `resource ids use the flavor application id`() {
        assertEquals(
            "com.trackmyshuttle.driver.staging:id/btn_submit",
            Ids.of("btn_submit", "com.trackmyshuttle.driver.staging"),
        )
    }
}
