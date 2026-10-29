package tms.driver.trip

import io.qameta.allure.Allure
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import tms.driver.base.DriverExtension
import tms.driver.core.config.Credentials
import tms.driver.core.device.Adb
import tms.driver.core.driver.DriverSession
import tms.driver.flows.LoginFlow
import tms.driver.screens.help.HelpCenterScreen
import tms.driver.screens.help.SupportScreen
import tms.driver.screens.help.WalkthroughScreen
import tms.driver.screens.home.OdHomeScreen
import tms.driver.screens.shell.HomeShell
import java.time.Duration

/**
 * Phase T10: the Help tab (HELP-001 to HELP-007 of docs/additional-coverage-plan.md). The Help Center is a Compose screen found by
 * text; the Support screen, the quick tour overlay and the walkthrough have resource ids. Video playback is not tested (decision:
 * skipped). No ride is needed, so these tests send no SMS.
 */
@Tag("help")
@Tag("trip")
@Tag("device")
@ExtendWith(DriverExtension::class)
@DisplayName("Driver Help Center")
class HelpTest {
    private lateinit var creds: Credentials

    @BeforeEach
    fun requireCredentials() {
        val c = Credentials.fromEnvironment()
        assumeTrue(c != null, "TMS_DRIVER_PHONE / TMS_DRIVER_PASSCODE are not set, so this test is skipped")
        creds = c!!
    }

    private fun openHelp(): HelpCenterScreen {
        LoginFlow(creds).loginToHome()
        OdHomeScreen().waitForEmptyState(Duration.ofSeconds(40))
        HomeShell().open(HomeShell.Tab.HELP)
        return HelpCenterScreen().waitUntilShown()
    }

    @Test
    @DisplayName("[HELP-001] The Help tab opens 'Help Center' with the video tour and three cards")
    fun helpCenterContent() {
        step("When the driver opens the Help tab")
        val help = openHelp()

        step("Then the screen is titled 'Help Center' and the Help tab is selected")
        assertEquals("Help Center", help.toolbarTitle())
        assertTrue(HomeShell().isSelected(HomeShell.Tab.HELP))

        step("And it shows the video tour with its length")
        assertEquals("Video Tour", help.videoTitle())
        assertEquals("Duration : 3 mins", help.videoDuration())

        step("And it offers Launch Quick Tour, Animation Tour and Call Support, each with its explanation")
        assertTrue(help.hasText("Launch Quick Tour"))
        assertTrue(help.hasText("Take a guided tour of the app features"))
        assertTrue(help.hasText("Animation Tour"))
        assertTrue(help.hasText("Browse app screenshots and features"))
        assertTrue(help.hasText("Call Support"))
        assertTrue(help.hasText("Get immediate help from our support team"))
    }

    @Test
    @DisplayName("[HELP-002 / HELP-003] Call Support shows the support number, and CALL NOW opens the dialer with that number")
    fun callSupport() {
        step("Given the driver is on the Help Center")
        val help = openHelp()

        step("When the driver taps Call Support, then the support screen shows the heading, the explanation, the number, CALL NOW and the tip")
        val support = help.openSupport()
        assertEquals("Call Support", support.toolbarTitle())
        assertEquals("Need Help?", support.headingText())
        assertEquals("Our support team is available 24/7 to assist you", support.descriptionText())
        assertEquals("Support Phone Number", support.phoneLabelText())
        assertEquals("(888) 574-8885", support.phoneText())
        assertEquals("CALL NOW", support.callText())
        assertTrue(support.tipText().startsWith("Tip: Please share your Organization Name"), "Tip was '${support.tipText()}'")

        step("When the driver taps CALL NOW, then the phone's dialer opens with the support number (nothing is dialled), and the app is still there")
        val (dialerPackage, shown) = support.callAndReadDialer()
        observe("dialer-package", dialerPackage)
        assertTrue(dialerPackage.contains("dialer"), "The dialer should open, but the front app was $dialerPackage")
        assertEquals("8885748885", shown.filter { it.isDigit() })
        assertTrue(SupportScreen().isShown(Duration.ofSeconds(15)), "The support screen should still be there after returning")
    }

