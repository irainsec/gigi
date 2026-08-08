import sys

path = r'app\src\main\java\com\aman\gigi\ui\GalaxyView.kt'
with open(path, 'r', encoding='utf-8') as f:
    lines = f.readlines()

new_block = """    val online: Boolean,
    val bitmap: androidx.compose.ui.graphics.ImageBitmap?,
    val sizeMul: Float,
    val ring: Boolean,
    var orbit: Int,
    var angle: Float,
    var speed: Float,
    val moons: List<GMoon>
)

private fun loadAsset(context: android.content.Context, path: String): androidx.compose.ui.graphics.ImageBitmap? = runCatching {
    context.assets.open(path).use { android.graphics.BitmapFactory.decodeStream(it) }.let { androidx.compose.ui.graphics.asImageBitmap(it) }
}.getOrNull()

private fun isGroupConn(c: com.aman.gigi.model.Connection) =
    c.isGroup || c.relationshipType.equals("GROUP", ignoreCase = true)

private fun hashStr(s: String): Int {
    var h = 0
    for (c in s) h = h * 31 + c.code
    return kotlin.math.abs(h)
}

@androidx.compose.runtime.Composable
fun GalaxyView(
    identity: com.aman.gigi.model.MemberIdentity?,
    connections: List<com.aman.gigi.model.Connection>,
    groupSizes: Map<String, Int>,
    camera: GalaxyCamera,
    onOpenConnection: (String) -> Unit,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    groupMemberEmojis: Map<String, List<String>> = emptyMap()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = androidx.compose.runtime.remember { context.getSharedPreferences("galaxy_orbits", android.content.Context.MODE_PRIVATE) }

    val emojiLoader = androidx.compose.runtime.remember {
        coil.ImageLoader.Builder(context).components {
"""

if 'android.os.Build' in lines[159]:
    lines = lines[:100] + [new_block] + lines[159:]
    with open(path, 'w', encoding='utf-8') as f:
        f.writelines(lines)
    print('Successfully inserted the missing code!')
else:
    print('Line mismatch! Line 160 is:', repr(lines[159]))
