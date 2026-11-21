package com.vidadomu.nexus.assistant

import org.junit.Assert.assertEquals
import org.junit.Test

class TurnNotificationsTextTest {
    @Test
    fun elTextoDelAvisoVaEnUnaSolaLinea() {
        assertEquals("Prueba local linea uno Linea dos", TurnNotifications.oneLine("Prueba local linea uno\nLinea dos"))
        assertEquals("Hola mundo", TurnNotifications.oneLine("  Hola \r\n\t mundo  "))
        assertEquals("", TurnNotifications.oneLine(" \n "))
    }
}
