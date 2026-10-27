package com.axel.apkexplorer;

import java.util.*;

/** A tiny fake Android app used to drive {@link ExplorationModel} the way {@link Engine} does. */
final class SimApp {
    static final class Item {
        final String label, target; final boolean toggle, tab; final int l, t, r, b;
        Item(String label, String target, boolean tab, boolean toggle, int l, int t, int r, int b) { this.label = label; this.target = target; this.tab = tab; this.toggle = toggle; this.l = l; this.t = t; this.r = r; this.b = b; }
    }
    static final class Scr {
        final String id, title; final List<String> body = new ArrayList<>(); final List<Item> items = new ArrayList<>(); String tab = "";
        Scr(String id, String title, String... body) { this.id = id; this.title = title; this.body.addAll(Arrays.asList(body)); }
    }
    static final class Obs { ScreenSignature sig; long[] hash; List<Candidate> cands; String title; String section; }

    final Map<String, Scr> screens = new LinkedHashMap<>();
    final Deque<String> stack = new ArrayDeque<>();
    final List<String> clicked = new ArrayList<>();
    String root = "home";
    int restarts;

    SimApp() { build(); restart(); }

    private Item tab(String label, String target, int index) { return new Item(label, target, true, false, index * 240, 1500, index * 240 + 240, 1590); }

    private void build() {
        String[] tabs = {"Inicio", "Buscar", "Perfil"}; String[] targets = {"home", "search", "profile"};
        Scr home = add(new Scr("home", "Inicio", "Bienvenido a la app de pruebas", "Resumen de la semana", "Novedades recientes", "Consejos para empezar", "Tus proyectos activos"));
        home.tab = "Inicio";
        String[] names = {"Alfa", "Beta", "Gamma", "Delta", "Epsilon"};
        for (int i = 0; i < names.length; i++) home.items.add(new Item(names[i] + " proyecto", i == 0 ? "detail1" : i == 1 ? "detail2" : "home", false, false, 0, 300 + i * 200, 720, 480 + i * 200));
        home.items.add(new Item("Ajustes", "settings", false, false, 600, 20, 700, 110));
        Scr search = add(new Scr("search", "Buscar", "Escribe para buscar contenido", "Búsquedas recientes", "Sugerencias populares", "Categorías disponibles", "Sin resultados todavía"));
        search.tab = "Buscar"; search.items.add(new Item("Historial", "history", false, false, 40, 300, 680, 420));
        search.items.add(new Item("Vaciar historial", "search", false, false, 40, 500, 680, 620));
        Scr profile = add(new Scr("profile", "Perfil", "Tu perfil y preferencias", "Plan actual gratuito", "Miembro desde hace tiempo", "Estadísticas de uso", "Ayuda y soporte"));
        profile.tab = "Perfil"; profile.items.add(new Item("Cuenta", "account", false, false, 40, 300, 680, 420));
        profile.items.add(new Item("Borrar cuenta", "profile", false, false, 40, 450, 680, 570));
        profile.items.add(new Item("Notificaciones", "profile", false, true, 40, 600, 680, 720));
        profile.items.add(new Item("Pulsar sin efecto", "profile", false, false, 40, 750, 680, 870));
        for (Scr s : new Scr[]{home, search, profile}) for (int i = 0; i < 3; i++) s.items.add(tab(tabs[i], targets[i], i));
        Scr d1 = add(new Scr("detail1", "Detalle Alfa", "Descripción completa de Alfa", "Responsable del proyecto Alfa", "Estado: en curso", "Última revisión", "Notas internas de Alfa"));
        d1.items.add(new Item("Siguiente", "detail2", false, false, 40, 1200, 680, 1320));
        Scr d2 = add(new Scr("detail2", "Detalle Beta", "Descripción completa de Beta", "Responsable del proyecto Beta", "Estado: pausado", "Calendario previsto", "Notas internas de Beta"));
        d2.items.add(new Item("Anterior", "detail1", false, false, 40, 1200, 680, 1320));
        Scr settings = add(new Scr("settings", "Ajustes de la app", "Preferencias generales", "Apariencia y tema", "Idioma de la interfaz", "Privacidad", "Acerca de la aplicación"));
        settings.items.add(new Item("Idioma", "language", false, false, 40, 300, 680, 420));
        add(new Scr("language", "Idioma", "Selecciona el idioma", "Español", "English", "Français", "Deutsch"));
        add(new Scr("history", "Historial", "Historial de búsquedas", "Hace una hora", "Ayer", "Semana pasada", "Mes pasado"));
        add(new Scr("account", "Cuenta", "Datos de la cuenta", "Correo electrónico", "Fecha de alta", "Método de acceso", "Seguridad de la cuenta"));
    }

    private Scr add(Scr s) { screens.put(s.id, s); return s; }

    String current() { return stack.peekFirst(); }

    void restart() { stack.clear(); stack.push(root); restarts++; }

    void back() { if (stack.size() > 1) stack.pop(); }

    void click(String label) {
        clicked.add(label);
        Scr s = screens.get(current());
        for (Item i : s.items) if (i.label.equals(label)) {
            if (i.toggle || i.target.equals(s.id)) return;
            if (i.tab) { stack.clear(); stack.push(i.target); return; }
            stack.push(i.target);
            return;
        }
        throw new IllegalStateException("No such control on " + s.id + ": " + label);
    }

    Obs observe() {
        Scr s = screens.get(current());
        List<UiNode> nodes = new ArrayList<>();
        UiNode titleNode = UiNode.text(s.title, 20, 40, 400, 120); nodes.add(titleNode);
        int y = 150; for (String b : s.body) { nodes.add(UiNode.text(b, 40, y, 680, y + 40)); y += 45; }
        for (Item i : s.items) {
            UiNode n = UiNode.clickable(i.label, i.l, i.t, i.r, i.b);
            n.toggleControl = i.toggle;
            n.stateOn = i.tab && i.label.equals(s.tab);
            nodes.add(n);
        }
        Obs o = new Obs();
        o.sig = ScreenSignature.of(nodes, 1);
        o.hash = PerceptualHash.dhash(pixels(s.id), 64, 128);
        o.cands = Candidate.extract(nodes, 720, 1600);
        o.title = s.title;
        o.section = "";
        for (Candidate c : o.cands) if (Candidate.TAB.equals(c.kind) && c.stateOn && c.top >= 0.84 * 1600) o.section = ExplorationModel.slug(c.label);
        return o;
    }

    /** Deterministic pseudo screenshot: 8x16 blocks of random luma, distinct per screen. */
    static int[] pixels(String id) {
        Random r = new Random(id.hashCode() * 7919L);
        int[] px = new int[64 * 128];
        int[] block = new int[8 * 16];
        for (int i = 0; i < block.length; i++) block[i] = 20 + r.nextInt(200);
        for (int y = 0; y < 128; y++) for (int x = 0; x < 64; x++) { int v = block[(y / 8) * 8 + x / 8]; px[y * 64 + x] = 0xff000000 | v << 16 | v << 8 | v; }
        return px;
    }
}
