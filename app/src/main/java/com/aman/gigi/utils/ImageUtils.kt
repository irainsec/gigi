package com.aman.gigi.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

object ImageUtils {
    /**
     * Parses a model (which might be a base64 data URL) into a format
     * that Coil/BitmapFactory can render (ByteArray).
     */
    fun parseEmojiModel(model: Any?): Any? {
        val str = model as? String ?: return model
        if (str.startsWith("data:image")) {
            val base64 = str.substringAfter("base64,")
            return try {
                Base64.decode(base64, Base64.DEFAULT)
            } catch (e: Exception) {
                model
            }
        }
        return model
    }
}

@Composable
fun SmartEmojiAvatar(
    emojiOrUrl: String?,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 24.sp,
    fallbackEmoji: String = "🌻",
    contentScale: ContentScale = ContentScale.Fit
) {
    val context = LocalContext.current
    val textVal = emojiOrUrl?.ifBlank { fallbackEmoji } ?: fallbackEmoji
    val parsed = ImageUtils.parseEmojiModel(textVal)

    if (parsed is ByteArray) {
        var animatedFrames by remember(parsed) { mutableStateOf<List<Bitmap>?>(null) }
        var isSpriteStrip by remember(parsed) { mutableStateOf(false) }

        LaunchedEffect(parsed) {
            withContext(Dispatchers.IO) {
                try {
                    val fullBitmap = BitmapFactory.decodeByteArray(parsed, 0, parsed.size)
                    if (fullBitmap != null && fullBitmap.width >= fullBitmap.height * 3 && fullBitmap.height > 0) {
                        val frameWidth = fullBitmap.height
                        val numFrames = (fullBitmap.width / frameWidth).coerceAtMost(4)
                        val frames = mutableListOf<Bitmap>()
                        for (i in 0 until numFrames) {
                            val frame = Bitmap.createBitmap(fullBitmap, i * frameWidth, 0, frameWidth, frameWidth)
                            frames.add(frame)
                        }
                        animatedFrames = frames
                        isSpriteStrip = true
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        if (isSpriteStrip && !animatedFrames.isNullOrEmpty()) {
            val frames = animatedFrames!!
            var frameIndex by remember(frames) { mutableStateOf(0) }
            LaunchedEffect(frames) {
                while (isActive) {
                    delay(150)
                    frameIndex = (frameIndex + 1) % frames.size
                }
            }
            val currentFrame = frames[frameIndex.coerceIn(0, frames.size - 1)]
            Image(
                bitmap = currentFrame.asImageBitmap(),
                contentDescription = "Twigi Avatar",
                contentScale = contentScale,
                modifier = modifier
            )
        } else {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(parsed)
                    .crossfade(true)
                    .build(),
                contentDescription = "Avatar",
                contentScale = contentScale,
                modifier = modifier
            )
        }
    } else if (textVal.startsWith("http://") || textVal.startsWith("https://")) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(textVal)
                .crossfade(true)
                .build(),
            contentDescription = "Avatar",
            contentScale = contentScale,
            modifier = modifier
        )
    } else {
        Text(
            text = textVal,
            fontSize = fontSize,
            modifier = modifier
        )
    }
}
