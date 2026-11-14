package com.vidadomu.nexus.ui.cut

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import com.vidadomu.nexus.cut.engine.PreviewController

/** TextureView participates in Compose zoom/pan; no export/canvas transform is changed. */
@UnstableApi
@Composable
internal fun CutPreviewSurface(controller: PreviewController, modifier: Modifier) {
    AndroidView(factory = { context ->
        TextureView(context).apply {
            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                private var output: Surface? = null
                override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                    output = Surface(texture).also { controller.attachSurface(it, width, height) }
                }
                override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
                    output?.let { controller.attachSurface(it, width, height) }
                }
                override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                    output?.let { controller.detachSurface(it); it.release() }; output = null
                    return true
                }
                override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
            }
        }
    }, modifier = modifier)
}
