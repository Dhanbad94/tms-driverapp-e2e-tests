package tms.driver.flows

import tms.driver.core.config.Credentials
import tms.driver.core.driver.DriverSession
import tms.driver.screens.auth.LoginScreen
import tms.driver.screens.auth.PasscodeScreen
import tms.driver.screens.shell.HomeShell
import tms.driver.screens.shell.ProfileScreen

/** Multi-step auth flows built from screen objects. Credentials are never logged. */
class LoginFlow(private val credentials: Credentials) {

    /** Launch -> phone entered and submitted -> Passcode screen shown. */
    fun toPasscodeScreen(): PasscodeScreen {
        val login = LaunchFlow().launchToLogin().expandForm()
        // The picker defaults to +1; the QA driver is registered under another country code.
        login.selectCountry(credentials.countryName, credentials.isdCode)
        login.enterPhone(credentials.phoneDigits())
        val passcode = login.submit().waitUntilShown()
        return passcode
    }

    /** Full login with the valid passcode; returns the post-login shell. */
    fun loginToHome(): HomeShell {
        toPasscodeScreen().enterPasscode(credentials.passcode).submit()
        return HomeShell().waitUntilShown()
    }

    /** Sign out through Profile with confirmation; returns the Login screen. */
    fun logout(): LoginScreen {
        val dialog = HomeShell().openProfile().tapSignOut()
        dialog.confirm()
        return LoginScreen().waitUntilShown()
    }
}
