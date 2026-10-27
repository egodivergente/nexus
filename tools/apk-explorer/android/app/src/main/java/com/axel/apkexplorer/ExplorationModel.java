package com.axel.apkexplorer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.Normalizer;
import java.util.*;

/**
 * The brain of the explorer. It owns what has been seen and decides what to do next, but never
 * touches a device, so a whole exploration can be simulated in a JVM test.
 *
 * Screens are identified by {@link ScreenSignature} (hierarchy) and {@link PerceptualHash} (pixels).
 * Navigation is a graph search over transitions that were really observed; the next target is the
 * pending action with the lowest cost (rank + depth + travel distance), which explores tabs and
 * menus (breadth) before drilling into content, and keeps travel short.
 */
public final class ExplorationModel {
    public static final int MAX_NO_PROGRESS = 18, MAX_RESTARTS = 5;

    public static final class Act {
        public String key = "", label = "", kind = Candidate.OTHER, cls = "", resId = "", status = "pending", reason = "", dest = "";
        public double rank;
        public int attempts, broken, missing, hopTries, left, top, right, bottom;
    }

    public static final class Screen {
        public String heading = "", id = "", title = "", section = "", parent = "", via = "", image = "", xml = "";
        public int depth, visits = 1;
        public boolean captured, unreachable;
        public ScreenSignature sig;
        public long[] hash;
        public final List<Act> acts = new ArrayList<>();
        public Act act(String key) { for (Act a : acts) if (a.key.equals(key)) return a; return null; }
    }

    public static final class Edge {
        public String from = "", to = "", key = "", label = "", outcome = "";
        public int step;
    }

    public static final class Event {
        public final String type, message, screen; public final int step;
        public Event(String type, String message, String screen, int step) { this.type = type; this.message = message; this.screen = screen; this.step = step; }
    }

    public static final class Seen {
        public Screen screen; public boolean isNew; public String matchedBy = "";
    }

    public static final class Decision {
        public enum Type { CLICK, BACK, RESTART, STOP }
        public Type type; public Act act; public Candidate cand; public String reason = "", target = ""; public boolean exploring;
        static Decision stop(String why) { Decision d = new Decision(); d.type = Type.STOP; d.reason = why; return d; }
        static Decision of(Type t, String why) { Decision d = new Decision(); d.type = t; d.reason = why; return d; }
    }

    private static final class Pending { String type = "", from = "", actKey = "", label = ""; boolean explore; }
    private static final class Hop { String type; Act act; Screen from, to; }

    public final Map<String, Screen> screens = new LinkedHashMap<>();
    public final List<Edge> edges = new ArrayList<>();
    public final List<Event> events = new ArrayList<>();
    public final Map<String, String> tabDest = new HashMap<>(), backDest = new HashMap<>();
    public final Set<String> backBroken = new HashSet<>();
    public final List<String> visitedPath = new ArrayList<>();
    public int maxDepth = 4, observations, duplicateObservations, actionsDone, navigationActions, restarts, loops, noProgress, externalExits;
    public String current = "", landing = "", stopReason = "";
    public boolean forceRestart;
    private Pending last;
    private final Deque<String> recent = new ArrayDeque<>();
    /** Actions not found on screen during the current decision; skipped for good only after repeated absence. */
    private final Set<Act> excluded = new HashSet<>();

    public Seen observe(ScreenSignature sig, long[] hash, List<Candidate> cands, String title, String sectionHint) {
        return observe(sig, hash, cands, title, title, sectionHint);
    }

