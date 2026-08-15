import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.lantern.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.lantern.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.1"
        buildConfigField("String", "LANTERN_CLIENT_ID", "\"${System.getenv("LANTERN_CLIENT_ID") ?: ""}\"")
        buildConfigField("String", "LANTERN_TENANT_ID", "\"${System.getenv("LANTERN_TENANT_ID") ?: ""}\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

val brandTokens = rootDir.resolve("../../docs/brand-tokens.json")
val copyBrandTokens = tasks.register<Copy>("copyBrandTokens") {
    from(brandTokens)
    into(layout.projectDirectory.dir("src/main/assets"))
    from(brandTokens)
    into(layout.projectDirectory.dir("src/test/resources"))
}
tasks.named("preBuild").configure { dependsOn(copyBrandTokens) }
tasks.configureEach {
    if (name.startsWith("test") || name.contains("UnitTest")) {
        dependsOn(copyBrandTokens)
    }
}

dependencies {
    implementation(project(":core"))
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-text")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("com.microsoft.identity.client:msal:5.10.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation(kotlin("test"))
}
