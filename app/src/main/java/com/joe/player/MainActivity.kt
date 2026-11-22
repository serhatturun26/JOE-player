package com.joe.player

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    val group: String = "Genel"
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
    URL("URL Liste"),
    FILE("Dosya Seç"),
    SINGLE("Tek Kanal")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
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
    var playlists by remember { mutableStateOf<List<SavedPlaylist>>(emptyList()) }
    var allChannels by remember { mutableStateOf<List<ChannelEntry>>(emptyList()) }
    var activeChannel by remember { mutableStateOf<ChannelEntry?>(null) }

    var importMode by remember { mutableStateOf(ImportMode.URL) }
    var listNameInput by remember { mutableStateOf("") }
    var playlistUrlInput by remember { mutableStateOf("") }
    var singleChannelName by remember { mutableStateOf("") }
    var singleStreamUrl by remember { mutableStateOf("") }
    var singleGroupName by remember { mutableStateOf("Canlı TV") }

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

        if (activeChannel == null && allChannels.isNotEmpty()) {
            activeChannel = allChannels.first()
        }
        statusText = "${newChannels.size} kanal işlendi (${uniqueChannels.size} yeni kanal eklendi)."
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
                    val parsed = parsePlaylistText(content, defaultGroup = "Yerel M3U")
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

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentRoute == BottomRoute.HOME,
                    onClick = { currentRoute = BottomRoute.HOME },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("Ana Sayfa") }
                )
                NavigationBarItem(
                    selected = currentRoute == BottomRoute.PLAYLISTS,
                    onClick = { currentRoute = BottomRoute.PLAYLISTS },
                    icon = { Icon(Icons.Default.QueueMusic, contentDescription = null) },
                    label = { Text("Listelerim") }
                )
                NavigationBarItem(
                    selected = currentRoute == BottomRoute.SETTINGS,
                    onClick = { currentRoute = BottomRoute.SETTINGS },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Ayarlar") }
                )
            }
        }
    ) { innerPadding ->
        when (currentRoute) {
            BottomRoute.HOME -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Ana Sayfa",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
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
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = activeChannel?.name ?: "Henüz kanal seçilmedi",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = activeChannel?.url ?: "Listelerim sekmesinden liste veya kanal yükleyin.",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(allChannels, key = { it.id }) { ch ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { activeChannel = ch }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(ch.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(ch.group, style = MaterialTheme.typography.labelSmall)
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
                            fontWeight = FontWeight.Bold
                        )
                    }

                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
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
                                                        val parsed = parsePlaylistText(raw, defaultGroup = "URL Liste")
                                                        addImportedChannels(
                                                            newChannels = parsed,
                                                            playlistTitle = listNameInput.trim().ifEmpty { "URL Listesi" },
                                                            sourceInfo = urlToLoad
                                                        )
                                                        listNameInput = ""
                                                        playlistUrlInput = ""
                                                    } catch (e: Exception) {
                                                        statusText = "Yüklenemedi: ${e.localizedMessage ?: "Bağlantı hatası"}"
                                                    } finally {
                                                        isLoading = false
                                                    }
                                                }
                                            },
                                            enabled = !isLoading && playlistUrlInput.isNotBlank(),
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
                                            label = { Text("Grup (İsteğe bağlı)") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Button(
                                            onClick = {
                                                val cleanUrl = singleStreamUrl.trim()
                                                val cleanName = singleChannelName.trim().ifEmpty { "Özel Kanal" }
                                                val cleanGroup = singleGroupName.trim().ifEmpty { "Canlı TV" }
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
                                            },
                                            enabled = !isLoading && singleStreamUrl.isNotBlank(),
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
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                }

                                if (statusText != null) {
                                    Text(
                                        text = statusText!!,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    items(playlists, key = { it.id }) { pl ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    pl.channels.firstOrNull()?.let { firstCh ->
                                        activeChannel = firstCh
                                        currentRoute = BottomRoute.HOME
                                    }
                                }
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
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
