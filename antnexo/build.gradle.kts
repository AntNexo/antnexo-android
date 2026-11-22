plugins { alias(libs.plugins.android.library) }

android {
    namespace = "com.antnexo"
    compileSdk = 36
    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        ignoreTestSources = true
        // versions move in their own PRs, not as lint noise
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }
    testOptions { unitTests { isIncludeAndroidResources = true } }
}


dependencies {
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)
    implementation(libs.webkit)
    implementation(libs.browser)
    implementation(libs.activity)
    implementation(libs.core.ktx)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
