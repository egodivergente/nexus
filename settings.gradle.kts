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
// Núcleo de Nexus Cut (editor multipista): Kotlin puro, probado en JVM.
include(":cut-core")
