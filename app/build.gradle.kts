import java.util.Properties
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Nombre de versiÃ³n: cada 10 versionCode cambia el nombre (vc 70â€“79 = Horizonte, 80â€“89 = Ãndigoâ€¦) para orientarse de un vistazo.
val versionCodenames = listOf("Aurora", "Boreal", "Cénit", "Dorado", "Eclipse", "Fénix", "Glaciar", "Horizonte", "Índigo", "Júpiter",
    "Kairos", "Lumen", "Mistral", "Nébula", "Ónix", "Polaris", "Quásar", "Ródano", "Sirio", "Tundra")
fun versionCodename(code: Int) = versionCodenames[(code / 10) % versionCodenames.size]

android {
    namespace = "com.vidadomu.nexus"
    compileSdk = 35
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "com.vidadomu.nexus"
        minSdk = 26
        targetSdk = 35
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        externalNativeBuild {cmake {arguments += listOf("-DCMAKE_BUILD_TYPE=Release", "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON")}}
        versionCode = 164
        versionName = "1.9.128-agent"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("boolean", "OFFLINE_QA", "false")
        buildConfigField("String", "CODENAME", "\"${versionCodename(versionCode!!)}\"")
        val revision = providers.gradleProperty("nexusRevision").orElse(providers.exec {
            commandLine("git", "rev-parse", "--short=12", "HEAD")
        }.standardOutput.asText.map { it.trim() }).get()
        buildConfigField("String", "SOURCE_REVISION", "\"${revision.replace(Regex("[^a-zA-Z0-9._-]"), "")}\"")
        buildConfigField("String", "BUILD_DATE", "\"${LocalDate.now(ZoneOffset.UTC)}\"")
        // Claves de Bedrock opcionales desde un fichero local FUERA del repo: -PnexusBedrockFile=<ruta a .properties>
        // (AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, AWS_DEFAULT_REGION, BEDROCK_MODEL_ID). Sin Ã©l quedan vacÃ­as.
        val bedrock = Properties().apply {
            (project.findProperty("nexusBedrockFile") as String?)?.let { f -> file(f).takeIf { it.isFile }?.inputStream()?.use { load(it) } }
        }
        fun bq(k: String) = "\"" + bedrock.getProperty(k, "").trim().replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        buildConfigField("String", "BEDROCK_ACCESS_KEY", bq("AWS_ACCESS_KEY_ID"))
        buildConfigField("String", "BEDROCK_SECRET_KEY", bq("AWS_SECRET_ACCESS_KEY"))
        buildConfigField("String", "BEDROCK_REGION", bq("AWS_DEFAULT_REGION"))
        buildConfigField("String", "BEDROCK_MODEL", bq("BEDROCK_MODEL_ID"))
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // Preserve the installed Agent certificate. This private file stays outside Git.
    val agentSigningFile = providers.gradleProperty("nexusAgentSigningFile").orElse(
        File(System.getProperty("user.home"), ".gradle/nexus-agent-signing.properties").absolutePath).get()
    val agentSigning = file(agentSigningFile).takeIf { it.isFile }?.let { configFile ->
        val values = Properties().apply { configFile.inputStream().use { load(it) } }
        signingConfigs.create("installedAgent") {
            storeFile = file(values.getProperty("storeFile"))
            storePassword = values.getProperty("storePassword")
            keyAlias = values.getProperty("keyAlias")
            keyPassword = values.getProperty("keyPassword")
        }
    }

    buildTypes {
        create("offlineQa") {
            initWith(getByName("debug"))
            // UI audit runs in its own sandbox, preserving earlier QA installations.
            applicationIdSuffix = when {project.hasProperty("nexusCanvasQa") -> ".canvasqa";project.hasProperty("nexusMinimalQa") -> ".minimalqa";project.hasProperty("nexusMegaQa") -> ".megaqa";project.hasProperty("nexusBackgroundQa") -> ".backgroundqa";project.hasProperty("nexusMediaQa") -> ".mediaqa";project.hasProperty("nexusUiQa") -> ".uiqa";else -> ".qa"}
            isDebuggable = true
            matchingFallbacks += listOf("debug")
            buildConfigField("boolean", "OFFLINE_QA", "true")
        }
        // InstalaciÃ³n de prueba JUNTO a la Nexus actual (otro paquete, otros datos): no la sustituye ni
        // necesita su firma. No comparte claves ni historial con com.vidadomu.nexus.
        create("agentPreview") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".agent"
            signingConfig = agentSigning // Without the private config, produce unsigned rather than replace the installed identity.
            versionNameSuffix = "-preview"
            isDebuggable = true
            matchingFallbacks += listOf("debug")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // Keep stable package ID
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.media3.common.util.UnstableApi"
        )
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    externalNativeBuild {cmake {path = file("src/main/cpp/CMakeLists.txt");version = "3.22.1"}}
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    // Skills del asistente (Markdown + YAML): se empaquetan como assets/skills/** y ademÃ¡s se pueden
    // sustituir sin recompilar desde files/skills o Documents/ALEXIA/Nexus/Skills.
    sourceSets["main"].assets.srcDir(rootProject.file("agent-skills"))
    testBuildType = "offlineQa"
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.all { it.maxHeapSize = "64m" }
    }
}

