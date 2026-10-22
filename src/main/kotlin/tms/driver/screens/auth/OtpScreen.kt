package tms.driver.screens.auth

import io.appium.java_client.AppiumBy
import org.openqa.selenium.By
import tms.driver.screens.BaseScreen
import tms.driver.screens.Ids
import java.time.Duration

/**
 * OTP screen (`VerifyOtpFragment`). The "Resend Code" control has NO resource-id in the app, so it
 * is located by visible text (case-insensitive).
 */
class OtpScreen : BaseScreen() {
    private val codeField: By = Ids.by("aet_code")
    private val title: By = Ids.by("atv_forgot_password")
    private val back: By = Ids.by("iv_back")
    private val resend: By = AppiumBy.androidUIAutomator("new UiSelector().textMatches(\"(?i).*resend.*\")")

    fun waitUntilShown(timeout: Duration = config.defaultTimeout): OtpScreen {
        waits.visible(codeField, timeout)
        return this
    }

    fun isShown(timeout: Duration = Duration.ofSeconds(2)): Boolean = waits.appears(codeField, timeout)
    fun titleText(): String = textOf(title)
    fun codeFieldText(): String = waits.visible(codeField).text

    private val subtitle: By = Ids.by("atv_forgot_password_message")
    private val next: By = Ids.by("btn_submit")
    private val error: By = Ids.by("atv_error_message")

    fun subtitleText(): String = textOf(subtitle)
    fun enterCode(code: String) {
        val el = waits.clickable(codeField)
        el.click()
        el.sendKeys(code)
    }
    fun isNextEnabled(): Boolean =
        driver.findElements(next).firstOrNull()?.let { runCatching { it.isDisplayed }.getOrDefault(false) && it.isEnabled } ?: false
    fun submit() { tap(next); waitForLoaderToFinish() }
    fun isErrorShown(): Boolean = waits.isVisibleNow(error)
    fun errorText(): String = textOf(error)

    fun tapResend() {
        tap(resend)
        waitForLoaderToFinish()
    }

    fun tapBack() = tap(back)
}
