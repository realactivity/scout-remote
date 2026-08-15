import org.gradle.api.artifacts.dsl.LockMode

plugins {
    kotlin("jvm") apply false
    kotlin("plugin.serialization") apply false
    kotlin("android") apply false
    id("org.jetbrains.kotlin.plugin.compose") apply false
    id("com.android.application") apply false
}

allprojects {
    dependencyLocking {
        lockAllConfigurations()
        lockMode.set(LockMode.STRICT)
    }
}
