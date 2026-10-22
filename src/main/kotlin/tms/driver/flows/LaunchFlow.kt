package tms.driver.flows

import tms.driver.core.driver.DriverSession
import tms.driver.screens.SystemDialogs
import tms.driver.screens.auth.LoginScreen

/**
 * App launch flow. A clean install lands on Login after the fixed 3 s splash (SplashViewModel).
 * Splash has no resource-ids, so we wait for the Login markers rather than for Splash itself.
 */
class LaunchFlow {
    fun launchToLogin(): LoginScreen {
        val dialogs = SystemDialogs(DriverSession.driver)
        // If autoGrantPermissions is off, a notification prompt can cover the screen on API 33+.
        dialogs.allowIfPresent()
        return LoginScreen().waitUntilShown()
    }
}
