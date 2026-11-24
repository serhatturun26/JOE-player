package com.joe.player

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object JoeColors {
    val BackgroundDark = Color(0xFF0D0607)
    val SurfaceDark = Color(0xFF1A0B0D)
    val CardDark = Color(0xFF241013)
    val RedPrimary = Color(0xFFE50914)
    val RedBright = Color(0xFFFF1E2D)
    val RedMuted = Color(0xFF5C1116)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFB8A3A6)
    val LiveBadge = Color(0xFFFF2A3B)
}

data class JoeChannelItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val isLive: Boolean = false
)

enum class JoeTab(val label: String, val icon: ImageVector) {
    LIVE("Canlı TV", Icons.Rounded.LiveTv),
    MOVIES("Filmler", Icons.Rounded.Movie),
    SERIES("Diziler", Icons.Rounded.Tv),
    PLAYLISTS("Listeler", Icons.Rounded.PlaylistPlay)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JoeAppTheme {
                JoeMainScreen()
            }
        }
    }
}

@Composable
fun JoeAppTheme(content: @Composable () -> Unit) {
    val colorScheme = darkColorScheme(
        primary = JoeColors.RedPrimary,
        onPrimary = JoeColors.TextPrimary,
        secondary = JoeColors.RedBright,
        background = JoeColors.BackgroundDark,
        onBackground = JoeColors.TextPrimary,
        surface = JoeColors.SurfaceDark,
        onSurface = JoeColors.TextPrimary,
        surfaceVariant = JoeColors.CardDark,
        onSurfaceVariant = JoeColors.TextSecondary
    )

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

@Composable
fun JoeMainScreen() {
    var selectedTab by remember { mutableStateOf(JoeTab.LIVE) }
    var selectedCategory by remember { mutableStateOf("Tümü") }
    var selectedChannel by remember { mutableStateOf<JoeChannelItem?>(null) }
    var activePlayerChannel by remember { mutableStateOf<JoeChannelItem?>(null) }
    var isOverlayVisible by remember { mutableStateOf(true) }

    val categories = listOf("Tümü", "Ulusal", "Spor", "Sinema", "Belgesel", "Türkçe Ses")

    val sampleChannels = remember {
        listOf(
            JoeChannelItem("1", "JOE HD Canlı", "Şimdi: Ana Haber Bülteni", isLive = true),
            JoeChannelItem("2", "Sinema Kırmızı 1", "Şimdi: Gece Yarısı Takibi (TR)", isLive = true),
            JoeChannelItem("3", "Spor Arena HD", "Şimdi: Haftanın Karşılaşması", isLive = true),
            JoeChannelItem("4", "Dizi Kulübü", "Şimdi: Sezon Finali Özel", isLive = true),
            JoeChannelItem("5", "Belgesel Dünya", "Şimdi: Derin Okyanuslar", isLive = true)
        )
    }

    val sampleVodItems = remember {
        listOf(
            JoeChannelItem("101", "Kızıl Gökyüzü", "2025 • Aksiyon • Türkçe Dublaj"),
            JoeChannelItem("102", "Son Sinyal", "2024 • Bilim Kurgu • TR / EN"),
            JoeChannelItem("103", "Şehirdeki Gölge", "2026 • Gerilim • Türkçe Altyazı"),
            JoeChannelItem("104", "Büyük Kaçış", "2023 • Macera • Türkçe Dublaj")
        )
    }

    if (activePlayerChannel != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Rounded.LiveTv,
                    contentDescription = null,
                    tint = JoeColors.RedBright,
                    modifier = Modifier.size(52.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = activePlayerChannel!!.title,
                    color = JoeColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }

            JoePlayerOverlay(
                currentChannel = activePlayerChannel!!,
                channels = sampleChannels,
                isVisible = isOverlayVisible,
                onToggleVisibility = { isOverlayVisible = !isOverlayVisible },
                onSelectChannel = { newChannel ->
                    activePlayerChannel = newChannel
                    isOverlayVisible = true
                },
                onClosePlayer = { activePlayerChannel = null }
            )
        }
        return
    }

    Scaffold(
        containerColor = JoeColors.BackgroundDark,
        topBar = {
            JoeTopBar(onRefreshClick = { selectedCategory = "Tümü" })
        },
        bottomBar = {
            JoeBottomBar(
                selectedTab = selectedTab,
                onSelectTab = { selectedTab = it }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            JoeColors.SurfaceDark,
                            JoeColors.BackgroundDark
                        )
                    )
                )
        ) {
            if (selectedTab != JoeTab.PLAYLISTS) {
                CategoryFilterRow(
                    categories = categories,
                    selectedCategory = selectedCategory,
                    onSelectCategory = { selectedCategory = it }
                )
            }

            when (selectedTab) {
                JoeTab.LIVE -> {
                    LiveContentSection(
                        channels = sampleChannels,
                        onChannelClick = { selectedChannel = it }
                    )
                }
                JoeTab.MOVIES -> {
                    MediaGridSection(
                        title = "Öne Çıkan Filmler",
                        items = sampleVodItems,
                        onItemClick = { selectedChannel = it }
                    )
                }
                JoeTab.SERIES -> {
                    MediaGridSection(
                        title = "Popüler Diziler",
                        items = sampleVodItems,
                        onItemClick = { selectedChannel = it }
                    )
                }
                JoeTab.PLAYLISTS -> {
                    PlaylistManagementSection()
                }
            }
        }

        selectedChannel?.let { item ->
            QuickLaunchDialog(
                item = item,
                onDismiss = { selectedChannel = null },
                onStartPlayback = {
                    selectedChannel = null
                    activePlayerChannel = item
                    isOverlayVisible = true
                }
            )
        }
    }
}

