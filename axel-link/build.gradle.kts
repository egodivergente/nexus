// AXEL Link: cliente único de AXEL Commander para AXEL Hub, Nexus y Nexus Desktop.
// Kotlin puro (JVM), sin Android: solo OkHttp. Nexus lleva una copia idéntica de estas fuentes.
plugins {
    id("org.jetbrains.kotlin.jvm")
}
// Sin toolchain fijo: compila con cualquier JDK 17 o posterior, igual en AXELPC que en CodeBuild.
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("junit:junit:4.13.2")
}
tasks.test { useJUnit() }
