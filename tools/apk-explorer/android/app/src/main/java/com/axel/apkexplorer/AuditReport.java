package com.axel.apkexplorer;

import java.util.*;

/** Human-readable outputs of an exploration: REPORT.md (agents and people) and index.html (gallery). */
public final class AuditReport {
    private AuditReport() {}

    public static final class Meta {
        public String pkg = "", appVersion = "", device = "", status = "", stopReason = "", mode = "";
        public long durationMs; public int captureLimit; public boolean resumed;
        public double avgSettleMs, maxSettleMs; public int settleSamples;
    }

    public static String markdown(ExplorationModel m, Meta meta) {
        StringBuilder s = new StringBuilder();
        s.append("# Auditoría visual · ").append(meta.pkg).append("\n\n");
        s.append("- Versión de la app: ").append(meta.appVersion).append("\n");
        s.append("- Dispositivo: ").append(meta.device).append("\n");
        s.append("- Resultado: **").append(meta.status).append("**").append(meta.stopReason.isEmpty() ? "" : " — " + meta.stopReason).append("\n");
        s.append("- Duración: ").append(String.format(Locale.ROOT, "%.1f s", meta.durationMs / 1000.0)).append(meta.resumed ? " (sesión reanudada)" : "").append("\n\n");

        s.append("## Resumen\n\n| Métrica | Valor |\n|---|---|\n");
        s.append("| Pantallas distintas encontradas | ").append(m.screens.size()).append(" |\n");
        s.append("| Capturas guardadas (todas con información nueva) | ").append(m.capturedCount()).append(" |\n");
        s.append("| Observaciones repetidas descartadas (sin captura) | ").append(m.duplicateObservations).append(" |\n");
        s.append("| Acciones realizadas | ").append(m.actionsDone).append(" (").append(m.navigationActions).append(" de desplazamiento) |\n");
        s.append("| Reinicios de la app | ").append(m.restarts).append(" |\n");
        s.append("| Bucles detectados | ").append(m.loops).append(" |\n");
        s.append("| Salidas fuera de la app | ").append(m.externalExits).append(" |\n");
        s.append("| Acciones pendientes sin explorar | ").append(m.pendingCount()).append(" |\n");
        if (meta.settleSamples > 0)
            s.append("| Espera adaptativa media / máxima | ").append(String.format(Locale.ROOT, "%.0f ms / %.0f ms", meta.avgSettleMs, meta.maxSettleMs)).append(" |\n");
        s.append("\n");

        s.append("## Pantallas exploradas\n");
        Map<String, List<ExplorationModel.Screen>> bySection = new LinkedHashMap<>();
        for (ExplorationModel.Screen x : m.screens.values()) bySection.computeIfAbsent(x.section.isEmpty() ? "general" : x.section, k -> new ArrayList<>()).add(x);
        for (Map.Entry<String, List<ExplorationModel.Screen>> e : bySection.entrySet()) {
            s.append("\n### ").append(e.getKey()).append("\n\n");
            for (ExplorationModel.Screen x : e.getValue()) {
                s.append("- **").append(x.id).append("** «").append(x.title).append("» · nivel ").append(x.depth)
                        .append(" · ").append(x.visits).append(x.visits == 1 ? " visita" : " visitas")
                        .append(x.captured ? " · `" + x.image + "`" : " · sin captura").append("\n");
                s.append("  - Ruta: ").append(String.join(" › ", m.routeTo(x))).append("\n");
                int done = 0, pending = 0, skipped = 0;
                for (ExplorationModel.Act a : x.acts) { if (a.status.equals("done")) done++; else if (a.status.equals("pending")) pending++; else skipped++; }
                s.append("  - Controles: ").append(done).append(" recorridos, ").append(pending).append(" pendientes, ").append(skipped).append(" omitidos\n");
            }
        }

        s.append("\n## Omitido\n\n");
        Map<String, List<String>> byReason = new TreeMap<>();
        for (ExplorationModel.Screen x : m.screens.values())
            for (ExplorationModel.Act a : x.acts)
                if (a.status.equals("skipped")) byReason.computeIfAbsent(reasonGroup(a.reason), k -> new ArrayList<>()).add(x.id + " «" + a.label + "»");
        if (byReason.isEmpty()) s.append("Nada.\n");
        for (Map.Entry<String, List<String>> e : byReason.entrySet()) {
            s.append("- **").append(e.getKey()).append("** (").append(e.getValue().size()).append("): ");
            List<String> v = e.getValue();
            s.append(String.join("; ", v.subList(0, Math.min(8, v.size())))).append(v.size() > 8 ? "; … " + (v.size() - 8) + " más" : "").append("\n");
        }
        s.append("- **Observaciones repetidas**: ").append(m.duplicateObservations).append(" veces se volvió a una pantalla ya capturada y no se guardó otra imagen.\n");

        s.append("\n## Errores y avisos\n\n");
        boolean any = false;
        for (ExplorationModel.Event e : m.events) { any = true; s.append("- [").append(e.type).append("] paso ").append(e.step).append(e.screen.isEmpty() ? "" : " (" + e.screen + ")").append(": ").append(e.message).append("\n"); }
        if (!any) s.append("Sin errores ni avisos.\n");

        s.append("\n## Rutas visitadas\n\n").append(String.join(" → ", m.visitedPath.subList(0, Math.min(m.visitedPath.size(), 80)))).append(m.visitedPath.size() > 80 ? " → …" : "").append("\n");
        s.append("\n## Cobertura\n\nLa cobertura es parcial por diseño: no se pulsan acciones sensibles, campos de texto, interruptores de ajustes ni elementos repetidos de listas. ");
        s.append(m.pendingCount() > 0 ? "Quedan " + m.pendingCount() + " acciones pendientes; la sesión puede reanudarse." : "No quedan acciones seguras pendientes.").append("\n");
        return s.toString();
    }

