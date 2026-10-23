package com.vidadomu.nexus.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.vidadomu.nexus.R
import com.vidadomu.nexus.settings.RuntimePreferences

/** Static supplied artwork or personal photo, without animation loops or sensor polling. */
@Composable fun NexusBackdrop(content: @Composable ()->Unit) {
    val config by RuntimePreferences.get(LocalContext.current).state.collectAsState()
    Box(Modifier.fillMaxSize()) {
        if(config.value("ui.background").toBoolean()) {
            val alpha=config.value("ui.backgroundOpacity").toFloat()
            val photo=config.value("ui.photo")
            if(photo.isNotBlank()) coil.compose.AsyncImage(model=photo,contentDescription=null,
                modifier=Modifier.matchParentSize().blur(config.value("ui.photoBlur").toFloat().dp).drawWithContent {drawContent();drawRect(Color(0xFF020916).copy(alpha=1-alpha))},contentScale=ContentScale.Crop)
            else Image(painterResource(R.drawable.nexus_blue),null,Modifier.matchParentSize(),alpha=alpha,contentScale=ContentScale.Crop)
        }
        content()
    }
}
