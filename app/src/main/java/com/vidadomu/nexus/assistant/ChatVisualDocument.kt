package com.vidadomu.nexus.assistant

import kotlinx.serialization.json.JsonPrimitive

/** Self-contained previews: only the bundled diagram engine may load outside the document. */
internal object ChatVisualDocument {
    private fun quoted(value:String)=JsonPrimitive(value).toString().replace("<","\\u003c")
    fun build(language:String,source:String,dark:Boolean=false,motion:Boolean=true,mermaidScript:String?=null):String {
        val palette=if(dark) "dark" else "default"
        val reduce=if(motion) "" else "<style>*,*::before,*::after{animation:none!important;transition:none!important}</style>"
        val content=if(language=="mermaid") {
            val engine=mermaidScript?.let {"<script>${it.replace("</script","<\\/script",ignoreCase=true)}</script>"}
                ?: "<script src=\"https://appassets.androidplatform.net/assets/renderer/mermaid.min.js\"></script>"
            """<div id="diagram"></div><pre id="visual-error" role="alert" hidden></pre>$engine<script>
            mermaid.initialize({startOnLoad:false,securityLevel:'strict',theme:'$palette'});
            mermaid.render('nexus-diagram',${quoted(source)}).then(r=>{document.getElementById('diagram').innerHTML=r.svg;window.NEXUS_RENDERED=true;}).catch(e=>{const n=document.getElementById('visual-error');n.hidden=false;n.textContent='No se pudo renderizar el diagrama: '+e.message;});
            </script>"""
        } else {
            """<iframe id="visual" sandbox="allow-scripts allow-downloads" style="border:0;width:100%;height:100vh" title="Contenido interactivo"></iframe><script>document.getElementById('visual').srcdoc=${quoted(source+reduce)};</script>"""
        }
        return """<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
        <meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'unsafe-inline' https://appassets.androidplatform.net; style-src 'unsafe-inline'; img-src data: blob:; font-src data:; frame-src 'self' about: blob:; connect-src 'none'; form-action 'none'; base-uri 'none'">
        <style>body{margin:0;font:15px system-ui;color:${if(dark) "#eeeeee" else "#202124"};background:transparent}#diagram{padding:12px;overflow:auto}#diagram svg{max-width:100%;height:auto}pre{white-space:pre-wrap;padding:16px;color:#c33}</style>$reduce</head><body>$content</body></html>""".trimIndent()
    }
}