    @Test
    @DisplayName("[HELP-004] Launch Quick Tour shows pictures that move on with each tap, and returns to the Help Center after the last one")
    fun quickTour() {
        step("Given the driver is on the Help Center")
        val help = openHelp()

        step("When the driver taps Launch Quick Tour, then a full-screen picture appears")
        val tour = help.launchQuickTour()
        assertTrue(tour.isShown(Duration.ofSeconds(5)))

        step("And tapping through closes it after a few taps, back on the Help Center")
        val taps = tour.tapThrough()
        observe("quick-tour-taps", taps.toString())
        assertTrue(taps in 3..15, "The tour should take a handful of taps, but took $taps")
        assertTrue(tour.isGone(), "The tour should be closed")
        assertTrue(help.isShown(Duration.ofSeconds(10)), "The Help Center should be shown again")
    }

    @Test
    @DisplayName("[HELP-005] Pressing Back during the quick tour closes it")
    fun quickTourBack() {
        step("Given the quick tour is showing")
        val help = openHelp()
        val tour = help.launchQuickTour()

        step("When the driver presses Back, then the tour closes and the Help Center is shown again")
        DriverSession.driver.navigate().back()
        assertTrue(tour.isGone(Duration.ofSeconds(8)), "Back should close the tour")
        assertEquals(DriverSession.config.appPackage, DriverSession.driver.currentPackage)
        assertTrue(help.isShown(Duration.ofSeconds(10)), "The Help Center should be shown again")
    }

    @Test
    @DisplayName("[HELP-006] Animation Tour steps through its pages with NEXT and BACK, and NEXT is off on the last page")
    fun walkthrough() {
        step("Given the driver is on the Help Center")
        val help = openHelp()

        step("When the driver opens Animation Tour, then the first page explains going On/Off Duty")
        val tour = help.openWalkthrough()
        assertEquals("BACK", tour.backText())
        assertEquals("NEXT", tour.nextText())
        assertEquals("How to go On/Off Duty?", tour.titleText())
        assertEquals("Click On/Off Duty toggle button and Sign out", tour.descriptionText())
        assertTrue(tour.isNextEnabled())

        step("And NEXT walks through every page until it is off on the last one")
        val titles = mutableListOf(tour.titleText())
        repeat(15) { if (tour.isNextEnabled()) { tour.next(); titles += tour.titleText() } }
        observe("walkthrough-pages", titles.joinToString(" > "))
        assertFalse(tour.isNextEnabled(), "NEXT should be off on the last page")
        assertEquals(
            listOf("How to go On/Off Duty?", "How to View Ride Requests?", "How to Start Trip?", "How to Reject/Manage Request?"),
            titles, "The walkthrough should have these four pages",
        )

        step("When the driver presses BACK on the last page, then the previous page is shown")
        val last = tour.titleText()
        tour.back()
        assertTrue(tour.titleText() != last, "BACK should show an earlier page")
        assertTrue(tour.isNextEnabled(), "NEXT should be on again")

        step("And leaving the tour returns to the Help Center")
        DriverSession.driver.navigate().back()
        assertTrue(help.isShown(Duration.ofSeconds(15)))
    }

    @Test
    @DisplayName("[HELP-007] Without a network the Help Center and the Support screen still open")
    fun helpOffline() {
        step("Given the driver is signed in")
        LoginFlow(creds).loginToHome()
        OdHomeScreen().waitForEmptyState(Duration.ofSeconds(40))
        val adb = Adb()
        try {
            step("When the network is switched off and the driver opens the Help tab, then the cards are shown")
            assertTrue(adb.setOffline(true), "Could not switch the network off")
            Thread.sleep(4_000)
            HomeShell().open(HomeShell.Tab.HELP)
            val help = HelpCenterScreen().waitUntilShown()
            assertTrue(help.hasText("Launch Quick Tour") && help.hasText("Animation Tour") && help.hasText("Call Support"))

            step("And Call Support still opens with the number")
            val support = help.openSupport()
            assertEquals("(888) 574-8885", support.phoneText())
        } finally {
            adb.setOffline(false)
        }
    }

    private fun observe(name: String, value: String) {
        Allure.addAttachment(name, value)
        LoggerFactory.getLogger("observed").info("OBSERVED {} = {}", name, value)
    }

    private fun step(description: String) {
        Allure.step(description)
        LoggerFactory.getLogger("steps").info("STEP: {}", description)
    }
}