    /** @param heading the screen heading without tab/filter suffix; two nearly identical frames under one heading are one screen. */
    public Seen observe(ScreenSignature sig, long[] hash, List<Candidate> cands, String title, String heading, String sectionHint) {
        observations++;
        Screen best = null; int bestDistance = Integer.MAX_VALUE; String how = "";
        for (Screen s : screens.values()) {
            int d = PerceptualHash.distance(s.hash, hash);
            boolean twin = d <= PerceptualHash.TWIN && s.sig.windows == sig.windows && s.sig.selected.equals(sig.selected);
            // Same heading, same window layout and a nearly identical picture: a filter or content variation, not a new screen.
            if (!twin && d <= PerceptualHash.LOOSE && s.sig.windows == sig.windows && heading != null && !heading.isEmpty() && !heading.equals("pantalla") && heading.equals(s.heading)) twin = true;
            boolean same = s.sig.sameScreen(sig);
            if ((twin || same) && d < bestDistance) { best = s; bestDistance = d; how = twin ? "visual" : "estructura"; }
        }
        Seen seen = new Seen();
        Pending p = last; last = null;
        Screen from = p == null ? null : screens.get(p.from);
        if (best == null) {
            Screen s = new Screen();
            s.id = String.format(Locale.ROOT, "s%03d", screens.size() + 1);
            s.sig = sig; s.hash = hash; s.heading = heading == null ? "" : heading; s.title = title == null || title.isEmpty() ? "pantalla" : title;
            if (from != null && p != null && p.type.equals("click")) {
                Act a = from.act(p.actKey);
                boolean tab = a != null && Candidate.TAB.equals(a.kind) && sig.windows == from.sig.windows;
                s.parent = tab ? from.parent : from.id;
                s.depth = tab ? from.depth : from.depth + 1;
                s.via = p.label;
                s.section = sectionHint != null && !sectionHint.isEmpty() ? sectionHint : from.section;
            } else {
                s.section = sectionHint != null && !sectionHint.isEmpty() ? sectionHint : slug(s.title);
            }
            screens.put(s.id, s);
            registerActs(s, cands);
            seen.screen = s; seen.isNew = true; seen.matchedBy = "nueva";
            noProgress = 0;
        } else {
            best.visits++;
            registerActs(best, cands);
            seen.screen = best; seen.matchedBy = how;
            duplicateObservations++;
        }
        Screen s = seen.screen;
        if (p != null) resolve(p, from, s, seen.isNew);
        if (!seen.isNew && p != null && p.explore) noProgress++;
        if (p != null && p.type.equals("restart")) landing = s.id;
        if (landing.isEmpty()) landing = s.id;
        if (!s.id.equals(current)) visitedPath.add(s.id);
        current = s.id;
        detectCycle(s, seen.isNew);
        return seen;
    }

    private void resolve(Pending p, Screen from, Screen to, boolean isNew) {
        Edge e = new Edge();
        e.from = p.from; e.to = to.id; e.key = p.actKey; e.label = p.label; e.step = actionsDone;
        if (p.type.equals("back")) {
            if (to == from) { backBroken.add(from.id); e.outcome = "sin_cambio"; warn("Atrás no cambió la pantalla " + from.id, from.id); }
            else { backDest.put(p.from, to.id); e.outcome = isNew ? "nueva" : "conocida"; }
        } else if (p.type.equals("click") && from != null) {
            Act a = from.act(p.actKey);
            if (to == from) {
                e.outcome = "sin_efecto";
                if (a != null && !a.status.equals("done")) { a.status = "skipped"; a.reason = "sin efecto visible"; }
                else if (a != null) { a.broken++; }
            } else {
                e.outcome = isNew ? "nueva" : (isAncestor(to, from) ? "vuelta" : "conocida");
                if (a != null) {
                    if (a.status.equals("done") && !a.dest.isEmpty() && !a.dest.equals(to.id)) { a.broken++; warn("La acción «" + a.label + "» ya no lleva a " + a.dest + " sino a " + to.id, from.id); }
                    else { a.status = "done"; a.dest = to.id; a.hopTries = 0; }
                    if (Candidate.TAB.equals(a.kind)) tabDest.put(a.key, to.id);
                }
            }
        } else return;
        edges.add(e);
    }

    private boolean isAncestor(Screen candidate, Screen of) {
        Set<String> guard = new HashSet<>();
        String id = of.parent;
        while (!id.isEmpty() && guard.add(id)) { if (id.equals(candidate.id)) return true; Screen s = screens.get(id); id = s == null ? "" : s.parent; }
        return false;
    }

