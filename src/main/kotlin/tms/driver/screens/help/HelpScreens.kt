package tms.driver.screens.help

import org.openqa.selenium.By
import tms.driver.core.driver.DriverSession
import tms.driver.screens.BaseScreen
import tms.driver.screens.Ids
import java.time.Duration

/**
 * The Help Center, a Compose screen: it has no resource ids, so everything is found by its text. The video player is a separate
 * (ExoPlayer) view and is not tested beyond its card text.
 */
class HelpCenterScreen : BaseScreen() {
    private fun text(t: String): By = By.xpath("//*[@text='$t']")

    fun waitUntilShown(timeout: Duration = Duration.ofSeconds(30)): HelpCenterScreen { waits.visible(text("Launch Quick Tour"), timeout); return this }
    fun isShown(timeout: Duration = Duration.ofSeconds(3)): Boolean = waits.appears(text("Launch Quick Tour"), timeout)
    fun hasText(t: String): Boolean = waits.isVisibleNow(text(t))
    fun toolbarTitle(): String = textOf(Ids.by("atv_toolbar_title"))        // "Help Center"
    fun videoTitle(): String = textOf(text("Video Tour"))
    fun videoDuration(): String = textOf(By.xpath("//*[starts-with(@text,'Duration')]"))   // "Duration : 3 mins"

    fun openSupport(): SupportScreen { tap(text("Call Support")); return SupportScreen().waitUntilShown() }
    fun launchQuickTour(): QuickTourOverlay { tap(text("Launch Quick Tour")); return QuickTourOverlay().waitUntilShown() }
    fun openWalkthrough(): WalkthroughScreen { tap(text("Animation Tour")); return WalkthroughScreen().waitUntilShown() }
}

/** "Call Support": a normal view with ids. CALL NOW opens the phone's dialer with the number; nothing is dialled. */
class SupportScreen : BaseScreen() {
    private val toolbar: By = Ids.by("atv_toolbar_title")
    private val heading: By = Ids.by("titleText")
    private val description: By = Ids.by("descriptionText")
    private val phoneLabel: By = Ids.by("supportPhoneLabel")
    private val phone: By = Ids.by("phoneNumber")
    private val call: By = Ids.by("callButton")
    private val tip: By = Ids.by("tipCard")

    fun waitUntilShown(timeout: Duration = Duration.ofSeconds(20)): SupportScreen { waits.visible(call, timeout); return this }
    fun isShown(timeout: Duration = Duration.ofSeconds(3)): Boolean = waits.appears(call, timeout)
    fun toolbarTitle(): String = textOf(toolbar)         // "Call Support"
    fun headingText(): String = textOf(heading)          // "Need Help?"
    fun descriptionText(): String = textOf(description)  // "Our support team is available 24/7 to assist you"
    fun phoneLabelText(): String = textOf(phoneLabel)    // "Support Phone Number"
    fun phoneText(): String = textOf(phone)              // "(888) 574-8885"
    fun callText(): String = textOf(call)                // "CALL NOW"
    fun tipText(): String = driver.findElement(By.xpath("//*[@resource-id='${Ids.of("tipCard")}']//*[contains(@text,'Tip')]")).text

    /** Taps CALL NOW; returns the dialer's package and the number it shows, and brings the app back to the front. */
    fun callAndReadDialer(): Pair<String, String> {
        tap(call)
        Thread.sleep(2_500)
        val d = DriverSession.driver
        val pkg = d.currentPackage.orEmpty()
        val shown = d.findElements(By.id("$pkg:id/digits")).firstOrNull()?.text.orEmpty()
        d.activateApp(DriverSession.config.appPackage)
        return pkg to shown
    }
}

/** The quick tour: a full-screen picture (`iv_intro_help`) that moves on with every tap and closes after the last one. */
class QuickTourOverlay : BaseScreen() {
    private val overlay: By = Ids.by("iv_intro_help")

    fun waitUntilShown(timeout: Duration = Duration.ofSeconds(15)): QuickTourOverlay { waits.visible(overlay, timeout); return this }
    fun isShown(timeout: Duration = Duration.ofSeconds(2)): Boolean = waits.appears(overlay, timeout)
    fun isGone(timeout: Duration = Duration.ofSeconds(5)): Boolean = waits.gone(overlay, timeout)

    /** Taps the picture until the tour closes; returns how many taps it took (0 when it never opened). */
    fun tapThrough(maxTaps: Int = 20): Int {
        var taps = 0
        while (taps < maxTaps && isShown(Duration.ofSeconds(2))) {
            runCatching { driver.findElement(overlay).click() }
            taps++
            Thread.sleep(700)
        }
        return taps
    }
}

/** "Animation Tour": titled steps with BACK and NEXT. NEXT is off on the last step. */
class WalkthroughScreen : BaseScreen() {
    private val title: By = Ids.by("tv_title")
    private val description: By = Ids.by("tv_desc")
    private val back: By = Ids.by("bt_back")
    private val next: By = Ids.by("bt_next")

    fun waitUntilShown(timeout: Duration = Duration.ofSeconds(20)): WalkthroughScreen { waits.visible(next, timeout); return this }
    fun isShown(timeout: Duration = Duration.ofSeconds(3)): Boolean = waits.appears(next, timeout)
    fun titleText(): String = textOf(title)
    fun descriptionText(): String = textOf(description)
    fun backText(): String = textOf(back)              // "BACK"
    fun nextText(): String = textOf(next)              // "NEXT"
    fun isNextEnabled(): Boolean = driver.findElement(next).isEnabled
    fun isBackEnabled(): Boolean = driver.findElement(back).isEnabled
    fun next() { tap(next); Thread.sleep(900) }
    fun back() { tap(back); Thread.sleep(900) }
}
