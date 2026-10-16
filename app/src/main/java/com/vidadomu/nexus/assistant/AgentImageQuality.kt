package com.vidadomu.nexus.assistant

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.vidadomu.nexus.agent.core.assets.Asset
import com.vidadomu.nexus.agent.core.assets.AssetType
import com.vidadomu.nexus.agent.core.tools.ToolInputException
import kotlinx.serialization.json.*
import kotlin.math.max

/** Cheap measurements on a sampled image; perceptual defects require actual vision. */
internal object AgentImageQuality {
    fun inspect(context:Context,asset:Asset):JsonObject {
        if(asset.type!=AssetType.IMAGE) throw ToolInputException("Para vídeo usa view_library_asset y extrae el fotograma que necesite revisión.")
        val uri=Uri.parse(asset.uri)
        val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true}
        context.contentResolver.openInputStream(uri)?.use {BitmapFactory.decodeStream(it,null,bounds)}
        if(bounds.outWidth<=0 || bounds.outHeight<=0) throw ToolInputException("No se pudo decodificar la imagen.")
        val options=BitmapFactory.Options().apply {inSampleSize=1;while(max(bounds.outWidth,bounds.outHeight)/inSampleSize>512) inSampleSize*=2}
        val bitmap=context.contentResolver.openInputStream(uri)?.use {BitmapFactory.decodeStream(it,null,options)} ?: throw ToolInputException("No se pudo leer la imagen.")
        try {
            val w=bitmap.width;val h=bitmap.height
            val pixels=IntArray(w*h);bitmap.getPixels(pixels,0,w,0,0,w,h)
            val luminance=IntArray(pixels.size)
            var shadows=0;var highlights=0;var sum=0.0
            pixels.forEachIndexed {i,c->
                val y=(54*((c ushr 16) and 255)+183*((c ushr 8) and 255)+19*(c and 255))/256
                luminance[i]=y;sum+=y;if(y<5)shadows++;if(y>250)highlights++
            }
            var edgeSum=0.0;var edgeSquares=0.0;var n=0
            for(y in 1 until h-1) for(x in 1 until w-1) {
                val i=y*w+x;val edge=(4*luminance[i]-luminance[i-1]-luminance[i+1]-luminance[i-w]-luminance[i+w]).toDouble()
                edgeSum+=edge;edgeSquares+=edge*edge;n++
            }
            val sharpness=if(n>0) max(0.0,edgeSquares/n-(edgeSum/n)*(edgeSum/n)) else 0.0
            return buildJsonObject {
                put("asset_id",asset.id);put("width",bounds.outWidth);put("height",bounds.outHeight)
                put("sample_width",w);put("sample_height",h);put("mean_luminance",sum/pixels.size)
                put("near_black_percent",100.0*shadows/pixels.size);put("near_white_percent",100.0*highlights/pixels.size)
                put("laplacian_variance",sharpness)
                put("small_for_hd",minOf(bounds.outWidth,bounds.outHeight)<720)
                put("review","Inspecciona la imagen adjunta: identidad, anatomía, objetos duplicados, bordes, texto y continuidad. Describe ubicación, evidencia y corrección. Estas medidas no prueban glitches: fondos lisos, bokeh y blanco/negro deliberados alteran los valores. No uses un umbral universal de nitidez.")
                put("requested_context_load",true)
            }
        } finally {bitmap.recycle()}
    }
}
