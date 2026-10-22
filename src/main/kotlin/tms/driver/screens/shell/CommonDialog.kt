package tms.driver.screens.shell

import org.openqa.selenium.By
import tms.driver.screens.BaseScreen
import tms.driver.screens.Ids

/** The app's shared alert dialog (`layout_common_dialog.xml`: tv_title, tv_msg, btn_positive, btn_negative). */
class CommonDialog : BaseScreen() {
    private val title: By = Ids.by("tv_title")
    private val message: By = Ids.by("tv_msg")
    private val positive: By = Ids.by("btn_positive")
    private val positiveVertical: By = Ids.by("btn_positive_vert")
    private val negative: By = Ids.by("btn_negative")

    fun waitUntilShown(): CommonDialog { waits.visible(title); return this }
    fun titleText(): String = textOf(title)
    fun messageText(): String = textOf(message)
    /** The dialog uses `btn_positive` or, in its vertical layout, `btn_positive_vert` (seen on Sign Out). */
    fun confirm() = tap(waits.anyVisible(positive, positiveVertical))
    fun cancel() = tap(negative)
    fun isShown(): Boolean = waits.isVisibleNow(title)
}
