package com.axel.apkexplorer;

import java.util.*;

/** An actionable control found on a screen, classified and ranked, or skipped with a reason. */
public final class Candidate {
    public static final String TAB = "tab", NAV = "nav", ITEM = "item", OTHER = "other";
    public String key = "", label = "", kind = OTHER, cls = "", resId = "", skipReason;
    public double rank;
    public int left, top, right, bottom;
    public boolean stateOn;
    /** Index of the node in the observation, used to pick the live accessibility node. */
    public int nodeIndex = -1;

    public static String keyOf(String label, String resId, String cls) {
        return ScreenSignature.normalize(label) + "|" + resId + "|" + cls;
    }

    /**
     * Turns the clickable nodes of one observation into candidates.
     * - Policy: sensitive verbs, settings toggles, text fields, scrims and unlabelled controls are skipped (and reported).
     * - Kinds: tab rows and filter chips first, then top-bar navigation, then other controls, then list items.
     * - Repeated list items: only two per group are kept, the rest is skipped as repeated.
     */
    public static List<Candidate> extract(List<UiNode> nodes, int screenW, int screenH) {
        List<Candidate> all = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        // A text field on screen means a messaging UI: its suggestion chips and quick replies SEND messages.
        boolean messaging = false;
        for (UiNode n : nodes) if (n.editable && n.visible && n.top >= 0.75 * screenH) messaging = true;
        for (int i = 0; i < nodes.size(); i++) {
            UiNode n = nodes.get(i);
            if (!n.clickable || !n.visible || !n.enabled || n.width() <= 0 || n.height() <= 0) continue;
            Candidate c = new Candidate();
            c.label = n.label == null ? "" : n.label.trim();
            c.cls = n.cls; c.resId = n.resId == null ? "" : n.resId;
            c.left = n.left; c.top = n.top; c.right = n.right; c.bottom = n.bottom;
            c.stateOn = n.stateOn; c.nodeIndex = i;
            c.key = keyOf(c.label, c.resId, c.cls);
            if (c.label.isEmpty()) c.key = "?" + ScreenSignature.shortClass(c.cls) + "@" + (n.left / 48) + "," + (n.top / 48);
            if (!seen.add(c.key)) continue;
            boolean scrim = n.width() * (long) n.height() > 0.85 * screenW * (long) screenH && n.scrollable == false && c.label.isEmpty();
            if (n.editable || n.password) c.skipReason = "campo de texto";
            else if (n.toggleControl) c.skipReason = "interruptor o control de ajuste";
            else if (scrim) c.skipReason = "capa de fondo";
            else if (n.checkableSub && !chipRow(nodes, n)) c.skipReason = "interruptor o casilla (cambia un ajuste)";
            else if (messaging && c.label.trim().split("\\s+").length >= 3 && n.top > 0.09 * screenH) c.skipReason = "posible mensaje o sugerencia (pantalla de chat): " + clip(c.label);
            else {
                String block = ActionPolicy.blockReason(c.label);
                if (block != null) c.skipReason = block.equals("sin etiqueta") ? block : block + ": " + clip(c.label);
            }
            all.add(c);
        }
        classify(all, screenW, screenH);
        return all;
    }

    /** Filter chips are checkable too, but they come in rows of three or more; a lone checkable control is a setting. */
    private static boolean chipRow(List<UiNode> nodes, UiNode n) {
        int same = 0;
        for (UiNode o : nodes)
            if (o != n && o.clickable && o.checkableSub && Math.abs((o.top + o.bottom) / 2 - (n.top + n.bottom) / 2) <= 12) same++;
        return same >= 2;
    }

    private static String clip(String s) { return s.length() > 40 ? s.substring(0, 40) + "…" : s; }

    private static void classify(List<Candidate> all, int w, int h) {
        List<Candidate> ok = new ArrayList<>();
        for (Candidate c : all) if (c.skipReason == null) ok.add(c);
        // Rows of aligned controls: bottom bars and chip rows are tabs.
        Set<Candidate> tabs = new HashSet<>();
        for (Candidate a : ok) {
            List<Candidate> row = new ArrayList<>();
            for (Candidate b : ok)
                if (Math.abs((a.top + a.bottom) / 2 - (b.top + b.bottom) / 2) <= 12 && Math.abs((a.bottom - a.top) - (b.bottom - b.top)) <= Math.max(24, (a.bottom - a.top) / 4)) row.add(b);
            if (row.size() < 3) continue;
            boolean bottomBar = a.bottom >= 0.84 * h && a.bottom - a.top <= 170 && row.size() <= 6;
            boolean hasState = false;
            for (Candidate b : row) hasState |= b.stateOn;
            if (bottomBar || hasState) tabs.addAll(row);
        }
        // Repeated look-alike controls (grids, lists).
        Map<String, List<Candidate>> groups = new LinkedHashMap<>();
        for (Candidate c : ok) groups.computeIfAbsent(c.cls + "|" + (c.right - c.left) / 24 + "x" + (c.bottom - c.top) / 24, k -> new ArrayList<>()).add(c);
        for (Candidate c : ok) {
            double base;
            if (tabs.contains(c)) { c.kind = TAB; base = 0; }
            else if (c.top < 0.09 * h && c.bottom - c.top <= 130) { c.kind = NAV; base = 10; }
            else { c.kind = OTHER; base = 20; }
            List<Candidate> g = groups.get(c.cls + "|" + (c.right - c.left) / 24 + "x" + (c.bottom - c.top) / 24);
            if (c.kind == OTHER && g.size() >= 4) {
                c.kind = ITEM; base = 40;
                if (g.indexOf(c) >= 2) c.skipReason = "elemento repetido de lista (" + g.size() + " similares)";
            }
            c.rank = base + 0.2 * ActionPolicy.priority(c.label, c.kind == TAB && c.top >= 0.84 * h) + 4.0 * c.top / Math.max(1, h) + 1.0 * c.left / Math.max(1, w);
        }
    }
}
