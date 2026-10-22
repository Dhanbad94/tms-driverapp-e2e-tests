import org.gradle.api.tasks.testing.Test

plugins {
    kotlin("jvm") version "2.0.21"
}

group = "com.trackmyshuttle.e2e"
version = "0.1.0"

repositories {
    mavenCentral()
}

// Versions are pinned here and are UNVERIFIED until the first `gradle dependencies` resolve
// (no JDK/Gradle/network resolve was available when this file was written).
val appiumClient = "9.3.0"
val junit = "5.11.3"
val allure = "2.29.0"

dependencies {
    implementation("io.appium:java-client:$appiumClient")
    implementation("io.qameta.allure:allure-java-commons:$allure")
    implementation("org.slf4j:slf4j-api:2.0.16")
    runtimeOnly("ch.qos.logback:logback-classic:1.5.12")

    testImplementation(platform("org.junit:junit-bom:$junit"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("io.qameta.allure:allure-junit5:$allure")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(17)
}

// Settings forwarded to the test JVM. Secrets are NOT forwarded from build files; they are read
// from process environment variables at runtime (see README).
fun Test.configureCommon() {
    useJUnitPlatform()
    systemProperty("tms.env", (findProperty("env") ?: System.getenv("TMS_ENV") ?: "staging").toString())
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = false
    }
}

// Default `test`: only tests that need NO device or Appium server (tag "config").
tasks.test {
    configureCommon()
    useJUnitPlatform { includeTags("config") }
}

// Device-backed launch smoke test (needs Appium server + device/emulator + APK installed or TMS_APK_PATH).
tasks.register<Test>("smokeTest") {
    group = "verification"
    description = "Runs @smoke tests against a live Appium session."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    configureCommon()
    useJUnitPlatform { includeTags("smoke") }
}

// Whole authentication slice (needs TMS_DRIVER_PHONE / TMS_DRIVER_PASSCODE). Excludes the SMS-sending test.
tasks.register<Test>("authTest") {
    group = "verification"
    description = "Runs @auth tests (excluding @sms) against a live Appium session."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    configureCommon()
    useJUnitPlatform { includeTags("auth"); excludeTags("sms") }
}

// Sends a real SMS to the QA phone (Forgot passcode + Resend). Run deliberately.
tasks.register<Test>("smsTest") {
    group = "verification"
    description = "Runs @sms tests (sends real SMS)."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    configureCommon()
    useJUnitPlatform { includeTags("sms") }
}

// Trip lifecycle tests (need TMS_TRIP_API_URL, TMS_TRIP_DRIVER_ID and the QA driver credentials; skipped otherwise).
tasks.register<Test>("tripTest") {
    group = "verification"
    description = "Runs @trip tests against a live Appium session."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    configureCommon()
    useJUnitPlatform { includeTags("trip") }
}

// Everything that runs on a device except the SMS-sending tests: the scheduled / CI regression run.
// One task so a test tagged both smoke and auth runs once.
tasks.register<Test>("regressionTest") {
    group = "verification"
    description = "Runs @smoke, @auth and @trip tests (excluding @sms) against a live Appium session."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    configureCommon()
    useJUnitPlatform { includeTags("smoke | auth | trip"); excludeTags("sms") }
}
