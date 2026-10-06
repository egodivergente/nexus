package com.vidadomu.nexus.provider.runway

import com.vidadomu.nexus.core.model.*
import kotlin.math.ceil
import kotlin.math.max

data class RunwayQuote(val usd:Double?,val detail:String)

/** Official public rate card, 2026-09-22. An estimate is never stored as a charge. */
object RunwayPricing {
    fun quote(model:NexusModel,parameters:Map<String,String>?=null,references:Int=0,inputSeconds:Double?=null,promptLength:Int?=null):RunwayQuote {
        val p=model.parameters.associate { it.key to it.defaultValue } + parameters.orEmpty()
        val id=model.providerModelId ?: model.id.removePrefix("runway/").substringBefore('/')
        val ratio=p["ratio"].orEmpty();val dims=ratio.split(':').mapNotNull(String::toDoubleOrNull)
        val pixels=if(dims.size==2)dims[0]*dims[1] else 0.0
        val explicit=p["resolution"].orEmpty().lowercase()
        val tier=when { explicit.isNotBlank()->explicit; "1080" in ratio->"1080p";"720" in ratio->"720p";"480" in ratio->"480p";pixels>4_000_000->"4k";pixels>1_200_000->"1080p";pixels>500_000->"720p";else->"480p" }
        val seconds=p["duration"]?.toDoubleOrNull() ?: inputSeconds
        val count=p["outputCount"]?.toIntOrNull() ?: 1
        val audio=p["audio"]?.toBooleanStrictOrNull() ?: true
        val quality=p["quality"]?.ifBlank { "high" } ?: "high"
        val requiresVideo=model.contractMedia.any { it.required && it.mime.startsWith("video/") }
        fun unknown(reason:String)=RunwayQuote(null,reason)
        val credits:Double=when {
            Modality.IMAGE in model.modalities && id!="magnific_precision_upscaler_v2" -> {
                if(ratio=="auto") return unknown("El precio depende del tamaño que el proveedor elija automáticamente.")
                val high4k=ratio in setOf("3840:1648","3840:2160","3504:2336","3264:2448","3200:2560","2880:2880","2560:3200","2448:3264","2336:3504","2160:3840","auto")
                val perImage=when(id) {
                    "gen4_image" -> if(ratio in setOf("1280:720","720:1280","720:720","960:720","720:960","1680:720")) 5.0 else 8.0
                    "gen4_image_turbo" -> 2.0
                    "muse_image" -> 1.0
                    "seedream5_lite" -> 4.0
                    "seedream5_pro" -> if(pixels>2_000_000 || ratio=="auto_2k")9.0 else 5.0
                    "gemini_image3_pro" -> if(pixels>8_000_000)40.0 else 20.0
                    "gemini_2.5_flash" -> 5.0
                    "grok_imagine_image_2" -> (if(p["quality"]=="low")4.0 else 6.0)+(if(pixels>2_000_000 || ratio=="auto_2k")2.0 else 0.0)
                    "gpt_image_2" -> when(quality){"low"->if(high4k)2.0 else 1.0;"medium"->if(high4k)11.0 else 5.0;else->if(high4k)41.0 else 20.0}
                    "gpt_image_2_5_flare","gpt_image_2_5_sunburst" -> when(quality){"low"->if(high4k)2.0 else 1.0;"medium"->if(high4k)11.0 else 5.0;"xhigh"->if(high4k)34.0 else 28.0;"max"->if(high4k)76.0 else 63.0;else->if(high4k)19.0 else 16.0}
                    else -> return unknown("Esta variante no tiene una tarifa pública confirmada en la tabla revisada.")
                }
                val refCharge=when(id){"grok_imagine_image_2"->references.toDouble();"gpt_image_2_5_flare","gpt_image_2_5_sunburst"->references*count.toDouble();else->0.0}
                max(if(id=="grok_imagine_image_2")6.0 else 0.0,perImage*count+refCharge)
            }
            id in setOf("eleven_v3","eleven_multilingual_v2") -> {
                val chars=promptLength ?: return unknown("El precio depende de los caracteres del texto.")
                max(1.0,ceil(chars/50.0))
            }
            id=="eleven_text_to_sound_v2" -> seconds ?: 2.0
            id=="seed_audio" -> return unknown("La tarifa depende de la duración del audio generado: 0,25 créditos/s, mínimo 5 créditos.")
            id in setOf("eleven_voice_isolation","eleven_voice_dubbing","eleven_multilingual_sts_v2") -> {
                val duration=inputSeconds ?: return unknown("Añade el audio para calcular su duración y coste.")
                ceil(duration / when(id){"eleven_voice_isolation"->6.0;"eleven_voice_dubbing"->2.0;else->3.0})
            }
            id=="magnific_precision_upscaler_v2" -> return unknown("25 créditos por imagen; 150 si la salida supera 4096 px. Hay que comprobar el tamaño final.")
            id=="magnific_video_upscaler_creative" -> return unknown("Tarifa por fotograma: requiere duración, resolución y frecuencia de imagen; no se estima como segundos.")
            id=="ruby" -> return unknown("20 créditos/s; 40 si el vídeo supera 4 megapíxeles. Requiere comprobar el archivo.")
            id=="enhance_frame_rate" -> ceil((inputSeconds ?: return unknown("Añade el vídeo para calcular el precio."))/2.0)
            else -> {
                val duration=seconds ?: return unknown("Selecciona una duración concreta para calcular el precio.")
                if(duration<=0 || !duration.isFinite()) return unknown("La duración automática se confirma al finalizar.")
                val rate=when(id) {
                    "wan3" -> when(tier){"1080p"->20.0;"720p"->10.0;else->5.0}
                    "seedance2_5" -> when(tier){"1080p"->68.0;"720p"->30.0;else->20.0}
                    "seedance2" -> when(tier){"4k"->150.0;"1080p"->40.0;else->36.0}
                    "seedance2_fast" -> 29.0
                    "seedance2_mini" -> 16.0
                    "grok_imagine_1_5" -> when(tier){"1080p"->29.0;"720p"->16.0;else->10.0}
                    "h3_max" -> if(tier=="768p")8.0 else 5.0
                    "hailuo3" -> if(tier=="2k")15.0 else 10.0
                    "aleph2" -> 28.0
                    "gen4.5" -> 12.0
                    "gen4_turbo","act_two" -> 5.0
                    "veo3.1" -> if(audio)40.0 else 20.0
                    "veo3.1_fast" -> if(audio)15.0 else 10.0
                    "happyhorse_1_0" -> if(tier=="1080p")30.0 else 15.0
                    "gemini_omni_flash" -> if(requiresVideo)11.0 else 10.0
                    else -> return unknown("Tarifa pendiente de confirmar para esta variante.")
                }
                val input=if(id in setOf("seedance2_5","hailuo3")) inputSeconds ?: if(requiresVideo) return unknown("Falta la duración del vídeo de referencia para cerrar el presupuesto.") else 0.0 else 0.0
                var cost=duration*rate + when(id){"seedance2_5"->input*rate/2;"hailuo3"->input.coerceAtMost(15.0)*rate+references*2;"grok_imagine_1_5","gemini_omni_flash"->references.toDouble();else->0.0}
                cost=max(cost,when(id){"seedance2_5"->80.0;"seedance2_mini"->64.0;"aleph2"->56.0;else->0.0})
                val format=p["outputFormat"].orEmpty()
                cost+=duration*when { format in setOf("prores","png_sequence")->5.0;format.startsWith("hdr") || format in setOf("hlg","sdr_rec709_10bit")->if(pixels>4_000_000)40.0 else 20.0;else->0.0 }
                cost
            }
        }
        return if(credits.isFinite() && credits>=0) RunwayQuote(credits*.01,"Runway · tarifa pública revisada 22/09/2026 · $credits créditos · antes de impuestos") else unknown("No se ha podido calcular un precio válido.")
    }
}
