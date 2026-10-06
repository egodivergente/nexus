package com.vidadomu.nexus.ui.models

import com.vidadomu.nexus.core.model.NexusModel

/** Editorial shortlist by executable route. Unknown or removed routes never get invented. */
internal data class FocusAdvice(val good: String, val avoid: String)

internal object CatalogFocus {
    private val advice = mapOf(
        "bytedance/seedream-v5.0-pro/text-to-image" to FocusAdvice("Fotografía, producto y composiciones con texto.", "Quieres editar una foto: usa Edit."),
        "bytedance/seedream-v5.0-pro/edit" to FocusAdvice("Cambios dirigidos sobre una imagen existente.", "No tienes imagen de partida."),
        "google/nano-banana-2/text-to-image" to FocusAdvice("Explorar conceptos visuales en varias rondas.", "Necesitas cambiar una foto concreta: usa Edit."),
        "google/nano-banana-2/edit" to FocusAdvice("Probar cambios sobre referencias visuales.", "No tienes imagen de partida."),
        "black-forest-labs/flux-2-pro/text-to-image" to FocusAdvice("Crear desde texto con control de composición y estilo.", "Necesitas conservar referencias: usa Edit."),
        "black-forest-labs/flux-2-pro/edit" to FocusAdvice("Combinar y editar varias imágenes de referencia.", "No tienes archivos de referencia."),
        "microsoft/mai-image-2.6-flash/text-to-image" to FocusAdvice("Imagen desde texto con tarifa por imagen publicada.", "Necesitas editar una foto: usa su variante Edit."),
        "microsoft/mai-image-2.6-flash/edit" to FocusAdvice("Editar una foto con tarifa por imagen publicada.", "No tienes imagen de partida."),
        "openai/gpt-image-2.5-sunburst/text-to-image" to FocusAdvice("Crear imágenes siguiendo un prompt detallado.", "Necesitas modificar una imagen: usa Edit."),
        "openai/gpt-image-2.5-sunburst/edit" to FocusAdvice("Modificar una imagen con instrucciones en lenguaje natural.", "No tienes imagen de entrada."),
        "qwen-image-3.0-pro/text-to-image" to FocusAdvice("Composiciones con texto en inglés o chino según su ficha.", "Quieres vídeo o una foto de partida."),
        "qwen-image-3.0-pro/edit" to FocusAdvice("Modificar una imagen existente con instrucciones.", "No tienes imagen de entrada."),
        "ideogram/v4/quality/text-to-image" to FocusAdvice("Crear una composición con la variante Quality.", "Necesitas editar una imagen previa."),
        "xai/grok-imagine-image-2.0/text-to-image" to FocusAdvice("Probar otra interpretación visual del mismo prompt.", "Necesitas mantener una referencia exacta."),
        "xai/grok-imagine-image-2.0/edit" to FocusAdvice("Transformar una imagen existente.", "Quieres empezar sin imagen."),
        "runway/gen4_image/image" to FocusAdvice("Crear o editar imágenes con referencias en Runway.", "Necesitas animar un clip."),
        "luma/uni-1/text-to-image" to FocusAdvice("Crear imágenes desde texto en otro motor.", "Necesitas editar: usa Edit."),
        "luma/uni-1/edit" to FocusAdvice("Cambiar una imagen existente con instrucciones.", "No tienes imagen de entrada."),
        "alibaba/wan-2.7/text-to-image" to FocusAdvice("Crear imágenes desde una descripción.", "Quieres animar una imagen."),
        "runway/magnific_precision_upscaler_v2/upscale-image" to FocusAdvice("Aumentar resolución y ajustar detalle de una imagen.", "Quieres crear una imagen nueva."),
        "minimax/h3/text-to-video" to FocusAdvice("Crear un clip desde la descripción de una escena.", "Necesitas animar un fotograma concreto."),
        "minimax/h3/image-to-video" to FocusAdvice("Dar movimiento a una imagen de partida.", "No tienes imagen de entrada."),
        "bytedance/seedance-2.5/text-to-video" to FocusAdvice("Crear escenas de vídeo desde texto.", "Necesitas conservar un fotograma: usa la variante de imagen."),
        "bytedance/seedance-2.5/image-to-video" to FocusAdvice("Dirigir el movimiento desde un fotograma inicial.", "Quieres generar sin imagen."),
        "crun/kling-v3-std" to FocusAdvice("Crear un clip con tarifa por cinco segundos.", "Necesitas transferir un movimiento existente."),
        "crun/kling-v3-motion-control-std" to FocusAdvice("Aplicar la actuación de un vídeo al personaje de una imagen.", "No tienes ambos archivos: imagen y vídeo."),
        "google/veo3.1/text-to-video" to FocusAdvice("Crear un plano de vídeo desde la descripción de la escena.", "Necesitas animar una imagen concreta."),
        "google/veo3.1/image-to-video" to FocusAdvice("Animar un fotograma inicial con instrucciones.", "No tienes imagen de entrada."),
        "runway/gen4.5/t2v" to FocusAdvice("Crear un clip indicando escena, duración y encuadre.", "Necesitas un fotograma inicial concreto."),
        "runway/gen4.5/i2v" to FocusAdvice("Crear un clip desde una imagen inicial.", "No tienes imagen de entrada."),
        "alibaba/wan-3.0-prime/text-to-video" to FocusAdvice("Crear una escena de vídeo desde texto.", "Necesitas referencias: usa esa variante."),
        "alibaba/wan-3.0-prime/image-to-video" to FocusAdvice("Dar movimiento a un fotograma inicial.", "No tienes imagen."),
        "alibaba/wan-3.0-prime/reference-to-video" to FocusAdvice("Guiar una escena con referencias visuales.", "Quieres un clip solo desde texto."),
        "luma/ray-3.2/text-to-video" to FocusAdvice("Crear un plano desde una descripción.", "Necesitas editar un vídeo grabado."),
        "luma/ray-3.2/image-to-video" to FocusAdvice("Dar movimiento a una imagen inicial.", "No tienes imagen de partida."),
        "luma/ray-3.2/video-to-video" to FocusAdvice("Transformar un vídeo de entrada.", "Quieres crear desde cero."),
        "vidu/q3-pro/start-end-to-video" to FocusAdvice("Dirigir la transición entre dos fotogramas.", "No tienes inicio y final."),
        "pixverse/v6/video-extend" to FocusAdvice("Continuar un clip ya creado.", "No tienes vídeo de entrada."),
        "minimax/hailuo-2.3/t2v-standard" to FocusAdvice("Crear un clip desde texto con la ruta Standard.", "Necesitas animar una imagen."),
        "minimax/hailuo-2.3/i2v-standard" to FocusAdvice("Crear un clip desde una imagen con la ruta Standard.", "No tienes imagen de partida.")
    )

    fun forModel(model: NexusModel): FocusAdvice? = advice[model.id]
}
