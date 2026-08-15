pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
    plugins {
        kotlin("jvm") version "2.0.21"
        kotlin("plugin.serialization") version "2.0.21"
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "scout-remote-mobile"

// :core is pure Kotlin/JVM. No Android APIs (the spec §16.1 guardrail, enforced by
// module boundary). The :app Android module is bootstrapped at Phase 0 (see app/README.md)
// and added here when it exists.
include(":core")
