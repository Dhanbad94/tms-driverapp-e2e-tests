package tms.driver.screens.requests

import org.openqa.selenium.By
import org.openqa.selenium.WebElement
import tms.driver.screens.BaseScreen
import tms.driver.screens.Ids
import java.time.Duration

/** The four tabs of the Ride Requests screen. */
enum class RequestsTab(val resourceName: String, val title: String) {
    REQUESTS("requests", "Requests"),
    ASSIGNED("assigned", "Assigned"),
    IN_PROGRESS("inprogress", "In-progress"),
    COMPLETED("completed", "Completed"),
}

/**
 * The Ride Requests screen (tabs Requests, Assigned, In-progress, Completed). Ids confirmed in live dumps.
 * Cards are found by the rider's name; the app shows the name in a different letter case than the sheets do
 * ("Appium_rider_…" on cards), so the unique number at the end of the name is what is matched.
 */
class RideRequestsScreen : BaseScreen() {
    private val title: By = Ids.by("atv_toolbar_title")
    private val requestsTab: By = Ids.by("requests")
    private val assignedTab: By = Ids.by("assigned")
    private val homeButton: By = Ids.by("fab_home")
    private val emptyMessage: By = Ids.by("no_ride_requests")
    private val homeEntry: By = Ids.by("fl_home_ride_requests")

    fun waitUntilShown(timeout: Duration = Duration.ofSeconds(30)): RideRequestsScreen { waits.visible(requestsTab, timeout); return this }
    fun isShown(timeout: Duration = Duration.ofSeconds(3)): Boolean = waits.appears(requestsTab, timeout)
    fun titleText(): String = textOf(title)            // "Ride Requests"
    fun hasAssignedTab(): Boolean = waits.isVisibleNow(assignedTab)

    /** The floating Home button on the Requests screens. */
    fun goHome() = tap(homeButton)

    /** Opens the screen from the Home tab (the "Requests" button with the badge). */
    fun openFromHome(): RideRequestsScreen { tap(homeEntry); return waitUntilShown() }

    fun open(tab: RequestsTab): RideRequestsScreen {
        tap(Ids.by(tab.resourceName))
        waits.appears(emptyMessage, Duration.ofSeconds(4))   // either the empty message or the first card appears
        return this
    }

    /** Titles of the four tabs, in order. */
    fun tabTitles(): List<String> = RequestsTab.values().map {
        driver.findElement(By.xpath("//*[@resource-id='${Ids.of(it.resourceName)}']//*[@resource-id='${Ids.of("tv_title")}']")).text
    }

    /** The number on a tab's badge; 0 when the tab shows none. */
    fun tabCount(tab: RequestsTab): Int =
        driver.findElements(By.xpath("//*[@resource-id='${Ids.of(tab.resourceName)}']//*[@resource-id='${Ids.of("tv_notification_count")}']"))
            .firstOrNull()?.text?.trim()?.toIntOrNull() ?: 0

    /** The "No Assigned Rides" style message, or null when the tab has cards. */
    fun emptyMessageText(timeout: Duration = Duration.ofSeconds(8)): String? =
        if (waits.appears(emptyMessage, timeout)) driver.findElement(emptyMessage).text else null

    /** Waits for the card of [riderName] on the open tab and returns it, or null when it does not appear. */
    fun card(riderName: String, timeout: Duration = Duration.ofSeconds(20)): RequestCard? {
        val locator = cardLocator(riderName)
        if (waits.appears(locator, minOf(timeout, Duration.ofSeconds(6)))) return RequestCard { driver.findElement(locator) }
        // The Requests tab is a long shared queue sorted oldest first, so a new ride can be below the fold.
        scrollTo(riderName.takeLast(13))
        return if (waits.appears(locator, timeout)) RequestCard { driver.findElement(locator) } else null
    }

    private fun scrollTo(uniqueText: String) {
        val selector = "new UiScrollable(new UiSelector().resourceId(\"${Ids.of("rv_ride_requests")}\").scrollable(true))" +
            ".setMaxSearchSwipes(40).scrollIntoView(new UiSelector().resourceId(\"${Ids.of("passenger_name")}\").textContains(\"$uniqueText\"))"
        runCatching { driver.findElement(io.appium.java_client.AppiumBy.androidUIAutomator(selector)) }
    }

    fun hasCard(riderName: String): Boolean = driver.findElements(cardLocator(riderName)).isNotEmpty()

