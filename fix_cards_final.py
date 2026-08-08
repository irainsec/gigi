
import os

filepath = r'c:\Users\ATPL-ADMIN\Downloads\gigi\app\src\main\java\com\aman\gigi\ui\LoveCardsSection.kt'
with open(filepath, 'r', encoding='utf-8') as f:
    lines = f.readlines()

new_lines = []
skip = False
for i, line in enumerate(lines):
    if 'private fun LoveCard2DCarousel(' in line:
        new_lines.append(line)
        # Add the fixed function body here
        fixed_body = """    item: LoveCardDraftItem,
    onTypeChange: (LoveCardType) -> Unit,
    onThemeChange: (String) -> Unit,
    onSelectSticker: (String?) -> Unit,
    onMoveSticker: (String, Float, Float) -> Unit,
    onShowStickerPalette: () -> Unit,
    selectedStickerId: String?,
    onMediaAttached: (String, String) -> Unit = { _, _ -> },
    onChoicesChange: (List<String>) -> Unit = {}
) {
    val allTypes = LoveCardType.values()
    val themes = storyThemeOptions()
    
    val virtualTypeCount = 10000 
    val virtualThemeCount = 10000
    
    val initialTypePage = (virtualTypeCount / 2) - ((virtualTypeCount / 2) % allTypes.size) + allTypes.indexOf(item.type).coerceAtLeast(0)
    val typePagerState = rememberPagerState(initialPage = initialTypePage) { virtualTypeCount }

    LaunchedEffect(typePagerState.currentPage) {
        val nextType = allTypes[typePagerState.currentPage % allTypes.size]
        if (nextType != item.type) {
            onTypeChange(nextType)
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        HorizontalPager(
            state = typePagerState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 42.dp),
            pageSpacing = 0.dp,
            beyondViewportPageCount = 1
        ) { typePageIndex ->
            val pageType = allTypes[typePageIndex % allTypes.size]
            
            val localInitialThemePage = remember { (virtualThemeCount / 2) - ((virtualThemeCount / 2) % themes.size) + themes.indexOfFirst { it.key == item.theme }.coerceAtLeast(0) }
            val localThemePagerState = rememberPagerState(initialPage = localInitialThemePage) { virtualThemeCount }
            
            LaunchedEffect(localThemePagerState.currentPage, typePagerState.currentPage == typePageIndex) {
                if (typePagerState.currentPage == typePageIndex) {
                    val nextTheme = themes[localThemePagerState.currentPage % themes.size].key
                    if (nextTheme != item.theme) {
                        onThemeChange(nextTheme)
                    }
                }
            }
            
            LaunchedEffect(item.theme) {
                val currentThemeKey = themes[localThemePagerState.currentPage % themes.size].key
                if (currentThemeKey != item.theme) {
                    val targetPage = (virtualThemeCount / 2) - ((virtualThemeCount / 2) % themes.size) + themes.indexOfFirst { it.key == item.theme }.coerceAtLeast(0)
                    localThemePagerState.scrollToPage(targetPage)
                }
            }
            
            var isThemeUnlocked by remember { mutableStateOf(false) }
            var isCardLong by remember { mutableStateOf(false) }

            VerticalPager(
                state = localThemePagerState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 42.dp),
                pageSpacing = 0.dp,
                beyondViewportPageCount = 1,
                userScrollEnabled = isThemeUnlocked || !isCardLong
            ) { themePageIndex ->
                val pageThemeKey = themes[themePageIndex % themes.size].key
                
                val displayItem = remember(item, pageType, pageThemeKey) {
                    item.copy(type = pageType, theme = pageThemeKey)
                }
                
                val typeOffset = (typePageIndex - typePagerState.currentPage) - typePagerState.currentPageOffsetFraction
                val themeOffset = (themePageIndex - localThemePagerState.currentPage) - localThemePagerState.currentPageOffsetFraction
                
                val absTypeOffset = kotlin.math.abs(typeOffset)
                val absThemeOffset = kotlin.math.abs(themeOffset)
                val activeOffset = if (absTypeOffset > absThemeOffset) typeOffset else themeOffset
                val absActiveOffset = kotlin.math.abs(activeOffset)

                val scale = 1f - (absActiveOffset * 0.15f)
                val alpha = 1f - (absActiveOffset * 0.3f)
                val rotation = activeOffset * 9f

                val scrollState = rememberScrollState()
                val haptic = remember { HapticFeedbackType.LongPress }
                val hapticManager = androidx.compose.ui.platform.LocalHapticFeedback.current
                
                LaunchedEffect(scrollState.maxValue) {
                    isCardLong = scrollState.maxValue > 0
                }
                
                LaunchedEffect(localThemePagerState.currentPage) {
                    isThemeUnlocked = false
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 40.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onLongPress = {
                                    isThemeUnlocked = true
                                    hapticManager.performHapticFeedback(haptic)
                                }
                            )
                        }
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LoveCardEditorCanvas(
                        item = displayItem,
                        modifier = Modifier
                            .fillMaxWidth(0.90f) 
                            .graphicsLayer {
                                scaleX = scale.coerceAtLeast(0.7f)
                                scaleY = scale.coerceAtLeast(0.7f)
                                this.alpha = alpha.coerceIn(0f, 1f)
                                rotationZ = rotation
                            },
                        selectedStickerId = selectedStickerId,
                        onSelectSticker = onSelectSticker,
                        onMoveSticker = onMoveSticker,
                        onShowStickerPalette = onShowStickerPalette,
                        onMediaAttached = onMediaAttached,
                        onPromptChange = { /* handled via state updates */ },
                        onChoicesChange = onChoicesChange
                    )
                    
                    if (isCardLong && !isThemeUnlocked) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Hold to change theme", 
                            color = theme.primaryText.copy(alpha = 0.4f),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        
        Column(
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("←", color = Color(0xFF8455FF).copy(alpha = 0.4f), fontWeight = FontWeight.Bold)
        }
        Column(
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("→", color = Color(0xFF8455FF).copy(alpha = 0.4f), fontWeight = FontWeight.Bold)
        }
        Row(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("↑", color = Color(0xFF8455FF).copy(alpha = 0.4f), fontWeight = FontWeight.Bold)
        }
        Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("↓", color = Color(0xFF8455FF).copy(alpha = 0.4f), fontWeight = FontWeight.Bold)
        }
    }
}
"""
        new_lines.append(fixed_body)
        skip = True
    elif skip and '@Composable' in line:
        new_lines.append(line)
        skip = False
    elif not skip:
        new_lines.append(line)

with open(filepath, 'w', encoding='utf-8') as f:
    f.writelines(new_lines)
"""
