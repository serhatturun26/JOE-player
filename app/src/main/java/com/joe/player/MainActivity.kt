package com.joe.player

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

enum class MediaKind(val label: String) {
    LIVE("Canlı TV"),
    MOVIES("Filmler"),
    SERIES("Diziler")
}

data class XtreamCredentials(
    val serverUrl: String,
    val username: String,
    val password: String
)

data class ChannelEntry(
    val id: String,
    val name: String,
    val url: String,
    val group: String = "Diğer",
    val kind: MediaKind = MediaKind.LIVE,
    val isFavorite: Boolean = false,
    val xtreamSeriesId: String? = null,
    val xtreamCreds: XtreamCredentials? = null
)

data class SeriesEpisodeItem(
    val id: String,
    val season: Int,
    val episodeNum: Int,
    val title: String,
    val streamUrl: String
)

data class SavedPlaylist(
    val id: String,
    val title: String,
    val sourceInfo: String,
    val channels: List<ChannelEntry>
)

private enum class BottomRoute(val label: String) {
    HOME("Ana Sayfa"),
    PLAYLISTS("Listelerim"),
    SETTINGS("Ayarlar")
}

private enum class ImportMode(val label: String) {
    XTREAM("Xtream"),
    URL("URL"),
    FILE("Dosya"),
    SINGLE("Tekli")
}

private enum class SortOrder(val label: String) {
    DEFAULT("Varsayılan Sıra"),
    AZ("İsme Göre (A-Z)"),
    FAVORITES_FIRST("Önce Favoriler")
}

