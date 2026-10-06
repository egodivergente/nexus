plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    application
}
kotlin { jvmToolchain(17) }
dependencies {
    implementation(project(":core"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
application { mainClass.set("DesktopAgentKt") }
tasks.register<Copy>("runtime") {
    dependsOn(tasks.jar, ":core:jar")
    from(configurations.runtimeClasspath, tasks.jar)
    into(layout.buildDirectory.dir("runtime"))
}
