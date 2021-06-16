package io.kinference.gradle.s3

import java.io.File

class Config(file: File) {
    data class Credentials(val awsAccessKey: String, val awsSecretKey: String)

    private val credentials: Credentials by lazy {
        file.readText().split("\n").map { it.trim() }.let { Credentials(it[0], it[1]) }
    }

    val awsAccessKey: String
        get() = System.getenv("AWS_ACCESS_KEY") ?: credentials.awsAccessKey

    val awsSecretKey: String
        get() = System.getenv("AWS_SECRET_KEY") ?: credentials.awsSecretKey
}
