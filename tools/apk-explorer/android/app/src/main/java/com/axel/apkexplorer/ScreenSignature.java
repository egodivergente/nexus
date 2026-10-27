package com.axel.apkexplorer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Identity of a screen derived from the UI hierarchy.
 *
 * Volatile content is normalised away (digits, clocks, ids) so counters, timers and
 * remote content do not make the same screen look new. Selection state (tabs, filter
 * chips) and the number of app windows (dialogs, popups) are part of the identity,
 * because those are real, user-visible states worth one capture each.
 */
public final class ScreenSignature {
    public final Set<String> texts, interactive, selected;
    public final int windows;
    public final boolean loading;
    public final String structureKey;

    private static final Pattern UUID = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern HEX = Pattern.compile("\\b[0-9a-f]{12,}\\b");
    private static final Pattern NUMBER = Pattern.compile("\\d+([.,:/]\\d+)*");
    private static final Pattern LOADING = Pattern.compile("^(cargando|loading|generando|procesando|descargando|espera|please wait|un momento)\\b.*");

    private ScreenSignature(Set<String> texts, Set<String> interactive, Set<String> selected, int windows, boolean loading) {
        this.texts = texts; this.interactive = interactive; this.selected = selected;
        this.windows = windows; this.loading = loading;
        this.structureKey = digest(String.join("\n", new TreeSet<>(texts)) + "\u0001" + String.join("\n", new TreeSet<>(interactive))
                + "\u0001" + String.join("\n", new TreeSet<>(selected)) + "\u0001" + windows);
    }

    /** Rebuilds a stored signature (resuming an interrupted session). */
    public static ScreenSignature restore(Collection<String> texts, Collection<String> interactive, Collection<String> selected, int windows) {
        return new ScreenSignature(new LinkedHashSet<>(texts), new LinkedHashSet<>(interactive), new LinkedHashSet<>(selected), windows, false);
    }

    public static ScreenSignature of(List<UiNode> nodes, int windows) {
        Set<String> texts = new LinkedHashSet<>(), interactive = new LinkedHashSet<>(), selected = new LinkedHashSet<>();
        boolean loading = false;
        for (UiNode n : nodes) {
            if (!n.visible) continue;
            if (n.progress) { loading = true; continue; }
            if (!n.password && !n.editable) {
                addText(texts, n.text);
                addText(texts, n.desc);
            }
            String own = normalize(n.text + " " + n.desc);
            if (LOADING.matcher(own).matches() && own.length() < 40) loading = true;
            if (n.clickable && n.enabled) {
                String l = normalize(n.label);
                String token = l.isEmpty() ? "?" + shortClass(n.cls) + "@" + (n.left / 48) + "," + (n.top / 48) : l;
                interactive.add(token);
                if (n.stateOn && !l.isEmpty()) selected.add(l);
            }
            if (texts.size() > 300) break;
        }
        return new ScreenSignature(texts, interactive, selected, windows, loading);
    }

    private static void addText(Set<String> out, String raw) {
        String s = normalize(raw);
        if (s.length() >= 2 && !s.equals("0")) out.add(s);
    }

    public static String shortClass(String cls) {
        int i = cls == null ? -1 : cls.lastIndexOf('.');
        return cls == null ? "" : cls.substring(i + 1);
    }

    /** Lower-case, accent-free, volatile parts replaced by placeholders, bounded length. */
    public static String normalize(String raw) {
        if (raw == null) return "";
        String s = Normalizer.normalize(raw, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        s = UUID.matcher(s).replaceAll("uuid");
        s = HEX.matcher(s).replaceAll("hex");
        s = NUMBER.matcher(s).replaceAll("0");
        s = s.replace('\u2026', ' ').replaceAll("\\s+", " ").trim();
        return s.length() > 60 ? s.substring(0, 60) : s;
    }

    public Set<String> all() {
        Set<String> a = new HashSet<>(texts);
        for (String s : interactive) a.add("i:" + s);
        for (String s : selected) a.add("s:" + s);
        return a;
    }

    /**
     * Same logical screen? Content variations (loaded images, counters, scrolled list) are
     * tolerated; a different tab/filter selection, a dialog/popup, or a changed body is not.
     */
    public boolean sameScreen(ScreenSignature o) {
        if (windows != o.windows || !selected.equals(o.selected)) return false;
        Set<String> a = all(), b = o.all();
        if (a.isEmpty() && b.isEmpty()) return true;
        int common = 0;
        for (String s : a) if (b.contains(s)) common++;
        int union = a.size() + b.size() - common;
        int added = b.size() - common, removed = a.size() - common;
        int tolerance = Math.max(1, (int) (0.10 * Math.max(a.size(), b.size())));
        return common / (double) union >= 0.85 && added <= tolerance && removed <= tolerance;
    }

    public static String digest(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < 8; i++) out.append(String.format(Locale.ROOT, "%02x", d[i] & 255));
            return out.toString();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
