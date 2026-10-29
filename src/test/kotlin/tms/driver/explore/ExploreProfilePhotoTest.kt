package tms.driver.explore

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.openqa.selenium.By
import tms.driver.base.DriverExtension
import tms.driver.core.config.Credentials
import tms.driver.core.device.Adb
import tms.driver.core.driver.DriverSession
import tms.driver.core.report.Artifacts
import tms.driver.flows.LoginFlow
import tms.driver.screens.Ids
import tms.driver.screens.home.OdHomeScreen
import tms.driver.screens.shell.HomeShell
import java.time.Duration

/**
 * Developer tool (tag "explore"): Edit Profile's photo sheet, the camera permission and the Delete Account alert.
 * SAFETY: the Delete Account alert is only opened and answered with its negative button (btn_negative, "No"). The positive
 * "yes, delete" button is never tapped. The camera permission is granted again at the end.
 */
@Tag("explore")
@Tag("device")
@ExtendWith(DriverExtension::class)
@DisplayName("Explore: profile photo sheet, camera permission, Delete Account alert (never deletes)")
class ExploreProfilePhotoTest {
    private val d get() = DriverSession.driver
    private val shots by lazy { Artifacts(d, DriverSession.config.artifactsDir.resolve("ExploreProfilePhoto")) }
    private fun tap(by: By): Boolean = runCatching { d.findElement(by).click(); true }.getOrDefault(false)
    private fun id(n: String) = Ids.by(n)

    private fun snap(label: String, waitMs: Long = 2_000) {
        Thread.sleep(waitMs); shots.screenshot(label); shots.pageSource(label)
        val src = d.pageSource
        val texts = Regex("""<[^>]*\btext="([^"]+)"[^>]*>""").findAll(src).map { it.groupValues[1].take(50) }.toList().distinct()
        val ids = Regex("""resource-id="[^"]*:id/([^"]+)"""").findAll(src).map { it.groupValues[1] }.toSet() - setOf("action_bar_root", "content")
        println("EXPLORE [$label] pkg=${d.currentPackage} texts=$texts")
        println("EXPLORE [$label] ids=${ids.take(40)}")
    }

    @Test
    @DisplayName("[EXPLORE-018] Photo sheet, camera permission, Delete Account alert")
    fun capture() {
        val creds = Credentials.fromEnvironment(); assumeTrue(creds != null, "QA driver credentials not set")
        val adb = Adb(); val pkg = DriverSession.config.appPackage
        LoginFlow(creds!!).loginToHome()
        OdHomeScreen().waitForEmptyState(Duration.ofSeconds(40))
        HomeShell().openProfile().openEdit()
        snap("edit-profile", 2_500)
        println("EXPLORE tap-camera-icon: ${tap(id("aiv_edit_image"))}")
        snap("photo-sheet", 2_000)
        d.navigate().back(); Thread.sleep(1_500)
        snap("after-sheet-back")

        // Delete Account first (while still on Edit Profile): open the menu and the alert, answer No only
        println("EXPLORE tap-more: ${tap(id("tv_more"))}")
        snap("more-sheet", 2_000)
        println("EXPLORE tap-delete-account: ${tap(By.xpath("//*[@text='Delete Account']"))}")
        snap("delete-alert", 2_000)
        println("EXPLORE tap-NO (btn_negative): ${tap(id("btn_negative"))}")
        snap("after-no", 2_000)

        // camera permission: revoking it kills the app process, so revoke, relaunch, and ask again from Edit Profile
        runCatching { d.navigate().back() }; Thread.sleep(1_000)
        println("EXPLORE revoke-camera: ${adb.revoke(pkg, "android.permission.CAMERA")}")
        Thread.sleep(3_000)
        runCatching { d.activateApp(pkg) }
        OdHomeScreen().waitForEmptyState(Duration.ofSeconds(60))
        HomeShell().openProfile().openEdit()
        println("EXPLORE tap-camera-icon-2: ${tap(id("aiv_edit_image"))}")
        Thread.sleep(2_000)
        println("EXPLORE tap-camera-option: ${tap(id("atv_camera"))}")
        snap("after-camera-option", 3_000)
        println("EXPLORE dialog-buttons: ${Regex("""resource-id="([^"]*permission[^"]*)"""").findAll(d.pageSource).map { it.groupValues[1] }.toList()}")
        println("EXPLORE tap-deny: ${tap(By.id("com.android.permissioncontroller:id/permission_deny_button"))}")
        snap("after-deny", 2_500)
        adb.grant(pkg, "android.permission.CAMERA")
    }
}
