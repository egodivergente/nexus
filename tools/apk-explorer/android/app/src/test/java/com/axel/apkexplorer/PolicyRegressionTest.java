package com.axel.apkexplorer;

import org.junit.Test;
import static org.junit.Assert.*;

/** Labels observed in real audits of Nexus Agent on the S25 (2026-10-27). */
public class PolicyRegressionTest {
    @Test public void nexusControlsThatChangeStateOrLeaveTheAppAreBlocked() {
        String[] blocked = {
            "Responder: Une el vídeo puente con los dos tramos limpios del clip original conservando el audio Unir vídeo en montaje",
            "Hacer una foto", "Grabar audio o dictar", "Hablar en directo", "Parar respuesta", "Enviar mensaje",
            "Modo de razonamiento: Medio", "Quitar 292 de la biblioteca", "Quitar 1 de la biblioteca", "Marcar todo", "Desmarcar", "Seleccionar",
            "Borrar", "Adjuntar · 292", "Al Agente", "Usar en Lienzo", "Exportar 292 en ZIP", "Importar archivos", "Importar desde el dispositivo",
            "Cambiar el nombre", "Cambiar el nombre de la conversación", "Cambiar de proyecto", "Cerrar conversación de la lista; conservar historial",
            "Nueva conversación", "edit · image",
            "Aumentar Presets por modelo", "Reducir presets por modelo", "Restaurar Presets por modelo a sin límite", "Nueva con este contexto",
            "Ámbito Ámbito: Global", "Abrir Flow", "Conectar Google Drive",
            "Favorite", "Ver proyectos en lista", "Ver proyectos en cuadrícula", "Upscale local 2x", "Orden: Más recientes", "Cuadrícula: 5 columnas",
            "Papelera"
        };
        for (String l : blocked) assertFalse("must be blocked: " + l, ActionPolicy.allowed(l));
    }

    @Test public void nexusNavigationRemainsExplorable() {
        String[] allowed = {
            "Conversaciones", "Ver encargos", "Ajustes", "Mostrar pestañas", "Ver cerradas · 5", "Saldo y gasto 0,06 €", "Historial de trabajos",
            "Todos", "Imagen", "Vídeo", "Audio", "Recientes", "Todo", "Generado por Nexus", "Importado por ti", "Editado en Nexus",
            "Ver toda la biblioteca", "Volver a la Biblioteca", "Cerrar Muro de resultados", "Cerrar escenario", "Listo", "Cancelar", "Reproducir vídeo", "Bajar al final"
        };
        for (String l : allowed) assertTrue("must stay allowed: " + l, ActionPolicy.allowed(l));
    }
}
