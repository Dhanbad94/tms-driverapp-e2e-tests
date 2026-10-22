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
}
