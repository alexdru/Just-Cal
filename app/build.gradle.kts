plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}
android {
    namespace = "com.justcal.app"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.justcal.app"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    buildTypes {
        release {
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
