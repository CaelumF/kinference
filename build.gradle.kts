import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompile

group = "io.kinference"
version = "0.1.1"

plugins {
    id("tanvd.kosogor") version "1.0.10" apply true
    idea apply true

    kotlin("jvm") version "1.4.20" apply false

    id("io.gitlab.arturbosch.detekt") version ("1.11.0") apply true
}

allprojects {
    repositories {
        jcenter()
        gradlePluginPortal()
    }
}

subprojects {
    apply {
        plugin("tanvd.kosogor")
        plugin("idea")

        plugin("kotlin")

        plugin("io.gitlab.arturbosch.detekt")
    }

    tasks.withType<KotlinJvmCompile> {
        kotlinOptions {
            jvmTarget = "11"
            languageVersion = "1.4"
            apiVersion = "1.4"
            freeCompilerArgs = freeCompilerArgs + listOf("-Xopt-in=kotlin.RequiresOptIn")
        }
    }

    detekt {
        parallel = true

        config = rootProject.files("detekt.yml")

        reports {
            xml.enabled = false
            html.enabled = false
        }
    }
}
