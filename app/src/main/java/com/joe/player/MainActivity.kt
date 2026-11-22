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
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class ChannelEntry(
    val id: String,
    val name: String,
    val url: String,
    val group: String = "Diğer",
    val isFavorite: Boolean = false
)

data class SavedPlaylist(
    val id: String,
    val title: String,
    val sourceInfo: String,
    val channels: List<ChannelEntry>
)

private enum class BottomRoute(val label: String) {
    LIVE_TV("Canlı TV"),
    PLAYLISTS("Listelerim"),
    SETTINGS("Ayarlar")
}

private enum class ImportMode(val label: String) {
    URL("URL Liste"),
    FILE("Dosya Seç"),
    SINGLE("Tek Kanal")
}

private enum class SortOrder(val label: String) {
    DEFAULT("Varsayılan Sıra"),
    AZ("İsme Göre (A-Z)"),
    FAVORITES_FIRST("Önce Favoriler")
}

// Görseldeki renk paleti
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

    var currentRoute by remember { mutableStateOf(BottomRoute.LIVE_TV) }
    var playlists by remember { mutableStateOf<List<SavedPlaylist>>(emptyList()) }
    var allChannels by remember {
        mutableStateOf(
            listOf(
                ChannelEntry(
                    id = "demo-1",
                    name = "Test Yayın 1 | FHD",
                    url = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
                    group = "Demo Kanallar"
                ),
                ChannelEntry(
                    id = "demo-2",
                    name = "Test Yayın 2 | 4K UHD",
                    url = "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8",
                    group = "Demo Kanallar"
                )
            )
        )
    }

    var activeChannel by remember { mutableStateOf<ChannelEntry?>(null) }
    var isPlayerFullScreen by remember { mutableStateOf(false) }

    // Canlı TV Arama, Kategori ve Sıralama durumları
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("Tümü") }
    var showCategoryMenu by remember { mutableStateOf(false) }
    var sortOrder by remember { mutableStateOf(SortOrder.DEFAULT) }
    var showSortMenu by remember { mutableStateOf(false) }

    // Listelerim sekmesi durumları
    var importMode by remember { mutableStateOf(ImportMode.URL) }
    var listNameInput by remember { mutableStateOf("") }
    var playlistUrlInput by remember { mutableStateOf("") }
    var singleChannelName by remember { mutableStateOf("") }
    var singleStreamUrl by remember { mutableStateOf("") }
    var singleGroupName by remember { mutableStateOf("Spor") }

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
            statusText = "Geçerli kanal bulunamadı."
            return
        }
        val existingUrls = allChannels.map { it.url }.toSet()
        val uniqueChannels = newChannels.filter { it.url !in existingUrls }
        allChannels = uniqueChannels + allChannels

        val newPlaylist = SavedPlaylist(
            id = "pl-${System.currentTimeMillis()}-${playlists.size}",
            title = playlistTitle.ifBlank { "Liste ${playlists.size + 1}" },
            sourceInfo = sourceInfo,
            channels = newChannels
        )
        playlists = listOf(newPlaylist) + playlists
        statusText = "${newChannels.size} kanal işlendi (${uniqueChannels.size} yeni eklendi)."
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
                    val parsed = parsePlaylistText(content, defaultGroup = "Diğer")
                    val title = listNameInput.trim().ifEmpty { "Yerel Dosya Listesi" }
                    addImportedChannels(parsed, title, "Yerel .m3u / .m3u8 dosyası")
                    listNameInput = ""
                } catch (e: Exception) {
                    statusText = "Dosya okunamadı: ${e.localizedMessage ?: "Hata"}"
                } finally {
                    isLoading = false
                }
            }
        }
    }

    // Tam ekran oynatıcı görünümü
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

    Scaffold(
        containerColor = LightPageBg,
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF1A1A1A),
                contentColor = Color.White
            ) {
                NavigationBarItem(
                    selected = currentRoute == BottomRoute.LIVE_TV,
                    onClick = { currentRoute = BottomRoute.LIVE_TV },
                    icon = { Icon(Icons.Default.LiveTv, contentDescription = null) },
                    label = { Text("Canlı TV") },
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
            BottomRoute.LIVE_TV -> {
                val categories = remember(allChannels) {
                    listOf("Tümü") + allChannels.map { it.group }.distinct()
                }

                val filteredAndSorted = remember(allChannels, searchQuery, selectedCategoryFilter, sortOrder) {
                    val base = allChannels.filter { ch ->
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
                    // Üst Araç Çubuğu: Arama ikonu + Kategori + Düzenle hap butonları
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
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
                                placeholder = { Text("Kanal ara...") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(24.dp)
                            )
                        } else {
                            // Kategori Butonu
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

                            // Düzenle (Sıralama) Butonu
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

                    // Aktif yayın varsa üstte mini oynatıcı şeridi
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

                    // Kategori Rafları ve Yatay Kanal Kartları
                    if (groupedChannels.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Kanal bulunamadı. Listelerim sekmesinden M3U yükleyebilirsiniz.",
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
                                item(key = categoryName) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        // Kategori Başlığı
                                        Text(
                                            text = categoryName,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF1A1A1A),
                                            modifier = Modifier.padding(horizontal = 16.dp)
                                        )

                                        // Yatay Kaydırılabilir Kanal Kartları
                                        LazyRow(
                                            contentPadding = PaddingValues(horizontal = 16.dp),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            items(channelList, key = { it.id }) { channel ->
                                                LiveTvChannelCard(
                                                    channel = channel,
                                                    isSelected = activeChannel?.id == channel.id,
                                                    onClick = { activeChannel = channel },
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
                                    text = "Yeni Liste Ekle (M3U / M3U8)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    ImportMode.entries.forEach { mode ->
                                        FilterChip(
                                            selected = importMode == mode,
                                            onClick = {
                                                importMode = mode
                                                statusText = null
                                            },
                                            label = { Text(mode.label, maxLines = 1) }
                                        )
                                    }
                                }

                                if (importMode != ImportMode.SINGLE) {
                                    OutlinedTextField(
                                        value = listNameInput,
                                        onValueChange = { listNameInput = it },
                                        label = { Text("Liste Adı (İsteğe bağlı)") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                when (importMode) {
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
                                                            downloadPlaylist(urlToLoad)
                                                        }
                                                        val parsed = parsePlaylistText(raw, defaultGroup = "Diğer")
                                                        addImportedChannels(
                                                            newChannels = parsed,
                                                            playlistTitle = listNameInput.trim().ifEmpty { "URL Listesi" },
                                                            sourceInfo = urlToLoad
                                                        )
                                                        listNameInput = ""
                                                        playlistUrlInput = ""
                                                        currentRoute = BottomRoute.LIVE_TV
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
                                            label = { Text("Kanal Adı") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        OutlinedTextField(
                                            value = singleStreamUrl,
                                            onValueChange = { singleStreamUrl = it },
                                            label = { Text("Yayın Bağlantısı (.m3u8 / Akış URL)") },
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
                                                val cleanName = singleChannelName.trim().ifEmpty { "Özel Kanal" }
                                                val cleanGroup = singleGroupName.trim().ifEmpty { "Diğer" }
                                                val singleItem = ChannelEntry(
                                                    id = "single-${System.currentTimeMillis()}",
                                                    name = cleanName,
                                                    url = cleanUrl,
                                                    group = cleanGroup
                                                )
                                                activeChannel = singleItem
                                                addImportedChannels(
                                                    newChannels = listOf(singleItem),
                                                    playlistTitle = cleanName,
                                                    sourceInfo = "Tekil Yayın Bağlantısı"
                                                )
                                                singleChannelName = ""
                                                singleStreamUrl = ""
                                                currentRoute = BottomRoute.LIVE_TV
                                            },
                                            enabled = !isLoading && singleStreamUrl.isNotBlank(),
                                            colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(48.dp)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Kanalı Ekle")
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
                                .clickable { currentRoute = BottomRoute.LIVE_TV },
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(pl.title, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "${pl.channels.size} kanal • ${pl.sourceInfo}",
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
                            Text("Kanal seçilince otomatik oynat")
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
private fun LiveTvChannelCard(
    channel: ChannelEntry,
    isSelected: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
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
            // Sağ üst Favori Kalp Butonu
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

            // Orta Logo Kutusu ve Alt Kanal İsmi
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
                        imageVector = Icons.Default.LiveTv,
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

private fun downloadPlaylist(urlString: String): String {
    val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15000
        readTimeout = 15000
        requestMethod = "GET"
        setRequestProperty("User-Agent", "JOE-Player/1.0")
    }
    return try {
        conn.inputStream.bufferedReader().use { it.readText() }
    } finally {
        conn.disconnect()
    }
}

private fun parsePlaylistText(rawText: String, defaultGroup: String): List<ChannelEntry> {
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
            val resolvedName = nextTitle ?: line.substringAfterLast('/').substringBefore('?').ifBlank { "Kanal" }
            items.add(
                ChannelEntry(
                    id = "ch-${items.size}-${line.hashCode()}",
                    name = resolvedName,
                    url = line,
                    group = nextGroup
                )
            )
            nextTitle = null
            nextGroup = defaultGroup
        }
    }
    return items
}
