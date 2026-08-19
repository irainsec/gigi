package com.aman.gigi.ui.nest

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aman.gigi.data.nest.FacingDirection
import com.aman.gigi.data.nest.FurnitureItem
import com.aman.gigi.data.nest.PetState
import com.aman.gigi.data.nest.TimeOfDay
import kotlin.math.cos
import kotlin.math.sin

object PixelArtRoomRenderer {

    // Palette constants
    private val WallBorder = Color(0xFF1E293B)
    private val WallTop = Color(0xFFE2E8F0)
    private val WallFace = Color(0xFFCBD5E1)
    private val WallShadow = Color(0x33000000)

    private val FloorTileBase = Color(0xFFE5E7EB)
    private val FloorTileAlt = Color(0xFFDCDFE4)
    private val FloorGridLine = Color(0xFFB8BCC6)

    private val WoodDark = Color(0xFF452B1E)
    private val WoodMedium = Color(0xFF78350F)
    private val WoodLight = Color(0xFFB45309)
    private val WoodDeskTop = Color(0xFFE2C9A5)

    fun drawRoomStructure(
        drawScope: DrawScope,
        w: Float,
        h: Float,
        wallpaper: String,
        flooring: String,
        timeOfDay: TimeOfDay
    ) {
        with(drawScope) {
            val wallH = h * 0.16f
            val wallThickness = 12.dp.toPx()
            val outerMargin = 10.dp.toPx()

            // 1. Room Floor Base
            val floorColor = when (flooring) {
                "dark_walnut" -> Color(0xFF332014)
                "pink_carpet" -> Color(0xFF4A182D)
                "tatami_mat" -> Color(0xFF3A4428)
                else -> Color(0xFFDDE1E8) // office_grid
            }
            drawRect(
                color = floorColor,
                topLeft = Offset(outerMargin, outerMargin + wallH),
                size = Size(w - outerMargin * 2, h - outerMargin * 2 - wallH)
            )

            // Floor Tiles Grid (Agent Town 2.5D grid lines)
            val tileSize = 28.dp.toPx()
            var tx = outerMargin
            while (tx < w - outerMargin) {
                var ty = outerMargin + wallH
                var alt = ((tx / tileSize).toInt() % 2 == 0)
                while (ty < h - outerMargin) {
                    if (flooring == "office_grid" || flooring == "warm_oak") {
                        val tileTint = if (alt) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.03f)
                        drawRect(
                            color = tileTint,
                            topLeft = Offset(tx, ty),
                            size = Size(tileSize, tileSize)
                        )
                        drawRect(
                            color = Color.Black.copy(alpha = 0.06f),
                            topLeft = Offset(tx, ty),
                            size = Size(tileSize, tileSize),
                            style = Stroke(width = 1f)
                        )
                    }
                    ty += tileSize
                    alt = !alt
                }
                tx += tileSize
            }

            // 2. Room Outer Perimeter Walls (Thick top-down walls)
            val wallBg = when (wallpaper) {
                "cozy_wood" -> Color(0xFF5A3926)
                "mint_sakura" -> Color(0xFF284840)
                "midnight_galaxy" -> Color(0xFF16122E)
                else -> Color(0xFFF1F5F9) // apartment_light
            }
            val wallFaceBg = when (wallpaper) {
                "cozy_wood" -> Color(0xFF3E2314)
                "mint_sakura" -> Color(0xFF1C352E)
                "midnight_galaxy" -> Color(0xFF0F0B1E)
                else -> Color(0xFFE2E8F0)
            }

            // Top North Wall
            drawRect(
                color = wallBg,
                topLeft = Offset(outerMargin, outerMargin),
                size = Size(w - outerMargin * 2, wallH)
            )
            // Top wall dark outline
            drawRect(
                color = WallBorder,
                topLeft = Offset(outerMargin, outerMargin),
                size = Size(w - outerMargin * 2, wallH),
                style = Stroke(width = 3.dp.toPx())
            )
            // Wall bottom baseboard
            drawRect(
                color = wallFaceBg,
                topLeft = Offset(outerMargin, outerMargin + wallH - 10.dp.toPx()),
                size = Size(w - outerMargin * 2, 10.dp.toPx())
            )
            drawLine(
                color = WallBorder,
                start = Offset(outerMargin, outerMargin + wallH),
                end = Offset(w - outerMargin, outerMargin + wallH),
                strokeWidth = 3.dp.toPx()
            )
            // Wall shadow on floor
            drawRect(
                color = WallShadow,
                topLeft = Offset(outerMargin, outerMargin + wallH),
                size = Size(w - outerMargin * 2, 14.dp.toPx())
            )

            // Left Wall
            drawRect(
                color = WallBorder,
                topLeft = Offset(outerMargin, outerMargin),
                size = Size(wallThickness, h - outerMargin * 2)
            )
            // Right Wall
            drawRect(
                color = WallBorder,
                topLeft = Offset(w - outerMargin - wallThickness, outerMargin),
                size = Size(wallThickness, h - outerMargin * 2)
            )
            // Bottom Wall
            drawRect(
                color = WallBorder,
                topLeft = Offset(outerMargin, h - outerMargin - wallThickness),
                size = Size(w - outerMargin * 2, wallThickness)
            )

            // 3. Middle Dividing Partition Wall (Creates Study vs Bedroom zones with Doorway)
            val midY = h * 0.48f
            val doorX = w * 0.44f
            val doorW = w * 0.16f

            // Left section of partition wall
            drawRect(
                color = wallBg,
                topLeft = Offset(outerMargin, midY - 14.dp.toPx()),
                size = Size(doorX - outerMargin, 14.dp.toPx())
            )
            drawRect(
                color = WallBorder,
                topLeft = Offset(outerMargin, midY - 14.dp.toPx()),
                size = Size(doorX - outerMargin, 14.dp.toPx()),
                style = Stroke(width = 2.5.dp.toPx())
            )
            // Left partition shadow
            drawRect(
                color = WallShadow,
                topLeft = Offset(outerMargin, midY),
                size = Size(doorX - outerMargin, 10.dp.toPx())
            )

            // Right section of partition wall
            drawRect(
                color = wallBg,
                topLeft = Offset(doorX + doorW, midY - 14.dp.toPx()),
                size = Size(w - outerMargin - (doorX + doorW), 14.dp.toPx())
            )
            drawRect(
                color = WallBorder,
                topLeft = Offset(doorX + doorW, midY - 14.dp.toPx()),
                size = Size(w - outerMargin - (doorX + doorW), 14.dp.toPx()),
                style = Stroke(width = 2.5.dp.toPx())
            )
            // Right partition shadow
            drawRect(
                color = WallShadow,
                topLeft = Offset(doorX + doorW, midY),
                size = Size(w - outerMargin - (doorX + doorW), 10.dp.toPx())
            )

            // Doorway Mat / Threshold
            drawRoundRect(
                color = Color(0xFFB45309).copy(alpha = 0.45f),
                topLeft = Offset(doorX + 4.dp.toPx(), midY - 8.dp.toPx()),
                size = Size(doorW - 8.dp.toPx(), 18.dp.toPx()),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )

            // 4. Wall Decorations (AC Unit, Window, Bulletin Board)
            // Wall Window (Top-Right Bedroom)
            val winW = 54.dp.toPx()
            val winH = 34.dp.toPx()
            val winX = w * 0.76f
            val winY = outerMargin + 10.dp.toPx()
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(timeOfDay.skyColorTop, timeOfDay.skyColorBottom), startY = winY, endY = winY + winH),
                topLeft = Offset(winX, winY),
                size = Size(winW, winH),
                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
            )
            drawRoundRect(
                color = WallBorder,
                topLeft = Offset(winX, winY),
                size = Size(winW, winH),
                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                style = Stroke(width = 2.5.dp.toPx())
            )
            // Window Panes
            drawLine(WallBorder, Offset(winX + winW / 2f, winY), Offset(winX + winW / 2f, winY + winH), strokeWidth = 2f)
            drawLine(WallBorder, Offset(winX, winY + winH / 2f), Offset(winX + winW, winY + winH / 2f), strokeWidth = 2f)

            // Wall AC Unit (Top-Center)
            val acW = 50.dp.toPx()
            val acH = 20.dp.toPx()
            val acX = w * 0.46f
            val acY = outerMargin + 12.dp.toPx()
            drawRoundRect(
                color = Color(0xFFF8FAFC),
                topLeft = Offset(acX, acY),
                size = Size(acW, acH),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )
            drawRoundRect(
                color = WallBorder,
                topLeft = Offset(acX, acY),
                size = Size(acW, acH),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                style = Stroke(width = 2f)
            )
            // AC Louver Slats
            for (i in 1..3) {
                val sy = acY + 5.dp.toPx() + i * 3.dp.toPx()
                drawLine(Color(0xFF94A3B8), Offset(acX + 6.dp.toPx(), sy), Offset(acX + acW - 14.dp.toPx(), sy), strokeWidth = 1.5f)
            }
            // Green Power LED
            drawCircle(Color(0xFF22C55E), radius = 2.dp.toPx(), center = Offset(acX + acW - 6.dp.toPx(), acY + acH / 2f))

            // Ambient Room Tint (Sunrise, Sunset, Night)
            if (timeOfDay.ambientTint != Color.Transparent) {
                drawRect(
                    color = timeOfDay.ambientTint,
                    topLeft = Offset.Zero,
                    size = Size(w, h)
                )
            }
        }
    }

    // ── Pixel Art Furniture Drawers ──

    fun drawFurniture(
        drawScope: DrawScope,
        item: FurnitureItem,
        baseX: Float,
        baseY: Float,
        isPlayingMusic: Boolean,
        vinylRotation: Float,
        notesCount: Int
    ) {
        with(drawScope) {
            when (item.type) {
                "desk_computer" -> drawDualMonitorDesk(baseX, baseY)
                "office_chair" -> drawOfficeChair(baseX, baseY)
                "bookshelf_large" -> drawBookshelf(baseX, baseY)
                "bulletin_board" -> drawBulletinBoard(baseX, baseY)
                "cozy_bed" -> drawCozyBed(baseX, baseY)
                "nightstand_lamp" -> drawNightstand(baseX, baseY)
                "sweetheart_sofa" -> drawSweetheartSofa(baseX, baseY)
                "coffee_table" -> drawCoffeeTable(baseX, baseY)
                "turntable_station" -> drawTurntable(baseX, baseY, isPlayingMusic, vinylRotation)
                "heart_rug" -> drawHeartRug(baseX, baseY)
                "mini_fridge" -> drawMiniFridge(baseX, baseY, notesCount)
                "potted_plant" -> drawPottedPlant(baseX, baseY)
                "wall_clock" -> drawWallClock(baseX, baseY)
                else -> drawGenericTable(baseX, baseY, item.name)
            }
        }
    }

    private fun DrawScope.drawDualMonitorDesk(x: Float, y: Float) {
        val w = 84.dp.toPx()
        val h = 44.dp.toPx()

        // Floor Shadow
        drawRoundRect(
            color = WallShadow,
            topLeft = Offset(x - w / 2f, y - 4.dp.toPx()),
            size = Size(w, h + 8.dp.toPx()),
            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
        )

        // Wooden Desk Surface
        drawRoundRect(
            color = WoodDeskTop,
            topLeft = Offset(x - w / 2f, y - h / 2f),
            size = Size(w, h),
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
        )
        drawRoundRect(
            color = WallBorder,
            topLeft = Offset(x - w / 2f, y - h / 2f),
            size = Size(w, h),
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
            style = Stroke(width = 2.dp.toPx())
        )

        // Dual Monitors Setup
        val monW = 28.dp.toPx()
        val monH = 18.dp.toPx()
        val monY = y - h / 2f + 4.dp.toPx()

        // Monitor 1 (Left - Blue Coding Screen)
        val m1X = x - w / 2f + 8.dp.toPx()
        drawRoundRect(Color(0xFF0F172A), Offset(m1X, monY), Size(monW, monH), CornerRadius(3.dp.toPx(), 3.dp.toPx()))
        drawRoundRect(Color(0xFF38BDF8), Offset(m1X + 2.dp.toPx(), monY + 2.dp.toPx()), Size(monW - 4.dp.toPx(), monH - 4.dp.toPx()), CornerRadius(2.dp.toPx(), 2.dp.toPx()))
        drawRoundRect(WallBorder, Offset(m1X, monY), Size(monW, monH), CornerRadius(3.dp.toPx(), 3.dp.toPx()), style = Stroke(1.5f))

        // Monitor 2 (Right - Chart / Galaxy Screen)
        val m2X = x + 4.dp.toPx()
        drawRoundRect(Color(0xFF0F172A), Offset(m2X, monY), Size(monW, monH), CornerRadius(3.dp.toPx(), 3.dp.toPx()))
        drawRoundRect(Color(0xFFF472B6), Offset(m2X + 2.dp.toPx(), monY + 2.dp.toPx()), Size(monW - 4.dp.toPx(), monH - 4.dp.toPx()), CornerRadius(2.dp.toPx(), 2.dp.toPx()))
        drawRoundRect(WallBorder, Offset(m2X, monY), Size(monW, monH), CornerRadius(3.dp.toPx(), 3.dp.toPx()), style = Stroke(1.5f))

        // Keyboard & Mousepad
        val kbW = 24.dp.toPx()
        val kbH = 9.dp.toPx()
        drawRoundRect(Color(0xFF334155), Offset(x - kbW / 2f, y + 4.dp.toPx()), Size(kbW, kbH), CornerRadius(2.dp.toPx(), 2.dp.toPx()))
        drawCircle(Color(0xFFE2E8F0), radius = 2.dp.toPx(), center = Offset(x + kbW / 2f + 6.dp.toPx(), y + 8.dp.toPx()))
    }

    private fun DrawScope.drawOfficeChair(x: Float, y: Float) {
        val size = 26.dp.toPx()
        // Shadow
        drawCircle(WallShadow, radius = size * 0.6f, center = Offset(x, y + 2.dp.toPx()))
        // Base / Wheels
        drawLine(WallBorder, Offset(x - size * 0.4f, y + size * 0.3f), Offset(x + size * 0.4f, y - size * 0.3f), strokeWidth = 2.5f)
        drawLine(WallBorder, Offset(x - size * 0.4f, y - size * 0.3f), Offset(x + size * 0.4f, y + size * 0.3f), strokeWidth = 2.5f)
        // Seat Cushion
        drawCircle(Color(0xFF3B82F6), radius = size * 0.45f, center = Offset(x, y))
        drawCircle(WallBorder, radius = size * 0.45f, center = Offset(x, y), style = Stroke(2f))
        // Backrest (Agent Town curved style)
        drawArc(
            color = Color(0xFF1D4ED8),
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(x - size * 0.45f, y - size * 0.55f),
            size = Size(size * 0.9f, size * 0.55f)
        )
        drawArc(
            color = WallBorder,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(x - size * 0.45f, y - size * 0.55f),
            size = Size(size * 0.9f, size * 0.55f),
            style = Stroke(2f)
        )
    }

    private fun DrawScope.drawBookshelf(x: Float, y: Float) {
        val w = 46.dp.toPx()
        val h = 58.dp.toPx()
        // Shadow
        drawRect(WallShadow, Offset(x - w / 2f, y - h / 2f + 4.dp.toPx()), Size(w + 4.dp.toPx(), h))
        // Wooden Frame
        drawRect(WoodMedium, Offset(x - w / 2f, y - h / 2f), Size(w, h))
        drawRect(WallBorder, Offset(x - w / 2f, y - h / 2f), Size(w, h), style = Stroke(2.5.dp.toPx()))

        // 3 Shelves with colorful books
        val bookColors = listOf(Color(0xFFEF4444), Color(0xFF3B82F6), Color(0xFF10B981), Color(0xFFF59E0B), Color(0xFF8B5CF6))
        for (row in 0..2) {
            val rowY = y - h / 2f + 6.dp.toPx() + row * 16.dp.toPx()
            drawLine(WallBorder, Offset(x - w / 2f, rowY + 14.dp.toPx()), Offset(x + w / 2f, rowY + 14.dp.toPx()), strokeWidth = 2f)
            // Books
            for (b in 0..4) {
                val bx = x - w / 2f + 5.dp.toPx() + b * 7.dp.toPx()
                val bh = 10.dp.toPx() + (b % 3) * 2.dp.toPx()
                val col = bookColors[(row * 2 + b) % bookColors.size]
                drawRect(col, Offset(bx, rowY + 14.dp.toPx() - bh), Size(6.dp.toPx(), bh))
                drawRect(WallBorder, Offset(bx, rowY + 14.dp.toPx() - bh), Size(6.dp.toPx(), bh), style = Stroke(1f))
            }
        }
    }

    private fun DrawScope.drawBulletinBoard(x: Float, y: Float) {
        val w = 52.dp.toPx()
        val h = 28.dp.toPx()
        // Cork background
        drawRoundRect(Color(0xFFD97706), Offset(x - w / 2f, y - h / 2f), Size(w, h), CornerRadius(3.dp.toPx(), 3.dp.toPx()))
        drawRoundRect(WoodDark, Offset(x - w / 2f, y - h / 2f), Size(w, h), CornerRadius(3.dp.toPx(), 3.dp.toPx()), style = Stroke(3.dp.toPx()))
        // Pinned sticky notes & photo
        drawRect(Color(0xFFFEF08A), Offset(x - w / 2f + 6.dp.toPx(), y - h / 2f + 5.dp.toPx()), Size(10.dp.toPx(), 10.dp.toPx()))
        drawCircle(Color(0xFFDC2626), radius = 1.5.dp.toPx(), center = Offset(x - w / 2f + 11.dp.toPx(), y - h / 2f + 6.dp.toPx()))

        drawRect(Color(0xFFFBCFE8), Offset(x + 4.dp.toPx(), y - h / 2f + 8.dp.toPx()), Size(12.dp.toPx(), 10.dp.toPx()))
        drawCircle(Color(0xFF2563EB), radius = 1.5.dp.toPx(), center = Offset(x + 10.dp.toPx(), y - h / 2f + 9.dp.toPx()))
    }

    private fun DrawScope.drawCozyBed(x: Float, y: Float) {
        val w = 78.dp.toPx()
        val h = 72.dp.toPx()
        // Shadow
        drawRoundRect(WallShadow, Offset(x - w / 2f, y - h / 2f + 4.dp.toPx()), Size(w + 6.dp.toPx(), h + 6.dp.toPx()), CornerRadius(8.dp.toPx(), 8.dp.toPx()))
        // Wooden Bed Frame
        drawRoundRect(WoodDark, Offset(x - w / 2f, y - h / 2f), Size(w, h), CornerRadius(6.dp.toPx(), 6.dp.toPx()))
        drawRoundRect(WallBorder, Offset(x - w / 2f, y - h / 2f), Size(w, h), CornerRadius(6.dp.toPx(), 6.dp.toPx()), style = Stroke(2.5.dp.toPx()))

        // Headboard
        drawRect(WoodLight, Offset(x - w / 2f + 3.dp.toPx(), y - h / 2f + 3.dp.toPx()), Size(w - 6.dp.toPx(), 14.dp.toPx()))

        // Pillows (Left & Right)
        val pW = 28.dp.toPx()
        val pH = 14.dp.toPx()
        drawRoundRect(Color(0xFFFDF4FF), Offset(x - w / 2f + 6.dp.toPx(), y - h / 2f + 10.dp.toPx()), Size(pW, pH), CornerRadius(4.dp.toPx(), 4.dp.toPx()))
        drawRoundRect(WallBorder, Offset(x - w / 2f + 6.dp.toPx(), y - h / 2f + 10.dp.toPx()), Size(pW, pH), CornerRadius(4.dp.toPx(), 4.dp.toPx()), style = Stroke(1.5f))

        drawRoundRect(Color(0xFFFDF4FF), Offset(x + w / 2f - 6.dp.toPx() - pW, y - h / 2f + 10.dp.toPx()), Size(pW, pH), CornerRadius(4.dp.toPx(), 4.dp.toPx()))
        drawRoundRect(WallBorder, Offset(x + w / 2f - 6.dp.toPx() - pW, y - h / 2f + 10.dp.toPx()), Size(pW, pH), CornerRadius(4.dp.toPx(), 4.dp.toPx()), style = Stroke(1.5f))

        // Purple / Pink Cozy Duvet Quilt
        val quiltH = 46.dp.toPx()
        drawRoundRect(Color(0xFFC084FC), Offset(x - w / 2f + 3.dp.toPx(), y + h / 2f - quiltH), Size(w - 6.dp.toPx(), quiltH), CornerRadius(4.dp.toPx(), 4.dp.toPx()))
        drawRoundRect(WallBorder, Offset(x - w / 2f + 3.dp.toPx(), y + h / 2f - quiltH), Size(w - 6.dp.toPx(), quiltH), CornerRadius(4.dp.toPx(), 4.dp.toPx()), style = Stroke(2f))

        // Duvet Fold Trim
        drawLine(Color(0xFFE879F9), Offset(x - w / 2f + 4.dp.toPx(), y + h / 2f - quiltH + 10.dp.toPx()), Offset(x + w / 2f - 4.dp.toPx(), y + h / 2f - quiltH + 10.dp.toPx()), strokeWidth = 3f)
    }

    private fun DrawScope.drawNightstand(x: Float, y: Float) {
        val size = 26.dp.toPx()
        // Wooden stand
        drawRect(WoodMedium, Offset(x - size / 2f, y - size / 2f), Size(size, size))
        drawRect(WallBorder, Offset(x - size / 2f, y - size / 2f), Size(size, size), style = Stroke(2f))
        // Drawer knob
        drawCircle(Color(0xFFFDE047), radius = 2.dp.toPx(), center = Offset(x, y))
        // Glowing Lampshade
        drawArc(Color(0xFFFEF08A), 180f, 180f, true, Offset(x - 8.dp.toPx(), y - size / 2f - 10.dp.toPx()), Size(16.dp.toPx(), 14.dp.toPx()))
        drawCircle(Color(0xFFF59E0B).copy(alpha = 0.3f), radius = 14.dp.toPx(), center = Offset(x, y - size / 2f - 4.dp.toPx()))
    }

    private fun DrawScope.drawSweetheartSofa(x: Float, y: Float) {
        val w = 82.dp.toPx()
        val h = 40.dp.toPx()
        // Shadow
        drawRoundRect(WallShadow, Offset(x - w / 2f, y - h / 2f + 4.dp.toPx()), Size(w + 4.dp.toPx(), h + 6.dp.toPx()), CornerRadius(8.dp.toPx(), 8.dp.toPx()))
        // Sofa Body
        drawRoundRect(Color(0xFFBE185D), Offset(x - w / 2f, y - h / 2f), Size(w, h), CornerRadius(8.dp.toPx(), 8.dp.toPx()))
        drawRoundRect(WallBorder, Offset(x - w / 2f, y - h / 2f), Size(w, h), CornerRadius(8.dp.toPx(), 8.dp.toPx()), style = Stroke(2.5.dp.toPx()))

        // 2 Cushions
        val cW = 34.dp.toPx()
        val cH = 24.dp.toPx()
        drawRoundRect(Color(0xFFDB2777), Offset(x - w / 2f + 5.dp.toPx(), y - 4.dp.toPx()), Size(cW, cH), CornerRadius(6.dp.toPx(), 6.dp.toPx()))
        drawRoundRect(Color(0xFFDB2777), Offset(x + w / 2f - 5.dp.toPx() - cW, y - 4.dp.toPx()), Size(cW, cH), CornerRadius(6.dp.toPx(), 6.dp.toPx()))

        // Armrests
        drawRoundRect(Color(0xFF9D174D), Offset(x - w / 2f, y - h / 2f + 6.dp.toPx()), Size(7.dp.toPx(), 28.dp.toPx()), CornerRadius(4.dp.toPx(), 4.dp.toPx()))
        drawRoundRect(Color(0xFF9D174D), Offset(x + w / 2f - 7.dp.toPx(), y - h / 2f + 6.dp.toPx()), Size(7.dp.toPx(), 28.dp.toPx()), CornerRadius(4.dp.toPx(), 4.dp.toPx()))
    }

    private fun DrawScope.drawCoffeeTable(x: Float, y: Float) {
        val w = 50.dp.toPx()
        val h = 24.dp.toPx()
        drawRoundRect(WoodDeskTop, Offset(x - w / 2f, y - h / 2f), Size(w, h), CornerRadius(4.dp.toPx(), 4.dp.toPx()))
        drawRoundRect(WallBorder, Offset(x - w / 2f, y - h / 2f), Size(w, h), CornerRadius(4.dp.toPx(), 4.dp.toPx()), style = Stroke(2f))

        // 2 Mugs
        drawCircle(Color(0xFF38BDF8), radius = 3.5.dp.toPx(), center = Offset(x - 10.dp.toPx(), y))
        drawCircle(Color(0xFFF472B6), radius = 3.5.dp.toPx(), center = Offset(x + 10.dp.toPx(), y))
    }

    private fun DrawScope.drawTurntable(x: Float, y: Float, isPlayingMusic: Boolean, vinylRotation: Float) {
        val size = 36.dp.toPx()
        // Wooden base
        drawRoundRect(Color(0xFF1E1035), Offset(x - size / 2f, y - size / 2f), Size(size, size), CornerRadius(6.dp.toPx(), 6.dp.toPx()))
        drawRoundRect(Color(0xFF8B5CF6), Offset(x - size / 2f, y - size / 2f), Size(size, size), CornerRadius(6.dp.toPx(), 6.dp.toPx()), style = Stroke(2f))

        // Vinyl Platter
        drawCircle(Color(0xFF0F172A), radius = 12.dp.toPx(), center = Offset(x - 2.dp.toPx(), y))
        drawCircle(Color(0xFFEC4899), radius = 4.dp.toPx(), center = Offset(x - 2.dp.toPx(), y))
        drawCircle(Color.White, radius = 1.dp.toPx(), center = Offset(x - 2.dp.toPx(), y))

        // Tonearm
        drawLine(Color(0xFFE2E8F0), Offset(x + size / 2f - 5.dp.toPx(), y - size / 2f + 5.dp.toPx()), Offset(x, y), strokeWidth = 2f)
    }

    private fun DrawScope.drawHeartRug(x: Float, y: Float) {
        val w = 96.dp.toPx()
        val h = 56.dp.toPx()
        drawRoundRect(
            color = Color(0xFFFBCFE8).copy(alpha = 0.55f),
            topLeft = Offset(x - w / 2f, y - h / 2f),
            size = Size(w, h),
            cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx())
        )
        drawRoundRect(
            color = Color(0xFFF472B6).copy(alpha = 0.8f),
            topLeft = Offset(x - w / 2f, y - h / 2f),
            size = Size(w, h),
            cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx()),
            style = Stroke(2.dp.toPx())
        )
    }

    private fun DrawScope.drawMiniFridge(x: Float, y: Float, notesCount: Int) {
        val w = 40.dp.toPx()
        val h = 54.dp.toPx()
        // Shadow
        drawRoundRect(WallShadow, Offset(x - w / 2f, y - h / 2f + 4.dp.toPx()), Size(w + 4.dp.toPx(), h + 6.dp.toPx()), CornerRadius(6.dp.toPx(), 6.dp.toPx()))
        // Pastel Aqua Body
        drawRoundRect(Color(0xFF38BDF8), Offset(x - w / 2f, y - h / 2f), Size(w, h), CornerRadius(6.dp.toPx(), 6.dp.toPx()))
        drawRoundRect(WallBorder, Offset(x - w / 2f, y - h / 2f), Size(w, h), CornerRadius(6.dp.toPx(), 6.dp.toPx()), style = Stroke(2.5.dp.toPx()))

        // Door Split Line
        drawLine(WallBorder, Offset(x - w / 2f, y - h / 2f + 16.dp.toPx()), Offset(x + w / 2f, y - h / 2f + 16.dp.toPx()), strokeWidth = 2f)

        // Chrome Handle
        drawRoundRect(Color(0xFFE2E8F0), Offset(x - w / 2f + 5.dp.toPx(), y - h / 2f + 22.dp.toPx()), Size(3.dp.toPx(), 14.dp.toPx()), CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx()))

        // Magnet Sticky Notes
        drawRect(Color(0xFFFEF08A), Offset(x + 2.dp.toPx(), y - h / 2f + 22.dp.toPx()), Size(8.dp.toPx(), 8.dp.toPx()))
        drawRect(Color(0xFFFBCFE8), Offset(x + 12.dp.toPx(), y - h / 2f + 28.dp.toPx()), Size(6.dp.toPx(), 7.dp.toPx()))

        if (notesCount > 0) {
            drawCircle(Color(0xFFEF4444), radius = 6.dp.toPx(), center = Offset(x + w / 2f, y - h / 2f))
        }
    }

    private fun DrawScope.drawPottedPlant(x: Float, y: Float) {
        // Terracotta Pot
        val potW = 24.dp.toPx()
        val potH = 18.dp.toPx()
        drawRoundRect(WallShadow, Offset(x - potW / 2f, y + 4.dp.toPx()), Size(potW, 8.dp.toPx()), CornerRadius(4.dp.toPx(), 4.dp.toPx()))
        drawRoundRect(Color(0xFFEA580C), Offset(x - potW / 2f, y), Size(potW, potH), CornerRadius(4.dp.toPx(), 4.dp.toPx()))
        drawRoundRect(WallBorder, Offset(x - potW / 2f, y), Size(potW, potH), CornerRadius(4.dp.toPx(), 4.dp.toPx()), style = Stroke(2f))

        // Lush Green Monstera Leaves
        val leafColors = listOf(Color(0xFF15803D), Color(0xFF22C55E), Color(0xFF16A34A))
        drawCircle(leafColors[0], radius = 12.dp.toPx(), center = Offset(x - 6.dp.toPx(), y - 10.dp.toPx()))
        drawCircle(leafColors[1], radius = 14.dp.toPx(), center = Offset(x + 6.dp.toPx(), y - 12.dp.toPx()))
        drawCircle(leafColors[2], radius = 10.dp.toPx(), center = Offset(x, y - 18.dp.toPx()))
    }

    private fun DrawScope.drawWallClock(x: Float, y: Float) {
        val r = 12.dp.toPx()
        drawCircle(Color(0xFFF8FAFC), radius = r, center = Offset(x, y))
        drawCircle(WallBorder, radius = r, center = Offset(x, y), style = Stroke(2f))
        // Hands
        drawLine(Color(0xFF0F172A), Offset(x, y), Offset(x, y - 6.dp.toPx()), strokeWidth = 1.5f)
        drawLine(Color(0xFFDC2626), Offset(x, y), Offset(x + 5.dp.toPx(), y), strokeWidth = 1.5f)
    }

    private fun DrawScope.drawGenericTable(x: Float, y: Float, name: String) {
        val size = 36.dp.toPx()
        drawRoundRect(WoodMedium, Offset(x - size / 2f, y - size / 2f), Size(size, size), CornerRadius(6.dp.toPx(), 6.dp.toPx()))
        drawRoundRect(WallBorder, Offset(x - size / 2f, y - size / 2f), Size(size, size), CornerRadius(6.dp.toPx(), 6.dp.toPx()), style = Stroke(2f))
    }

    // ── Pixel Character Floor Shadow & Base Rendering ──

    fun drawCharacterShadow(drawScope: DrawScope, x: Float, y: Float) {
        with(drawScope) {
            val shadowW = 28.dp.toPx()
            val shadowH = 10.dp.toPx()
            drawOval(
                color = Color.Black.copy(alpha = 0.28f),
                topLeft = Offset(x - shadowW / 2f, y - shadowH / 2f + 16.dp.toPx()),
                size = Size(shadowW, shadowH)
            )
        }
    }
}