    private void registerActs(Screen s, List<Candidate> cands) {
        for (Candidate c : cands) {
            Act a = s.act(c.key);
            if (a != null) { a.left = c.left; a.top = c.top; a.right = c.right; a.bottom = c.bottom; continue; }
            a = new Act();
            a.key = c.key; a.label = c.label; a.kind = c.kind; a.cls = c.cls; a.resId = c.resId; a.rank = c.rank;
            a.left = c.left; a.top = c.top; a.right = c.right; a.bottom = c.bottom;
            if (c.skipReason != null) { a.status = "skipped"; a.reason = c.skipReason; }
            else if (s.depth >= maxDepth && !Candidate.TAB.equals(c.kind)) { a.status = "skipped"; a.reason = "profundidad máxima (" + maxDepth + ")"; }
            s.acts.add(a);
        }
    }

    private void detectCycle(Screen s, boolean isNew) {
        if (isNew) { recent.clear(); return; }
        recent.addLast(s.id);
        while (recent.size() > 10) recent.removeFirst();
        if (recent.size() >= 8 && noProgress >= 6) {
            Set<String> distinct = new HashSet<>(recent);
            if (distinct.size() <= 2) {
                loops++;
                events.add(new Event("loop", "Bucle de navegación entre " + String.join(" ↔ ", distinct) + ": se reinicia la app para salir", s.id, actionsDone));
                forceRestart = true;
                for (String id : distinct) { Screen x = screens.get(id); if (x != null) for (Act a : x.acts) if (a.status.equals("done") && distinct.contains(a.dest)) a.broken++; }
                recent.clear();
            }
        }
    }

    /**
     * Tabs and filter chips lead to the same place from every screen that shows them. Once one click
     * revealed where a tab goes, the copies on the other screens are not clicked again (no N x N tab clicking).
     */
    private void resolveKnownTabs() {
        for (Screen s : screens.values())
            for (Act a : s.acts) {
                if (!a.status.equals("pending") || !Candidate.TAB.equals(a.kind)) continue;
                String d = tabDest.get(a.key);
                if (d == null || !screens.containsKey(d)) continue;
                if (d.equals(s.id)) { a.status = "skipped"; a.reason = "pestaña de esta misma pantalla"; }
                else { a.status = "done"; a.dest = d; }
            }
    }

    public Act bestPending(Screen s) {
        Act best = null;
        for (Act a : s.acts) if (a.status.equals("pending") && a.attempts < 2 && !excluded.contains(a) && (best == null || a.rank < best.rank)) best = a;
        return best;
    }

    public Decision next(List<Candidate> cands) {
        if (noProgress >= MAX_NO_PROGRESS) return stop("estancado: " + noProgress + " acciones seguidas sin pantallas nuevas");
        if (loops >= 3) return stop("bucle persistente de navegación");
        if (forceRestart) { forceRestart = false; if (restarts < MAX_RESTARTS) return restart("salir de un bucle"); }
        excluded.clear();
        resolveKnownTabs();
        for (int attempt = 0; attempt < 12; attempt++) {
            Screen cur = screens.get(current);
            if (cur == null) return stop("sin pantalla actual");
            Map<String, Integer> dist = new HashMap<>(); Map<String, Hop> first = new HashMap<>();
            ArrayDeque<Screen> queue = new ArrayDeque<>();
            dist.put(cur.id, 0); queue.add(cur);
            while (!queue.isEmpty()) {
                Screen x = queue.poll();
                for (Hop h : neighbors(x)) {
                    if (dist.containsKey(h.to.id)) continue;
                    dist.put(h.to.id, dist.get(x.id) + 1);
                    first.put(h.to.id, x == cur ? h : first.get(x.id));
                    queue.add(h.to);
                }
            }
            double bestScore = Double.MAX_VALUE; Screen bestScreen = null; Act bestAct = null;
            List<Screen> unreachable = new ArrayList<>();
            for (Screen x : screens.values()) {
                Act a = bestPending(x);
                if (a == null || x.unreachable) continue;
                Integer d = dist.get(x.id);
                if (d == null) { unreachable.add(x); continue; }
                double score = a.rank + 12.0 * x.depth + 4.0 * d;
                if (score < bestScore) { bestScore = score; bestScreen = x; bestAct = a; }
            }
            if (bestScreen == null) {
                if (unreachable.isEmpty()) return stop(excluded.isEmpty() ? "completo" : "quedan acciones pendientes que no están visibles ahora");
                if (restarts < MAX_RESTARTS && !(cur.id.equals(landing) && restarts > 0)) return restart("llegar a pantallas pendientes");
                for (Screen x : unreachable) { x.unreachable = true; events.add(new Event("error", "Pantalla inalcanzable desde la app: " + x.id + " «" + x.title + "»", x.id, actionsDone)); }
                continue;
            }
            if (bestScreen == cur) {
                Candidate c = find(cands, bestAct.key);
                if (c == null) { if (++bestAct.missing >= 3) { bestAct.status = "skipped"; bestAct.reason = "ya no está visible"; } else excluded.add(bestAct); continue; }
                Decision d = Decision.of(Decision.Type.CLICK, "explorar");
                d.act = bestAct; d.cand = c; d.exploring = true; d.target = cur.id;
                return d;
            }
            Hop hop = first.get(bestScreen.id);
            d: {
                if (hop.type.equals("back")) { Decision d = Decision.of(Decision.Type.BACK, "ir a " + bestScreen.id); d.target = bestScreen.id; return d; }
                Candidate c = find(cands, hop.act.key);
                if (c == null) { hop.act.broken++; warn("Ruta rota: «" + hop.act.label + "» no está en " + cur.id, cur.id); break d; }
                Decision d = Decision.of(Decision.Type.CLICK, "ir a " + bestScreen.id);
                d.act = hop.act; d.cand = c; d.exploring = false; d.target = bestScreen.id;
                return d;
            }
        }
        return stop("sin ruta fiable hacia las pantallas pendientes");
    }

