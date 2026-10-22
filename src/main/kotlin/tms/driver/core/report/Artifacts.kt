package tms.driver.core.report

import io.appium.java_client.android.AndroidDriver
import io.qameta.allure.Allure
import org.openqa.selenium.OutputType
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path

/**
 * Failure/evidence capture: screenshot, UI hierarchy and logcat, written to disk and attached to Allure.
 * Each capture is independent and never throws, so diagnostics can't mask the original failure.
 */
class Artifacts(private val driver: AndroidDriver, private val outputDir: Path) {
    private val log = LoggerFactory.getLogger(Artifacts::class.java)

    fun screenshot(name: String): Path? = runCatching {
        val bytes = driver.getScreenshotAs(OutputType.BYTES)
        val file = write("$name.png", bytes)
        Allure.addAttachment(name, "image/png", ByteArrayInputStream(bytes), "png")
        file
    }.onFailure { log.warn("screenshot '{}' failed: {}", name, it.message) }.getOrNull()

    fun pageSource(name: String): Path? = runCatching {
        val xml = driver.pageSource
        val file = write("$name.xml", xml.toByteArray())
        Allure.addAttachment(name, "text/xml", xml, "xml")
        file
    }.onFailure { log.warn("page source '{}' failed: {}", name, it.message) }.getOrNull()

    fun logcat(name: String): Path? = runCatching {
        val text = driver.manage().logs().get("logcat").all.joinToString("\n") { it.message }
        val file = write("$name.log", text.toByteArray())
        Allure.addAttachment(name, "text/plain", text, "log")
        file
    }.onFailure { log.warn("logcat '{}' failed: {}", name, it.message) }.getOrNull()

    fun captureAll(prefix: String) {
        screenshot("$prefix-screenshot")
        pageSource("$prefix-pagesource")
        logcat("$prefix-logcat")
    }

    private fun write(fileName: String, bytes: ByteArray): Path {
        Files.createDirectories(outputDir)
        return Files.write(outputDir.resolve(fileName), bytes)
    }
}
