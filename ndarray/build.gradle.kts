group = rootProject.group
version = rootProject.version

plugins {
    id("io.kinference.primitives") version "0.1.12" apply true
}

kotlin {
    jvm {

    }

    js {
        browser {
            testTask {
                useKarma {
                    useChrome()
                }
            }
        }

        useCommonJs()
    }

    sourceSets {
        val commonMain by getting {
            repositories {
                mavenCentral()
                maven(url = "https://packages.jetbrains.team/maven/p/ki/maven")
            }

            dependencies {
                api(kotlin("stdlib"))
                api("io.kinference.primitives:primitives-annotations:0.1.12")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.2")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }

        val jvmTest by getting {
            dependsOn(commonTest)
            dependencies {
                implementation(kotlin("test-junit"))
            }
        }

        val jsMain by getting {
            dependencies {
                implementation(npm("regl", "2.0.1"))
            }
        }

        val jsTest by getting {
            dependsOn(commonTest)
            dependencies {
                implementation(kotlin("test-js"))
            }
        }
    }
}

