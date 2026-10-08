pluginManagement { repositories { gradlePluginPortal(); mavenCentral() } }
dependencyResolutionManagement { repositories { mavenCentral() } }
rootProject.name = "NexusAndroidCoreValidation"
include(":agent-core")
project(":agent-core").projectDir = file("../../../agent-core")
