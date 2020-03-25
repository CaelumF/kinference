import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompile
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.gradle.api.internal.HasConvention

group = "org.jetbrains.research.kotlin.mpp.inference"
version = "0.1.0"

plugins {
    java
    idea
    kotlin("jvm") version "1.3.70" apply true
    id("com.squareup.wire") version "3.1.0" apply true
    id("io.gitlab.arturbosch.detekt") version ("1.6.0") apply true
}

repositories {
    jcenter()
    maven("https://dl.bintray.com/mipt-npm/scientifik")
}

val generatedDir = "${projectDir}/src/main/kotlin-gen"

wire {
    protoPath {
        srcDir("${projectDir}/src/main/proto")
    }
    kotlin {
        out = generatedDir
    }
}

val SourceSet.kotlin: SourceDirectorySet
    get() = (this as HasConvention)
        .convention
        .getPlugin(KotlinSourceSet::class.java)
        .kotlin

sourceSets {
    main {
        kotlin.srcDirs(generatedDir)
    }
}

idea {
    module.generatedSourceDirs.plusAssign(files(generatedDir))
}

detekt {
    parallel = true
    failFast = false
    config = files(File(rootProject.projectDir, "buildScripts/detekt/detekt.yml"))
    reports {
        xml.enabled = false
        html.enabled = false
    }
}

tasks.withType<KotlinJvmCompile> {
    kotlinOptions {
        jvmTarget = "1.8"
        languageVersion = "1.3"
        apiVersion = "1.3"
    }
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(kotlin("stdlib-jdk8"))
    api("com.squareup.wire:wire-runtime:3.1.0")
    api("scientifik", "kmath-core-jvm", "0.1.3")
}
