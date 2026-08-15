pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
    plugins {
        kotlin("jvm") version "2.0.21"
        kotlin("plugin.serialization") version "2.0.21"
        kotlin("android") version "2.0.21"
        id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
        id("com.android.application") version "9.3.1"
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
        maven {
            url = uri("https://pkgs.dev.azure.com/MicrosoftDeviceSDK/DuoSDK-Public/_packaging/Duo-SDK-Feed/maven/v1")
        }
    }
}

rootProject.name = "lantern-mobile"

// :core is pure Kotlin/JVM. No Android APIs (the spec §16.1 guardrail, enforced by
// module boundary). :app is included when an Android SDK is configured so
// `./gradlew :core:build` still works on machines without the SDK.
include(":core")
val androidHome = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
val localProperties = file("local.properties")
if (!androidHome.isNullOrBlank() || localProperties.exists()) {
    include(":app")
}