@Composable
private fun JoeTopBar(onRefreshClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(JoeColors.SurfaceDark)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(JoeColors.RedBright, JoeColors.RedMuted)
                        )
                    )
                    .border(1.dp, JoeColors.RedBright.copy(alpha = 0.6f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "JOE",
                    color = JoeColors.TextPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = "JOE PLAYER",
                    color = JoeColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "Aktif Liste • Hazır",
                    color = JoeColors.TextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        IconButton(
            onClick = onRefreshClick,
            modifier = Modifier
                .clip(CircleShape)
                .background(JoeColors.CardDark)
        ) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = "Listeyi Güncelle",
                tint = JoeColors.RedBright
            )
        }
    }
}

@Composable
private fun JoeBottomBar(
    selectedTab: JoeTab,
    onSelectTab: (JoeTab) -> Unit
) {
    NavigationBar(
        containerColor = JoeColors.SurfaceDark,
        tonalElevation = 0.dp
    ) {
        JoeTab.entries.forEach { tab ->
            val isSelected = selectedTab == tab
            NavigationBarItem(
                selected = isSelected,
                onClick = { onSelectTab(tab) },
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label
                    )
                },
                label = {
                    Text(
                        text = tab.label,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = JoeColors.TextPrimary,
                    selectedTextColor = JoeColors.RedBright,
                    indicatorColor = JoeColors.RedPrimary.copy(alpha = 0.28f),
                    unselectedIconColor = JoeColors.TextSecondary,
                    unselectedTextColor = JoeColors.TextSecondary
                )
            )
        }
    }
}

