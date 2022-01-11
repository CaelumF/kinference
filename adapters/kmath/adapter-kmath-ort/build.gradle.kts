import io.kinference.gradle.configureTests
import io.kinference.gradle.Versions

group = rootProject.group
version = rootProject.version

repositories {
    maven("https://repo.kotlin.link")
}

kotlin {
    jvm {
        testRuns["test"].executionTask {
            configureTests()

            enabled = !project.hasProperty("disable-tests")
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":inference:inference-api"))
                api("space.kscience:kmath-core:${Versions.kmath}")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-annotations-common"))
                implementation(project(":utils:test-utils"))
            }
        }

        val jvmMain by getting {
            dependencies {
                api(project(":inference:inference-ort"))
            }
        }

        val jvmTest by getting {
            dependencies {
                api("org.slf4j:slf4j-simple:${Versions.slf4j}")
            }
        }
    }
}
