plugins {
    kotlin("multiplatform") version "2.3.21"
    id("publishing-conventions")
}

group = "nl.ncaj.ftxui"

repositories {
    mavenLocal()
    mavenCentral()
}

publishing {
    publications.withType<MavenPublication>().all {
        pom {
            description.set("Opinionated TUI application framework for Kotlin Multiplatform Native, built on ftxui-kt")
        }
    }
}

kotlin {
    macosArm64()
    linuxX64()

    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget> {
        binaries {
            executable {
                entryPoint = "demoMain"
                baseName = "ftxui-kt-framework"
            }
        }
    }

    sourceSets {
        all {
            languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi")
            languageSettings.optIn("kotlin.experimental.ExperimentalNativeApi")
            languageSettings.optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
            languageSettings.optIn("kotlinx.coroutines.DelicateCoroutinesApi")
        }
        nativeMain {
            dependencies {
                implementation("nl.ncaj.ftxui:ftxui-kt-dsl:1.2.0")
                implementation("nl.ncaj.ftxui:ftxui-kt:1.2.4")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            }
        }
    }
}
