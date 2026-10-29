package tms.driver.trip

import io.qameta.allure.Allure
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import tms.driver.base.DriverExtension
import tms.driver.core.config.Credentials
import tms.driver.core.device.Adb
import tms.driver.core.driver.DriverSession
import tms.driver.core.wait.Toasts
import tms.driver.flows.LoginFlow
import tms.driver.screens.home.OdHomeScreen
import tms.driver.screens.shell.CameraPermissionDialog
import tms.driver.screens.shell.EditProfileScreen
import tms.driver.screens.shell.HomeShell
import java.time.Duration

/**
 * Phase T12e (PROF-001 to PROF-003 of docs/additional-coverage-plan.md): the photo sheet on Edit Profile, a refused camera permission and
 * the Delete Account alert. SAFETY: the Delete Account alert is opened and answered with NO only; its "YES, DELETE" button is never tapped.
 * A picture is never uploaded. Revoking the camera permission restarts the app process, so the test grants it again in a finally block.
 */
@Tag("profile")
@Tag("trip")
@Tag("device")
@ExtendWith(DriverExtension::class)
@DisplayName("Driver profile: photo sheet, camera permission and Delete Account alert")
class ProfilePhotoTest {
    private lateinit var creds: Credentials

    @BeforeEach
    fun requireCredentials() {
        val c = Credentials.fromEnvironment()
        assumeTrue(c != null, "TMS_DRIVER_PHONE / TMS_DRIVER_PASSCODE are not set, so this test is skipped")
        creds = c!!
    }

    private fun openEditProfile(): EditProfileScreen {
        LoginFlow(creds).loginToHome()
        assertTrue(OdHomeScreen().waitForEmptyState(Duration.ofSeconds(60)))
        return HomeShell().openProfile().openEdit()
    }

    @Test
    @DisplayName("[PROF-001] The camera icon on Edit Profile opens a sheet with Camera and Gallery only, and Back closes it")
    fun photoSheet() {
        step("Given the driver is on Edit Profile")
        val edit = openEditProfile()

        step("When the driver taps the camera icon, then a sheet offers Camera and Gallery and nothing else")
        val sheet = edit.openPhotoSheet()
        assertEquals(listOf("Camera", "Gallery"), sheet.options())

        step("And Back closes the sheet and leaves Edit Profile as it was")
        sheet.dismiss()
        assertFalse(sheet.isShown(Duration.ofSeconds(2)))
        assertEquals("Automation", edit.firstNameText())
    }

    @Test
    @DisplayName("[PROF-002] Refusing the camera permission does nothing and says nothing")
    fun cameraPermissionRefused() {
        val adb = Adb()
        val pkg = DriverSession.config.appPackage
        step("Given the camera permission has been taken away (which restarts the app) and the driver is on Edit Profile again")
        openEditProfile()
        try {
            assertTrue(adb.revoke(pkg, "android.permission.CAMERA").ok, "Could not revoke the camera permission")
            Thread.sleep(3_000)
            runCatching { DriverSession.driver.activateApp(pkg) }
            assertTrue(OdHomeScreen().waitForEmptyState(Duration.ofSeconds(60)), "The app should come back on Home after the permission change")
            val edit = HomeShell().openProfile().openEdit()

            step("When the driver chooses Camera, then the system asks for the camera permission")
            val sheet = edit.openPhotoSheet()
            sheet.tapCamera()
            val dialog = CameraPermissionDialog().waitUntilShown()
            assertTrue(dialog.messageText().contains("take pictures and record video"), "Message was '${dialog.messageText()}'")
            assertEquals(listOf("While using the app", "Only this time", "Don’t allow"), dialog.optionTexts())

            step("And when the driver refuses, then Edit Profile is shown again without any message")
            Toasts.mark(DriverSession.driver)
            dialog.deny()
            assertEquals(pkg, DriverSession.driver.currentPackage)
            assertEquals("Automation", edit.waitUntilShown().firstNameText())
            val toast = Toasts.read(DriverSession.driver, Duration.ofSeconds(3))
            observe("toast-after-refusal", toast ?: "(none)")
            assertEquals(null, toast, "The app gives no message when the camera permission is refused")
        } finally {
            adb.grant(pkg, "android.permission.CAMERA")
        }
    }

    @Test
    @DisplayName("[PROF-003] Delete Account asks twice; answering NO keeps the account and the driver signed in")
    fun deleteAccountAskedTwiceAnswerNo() {
        step("Given the driver is on Edit Profile")
        val edit = openEditProfile()

        step("When the driver opens the toolbar menu, then it offers Delete Account")
        val menu = edit.openAccountMenu()
        assertEquals("Delete Account", menu.deleteAccountText())

        step("And choosing it asks 'Delete Account?' with YES, DELETE and NO")
        val alert = menu.openDeleteAlert()
        assertEquals("Delete Account?", alert.titleText())
        assertEquals("Are you sure you want to delete your account?", alert.messageText())
        assertTrue(DriverSession.driver.pageSource.contains("YES, DELETE"), "The alert should offer YES, DELETE")

        step("When the driver answers NO, then the alert closes and the driver is still signed in")
        alert.cancel()
        assertFalse(alert.isShown(), "The alert should close")
        DriverSession.driver.navigate().back()
        Thread.sleep(1_500)
        DriverSession.driver.navigate().back()
        Thread.sleep(1_500)
        assertTrue(OdHomeScreen().waitForEmptyState(Duration.ofSeconds(30)) || HomeShell().isShown(Duration.ofSeconds(10)), "The driver should still be signed in")
    }

    private fun observe(name: String, value: String) {
        Allure.addAttachment(name, value)
        LoggerFactory.getLogger("observed").info("OBSERVED {} = {}", name, value)
    }

    private fun step(description: String) {
        Allure.step(description)
        LoggerFactory.getLogger("steps").info("STEP: {}", description)
    }
}
