package com.axel.apkexplorer;
import org.junit.Test;
import static org.junit.Assert.*;
public class ActionPolicyTest {
    @Test public void rejectsChangesAndEmptyLabels(){for(String s:new String[]{"Mover a otro proyecto","Eliminar","Guardar","Add Person","Crear carpeta","Enviar","","+","Importar","Share","Renombrar"})assertFalse(s,ActionPolicy.allowed(s));}
    @Test public void permitsNavigation(){for(String s:new String[]{"Modelos","Biblioteca","Cancelar","Atrás","Ver imagen"})assertTrue(s,ActionPolicy.allowed(s));}
    @Test public void escapesReportMarkup(){assertEquals("&lt;img&gt;&amp;&quot;&#39;",ActionPolicy.html("<img>&\"'"));}
}
