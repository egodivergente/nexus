package com.vidadomu.nexus

import com.vidadomu.nexus.assistant.ChatVisualDocument
import org.junit.Assert.*
import org.junit.Test

class ChatVisualDocumentTest {
    @Test fun generatedScriptsStayInsideTheIsolatedDocumentAndDoNotBreakOutOfTheWrapper() {
        val html=ChatVisualDocument.build("html","<button onclick=\"this.textContent='OK'\">Probar</button></script><script>window.test=1</script>")
        assertTrue(html.contains("sandbox=\"allow-scripts allow-downloads\""))
        assertFalse(html.contains("allow-same-origin"))
        assertFalse(html.contains("<button onclick"))
        assertTrue(html.contains("\\u003cbutton"))
    }
    @Test fun diagramsUseTheBundledRendererAndCanBeExportedWithoutNetworkDependencies() {
        val source="flowchart LR\nA[Idea] --> B[Resultado]"
        val preview=ChatVisualDocument.build("mermaid",source)
        assertTrue(preview.contains("/assets/renderer/mermaid.min.js"))
        assertTrue(preview.contains("securityLevel:'strict'"))
        val standalone=ChatVisualDocument.build("mermaid",source,mermaidScript="window.mermaid={render:()=>Promise.resolve({svg:'<svg></svg>'})}")
        assertFalse(standalone.contains("<script src="))
        assertTrue(standalone.contains("window.mermaid="))
    }
}
