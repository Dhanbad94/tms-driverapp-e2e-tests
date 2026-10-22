package tms.driver.screens.home

import org.openqa.selenium.By
import tms.driver.screens.BaseScreen
import tms.driver.screens.Ids
import java.time.Duration

/**
 * Home dashboard of the on-demand (ASAP) driver (`MainFragment`).
 * UNVERIFIED: these ids come from the Phase 1 layout analysis and have not yet been checked against a live
 * dump of a home screen that has a trip, because no trip could be created yet.
 * The empty-state ids (`atv_no_dispatch`) were seen in a live dump of the "no trips" home screen.
 */
class OdHomeScreen : BaseScreen() {
    private val emptyState: By = Ids.by("atv_no_dispatch")
    private val dispatchSummary: By = Ids.by("atv_label")
    private val startButton: By = Ids.by("btn_go")

    fun isEmptyStateShown(): Boolean = waits.isVisibleNow(emptyState)
    fun hasDispatchSummary(): Boolean = waits.isVisibleNow(dispatchSummary)
    fun isStartButtonShown(): Boolean = waits.isVisibleNow(startButton)
    /** Waits until the dashboard shows a trip (summary or start button); false if none appears in time. */
    fun waitForTrip(timeout: Duration): Boolean =
        runCatching { waits.anyVisible(dispatchSummary, startButton, timeout = timeout) }.isSuccess

    fun startButtonText(): String = textOf(startButton)
    fun summaryText(): String = textOf(dispatchSummary)
}
