plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.antnexo.sample"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.antnexo.sample"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        // The board it opens; override with `adb shell am start … --es host <url> --es workspace <slug>`.
        buildConfigField("String", "ANTNEXO_HOST", "\"https://splynx.antnexo.com\"")
        buildConfigField("String", "ANTNEXO_WORKSPACE", "\"splynx\"")
    }
    buildFeatures { buildConfig = true }
    buildTypes {
        release {
            // R8 on, to check the library needs no keep rules
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        // a sample: no icon, and it targets what the library is built against
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion", "OldTargetApi",
            "MissingApplicationIcon")
    }
}

dependencies {
    implementation(project(":antnexo-compose"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
}
