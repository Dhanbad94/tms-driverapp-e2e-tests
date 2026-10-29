package tms.driver.screens.shell

import io.appium.java_client.AppiumBy
import org.openqa.selenium.By
import tms.driver.core.wait.Toasts
import tms.driver.screens.BaseScreen
import tms.driver.screens.Ids

/** Edit Profile (`EditProfileFragment`). Ids from the layout analysis; validation errors are Toasts. */
class EditProfileScreen : BaseScreen() {
    private val firstName: By = Ids.by("atv_first_name")
    private val lastName: By = Ids.by("atv_last_name")
    private val email: By = Ids.by("atv_email")
    private val save: By = Ids.by("btn_apply")
    private val cancel: By = Ids.by("btn_cancel")

    fun waitUntilShown(): EditProfileScreen { waits.visible(firstName); return this }

    fun firstNameText(): String = waits.visible(firstName).text
    fun lastNameText(): String = waits.visible(lastName).text

    fun setFirstName(value: String) = replace(firstName, value)
    fun setLastName(value: String) = replace(lastName, value)
    fun setEmail(value: String) = replace(email, value)

    private fun replace(by: By, value: String) {
        val el = waits.clickable(by)
        el.click()
        el.clear()
        if (value.isNotEmpty()) el.sendKeys(value)
    }

    /** Taps Save and returns the toast text shown (null if none was readable). */
    fun saveExpectingToast(): String? {
        scrollTo("btn_apply")
        Toasts.mark(driver)
        waits.clickable(Ids.by("btn_apply")).click()
        return Toasts.read(driver)
    }

    fun leave() = clickBelowFold("btn_cancel")

    /**
     * The Save/Cancel buttons sit below the fold and are covered by the keyboard while a field has
     * focus (confirmed by a live dump), so hide the keyboard and scroll them into view first.
     */
    private fun clickBelowFold(idName: String) {
        scrollTo(idName)
        waits.clickable(Ids.by(idName)).click()
    }

    private fun scrollTo(idName: String) {
        runCatching { driver.hideKeyboard() }
        val scroll = AppiumBy.androidUIAutomator(
            "new UiScrollable(new UiSelector().scrollable(true).instance(0))" +
                ".scrollIntoView(new UiSelector().resourceId(\"${Ids.of(idName)}\"))",
        )
        runCatching { driver.findElement(scroll) }
    }

    // --- photo and account menu -------------------------------------------------------------------------------------------

    /** Taps the camera icon on the photo; the bottom sheet offers Camera and Gallery. */
    fun openPhotoSheet(): PhotoSheet { tap(Ids.by("aiv_edit_image")); return PhotoSheet().waitUntilShown() }

    /** Taps the three dots in the toolbar; the sheet offers Delete Account. */
    fun openAccountMenu(): AccountMenuSheet { tap(Ids.by("tv_more")); return AccountMenuSheet().waitUntilShown() }
}

/** The sheet behind the camera icon: Camera and Gallery only (no remove option). */
class PhotoSheet : BaseScreen() {
    private val camera: By = Ids.by("atv_camera")
    private val gallery: By = Ids.by("atv_gallery")

    fun waitUntilShown(timeout: java.time.Duration = java.time.Duration.ofSeconds(15)): PhotoSheet { waits.visible(camera, timeout); return this }
    fun isShown(timeout: java.time.Duration = java.time.Duration.ofSeconds(2)): Boolean = waits.appears(camera, timeout)
    fun cameraText(): String = textOf(camera)       // "Camera"
    fun galleryText(): String = textOf(gallery)     // "Gallery"
    /** The texts of the sheet, so a test can check that nothing else (for example a remove option) is offered. */
    fun options(): List<String> = listOf(camera, gallery).map { textOf(it) }
    fun tapCamera() = tap(camera)
    fun dismiss() { tms.driver.core.driver.DriverSession.driver.navigate().back() }
}

/** The sheet behind the toolbar dots on Edit Profile. */
class AccountMenuSheet : BaseScreen() {
    private val deleteAccount: By = By.xpath("//*[@text='Delete Account']")

    fun waitUntilShown(timeout: java.time.Duration = java.time.Duration.ofSeconds(15)): AccountMenuSheet { waits.visible(deleteAccount, timeout); return this }
    fun isShown(timeout: java.time.Duration = java.time.Duration.ofSeconds(2)): Boolean = waits.appears(deleteAccount, timeout)
    fun deleteAccountText(): String = textOf(deleteAccount)

    /** Opens the "Delete Account?" alert. The alert's buttons are YES, DELETE and NO: tests only ever answer NO. */
    fun openDeleteAlert(): CommonDialog { tap(deleteAccount); return CommonDialog().waitUntilShown() }
}

/** The system dialog asking for the camera permission. */
class CameraPermissionDialog : BaseScreen() {
    private val message: By = By.id("com.android.permissioncontroller:id/permission_message")
    private val whileUsing: By = By.id("com.android.permissioncontroller:id/permission_allow_foreground_only_button")
    private val onlyThisTime: By = By.id("com.android.permissioncontroller:id/permission_allow_one_time_button")
    private val deny: By = By.id("com.android.permissioncontroller:id/permission_deny_button")

    fun waitUntilShown(timeout: java.time.Duration = java.time.Duration.ofSeconds(15)): CameraPermissionDialog { waits.visible(message, timeout); return this }
    fun messageText(): String = textOf(message)
    fun optionTexts(): List<String> = listOf(whileUsing, onlyThisTime, deny).map { textOf(it) }
    fun deny() = tap(deny)
}
