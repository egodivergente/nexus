package com.vidadomu.nexus.ui.create
import android.content.Context
import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.provider.atlas.*
object LocalQaFixture {
    fun model(context:Context,kind:String="motion"):NexusModel {
        check(com.vidadomu.nexus.BuildConfig.OFFLINE_QA)
        val imageForm=kind in setOf("image", "references")
        val base=NexusModel(parameters=emptyList(),capabilities=emptyList(),id="kwaivgi/kling-v3.0-std/motion-control",providerId="atlas_cloud",displayName="Kling · Motion Control",organization="Kling",family="Kling",modalities=listOf(Modality.VIDEO))
        val configured=if(imageForm) base.copy(id="openai/gpt-image-2.5-flare/edit",displayName="GPT Image · Edición",organization="OpenAI",family="GPT Image",modalities=listOf(Modality.IMAGE)) else base
        return NativeModelSchema.decode(configured,context.assets.open(if(imageForm) "qa-image.json" else "qa-motion.json").bufferedReader().use {it.readText()}).model
    }
    fun media(context:Context,model:NexusModel,count:Int=1):List<AttachedMedia> {
        val image=java.io.File(context.filesDir,"qa-image.png")
        android.graphics.Bitmap.createBitmap(256,256,android.graphics.Bitmap.Config.ARGB_8888).let {bitmap ->
            bitmap.eraseColor(android.graphics.Color.rgb(24,44,48));image.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        }
        return (1..count.coerceIn(1,3)).map { index ->
            val file=if(index==1) image else java.io.File(context.filesDir,"qa-image-$index.png").also { image.copyTo(it,overwrite=true) }
            AttachedMedia(uriString=android.net.Uri.fromFile(file).toString(),mimeType="image/png",slotType=if(model.imagesArrayField!=null) MediaSlotType.REFERENCE_IMAGE else MediaSlotType.PRIMARY_IMAGE)
        }+
            java.io.File(context.filesDir,"qa-video.mp4").takeIf {it.isFile && model.supportsVideoInput}?.let {listOf(AttachedMedia(uriString=android.net.Uri.fromFile(it).toString(),mimeType="video/mp4",slotType=MediaSlotType.VIDEO_INPUT))}.orEmpty()
    }
}
