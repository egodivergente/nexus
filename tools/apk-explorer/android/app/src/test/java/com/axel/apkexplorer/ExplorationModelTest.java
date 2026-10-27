package com.axel.apkexplorer;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class ExplorationModelTest {
    /** Drives the model against the simulated app exactly like Engine does. Returns the stop reason. */
    private static String run(ExplorationModel m, SimApp app, List<String> captures, int maxSteps, int captureCeiling) {
        for (int step = 0; step < maxSteps; step++) {
            SimApp.Obs o = app.observe();
            ExplorationModel.Seen seen = m.observe(o.sig, o.hash, o.cands, o.title, o.section);
            if (seen.isNew) { captures.add(seen.screen.title); m.markCaptured(seen.screen, "screens/x/" + captures.size() + ".png", "x.xml"); }
            if (m.capturedCount() >= captureCeiling) return "limite";
            ExplorationModel.Decision d = m.next(o.cands);
            switch (d.type) {
                case STOP: return d.reason;
                case CLICK: m.performed(d); app.click(d.act.label); break;
                case BACK: m.performed(d); app.back(); break;
                case RESTART: m.performed(d); app.restart(); break;
            }
        }
        return "pasos";
    }

    @Test public void exploresEveryReachableScreenOnceAndTerminates() {
        ExplorationModel m = new ExplorationModel(); SimApp app = new SimApp(); List<String> captures = new ArrayList<>();
        String reason = run(m, app, captures, 300, 99);
        assertEquals("completo", reason);
        // 9 distinct screens, each captured exactly once: no repeated captures.
        assertEquals(new TreeSet<>(captures).size(), captures.size());
        assertEquals(9, captures.size());
        assertEquals(0, m.loops);
        assertEquals(m.pendingCount(), 0);
    }

    @Test public void neverClicksSensitiveOrToggleControls() {
        ExplorationModel m = new ExplorationModel(); SimApp app = new SimApp(); run(m, app, new ArrayList<>(), 300, 99);
        assertFalse(app.clicked.contains("Borrar cuenta"));
        assertFalse(app.clicked.contains("Vaciar historial"));
        assertFalse(app.clicked.contains("Notificaciones"));
        boolean reported = false;
        for (ExplorationModel.Screen s : m.screens.values()) for (ExplorationModel.Act a : s.acts) if (a.label.equals("Borrar cuenta")) { assertEquals("skipped", a.status); reported = true; }
        assertTrue("skipped actions must be reported", reported);
    }

    @Test public void tabsAreExploredBeforeDrillingIntoContent() {
        ExplorationModel m = new ExplorationModel(); SimApp app = new SimApp(); List<String> captures = new ArrayList<>();
        run(m, app, captures, 300, 3);
        assertEquals(Arrays.asList("Inicio", "Buscar", "Perfil"), captures);
    }

    @Test public void repeatedListItemsAreCappedAndMarkedNoEffectOnce() {
        ExplorationModel m = new ExplorationModel(); SimApp app = new SimApp(); run(m, app, new ArrayList<>(), 300, 99);
        ExplorationModel.Screen home = m.screens.values().iterator().next();
        int skippedRepeated = 0;
        for (ExplorationModel.Act a : home.acts) if (a.reason.startsWith("elemento repetido")) skippedRepeated++;
        assertEquals(3, skippedRepeated);
        int noEffect = 0;
        for (ExplorationModel.Screen s : m.screens.values()) for (ExplorationModel.Act a : s.acts) if (a.label.equals("Pulsar sin efecto") && a.reason.startsWith("sin efecto")) noEffect++;
        assertEquals(1, noEffect);
    }

    @Test public void revisitingScreensNeverCapturesAgainEvenAfterBackAndRestart() {
        ExplorationModel m = new ExplorationModel(); SimApp app = new SimApp(); List<String> captures = new ArrayList<>();
        run(m, app, captures, 300, 99);
        assertTrue(m.duplicateObservations > 0);
        assertEquals(m.capturedCount(), m.screens.size());
    }

    @Test public void resumeContinuesWithoutRecapturing() throws Exception {
        ExplorationModel m = new ExplorationModel(); SimApp app = new SimApp(); List<String> first = new ArrayList<>();
        assertEquals("limite", run(m, app, first, 300, 4));
        ExplorationModel restored = ExplorationModel.fromJson(new org.json.JSONObject(m.toJson().toString()));
        assertEquals(m.screens.size(), restored.screens.size());
        // New process: the app is relaunched; the model sees a restart landing on the root.
        app.restart();
        restored.performed(ExplorationModel.Decision.of(ExplorationModel.Decision.Type.RESTART, "reanudar"));
        List<String> second = new ArrayList<>();
        String reason = run(restored, app, second, 300, 99);
        assertEquals("completo", reason);
        for (String s : second) assertFalse("recaptured " + s, first.contains(s));
        assertEquals(9, first.size() + second.size());
    }

    @Test public void depthLimitSkipsAndReportsInsteadOfDrillingForever() {
        ExplorationModel m = new ExplorationModel(); m.maxDepth = 1; SimApp app = new SimApp(); List<String> captures = new ArrayList<>();
        assertEquals("completo", run(m, app, captures, 300, 99));
        assertFalse(captures.contains("Idioma"));
        boolean reported = false;
        for (ExplorationModel.Screen s : m.screens.values()) for (ExplorationModel.Act a : s.acts) if (a.reason.startsWith("profundidad")) reported = true;
        assertTrue(reported);
    }

    @Test public void routesDescribeHowEachScreenWasReached() {
        ExplorationModel m = new ExplorationModel(); SimApp app = new SimApp(); run(m, app, new ArrayList<>(), 300, 99);
        for (ExplorationModel.Screen s : m.screens.values()) if (s.title.equals("Idioma")) assertEquals(Arrays.asList("Inicio", "Ajustes", "Idioma"), m.routeTo(s));
    }

    @Test public void stalledAppStopsInsteadOfLooping() {
        // Every click is a no-op: the model must give up quickly.
        ExplorationModel m = new ExplorationModel(); SimApp app = new SimApp(); List<String> captures = new ArrayList<>();
        SimApp.Obs o = app.observe();
        m.observe(o.sig, o.hash, o.cands, o.title, o.section);
        String reason = "";
        for (int i = 0; i < 200; i++) {
            ExplorationModel.Decision d = m.next(o.cands);
            if (d.type == ExplorationModel.Decision.Type.STOP) { reason = d.reason; break; }
            m.performed(d);
            m.observe(o.sig, o.hash, o.cands, o.title, o.section);
        }
        assertTrue(reason, reason.startsWith("estancado") || reason.equals("completo"));
    }
    @Test public void nearlyIdenticalFramesUnderOneHeadingAreOneScreen() {
        SimApp app = new SimApp(); SimApp.Obs o = app.observe();
        ExplorationModel m = new ExplorationModel();
        m.observe(o.sig, o.hash, o.cands, "Saldo", "Saldo", "");
        long[] near = o.hash.clone(); near[0] ^= 0x7fffL; near[1] ^= 0x1fL;   // 20 bits apart: a content variation
        List<UiNode> other = new ArrayList<>();
        other.add(UiNode.text("Variante distinta de contenido", 40, 200, 600, 240));
        ScreenSignature different = ScreenSignature.of(other, 1);
        assertFalse(m.observe(different, near, o.cands, "Saldo · Imagen", "Saldo", "").isNew);
        assertTrue(m.observe(different, near, o.cands, "Otra pantalla", "Otra pantalla", "").isNew);
        assertEquals(2, m.screens.size());
    }
}