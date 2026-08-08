package com.aman.gigi.ui.screensaver.components

import android.os.Build as AndroidBuild
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.skydoves.cloudy.Cloudy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

@Composable
fun GifPickerTray(
    onGifSelected: (String) -> Unit,
    onLocalGifSelected: (android.net.Uri) -> Unit,
    onDismiss: () -> Unit,
    recentGifs: List<String> = emptyList()
) {
    val isDark = false
    var searchQuery by remember { mutableStateOf("") }
    var gifResults by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val plan by com.aman.gigi.utils.AppConfig.planFlow.collectAsState()
    var showUpgradeSheet by remember { mutableStateOf(false) }

    val GIPHY_API_KEY = com.aman.gigi.utils.AppConfig.giphyApiKey

    val curatedGifs = listOf(
        "https://media0.giphy.com/media/v1.Y2lkPTc5MGI3NjExOHpqcXpqcXpqcXpqcXpqcXpqcXpqcXpqcXpqcXpqcXpqcXpqcXpqcXpqcXpqcXpqcXpqcXpqcXpqcXpqcXpqcXpqcXpqcSZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/L2z7uBc4O6UHC/giphy.gif",
        "https://media4.giphy.com/media/v1.Y2lkPTc5MGI3NjExOHpqcXpqcXpqcXpqcXpqcXpqcXpqcXpqcXpqcXpqcXpqcXpqcXpqcXpqcXpqcXpqcXpqcXpqcXpqcXpqcXpqcXpqcXpqcXpqcSZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/vfkV6kLdof5tK/giphy.gif",
        "https://media1.giphy.com/media/v1.Y2lkPTc5MGI3NjExOHpqcXpqcXpqcXpqcXpqcXpqcXpqcXpqcXpqcXpqcXpqcXpqcXpqcXpqcXpqcXpqcXpqcXpqcXpqcXpqcXpqcXpqcXpqcXpqcSZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/IHuF7vE0P9gNq/giphy.gif"
    )

    LaunchedEffect(searchQuery, recentGifs) {
        if (searchQuery.isBlank()) {
            // Show recent GIFs if available, otherwise fallback to curated
            gifResults = if (recentGifs.isNotEmpty()) recentGifs else curatedGifs
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        android.util.Log.d("GifPickerTray", "Picker returned Uri: $uri")
        if (uri != null) {
            android.widget.Toast.makeText(context, "GIF selected, processing...", android.widget.Toast.LENGTH_SHORT).show()
            onLocalGifSelected(uri)
            onDismiss()
        } else {
            android.util.Log.e("GifPickerTray", "Picker returned null Uri")
        }
    }

    fun searchGiphy(query: String) {
        if (query.isBlank()) {
            gifResults = if (recentGifs.isNotEmpty()) recentGifs else curatedGifs
            return
        }
        if (!plan.features.gifPicker) {
            showUpgradeSheet = true
            return
        }
        if (GIPHY_API_KEY.isNullOrBlank()) {
            gifResults = emptyList()
            return
        }

        scope.launch {
            isLoading = true
            android.util.Log.d("GifPickerTray", "Searching Giphy for: $query")
            try {
                // Add a small delay for debounce
                kotlinx.coroutines.delay(500)
                if (searchQuery != query) return@launch // Cancel if query changed

                val results = withContext(Dispatchers.IO) {
                    val client = OkHttpClient()
                    val url = "https://api.giphy.com/v1/gifs/search?api_key=${GIPHY_API_KEY}&q=${java.net.URLEncoder.encode(query, "UTF-8")}&limit=20&rating=g"

                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0")
                        .build()
                    val response = client.newCall(request).execute()
                    val body = response.body?.string() ?: ""

                    android.util.Log.d("GifPickerTray", "Giphy Response code: ${response.code}")

                    if (!response.isSuccessful) {
                        android.util.Log.e("GifPickerTray", "Giphy API error: ${response.code} - ${response.message}\nBody: $body")
                        return@withContext emptyList<String>()
                    }

                    val json = JSONObject(body)
                    val data = json.getJSONArray("data")
                    val urls = mutableListOf<String>()
                    for (i in 0 until data.length()) {
                        val gif = data.getJSONObject(i)
                        val images = gif.getJSONObject("images")
                        val imageObj = images.optJSONObject("fixed_height") ?: images.optJSONObject("downsized")
                        if (imageObj != null) {
                            urls.add(imageObj.getString("url"))
                        }
                    }
                    urls
                }
                gifResults = results
            } catch (e: Exception) {
                android.util.Log.e("GifPickerTray", "Error searching Giphy", e)
                gifResults = emptyList() // Show nothing or error state
            } finally {
                isLoading = false
            }
        }
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .clip(RoundedCornerShape(32.dp))
                .border(2.dp, if (isDark) Color.White.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.4f), RoundedCornerShape(32.dp))
        ) {
            Cloudy(radius = 35) {
                Box(Modifier.matchParentSize().background(if (isDark) Color.White.copy(alpha = 0.04f) else Color.White.copy(alpha = 0.2f)))
            }
            Surface(
                color = if (isDark) Color(0xFF1A1A2E).copy(alpha = 0.97f) else Color(0xFFE6E0FF).copy(alpha = 0.85f),
                shape = RoundedCornerShape(32.dp),
                tonalElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Change title based on whether showing search results or recents
                        val title = if (searchQuery.isNotBlank()) "Search Results"
                                    else if (recentGifs.isNotEmpty()) "Recent Moments"
                                    else "Magic Moments"
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF8B5CF6)
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = if (isDark) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.4f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            searchGiphy(it)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search Giphy...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF8B5CF6),
                            unfocusedBorderColor = if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.1f)
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Local Picker Button
                    Button(
                        onClick = { launcher.launch("image/gif") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.5f),
                            contentColor = Color(0xFF8B5CF6)
                        )
                    ) {
                        Text("Pick from Gallery", fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (isLoading) {
                        Box(Modifier.height(300.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color(0xFF8B5CF6))
                        }
                    } else if (searchQuery.isNotEmpty() && gifResults.isEmpty()) {
                        Box(Modifier.height(300.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    tint = if (isDark) Color(0xFFB0A8D0) else Color.Gray,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "No results found.",
                                    color = if (isDark) Color(0xFFB0A8D0) else Color.Gray
                                )
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.height(300.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(gifResults.size) { index ->
                                val url = gifResults[index]
                                Box(
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(if (isDark) Color.White.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.3f))
                                        .clickable { onGifSelected(url) }
                                        .border(1.dp, if (isDark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(url)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showUpgradeSheet) {
        com.aman.gigi.ui.components.UpgradeSheet(
            featureName = "GIF Picker",
            featureDescription = "Search and send GIFs to your partner.",
            onDismiss = { showUpgradeSheet = false }
        )
    }
}