    /** The sort orders of the Requests tab. */
    enum class Sort(val option: String) { OLDEST("Oldest First"), NEWEST("Newest First"), RIDER("Rider Name (A-Z)") }

    private val sortLabel: By = Ids.by("tv_sort")
    private val searchIcon: By = Ids.by("iv_search")

    /** The short label next to the sort control: "Oldest", "Newest", ... */
    fun sortLabelText(): String = textOf(sortLabel)

    /** Opens the sort sheet and returns its option texts, leaving it open. */
    fun openSortOptions(): List<String> {
        tap(sortLabel)
        waits.visible(By.xpath("//*[@text='Sort By']"))
        return driver.findElements(Ids.by("tv_title")).map { it.text }.filter { it != "Sort By" }
    }

    /** Closes the sort sheet without choosing. */
    fun closeSortOptions() { driver.navigate().back(); Thread.sleep(800) }

    fun sortBy(order: Sort): RideRequestsScreen {
        openSortOptions()
        driver.findElement(By.xpath("//*[@resource-id='${Ids.of("tv_title")}' and @text='${order.option}']")).click()
        Thread.sleep(2_000)
        return this
    }

    /** Opens the Requests tab with the newest rides first: the shared queue holds dozens of rides, a new one is at the top. */
    fun openQueue(): RideRequestsScreen { open(RequestsTab.REQUESTS); return sortBy(Sort.NEWEST) }

    /** Rider names of the cards on screen, top to bottom. */
    fun cardNames(): List<String> = driver.findElements(Ids.by("passenger_name")).map { it.text }

    /** The first card on screen. */
    fun firstCard(): RequestCard? {
        val first = By.xpath("(//*[@resource-id='${Ids.of("passenger_name")}']/ancestor::*[.//*[@resource-id='${Ids.of("time_ago")}']][1])[1]")
        return if (driver.findElements(first).isEmpty()) null else RequestCard { driver.findElement(first) }
    }

    fun openSearch(): SearchScreen { tap(searchIcon); return SearchScreen().waitUntilShown() }

    /** True when a piece of text (for example the "Last 24 Hours" header of the Completed tab) is on screen. */
    fun isTextShown(text: String): Boolean = waits.isVisibleNow(By.xpath("//*[@text='$text']"))

    private fun cardLocator(riderName: String): By {
        val unique = riderName.takeLast(13)
        // The card is the closest ancestor of the passenger name that also holds the status line (time_ago).
        return By.xpath(
            "//*[@resource-id='${Ids.of("passenger_name")}' and contains(@text,'$unique')]" +
                "/ancestor::*[.//*[@resource-id='${Ids.of("time_ago")}']][1]"
        )
    }
}

/** One card of the Assigned / In-progress / Completed lists. */
class RequestCard(private val find: () -> WebElement) {
    /** The card is found again when the list refreshes underneath it (the queue updates live). */
    private val root: WebElement get() = find()

    private fun <T> fresh(block: (WebElement) -> T): T {
        var last: Throwable? = null
        repeat(4) {
            try { return block(find()) } catch (e: org.openqa.selenium.StaleElementReferenceException) { last = e; Thread.sleep(400) }
        }
        throw last!!
    }

    private fun text(name: String): String? = fresh { r ->
        r.findElements(Ids.by(name)).firstOrNull()?.let { runCatching { it.text }.getOrNull() }
    }

    fun statusText(): String? = text("time_ago")           // "Trip not Started by Driver", "Canceled By Driver (at hh:mm)"
    fun pickupName(): String? = text("pickup_name")
    fun pickupAddress(): String? = text("title_pickup")
    fun dropoffName(): String? = text("drop_off_name")
    fun dropoffAddress(): String? = text("title_drop_off")
    fun passengerName(): String? = text("passenger_name")
    fun partySize(): String? = text("passenger_count")
    fun notes(): String? = text("notes")
    fun roomText(): String? = text("tv_room")
    fun flightText(): String? = text("tv_flight")
    fun requestedAt(): String? = text("tv_req_time")        // "| Just now" or "| 01:04 PM"
    fun driverName(): String? = text("driver_name_tv")      // "Automation Driver (You)" on the Completed tab
    fun assignButtonText(): String? = text("assign_other_driver")      // "ASSIGN TO DRIVER"
    fun assignedToYouText(): String? = text("add_to_trip")             // "ASSIGNED TO YOU"
    fun isAdaShown(): Boolean = fresh { r -> r.findElements(Ids.by("aiv_ada")).any { runCatching { it.isDisplayed }.getOrDefault(false) } }
    fun hasMoreOptions(): Boolean = fresh { r -> r.findElements(Ids.by("options_more")).isNotEmpty() }

