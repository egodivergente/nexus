pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Nexus"
include(":app")
// Núcleo del asistente (Kotlin puro, probado en JVM).
include(":agent-core")
// AXEL Link: cliente único de AXEL Commander (Kotlin puro), copia idéntica del módulo de AXEL Hub.
include(":axel-link")
