package com.axel.apkexplorer;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class DetectionTest {
    private static List<UiNode> screen(String title, int selectedTab, String... body) {
        List<UiNode> n = new ArrayList<>();
        n.add(UiNode.text(title, 20, 40, 400, 120));
        int y = 150; for (String b : body) { n.add(UiNode.text(b, 40, y, 680, y + 40)); y += 45; }
        String[] tabs = {"Inicio", "Buscar", "Perfil"};
        for (int i = 0; i < 3; i++) { UiNode t = UiNode.clickable(tabs[i], i * 240, 1500, i * 240 + 240, 1590); t.stateOn = i == selectedTab; n.add(t); }
        return n;
    }

    @Test public void volatileDigitsClockAndIdsDoNotChangeIdentity() {
        ScreenSignature a = ScreenSignature.of(screen("Biblioteca", 0, "68 archivos", "Hace 3 min 12:45", "frame_b63bfa6d-939f-454a-85c9-43c41ddc00d9.jpg", "Saldo 0,06 €", "Carpetas", "Recientes"), 1);
        ScreenSignature b = ScreenSignature.of(screen("Biblioteca", 0, "84 archivos", "Hace 7 min 13:01", "frame_00000000-1111-2222-3333-444444444444.jpg", "Saldo 1,10 €", "Carpetas", "Recientes"), 1);
        assertTrue(a.sameScreen(b));
        assertEquals(a.structureKey, b.structureKey);
    }

    @Test public void differentSelectedTabIsADifferentState() {
        ScreenSignature a = ScreenSignature.of(screen("Biblioteca", 0, "Carpetas", "Recientes", "Archivos", "Filtros", "Orden"), 1);
        ScreenSignature b = ScreenSignature.of(screen("Biblioteca", 1, "Carpetas", "Recientes", "Archivos", "Filtros", "Orden"), 1);
        assertFalse(a.sameScreen(b));
    }

    @Test public void dialogOrPopupWindowCreatesNewState() {
        List<UiNode> base = screen("Biblioteca", 0, "Carpetas", "Recientes", "Archivos", "Filtros", "Orden");
        ScreenSignature a = ScreenSignature.of(base, 1);
        List<UiNode> withDialog = new ArrayList<>(base); withDialog.add(UiNode.text("¿Seguro?", 100, 600, 600, 660));
        assertFalse(a.sameScreen(ScreenSignature.of(withDialog, 2)));
    }

    @Test public void differentBodyIsADifferentScreen() {
        ScreenSignature a = ScreenSignature.of(screen("Inicio", 0, "Uno dos", "Tres cuatro", "Cinco seis"), 1);
        ScreenSignature b = ScreenSignature.of(screen("Ajustes", 0, "Idioma", "Tema", "Privacidad", "Acerca de"), 1);
        assertFalse(a.sameScreen(b));
    }

    @Test public void progressIndicatorMarksLoading() {
        List<UiNode> n = screen("Cargando datos", 0, "Espera");
        UiNode p = new UiNode(); p.cls = "android.widget.ProgressBar"; p.progress = true; n.add(p);
        assertTrue(ScreenSignature.of(n, 1).loading);
        assertFalse(ScreenSignature.of(screen("Listo", 0, "Contenido"), 1).loading);
    }

    // ----- perceptual hash -----
    private static int[] frame(int seed, int noise) {
        int[] px = SimApp.pixels("frame" + seed);
        Random r = new Random(5);
        for (int i = 0; i < px.length; i += 97) { int v = (px[i] & 255) + (noise == 0 ? 0 : r.nextInt(2 * noise + 1) - noise); v = Math.max(0, Math.min(255, v)); px[i] = 0xff000000 | v << 16 | v << 8 | v; }
        return px;
    }

    @Test public void renderingNoiseStaysWithinTwinDistance() {
        long[] a = PerceptualHash.dhash(frame(1, 0), 64, 128), b = PerceptualHash.dhash(frame(1, 4), 64, 128);
        assertTrue(PerceptualHash.distance(a, b) <= PerceptualHash.TWIN);
    }

    @Test public void differentLayoutsAreFarApart() {
        long[] a = PerceptualHash.dhash(frame(1, 0), 64, 128), b = PerceptualHash.dhash(frame(2, 0), 64, 128);
        assertTrue(PerceptualHash.distance(a, b) > 40);
    }

    @Test public void hashRoundTripsThroughHex() {
        long[] a = PerceptualHash.dhash(frame(3, 0), 64, 128);
        assertArrayEquals(a, PerceptualHash.fromHex(PerceptualHash.toHex(a)));
    }

    // ----- stability -----
    private static int[] flat(int v) { int[] p = new int[64 * 128]; Arrays.fill(p, 0xff000000 | v << 16 | v << 8 | v); return p; }

    @Test public void staticUiIsStableAfterAShortWait() {
        StabilityTracker t = new StabilityTracker(0, 300, 7000, 6500);
        StabilityTracker.Verdict v = StabilityTracker.Verdict.WAIT; long now = 0;
        while (v == StabilityTracker.Verdict.WAIT && now < 3000) { now += 150; v = t.add(now, "k", flat(40), false); }
        assertEquals(StabilityTracker.Verdict.STABLE, v);
        assertTrue(now < 1200);
    }

    @Test public void structureStillChangingKeepsWaitingUntilTimeout() {
        StabilityTracker t = new StabilityTracker(0, 300, 2000, 6500);
        StabilityTracker.Verdict v = StabilityTracker.Verdict.WAIT; int i = 0;
        for (long now = 150; now <= 2100 && v == StabilityTracker.Verdict.WAIT; now += 150) v = t.add(now, "k" + (i++), flat(40), false);
        assertEquals(StabilityTracker.Verdict.TIMEOUT_UNSTABLE, v);
    }

    @Test public void spinnerWaitsForLoadingToEnd() {
        StabilityTracker t = new StabilityTracker(0, 300, 7000, 6500);
        for (long now = 150; now <= 3000; now += 150) assertEquals(StabilityTracker.Verdict.WAIT, t.add(now, "k", flat((int) now % 200), true));
        assertEquals(StabilityTracker.Verdict.STABLE, runUntilDone(t, 3150, "k2"));
    }

    private static StabilityTracker.Verdict runUntilDone(StabilityTracker t, long from, String key) {
        StabilityTracker.Verdict v = StabilityTracker.Verdict.WAIT;
        for (long now = from; now < from + 3000 && v == StabilityTracker.Verdict.WAIT; now += 150) v = t.add(now, key, flat(40), false);
        return v;
    }

    @Test public void animatedPixelsWithConstantStructureAreAccepted() {
        StabilityTracker t = new StabilityTracker(0, 300, 7000, 6500);
        StabilityTracker.Verdict v = StabilityTracker.Verdict.WAIT;
        for (long now = 150; now <= 2500 && v == StabilityTracker.Verdict.WAIT; now += 150) v = t.add(now, "k", flat((int) (now / 150) % 2 == 0 ? 20 : 220), false);
        assertEquals(StabilityTracker.Verdict.STABLE_ANIMATING, v);
    }

    // ----- policy and candidates -----
    @Test public void policyBlocksDestructiveSelectionAndAccountActions() {
        for (String l : new String[]{"Quitar 292 de la biblioteca", "Marcar todo", "Desmarcar", "Adjuntar", "Al Agente", "Borrar", "Cerrar sesión", "Delete account", "Comprar créditos", "Vaciar historial", "Publicar", "Enviar"})
            assertFalse(l, ActionPolicy.allowed(l));
        for (String l : new String[]{"Biblioteca", "Modelos", "Historial", "Ajustes", "Todos", "Imagen", "Video", "Audio", "Facturación", "Cancelar"})
            assertTrue(l, ActionPolicy.allowed(l));
    }

    @Test public void extractorClassifiesTabsNavAndRepeatedItems() {
        List<UiNode> n = new ArrayList<>();
        for (int i = 0; i < 4; i++) { UiNode chip = UiNode.clickable(new String[]{"Todos", "Imagen", "Video", "Audio"}[i], 32 + i * 150, 130, 160 + i * 150, 226); chip.stateOn = i == 0; n.add(chip); }
        for (int i = 0; i < 6; i++) n.add(UiNode.clickable("Carpeta " + (char) ('A' + i), (i % 4) * 177, 300 + (i / 4) * 200, (i % 4) * 177 + 170, 480 + (i / 4) * 200));
        n.add(UiNode.clickable("Buscar", 528, 13, 624, 109));
        UiNode sw = UiNode.clickable("Notificaciones", 0, 900, 720, 1000); sw.toggleControl = true; n.add(sw);
        UiNode edit = UiNode.clickable("Nombre", 0, 1000, 720, 1100); edit.editable = true; n.add(edit);
        Map<String, Candidate> byLabel = new HashMap<>();
        for (Candidate c : Candidate.extract(n, 720, 1600)) byLabel.put(c.label, c);
        assertEquals(Candidate.TAB, byLabel.get("Imagen").kind);
        assertEquals(Candidate.NAV, byLabel.get("Buscar").kind);
        assertEquals(Candidate.ITEM, byLabel.get("Carpeta A").kind);
        assertNull(byLabel.get("Carpeta A").skipReason);
        assertNull(byLabel.get("Carpeta B").skipReason);
        assertTrue(byLabel.get("Carpeta C").skipReason.startsWith("elemento repetido"));
        assertNotNull(byLabel.get("Notificaciones").skipReason);
        assertEquals("campo de texto", byLabel.get("Nombre").skipReason);
        assertTrue(byLabel.get("Imagen").rank < byLabel.get("Buscar").rank);
        assertTrue(byLabel.get("Buscar").rank < byLabel.get("Carpeta A").rank);
    }
    @Test public void loneCheckableControlIsASettingButChipRowsAreFilters() {
        List<UiNode> n = new ArrayList<>();
        UiNode sw = UiNode.clickable("Proveedor simulado", 552, 131, 656, 227); sw.checkableSub = true; n.add(sw);
        for (int i = 0; i < 3; i++) { UiNode chip = UiNode.clickable(new String[]{"Todos", "Imagen", "Video"}[i], 32 + i * 150, 600, 160 + i * 150, 690); chip.checkableSub = true; n.add(chip); }
        Map<String, Candidate> by = new HashMap<>();
        for (Candidate c : Candidate.extract(n, 720, 1600)) by.put(c.label, c);
        assertNotNull(by.get("Proveedor simulado").skipReason);
        assertNull(by.get("Imagen").skipReason);
    }

    @Test public void chatComposerMakesLongControlsMessagesButSettingsSearchDoesNot() {
        List<UiNode> chat = new ArrayList<>();
        UiNode box = UiNode.text("", 216, 1484, 496, 1580); box.editable = true; chat.add(box);
        chat.add(UiNode.clickable("Responder: Une el vídeo puente con los dos tramos", 20, 1200, 700, 1300));
        chat.add(UiNode.clickable("Conversaciones", 0, 0, 96, 96));
        Map<String, Candidate> by = new HashMap<>();
        for (Candidate c : Candidate.extract(chat, 720, 1600)) by.put(c.label, c);
        assertNotNull(by.get("Responder: Une el vídeo puente con los dos tramos").skipReason);
        assertNull(by.get("Conversaciones").skipReason);
        List<UiNode> settings = new ArrayList<>();
        UiNode search = UiNode.text("", 40, 130, 680, 220); search.editable = true; settings.add(search);
        settings.add(UiNode.clickable("Personalización Tema, tipografía, fondo, movimiento y háptica", 20, 300, 700, 440));
        for (Candidate c : Candidate.extract(settings, 720, 1600)) if (c.label.startsWith("Personal")) assertNull(c.skipReason);
    }
}