@Composable
private fun CategoryFilterRow(
    categories: List<String>,
    selectedCategory: String,
    onSelectCategory: (String) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(categories) { category ->
            val selected = category == selectedCategory
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (selected) JoeColors.RedPrimary else JoeColors.CardDark)
                    .border(
                        width = 1.dp,
                        color = if (selected) JoeColors.RedBright else JoeColors.RedMuted,
                        shape = RoundedCornerShape(20.dp)
                    )
                    .clickable { onSelectCategory(category) }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text(
                    text = category,
                    color = if (selected) JoeColors.TextPrimary else JoeColors.TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun LiveContentSection(
    channels: List<JoeChannelItem>,
    onChannelClick: (JoeChannelItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = "Canlı Kanallar",
                color = JoeColors.TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        items(channels, key = { it.id }) { channel ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(JoeColors.CardDark)
                    .border(1.dp, JoeColors.RedMuted.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                    .clickable { onChannelClick(channel) }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(JoeColors.RedMuted),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = JoeColors.RedBright,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = channel.title,
                            color = JoeColors.TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (channel.isLive) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(JoeColors.LiveBadge)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "CANLI",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = channel.subtitle,
                        color = JoeColors.TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = JoeColors.TextSecondary
                )
            }
        }
    }
}

@Composable
private fun MediaGridSection(
    title: String,
    items: List<JoeChannelItem>,
    onItemClick: (JoeChannelItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = title,
                color = JoeColors.TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }

        items(items.chunked(2)) { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowItems.forEach { item ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(JoeColors.CardDark)
                            .border(1.dp, JoeColors.RedMuted.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                            .clickable { onItemClick(item) }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(115.dp)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(JoeColors.RedMuted, JoeColors.SurfaceDark)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayCircleFilled,
                                contentDescription = null,
                                tint = JoeColors.RedBright,
                                modifier = Modifier.size(42.dp)
                            )
                        }
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = item.title,
                                color = JoeColors.TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = item.subtitle,
                                color = JoeColors.TextSecondary,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PlaylistManagementSection() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Liste Yönetimi (Xtream • M3U • GitHub)",
            color = JoeColors.TextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = JoeColors.CardDark),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Kayıtlı Kaynaklar",
                    color = JoeColors.RedBright,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = "Kayıtlı listelerini buradan tek dokunuşla güncelleyebilir, silebilir veya yeni Xtream / M3U / GitHub bağlantısı ekleyebilirsin.",
                    color = JoeColors.TextSecondary,
                    fontSize = 13.sp
                )
                Button(
                    onClick = { },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = JoeColors.RedPrimary,
                        contentColor = JoeColors.TextPrimary
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Rounded.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Yeni Liste Ekle", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun QuickLaunchDialog(
    item: JoeChannelItem,
    onDismiss: () -> Unit,
    onStartPlayback: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = JoeColors.CardDark,
        titleContentColor = JoeColors.TextPrimary,
        textContentColor = JoeColors.TextSecondary,
        title = {
            Text(text = item.title, fontWeight = FontWeight.Bold)
        },
        text = {
            Text(text = "${item.subtitle}\n\nYayın oynatıcıya aktarılsın mı?")
        },
        confirmButton = {
            Button(
                onClick = onStartPlayback,
                colors = ButtonDefaults.buttonColors(
                    containerColor = JoeColors.RedPrimary,
                    contentColor = JoeColors.TextPrimary
                )
            ) {
                Text("Oynat", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Kapat", color = JoeColors.TextSecondary)
            }
        }
    )
}

@Composable
fun JoePlayerOverlay(
    currentChannel: JoeChannelItem,
    channels: List<JoeChannelItem>,
    isVisible: Boolean,
    onToggleVisibility: () -> Unit,
    onSelectChannel: (JoeChannelItem) -> Unit,
    onClosePlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showChannelDrawer by remember { mutableStateOf(false) }
    var selectedAudioTrack by remember { mutableStateOf("Türkçe (TR)") }
    var keepScreenOn by remember { mutableStateOf(true) }

    val audioTracks = listOf("Türkçe (TR)", "Orijinal", "Türkçe Altyazı")

    DisposableEffect(keepScreenOn) {
        val window = (context as? Activity)?.window
        if (keepScreenOn) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable { onToggleVisibility() }
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.82f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.88f)
                            )
                        )
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = onClosePlayer,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(JoeColors.CardDark.copy(alpha = 0.9f))
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ArrowBack,
                                contentDescription = "Geri",
                                tint = JoeColors.TextPrimary
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = currentChannel.title,
                                color = JoeColors.TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = currentChannel.subtitle,
                                color = JoeColors.TextSecondary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(JoeColors.CardDark.copy(alpha = 0.9f))
                            .border(
                                1.dp,
                                if (keepScreenOn) JoeColors.RedBright else JoeColors.RedMuted,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { keepScreenOn = !keepScreenOn }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lightbulb,
                            contentDescription = "Ekran Açık Kalsın",
                            tint = if (keepScreenOn) JoeColors.RedBright else JoeColors.TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (keepScreenOn) "Açık" else "Normal",
                            color = JoeColors.TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        audioTracks.forEach { track ->
                            val active = selectedAudioTrack == track
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (active) JoeColors.RedPrimary
                                        else JoeColors.CardDark.copy(alpha = 0.92f)
                                    )
                                    .border(
                                        1.dp,
                                        if (active) JoeColors.RedBright else JoeColors.RedMuted,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { selectedAudioTrack = track }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = track,
                                    color = JoeColors.TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    Button(
                        onClick = { showChannelDrawer = !showChannelDrawer },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = JoeColors.RedPrimary,
                            contentColor = JoeColors.TextPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Rounded.QueueMusic, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (showChannelDrawer) "Kanal Listesini Gizle" else "Hızlı Kanal Listesi",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = isVisible && showChannelDrawer,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
                .width(260.dp)
                .heightIn(max = 320.dp)
        ) {
            LazyColumn(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(JoeColors.SurfaceDark.copy(alpha = 0.96f))
                    .border(1.dp, JoeColors.RedBright.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(channels, key = { it.id }) { item ->
                    val isCurrent = item.id == currentChannel.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isCurrent) JoeColors.RedPrimary.copy(alpha = 0.35f) else JoeColors.CardDark)
                            .clickable {
                                onSelectChannel(item)
                                showChannelDrawer = false
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.title,
                            color = if (isCurrent) JoeColors.RedBright else JoeColors.TextPrimary,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
