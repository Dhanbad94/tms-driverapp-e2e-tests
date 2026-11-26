package tms.driver.explore

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import tms.driver.base.DriverExtension
import tms.driver.core.config.Credentials
import tms.driver.core.driver.DriverSession
import tms.driver.core.report.Artifacts
import tms.driver.core.wait.Toasts
import tms.driver.flows.LoginFlow
import tms.driver.screens.Ids
import tms.driver.screens.home.OdHomeScreen
import tms.driver.screens.shell.CommonDialog
import tms.driver.screens.shell.HomeShell
import java.time.Duration

/** Developer tool (tag "explore"): Edit Profile save (with restore) and Change Passcode with the same value. */
@Tag("explore")
@Tag("device")
@ExtendWith(DriverExtension::class)
@DisplayName("Explore: account changes")
class ExploreAccountTest {
    private val d get() = DriverSession.driver
    private val shots by lazy { Artifacts(d, DriverSession.config.artifactsDir.resolve("ExploreAccount")) }
    private fun snap(label: String, waitMs: Long = 1_000) {
        Thread.sleep(waitMs); shots.screenshot(label); shots.pageSource(label)
        val texts = Regex("""<[^>]*\btext="([^"]+)"[^>]*>""").findAll(d.pageSource).map { it.groupValues[1].take(50) }.toList().distinct()
        println("EXPLORE [$label] texts=${texts.take(30)}")
    }
    private fun saveEnabled() = d.findElements(Ids.by("btn_apply")).firstOrNull()?.isEnabled
    private fun emailText() = d.findElement(Ids.by("atv_email")).text

    @Test
    @DisplayName("[EXPLORE-029] Account")
    fun capture() {
        val creds = Credentials.fromEnvironment(); assumeTrue(creds != null, "QA driver credentials not set")
        LoginFlow(creds!!).loginToHome()
        OdHomeScreen().waitForEmptyState(Duration.ofSeconds(60))
        val profile = HomeShell().openProfile(); snap("profile")
        println("EXPLORE profile-name=${profile.nameText()}")
        val edit = profile.openEdit(); snap("edit")
        val first = edit.firstNameText(); val last = edit.lastNameText(); val email = emailText()
        println("EXPLORE first=$first last=$last email=[$email] saveEnabledInitially=${saveEnabled()}")
        edit.setLastName(last + " "); println("EXPLORE save after whitespace=${saveEnabled()}")
        edit.setLastName(last); println("EXPLORE save after revert=${saveEnabled()}")
        edit.setFirstName("A".repeat(25)); println("EXPLORE firstLen=${edit.firstNameText().length}")
        edit.setFirstName(first)
        try {
            edit.setLastName("Driver QA"); edit.setEmail("qa.driver.test@example.com")
            val toast = edit.saveExpectingToast(); println("EXPLORE save-toast=$toast")
            snap("after-save", 2000)
            println("EXPLORE after-save pkg-screen profileShown=${runCatching { profile.waitUntilShown(); profile.nameText() }.getOrNull()}")
        } finally {
            val e2 = runCatching { if (d.findElements(Ids.by("atv_last_name")).isEmpty()) profile.openEdit() else edit }.getOrNull()
            if (e2 != null) { e2.setLastName(last); e2.setEmail(email); println("EXPLORE restore-toast=" + e2.saveExpectingToast()) }
        }
        snap("after-restore", 2000)
        runCatching { println("EXPLORE profile-name-after-restore=" + profile.waitUntilShown().nameText()) }
        val cp = profile.openChangePasscode()
        cp.enterPrimary(creds.passcode); cp.submit()
        println("EXPLORE cp label=${cp.labelText()}")
        cp.enterPrimary(creds.passcode); cp.enterConfirm(creds.passcode); println("EXPLORE submitEnabled=${cp.isSubmitEnabled()}")
        Toasts.mark(d); cp.submit(); snap("cp-submitted", 1500)
        println("EXPLORE cp toast=" + Toasts.read(d, Duration.ofSeconds(3)))
        runCatching { val dlg = CommonDialog().waitUntilShown(); println("EXPLORE dialog=${dlg.titleText()} | ${dlg.messageText()}"); dlg.confirm() }
        snap("after-cp", 1500)
    }
}