    private static String reasonGroup(String r) {
        int i = r.indexOf(':');
        String base = i > 0 ? r.substring(0, i) : r;
        int p = base.indexOf('(');
        return p > 0 ? base.substring(0, p).trim() : base.trim();
    }

    public static String html(ExplorationModel m, Meta meta) {
        StringBuilder h = new StringBuilder("<!doctype html><meta charset='utf-8'><meta name='viewport' content='width=device-width'><title>APK Explorer</title><style>body{background:#101115;color:#e8e9f0;font:15px sans-serif;padding:20px}img{max-width:100%;max-height:70vh;border-radius:14px}article{display:inline-block;vertical-align:top;width:230px;margin:10px}h2{font-size:15px;margin:6px 0}a{color:#c9bdff}small{color:#a2a6b5}</style><h1>APK Explorer</h1><p>");
        h.append(ActionPolicy.html(meta.pkg)).append(" · ").append(ActionPolicy.html(meta.status)).append(" · ").append(m.capturedCount()).append(" capturas únicas · ").append(m.duplicateObservations).append(" repeticiones descartadas · ver REPORT.md</p>");
        Map<String, List<ExplorationModel.Screen>> bySection = new LinkedHashMap<>();
        for (ExplorationModel.Screen x : m.screens.values()) if (x.captured) bySection.computeIfAbsent(x.section.isEmpty() ? "general" : x.section, k -> new ArrayList<>()).add(x);
        for (Map.Entry<String, List<ExplorationModel.Screen>> e : bySection.entrySet()) {
            h.append("<h2>").append(ActionPolicy.html(e.getKey())).append("</h2>");
            for (ExplorationModel.Screen x : e.getValue())
                h.append("<article><a href='").append(ActionPolicy.html(x.image)).append("'><img src='").append(ActionPolicy.html(x.image)).append("'></a><h2>")
                        .append(ActionPolicy.html(x.id + " · " + x.title)).append("</h2><small>").append(ActionPolicy.html(String.join(" › ", m.routeTo(x)))).append("</small></article>");
        }
        return h.toString();
    }
}
