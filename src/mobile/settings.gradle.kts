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
        id("com.android.application") version "8.7.3"
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
// module boundary). :app is the Android client (see app/README.md).
include(":core")
include(":app")
