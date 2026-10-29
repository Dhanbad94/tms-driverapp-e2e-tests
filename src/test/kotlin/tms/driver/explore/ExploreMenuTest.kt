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
import tms.driver.flows.LoginFlow
import tms.driver.screens.Ids
import tms.driver.screens.home.OdHomeScreen
import tms.driver.screens.shell.HomeShell
import java.time.Duration

/**
 * Developer tool (tag "explore"): looks for any way to the Notifications screen or the Dispatch Log. On Home, Requests, Profile and
 * Help it lists every text and clickable thing, taps the toolbar menu icon where there is one, and records what happens.
 */
@Tag("explore")
@Tag("device")
@ExtendWith(DriverExtension::class)
@DisplayName("Explore: ways to Notifications and Dispatch Log (read-only)")
class ExploreMenuTest {
    private val d get() = DriverSession.driver

    private fun dump(label: String) {
        Thread.sleep(2_000)
        val src = d.pageSource
        val texts = Regex("""<[^>]*\btext="([^"]+)"[^>]*>""").findAll(src).map { it.groupValues[1].take(40) }.toList().distinct()
        val ids = Regex("""resource-id="[^"]*:id/([^"]+)"[^>]*clickable="true"|clickable="true"[^>]*resource-id="[^"]*:id/([^"]+)"""").findAll(src).map { it.groupValues[1].ifEmpty { it.groupValues[2] } }.toSet()
        println("EXPLORE [$label] texts=$texts")
        println("EXPLORE [$label] clickable-ids=$ids")
        println("EXPLORE [$label] visible-text-mentions: notification=${texts.any { it.contains("otification", true) }} dispatchLog=${texts.any { it.contains("ispatch", true) && it.contains("log", true) }}")
    }

    private fun tryMenu(label: String) {
        val icons = d.findElements(Ids.by("iv_menu"))
        println("EXPLORE [$label] iv_menu present=${icons.size} displayed=${icons.any { runCatching { it.isDisplayed }.getOrDefault(false) }}")
        if (icons.any { runCatching { it.isDisplayed }.getOrDefault(false) }) {
            runCatching { icons.first { it.isDisplayed }.click() }
            dump("$label-after-menu-tap")
        }
    }

    @Test
    @DisplayName("[EXPLORE-016] Looking for Notifications and Dispatch Log")
    fun capture() {
        val creds = Credentials.fromEnvironment(); assumeTrue(creds != null, "QA driver credentials not set")
        LoginFlow(creds!!).loginToHome()
        OdHomeScreen().waitForEmptyState(Duration.ofSeconds(40))
        dump("home"); tryMenu("home")
        println("EXPLORE tabs-present: ${HomeShell().presentTabs()}")
        runCatching { d.findElement(Ids.by("fl_home_ride_requests")).click() }; dump("requests"); tryMenu("requests")
        Thread.sleep(1_500)
        HomeShell().open(HomeShell.Tab.PROFILE); dump("profile"); tryMenu("profile")
        HomeShell().open(HomeShell.Tab.HELP); dump("help"); tryMenu("help")
        // The hidden tab: tapping where it would be must not open anything
        println("EXPLORE notifi-tab-nodes: ${d.findElements(Ids.by("cl_nav_notifi")).size} displayed=${d.findElements(Ids.by("cl_nav_notifi")).any { runCatching { it.isDisplayed }.getOrDefault(false) }}")
    }
}
