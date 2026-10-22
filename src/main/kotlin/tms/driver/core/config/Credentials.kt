package tms.driver.core.config

/**
 * QA driver credentials, read ONLY from process environment variables
 * (`TMS_DRIVER_PHONE`, `TMS_DRIVER_PASSCODE`; optional `TMS_DRIVER_ISD`, `TMS_DRIVER_COUNTRY`).
 * Never stored in the repo; [toString] is masked so they cannot leak through logs or Allure.
 */
class Credentials private constructor(
    private val rawPhone: String,
    val passcode: String,
    isd: String?,
    country: String?,
) {
    /** Dial code selected in the country picker before typing the number (default India, +91). */
    val isdCode: String = isd ?: DEFAULT_ISD

    /** Country name searched for in the picker (default India). */
    val countryName: String = country ?: DEFAULT_COUNTRY

    /** Phone as typed on the Login screen: digits only, with a leading country code removed. */
    fun phoneDigits(): String {
        var digits = rawPhone.filter { it.isDigit() }
        val code = isdCode.filter { it.isDigit() }
        if (code.isNotEmpty() && digits.startsWith(code) && digits.length - code.length >= 7) {
            digits = digits.removePrefix(code)
        }
        return digits
    }

    /** A 4-digit passcode that is guaranteed to differ from the real one (every digit +1 mod 10). */
    fun wrongPasscode(): String = passcode.map { ((it - '0' + 1) % 10).toString() }.joinToString("")

    /** Another 4-digit passcode, different from the real one and from [wrongPasscode] when shift differs. */
    fun otherPasscode(shift: Int): String =
        passcode.map { ((it - '0' + shift) % 10).toString() }.joinToString("")

    override fun toString() = "Credentials(phone=***, passcode=****)"

    companion object {
        const val DEFAULT_ISD = "+91"
        const val DEFAULT_COUNTRY = "India"

        /** Returns null when the variables are not set, so tests can skip instead of fail. */
        fun fromEnvironment(): Credentials? {
            val phone = System.getenv("TMS_DRIVER_PHONE")?.takeIf { it.isNotBlank() } ?: return null
            val pass = System.getenv("TMS_DRIVER_PASSCODE")?.takeIf { it.isNotBlank() } ?: return null
            require(pass.length == 4 && pass.all { it.isDigit() }) { "TMS_DRIVER_PASSCODE must be 4 digits" }
            return Credentials(
                phone, pass,
                System.getenv("TMS_DRIVER_ISD")?.takeIf { it.isNotBlank() },
                System.getenv("TMS_DRIVER_COUNTRY")?.takeIf { it.isNotBlank() },
            )
        }
    }
}
