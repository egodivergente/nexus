pluginManagement { repositories { gradlePluginPortal(); mavenCentral() } }
dependencyResolutionManagement { repositories { mavenCentral() } }
rootProject.name = "nexus-desktop-agent"
include(":core")

// Pinned, hash-verified Corte source; no Android dependency.
include(":corte-model", ":corte-timeline", ":corte-storage")
project(":corte-model").projectDir = file("../corte/core-model")
project(":corte-timeline").projectDir = file("../corte/core-timeline")
project(":corte-storage").projectDir = file("../corte/core-storage")
