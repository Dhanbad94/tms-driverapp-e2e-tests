package tms.driver.screens

import io.appium.java_client.AppiumBy
import org.openqa.selenium.By
import tms.driver.core.driver.DriverSession

/**
 * Resource-id helper. Confirmed from a staging UI dump: ids are reported under the flavor's
 * applicationId (`com.trackmyshuttle.driver.staging:id/<name>`), not the base R namespace.
 * The package therefore comes from the active session's config. Build locators through this helper.
 */
object Ids {
    fun of(name: String, appPackage: String = DriverSession.config.appPackage): String =
        "$appPackage:id/$name"

    fun by(name: String): By = AppiumBy.id(of(name))

    /** Global API-in-flight dialog shown by AppBaseActivity.showLoading(). */
    val LOADER: By get() = by("progressDialogPrbLoading")
}
