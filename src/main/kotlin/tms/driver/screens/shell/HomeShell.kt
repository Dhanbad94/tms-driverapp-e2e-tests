package tms.driver.screens.shell

import org.openqa.selenium.By
import tms.driver.screens.BaseScreen
import tms.driver.screens.Ids
import java.time.Duration

/**
 * Toolbar + bottom navigation shared by the OD shell (LandingActivity) and the FR shell
 * (ActionActivity); both use the same ids. Ids come from the Phase 1 layout analysis and are
 * UNVERIFIED against a post-login UI dump.
 */
class HomeShell : BaseScreen() {
    private val driverName: By = Ids.by("tv_driver_name")
    private val dutySwitch: By = Ids.by("sw_duty")
    private val navHome: By = Ids.by("cl_nav_home")
    private val navProfile: By = Ids.by("cl_nav_profile")
    private val introOverlay: By = Ids.by("iv_intro_help")
    private val orgPicker: By = Ids.by("org_list")

    /** True when a multi-org driver is shown the organisation picker before the dashboard. */
    fun isOrgPickerShown(): Boolean = waits.isVisibleNow(orgPicker)

    fun waitUntilShown(timeout: Duration = Duration.ofSeconds(45)): HomeShell {
        waits.anyVisible(driverName, dutySwitch, navHome, timeout = timeout)
        dismissIntroIfShown()
        return this
    }

    fun isShown(timeout: Duration = Duration.ofSeconds(3)): Boolean =
        runCatching { waits.anyVisible(driverName, dutySwitch, navHome, timeout = timeout) }.isSuccess

    fun driverNameText(): String = textOf(driverName)

    /** First-run coachmark overlay: tap through until it is gone (bounded). */
    fun dismissIntroIfShown(maxTaps: Int = 20) {
        repeat(maxTaps) {
            if (!waits.appears(introOverlay, Duration.ofSeconds(2))) return
            driver.findElements(introOverlay).firstOrNull()?.click()
        }
    }

    enum class Tab(val nav: String, val indicator: String) {
        HOME("cl_nav_home", "v_nav_home"),
        NOTIFICATIONS("cl_nav_notifi", "v_nav_notifi"),
        PROFILE("cl_nav_profile", "v_nav_profile"),
        HELP("cl_nav_help", "v_nav_help"),
    }

    /** Tabs actually rendered. The staging OD/ASAP build shows Home, Profile and Help only. */
    fun presentTabs(): List<Tab> = Tab.values().filter { waits.isVisibleNow(Ids.by(it.nav)) }
    fun open(tab: Tab) = tap(Ids.by(tab.nav))
    fun isSelected(tab: Tab): Boolean = waits.appears(Ids.by(tab.indicator), Duration.ofSeconds(3))

    private val dutyText: By = Ids.by("tv_sw_duty")
    private val dutyTap: By = Ids.by("v_duty")
    private val homeSignOut: By = Ids.by("tv_home_signout")

    fun dutyState(): String = textOf(dutyText).trim().uppercase()
    fun toggleDuty() = tap(dutyTap)
    /** Toolbar Sign Out (visible only off duty); logs out immediately, no confirmation. */
    fun tapHomeSignOut() = tap(homeSignOut)
    fun isHomeSignOutVisible(): Boolean = waits.isVisibleNow(homeSignOut)
    fun waitForDuty(state: String, timeout: Duration = Duration.ofSeconds(20)): Boolean {
        val end = System.nanoTime() + timeout.toNanos()
        while (System.nanoTime() < end) {
            if (runCatching { dutyState() }.getOrNull() == state.uppercase()) return true
            Thread.sleep(300)
        }
        return false
    }

    fun openProfile(): ProfileScreen {
        tap(navProfile)
        return ProfileScreen().waitUntilShown()
    }
}
