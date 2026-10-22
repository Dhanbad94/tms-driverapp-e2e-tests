package tms.driver.screens.shell

import org.openqa.selenium.By
import tms.driver.screens.BaseScreen
import tms.driver.screens.Ids

/** Profile tab (`MyProfileFragment`): name, version, sign out (with confirmation dialog). */
class ProfileScreen : BaseScreen() {
    private val name: By = Ids.by("tv_name")
    private val version: By = Ids.by("tv_version")
    private val signOut: By = Ids.by("tv_signout")

    fun waitUntilShown(): ProfileScreen { waits.visible(signOut); return this }
    fun nameText(): String = textOf(name)
    fun versionText(): String = textOf(version)

    fun openEdit(): EditProfileScreen { tap(Ids.by("bt_edit")); return EditProfileScreen().waitUntilShown() }
    fun openChangePasscode(): ChangePasscodeScreen { tap(Ids.by("tv_change_password")); return ChangePasscodeScreen().waitUntilShown() }

    /** Opens the "Sign Out?" confirmation without confirming. */
    fun tapSignOut(): CommonDialog {
        tap(signOut)
        return CommonDialog().waitUntilShown()
    }
}
