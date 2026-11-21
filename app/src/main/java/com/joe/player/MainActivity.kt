package com.joe.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

data class Channel(
    val id: String,
    val name: String,
    val streamUrl: String,
    val category: String = "Genel",
    val isFavorite: Boolean = false
)

data class PlaylistSource(
    val id: String,
    val title: String,
    val url: String,
    val channelCount: Int
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JoeApp()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoeApp() {
    var darkTheme by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableStateOf(0) } // 0: Ana Sayfa, 1: Listelerim, 2: Ayarlar
    var playlists by remember { mutableStateOf(listOf<PlaylistSource>()) }
    var channels by remember { mutableStateOf(listOf<Channel>()) }
    var lastWatched by remember { mutableStateOf<Channel?>(null) }
    var activeChannel by remember { mutableStateOf<Channel?>(null) }
    var selectedCategory by remember { mutableStateOf("Tümü") }

    val colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()

    MaterialTheme(colorScheme = colorScheme) {
        if (activeChannel != null) {
            PlayerScreen(
                channel = activeChannel!!,
                onBack = { activeChannel = null },
                onToggleFavorite = { target ->
                    channels = channels.map {
                        if (it.id == target.id) it.copy(isFavorite = !it.isFavorite) else it
                    }
                    if (activeChannel?.id == target.id) {
                        activeChannel = activeChannel?.copy(isFavorite = !target.isFavorite)
                    }
                }
            )
        } else {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = when (selectedTab) {
                                    0 -> "JOE"
                                    1 -> "Listelerim"
                                    else -> "Ayarlar"
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }
                    )
                },
                bottomBar = {
                    NavigationBar {
                        NavigationBarItem(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            icon = { Icon(Icons.Default.Home, contentDescription = null) },
                            label = { Text("Ana Sayfa") }
                        )
                        NavigationBarItem(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            icon = { Icon(Icons.Default.PlaylistPlay, contentDescription = null) },
                            label = { Text("Listelerim") }
                        )
                        NavigationBarItem(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                            label = { Text("Ayarlar") }
                        )
                    }
                }
            ) { innerPadding ->
                Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                    when (selectedTab) {
                        0 -> HomeTab(
                            channels = channels,
                            lastWatched = lastWatched,
                            selectedCategory = selectedCategory,
                            onSelectCategory = { selectedCategory = it },
                            onOpenChannel = { ch ->
                                lastWatched = ch
                                activeChannel = ch
                            },
                            onToggleFavorite = { target ->
                                channels = channels.map {
                                    if (it.id == target.id) it.copy(isFavorite = !it.isFavorite) else it
                                }
                            },
                            onGoToPlaylists = { selectedTab = 1 }
                        )
                        1 -> PlaylistsTab(
                            playlists = playlists,
                            onAddPlaylist = { title, url, parsedChannels ->
                                val sourceId = "src_${System.currentTimeMillis()}"
                                val tagged = parsedChannels.map { it.copy(id = "${sourceId}_${it.id}") }
                                playlists = playlists + PlaylistSource(
                                    id = sourceId,
                                    title = title.ifBlank { "Liste ${playlists.size + 1}" },
                                    url = url,
                                    channelCount = tagged.size
                                )
                                channels = channels + tagged
                                selectedTab = 0
                            },
                            onDeletePlaylist = { source ->
                                playlists = playlists.filterNot { it.id == source.id }
                                channels = channels.filterNot { it.id.startsWith(source.id) }
                            }
                        )
                        2 -> SettingsTab(
                            darkTheme = darkTheme,
                            onToggleDarkTheme = { darkTheme = it }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HomeTab(
    channels: List<Channel>,
    lastWatched: Channel?,
    selectedCategory: String,
    onSelectCategory: (String) -> Unit,
    onOpenChannel: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    onGoToPlaylists: () -> Unit
) {
    if (channels.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Henüz kanal listesi eklenmedi", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Listelerim sekmesinden kendi M3U veya M3U8 bağlantınızı ekleyerek başlayın.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onGoToPlaylists) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Liste Ekle")
            }
        }
        return
    }

    val categories = listOf("Tümü", "Favoriler") + channels.map { it.category }.distinct()
    val filtered = when (selectedCategory) {
        "Tümü" -> channels
        "Favoriler" -> channels.filter { it.isFavorite }
        else -> channels.filter { it.category == selectedCategory }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        if (lastWatched != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onOpenChannel(lastWatched) }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PlayCircle, contentDescription = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Kaldığın Yerden Devam Et", style = MaterialTheme.typography.labelMedium)
                            Text(lastWatched.name, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories) { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { onSelectCategory(category) },
                        label = { Text(category) }
                    )
                }
            }
        }

        items(filtered, key = { it.id }) { channel ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onOpenChannel(channel) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(channel.name, fontWeight = FontWeight.SemiBold)
                        Text(channel.category, style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { onToggleFavorite(channel) }) {
                        Icon(
                            imageVector = if (channel.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Favori"
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistsTab(
    playlists: List<PlaylistSource>,
    onAddPlaylist: (String, String, List<Channel>) -> Unit,
    onDeletePlaylist: (PlaylistSource) -> Unit
) {
    val scope = rememberCoroutineScope()
    var titleInput by remember { mutableStateOf("") }
    var urlInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Yeni Liste Ekle (M3U / M3U8)", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = titleInput,
                        onValueChange = { titleInput = it },
                        label = { Text("Liste Adı (İsteğe bağlı)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text("M3U / M3U8 Bağlantısı (URL)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        enabled = !isLoading && urlInput.isNotBlank(),
                        onClick = {
                            isLoading = true
                            statusMessage = null
                            val targetUrl = urlInput.trim()
                            val targetTitle = titleInput.trim()
                            scope.launch {
                                try {
                                    val rawText = withContext(Dispatchers.IO) {
                                        URL(targetUrl).readText()
                                    }
                                    val parsed = parseM3uChannels(rawText)
                                    if (parsed.isEmpty()) {
                                        statusMessage = "Bağlantı okundu ancak geçerli kanal bulunamadı."
                                    } else {
                                        onAddPlaylist(targetTitle, targetUrl, parsed)
                                        titleInput = ""
                                        urlInput = ""
                                        statusMessage = "${parsed.size} kanal eklendi."
                                    }
                                } catch (_: Exception) {
                                    statusMessage = "Liste yüklenemedi. Bağlantıyı kontrol edip tekrar deneyin."
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isLoading) "Yükleniyor..." else "Kaydet ve Yükle")
                    }
                    if (statusMessage != null) {
                        Text(statusMessage!!, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        items(playlists, key = { it.id }) { source ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(source.title, fontWeight = FontWeight.SemiBold)
                        Text("${source.channelCount} kanal", style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { onDeletePlaylist(source) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Sil")
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsTab(
    darkTheme: Boolean,
    onToggleDarkTheme: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(14.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Karanlık Mod", fontWeight = FontWeight.SemiBold)
                    Text("Göz yormayan koyu tema", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = darkTheme, onCheckedChange = onToggleDarkTheme)
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Yasal Bilgilendirme", fontWeight = FontWeight.Bold)
                Text(
                    "Bu uygulama herhangi bir yayın veya kanal sağlamaz; oynatılan tüm içerik ve bağlantıların sorumluluğu kullanıcıya aittir.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun PlayerScreen(
    channel: Channel,
    onBack: () -> Unit,
    onToggleFavorite: (Channel) -> Unit
) {
    val context = LocalContext.current
    val exoPlayer = remember(channel.streamUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(channel.streamUrl))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Geri")
            }
            Text(
                text = channel.name,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { onToggleFavorite(channel) }) {
                Icon(
                    imageVector = if (channel.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "Favori"
                )
            }
        }

        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                }
            },
            modifier = Modifier.fillMaxWidth().weight(1f)
        )
    }
}

fun parseM3uChannels(content: String): List<Channel> {
    val result = mutableListOf<Channel>()
    var currentName = "Bilinmeyen Kanal"
    var currentCategory = "Genel"

    content.lines().forEachIndexed { index, rawLine ->
        val line = rawLine.trim()
        if (line.startsWith("#EXTINF")) {
            currentName = line.substringAfterLast(",", "Bilinmeyen Kanal").trim()
            currentCategory = Regex("""group-title="([^"]*)"""").find(line)?.groupValues?.get(1)
                ?.ifBlank { "Genel" } ?: "Genel"
        } else if (line.isNotEmpty() && !line.startsWith("#")) {
            result.add(
                Channel(
                    id = "${index}_${line.hashCode()}",
                    name = currentName,
                    streamUrl = line,
                    category = currentCategory
                )
            )
            currentName = "Bilinmeyen Kanal"
            currentCategory = "Genel"
        }
    }
    return result
}
