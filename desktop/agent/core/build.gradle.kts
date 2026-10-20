import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Núcleo del agente: Kotlin puro (sin Android) para que se pruebe en la JVM y se incluya en Nexus
// como módulo (`implementation(project(":agent-core"))`) sin tocar el resto de la app.
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
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // YAML 1.2 en Java puro (compatible con Android) para las cabeceras de las skills.
    implementation("org.snakeyaml:snakeyaml-engine:2.7")

    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

tasks.test {
    useJUnitPlatform()
    // Las pruebas de skills leen la carpeta real del repositorio.
    systemProperty("nexus.skills.dir", rootProject.file("agent-skills/skills").absolutePath)
}

// Compile the authoritative Android agent source directly: duplicate trees are not inputs.
sourceSets {
    main { kotlin.setSrcDirs(listOf("../../../agent-core/src/main/kotlin")) }
    test { kotlin.setSrcDirs(listOf("../../../agent-core/src/test/kotlin")); resources.setSrcDirs(listOf("../../../agent-core/src/test/resources")) }
}
