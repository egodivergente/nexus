package com.vidadomu.nexus.ui.models

import com.vidadomu.nexus.ui.theme.NexusTokens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.vidadomu.nexus.core.model.NexusModel

internal data class ResearchedModelNote(val evidence: String, val implication: String, val source: String)

/** Version-specific first-party evidence reviewed 2026-10-23. Provider contracts remain authoritative for inputs. */
internal fun researchedModelNote(model: NexusModel): ResearchedModelNote? {
    val id=(model.providerModelId ?: model.id).lowercase()
    return when {
        "grok-imagine-video" in id && "1.5" in id -> ResearchedModelNote(
            "xAI distingue 1080p nativo en texto/imagen de 720p al usar referencias. La variante Lite reescala 720p y no ofrece los mismos controles de referencias y voz.",
            "Para personaje y voz dirigidos, comprueba la ruta de referencias; para salida 1080p nativa, comprueba la ruta texto/imagen. Developer no se equipara automáticamente a Lite.","https://docs.x.ai/developers/model-capabilities/video/overview")
        "grok-imagine-image-2.0" in id -> ResearchedModelNote(
            "La API original de xAI admite edición con hasta 5 referencias y salida 1K/2K.",
            "La cantidad admitida aquí es la del contrato de tu proveedor; no heredes el límite de otra variante o de la web.","https://docs.x.ai/developers/model-capabilities/imagine")
        "minimax/h3-max" in id -> ResearchedModelNote(
            "H3 Max está optimizado para velocidad, con postentrenamiento de fal.ai. MiniMax publica unos 15 s para generar 5 s y unos 40 s para 15 s en MiniMax Design.",
            "Max no significa automáticamente más calidad que H3. Es una elección para iterar rápido; esos tiempos no son una promesa de latencia de Atlas.","https://design.minimax.io/tools/minimax-h3-max")
        "minimax/h3" in id -> ResearchedModelNote(
            "H3 abierto: 4–15 s, 24 fps y audio estéreo de 32 kHz. El modelo base trabaja a 768p en el lado corto; H3-Regenerate-2K es una operación específica para 2K.",
            "FL2VA controla inicio/final; Ref2VA admite contexto multimodal. No confundas esas rutas ni interpretes toda salida 2K como resolución nativa del modelo base.","https://www.minimax.io/news/minimax-h3-open-source")
        "hailuo-2.3" in id && "fast" in id -> ResearchedModelNote(
            "MiniMax describe 2.3 Fast como imagen a vídeo: 6 s a 768p en torno a 55 s en la integración de VEED.",
            "Es una ruta para animar una imagen y reducir espera/coste; no una variante equivalente de texto a vídeo. El tiempo depende de la plataforma.","https://www.minimax.io/news/minimax-and-veed-hailuo-23-for-pro-level-ai-video")
        "hailuo-2.3" in id -> ResearchedModelNote(
            "MiniMax atribuye a 2.3 mejoras de microexpresión, movimiento corporal y respuesta a órdenes de movimiento respecto a Hailuo 02.",
            "Interesa para actuación y acción de personajes. Standard/Pro y 768p/1080p se eligen con los parámetros de esta ruta, sin asumir que Fast conserva el mismo modo.","https://www.minimax.io/news/minimax-hailuo-23")
        "seedance-2.5" in id -> ResearchedModelNote(
            "El modelo original amplía a 30 s y referencias de hasta 30 imágenes, 10 vídeos y 10 audios, con edición por timestamp.",
            "Conviene para continuidad y dirección de varios elementos. En Nexus usa solo los campos que publique este endpoint; la capacidad del modelo completo no añade controles ausentes en el proveedor.","https://seed.bytedance.com/en/blog/one-take-creation-flexible-referencing-introducing-seedance-2-5")
        "seedream" in id && ("5.0-pro" in id || "5.0-flash" in id) -> ResearchedModelNote(
            "Pro y Flash 5.0 incorporan edición con coordenadas <point>/<bbox> y separación en una base más hasta 16 capas PNG con alfa. La API original no admite generación secuencial en estas dos variantes.",
            "Para colocar un elemento usa coordenadas, no solo adjetivos. Para varias imágenes usa envíos independientes; no envíes el parámetro sequential de Lite/4.5 por herencia.","https://docs.byteplus.com/en/docs/ModelArk/seedream-5-0-pro")
        "flux-2" in id || "flux.2" in id -> {
            val variant=when {"flex" in id->"Flex prioriza tipografía y ajustes de pasos/guía.";"klein" in id->"Klein prioriza latencia y volumen; sus tamaños 4B y 9B no son intercambiables.";"max" in id->"Max prioriza el acabado final e incorpora búsqueda fundamentada en la API original.";"dev" in id->"Dev es la variante de desarrollo; no hereda automáticamente la licencia comercial de los servicios Pro.";else->"Pro busca equilibrio de coste, velocidad y acabado de producción."}
            ResearchedModelNote(variant+" BFL diferencia 8 referencias en la API Pro frente a 10 en su playground.","Para texto pequeño valora Flex; para iteración rápida, Klein. Confirma referencias y controles en la ruta del proveedor.","https://help.bfl.ai/articles/6122710168-which-flux-2-model-should-i-choose")
        }
        "gen4.5" in id || "gen-4.5" in id -> ResearchedModelNote(
            "Runway publica 2–10 s, salida 720p y 24/25 fps para Gen-4.5 en su interfaz. Está orientado a instrucciones secuenciadas y coreografía de cámara.",
            "La resolución del servicio y un upscale posterior son pasos distintos. En I2V dedica el prompt a movimiento/cámara; la imagen ya define el aspecto inicial.","https://help.runwayml.com/hc/en-us/articles/46974685288467-Creating-with-Gen-4-5")
        "qwen-image-3.0" in id -> ResearchedModelNote(
            "Qwen publica entrada de hasta 4,5k tokens, texto de 10 px y generación nativa en 12 idiomas para Image 3.0.",
            "Es una candidatura para páginas densas y composición con tipografía. 4,5k tokens no son 4.500 caracteres: el máximo del endpoint se muestra con su propia unidad.","https://qwen.ai/blog?id=qwen-image-3.0")
        "ideogram" in id && ("v4" in id || "4.0" in id) -> ResearchedModelNote(
            "Ideogram 4.0 publica OCR inglés 0,97 en X-Omni, mIoU 0,69 en 7Bench y alineación 0,89 en Prism-bench. Son métricas del fabricante y de su configuración de evaluación.",
            "Para tipografía crítica distingue Quality 48 de Default 20 y Turbo 12; el proveedor documenta colas de pulido y tiempos distintos. Esos benchmarks no prueban que todas las variantes rindan igual.","https://ideogram.ai/blog/ideogram-4.0/")
        "veo3.1" in id || "veo-3.1" in id || "veo3-1" in id -> ResearchedModelNote(
            "En Gemini, 1080p/4K y referencias exigen 8 s; la extensión queda en 720p. La semilla no garantiza determinismo.",
            "No mezcles resolución, duración y referencias como opciones independientes. Flow y otros proveedores pueden exponer combinaciones distintas: consulta los ajustes de esta ruta.","https://ai.google.dev/gemini-api/docs/veo")
        "pixverse" in id && ("v6" in id || "/6" in id) -> ResearchedModelNote(
            "V6 Omni admite hasta 10 imágenes y 2 vídeos con duración conjunta de hasta 15 s. Con referencia de vídeo, duration=0 adapta la salida al vídeo más largo.",
            "La referencia de vídeo cambia duración y tarifa; no es solo otra imagen adjunta. Extender, Mimic y Lip Sync son operaciones separadas.","https://docs.platform.pixverse.ai/fusion-reference-to-video-v6-video-reference-integration-guide-2269890m0")
        "luma/uni-1" in id || "uni_1" in id -> ResearchedModelNote(
            "Luma distingue source para editar una imagen de image_ref para crear con referencias; Uni-1 y Uni-1 Max comparten parámetros pero no precio/calidad.",
            "Una imagen como inspiración y una imagen que debe conservarse requieren rutas distintas. Comprueba si esta ficha abre creación o edición.","https://docs.agents.lumalabs.ai/guides/model/")
        else -> null
    }
}

@Composable internal fun ModelResearchNotes(model: NexusModel) {
    val note=remember(model) {researchedModelNote(model)} ?: return
    val uri=LocalUriHandler.current
    var open by remember {mutableStateOf(false)}
    Column(verticalArrangement=Arrangement.spacedBy(NexusTokens.space4)) {
        Text(note.evidence,style=MaterialTheme.typography.bodySmall)
        Text(note.implication,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick={open=!open},contentPadding=PaddingValues(0.dp)) {Text(if(open) "Ocultar procedencia" else "Fuente y criterio")}
        if(open) {Text("Revisión editorial · 23 oct 2026 · especificaciones del fabricante; límites de tu proveedor arriba.",style=MaterialTheme.typography.labelSmall);TextButton(onClick={uri.openUri(note.source)}) {Text("Abrir fuente primaria")}}
    }
}
