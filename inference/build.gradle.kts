import io.kinference.gradle.useBenchmarkTests
import io.kinference.gradle.useHeavyTests
import tanvd.kosogor.proxy.publishJar

group = rootProject.group
version = rootProject.version

plugins {
    kotlin("kapt") apply true
}

tasks.test {
    useJUnitPlatform {
        excludeTags("heavy")
        excludeTags("benchmark")
    }
    maxHeapSize = "20m"

    testLogging {
        events("passed", "skipped", "failed")
    }
}


useHeavyTests()
useBenchmarkTests()

dependencies {
    api(project(":ndarray"))
    implementation(project(":reader"))

    api("ch.qos.logback", "logback-classic", "1.2.3")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.8")

    testImplementation("org.openjdk.jmh", "jmh-core", "1.25.1")
    testImplementation("org.openjdk.jmh", "jmh-generator-annprocess", "1.25.1")
    kaptTest("org.openjdk.jmh", "jmh-generator-annprocess", "1.25.1")

    testImplementation("org.junit.jupiter", "junit-jupiter", "5.6.2")
    testImplementation("com.microsoft.onnxruntime", "onnxruntime", "1.4.0")
    testImplementation("org.jetbrains.kotlinx", "kotlinx-serialization-json", "1.0.1")

    testImplementation(project(":loaders"))
}


publishJar {
    bintray {
        username = "tanvd"
        repository = "io.kinference"
        info {
            description = "KInference inference module"
            vcsUrl = "https://github.com/JetBrains-Research/kinference"
            githubRepo = "https://github.com/JetBrains-Research/kinference"
            labels.addAll(listOf("kotlin", "inference", "ml"))
        }
    }
}

