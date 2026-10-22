package tms.driver.screens.shell

import org.openqa.selenium.By
import tms.driver.screens.BaseScreen
import tms.driver.screens.Ids

/** Change Passcode (`ChangePasscodeFragment`): step 1 current passcode, step 2 new + confirm. */
class ChangePasscodeScreen : BaseScreen() {
    private val label: By = Ids.by("atv_label")
    private val primary: By = Ids.by("otp_primary")
    private val confirm: By = Ids.by("otp_confirm")
    private val submit: By = Ids.by("btn_submit")
    private val error: By = Ids.by("atv_error_message")

    fun waitUntilShown(): ChangePasscodeScreen { waits.visible(primary); return this }
    fun labelText(): String = textOf(label)

    fun enterPrimary(code: String) = typeDigitsByKeys(primary, code)
    fun enterConfirm(code: String) = typeDigitsByKeys(confirm, code)

    fun isSubmitEnabled(): Boolean =
        driver.findElements(submit).firstOrNull()?.let { runCatching { it.isDisplayed }.getOrDefault(false) && it.isEnabled } ?: false

    fun submit() {
        tap(submit)
        waitForLoaderToFinish()
    }

    fun errorText(): String = waits.visible(error).text
    fun waitForLabel(textContains: String) =
        waits.anyVisible(By.xpath("//*[contains(@text,'$textContains')]"))
}
