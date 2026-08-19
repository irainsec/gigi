package com.aman.gigi.ui

import android.app.Activity
import android.app.DatePickerDialog
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.pow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aman.gigi.R
import com.aman.gigi.model.MemberIdentity
import com.aman.gigi.model.ServerMode
import com.aman.gigi.model.ServerStatus
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.skydoves.cloudy.Cloudy
import kotlinx.coroutines.launch
import java.util.Calendar

@Composable
fun OnboardingGateScreen(
    serverStatus: ServerStatus,
    memberIdentity: MemberIdentity?,
    isBusy: Boolean,
    authError: String?,
    onGoogleSignIn: (googleIdToken: String) -> Unit,
    onCompleteProfile: (displayName: String, gender: String, avatarUri: Uri?, dateOfBirth: String?) -> Unit,
    onClearError: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    val isSignedIn = memberIdentity != null &&
        (!memberIdentity.phoneNumber.isNullOrBlank() || !memberIdentity.googleEmail.isNullOrBlank())
    val needsProfile = isSignedIn && !memberIdentity!!.profileComplete

    var displayName by rememberSaveable(memberIdentity?.displayName) {
        mutableStateOf(memberIdentity?.displayName.orEmpty())
    }
    var greetingStyle by rememberSaveable(memberIdentity?.gender) {
        mutableStateOf(normalizeGreetingStyle(memberIdentity?.gender))
    }
    var dateOfBirth by rememberSaveable(memberIdentity?.dateOfBirth) {
        mutableStateOf(memberIdentity?.dateOfBirth.orEmpty())
    }

    val onboardCtx = LocalContext.current
    var chosenEmoji by remember {
        mutableStateOf(
            onboardCtx.getSharedPreferences("galaxy_orbits", Context.MODE_PRIVATE)
                .getString("emoji_self", "file:///android_asset/galaxy/emoji/sun_with_face.png") ?: ""
        )
    }
    var showAvatarChooser by remember { mutableStateOf(false) }
    val onboardEmojiLoader = remember {
        coil.ImageLoader.Builder(onboardCtx).components {
            if (android.os.Build.VERSION.SDK_INT >= 28) add(coil.decode.ImageDecoderDecoder.Factory())
            else add(coil.decode.GifDecoder.Factory())
        }.build()
    }

    if (showAvatarChooser) {
        com.aman.gigi.ui.components.AvatarEmojiPickerDialog(
            onDismiss = { showAvatarChooser = false },
            onPickEmoji = { url ->
                chosenEmoji = url
                onboardCtx.getSharedPreferences("galaxy_orbits", Context.MODE_PRIVATE)
                    .edit().putString("emoji_self", url).apply()
            }
        )
    }

    var localSignInError by remember { mutableStateOf<String?>(null) }

    // Google Sign-In launcher
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val intentData = result.data
        if (intentData != null) {
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(intentData)
                val account = task.getResult(ApiException::class.java)
                val idToken = account?.idToken
                if (!idToken.isNullOrBlank()) {
                    localSignInError = null
                    onGoogleSignIn(idToken)
                } else {
                    localSignInError = "Sign-in succeeded but no ID token was returned."
                }
            } catch (e: ApiException) {
                localSignInError = when (e.statusCode) {
                    10 -> "Configuration error (code 10): SHA fingerprint missing."
                    12501 -> null
                    else -> "Sign-In error (${e.statusCode}): ${e.message}"
                }
            }
        }
    }

    val googleSignInClient = remember(activity) {
        activity?.let {
            val webClientId = try { it.getString(R.string.default_web_client_id) } catch (e: Exception) { "" }
            val builder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestProfile()
            if (!webClientId.isNullOrBlank()) {
                builder.requestIdToken(webClientId)
            }
            GoogleSignIn.getClient(it, builder.build())
        }
    }

    var introSeen by rememberSaveable { mutableStateOf(false) }
    if (!isSignedIn && !introSeen) {
        GigiIntroCards(onDone = { introSeen = true })
        return
    }

    // Cute background with floating particles
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF2B1B4D), Color(0xFF1E153A), Color(0xFF130E26))
                )
            )
    ) {
        CuteFloatingSparkles()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header Indicator Pill
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = Color.White.copy(alpha = 0.1f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("🌸", fontSize = 16.sp)
                    Text(
                        text = if (!isSignedIn) "Step 1 of 2 · Welcome" else "Step 2 of 2 · Make it yours",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFDDD6FE)
                    )
                }
            }

            if (serverStatus.mode != ServerMode.ONLINE) {
                GlassCard {
                    Text(
                        text = if (serverStatus.mode == ServerMode.MAINTENANCE) "Server in maintenance 🛠️" else "Server offline 📡",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD166)
                    )
                    Text(
                        text = serverStatus.message ?: "Partner sync is paused temporarily. Alarms still work.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFC4B5FD)
                    )
                }
            }

            AnimatedContent(
                targetState = isSignedIn,
                transitionSpec = {
                    slideInHorizontally { width -> width } + fadeIn() togetherWith
                            slideOutHorizontally { width -> -width } + fadeOut()
                },
                label = "onboarding_cards"
            ) { signedInState ->
                if (!signedInState) {
                    // ── STEP 1: SIGN IN CARD ──────────────────────────────────────────
                    GlassCard {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("🌌", fontSize = 48.sp)
                            Text(
                                text = "Welcome to Gigi",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Sign in with your Google account to sync your partner connections, shared alarms, and profile across all your devices.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFC4B5FD),
                                textAlign = TextAlign.Center,
                                lineHeight = 21.sp
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Button(
                                onClick = {
                                    if (!authError.isNullOrBlank()) onClearError()
                                    googleSignInClient?.let {
                                        googleSignInLauncher.launch(it.signInIntent)
                                    }
                                },
                                enabled = !isBusy && serverStatus.mode == ServerMode.ONLINE,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp),
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color(0xFF241B45)
                                )
                            ) {
                                if (isBusy) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.5.dp,
                                        color = Color(0xFF8B5CF6)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("Signing in…", fontWeight = FontWeight.Bold)
                                } else {
                                    Text(
                                        text = "G",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color(0xFF4285F4)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        "Continue with Google",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }
                            }

                            (localSignInError ?: authError)?.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFFF6B6B),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    // ── STEP 2: MAKE IT YOURS CUTE PROFILE CARD ────────────────────────
                    GlassCard {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Make it yours 🌸",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )

                            memberIdentity?.googleEmail?.let { email ->
                                Text(
                                    text = "Signed in as $email",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFA78BFA)
                                )
                            }

                            Text(
                                text = "Add your name, birthday, and avatar. You can change this anytime.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFC4B5FD),
                                textAlign = TextAlign.Center,
                                lineHeight = 20.sp
                            )

                            // Avatar Balloon/Emoji Picker
                            val pulseAnim = rememberInfiniteTransition(label = "pulse")
                            val avatarScale by pulseAnim.animateFloat(
                                initialValue = 0.96f, targetValue = 1.04f,
                                animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Reverse),
                                label = "avatarScale"
                            )

                            Box(
                                modifier = Modifier
                                    .size(110.dp)
                                    .scale(avatarScale)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(
                                            listOf(Color(0xFF8B5CF6).copy(alpha = 0.4f), Color.Transparent)
                                        )
                                    )
                                    .border(2.dp, Color(0xFFA78BFA), CircleShape)
                                    .clickable { showAvatarChooser = true },
                                contentAlignment = Alignment.Center
                            ) {
                                val avatarUrl = memberIdentity?.avatarUrl?.takeIf { it.isNotBlank() } ?: chosenEmoji
                                if (avatarUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = coil.request.ImageRequest.Builder(onboardCtx).data(avatarUrl).build(),
                                        imageLoader = onboardEmojiLoader,
                                        contentDescription = "Profile avatar",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.size(76.dp)
                                    )
                                } else {
                                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.White)
                                }
                            }

                            Surface(
                                onClick = { showAvatarChooser = true },
                                shape = RoundedCornerShape(999.dp),
                                color = Color(0xFF8B5CF6).copy(alpha = 0.25f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFA78BFA).copy(alpha = 0.5f))
                            ) {
                                Text(
                                    "Choose your avatar 🎈",
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Name Input
                            OutlinedTextField(
                                value = displayName,
                                onValueChange = {
                                    displayName = it
                                    if (!authError.isNullOrBlank()) onClearError()
                                },
                                label = { Text("Your name 🌸", color = Color(0xFFC4B5FD)) },
                                placeholder = { Text("e.g. Aman Raj", color = Color(0xFF8878A8)) },
                                singleLine = true,
                                enabled = !isBusy,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFFA78BFA),
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )

                            // Date of Birth Picker (Working Tap Overlay!)
                            val calendar = Calendar.getInstance()
                            val datePickerDialog = DatePickerDialog(
                                context,
                                { _, year, month, dayOfMonth ->
                                    dateOfBirth = String.format("%02d/%02d/%04d", dayOfMonth, month + 1, year)
                                    if (!authError.isNullOrBlank()) onClearError()
                                },
                                calendar.get(Calendar.YEAR) - 20,
                                calendar.get(Calendar.MONTH),
                                calendar.get(Calendar.DAY_OF_MONTH)
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable { datePickerDialog.show() }
                            ) {
                                OutlinedTextField(
                                    value = dateOfBirth,
                                    onValueChange = { dateOfBirth = it },
                                    label = { Text("Date of Birth 🎂", color = Color(0xFFC4B5FD)) },
                                    placeholder = { Text("DD/MM/YYYY", color = Color(0xFF8878A8)) },
                                    singleLine = true,
                                    readOnly = true,
                                    enabled = false,
                                    leadingIcon = {
                                        Icon(Icons.Default.Cake, contentDescription = null, tint = Color(0xFFEC4899))
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(20.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        disabledBorderColor = Color.White.copy(alpha = 0.2f),
                                        disabledTextColor = Color.White,
                                        disabledLabelColor = Color(0xFFC4B5FD),
                                        disabledPlaceholderColor = Color(0xFF8878A8)
                                    )
                                )
                                // Transparent touch overlay to guarantee click opens DatePicker
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .clickable { datePickerDialog.show() }
                                )
                            }

                            // Cute Him / Her Gender Selection
                            Text("How Gigi greets you ✨", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDDD6FE))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val options = listOf(
                                    Triple("him", "Him 🙋‍♂️", Color(0xFF60A5FA)),
                                    Triple("her", "Her 🙋‍♀️", Color(0xFFF472B6))
                                )
                                options.forEach { (key, label, accentColor) ->
                                    val isSelected = greetingStyle == key
                                    Surface(
                                        onClick = {
                                            greetingStyle = key
                                            if (!authError.isNullOrBlank()) onClearError()
                                        },
                                        shape = RoundedCornerShape(16.dp),
                                        color = if (isSelected) accentColor.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.08f),
                                        border = androidx.compose.foundation.BorderStroke(
                                            if (isSelected) 2.dp else 1.dp,
                                            if (isSelected) accentColor else Color.White.copy(alpha = 0.15f)
                                        ),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.padding(vertical = 14.dp)
                                        ) {
                                            Text(
                                                text = label,
                                                color = if (isSelected) Color.White else Color(0xFFC4B5FD),
                                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                                fontSize = 15.sp
                                            )
                                        }
                                    }
                                }
                            }

                            authError?.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFFF6B6B),
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )
                            }

                            // Finish Setup Gradient Button
                            Button(
                                onClick = {
                                    onCompleteProfile(
                                        displayName,
                                        greetingStyle,
                                        if (chosenEmoji.isNotBlank()) Uri.parse(chosenEmoji) else null,
                                        dateOfBirth
                                    )
                                },
                                enabled = !isBusy && displayName.isNotBlank() && serverStatus.mode == ServerMode.ONLINE,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                shape = RoundedCornerShape(999.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF8B5CF6)
                                )
                            ) {
                                Text(
                                    "Finish setup ✨",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

            // Lock / Privacy info
            GlassCard {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color(0xFFA78BFA)
                    )
                    Text(
                        text = "After sign-in, your partner connections, shared alarms, and profile restore from the server automatically on any device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFC4B5FD),
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

private fun normalizeGreetingStyle(value: String?): String {
    return when {
        value.equals("her", ignoreCase = true) -> "her"
        value.equals("sparkle", ignoreCase = true) -> "sparkle"
        else -> "him"
    }
}

@Composable
private fun GlassCard(content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(32.dp))
    ) {
        Cloudy(radius = 24) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.White.copy(alpha = 0.06f))
            )
        }
        Surface(
            color = Color(0xFF1E1436).copy(alpha = 0.88f),
            shape = RoundedCornerShape(32.dp)
        ) {
            Column(
                modifier = Modifier.padding(26.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content
            )
        }
    }
}

