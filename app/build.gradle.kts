plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "co.topflighthealth.app"
    compileSdk = 37

    defaultConfig {
        // Permanent once the app is on Google Play. Never change it.
        applicationId = "co.topflighthealth.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        // AppAuth catches the sign-in callback on this scheme. It must match
        // ANDROID_REDIRECT_URI in TFHReact2024/packages/functions/src/auth/constants.ts.
        manifestPlaceholders["appAuthRedirectScheme"] = "co.topflighthealth.app"
    }

    // Two versions of the app, like AppConstants.env on iOS:
    // dev talks to devapi + the dev auth server, prod to the live ones.
    flavorDimensions += "env"
    productFlavors {
        create("dev") {
            dimension = "env"
            resValue("string", "app_name", "TFH Dev")
            buildConfigField("String", "ENV", "\"dev\"")
            buildConfigField("String", "API_BASE_URL", "\"https://devapi.topflighthealth.co/v1/\"")
            buildConfigField("String", "AUTH_AUTHORIZE_URL", "\"https://pphauxbu5zoj237toizmykbxca0ifwys.lambda-url.us-east-1.on.aws/authorize\"")
            buildConfigField("String", "AUTH_TOKEN_URL", "\"https://pphauxbu5zoj237toizmykbxca0ifwys.lambda-url.us-east-1.on.aws/token\"")
        }
        create("prod") {
            dimension = "env"
            resValue("string", "app_name", "Top Flight Health")
            buildConfigField("String", "ENV", "\"prod\"")
            buildConfigField("String", "API_BASE_URL", "\"https://api.topflighthealth.co/v1/\"")
            buildConfigField("String", "AUTH_AUTHORIZE_URL", "\"https://auth.topflighthealth.co/authorize\"")
            buildConfigField("String", "AUTH_TOKEN_URL", "\"https://auth.topflighthealth.co/token\"")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.browser)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.appauth)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
