package com.vidadomu.nexus.provider.crun
import com.vidadomu.nexus.core.model.*
import kotlinx.serialization.json.*

object CrunTextCatalog {
    fun decode(raw:String):List<NexusModel> {
        val root=Json.parseToJsonElement(raw).jsonObject
        val data=root["data"] as? JsonArray ?: error("Formato de catálogo de texto no reconocido")
        return data.mapNotNull { entry ->
            val item=entry as? JsonObject ?: return@mapNotNull null
            val id=item["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            NexusModel(id="crun/chat/$id",providerModelId=id,displayName=id,providerId="crun",
                organization=item["owned_by"]?.jsonPrimitive?.contentOrNull ?: "Crun",
                modalities=listOf(Modality.CHAT),capabilities=listOf(ModelCapability.CHAT_COMPLETION),
                description=item["description"]?.jsonPrimitive?.contentOrNull ?: "Modelo de texto disponible en tu cuenta Crun. El proveedor no ha entregado una descripción de sus especialidades.",
                parameters=listOf(ModelParameter("max_tokens","Límite de respuesta",ParameterType.INTEGER,"512",minValue=1f,nativeType="integer")),
                contractEndpoint="/api/v1/chat/completions",contractPromptField="prompt",promptRequired=true)
        }
    }
    val suno:List<NexusModel> = listOf("v5.5","v5","v4.5plus","v4.5","v4.5all","v4").map { version ->
        NexusModel(id="crun/suno/$version",providerModelId="suno/music-generate",displayName="Suno $version",providerId="crun",organization="Suno",
            modalities=listOf(Modality.AUDIO),capabilities=listOf(ModelCapability.TEXT_TO_AUDIO),
            description=when(version) {
                "v5.5"->"Suno 5.5 compone música desde una idea o desde tu letra. Crun lo presenta como una versión orientada a personalizar el resultado musical. Define género, ambiente e instrumentación, o activa instrumental para prescindir de la voz."
                "v5"->"Suno 5 convierte una descripción o letra en una canción. La documentación destaca su expresión musical y velocidad; permite dirigir el estilo y excluir géneros. Útil para maquetas cantadas y bandas sonoras originales."
                "v4"->"Suno 4 genera canciones de hasta cuatro minutos. En modo con letra, admite 3000 caracteres de letra y 200 de estilo. Úsalo para piezas breves o para mantener el sonido de proyectos hechos con esta versión."
                else->"Suno $version genera piezas de hasta ocho minutos según Crun. Puedes partir de una idea o escribir letra y estilo por separado; la variante seleccionada se envía expresamente al proveedor."
            },
            parameters=listOf(
                ModelParameter("mode","Cómo componer",ParameterType.ENUM,"simple",options=listOf("simple","custom"),isRequired=true,nativeType="string"),
                ModelParameter("instrumental","Solo instrumental",ParameterType.BOOLEAN,"false",isRequired=true,nativeType="boolean"),
                ModelParameter("title","Título (modo custom)",ParameterType.TEXT,"",nativeType="string"),
                ModelParameter("tags","Estilo musical (modo custom)",ParameterType.TEXT,"",nativeType="string"),
                ModelParameter("lyrics","Letra (modo custom)",ParameterType.LONG_TEXT,"",nativeType="string")
            ),contractEndpoint="/api/v1/client/job/CreateTask",contractDefaults=mapOf("model" to version),contractPromptField="prompt",promptRequired=false)
    }
    fun validateSuno(model:NexusModel,request:GenerationRequest) {
        if(model.providerModelId!="suno/music-generate") return
        val p=model.parameters.associate { it.key to it.defaultValue }+request.parameters
        if(p["mode"]=="simple") require(request.prompt.isNotBlank() && request.prompt.length<=500) { "Describe la canción con entre 1 y 500 caracteres." }
        else {
            require(!p["title"].isNullOrBlank() && p["title"]!!.length<=100) { "Añade un título de hasta 100 caracteres." }
            val v4=model.contractDefaults["model"]=="v4"
            require(!p["tags"].isNullOrBlank() && p["tags"]!!.length<=if(v4)200 else 1000) { "Añade el estilo musical y respeta su longitud máxima." }
            if(p["instrumental"]!="true") require(!p["lyrics"].isNullOrBlank() && p["lyrics"]!!.length<=if(v4)3000 else 5000) { "Añade una letra válida para esta versión de Suno." }
        }
    }
}