private val AccentOrange = Color(0xFFE66C2C)
private val CardDarkBg = Color(0xFF2B2B2B)
private val ChipDarkBg = Color(0xFF1F1F1F)
private val LightPageBg = Color(0xFFF7F7F7)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = AccentOrange,
                    background = LightPageBg,
                    surface = Color.White
                )
            ) {
                JoeApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JoeApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentRoute by remember { mutableStateOf(BottomRoute.PLAYLISTS) }
    var selectedMediaKind by remember { mutableStateOf(MediaKind.LIVE) }
    var playlists by remember { mutableStateOf<List<SavedPlaylist>>(emptyList()) }

    var allChannels by remember {
        mutableStateOf(
            listOf(
                ChannelEntry(
                    id = "demo-live-1",
                    name = "Test Yayın 1 | FHD",
                    url = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
                    group = "Demo Kanallar",
                    kind = MediaKind.LIVE
                )
            )
        )
    }

    var activeChannel by remember { mutableStateOf<ChannelEntry?>(null) }
    var isPlayerFullScreen by remember { mutableStateOf(false) }

    // Xtream Dizi Bölüm Seçici Dialog durumları
    var selectedSeriesForEpisodes by remember { mutableStateOf<ChannelEntry?>(null) }
    var seriesEpisodes by remember { mutableStateOf<List<SeriesEpisodeItem>>(emptyList()) }
    var isFetchingEpisodes by remember { mutableStateOf(false) }
    var episodeErrorText by remember { mutableStateOf<String?>(null) }

    // Arama, Kategori ve Sıralama durumları
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("Tümü") }
    var showCategoryMenu by remember { mutableStateOf(false) }
    var sortOrder by remember { mutableStateOf(SortOrder.DEFAULT) }
    var showSortMenu by remember { mutableStateOf(false) }

    // Listelerim sekmesi durumları
    var importMode by remember { mutableStateOf(ImportMode.XTREAM) }
    var defaultImportKind by remember { mutableStateOf(MediaKind.LIVE) }
    var listNameInput by remember { mutableStateOf("") }

    // Xtream alanları
    var xtreamServerInput by remember { mutableStateOf("") }
    var xtreamUserInput by remember { mutableStateOf("") }
    var xtreamPassInput by remember { mutableStateOf("") }
    var showXtreamPassword by remember { mutableStateOf(false) }

    // URL ve Tekli alanlar
    var playlistUrlInput by remember { mutableStateOf("") }
    var singleChannelName by remember { mutableStateOf("") }
    var singleStreamUrl by remember { mutableStateOf("") }
    var singleGroupName by remember { mutableStateOf("Genel") }

    var autoPlayOnSelect by remember { mutableStateOf(true) }
    var statusText by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val exoPlayer = remember { ExoPlayer.Builder(context).build() }

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    LaunchedEffect(activeChannel?.url, autoPlayOnSelect) {
        val target = activeChannel?.url
        if (!target.isNullOrBlank()) {
            exoPlayer.setMediaItem(MediaItem.fromUri(target))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = autoPlayOnSelect
        } else {
            exoPlayer.stop()
        }
    }

    fun toggleFavorite(channelId: String) {
        allChannels = allChannels.map {
            if (it.id == channelId) it.copy(isFavorite = !it.isFavorite) else it
        }
    }

    fun addImportedChannels(
        newChannels: List<ChannelEntry>,
        playlistTitle: String,
        sourceInfo: String
    ) {
        if (newChannels.isEmpty()) {
            statusText = "Geçerli içerik bulunamadı."
            return
        }
        val existingKeys = allChannels.map { "${it.kind.name}-${it.id}-${it.url}" }.toSet()
        val uniqueChannels = newChannels.filter { "${it.kind.name}-${it.id}-${it.url}" !in existingKeys }
        allChannels = uniqueChannels + allChannels

        val newPlaylist = SavedPlaylist(
            id = "pl-${System.currentTimeMillis()}-${playlists.size}",
            title = playlistTitle.ifBlank { "Liste ${playlists.size + 1}" },
            sourceInfo = sourceInfo,
            channels = newChannels
        )
        playlists = listOf(newPlaylist) + playlists
        val liveCount = newChannels.count { it.kind == MediaKind.LIVE }
        val movieCount = newChannels.count { it.kind == MediaKind.MOVIES }
        val seriesCount = newChannels.count { it.kind == MediaKind.SERIES }
        statusText = "Yüklendi: $liveCount Canlı TV, $movieCount Film, $seriesCount Dizi"
    }

    fun handleCardClick(item: ChannelEntry) {
        if (item.kind == MediaKind.SERIES && item.xtreamSeriesId != null && item.xtreamCreds != null) {
            selectedSeriesForEpisodes = item
            seriesEpisodes = emptyList()
            episodeErrorText = null
            scope.launch {
                isFetchingEpisodes = true
                try {
                    seriesEpisodes = withContext(Dispatchers.IO) {
                        fetchXtreamSeriesEpisodes(item.xtreamCreds, item.xtreamSeriesId)
                    }
                    if (seriesEpisodes.isEmpty()) {
                        episodeErrorText = "Bu dizi için bölüm bulunamadı."
                    }
                } catch (e: Exception) {
                    episodeErrorText = "Bölümler alınamadı: ${e.localizedMessage ?: "Bağlantı hatası"}"
                } finally {
                    isFetchingEpisodes = false
                }
            }
        } else {
            activeChannel = item
        }
    }

    val m3uFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isLoading = true
                statusText = null
                try {
                    val content = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            BufferedReader(InputStreamReader(stream)).readText()
                        }.orEmpty()
                    }
                    val parsed = parsePlaylistText(
                        rawText = content,
                        defaultGroup = "Diğer",
                        preferredKind = defaultImportKind
                    )
                    val title = listNameInput.trim().ifEmpty { "Yerel Dosya Listesi" }
                    addImportedChannels(parsed, title, "Yerel .m3u / .m3u8 dosyası")
                    listNameInput = ""
                    currentRoute = BottomRoute.HOME
                } catch (e: Exception) {
                    statusText = "Dosya okunamadı: ${e.localizedMessage ?: "Hata"}"
                } finally {
                    isLoading = false
                }
            }
        }
    }

    if (isPlayerFullScreen && activeChannel != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = true
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            IconButton(
                onClick = { isPlayerFullScreen = false },
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.TopStart)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Geri",
                    tint = Color.White
                )
            }
        }
        return
    }

    // Xtream Dizi Bölüm Seçici Dialog
    if (selectedSeriesForEpisodes != null) {
        val seriesItem = selectedSeriesForEpisodes!!
        AlertDialog(
            onDismissRequest = { selectedSeriesForEpisodes = null },
            title = {
                Text(
                    text = seriesItem.name,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 360.dp)
                ) {
                    when {
                        isFetchingEpisodes -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(color = AccentOrange)
                                Text("Sezon ve bölümler yükleniyor...")
                            }
                        }

                        episodeErrorText != null -> {
                            Text(episodeErrorText!!, color = MaterialTheme.colorScheme.error)
                        }

                        else -> {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(seriesEpisodes, key = { it.id }) { ep ->
                                    Surface(
                                        onClick = {
                                            activeChannel = ChannelEntry(
                                                id = "ep-${ep.id}",
                                                name = "${seriesItem.name} - S${ep.season}E${ep.episodeNum}: ${ep.title}",
                                                url = ep.streamUrl,
                                                group = seriesItem.group,
                                                kind = MediaKind.SERIES
                                            )
                                            selectedSeriesForEpisodes = null
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFFF2F2F2),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                tint = AccentOrange
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Sezon ${ep.season} • Bölüm ${ep.episodeNum} - ${ep.title}",
                                                fontSize = 13.sp,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedSeriesForEpisodes = null }) {
                    Text("Kapat", color = AccentOrange)
                }
            }
        )
    }

    Scaffold(
        containerColor = LightPageBg,
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF1A1A1A),
                contentColor = Color.White
            ) {
                NavigationBarItem(
                    selected = currentRoute == BottomRoute.HOME,
                    onClick = { currentRoute = BottomRoute.HOME },
                    icon = { Icon(Icons.Default.LiveTv, contentDescription = null) },
                    label = { Text("Ana Sayfa") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AccentOrange,
                        selectedTextColor = AccentOrange,
                        indicatorColor = Color(0xFF2B2B2B),
                        unselectedIconColor = Color.LightGray,
                        unselectedTextColor = Color.LightGray
                    )
                )
                NavigationBarItem(
                    selected = currentRoute == BottomRoute.PLAYLISTS,
                    onClick = { currentRoute = BottomRoute.PLAYLISTS },
                    icon = { Icon(Icons.Default.QueueMusic, contentDescription = null) },
                    label = { Text("Listelerim") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AccentOrange,
                        selectedTextColor = AccentOrange,
                        indicatorColor = Color(0xFF2B2B2B),
                        unselectedIconColor = Color.LightGray,
                        unselectedTextColor = Color.LightGray
                    )
                )
                NavigationBarItem(
                    selected = currentRoute == BottomRoute.SETTINGS,
                    onClick = { currentRoute = BottomRoute.SETTINGS },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Ayarlar") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AccentOrange,
                        selectedTextColor = AccentOrange,
                        indicatorColor = Color(0xFF2B2B2B),
                        unselectedIconColor = Color.LightGray,
                        unselectedTextColor = Color.LightGray
                    )
                )
            }
        }
    ) { innerPadding ->
        when (currentRoute) {
            BottomRoute.HOME -> {
                val kindChannels = remember(allChannels, selectedMediaKind) {
                    allChannels.filter { it.kind == selectedMediaKind }
                }

                val categories = remember(kindChannels) {
                    listOf("Tümü") + kindChannels.map { it.group }.distinct()
                }

                LaunchedEffect(selectedMediaKind, categories) {
                    if (selectedCategoryFilter !in categories) {
                        selectedCategoryFilter = "Tümü"
                    }
                }

                val filteredAndSorted = remember(kindChannels, searchQuery, selectedCategoryFilter, sortOrder) {
                    val base = kindChannels.filter { ch ->
                        val matchesCat = selectedCategoryFilter == "Tümü" || ch.group == selectedCategoryFilter
                        val matchesQuery = searchQuery.isBlank() ||
                            ch.name.contains(searchQuery, ignoreCase = true) ||
                            ch.group.contains(searchQuery, ignoreCase = true)
                        matchesCat && matchesQuery
                    }
                    when (sortOrder) {
                        SortOrder.DEFAULT -> base
                        SortOrder.AZ -> base.sortedBy { it.name.lowercase() }
                        SortOrder.FAVORITES_FIRST -> base.sortedByDescending { it.isFavorite }
                    }
                }

                val groupedChannels = remember(filteredAndSorted) {
                    filteredAndSorted.groupBy { it.group }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    // Üst Medya Modu Seçici: Canlı TV | Filmler | Diziler
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MediaKind.entries.forEach { kind ->
                            val isSelected = selectedMediaKind == kind
                            val count = allChannels.count { it.kind == kind }
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedMediaKind = kind
                                    selectedCategoryFilter = "Tümü"
                                },
                                label = {
                                    Text(
                                        text = "${kind.label} ($count)",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AccentOrange,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    // Üst Araç Çubuğu: Arama + Kategori + Düzenle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        IconButton(
                            onClick = {
                                isSearchActive = !isSearchActive
                                if (!isSearchActive) searchQuery = ""
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = "Ara",
                                tint = Color.Black,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        if (isSearchActive) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("${selectedMediaKind.label} içinde ara...") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(24.dp)
                            )
                        } else {
                            Box {
                                Surface(
                                    onClick = { showCategoryMenu = true },
                                    shape = RoundedCornerShape(24.dp),
                                    color = ChipDarkBg
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Sort,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = if (selectedCategoryFilter == "Tümü") "Kategori" else selectedCategoryFilter,
                                            color = Color.White,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = showCategoryMenu,
                                    onDismissRequest = { showCategoryMenu = false }
                                ) {
                                    categories.forEach { cat ->
                                        DropdownMenuItem(
                                            text = { Text(cat) },
                                            onClick = {
                                                selectedCategoryFilter = cat
                                                showCategoryMenu = false
                                            }
                                        )
                                    }
                                }
                            }

                            Box {
                                Surface(
                                    onClick = { showSortMenu = true },
                                    shape = RoundedCornerShape(24.dp),
                                    color = ChipDarkBg
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Sort,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "Düzenle",
                                            color = Color.White,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 14.sp
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = showSortMenu,
                                    onDismissRequest = { showSortMenu = false }
                                ) {
                                    SortOrder.entries.forEach { order ->
                                        DropdownMenuItem(
                                            text = { Text(order.label) },
                                            onClick = {
                                                sortOrder = order
                                                showSortMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFFE6E6E6))

                    if (activeChannel != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .background(Color.Black)
                        ) {
                            AndroidView(
                                factory = { ctx ->
                                    PlayerView(ctx).apply {
                                        player = exoPlayer
                                        useController = true
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                            Row(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                IconButton(
                                    onClick = { isPlayerFullScreen = true },
                                    modifier = Modifier
                                        .size(34.dp)
                                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                ) {
                                    Icon(Icons.Default.Fullscreen, contentDescription = "Tam Ekran", tint = Color.White)
                                }
                                IconButton(
                                    onClick = { activeChannel = null },
                                    modifier = Modifier
                                        .size(34.dp)
                                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Kapat", tint = Color.White)
                                }
                            }
                        }
                    }

                    if (groupedChannels.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${selectedMediaKind.label} bölümünde içerik bulunamadı. Listelerim sekmesinden Xtream veya M3U ekleyebilirsiniz.",
                                color = Color.DarkGray,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(24.dp)
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            groupedChannels.forEach { (categoryName, channelList) ->
                                item(key = "${selectedMediaKind.name}-$categoryName") {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            text = categoryName,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF1A1A1A),
                                            modifier = Modifier.padding(horizontal = 16.dp)
                                        )

                                        LazyRow(
                                            contentPadding = PaddingValues(horizontal = 16.dp),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            items(channelList, key = { it.id }) { channel ->
                                                MediaShelfCard(
                                                    channel = channel,
                                                    isSelected = activeChannel?.id == channel.id,
                                                    onClick = { handleCardClick(channel) },
                                                    onToggleFavorite = { toggleFavorite(channel.id) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            BottomRoute.PLAYLISTS -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            text = "Listelerim",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1A1A1A)
                        )
                    }

                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Yeni Liste / Hesap Ekle",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    ImportMode.entries.forEach { mode ->
                                        FilterChip(
                                            selected = importMode == mode,
                                            onClick = {
                                                importMode = mode
                                                statusText = null
                                            },
                                            label = { Text(mode.label, maxLines = 1, fontSize = 12.sp) }
                                        )
                                    }
                                }

                                if (importMode != ImportMode.SINGLE) {
                                    OutlinedTextField(
                                        value = listNameInput,
                                        onValueChange = { listNameInput = it },
                                        label = { Text("Liste / Hesap Adı (İsteğe bağlı)") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                // Xtream kendi kategorilerini otomatik ayırır; diğer modlar için hedef bölüm seçici
                                if (importMode != ImportMode.XTREAM) {
                                    Text(
                                        text = "Hedef Bölüm (Otomatik veya Seçili)",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.DarkGray
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        MediaKind.entries.forEach { kind ->
                                            FilterChip(
                                                selected = defaultImportKind == kind,
                                                onClick = { defaultImportKind = kind },
                                                label = { Text(kind.label, maxLines = 1, fontSize = 12.sp) }
                                            )
                                        }
                                    }
                                }

                                when (importMode) {
                                    ImportMode.XTREAM -> {
                                        OutlinedTextField(
                                            value = xtreamServerInput,
                                            onValueChange = { xtreamServerInput = it },
                                            label = { Text("Sunucu Adresi (http://ornek.com:8080)") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        OutlinedTextField(
                                            value = xtreamUserInput,
                                            onValueChange = { xtreamUserInput = it },
                                            label = { Text("Kullanıcı Adı") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        OutlinedTextField(
                                            value = xtreamPassInput,
                                            onValueChange = { xtreamPassInput = it },
                                            label = { Text("Şifre") },
                                            singleLine = true,
                                            visualTransformation = if (showXtreamPassword) {
                                                VisualTransformation.None
                                            } else {
                                                PasswordVisualTransformation()
                                            },
                                            trailingIcon = {
                                                IconButton(onClick = { showXtreamPassword = !showXtreamPassword }) {
                                                    Icon(
                                                        imageVector = if (showXtreamPassword) {
                                                            Icons.Default.VisibilityOff
                                                        } else {
                                                            Icons.Default.Visibility
                                                        },
                                                        contentDescription = "Şifre Göster"
                                                    )
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Button(
                                            onClick = {
                                                val creds = XtreamCredentials(
                                                    serverUrl = normalizeServerUrl(xtreamServerInput),
                                                    username = xtreamUserInput.trim(),
                                                    password = xtreamPassInput.trim()
                                                )
                                                scope.launch {
                                                    isLoading = true
                                                    statusText = "Canlı TV, Filmler ve Diziler çekiliyor..."
                                                    try {
                                                        val imported = withContext(Dispatchers.IO) {
                                                            fetchAllXtreamMedia(creds)
                                                        }
                                                        val title = listNameInput.trim().ifEmpty {
                                                            "Xtream (${creds.username})"
                                                        }
                                                        addImportedChannels(
                                                            newChannels = imported,
                                                            playlistTitle = title,
                                                            sourceInfo = creds.serverUrl
                                                        )
                                                        listNameInput = ""
                                                        selectedMediaKind = MediaKind.LIVE
                                                        currentRoute = BottomRoute.HOME
                                                    } catch (e: Exception) {
                                                        statusText = "Xtream girişi başarısız: ${e.localizedMessage ?: "Bağlantı veya hesap hatası"}"
                                                    } finally {
                                                        isLoading = false
                                                    }
                                                }
                                            },
                                            enabled = !isLoading &&
                                                xtreamServerInput.isNotBlank() &&
                                                xtreamUserInput.isNotBlank() &&
                                                xtreamPassInput.isNotBlank(),
                                            colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(48.dp)
                                        ) {
                                            Icon(Icons.Default.Login, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Xtream Bağlan ve Çek")
                                        }
                                    }

                                    ImportMode.URL -> {
                                        OutlinedTextField(
                                            value = playlistUrlInput,
                                            onValueChange = { playlistUrlInput = it },
                                            label = { Text("M3U / M3U8 Bağlantısı (URL)") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Button(
                                            onClick = {
                                                val urlToLoad = playlistUrlInput.trim()
                                                scope.launch {
                                                    isLoading = true
                                                    statusText = null
                                                    try {
                                                        val raw = withContext(Dispatchers.IO) {
                                                            downloadText(urlToLoad)
                                                        }
                                                        val parsed = parsePlaylistText(
                                                            rawText = raw,
                                                            defaultGroup = "Diğer",
                                                            preferredKind = defaultImportKind
                                                        )
                                                        addImportedChannels(
                                                            newChannels = parsed,
                                                            playlistTitle = listNameInput.trim().ifEmpty { "URL Listesi" },
                                                            sourceInfo = urlToLoad
                                                        )
                                                        listNameInput = ""
                                                        playlistUrlInput = ""
                                                        selectedMediaKind = defaultImportKind
                                                        currentRoute = BottomRoute.HOME
                                                    } catch (e: Exception) {
                                                        statusText = "Yüklenemedi: ${e.localizedMessage ?: "Bağlantı hatası"}"
                                                    } finally {
                                                        isLoading = false
                                                    }
                                                }
                                            },
                                            enabled = !isLoading && playlistUrlInput.isNotBlank(),
                                            colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(48.dp)
                                        ) {
                                            Icon(Icons.Default.Link, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Kaydet ve Yükle")
                                        }
                                    }

                                    ImportMode.FILE -> {
                                        Button(
                                            onClick = { m3uFileLauncher.launch(arrayOf("*/*")) },
                                            enabled = !isLoading,
                                            colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(48.dp)
                                        ) {
                                            Icon(Icons.Default.FolderOpen, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Cihazdan .m3u / .m3u8 Seç")
                                        }
                                    }

                                    ImportMode.SINGLE -> {
                                        OutlinedTextField(
                                            value = singleChannelName,
                                            onValueChange = { singleChannelName = it },
                                            label = { Text("İçerik / Kanal Adı") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        OutlinedTextField(
                                            value = singleStreamUrl,
                                            onValueChange = { singleStreamUrl = it },
                                            label = { Text("Yayın Bağlantısı (.m3u8 / MP4 / Akış)") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        OutlinedTextField(
                                            value = singleGroupName,
                                            onValueChange = { singleGroupName = it },
                                            label = { Text("Kategori / Grup") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Button(
                                            onClick = {
                                                val cleanUrl = singleStreamUrl.trim()
                                                val cleanName = singleChannelName.trim().ifEmpty { "Özel İçerik" }
                                                val cleanGroup = singleGroupName.trim().ifEmpty { "Genel" }
                                                val singleItem = ChannelEntry(
                                                    id = "single-${System.currentTimeMillis()}",
                                                    name = cleanName,
                                                    url = cleanUrl,
                                                    group = cleanGroup,
                                                    kind = defaultImportKind
                                                )
                                                activeChannel = singleItem
                                                addImportedChannels(
                                                    newChannels = listOf(singleItem),
                                                    playlistTitle = cleanName,
                                                    sourceInfo = "Tekil Yayın (${defaultImportKind.label})"
                                                )
                                                singleChannelName = ""
                                                singleStreamUrl = ""
                                                selectedMediaKind = defaultImportKind
                                                currentRoute = BottomRoute.HOME
                                            },
                                            enabled = !isLoading && singleStreamUrl.isNotBlank(),
                                            colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(48.dp)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("İçeriği Ekle")
                                        }
                                    }
                                }

                                if (isLoading) {
                                    LinearProgressIndicator(
                                        modifier = Modifier.fillMaxWidth(),
                                        color = AccentOrange
                                    )
                                }

                                if (statusText != null) {
                                    Text(
                                        text = statusText!!,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AccentOrange
                                    )
                                }
                            }
                        }
                    }

                    items(playlists, key = { it.id }) { pl ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { currentRoute = BottomRoute.HOME },
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(pl.title, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "${pl.channels.size} içerik • ${pl.sourceInfo}",
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            BottomRoute.SETTINGS -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Ayarlar",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1A1A1A)
                    )
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("İçerik seçilince otomatik oynat")
                            Switch(
                                checked = autoPlayOnSelect,
                                onCheckedChange = { autoPlayOnSelect = it }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaShelfCard(
    channel: ChannelEntry,
    isSelected: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val shelfIcon = when (channel.kind) {
        MediaKind.LIVE -> Icons.Default.LiveTv
        MediaKind.MOVIES -> Icons.Default.Movie
        MediaKind.SERIES -> Icons.Default.Tv
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = CardDarkBg,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) AccentOrange else AccentOrange.copy(alpha = 0.55f)
        ),
        modifier = Modifier
            .width(155.dp)
            .height(118.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(34.dp)
            ) {
                Icon(
                    imageVector = if (channel.isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "Favori",
                    tint = if (channel.isFavorite) AccentOrange else Color.LightGray,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Spacer(modifier = Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1A1A1A)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = shelfIcon,
                        contentDescription = null,
                        tint = Color.LightGray,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Text(
                    text = channel.name,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

private fun normalizeServerUrl(raw: String): String {
    var trimmed = raw.trim().trimEnd('/')
    if (!trimmed.startsWith("http://", ignoreCase = true) &&
        !trimmed.startsWith("https://", ignoreCase = true)
    ) {
        trimmed = "http://$trimmed"
    }
    return trimmed
}

private fun buildPlayerApiUrl(creds: XtreamCredentials, action: String? = null, extra: String = ""): String {
    val u = URLEncoder.encode(creds.username, "UTF-8")
    val p = URLEncoder.encode(creds.password, "UTF-8")
    val base = "${creds.serverUrl}/player_api.php?username=$u&password=$p"
    return if (action.isNullOrBlank()) base else "$base&action=$action$extra"
}

private fun fetchAllXtreamMedia(creds: XtreamCredentials): List<ChannelEntry> {
    // 1. Kimlik doğrulama kontrolü
    val authJson = JSONObject(downloadText(buildPlayerApiUrl(creds)))
    val userInfo = authJson.optJSONObject("user_info")
    val authStatus = userInfo?.optInt("auth", 0) ?: 0
    if (authStatus == 0) {
        throw IllegalStateException("Kullanıcı adı veya şifre geçersiz.")
    }

    val results = mutableListOf<ChannelEntry>()

    // 2. Canlı TV Kategorileri ve Kanalları
    val liveCatMap = parseCategoryMap(safeDownload(buildPlayerApiUrl(creds, "get_live_categories")))
    val liveArray = JSONArray(safeDownload(buildPlayerApiUrl(creds, "get_live_streams"), "[]"))
    for (i in 0 until liveArray.length()) {
        val obj = liveArray.optJSONObject(i) ?: continue
        val streamId = obj.optString("stream_id").ifBlank { obj.optInt("stream_id").toString() }
        val name = obj.optString("name", "Canlı Kanal").trim()
        val catId = obj.optString("category_id")
        val group = liveCatMap[catId] ?: "Canlı TV"
        val streamUrl = "${creds.serverUrl}/live/${creds.username}/${creds.password}/$streamId.m3u8"
        results.add(
            ChannelEntry(
                id = "xt-live-$streamId",
                name = name,
                url = streamUrl,
                group = group,
                kind = MediaKind.LIVE
            )
        )
    }

    // 3. Filmler (VOD) Kategorileri ve Akışları
    val vodCatMap = parseCategoryMap(safeDownload(buildPlayerApiUrl(creds, "get_vod_categories")))
    val vodArray = JSONArray(safeDownload(buildPlayerApiUrl(creds, "get_vod_streams"), "[]"))
    for (i in 0 until vodArray.length()) {
        val obj = vodArray.optJSONObject(i) ?: continue
        val streamId = obj.optString("stream_id").ifBlank { obj.optInt("stream_id").toString() }
        val name = obj.optString("name", "Film").trim()
        val catId = obj.optString("category_id")
        val ext = obj.optString("container_extension", "mp4").ifBlank { "mp4" }
        val group = vodCatMap[catId] ?: "Filmler"
        val movieUrl = "${creds.serverUrl}/movie/${creds.username}/${creds.password}/$streamId.$ext"
        results.add(
            ChannelEntry(
                id = "xt-vod-$streamId",
                name = name,
                url = movieUrl,
                group = group,
                kind = MediaKind.MOVIES
            )
        )
    }

    // 4. Diziler Kategorileri ve Dizi Listesi
    val seriesCatMap = parseCategoryMap(safeDownload(buildPlayerApiUrl(creds, "get_series_categories")))
    val seriesArray = JSONArray(safeDownload(buildPlayerApiUrl(creds, "get_series"), "[]"))
    for (i in 0 until seriesArray.length()) {
        val obj = seriesArray.optJSONObject(i) ?: continue
        val seriesId = obj.optString("series_id").ifBlank { obj.optInt("series_id").toString() }
        val name = obj.optString("name", "Dizi").trim()
        val catId = obj.optString("category_id")
        val group = seriesCatMap[catId] ?: "Diziler"
        results.add(
            ChannelEntry(
                id = "xt-series-$seriesId",
                name = name,
                url = "",
                group = group,
                kind = MediaKind.SERIES,
                xtreamSeriesId = seriesId,
                xtreamCreds = creds
            )
        )
    }

    return results
}

private fun fetchXtreamSeriesEpisodes(creds: XtreamCredentials, seriesId: String): List<SeriesEpisodeItem> {
    val raw = downloadText(buildPlayerApiUrl(creds, "get_series_info", "&series_id=$seriesId"))
    val root = JSONObject(raw)
    val episodesObj = root.optJSONObject("episodes") ?: return emptyList()
    val list = mutableListOf<SeriesEpisodeItem>()

    val seasonKeys = episodesObj.keys()
    while (seasonKeys.hasNext()) {
        val seasonKey = seasonKeys.next()
        val seasonNum = seasonKey.toIntOrNull() ?: 1
        val epArray = episodesObj.optJSONArray(seasonKey) ?: continue
        for (i in 0 until epArray.length()) {
            val epObj = epArray.optJSONObject(i) ?: continue
            val epId = epObj.optString("id")
            if (epId.isBlank()) continue
            val epNum = epObj.optInt("episode_num", i + 1)
            val title = epObj.optString("title", "Bölüm $epNum").trim()
            val ext = epObj.optString("container_extension", "mp4").ifBlank { "mp4" }
            val epUrl = "${creds.serverUrl}/series/${creds.username}/${creds.password}/$epId.$ext"
            list.add(
                SeriesEpisodeItem(
                    id = epId,
                    season = seasonNum,
                    episodeNum = epNum,
                    title = title,
                    streamUrl = epUrl
                )
            )
        }
    }
    return list.sortedWith(compareBy({ it.season }, { it.episodeNum }))
}

private fun parseCategoryMap(rawJson: String): Map<String, String> {
    val map = mutableMapOf<String, String>()
    val arr = try {
        JSONArray(rawJson)
    } catch (_: Exception) {
        return map
    }
    for (i in 0 until arr.length()) {
        val obj = arr.optJSONObject(i) ?: continue
        val id = obj.optString("category_id")
        val name = obj.optString("category_name")
        if (id.isNotBlank() && name.isNotBlank()) {
            map[id] = name
        }
    }
    return map
}

private fun safeDownload(url: String, fallback: String = "[]"): String {
    return try {
        downloadText(url)
    } catch (_: Exception) {
        fallback
    }
}

private fun downloadText(urlString: String): String {
    val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15000
        readTimeout = 20000
        requestMethod = "GET"
        setRequestProperty("User-Agent", "JOE-Player/1.0")
    }
    return try {
        conn.inputStream.bufferedReader().use { it.readText() }
    } finally {
        conn.disconnect()
    }
}

private fun detectMediaKind(group: String, title: String, fallback: MediaKind): MediaKind {
    val combined = "${group.lowercase()} ${title.lowercase()}"
    val seriesKeywords = listOf("dizi", "series", "sezon", "season", "bölüm", "episode", "s01", "s02", "s03", "e01")
    if (seriesKeywords.any { combined.contains(it) }) {
        return MediaKind.SERIES
    }
    val movieKeywords = listOf("film", "movie", "movies", "sinema", "vod", "4k film", "hd film")
    if (movieKeywords.any { combined.contains(it) }) {
        return MediaKind.MOVIES
    }
    return fallback
}

private fun parsePlaylistText(
    rawText: String,
    defaultGroup: String,
    preferredKind: MediaKind
): List<ChannelEntry> {
    val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val items = mutableListOf<ChannelEntry>()
    var nextTitle: String? = null
    var nextGroup = defaultGroup

    for (line in lines) {
        if (line.startsWith("#EXTINF:", ignoreCase = true)) {
            val groupRegex = Regex("""group-title="([^"]*)"""", RegexOption.IGNORE_CASE).find(line)
            nextGroup = groupRegex?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() } ?: defaultGroup
            val commaIdx = line.lastIndexOf(',')
            nextTitle = if (commaIdx != -1 && commaIdx + 1 < line.length) {
                line.substring(commaIdx + 1).trim()
            } else {
                null
            }
        } else if (!line.startsWith("#")) {
            val resolvedName = nextTitle ?: line.substringAfterLast('/').substringBefore('?').ifBlank { "İçerik" }
            val resolvedKind = detectMediaKind(
                group = nextGroup,
                title = resolvedName,
                fallback = preferredKind
            )
            items.add(
                ChannelEntry(
                    id = "item-${items.size}-${line.hashCode()}",
                    name = resolvedName,
                    url = line,
                    group = nextGroup,
                    kind = resolvedKind
                )
            )
            nextTitle = null
            nextGroup = defaultGroup
        }
    }
    return items
}