    private Decision stop(String why) { stopReason = why; return Decision.stop(why); }
    private Decision restart(String why) { restarts++; return Decision.of(Decision.Type.RESTART, why); }

    private Candidate find(List<Candidate> cands, String key) {
        for (Candidate c : cands) if (c.key.equals(key) && c.skipReason == null) return c;
        return null;
    }

    private List<Hop> neighbors(Screen x) {
        List<Hop> out = new ArrayList<>();
        for (Act a : x.acts) {
            if (a.broken >= 2) continue;
            String d = !a.dest.isEmpty() ? a.dest : (Candidate.TAB.equals(a.kind) ? tabDest.get(a.key) : null);
            if (d == null || d.equals(x.id)) continue;
            Screen y = screens.get(d);
            if (y == null || y.unreachable) continue;
            Hop h = new Hop(); h.type = "click"; h.act = a; h.from = x; h.to = y; out.add(h);
        }
        String bd = backDest.get(x.id);
        if (bd == null && !x.parent.isEmpty() && !backBroken.contains(x.id)) bd = x.parent;
        if (bd != null && !bd.equals(x.id) && screens.containsKey(bd)) {
            Hop h = new Hop(); h.type = "back"; h.from = x; h.to = screens.get(bd); out.add(h);
        }
        return out;
    }

    /** Record that the engine is about to perform the decision. */
    public void performed(Decision d) {
        Pending p = new Pending();
        p.from = current;
        switch (d.type) {
            case CLICK: p.type = "click"; p.actKey = d.act.key; p.label = d.act.label; p.explore = d.exploring; d.act.attempts++;
                // A navigation hop that keeps failing to reach its destination is retired after three tries.
                if (!d.exploring) { d.act.hopTries++; if (d.act.hopTries >= 3) d.act.broken = Math.max(d.act.broken, 2); } actionsDone++; if (!d.exploring) navigationActions++; break;
            case BACK: p.type = "back"; p.label = "Atrás"; actionsDone++; navigationActions++; break;
            case RESTART: p.type = "restart"; p.label = "Reinicio de la app"; break;
            default: return;
        }
        last = p;
    }

    /** The click could not be delivered. */
    public void actionFailed(Decision d, String why) {
        last = null;
        if (d.act != null) { d.act.status = "skipped"; d.act.reason = "no se pudo pulsar: " + why; }
        events.add(new Event("error", "No se pudo pulsar «" + (d.act == null ? "?" : d.act.label) + "»: " + why, current, actionsDone));
    }

