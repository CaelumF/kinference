import io.kinference.gradle.Versions
import io.kinference.gradle.configureTests

group = rootProject.group
version = rootProject.version

repositories {
    maven("https://repo.kotlin.link")
}

kotlin {
    jvm {
        configureTests()
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
                implementation(project(":utils:utils-testing"))
            }
        }

        val jvmMain by getting {
            dependencies {
                api(project(":inference:inference-ort"))
            }
        }
    }
}
