// Arranque alternativo para probar los núcleos Kotlin puros sin el SDK de Android:
//   ./gradlew -c settings-jvm.gradle.kts :cut-core:test :agent-core:test
// Útil en contenedores y CI donde no hay SDK; el arranque normal (settings.gradle.kts) no cambia.
pluginManagement {
    repositories {
        maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2/") }
        google(); mavenCentral(); gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2/") }
        google(); mavenCentral()
    }
}

rootProject.name = "Nexus"
rootProject.buildFileName = "build-jvm.gradle.kts"
include(":agent-core")
include(":cut-core")
