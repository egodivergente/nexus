// Raíz del arranque JVM (settings-jvm.gradle.kts): solo los complementos que usan los núcleos puros.
plugins {
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.20" apply false
    id("org.jetbrains.kotlin.jvm") version "2.3.20" apply false
}
