plugins {
    kotlin("jvm") version "2.3.20"
    kotlin("plugin.serialization") version "2.3.20"
    application
}
kotlin { jvmToolchain(17) }
dependencies {
    implementation(project(":core"))
    implementation(project(":corte-timeline"))
    implementation(project(":corte-storage"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
tasks.test { useJUnitPlatform() }
allprojects {
    tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
        maxParallelForks = 1
        maxHeapSize = "256m"
        jvmArgs("-XX:+UseSerialGC", "-XX:ActiveProcessorCount=2")
    }
}
application { mainClass.set("DesktopAgentKt") }
tasks.register<Copy>("runtime") {
    dependsOn(tasks.jar, ":core:jar")
    from(configurations.runtimeClasspath, tasks.jar)
    into(layout.buildDirectory.dir("runtime"))
}
