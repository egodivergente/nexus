package com.axel.apkexplorer;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public final class ActionPolicy {
    private static final Pattern RISK = Pattern.compile("\\b(delete|remove|erase|clear|reset|purchase|buy|pay|subscribe|send|submit|generate|order|confirm|accept|allow|install|uninstall|logout|save|publish|upload|move|rename|create|add|import|export|download|share|edit|eliminar|borrar|vaciar|restablecer|comprar|pagar|suscribir\\w*|enviar|generar|confirmar|aceptar|permitir|instalar|desinstalar|guardar|publicar|subir|mover|renombrar|crear|anadir|agregar|importar|exportar|descargar|compartir|editar|transfer\\w*)\\b");
    public static boolean allowed(String label) {
        String s=Normalizer.normalize(label,Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).trim();
        return !s.isEmpty() && !s.equals("+") && !RISK.matcher(s).find();
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
