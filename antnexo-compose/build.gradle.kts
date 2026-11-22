plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    `maven-publish`
}

android {
    publishing { singleVariant("release") { withSourcesJar() } }
    namespace = "com.antnexo.compose"
    compileSdk = 36
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        ignoreTestSources = true
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }
}

dependencies {
    api(project(":antnexo"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)
}

// Maven coordinates com.antnexo:antnexo-android-compose (JitPack builds them from a tag; Maven Central once the namespace is verified).
publishing {
    publications {
        register<MavenPublication>("release") {
            // JitPack sets GROUP (com.github.AntNexo.antnexo-android) and VERSION (the tag); Maven Central uses ours
            groupId = System.getenv("GROUP") ?: "com.antnexo"
            artifactId = "antnexo-android-compose"
            version = System.getenv("VERSION") ?: rootProject.extra["sdkVersion"] as String
            afterEvaluate { from(components["release"]) }
            pom {
                name.set("antnexo-android-compose")
                description.set("Jetpack Compose sheet for the AntNexo Android SDK")
                url.set("https://github.com/AntNexo/antnexo-android")
                licenses { license { name.set("MIT"); url.set("https://opensource.org/licenses/MIT") } }
                developers { developer { name.set("AntNexo"); url.set("https://antnexo.com") } }
                scm { url.set("https://github.com/AntNexo/antnexo-android") }
            }
        }
    }
}
