import re

with open('app/src/main/java/com/aman/gigi/ui/Developer.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Add var showAddMembersDialog by remember { mutableStateOf(false) } to HubScene
content = content.replace(
    'var showGroupBuilder by remember { mutableStateOf(false) }',
    'var showGroupBuilder by remember { mutableStateOf(false) }\n    var showAddMembersDialog by remember { mutableStateOf(false) }'
)

# 2. In HubScene, update the ProfileHero call
old_profile_hero_call = """                ProfileHero(
                    identity = memberIdentity,
                    currentPartner = currentPartner,
                    groupMembers = groupMembers,
                    connectionCount = creatorConnections.size,
                    pendingAvatarUri = pendingAvatarUri,
                    serverMode = serverStatus.mode,
                    quotePreview = partnerQuotePreview,
                    selfQuoteText = selfQuoteText,
                    theme = theme,
                    onSelfAvatarClick = { showAvatarChooser = true },
                    onPartnerClick = {
                        if (currentPartner != null) {
                            showQuoteDialog = true
                        }
                    },
                    onEmojiClick = { target ->
                        emojiPickerTarget = target
                        showEmojiPicker = true
                    }
                )"""

new_profile_hero_call = """                ProfileHero(
                    identity = memberIdentity,
                    currentPartner = currentPartner,
                    groupMembers = groupMembers,
                    connectionCount = creatorConnections.size,
                    pendingAvatarUri = pendingAvatarUri,
                    serverMode = serverStatus.mode,
                    quotePreview = partnerQuotePreview,
                    selfQuoteText = selfQuoteText,
                    theme = theme,
                    activeConnections = activeConnections,
                    onSelfAvatarClick = { showAvatarChooser = true },
                    onPartnerClick = {
                        if (currentPartner != null) {
                            showQuoteDialog = true
                        }
                    },
                    onEmojiClick = { target ->
                        emojiPickerTarget = target
                        showEmojiPicker = true
                    },
                    onAddClick = { showAddMembersDialog = true }
                )"""
content = content.replace(old_profile_hero_call, new_profile_hero_call)

# 3. Add AddMembersDialog logic inside HubScene (near GroupBuilderDialog)
old_group_builder = """                if (showGroupBuilder) {
                    GroupBuilderDialog(
                        connections = activeConnections.filter {
                            !isGroupConnection(it)
                        },
                        onDismiss = { showGroupBuilder = false },
                        onCreate = { name, ids, emoji ->
                            showGroupBuilder = false
                            viewModel.createGroupFromConnections(name, ids, emoji)
                        }
                    )
                }"""

new_group_builder = """                if (showGroupBuilder) {
                    GroupBuilderDialog(
                        connections = activeConnections.filter {
                            !isGroupConnection(it)
                        },
                        onDismiss = { showGroupBuilder = false },
                        onCreate = { name, ids, emoji ->
                            showGroupBuilder = false
                            viewModel.createGroupFromConnections(name, ids, emoji)
                        }
                    )
                }
                
                if (showAddMembersDialog && currentPartner != null) {
                    // Reusing GroupBuilderDialog UI pattern but adapted for adding members.
                    GroupBuilderDialog(
                        connections = activeConnections.filter {
                            !isGroupConnection(it) && groupMembers.none { gm -> gm.memberDeviceId == it.partnerDeviceId }
                        },
                        onDismiss = { showAddMembersDialog = false },
                        onCreate = { _, ids, _ ->
                            showAddMembersDialog = false
                            viewModel.inviteConnectionsToGroup(currentPartner.connectionId, currentPartner.partnerName, ids)
                        },
                        isAddingToExistingGroup = true
                    )
                }"""
content = content.replace(old_group_builder, new_group_builder)

# 4. Update ProfileHero signature
old_ph_sig = """private fun ProfileHero(
    identity: MemberIdentity?,
    currentPartner: com.aman.gigi.model.Connection?,
    groupMembers: List<com.aman.gigi.model.ConnectionMember> = emptyList(),
    connectionCount: Int,
    pendingAvatarUri: Uri?,
    serverMode: ServerMode,
    quotePreview: ReceivedQuoteOverlay?,
    selfQuoteText: String?,
    theme: com.aman.gigi.model.ConnectionTheme,
    onSelfAvatarClick: () -> Unit,
    onPartnerClick: () -> Unit,
    onEmojiClick: (String) -> Unit
)"""
new_ph_sig = """private fun ProfileHero(
    identity: MemberIdentity?,
    currentPartner: com.aman.gigi.model.Connection?,
    groupMembers: List<com.aman.gigi.model.ConnectionMember> = emptyList(),
    connectionCount: Int,
    pendingAvatarUri: Uri?,
    serverMode: ServerMode,
    quotePreview: ReceivedQuoteOverlay?,
    selfQuoteText: String?,
    theme: com.aman.gigi.model.ConnectionTheme,
    activeConnections: List<com.aman.gigi.model.Connection> = emptyList(),
    onSelfAvatarClick: () -> Unit,
    onPartnerClick: () -> Unit,
    onEmojiClick: (String) -> Unit,
    onAddClick: (() -> Unit)? = null
)"""
content = content.replace(old_ph_sig, new_ph_sig)

# 5. Update ProfileHero call to GroupMembersGrid
old_gmg_call = """                    if (isGroupView) {
                        GroupMembersGrid(
                            members = groupMembers,
                            theme = theme,
                            modifier = Modifier.matchParentSize().padding(14.dp)
                        )
                    }"""
new_gmg_call = """                    if (isGroupView) {
                        GroupMembersGrid(
                            members = groupMembers,
                            theme = theme,
                            activeConnections = activeConnections,
                            onAddClick = onAddClick,
                            modifier = Modifier.matchParentSize().padding(14.dp)
                        )
                    }"""
content = content.replace(old_gmg_call, new_gmg_call)

# 6. Update GroupMembersGrid signature and row
old_gmg_sig = """private fun GroupMembersGrid(
    members: List<com.aman.gigi.model.ConnectionMember>,
    theme: com.aman.gigi.model.ConnectionTheme,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = theme.primaryColor.copy(alpha = 0.12f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("👯", fontSize = 15.sp)
                Text(
                    "${members.size} members",
                    color = theme.primaryColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }"""
new_gmg_sig = """private fun GroupMembersGrid(
    members: List<com.aman.gigi.model.ConnectionMember>,
    theme: com.aman.gigi.model.ConnectionTheme,
    activeConnections: List<com.aman.gigi.model.Connection> = emptyList(),
    onAddClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = theme.primaryColor.copy(alpha = 0.12f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("👯", fontSize = 15.sp)
                Text(
                    "${members.size} members",
                    color = theme.primaryColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                if (onAddClick != null) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(theme.primaryColor.copy(alpha = 0.2f))
                            .clickable(onClick = onAddClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("+", color = theme.primaryColor, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.offset(y = (-1).dp))
                    }
                }
            }
        }"""
content = content.replace(old_gmg_sig, new_gmg_sig)

# 7. Update MemberMiniCard call in GroupMembersGrid
old_mmc_call = """            members.forEach { m ->
                MemberMiniCard(member = m, theme = theme)
            }"""
new_mmc_call = """            members.forEach { m ->
                val localConn = activeConnections.find { !it.isGroup && it.partnerDeviceId == m.memberDeviceId }
                val localEmoji = localConn?.partnerEmoji
                MemberMiniCard(member = m, theme = theme, localPartnerEmoji = localEmoji)
            }"""
content = content.replace(old_mmc_call, new_mmc_call)


# 8. Update MemberMiniCard signature and emoji logic
old_mmc_sig = """private fun MemberMiniCard(
    member: com.aman.gigi.model.ConnectionMember,
    theme: com.aman.gigi.model.ConnectionTheme
) {"""
new_mmc_sig = """private fun MemberMiniCard(
    member: com.aman.gigi.model.ConnectionMember,
    theme: com.aman.gigi.model.ConnectionTheme,
    localPartnerEmoji: String? = null
) {"""
content = content.replace(old_mmc_sig, new_mmc_sig)

old_mmc_emoji = """                } else if (!url.isNullOrBlank()) {
                    AsyncImage(
                        model = url,
                        contentDescription = member.memberName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize().clip(CircleShape)
                    )
                } else if (member.memberEmoji.isNotBlank()) {
                    Text(member.memberEmoji, fontSize = 26.sp)
                } else {"""
new_mmc_emoji = """                } else if (!url.isNullOrBlank()) {
                    AsyncImage(
                        model = url,
                        contentDescription = member.memberName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize().clip(CircleShape)
                    )
                } else if (member.memberEmoji != "🌻" && member.memberEmoji.isNotBlank()) {
                    Text(member.memberEmoji, fontSize = 26.sp)
                } else if (!localPartnerEmoji.isNullOrBlank()) {
                    Text(localPartnerEmoji, fontSize = 26.sp)
                } else if (member.memberEmoji.isNotBlank()) {
                    Text(member.memberEmoji, fontSize = 26.sp)
                } else {"""
content = content.replace(old_mmc_emoji, new_mmc_emoji)

# 9. Update GroupBuilderDialog to accept isAddingToExistingGroup
old_gbd_sig = """private fun GroupBuilderDialog(
    connections: List<Connection>,
    onDismiss: () -> Unit,
    onCreate: (String, List<String>, String?) -> Unit
) {"""
new_gbd_sig = """private fun GroupBuilderDialog(
    connections: List<Connection>,
    onDismiss: () -> Unit,
    onCreate: (String, List<String>, String?) -> Unit,
    isAddingToExistingGroup: Boolean = false
) {"""
content = content.replace(old_gbd_sig, new_gbd_sig)

old_gbd_ui = """                Text("New Group 👯", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF3B2A6B))
                Spacer(Modifier.height(2.dp))
                Text("Pick an emoji, who to add, and a name.", fontSize = 12.sp, color = Color(0xFF9A8FC0))
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Group emoji chooser — tap to pick an animated emoji for the group.
                    Surface(
                        onClick = { showGroupEmojiPicker = true },
                        shape = CircleShape,
                        color = Color(0xFFF3EEFF),
                        modifier = Modifier.size(56.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            val ge = groupEmoji
                            if (ge != null) {
                                AsyncImage(
                                    model = ImageRequest.Builder(gbCtx).data(ge).build(),
                                    imageLoader = gbLoader,
                                    contentDescription = "Group emoji",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize().padding(8.dp)
                                )
                            } else {
                                Text("👯", fontSize = 26.sp)
                            }
                        }
                    }
                    OutlinedTextField(
                        value = groupName,
                        onValueChange = { groupName = it },
                        label = { Text("Group name") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF8B5CF6),
                            unfocusedBorderColor = Color(0xFF9A8FC0).copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(14.dp))"""
new_gbd_ui = """                Text(if (isAddingToExistingGroup) "Add Members 👯" else "New Group 👯", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF3B2A6B))
                Spacer(Modifier.height(2.dp))
                Text(if (isAddingToExistingGroup) "Pick who you want to add to this group." else "Pick an emoji, who to add, and a name.", fontSize = 12.sp, color = Color(0xFF9A8FC0))
                Spacer(Modifier.height(16.dp))
                if (!isAddingToExistingGroup) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Group emoji chooser — tap to pick an animated emoji for the group.
                        Surface(
                            onClick = { showGroupEmojiPicker = true },
                            shape = CircleShape,
                            color = Color(0xFFF3EEFF),
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                val ge = groupEmoji
                                if (ge != null) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(gbCtx).data(ge).build(),
                                        imageLoader = gbLoader,
                                        contentDescription = "Group emoji",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize().padding(8.dp)
                                    )
                                } else {
                                    Text("👯", fontSize = 26.sp)
                                }
                            }
                        }
                        OutlinedTextField(
                            value = groupName,
                            onValueChange = { groupName = it },
                            label = { Text("Group name") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF8B5CF6),
                                unfocusedBorderColor = Color(0xFF9A8FC0).copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                }"""
content = content.replace(old_gbd_ui, new_gbd_ui)

old_gbd_btn = """                    Button(
                        onClick = { onCreate(groupName, selected.toList(), groupEmoji) },
                        enabled = groupName.isNotBlank() && selected.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier.weight(1f)
                    ) { Text("Create") }"""
new_gbd_btn = """                    Button(
                        onClick = { onCreate(groupName, selected.toList(), groupEmoji) },
                        enabled = (isAddingToExistingGroup || groupName.isNotBlank()) && selected.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier.weight(1f)
                    ) { Text(if (isAddingToExistingGroup) "Add" else "Create") }"""
content = content.replace(old_gbd_btn, new_gbd_btn)

with open('app/src/main/java/com/aman/gigi/ui/Developer.kt', 'w', encoding='utf-8') as f:
    f.write(content)
print("Updated Developer.kt")
