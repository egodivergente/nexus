import java.util.Properties
import java.io.FileInputStream
import java.security.KeyStore
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// One machine-local configuration shared by every LLM/IDE using this Gradle user home.
// The file and keystores stay outside the repository. CI writes the same format from
// encrypted GitHub Actions secrets before invoking Gradle.
val nexusSigningPropertiesFile = (project.findProperty("nexusSigningFile") as String?)
    ?.let(::file)
    ?: file("${System.getProperty("user.home")}/.gradle/nexus-signing.properties")
val nexusSigningProperties = Properties().apply {
    if (nexusSigningPropertiesFile.isFile) {
        nexusSigningPropertiesFile.inputStream().use(::load)
    }
}

fun nexusSigningProperty(identity: String, name: String): String? =
    nexusSigningProperties.getProperty("nexus.$identity.$name")?.trim()?.takeIf(String::isNotEmpty)

fun nexusSigningStore(identity: String): File? = nexusSigningProperty(identity, "storeFile")
    ?.replace("${'$'}{user.home}", System.getProperty("user.home"))
    ?.let(::file)

val nexusExpectedSigningFingerprints = mapOf(
    "main" to "287a0c42d49c672ef61fc789af44584f5cab9a54f0e1ae4aa1332af4a4bab0f9",
    "agent" to "8b75db0fe0849019990f0d9d6426b95d85414f0585acac78a06c484b91cd7127"
)

val verifyNexusSigning = tasks.register("verifyNexusSigning") {
    group = "verification"
    description = "Checks that Nexus APK variants use the established signing identities."
    doLast {
        check(nexusSigningPropertiesFile.isFile) {
            "Nexus signing config is missing at ${nexusSigningPropertiesFile.absolutePath}. See docs/ANDROID-SIGNING.md."
        }
        nexusExpectedSigningFingerprints.forEach { (identity, expectedFingerprint) ->
            val store = nexusSigningStore(identity)
            check(store != null && store.isFile) {
                "Nexus '$identity' keystore is missing. See docs/ANDROID-SIGNING.md."
            }
            val storePassword = nexusSigningProperty(identity, "storePassword")
                ?: error("Nexus '$identity' keystore password is missing from the local signing config.")
            val keyAlias = nexusSigningProperty(identity, "keyAlias")
                ?: error("Nexus '$identity' key alias is missing from the local signing config.")
            val keyPassword = nexusSigningProperty(identity, "keyPassword")
                ?: error("Nexus '$identity' key password is missing from the local signing config.")

            val keyStore = sequenceOf("PKCS12", "JKS").firstNotNullOfOrNull { type ->
                runCatching {
                    KeyStore.getInstance(type).apply {
                        FileInputStream(store).use { load(it, storePassword.toCharArray()) }
                    }
                }.getOrNull()
            } ?: error("Nexus '$identity' keystore could not be opened; check its local configuration.")
            val certificate = keyStore.getCertificate(keyAlias)
                ?: error("Nexus '$identity' keystore does not contain the configured alias.")
            check(keyStore.getKey(keyAlias, keyPassword.toCharArray()) != null) {
                "Nexus '$identity' key password is incorrect."
            }
            val actualFingerprint = MessageDigest.getInstance("SHA-256")
                .digest(certificate.encoded)
                .joinToString("") { "%02x".format(it) }
            check(actualFingerprint.equals(expectedFingerprint, ignoreCase = true)) {
                "Nexus '$identity' signing certificate does not match the established app identity."
            }
        }
        logger.lifecycle("Nexus signing identities verified (main + agent).")
    }
}

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
        versionCode = 63
        versionName = "1.9.29-agent"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("boolean", "OFFLINE_QA", "false")
        // Claves de Bedrock opcionales desde un fichero local FUERA del repo: -PnexusBedrockFile=<ruta a .properties>
        // (AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, AWS_DEFAULT_REGION, BEDROCK_MODEL_ID). Sin él quedan vacías.
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

    signingConfigs {
        create("nexusMain") {
            storeFile = nexusSigningStore("main")
            storePassword = nexusSigningProperty("main", "storePassword")
            keyAlias = nexusSigningProperty("main", "keyAlias")
            keyPassword = nexusSigningProperty("main", "keyPassword")
        }
        create("nexusAgent") {
            storeFile = nexusSigningStore("agent")
            storePassword = nexusSigningProperty("agent", "storePassword")
            keyAlias = nexusSigningProperty("agent", "keyAlias")
            keyPassword = nexusSigningProperty("agent", "keyPassword")
        }
    }
    buildTypes {
        create("offlineQa") {
            initWith(getByName("debug"))
            // UI audit runs in its own sandbox, preserving earlier QA installations.
            applicationIdSuffix = when {project.hasProperty("nexusMediaQa") -> ".mediaqa";project.hasProperty("nexusUiQa") -> ".uiqa";else -> ".qa"}
            isDebuggable = true
            matchingFallbacks += listOf("debug")
            buildConfigField("boolean", "OFFLINE_QA", "true")
            signingConfig = signingConfigs.getByName("nexusMain")
        }
        // Instalación de prueba JUNTO a la Nexus actual (otro paquete, otros datos): no la sustituye ni
        // necesita su firma. No comparte claves ni historial con com.vidadomu.nexus.
        create("agentPreview") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".agent"
            versionNameSuffix = "-preview"
            isDebuggable = true
            matchingFallbacks += listOf("debug")
            signingConfig = signingConfigs.getByName("nexusAgent")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("nexusMain")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // Keep stable package ID
            signingConfig = signingConfigs.getByName("nexusMain")
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
    // Skills del asistente (Markdown + YAML): se empaquetan como assets/skills/** y además se pueden
    // sustituir sin recompilar desde files/skills o Documents/ALEXIA/Nexus/Skills.
    sourceSets["main"].assets.srcDir(rootProject.file("agent-skills"))
    testBuildType = "offlineQa"
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.all { it.maxHeapSize = "64m" }
    }
}

// Never let the Android plugin silently fall back to a host-generated debug key.
tasks.configureEach {
    if (name.matches(Regex("^(assemble|bundle|package|install)(Debug|Release|OfflineQa|AgentPreview)(?:[A-Z].*)?$"))) {
        dependsOn(verifyNexusSigning)
    }
}

dependencies {
    // Núcleo del asistente (Kotlin puro, probado en JVM).
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
    implementation("androidx.work:work-runtime-ktx:2.9.1")

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
