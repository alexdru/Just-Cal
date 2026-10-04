import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}
val appVersion = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}
val releaseVersionName = requireNotNull(appVersion.getProperty("versionName"))
val versionCodeText = requireNotNull(appVersion.getProperty("versionCode"))
require(Regex("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)").matches(releaseVersionName)) {
    "versionName must be MAJOR.MINOR.PATCH without a v prefix or leading zeroes."
}
val releaseVersionCode = requireNotNull(versionCodeText.toIntOrNull()) {
    "versionCode must be an integer."
}
require(releaseVersionCode in 1..2_100_000_000) {
    "versionCode must be a positive integer no greater than Android's limit of 2100000000."
}
require(versionCodeText == releaseVersionCode.toString()) {
    "versionCode must not contain leading zeroes, whitespace or a sign."
}

val signingVariables = listOf("ANDROID_KEYSTORE_PATH", "ANDROID_KEYSTORE_PASSWORD",
    "ANDROID_KEY_ALIAS", "ANDROID_KEY_PASSWORD")
val signingValues = signingVariables.associateWith { providers.environmentVariable(it).orNull }
val hasReleaseSigning = signingValues.values.all { !it.isNullOrBlank() }

val validateReleaseSigning = tasks.register("validateReleaseSigning") {
    group = "verification"
    description = "Reject release packaging without complete local/CI signing credentials."
    doLast {
        val missing = signingValues.filterValues { it.isNullOrBlank() }.keys
        check(missing.isEmpty()) {
            "Release signing is missing: ${missing.joinToString()}. See docs/RELEASING.md."
        }
        check(file(requireNotNull(signingValues["ANDROID_KEYSTORE_PATH"])).isFile) {
            "ANDROID_KEYSTORE_PATH must point to an existing keystore. See docs/RELEASING.md."
        }
    }
}

tasks.register("validateReleaseVersion") {
    group = "verification"
    description = "Validate the explicit Android version against RELEASE_TAG."
    val tag = providers.environmentVariable("RELEASE_TAG")
    doLast {
        check(tag.orNull == "v$releaseVersionName") {
            "RELEASE_TAG must be v$releaseVersionName, matching version.properties."
        }
        logger.lifecycle("Release version: $releaseVersionName ($releaseVersionCode)")
    }
}

// Keep compile/lint/debug usable without secrets; never package an unsigned release.
tasks.matching { it.name in setOf("assembleRelease", "bundleRelease", "packageRelease", "signReleaseBundle") }
    .configureEach { dependsOn(validateReleaseSigning) }

android {
    namespace = "com.justcal.app"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.justcal.app"
        minSdk = 30
        targetSdk = 37
        versionCode = releaseVersionCode
        versionName = releaseVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
    // AGP marks this supported locale-generation DSL as incubating.
    @Suppress("UnstableApiUsage")
    androidResources {
        generateLocaleConfig = true
        localeFilters += listOf("en", "ru")
    }
    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(requireNotNull(signingValues["ANDROID_KEYSTORE_PATH"]))
                storePassword = signingValues["ANDROID_KEYSTORE_PASSWORD"]
                keyAlias = signingValues["ANDROID_KEY_ALIAS"]
                keyPassword = signingValues["ANDROID_KEY_PASSWORD"]
            }
        }
    }
    buildTypes {
        release {
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
room { schemaDirectory("$projectDir/schemas") }
dependencies {
    implementation(platform(libs.compose.bom))
    androidTestImplementation(platform(libs.compose.bom))
    implementation(libs.core)
    implementation(libs.activity)
    implementation(libs.appcompat)
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.material3)
    implementation(libs.preview)
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.navigation)
    implementation(libs.navigation.runtime)
    implementation(libs.navigation.ui)
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.viewmodel)
    implementation(libs.datastore)
    implementation(libs.coroutines)
    implementation(libs.serialization)
    debugImplementation(libs.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(libs.test.junit)
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.test.core)
    androidTestImplementation(libs.test.espresso)
    androidTestImplementation(libs.ui.test)
    debugImplementation(libs.ui.test.manifest)
}
