package com.axel.apkexplorer;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class ExplorationRegressionTest {
    @Test public void primarySectionsPrecedeRepeatedErrorDetails(){
        List<String> labels=new ArrayList<>(Arrays.asList("Ver detalle del error","Ocultar detalle del error","Ajustes del asistente","Lienzo Lienzo","Modelos Modelos","Biblioteca Biblio"));
        labels.sort(Comparator.comparingInt(s->ActionPolicy.priority(s,false)));
        assertEquals(Arrays.asList("Lienzo Lienzo","Modelos Modelos","Biblioteca Biblio"),labels.subList(0,3));
    }
    @Test public void repositionedRepeatedLabelsHaveOneBudget(){
        Set<String> attempted=new HashSet<>();
        assertTrue(attempted.add(ActionPolicy.identity("Ver detalle del error")));
        assertFalse(attempted.add(ActionPolicy.identity("Ver  detalle del error ")));
        assertEquals(ActionPolicy.identity("Modelos"),ActionPolicy.identity("Modelos Modelos"));
    }
    @Test public void rejectsExactDuplicateImagesEvenWithDifferentXml(){
        int[] first=new int[8192];Arrays.fill(first,0x102F39);
        assertTrue(VisualFingerprint.same(first,first.clone()));
    }
    @Test public void ignoresSmallRenderingNoise(){
        int[] a=new int[8192],b=new int[8192];Arrays.fill(a,0x102F39);Arrays.fill(b,0x112F39);
        assertTrue(VisualFingerprint.same(a,b));
    }
    @Test public void retainsDialogsAndSmallMeaningfulRegions(){
        int[] a=new int[8192],b=new int[8192];Arrays.fill(a,0x102F39);Arrays.fill(b,0x102F39);
        Arrays.fill(b,100,220,0xffffff);
        assertFalse(VisualFingerprint.same(a,b));
        assertFalse(VisualFingerprint.same(a,new int[1]));
    }
}
