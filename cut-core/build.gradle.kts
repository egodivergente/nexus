import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Núcleo de Nexus Cut: modelo temporal, motor de edición, bus de comandos y registro de capacidades.
// Kotlin puro (sin Android): se prueba en la JVM y lo comparten la app, el agente y, más adelante, Desktop.
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}

tasks.test { maxHeapSize = "256m" }