@Composable
private fun CuteFloatingSparkles() {
    val infiniteTransition = rememberInfiniteTransition(label = "sparkles")
    val alpha1 by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(2500, easing = LinearEasing), RepeatMode.Reverse),
        label = "alpha1"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Text("🌸", fontSize = 24.sp, modifier = Modifier.padding(start = 30.dp, top = 80.dp).scale(alpha1))
        Text("💖", fontSize = 20.sp, modifier = Modifier.align(Alignment.TopEnd).padding(end = 40.dp, top = 120.dp).scale(alpha1))
        Text("✨", fontSize = 28.sp, modifier = Modifier.align(Alignment.CenterStart).padding(start = 20.dp).scale(alpha1))
        Text("🌟", fontSize = 22.sp, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 30.dp, bottom = 140.dp).scale(alpha1))
    }
}

private data class StoryChapter(
    val chapterBadge: String,
    val title: String,
    val story: String,
    val accentColor: Color
)

private val STORY_CHAPTERS = listOf(
    StoryChapter(
        "Chapter 1 · My Galaxy 🌌",
        "Your Personal Sanctuary",
        "Two stars orbiting in a living cosmic galaxy. See each other's live presence, battery level, and music in real time without any noise.",
        Color(0xFF8B5CF6)
    ),
    StoryChapter(
        "Chapter 2 · Live Scribbles 🎨",
        "Drawings on Lock & Home Screen",
        "Draw handwritten doodles, photo notes, and love sparkles. They stream live onto your person's lock screen and interactive home screen widget.",
        Color(0xFFEC4899)
    ),
    StoryChapter(
        "Chapter 3 · The Cosmic Nebula 🌌",
        "Drifting in Open Space",
        "Explore the public Cosmic Nebula. Discover other public voyagers drifting across procedural gas clouds, or keep your galaxy intimate and private.",
        Color(0xFF38BDF8)
    ),
    StoryChapter(
        "Chapter 4 · Our Nest & Twigi 🏡",
        "Your Cozy Shared Home",
        "Decorate your shared room, leave fridge doodle notes, cuddle Mochi the cat, and listen to songs together in real-time synchronicity.",
        Color(0xFF34D399)
    )
)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun GigiIntroCards(onDone: () -> Unit) {
    val pager = androidx.compose.foundation.pager.rememberPagerState { STORY_CHAPTERS.size }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF1E1436), Color(0xFF28194D), Color(0xFF130E26))
                )
            )
    ) {
        CuteFloatingSparkles()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
        ) {
            // Top Bar with Skip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color.White.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                ) {
                    Text(
                        STORY_CHAPTERS[pager.currentPage].chapterBadge,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    "Skip",
                    color = Color(0xFF9A8FC0),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .padding(8.dp)
                        .clickable { onDone() }
                )
            }

            // Pager Content
            androidx.compose.foundation.pager.HorizontalPager(
                state = pager,
                modifier = Modifier.weight(1f)
            ) { page ->
                val ch = STORY_CHAPTERS[page]
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Animated Interactive Graphic per Chapter
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .background(
                                Brush.radialGradient(
                                    listOf(ch.accentColor.copy(alpha = 0.35f), Color.Transparent)
                                ),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        when (page) {
                            0 -> AnimatedChapter1Spark()
                            1 -> AnimatedChapter2Doodle()
                            2 -> AnimatedChapter3Beats()
                            else -> AnimatedChapter4Galaxy()
                        }
                    }

                    Spacer(Modifier.height(32.dp))

                    Text(
                        text = ch.title,
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(14.dp))

                    Text(
                        text = ch.story,
                        color = Color(0xFFC4B5FD),
                        fontSize = 15.sp,
                        lineHeight = 23.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 10.dp)
                    )
                }
            }

            // Dots Indicator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(STORY_CHAPTERS.size) { i ->
                    val isSel = i == pager.currentPage
                    val dotWidth by animateDpAsState(if (isSel) 24.dp else 8.dp, label = "dotWidth")
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .height(8.dp)
                            .width(dotWidth)
                            .clip(CircleShape)
                            .background(
                                if (isSel) STORY_CHAPTERS[i].accentColor
                                else Color.White.copy(alpha = 0.25f)
                            )
                    )
                }
            }

            // Bottom Action Button
            Button(
                onClick = {
                    if (pager.currentPage < STORY_CHAPTERS.size - 1) {
                        scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                    } else onDone()
                },
                colors = ButtonDefaults.buttonColors(containerColor = STORY_CHAPTERS[pager.currentPage].accentColor),
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    text = if (pager.currentPage < STORY_CHAPTERS.size - 1) "Continue Story ➔" else "Begin Your Story ✨",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

// Chapter 1: Living Galaxy with Orbiting Partner Star
@Composable
private fun AnimatedChapter1Spark() {
    val infiniteTransition = rememberInfiniteTransition(label = "ch1")
    val orbitAngle by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(4500, easing = LinearEasing), RepeatMode.Restart),
        label = "orbit"
    )
    val corePulse by infiniteTransition.animateFloat(
        initialValue = 0.92f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "corePulse"
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(160.dp)) {
        Box(
            modifier = Modifier
                .size(128.dp)
                .border(1.5.dp, Color(0xFF8B5CF6).copy(alpha = 0.45f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(70.dp)
                .scale(corePulse)
                .background(Brush.radialGradient(listOf(Color(0xFFFBBF24).copy(alpha = 0.65f), Color.Transparent)), CircleShape)
        )
        Text("☀️", fontSize = 48.sp, modifier = Modifier.scale(corePulse))

        // Orbiting partner star
        val angleRad = Math.toRadians(orbitAngle.toDouble())
        val offsetX = (58 * Math.cos(angleRad)).dp
        val offsetY = (58 * Math.sin(angleRad)).dp

        Text(
            "💖",
            fontSize = 26.sp,
            modifier = Modifier.offset(x = offsetX, y = offsetY)
        )
    }
}

// Chapter 2: Real-time Live Heart Drawing Stroke on Home Widget
@Composable
private fun AnimatedChapter2Doodle() {
    val infiniteTransition = rememberInfiniteTransition(label = "ch2")
    val strokeProgress by infiniteTransition.animateFloat(
        initialValue = 0.05f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Restart),
        label = "progress"
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(160.dp)) {
        // Widget Frame
        Box(
            modifier = Modifier
                .size(136.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Color(0xFF1E1035))
                .border(1.5.dp, Color(0xFFEC4899).copy(alpha = 0.5f), RoundedCornerShape(26.dp)),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.Canvas(modifier = Modifier.size(90.dp)) {
                val path = androidx.compose.ui.graphics.Path()
                val w = size.width
                val h = size.height
                val cx = w / 2f
                val cy = h / 2.2f
                val scale = w / 36f

                val totalSteps = (100 * strokeProgress).toInt().coerceAtLeast(2)
                for (i in 0..totalSteps) {
                    val t = (i / 100f) * 2 * Math.PI.toFloat()
                    val sinVal = kotlin.math.sin(t.toDouble())
                    val x = cx + (16 * sinVal * sinVal * sinVal).toFloat() * scale
                    val y = cy - (13 * kotlin.math.cos(t.toDouble()) - 5 * kotlin.math.cos(2 * t.toDouble()) - 2 * kotlin.math.cos(3 * t.toDouble()) - kotlin.math.cos(4 * t.toDouble())).toFloat() * scale
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }

                drawPath(
                    path = path,
                    color = Color(0xFFEC4899),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 5.dp.toPx(),
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                )
            }
        }
        Text("✏️", fontSize = 28.sp, modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp))
    }
}

