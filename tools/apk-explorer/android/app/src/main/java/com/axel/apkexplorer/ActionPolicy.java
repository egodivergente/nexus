package com.axel.apkexplorer;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public final class ActionPolicy {
    private static final Pattern RISK = Pattern.compile("\\b(delete|remove|erase|clear|reset|purchase|buy|pay|subscribe|send|submit|generate|order|confirm|accept|allow|install|uninstall|logout|save|publish|upload|move|rename|create|add|import|export|download|share|edit|eliminar|borrar|vaciar|restablecer|comprar|pagar|suscribir\\w*|enviar|generar|confirmar|aceptar|permitir|instalar|desinstalar|guardar|publicar|subir|mover|renombrar|crear|anadir|agregar|importar|exportar|descargar|compartir|editar|transfer\\w*)\\b");
    /**
     * Second layer added in 0.4.0. These verbs were observed (or are likely) to change state, selection,
     * accounts or attached content even when no explicit "confirm" step follows.
     */
    private static final Pattern RISK_EXTRA = Pattern.compile("\\b(nueva conversacion|nuevo chat|new chat|new conversation|cambiar|change|modificar|modify|cerrar (la )?conversacion|archivar|archive|responder|reply|respond|preguntar|ask|parar|detener|stop|interrumpir|pausar|pause|razonamiento|reasoning|temperatura|temperature|hacer (una )?foto|tomar (una )?foto|take (a )?photo|camara|camera|grabar|record|dictar|hablar|voz|voice|microfono|escanear|scan|linterna|quitar|quita|retirar|trash|papelera|bin|discard|descartar|cancel subscription|cancelar suscripcion|sign ?out|sign ?in|sign ?up|log ?out|log ?in|logout|cerrar sesion|iniciar sesion|registrar\\w*|salir|exit|mark all|select all|unselect|deselect|marcar|desmarcar|seleccionar|deseleccionar|adjuntar|attach|al agente|to agent|usar en|use in|enviar a|send to|publicar|post|comment\\w*|comentar|pedir|solicitar|request|apply|aplicar|update|actualizar|renovar|renew|top ?up|recargar|checkout|pagos?|payment|unirse|join|follow|seguir|invite|invitar|call|llamar|dial|sms|emergencia|emergency|factory|fabrica|wipe|formatear|format|permisos?|permission|grant|otorgar|bloquear|block|report|reportar|denunciar)\\b");
    private static final Pattern SENSITIVE_SETTING = Pattern.compile("\\b(wi-?fi|bluetooth|modo avion|airplane|nfc|ubicacion|location|brillo|brightness|volumen|volume|biometri\\w*|huella|fingerprint|pin|contrasena|password|clave)\\b");

    public static boolean allowed(String label) { return blockReason(label) == null; }

    /** @return null when the label may be clicked, otherwise a short human reason for skipping it. */
    public static String blockReason(String label) {
        String s = Normalizer.normalize(label == null ? "" : label, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
        if (s.isEmpty()) return "sin etiqueta";
        if (s.equals("+")) return "acción de añadir";
        if (RISK.matcher(s).find() || RISK_EXTRA.matcher(s).find()) return "acción sensible";
        if (SENSITIVE_SETTING.matcher(s).find()) return "ajuste sensible";
        return null;
    }
    public static String html(String s) { return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;"); }
    public static String identity(String label) {
        String s=Normalizer.normalize(label,Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).trim();
        return String.join(" ",new java.util.LinkedHashSet<>(java.util.Arrays.asList(s.split("\\s+"))));
    }
    public static int priority(String label, boolean bottomTab) {
        String s=identity(label);
        if(bottomTab || s.matches("(inicio|home|lienzo|canvas|modelos|models|biblioteca|library|biblio|galeria|gallery|proyectos|projects|agente|agent|explorar|explore)( .*)?"))return 0;
        if(s.matches(".*(detalle del error|error details|ocultar detalle|hide details).*"))return 90;
        if(s.matches("(cancelar|cancel|cerrar|close|atras|back)( .*)?"))return 80;
        if(s.contains("ajustes")||s.contains("settings"))return 40;
        return 20;
    }
}
