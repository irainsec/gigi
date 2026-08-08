import os
import re

music_kt_path = "c:/Users/ATPL-ADMIN/Downloads/gigi/app/src/main/java/com/aman/gigi/ui/Music.kt"
with open(music_kt_path, 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Add VinylRecordShelf and VinylShelfItem
vinyl_components = """
@androidx.compose.foundation.ExperimentalFoundationApi
@Composable
private fun VinylRecordShelf(
    albums: List<MusicAlbum>,
    activeAlbumId: Long?,
    dynamicPalettes: Map<Long, MusicPalette>,
    onClick: (MusicAlbum) -> Unit
) {
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { albums.size })
    
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Shelf Background
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .align(Alignment.BottomCenter)
                .padding(bottom = 140.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.4f),
                            Color.Black.copy(alpha = 0.0f)
                        )
                    )
                )
        )
        
        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().height(460.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 80.dp),
            pageSpacing = (-80).dp
        ) { page ->
            val album = albums[page]
            val palette = albumPaletteFor(album, dynamicPalettes)
            
            // Calculate 3D transformation based on page offset
            val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
            val absOffset = kotlin.math.abs(pageOffset)
            
            val scale = 1f - (absOffset * 0.15f).coerceAtMost(0.4f)
            val alpha = 1f - (absOffset * 0.3f).coerceAtMost(0.6f)
            val rotationY = pageOffset * -25f
            val zIndex = 100f - absOffset * 10f
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        this.scaleX = scale
                        this.scaleY = scale
                        this.alpha = alpha
                        this.rotationY = rotationY
                        this.cameraDistance = 12f * density
                    }
                    .zIndex(zIndex),
                contentAlignment = Alignment.Center
            ) {
                VinylShelfItem(
                    album = album,
                    palette = palette,
                    isActive = album.albumId == activeAlbumId,
                    onClick = { onClick(album) }
                )
            }
        }
        
        // Active Indicator underneath
        val activeAlbum = albums.getOrNull(pagerState.currentPage)
        if (activeAlbum != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 180.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = activeAlbum.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (activeAlbum.artist.isNotBlank()) {
                    Text(
                        text = activeAlbum.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun VinylShelfItem(
    album: MusicAlbum,
    palette: MusicPalette,
    isActive: Boolean,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isPressed) 0.95f else 1f)
    val pullOutOffset by animateFloatAsState(if (isPressed) (-40).toFloat() else 0f)
    
    Box(
        modifier = Modifier
            .size(240.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = pullOutOffset
            }
            .shadow(
                elevation = if (isActive) 24.dp else 12.dp,
                shape = RoundedCornerShape(4.dp),
                ambientColor = palette.accent,
                spotColor = palette.accent
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = { onClick() }
                )
            }
    ) {
        // Vinyl Record sticking out slightly
        Surface(
            modifier = Modifier
                .size(230.dp)
                .align(Alignment.CenterEnd)
                .offset(x = 20.dp),
            shape = androidx.compose.foundation.shape.CircleShape,
            color = Color(0xFF111111),
            border = BorderStroke(1.dp, Color(0xFF222222))
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Grooves
                for (i in 1..5) {
                    Box(
                        modifier = Modifier
                            .size((230 - i * 30).dp)
                            .border(0.5.dp, Color.White.copy(alpha = 0.05f), androidx.compose.foundation.shape.CircleShape)
                    )
                }
                // Center Label
                val firstArt = album.songs.firstOrNull()?.artBase64
                if (firstArt != null) {
                    com.aman.gigi.ui.components.B64Image(
                        b64 = firstArt,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                    )
                } else {
                    Surface(
                        modifier = Modifier.size(80.dp),
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = palette.accent
                    ) {}
                }
                // Spindle hole
                Surface(
                    modifier = Modifier.size(12.dp),
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = Color.Black
                ) {}
            }
        }
        
        // Album Sleeve
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(4.dp),
            color = palette.background
        ) {
            val coverArt = album.songs.firstOrNull()?.artBase64
            if (coverArt != null) {
                com.aman.gigi.ui.components.B64Image(
                    b64 = coverArt,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Album,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = palette.accent.copy(alpha = 0.5f)
                    )
                }
            }
            
            // Sleeve shine effect
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.2f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.1f)
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(1000f, 1000f)
                        )
                    )
            )
            
            // Spine text
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(16.dp)
                    .align(Alignment.CenterStart)
                    .background(Color.Black.copy(alpha = 0.4f))
            ) {
                Text(
                    text = album.title,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 8.sp,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .graphicsLayer { rotationZ = -90f }
                )
            }
        }
        
        if (isActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(2.dp, palette.accent, RoundedCornerShape(4.dp))
            )
        }
    }
}
"""

if "fun VinylRecordShelf" not in content:
    target_insert = "private fun AlbumBrowserOverlay("
    content = content.replace(target_insert, vinyl_components + "\n" + target_insert)

# 2. Modify AlbumBrowserOverlay LazyColumn to use VinylRecordShelf when not searching

target_list = """                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            top = 254.dp,
                            start = 24.dp,
                            end = 24.dp,
                            bottom = 182.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(if (searchQuery.isNotEmpty()) 8.dp else -70.dp)
                    ) {
                        if (searchQuery.isNotEmpty()) {
                            if (uiState.isSearching) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 40.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            color = palette.accent
                                        )
                                    }
                                }
                            } else if (uiState.searchResults.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 40.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "No results found on YouTube Music",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = palette.textMuted
                                        )
                                    }
                                }
                            } else {
                                itemsIndexed(
                                    items = uiState.searchResults,
                                    key = { _, result -> result.videoId }
                                ) { _, result ->
                                    YTSearchResultRow(
                                        result = result,
                                        isDownloading = uiState.downloadingVideoIds.contains(result.videoId),
                                        onDownload = { onDownloadSong(result) },
                                        palette = palette
                                    )
                                }
                            }
                        } else {
                            if (albums.isEmpty()) {
                                item(key = "empty-albums") {
                                    EmptyAlbumBrowserState(onCreateAlbum = onCreateAlbum, palette = palette)
                                }
                            }

                            itemsIndexed(
                                items = albums,
                                key = { _, album -> album.albumId }
                            ) { index, album ->
                                val approximateCenter = listState.firstVisibleItemIndex.toFloat() +
                                    (listState.firstVisibleItemScrollOffset / itemHeightPx)
                                val relative = index.toFloat() - approximateCenter
                                val focus = (1f - (relative.absoluteValue * 0.24f)).coerceIn(0.52f, 1f)
                                AlbumBrowserCard(
                                    album = album,
                                    palette = albumPaletteFor(album, dynamicPalettes),
                                    isActive = album.albumId == activeAlbumId,
                                    focus = focus,
                                    relativeOffset = relative.coerceIn(-2f, 2f),
                                    onClick = { activeDetailAlbum = album }
                                )
                            }
                        }
                    }"""

replacement_list = """                    if (searchQuery.isNotEmpty()) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                top = 254.dp,
                                start = 24.dp,
                                end = 24.dp,
                                bottom = 182.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (uiState.isSearching) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 40.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            color = palette.accent
                                        )
                                    }
                                }
                            } else if (uiState.searchResults.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 40.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "No results found on YouTube Music",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = palette.textMuted
                                        )
                                    }
                                }
                            } else {
                                itemsIndexed(
                                    items = uiState.searchResults,
                                    key = { _, result -> result.videoId }
                                ) { _, result ->
                                    YTSearchResultRow(
                                        result = result,
                                        isDownloading = uiState.downloadingVideoIds.contains(result.videoId),
                                        onDownload = { onDownloadSong(result) },
                                        palette = palette
                                    )
                                }
                            }
                        }
                    } else {
                        if (albums.isEmpty()) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    top = 254.dp,
                                    start = 24.dp,
                                    end = 24.dp,
                                    bottom = 182.dp
                                )
                            ) {
                                item(key = "empty-albums") {
                                    EmptyAlbumBrowserState(onCreateAlbum = onCreateAlbum, palette = palette)
                                }
                            }
                        } else {
                            @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
                            VinylRecordShelf(
                                albums = albums,
                                activeAlbumId = activeAlbumId,
                                dynamicPalettes = dynamicPalettes,
                                onClick = { activeDetailAlbum = it }
                            )
                        }
                    }"""

if "itemsIndexed(\n                                items = albums," in content:
    content = content.replace(target_list, replacement_list)

with open(music_kt_path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Updated Music.kt with VinylRecordShelf!")
