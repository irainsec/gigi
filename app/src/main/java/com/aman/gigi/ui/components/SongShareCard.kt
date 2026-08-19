package com.aman.gigi.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.FileProvider
import com.aman.gigi.model.LocalSong
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Renders a song as a shareable card and hands it to the system share sheet.
 *
 * Drawn straight onto an Android Canvas rather than by capturing a composable: the
 * card has to exist off-screen at a fixed export size, and the usual trick of
 * screenshotting a composed layout drags in a window, a measure pass and a pile of
 * timing bugs for a picture that's fundamentally a few rectangles and two strings.
 */
object SongShareCard {

    private const val W = 1080
    private const val H = 1350          // 4:5, the friendliest aspect for feeds/stories

    suspend fun share(
        context: Context,
        song: LocalSong,
        artwork: Bitmap?,
        accent: Int = 0xFFB9A6FF.toInt()
    ) = withContext(Dispatchers.IO) {
        val bitmap = render(song, artwork, accent)
        val uri = writeToCache(context, bitmap)
        withContext(Dispatchers.Main) {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "${song.title} — ${song.artist}")
                // Without this the receiving app cannot read our FileProvider uri.
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                Intent.createChooser(send, "Share this song")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** The same image, saved but not shared — used by "send to a connection". */
    suspend fun renderToUri(
        context: Context,
        song: LocalSong,
        artwork: Bitmap?,
        accent: Int = 0xFFB9A6FF.toInt()
    ): Uri = withContext(Dispatchers.IO) {
        writeToCache(context, render(song, artwork, accent))
    }

    private fun writeToCache(context: Context, bitmap: Bitmap): Uri {
        val dir = File(context.cacheDir, "shares").apply { mkdirs() }
        val file = File(dir, "gigi-song.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // A raw file:// uri throws FileUriExposedException on modern Android.
        return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    }

    private fun render(song: LocalSong, artwork: Bitmap?, accent: Int): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = AndroidCanvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // backdrop: the artwork blown up and darkened, so the card takes its colour
        // from the song rather than from a fixed palette
        paint.color = 0xFF15121F.toInt()
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), paint)
        if (artwork != null) {
            val src = android.graphics.Rect(0, 0, artwork.width, artwork.height)
            val dst = android.graphics.Rect(-160, -160, W + 160, H + 160)
            paint.alpha = 90
            c.drawBitmap(artwork, src, dst, paint)
            paint.alpha = 255
        }
        paint.color = 0xCC0E0B18.toInt()
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), paint)

        // the record itself
        val cx = W / 2f
        val cy = H * 0.40f
        val r = W * 0.31f

        paint.color = 0xFF0B0910.toInt()
        c.drawCircle(cx, cy, r, paint)
        paint.color = 0x22FFFFFF
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        // grooves
        var gr = r * 0.42f
        while (gr < r * 0.97f) {
            c.drawCircle(cx, cy, gr, paint)
            gr += r * 0.055f
        }
        paint.style = Paint.Style.FILL

        if (artwork != null) {
            val label = r * 0.42f
            val rounded = Bitmap.createBitmap(
                (label * 2).toInt(), (label * 2).toInt(), Bitmap.Config.ARGB_8888
            )
            val lc = AndroidCanvas(rounded)
            val lp = Paint(Paint.ANTI_ALIAS_FLAG)
            lc.drawCircle(label, label, label, lp)
            lp.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
            lc.drawBitmap(
                artwork,
                android.graphics.Rect(0, 0, artwork.width, artwork.height),
                RectF(0f, 0f, label * 2, label * 2),
                lp
            )
            c.drawBitmap(rounded, cx - label, cy - label, null)
        }
        // spindle
        paint.color = 0xFF15121F.toInt()
        c.drawCircle(cx, cy, r * 0.045f, paint)

        // title + artist
        paint.color = 0xFFFFFFFF.toInt()
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 66f
        drawEllipsized(c, paint, song.title, cx, H * 0.735f, W * 0.84f)

        paint.color = 0xB3FFFFFF.toInt()
        paint.typeface = Typeface.DEFAULT
        paint.textSize = 44f
        drawEllipsized(c, paint, song.artist, cx, H * 0.735f + 68f, W * 0.84f)

        // a small mark, deliberately quiet
        paint.color = accent
        paint.textSize = 30f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        c.drawText("on Gigi", cx, H * 0.90f, paint)

        return bmp
    }

    /** Truncates with an ellipsis so a long title never runs off the card. */
    private fun drawEllipsized(
        c: AndroidCanvas, paint: Paint, text: String, x: Float, y: Float, maxWidth: Float
    ) {
        var shown = text
        if (paint.measureText(shown) > maxWidth) {
            while (shown.isNotEmpty() && paint.measureText("$shown…") > maxWidth) {
                shown = shown.dropLast(1)
            }
            shown = "$shown…"
        }
        c.drawText(shown, x, y, paint)
    }
}