    /** Opens the three-dots menu; a tap right after a tab switch can be lost, so it is repeated once if nothing opens. */
    fun openMenu(): CardMenuSheet {
        fresh { r -> r.findElement(Ids.by("options_more")).click() }
        val menu = CardMenuSheet()
        if (!menu.isShown(Duration.ofSeconds(5))) fresh { r -> r.findElement(Ids.by("options_more")).click() }
        return menu.waitUntilShown()
    }
    fun tapAssignToDriver() = fresh { r -> r.findElement(Ids.by("assign_other_driver")).click() }
    /** Stages the ride for the driver; the label turns to "ASSIGNED TO YOU" and the confirm bar appears. */
    fun tapAssignToSelf() = fresh { r -> r.findElement(Ids.by("add_to_trip")).click() }
    fun areAssignButtonsDisplayed(): Boolean =
        fresh { r -> listOf("assign_other_driver", "add_to_trip").all { n -> r.findElements(Ids.by(n)).any { runCatching { it.isDisplayed }.getOrDefault(false) } } }
}

/** The bar at the bottom of the Requests tab once a ride is staged with ASSIGN TO SELF. */
class AssignmentBar : BaseScreen() {
    private val container: By = Ids.by("btn_container")
    // The queue's bar reads "CONFIRM & START TRIP" (id btn_confirm); the Assigned tab's bar reads "CONFIRM" and has no id.
    private val confirm: By = By.xpath("//*[@resource-id='${Ids.of("btn_container")}']//*[@clickable='true' and starts-with(@text,'CONFIRM')]")
    private val cancel: By = By.xpath("//*[@resource-id='${Ids.of("btn_container")}']//*[@text='CANCEL']")

    fun isShown(timeout: Duration = Duration.ofSeconds(5)): Boolean = waits.appears(container, timeout)
    fun waitUntilShown(timeout: Duration = Duration.ofSeconds(10)): AssignmentBar { waits.visible(container, timeout); return this }
    fun isGone(timeout: Duration = Duration.ofSeconds(5)): Boolean = waits.gone(container, timeout)
    fun cancelText(): String = textOf(cancel)          // "CANCEL"
    fun confirmText(): String = textOf(confirm)        // "CONFIRM & START TRIP"
    fun cancel() = tap(cancel)
    fun confirm() = tap(confirm)
}

/** The search screen opened from the magnifier: needs two characters, finds riders and drivers. */
class SearchScreen : BaseScreen() {
    private val field: By = Ids.by("etv_search")
    private val message: By = Ids.by("no_ride_requests")
    private val close: By = Ids.by("atv_cancel_search_view")

    fun waitUntilShown(timeout: Duration = Duration.ofSeconds(10)): SearchScreen { waits.visible(field, timeout); return this }
    fun hintText(): String = textOf(message)           // "Search Driver or Rider", later "No Results Found"
    fun messageOrNull(): String? = driver.findElements(message).firstOrNull()?.takeIf { it.isDisplayed }?.text
    fun type(text: String): SearchScreen {
        waits.visible(field).also { it.clear(); it.sendKeys(text) }
        Thread.sleep(2_000)
        return this
    }
    fun resultNames(): List<String> = driver.findElements(Ids.by("passenger_name")).map { it.text }
    fun hasSectionTitle(title: String): Boolean = waits.isVisibleNow(By.xpath("//*[@resource-id='${Ids.of("tv_title")}' and @text='$title']"))
    fun close() = tap(close)
}

/** The dialog shown when the staged ride's party does not fit the shuttle: a message and an X. Ids confirmed live. */
class CapacityDialog : BaseScreen() {
    private val message: By = Ids.by("atv_label")
    private val close: By = Ids.by("cancel")

    fun waitUntilShown(timeout: Duration = Duration.ofSeconds(20)): CapacityDialog { waits.visible(message, timeout); return this }
    fun isShown(timeout: Duration = Duration.ofSeconds(2)): Boolean = waits.appears(message, timeout)
    fun messageText(): String = textOf(message)     // "Shuttle doesn't have enough capacity, please select another."
    fun close() = tap(close)
}