    /** The last action left the audited app (another package, launcher, system dialog). */
    public void externalExit(String what) {
        Pending p = last; last = null;
        externalExits++;
        Screen from = p == null ? null : screens.get(p.from);
        if (p != null && p.type.equals("click") && from != null) {
            Act a = from.act(p.actKey);
            if (a != null && !a.status.equals("done")) { a.status = "skipped"; a.reason = "sale de la app (" + what + ")"; }
            Edge e = new Edge(); e.from = p.from; e.to = "externo"; e.key = p.actKey; e.label = p.label; e.outcome = "externo"; e.step = actionsDone; edges.add(e);
        }
        events.add(new Event("warn", "La acción llevó fuera de la app: " + what, current, actionsDone));
        Pending r = new Pending(); r.type = "recover"; r.from = current; last = r;
    }

    public void markCaptured(Screen s, String image, String xml) { s.captured = true; s.image = image; s.xml = xml; }
    public void warn(String msg, String screen) { events.add(new Event("warn", msg, screen, actionsDone)); }
    public void error(String msg, String screen) { events.add(new Event("error", msg, screen, actionsDone)); }

    public int capturedCount() { int n = 0; for (Screen s : screens.values()) if (s.captured) n++; return n; }

    public int pendingCount() {
        int n = 0;
        for (Screen s : screens.values()) if (!s.unreachable) for (Act a : s.acts) if (a.status.equals("pending") && a.attempts < 2) n++;
        return n;
    }

    public List<String> routeTo(Screen s) {
        LinkedList<String> labels = new LinkedList<>();
        Set<String> guard = new HashSet<>();
        Screen x = s;
        while (x != null && guard.add(x.id)) {
            labels.addFirst(x.via.isEmpty() ? x.title : x.via);
            x = x.parent.isEmpty() ? null : screens.get(x.parent);
        }
        return labels;
    }

    public static String slug(String s) {
        String n = Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (n.length() > 40) n = n.substring(0, 40).replaceAll("-+$", "");
        return n.isEmpty() ? "pantalla" : n;
    }

