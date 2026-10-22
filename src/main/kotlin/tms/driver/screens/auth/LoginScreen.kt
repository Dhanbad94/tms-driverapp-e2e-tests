package tms.driver.screens.auth

import io.appium.java_client.AppiumBy
import org.openqa.selenium.By
import tms.driver.screens.BaseScreen
import tms.driver.screens.Ids
import java.time.Duration

/**
 * Login screen (`LoginFragment`, `L/fragment_login.xml`).
 *
 * The screen has two visual states: the initial "welcome" state (number area collapsed) and the
 * expanded form. Ids below were confirmed in the layout during Phase 1; they are unverified against
 * a live UI dump.
 */
class LoginScreen : BaseScreen() {
    private val welcomeArt: By = Ids.by("aiv_welcome")
    private val numberAreaOverlay: By = Ids.by("v_number_click")
    private val whatsYourNumber: By = Ids.by("atv_your_number")
    private val phoneField: By = Ids.by("et_mobile_number")
    private val countryPicker: By = Ids.by("ccp_picker")
    private val submitButton: By = Ids.by("btn_submit")
    private val errorMessage: By = Ids.by("atv_error_message")

    /** Any element that proves the Login screen is showing in either of its two states. */
    private val anyLoginMarker: Array<By> = arrayOf(welcomeArt, numberAreaOverlay, whatsYourNumber, phoneField)

    fun waitUntilShown(timeout: Duration = config.launchTimeout): LoginScreen {
        waits.anyVisible(*anyLoginMarker, timeout = timeout)
        return this
    }

    fun isShown(timeout: Duration = Duration.ofSeconds(2)): Boolean =
        runCatching { waits.anyVisible(*anyLoginMarker, timeout = timeout) }.isSuccess

    fun expandForm(): LoginScreen {
        tap(numberAreaOverlay)
        waits.visible(phoneField)
        return this
    }

    /**
     * Opens the country-code picker (`SelectCountryCode` dialog), searches by [countryName]
     * and picks the matching row, then verifies the picker shows [isdCode].
     * Dialog ids (`search_view`, `country_code_rv`, `country_name`) are from the layout analysis
     * and are UNVERIFIED on a live session.
     */
    fun selectCountry(countryName: String, isdCode: String): LoginScreen {
        if (countryCodeText().filter { it.isDigit() } == isdCode.filter { it.isDigit() }) return this
        tap(countryPicker)
        waits.visible(Ids.by("search_view"))
        tap(Ids.by("search_view"))
        driver.findElement(AppiumBy.className("android.widget.EditText")).sendKeys(countryName)
        val row = AppiumBy.androidUIAutomator(
            "new UiSelector().resourceId(\"${Ids.of("country_name")}\").textContains(\"$countryName\")",
        )
        tap(row)
        waits.visible(phoneField)
        val shown = countryCodeText()
        check(shown.filter { it.isDigit() } == isdCode.filter { it.isDigit() }) {
            "country picker shows '$shown' after selecting '$countryName', expected '$isdCode'"
        }
        return this
    }

    /**
     * Confirmed from a live dump: after the country dialog closes, `et_mobile_number` is
     * `enabled=false` and the `v_number_click` overlay is on top again. Tapping the overlay
     * re-enables the field.
     */
    private fun ensureFormActive() {
        val field = driver.findElements(phoneField).firstOrNull()
        if (field != null && field.isEnabled) return
        if (waits.isVisibleNow(numberAreaOverlay)) tap(numberAreaOverlay)
        waits.clickable(phoneField)
    }

    fun enterPhone(digits: String): LoginScreen {
        ensureFormActive()
        waits.clickable(phoneField).click()
        val field = waits.visible(phoneField)
        try {
            field.sendKeys(digits)
        } catch (e: org.openqa.selenium.InvalidElementStateException) {
            digits.forEach { ch ->
                driver.pressKey(
                    io.appium.java_client.android.nativekey.KeyEvent(
                        io.appium.java_client.android.nativekey.AndroidKey.valueOf("DIGIT_$ch"),
                    ),
                )
            }
        }
        return this
    }

    fun submit(): PasscodeScreen {
        tap(submitButton)
        waitForLoaderToFinish()
        return PasscodeScreen()
    }

    private val termsText: By = Ids.by("atv_terms_condition")

    fun isPhoneFieldEnabled(): Boolean = driver.findElement(phoneField).isEnabled
    /** The terms text fades in after the 200 ms expand transition, so wait briefly for it. */
    fun isTermsShown(timeout: Duration = Duration.ofSeconds(3)): Boolean {
        // The on-screen keyboard (shown on expand) covers the terms text, so hide it first.
        runCatching { driver.hideKeyboard() }
        return waits.appears(termsText, timeout)
    }

    /** Digits currently in the phone field (an empty field reports its hint, which has no digits). */
    fun phoneDigitCount(): Int = waits.visible(phoneField).text.count { it.isDigit() }

    fun clearPhone(): LoginScreen {
        ensureFormActive()
        val el = waits.clickable(phoneField)
        el.click()
        el.clear()
        return this
    }

    fun isSubmitEnabled(): Boolean =
        driver.findElements(submitButton).firstOrNull()?.let {
            runCatching { it.isDisplayed }.getOrDefault(false) && it.isEnabled
        } ?: false

    fun isErrorShown(): Boolean = waits.isVisibleNow(errorMessage)

    fun isSubmitVisible(): Boolean = waits.isVisibleNow(submitButton)
    fun countryCodeText(): String = textOf(countryPicker)
    fun errorText(): String = textOf(errorMessage)
}
