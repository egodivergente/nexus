package com.vidadomu.nexus

import com.vidadomu.nexus.ui.navigation.Screen
import org.junit.Assert.*
import org.junit.Test

class ScreenInitializationTest {
    @Test fun startingWithAnyDestinationKeepsEveryBottomNavigationItemValid() {
        val prefix = Screen::class.java.name
        val parent = Screen::class.java.classLoader!!
        for (first in listOf("Assistant", "Create", "Models", "Generations", "Library", "Settings")) {
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
            assertEquals(5, items.size)
            assertTrue("Opening $first must not retain a partially initialized destination", items.all { it != null })
            assertEquals(listOf("assistant", "create", "models", "generations", "library"),
                items.map { base.getMethod("getRoute").invoke(it) })
        }
    }
}