    // ----- persistence (resume) -----

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("version", 1).put("current", current).put("landing", landing).put("max_depth", maxDepth)
                .put("observations", observations).put("duplicate_observations", duplicateObservations)
                .put("actions", actionsDone).put("navigation_actions", navigationActions).put("restarts", restarts)
                .put("loops", loops).put("no_progress", noProgress).put("external_exits", externalExits);
        JSONArray ss = new JSONArray();
        for (Screen s : screens.values()) {
            JSONObject j = new JSONObject();
            j.put("id", s.id).put("title", s.title).put("section", s.section).put("parent", s.parent).put("via", s.via)
                    .put("heading", s.heading).put("image", s.image).put("xml", s.xml).put("depth", s.depth).put("visits", s.visits)
                    .put("captured", s.captured).put("unreachable", s.unreachable).put("hash", PerceptualHash.toHex(s.hash))
                    .put("windows", s.sig.windows).put("texts", new JSONArray(s.sig.texts)).put("interactive", new JSONArray(s.sig.interactive))
                    .put("selected", new JSONArray(s.sig.selected));
            JSONArray as = new JSONArray();
            for (Act a : s.acts) as.put(new JSONObject().put("key", a.key).put("label", a.label).put("kind", a.kind).put("cls", a.cls)
                    .put("res", a.resId).put("status", a.status).put("reason", a.reason).put("dest", a.dest).put("rank", a.rank)
                    .put("attempts", a.attempts).put("broken", a.broken).put("b", new JSONArray(new int[]{a.left, a.top, a.right, a.bottom})));
            j.put("acts", as);
            ss.put(j);
        }
        o.put("screens", ss);
        JSONArray es = new JSONArray();
        for (Edge e : edges) es.put(new JSONObject().put("from", e.from).put("to", e.to).put("key", e.key).put("label", e.label).put("outcome", e.outcome).put("step", e.step));
        o.put("edges", es);
        JSONArray ev = new JSONArray();
        for (Event e : events) ev.put(new JSONObject().put("type", e.type).put("message", e.message).put("screen", e.screen).put("step", e.step));
        o.put("events", ev);
        o.put("tab_dest", new JSONObject(tabDest)).put("back_dest", new JSONObject(backDest)).put("back_broken", new JSONArray(backBroken));
        o.put("path", new JSONArray(visitedPath));
        return o;
    }

    public static ExplorationModel fromJson(JSONObject o) throws JSONException {
        ExplorationModel m = new ExplorationModel();
        m.current = o.optString("current"); m.landing = o.optString("landing"); m.maxDepth = o.optInt("max_depth", 4);
        m.observations = o.optInt("observations"); m.duplicateObservations = o.optInt("duplicate_observations");
        m.actionsDone = o.optInt("actions"); m.navigationActions = o.optInt("navigation_actions"); m.restarts = o.optInt("restarts");
        m.loops = o.optInt("loops"); m.noProgress = 0; m.externalExits = o.optInt("external_exits");
        JSONArray ss = o.getJSONArray("screens");
        for (int i = 0; i < ss.length(); i++) {
            JSONObject j = ss.getJSONObject(i);
            Screen s = new Screen();
            s.id = j.getString("id"); s.title = j.optString("title"); s.heading = j.optString("heading", s.title); s.section = j.optString("section"); s.parent = j.optString("parent");
            s.via = j.optString("via"); s.image = j.optString("image"); s.xml = j.optString("xml"); s.depth = j.optInt("depth");
            s.visits = j.optInt("visits", 1); s.captured = j.optBoolean("captured"); s.unreachable = false;
            s.hash = PerceptualHash.fromHex(j.getString("hash"));
            s.sig = ScreenSignature.restore(strings(j.getJSONArray("texts")), strings(j.getJSONArray("interactive")), strings(j.getJSONArray("selected")), j.optInt("windows", 1));
            JSONArray as = j.getJSONArray("acts");
            for (int k = 0; k < as.length(); k++) {
                JSONObject a = as.getJSONObject(k);
                Act x = new Act();
                x.key = a.getString("key"); x.label = a.optString("label"); x.kind = a.optString("kind"); x.cls = a.optString("cls"); x.resId = a.optString("res");
                x.status = a.optString("status", "pending"); x.reason = a.optString("reason"); x.dest = a.optString("dest"); x.rank = a.optDouble("rank");
                x.attempts = a.optInt("attempts"); x.broken = a.optInt("broken");
                JSONArray b = a.optJSONArray("b");
                if (b != null && b.length() == 4) { x.left = b.getInt(0); x.top = b.getInt(1); x.right = b.getInt(2); x.bottom = b.getInt(3); }
                s.acts.add(x);
            }
            m.screens.put(s.id, s);
        }
        JSONArray es = o.optJSONArray("edges");
        for (int i = 0; es != null && i < es.length(); i++) {
            JSONObject j = es.getJSONObject(i);
            Edge e = new Edge(); e.from = j.optString("from"); e.to = j.optString("to"); e.key = j.optString("key"); e.label = j.optString("label"); e.outcome = j.optString("outcome"); e.step = j.optInt("step");
            m.edges.add(e);
        }
        JSONArray ev = o.optJSONArray("events");
        for (int i = 0; ev != null && i < ev.length(); i++) { JSONObject j = ev.getJSONObject(i); m.events.add(new Event(j.optString("type"), j.optString("message"), j.optString("screen"), j.optInt("step"))); }
        putAll(m.tabDest, o.optJSONObject("tab_dest")); putAll(m.backDest, o.optJSONObject("back_dest"));
        JSONArray bb = o.optJSONArray("back_broken");
        for (int i = 0; bb != null && i < bb.length(); i++) m.backBroken.add(bb.getString(i));
        JSONArray path = o.optJSONArray("path");
        for (int i = 0; path != null && i < path.length(); i++) m.visitedPath.add(path.getString(i));
        return m;
    }

    private static List<String> strings(JSONArray a) throws JSONException { List<String> l = new ArrayList<>(); for (int i = 0; i < a.length(); i++) l.add(a.getString(i)); return l; }
    private static void putAll(Map<String, String> into, JSONObject o) throws JSONException { if (o == null) return; for (Iterator<String> it = o.keys(); it.hasNext(); ) { String k = it.next(); into.put(k, o.getString(k)); } }
}
