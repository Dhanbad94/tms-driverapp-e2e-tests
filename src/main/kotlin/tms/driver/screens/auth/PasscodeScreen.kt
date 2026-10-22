package tms.driver.screens.auth

import org.openqa.selenium.By
import tms.driver.screens.BaseScreen
import tms.driver.screens.Ids
import java.time.Duration

/**
 * Passcode screen (`PassCodeFragment`, login mode). `otp_view` is a custom-drawn masked
 * AppCompatEditText, so digits are sent as key events (more robust than sendKeys on a custom view).
 * UNVERIFIED on a live session.
 */
class PasscodeScreen : BaseScreen() {
    private val title: By = Ids.by("atv_enter_passcode")
    private val otpView: By = Ids.by("otp_view")
    private val submit: By = Ids.by("btn_submit")
    private val forgot: By = Ids.by("atv_forgot_passcode")
    private val error: By = Ids.by("atv_error_message")

    fun waitUntilShown(timeout: Duration = config.defaultTimeout): PasscodeScreen {
        waits.visible(otpView, timeout)
        return this
    }

    fun isShown(timeout: Duration = Duration.ofSeconds(2)): Boolean = waits.appears(otpView, timeout)
    fun titleText(): String = textOf(title)
    fun fieldText(): String = waits.visible(otpView).text
    fun isErrorShown(): Boolean = waits.isVisibleNow(error)
    fun isForgotShown(): Boolean = waits.isVisibleNow(forgot)

    fun enterPasscode(code: String): PasscodeScreen {
        typeDigitsByKeys(otpView, code)
        return this
    }

    /** Submit button as currently shown; null when it is hidden (e.g. while an error is displayed). */
    fun isSubmitEnabled(): Boolean? =
        driver.findElements(submit).firstOrNull()?.takeIf { runCatching { it.isDisplayed }.getOrDefault(false) }?.isEnabled

    fun submit() {
        tap(submit)
        waitForLoaderToFinish()
    }

    fun errorText(timeout: Duration = config.defaultTimeout): String = waits.visible(error, timeout).text

    fun tapForgotPasscode(): OtpScreen {
        tap(forgot)
        waitForLoaderToFinish()
        return OtpScreen().waitUntilShown()
    }
}
