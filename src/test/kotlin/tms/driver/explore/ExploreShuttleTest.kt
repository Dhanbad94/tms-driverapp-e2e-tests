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
import java.time.Duration

/**
 * Developer tool (tag "explore"): the Home header, the Select Shuttle list, its search and the Rename Shuttle dialog.
 * Nothing is changed: the dialog is opened and cancelled, no shuttle is confirmed.
 */
@Tag("explore")
@Tag("device")
@ExtendWith(DriverExtension::class)
@DisplayName("Explore: Select Shuttle and Rename Shuttle (read-only)")
class ExploreShuttleTest {
    private val d get() = DriverSession.driver
    private val shots by lazy { Artifacts(d, DriverSession.config.artifactsDir.resolve("ExploreShuttle")) }
    private fun tap(by: By): Boolean = runCatching { d.findElement(by).click(); true }.getOrDefault(false)
    private fun id(n: String) = Ids.by(n)

    private fun snap(label: String, waitMs: Long = 2_000) {
        Thread.sleep(waitMs); shots.screenshot(label); shots.pageSource(label)
        val seen = LinkedHashSet<String>()
        Regex("""<[^>]*resource-id="[^"]*:id/([^"]+)"[^>]*>""").findAll(d.pageSource).forEach { m ->
            val rid = m.groupValues[1]
            val text = Regex("""\btext="([^"]*)"""").find(m.value)?.groupValues?.get(1).orEmpty()
            val en = Regex("""\benabled="(true|false)"""").find(m.value)?.groupValues?.get(1)
            if (rid !in setOf("tv_nav_home", "tv_nav_profile", "tv_nav_help") && (text.isNotBlank() || rid.startsWith("btn_"))) seen += "$rid${if (rid.startsWith("btn_")) "[en=$en]" else ""}${if (text.isNotBlank()) "='" + text.take(34) + "'" else ""}"
        }
        println("EXPLORE [$label] ${seen.take(60).joinToString(" | ")}")
        println("EXPLORE [$label] ids: ${Regex("""resource-id="[^"]*:id/([^"]+)"""").findAll(d.pageSource).map { it.groupValues[1] }.toSet().joinToString(",")}")
    }

    @Test
    @DisplayName("[EXPLORE-014] Select Shuttle and Rename Shuttle")
    fun capture() {
        val creds = Credentials.fromEnvironment(); assumeTrue(creds != null, "QA driver credentials not set")
        LoginFlow(creds!!).loginToHome()
        OdHomeScreen().waitForEmptyState(Duration.ofSeconds(40))
        snap("home")
        println("EXPLORE tap-shuttle: ${tap(id("tv_shuttle"))}")
        snap("select-shuttle", 3_000)
        // search
        println("EXPLORE tap-search: ${tap(id("iv_search"))}")
        snap("search-open")
        runCatching { d.findElement(By.className("android.widget.EditText")).sendKeys("2") }
        snap("search-2")
        runCatching { d.findElement(By.className("android.widget.EditText")).clear(); d.findElement(By.className("android.widget.EditText")).sendKeys("zzqq") }
        snap("search-nomatch")
        println("EXPLORE close-search: ${tap(id("atv_cancel_search_view"))}")
        Thread.sleep(1_000)
        snap("after-search-closed")
        // rename dialog: open and cancel
        println("EXPLORE tap-edit: ${tap(id("aiv_edit"))}")
        snap("rename-dialog", 2_500)
        println("EXPLORE tap-cancel: ${tap(id("btn_cancel"))}")
        snap("after-rename-cancel")
        // pick a row but do not confirm
        println("EXPLORE tap-row-shuttle2: ${runCatching { d.findElement(By.xpath("//*[@resource-id='${Ids.of("atv_name")}' and @text='Automated Shuttle-2']")).click(); true }.getOrDefault(false)}")
        snap("row-selected", 3_000)
        println("EXPLORE tap-confirm-with-shuttle2: ${tap(id("btn_apply"))}")
        snap("after-confirm-shuttle2", 5_000)
        d.navigate().back(); Thread.sleep(1_500)
        snap("home-after")
    }
}