val inpaintRuntimeDirectory = layout.buildDirectory.dir("generated/inpaint-runtime")
val inpaintRuntime = tasks.register<Copy>("prepareInpaintRuntime") {
    val host = when {
        System.getProperty("os.name").startsWith("Windows") -> "windows-x86_64"
        System.getProperty("os.name").startsWith("Mac") -> "darwin-x86_64"
        else -> "linux-x86_64"
    }
    val libraries = androidComponents.sdkComponents.sdkDirectory.map {
        it.dir("ndk/${android.ndkVersion}/toolchains/llvm/prebuilt/$host/sysroot/usr/lib")
    }
    val targets = mapOf("arm64-v8a" to "aarch64-linux-android", "x86_64" to "x86_64-linux-android")
    targets.forEach { (abi, triple) ->
        from(libraries.map { it.file("$triple/libc++_shared.so") }) { into(abi) }
    }
    into(inpaintRuntimeDirectory)
    doFirst { targets.values.forEach { triple -> check(libraries.get().file("$triple/libc++_shared.so").asFile.isFile) { "Falta libc++ del NDK configurado para $triple" } } }
}
android.sourceSets["main"].jniLibs.srcDir(inpaintRuntimeDirectory)
// Optional verified native binaries from an existing Agent build; normal builds compile CMake as before.
(project.findProperty("nexusAgentPrebuiltJniDir") as String?)?.let {
    android.sourceSets["agentPreview"].jniLibs.srcDir(file(it))
}
tasks.named("preBuild") { dependsOn(inpaintRuntime) }

dependencies {
    implementation("com.google.android.gms:play-services-auth:22.0.0")
    // NÃºcleo del asistente (Kotlin puro, probado en JVM).
    implementation(project(":agent-core"))
    implementation("sh.calvin.reorderable:reorderable:2.4.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.3")

    val composeBom = platform("androidx.compose:compose-bom:2024.10.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.navigation:navigation-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")

    // Kotlinx Serialization JSON
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Image loading
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-video:2.7.0")

    // Video/Audio playback with Media3 ExoPlayer
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.media3:media3-common:1.4.1")
    implementation("androidx.media3:media3-transformer:1.4.1")

    // Security Crypto for Keystore backed storage
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // WorkManager for background generation tracking
    // 2.10 handles the Android 15+ foreground-service timeout (Service.onTimeout) instead of letting the process crash.
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // Test dependencies
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    "offlineQaImplementation"("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// Preview: skip the Kotlin JVM boxing optimizer, which can stall on large Compose
// settings functions. Android's runtime still optimizes the installed bytecode.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    if (name in listOf("compileAgentPreviewKotlin", "compileOfflineQaKotlin")) compilerOptions.freeCompilerArgs.add("-Xno-optimize")
}
