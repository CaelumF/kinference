import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompile
import org.jetbrains.research.kotlin.inference.kotlin

group = "org.jetbrains.research.kotlin.inference"
version = "0.1.0"

plugins {
    idea
    kotlin("jvm") version "1.3.72" apply true
    id("com.squareup.wire") version "3.1.0" apply true
    id("io.gitlab.arturbosch.detekt") version ("1.6.0") apply true
}

repositories {
    jcenter()
}

val generatedDir = "src/main/kotlin-gen"

wire {
    protoPath("src/main/proto")

    kotlin {
        out = generatedDir
    }
}

sourceSets {
    main {
        kotlin.srcDirs(generatedDir)
    }
}

tasks.compileTestKotlin{
    doFirst(){
        source = source.filter { "kotlin-gen" !in it.path }.asFileTree
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

tasks.test {
    useJUnitPlatform()
    maxHeapSize = "2048m"

    testLogging {
        events("passed", "skipped", "failed")
    }
}

dependencies {
    implementation(kotlin("stdlib"))
    api("com.squareup.wire", "wire-runtime", "3.1.0")
    testImplementation("org.junit.jupiter", "junit-jupiter", "5.6.2")
}