// Chapter 3: The Cosmic Nebula Drifting Cloud
@Composable
private fun AnimatedChapter3Beats() {
    val infiniteTransition = rememberInfiniteTransition(label = "ch3")
    val cloudDrift by infiniteTransition.animateFloat(
        initialValue = -10f, targetValue = 10f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "cloud"
    )
    val starTwinkle by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse),
        label = "twinkle"
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(160.dp)) {
        Box(
            modifier = Modifier
                .size(130.dp)
                .background(Brush.radialGradient(listOf(Color(0xFF38BDF8).copy(alpha = 0.45f), Color(0xFFC084FC).copy(alpha = 0.25f), Color.Transparent)), CircleShape)
        )
        Text("🌌", fontSize = 68.sp, modifier = Modifier.offset(x = cloudDrift.dp))
        Text("✨", fontSize = 28.sp, modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).scale(starTwinkle))
        Text("🪐", fontSize = 34.sp, modifier = Modifier.align(Alignment.BottomStart).padding(6.dp).offset(y = (-cloudDrift).dp))
    }
}

// Chapter 4: Twigi Companion & Shared Spotify Beats
@Composable
private fun AnimatedChapter4Galaxy() {
    val infiniteTransition = rememberInfiniteTransition(label = "ch4")
    val vinylRotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3500, easing = LinearEasing), RepeatMode.Restart),
        label = "vinyl"
    )
    val twigiBounce by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = -8f,
        animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "twigiBounce"
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(160.dp)) {
        Box(
            modifier = Modifier
                .size(124.dp)
                .background(Brush.radialGradient(listOf(Color(0xFF34D399).copy(alpha = 0.35f), Color.Transparent)), CircleShape)
        )
        // Vinyl record
        Text(
            "💿",
            fontSize = 58.sp,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp).graphicsLayer { rotationZ = vinylRotation }
        )
        // Twigi companion
        Text(
            "🌱",
            fontSize = 48.sp,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp).offset(y = twigiBounce.dp)
        )
        Text("🎵", fontSize = 24.sp, modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
    }
}
