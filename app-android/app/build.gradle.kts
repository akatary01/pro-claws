import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use(::load)
    }
}
val localMapboxPublicToken = providers.provider {
    localProperties.getProperty("MAPBOX_PUBLIC_TOKEN")
        ?: localProperties.getProperty("MAPBOX_ACCESS_TOKEN")
        ?: ""
}
val mapboxPublicToken = providers.gradleProperty("MAPBOX_PUBLIC_TOKEN")
    .orElse(providers.gradleProperty("MAPBOX_ACCESS_TOKEN"))
    .orElse(providers.environmentVariable("MAPBOX_PUBLIC_TOKEN"))
    .orElse(providers.environmentVariable("MAPBOX_ACCESS_TOKEN"))
    .orElse(localMapboxPublicToken)
    .get()

android {
    namespace = "com.vendistri.operations"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.vendistri.operations"
        minSdk = 26
        targetSdk = 36
        versionCode = 11
        versionName = "1.0.10"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        resValue("string", "mapbox_access_token", mapboxPublicToken)
    }

    buildTypes {
        debug {
            buildConfigField("String", "BACKEND_URL", "\"http://10.0.2.2:8000/vendistri/be\"")
            buildConfigField("String", "APP_WEB_URL", "\"http://10.0.2.2:3000\"")
            buildConfigField("String", "SIGNUP_WEB_URL", "\"http://10.0.2.2:5173\"")
            buildConfigField("String", "SERVICE_FORM_WEB_URL", "\"http://10.0.2.2:3000\"")
        }
        release {
            buildConfigField("String", "BACKEND_URL", "\"https://secure.vendistri.com/vendistri/be\"")
            buildConfigField("String", "APP_WEB_URL", "\"https://vendistri.com\"")
            buildConfigField("String", "SIGNUP_WEB_URL", "\"https://vendistri.com\"")
            buildConfigField("String", "SERVICE_FORM_WEB_URL", "\"https://vendistri.com\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            ndk {
                debugSymbolLevel = "FULL"
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.mapbox.maps)
    implementation(libs.mapbox.maps.compose)
    implementation(libs.mapbox.navigation.core)
    implementation(libs.mapbox.navigation.ui.maps)
    implementation(libs.okhttp)
    testImplementation(libs.json)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
