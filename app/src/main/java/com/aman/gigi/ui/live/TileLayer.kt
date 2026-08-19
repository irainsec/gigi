package com.aman.gigi.ui.live

import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** The rectangle of tiles covering a viewport at one zoom level. */
data class TileWindow(val zoom: Int, val minX: Int, val maxX: Int, val minY: Int, val maxY: Int)

private const val PARALLEL_TILES = 8

/**
 * Loads every tile in [window] concurrently and returns them as they arrive.
 *
 * The first version of this fetched tiles one at a time in a nested loop. A full-screen
 * map needs 20–40 tiles and each takes ~0.5–2 s, so the map took the better part of a
 * minute to fill in. Eight at a time turns that into a few seconds, and the disk cache
 * in [OsmTiles] makes every subsequent visit instant.
 */
@Composable
fun rememberTiles(window: TileWindow): SnapshotStateMap<String, ImageBitmap> {
    val context = LocalContext.current
    val tiles = remember { mutableStateMapOf<String, ImageBitmap>() }

    LaunchedEffect(Unit) { OsmTiles.init(context) }

    LaunchedEffect(window) {
        // A pinch changes the window on every frame. Without this pause each frame
        // would cancel and restart the whole fetch, so nothing ever finished and the
        // gesture felt like treacle.
        delay(140)
        val wanted = buildList {
            for (x in window.minX..window.maxX) for (y in window.minY..window.maxY) {
                val k = "${window.zoom}/$x/$y"
                if (!tiles.containsKey(k)) add(Triple(window.zoom, x, y))
            }
        }
        if (wanted.isEmpty()) return@LaunchedEffect

        // Centre-out, so the part the user is looking at fills in first.
        val cx = (window.minX + window.maxX) / 2.0
        val cy = (window.minY + window.maxY) / 2.0
        val ordered = wanted.sortedBy { (_, x, y) ->
            val dx = x - cx; val dy = y - cy; dx * dx + dy * dy
        }

        val gate = Semaphore(PARALLEL_TILES)
        coroutineScope {
            ordered.map { (z, x, y) ->
                async(Dispatchers.IO) {
                    gate.withPermit {
                        val bmp = runCatching { OsmTiles.tile(z, x, y) }.getOrNull()
                            ?: return@withPermit
                        val image = bmp.asImageBitmap()
                        // The decode stays on IO, but the map write has to come back to
                        // the main thread: SnapshotStateMap cannot take concurrent
                        // off-thread writes, and eight loaders racing on it crashes with
                        // "Reading a state that was created after the snapshot was taken".
                        withContext(Dispatchers.Main.immediate) {
                            tiles["$z/$x/$y"] = image
                        }
                    }
                }
            }.awaitAll()
        }
    }

    return tiles
}

/**
 * Draws the tile grid. Where a tile hasn't arrived yet it upscales the nearest cached
 * parent, so you get a blurry map immediately instead of empty background.
 */
fun DrawScope.drawTileGrid(
    window: TileWindow,
    tiles: Map<String, ImageBitmap>,
    worldX: Double,
    worldY: Double,
    scale: Float
) {
    val tilePx = 256f * scale
    val dstSide = tilePx.roundToInt() + 1

    for (x in window.minX..window.maxX) for (y in window.minY..window.maxY) {
        val left = ((x * 256.0 - worldX) * scale + size.width / 2f).toFloat()
        val top = ((y * 256.0 - worldY) * scale + size.height / 2f).toFloat()
        val dstOffset = IntOffset(left.roundToInt(), top.roundToInt())
        val dstSize = IntSize(dstSide, dstSide)

        val exact = tiles["${window.zoom}/$x/$y"]
        if (exact != null) {
            drawImage(image = exact, dstOffset = dstOffset, dstSize = dstSize)
            continue
        }
        // Blurry stand-in from a zoomed-out ancestor we already have.
        val anc = OsmTiles.peekAncestor(window.zoom, x, y) ?: continue
        drawImage(
            image = anc.bitmap.asImageBitmap(),
            srcOffset = IntOffset(anc.srcX.roundToInt(), anc.srcY.roundToInt()),
            srcSize = IntSize(anc.srcSize.roundToInt().coerceAtLeast(1), anc.srcSize.roundToInt().coerceAtLeast(1)),
            dstOffset = dstOffset,
            dstSize = dstSize
        )
    }
}
