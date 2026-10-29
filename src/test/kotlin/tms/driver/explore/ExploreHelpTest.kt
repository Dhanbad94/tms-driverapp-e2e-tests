package tms.driver.explore

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.openqa.selenium.By
import tms.driver.base.DriverExtension
import tms.driver.core.config.Credentials
import tms.driver.core.driver.DriverSession
import tms.driver.core.report.Artifacts
import tms.driver.flows.LoginFlow
import tms.driver.screens.Ids
import tms.driver.screens.home.OdHomeScreen
import tms.driver.screens.shell.HomeShell
import java.time.Duration

/**
 * Developer tool (tag "explore"): the Help Center (a Compose screen), the Support screen, the quick tour overlay and the
 * walkthrough. Nothing is changed; the quick tour and walkthrough are only stepped through and left again.
 */
@Tag("explore")
@Tag("device")
@ExtendWith(DriverExtension::class)
@DisplayName("Explore: Help Center, Support, quick tour, walkthrough (read-only)")
class ExploreHelpTest {
    private val d get() = DriverSession.driver
    private val shots by lazy { Artifacts(d, DriverSession.config.artifactsDir.resolve("ExploreHelp")) }
    private fun tapText(text: String): Boolean = runCatching { d.findElement(By.xpath("//*[@text='$text']")).click(); true }.getOrDefault(false)
    private fun tapId(n: String): Boolean = runCatching { d.findElement(Ids.by(n)).click(); true }.getOrDefault(false)

    private fun snap(label: String, waitMs: Long = 2_500) {
        Thread.sleep(waitMs); shots.screenshot(label); shots.pageSource(label)
        val src = d.pageSource
        val texts = Regex("""<[^>]*\btext="([^"]+)"[^>]*>""").findAll(src).map { m ->
            val clickable = Regex("""clickable="(true|false)"""").find(m.value)?.groupValues?.get(1)
            "'${m.groupValues[1].take(60)}'${if (clickable == "true") "*" else ""}"
        }.toList()
        val descs = Regex("""content-desc="([^"]+)"""").findAll(src).map { it.groupValues[1].take(40) }.toList()
        val ids = Regex("""resource-id="[^"]*:id/([^"]+)"""").findAll(src).map { it.groupValues[1] }.toSet() - setOf("action_bar_root", "content")
        println("EXPLORE [$label] pkg=${d.currentPackage} texts=${texts.take(30)}")
        println("EXPLORE [$label] descs=$descs ids=${ids.take(25)}")
    }

    @Test
    @DisplayName("[EXPLORE-015] Help Center, Support, quick tour, walkthrough")
    fun capture() {
        val creds = Credentials.fromEnvironment(); assumeTrue(creds != null, "QA driver credentials not set")
        LoginFlow(creds!!).loginToHome()
        OdHomeScreen().waitForEmptyState(Duration.ofSeconds(40))
        HomeShell().open(HomeShell.Tab.HELP)
        snap("help-center", 4_000)

        println("EXPLORE tap-call-support: ${tapText("Call Support")}")
        snap("support", 3_000)
        d.navigate().back(); Thread.sleep(2_000)
        snap("help-after-support")

        println("EXPLORE tap-quick-tour: ${tapText("Launch Quick Tour")}")
        snap("quick-tour-1", 3_000)
        var steps = 1
        repeat(25) { i ->
            d.findElements(Ids.by("iv_intro_help")).firstOrNull()?.takeIf { it.isDisplayed } ?: return@repeat
            runCatching { d.findElement(Ids.by("iv_intro_help")).click() }
            steps++
            Thread.sleep(900)
            if (i % 5 == 4) snap("quick-tour-${steps}", 500)
        }
        println("EXPLORE quick-tour-steps-tapped: $steps; overlay-still-shown=${d.findElements(Ids.by("iv_intro_help")).any { runCatching { it.isDisplayed }.getOrDefault(false) }}")
        snap("after-quick-tour", 2_000)

        // back to the Help tab if the tour left us on Home
        if (d.findElements(By.xpath("//*[@text='Animation Tour']")).isEmpty()) HomeShell().open(HomeShell.Tab.HELP)
        Thread.sleep(2_000)
        println("EXPLORE tap-animation-tour: ${tapText("Animation Tour")}")
        snap("walkthrough-1", 4_000)
        repeat(12) { i ->
            if (!tapId("bt_next")) return@repeat
            Thread.sleep(1_200)
            if (i < 2 || i % 4 == 3) snap("walkthrough-next-${i + 1}", 300)
        }
        println("EXPLORE bt_next-state: ${d.findElements(Ids.by("bt_next")).firstOrNull()?.let { "text=${it.text} enabled=${it.isEnabled}" }}")
        println("EXPLORE bt_back-state: ${d.findElements(Ids.by("bt_back")).firstOrNull()?.let { "text=${it.text} enabled=${it.isEnabled}" }}")
        d.navigate().back(); Thread.sleep(1_500)
        snap("help-after-walkthrough")
    }
}
