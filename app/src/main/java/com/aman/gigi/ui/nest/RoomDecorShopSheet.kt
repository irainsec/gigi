package com.aman.gigi.ui.nest

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aman.gigi.data.nest.FlooringOption
import com.aman.gigi.data.nest.WallpaperOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomDecorShopSheet(
    currentWallpaper: String,
    currentFlooring: String,
    onDismiss: () -> Unit,
    onSelectWallpaper: (String) -> Unit,
    onSelectFlooring: (String) -> Unit
) {
    val wallpapers = listOf(
        WallpaperOption("apartment_light", "Cozy Studio", Color(0xFFE2E8F0), "🏢"),
        WallpaperOption("lavender_stars", "Lavender Stars", Color(0xFF34225E), "✨"),
        WallpaperOption("cozy_wood", "Wood Cabin", Color(0xFF452B1E), "🪵"),
        WallpaperOption("mint_sakura", "Mint Sakura", Color(0xFF1E3A34), "🌸"),
        WallpaperOption("midnight_galaxy", "Midnight Galaxy", Color(0xFF0F0B1E), "🌌")
    )

    val floorings = listOf(
        FlooringOption("office_grid", "RPG Grid Tiles", Color(0xFFCBD5E1), Color(0xFF94A3B8)),
        FlooringOption("warm_oak", "Warm Oak", Color(0xFF5C3A21), Color(0xFF3E2312)),
        FlooringOption("dark_walnut", "Dark Walnut", Color(0xFF26170E), Color(0xFF1A0F09)),
        FlooringOption("pink_carpet", "Pink Carpet", Color(0xFF9D174D), Color(0xFF831843)),
        FlooringOption("tatami_mat", "Tatami Mat", Color(0xFF4D5B34), Color(0xFF3B4628))
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF18112E),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("🎨", fontSize = 24.sp)
                Text(
                    text = "Room Decor Studio",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Text(
                text = "Customize your shared room wallpaper and flooring",
                color = Color(0xFFC4B5FD),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 1. Wallpaper Selection
            Text(
                text = "Wallpapers 🖼️",
                color = Color(0xFFFDE68A),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(wallpapers) { wp ->
                    val isSelected = currentWallpaper == wp.id
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = wp.primaryColor,
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (isSelected) 2.5.dp else 1.dp,
                            color = if (isSelected) Color(0xFFFDE047) else Color.White.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier
                            .size(width = 115.dp, height = 95.dp)
                            .clickable { onSelectWallpaper(wp.id) }
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(wp.patternEmoji, fontSize = 28.sp)
                            Text(
                                text = wp.name,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 2. Flooring Selection
            Text(
                text = "Floorings 🪵",
                color = Color(0xFFFDE68A),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(floorings) { fl ->
                    val isSelected = currentFlooring == fl.id
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = fl.primaryColor,
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (isSelected) 2.5.dp else 1.dp,
                            color = if (isSelected) Color(0xFFFDE047) else Color.White.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier
                            .size(width = 115.dp, height = 95.dp)
                            .clickable { onSelectFlooring(fl.id) }
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("🏠", fontSize = 28.sp)
                            Text(
                                text = fl.name,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("Done Decorating ✨", fontWeight = FontWeight.Bold)
            }
        }
    }
}
