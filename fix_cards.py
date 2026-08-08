
with open(r'c:\Users\ATPL-ADMIN\Downloads\gigi\app\src\main\java\com\aman\gigi\ui\LoveCardsSection.kt', 'r') as f:
    lines = f.readlines()

new_lines = []
skip = 0
for i, line in enumerate(lines):
    # lines are 0-indexed here, so line 1077 in view_file is index 1076
    if i == 1076: # Line 1077 index
        new_lines.append('            // Vertical theme pager inside each horizontal type page\n')
        new_lines.append('            var isThemeUnlocked by remember { mutableStateOf(false) }\n')
        new_lines.append('            var isCardLong by remember { mutableStateOf(false) }\n')
        new_lines.append('\n')
        new_lines.append('            VerticalPager(\n')
        new_lines.append('                state = localThemePagerState,\n')
        new_lines.append('                modifier = Modifier.fillMaxSize(),\n')
        new_lines.append('                contentPadding = PaddingValues(vertical = 42.dp),\n')
        new_lines.append('                pageSpacing = 0.dp,\n')
        new_lines.append('                beyondViewportPageCount = 1,\n')
        new_lines.append('                userScrollEnabled = isThemeUnlocked || !isCardLong\n')
        new_lines.append('            ) { themePageIndex ->\n')
    elif i == 1077 or i == 1078: # Skip the old empty line and the next line if they are duplicates
        pass
    # Fix the other broken parts
    elif 'var isThemeUnlocked by remember { mutableStateOf(false) }' in line and i > 1100:
        pass # Remove duplicate
    elif 'val isCardLong = scrollState.maxValue > 0' in line:
        new_lines.append('                // Track if this specific card page is long\n')
        new_lines.append('                LaunchedEffect(scrollState.maxValue) {\n')
        new_lines.append('                    isCardLong = scrollState.maxValue > 0\n')
        new_lines.append('                }\n')
    else:
        new_lines.append(line)

with open(r'c:\Users\ATPL-ADMIN\Downloads\gigi\app\src\main\java\com\aman\gigi\ui\LoveCardsSection.kt', 'w') as f:
    f.writelines(new_lines)
