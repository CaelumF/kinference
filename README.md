<h1> <img align="center" width="64" height="64" src="https://s3-eu-west-1.amazonaws.com/public-resources.ml-labs.aws.intellij.net/static/kinference/icon_256.png" alt="KInference Icon"> KInference </h1>

[![JB Research](https://jb.gg/badges/research-flat-square.svg)](https://research.jetbrains.org/)

KInference is a library that makes possible execution of complex ML models (written via ONNX) in Kotlin.

[ONNX](https://github.com/onnx/onnx) is a popular ecosystem for building, training, evaluating, and exchanging ML and DL models. It makes the process much
simpler and divides the model into building blocks that can be switched or tuned to one's liking.

However, ONNX carries with itself a lot of dependencies and requirements that complicate its use in some cases. 
KInference is designed to facilitate ONNX models inference on a variety of platforms via configurable backends.
Our library addresses the problem not only of server side inference, but also of local inference as well and provides 
several solutions that are suitable for running both on user and server side. 

Right now, KInference is in active development.

### Table of contents:
* [Why should I use KInference?](#why-should-i-use-kinference)
* [KInference Backends](#kinference-backends)
* [Third-party math libraries adapters](#third-party-math-libraries-adapters)
* [Getting started](#getting-started)
* [Examples](#examples)

### Why should I use KInference?

* **KInference is specifically optimized for inference.**
  Most of the existing ML libraries are, in fact, versatile tools for model learning and inference, 
  but carry with themselves a lot of dependencies and requirements. KInference instead addresses inference-only functionality
  to help to facilitate model inference with a relatively small yet convenient API and inference-specific optimizations.

* **KInference has pure-JS and pure-JVM backends** that make possible running models anywhere where JS or JVM virtual machine is available.
  In addition, you can switch between the chosen backends using backend configuration.

* **KInference supports configurable backends.**
  KInference employs platform-specific optimizations and allows essential for multiplatform projects backend configuration.
  You can choose backend for every module in the `build.gradle.kts` project file just by adding there corresponding dependencies, 
  while keeping most of your KInference-related code in common module.

* **KInference enables data preprocessing.** 
  We understand that data needs preprocessing before feeding it to the model and that is why we implemented numpy-like n-dimensional arrays.
  In addition, KInference can work with custom array formats and some of them are available just out-of-the-box
  (see [multik](https://github.com/Kotlin/multik), 
  [kmath](https://github.com/SciProgCentre/kmath)).

## KInference backends

### KInference Core
Pure Kotlin implementation that requires anything but vanilla Kotlin. KInference Core is lightweight but fast, and supports numerous ONNX operators.
It makes the library easy to use and especially convenient for various applications that require the models to run locally on users' machines.
Note that this backend is well-optimized for JVM projects only, and, despite the fact that KInference Core is available for JavaScript projects, 
it is highly recommended to use KInference TensorFlow.js backend instead for more performance.

Dependency coordinates:
```kotlin
dependencies {
    api("io.kinference", "inference-core", "0.2.2")
}
```

### TensorFlow.js
High-performance JavaScript backend that relies on [Tensorflow.js](https://www.tensorflow.org/js/) library. 
Essentially, it employs GPU operations provided by TensorFlow.js to boost the computations. 
In addition, this implementation enables model execution directly in the user's browser.
Recommended backend for JavaScript projects.

Dependency coordinates:
```kotlin
dependencies {
    api("io.kinference", "inference-tfjs", "0.2.2")
}
```

### ONNXRuntime CPU and ONNXRuntime GPU
Java backends that use [ONNXRuntime](https://github.com/microsoft/onnxruntime) as an inference engine 
and provide common KInference API to interact with ONNXRuntime library.

Note that the GPU backend is **CUDA-only**.
To check on the system requirements, visit the following [link](https://onnxruntime.ai/docs/execution-providers/CUDA-ExecutionProvider.html#requirements)

ONNXRuntime backend dependency coordinates:
```kotlin
dependencies {
    api("io.kinference", "inference-ort", "0.2.2")
}
```

ONNXRuntime GPU backend dependency coordinates:
```kotlin
dependencies {
    api("io.kinference", "inference-ort-gpu", "0.2.2")
}
```

## Third-party math libraries adapters
KInference works with custom array formats, and some of them are available just out-of-the-box.
Basically, adapters enable working with familiar array formats and libraries. 
You can use several third-party Kotlin math libraries with KInference via our data adapters.
In addition to the library adapters listed below, you can implement your own adapters using KInference adapters API.

### KMath adapter
Array adapter for [kmath](https://github.com/SciProgCentre/kmath) library that works with JVM KInference backends.

Dependency coordinates:
```kotlin
dependencies {
    api("io.kinference", "adater-kmath-{backend_name}", "0.2.2")
}
```

### Multik adapter
Array adapter for [multik](https://github.com/Kotlin/multik) library that works with JVM KInference backends.

Dependency coordinates:
```kotlin
dependencies {
    api("io.kinference", "adater-multik-{backend_name}", "0.2.2")
}
```

## Getting started
Latest version of KInference is: *0.2.2*

### Setup dependencies repository

First, in `build.gradle.kts` you should add KInference repository via:

```kotlin
repositories {
    maven {
        url = uri("https://packages.jetbrains.team/maven/p/ki/maven")
    }
}
```

### Project setup
To enable the backend, you can add chosen KInference Runtime as dependency:

```kotlin
dependencies {
    api("io.kinference", "inference-core", "0.2.2")
}
```

### Multi-backend project setup
To configure individual KInference backend for each target, you should add corresponding backends to the dependencies.

```kotlin
kotlin {
    jvm {}

    js(IR) {
        browser()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api("io.kinference:inference-api:0.2.2")
                api("io.kinference:ndarray-api:0.2.2")
            }
        }

        val jvmMain by getting {
            dependencies {
                api("io.kinference:inference-core:0.2.2")
            }
        }

        val jsMain by getting {
            dependencies {
                api("io.kinference:inference-tfjs:0.2.2")
            }
        }
    }
}
```

## Examples
You can find several KInference usage examples in [this repository](https://github.com/JetBrains-Research/kinference-examples).
The repository has examples of multi-backend project configuration and sharing KInference-related code between the modules.

## Want to know more?
KInference API itself is widely documented, so you can explore its code and interfaces to get to know KInference better.

You may also submit feedback and ask questions on repository issues and issues discussions.
