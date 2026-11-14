package com.vidadomu.nexus

import com.vidadomu.nexus.ui.navigation.Screen
import org.junit.Assert.*
import org.junit.Test

class ScreenInitializationTest {
    @Test fun startingWithAnyDestinationKeepsEveryBottomNavigationItemValid() {
        val prefix = Screen::class.java.name
        val parent = Screen::class.java.classLoader!!
        // No se fija el número de destinos: la barra crece con el producto. Lo que se comprueba es que
        // sea la misma y esté completa sea cual sea la pantalla que inicializa la clase primero.
        val routesByEntryPoint = mutableListOf<Pair<String, List<String>>>()
        for (first in listOf("Assistant", "Create", "Models", "Generations", "Library", "Settings", "Flow")) {
            // Fresh class initialization for each entry point; independent of test order.
            val loader = object : ClassLoader(parent) {
                override fun loadClass(name: String, resolve: Boolean): Class<*> {
                    if (name != prefix && !name.startsWith("$prefix\$")) return super.loadClass(name, resolve)
                    synchronized(this) {
                        val loaded = findLoadedClass(name) ?: parent.getResourceAsStream(name.replace('.', '/') + ".class")!!.use {
                            val bytes = it.readBytes()
                            defineClass(name, bytes, 0, bytes.size)
                        }
                        if (resolve) resolveClass(loaded)
                        return loaded
                    }
                }
            }
            Class.forName(prefix, true, loader).getField(first).get(null)
            val base = Class.forName(prefix, true, loader)
            val companion = base.getField("Companion").get(null)
            val items = companion.javaClass.getMethod("getBottomNavItems").invoke(companion) as List<*>
            assertTrue("Opening $first must not leave the bottom navigation empty", items.isNotEmpty())
            assertTrue("Opening $first must not retain a partially initialized destination", items.all { it != null })
            routesByEntryPoint += first to items.map { base.getMethod("getRoute").invoke(it) as String }
        }
        val reference = routesByEntryPoint.first()
        for ((entry, routes) in routesByEntryPoint.drop(1)) {
            assertEquals("Opening $entry changes the bottom navigation", reference.second, routes)
        }
    }
}
