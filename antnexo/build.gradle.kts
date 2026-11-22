plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
}

android {
    publishing { singleVariant("release") { withSourcesJar() } }
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

// Maven coordinates com.antnexo:antnexo-android (JitPack builds them from a tag; Maven Central once the namespace is verified).
publishing {
    publications {
        register<MavenPublication>("release") {
            // JitPack sets GROUP (com.github.AntNexo.antnexo-android) and VERSION (the tag); Maven Central uses ours
            groupId = System.getenv("GROUP") ?: "com.antnexo"
            artifactId = "antnexo-android"
            version = System.getenv("VERSION") ?: rootProject.extra["sdkVersion"] as String
            afterEvaluate { from(components["release"]) }
            pom {
                name.set("antnexo-android")
                description.set("AntNexo feedback board, roadmap and What's new in Android apps")
                url.set("https://github.com/AntNexo/antnexo-android")
                licenses { license { name.set("MIT"); url.set("https://opensource.org/licenses/MIT") } }
                developers { developer { name.set("AntNexo"); url.set("https://antnexo.com") } }
                scm { url.set("https://github.com/AntNexo/antnexo-android") }
            }
        }
    }
}
