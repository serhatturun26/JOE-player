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
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
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

data class ChannelItem(
    val id: String,
    val name: String,
    val url: String,
    val group: String = "Genel",
    val isFavorite: Boolean = false
)

enum class AddOptionTab(val label: String) {
    URL("URL Liste"),
    FILE("Dosya Seç"),
    SINGLE("Tek Kanal")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                JoePlayerApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoePlayerApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var channels by remember {
        mutableStateOf(
            listOf(
                ChannelItem(
                    id = "sample-hls-1",
                    name = "Test HLS Akışı (Big Buck Bunny)",
                    url = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
                    group = "Demo",
                    isFavorite = true
                )
            )
        )
    }

    var selectedChannel by remember { mutableStateOf<ChannelItem?>(channels.firstOrNull()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(AddOptionTab.URL) }

    // Form alanları
    var playlistUrlInput by remember { mutableStateOf("") }
    var singleNameInput by remember { mutableStateOf("") }
    var singleUrlInput by remember { mutableStateOf("") }
    var singleGroupInput by remember { mutableStateOf("Canlı TV") }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build()
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    LaunchedEffect(selectedChannel?.url) {
        val url = selectedChannel?.url
        if (!url.isNullOrBlank()) {
            exoPlayer.setMediaItem(MediaItem.fromUri(url))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        }
    }

    fun mergeChannels(newItems: List<ChannelItem>, sourceLabel: String) {
        if (newItems.isEmpty()) {
            statusMessage = "$sourceLabel içinde geçerli kanal bulunamadı."
            return
        }
        val existingUrls = channels.map { it.url }.toSet()
        val uniqueNew = newItems.filter { it.url !in existingUrls }
        channels = uniqueNew + channels
        if (selectedChannel == null && channels.isNotEmpty()) {
            selectedChannel = channels.first()
        }
        statusMessage = "$sourceLabel üzerinden ${newItems.size} kanal işlendi (${uniqueNew.size} yeni eklendi)."
        showAddDialog = false
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isLoading = true
                statusMessage = null
                try {
                    val content = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            BufferedReader(InputStreamReader(stream)).readText()
                        }.orEmpty()
                    }
                    val parsed = parseM3uContent(content, defaultGroup = "Yerel Liste")
                    mergeChannels(parsed, "Dosya")
                } catch (e: Exception) {
                    statusMessage = "Dosya okunamadı: ${e.localizedMessage ?: "Bilinmeyen hata"}"
                } finally {
                    isLoading = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LiveTv, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("JOE Player", fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        statusMessage = null
                        showAddDialog = true
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Kanal veya Liste Ekle")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Oynatıcı alanı
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
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

            // Seçili kanal bilgisi ve hızlı yükleme kısayolları
            Surface(
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = selectedChannel?.name ?: "Bir kanal seçin veya yükleyin",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = selectedChannel?.let { "${it.group} • ${it.url}" }
                            ?: "URL, .m3u/.m3u8 dosyası veya tek yayın bağlantısı ekleyebilirsiniz.",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                selectedTab = AddOptionTab.URL
                                statusMessage = null
                                showAddDialog = true
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("URL", maxLines = 1)
                        }

                        OutlinedButton(
                            onClick = {
                                statusMessage = null
                                filePickerLauncher.launch(arrayOf("*/*"))
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Dosya", maxLines = 1)
                        }

                        Button(
                            onClick = {
                                selectedTab = AddOptionTab.SINGLE
                                statusMessage = null
                                showAddDialog = true
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Ekle", maxLines = 1)
                        }
                    }

                    if (statusMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = statusMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Kanal listesi
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(channels, key = { it.id }) { channel ->
                    val isPlaying = selectedChannel?.id == channel.id
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedChannel = channel },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isPlaying) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Oynat"
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = channel.name,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = channel.group,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = {
                                channels = channels.map {
                                    if (it.id == channel.id) it.copy(isFavorite = !it.isFavorite) else it
                                }
                            }) {
                                Icon(
                                    imageVector = if (channel.isFavorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                                    contentDescription = "Favori"
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { if (!isLoading) showAddDialog = false },
            title = { Text("Kanal / Liste Yükle") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AddOptionTab.entries.forEach { tab ->
                            FilterChip(
                                selected = selectedTab == tab,
                                onClick = {
                                    selectedTab = tab
                                    statusMessage = null
                                },
                                label = { Text(tab.label, maxLines = 1) }
                            )
                        }
                    }

                    when (selectedTab) {
                        AddOptionTab.URL -> {
                            OutlinedTextField(
                                value = playlistUrlInput,
                                onValueChange = { playlistUrlInput = it },
                                label = { Text("M3U / M3U8 Liste Bağlantısı") },
                                placeholder = { Text("https://.../liste.m3u8") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        AddOptionTab.FILE -> {
                            Text(
                                text = "Cihazınızdaki .m3u veya .m3u8 dosyasını seçerek kanalları içe aktarabilirsiniz.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Button(
                                onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                                enabled = !isLoading,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Cihazdan Dosya Seç")
                            }
                        }

                        AddOptionTab.SINGLE -> {
                            OutlinedTextField(
                                value = singleNameInput,
                                onValueChange = { singleNameInput = it },
                                label = { Text("Kanal Adı") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = singleUrlInput,
                                onValueChange = { singleUrlInput = it },
                                label = { Text("Yayın Bağlantısı (.m3u8 / akış)") },
                                placeholder = { Text("https://.../yayin.m3u8") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = singleGroupInput,
                                onValueChange = { singleGroupInput = it },
                                label = { Text("Kategori / Grup") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    if (isLoading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    if (statusMessage != null) {
                        Text(
                            text = statusMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                when (selectedTab) {
                    AddOptionTab.URL -> {
                        Button(
                            enabled = !isLoading && playlistUrlInput.isNotBlank(),
                            onClick = {
                                val targetUrl = playlistUrlInput.trim()
                                scope.launch {
                                    isLoading = true
                                    statusMessage = null
                                    try {
                                        val content = withContext(Dispatchers.IO) {
                                            fetchRemotePlaylist(targetUrl)
                                        }
                                        val parsed = parseM3uContent(content, defaultGroup = "Uzak Liste")
                                        mergeChannels(parsed, "URL")
                                        playlistUrlInput = ""
                                    } catch (e: Exception) {
                                        statusMessage = "Liste çekilemedi: ${e.localizedMessage ?: "Bağlantı hatası"}"
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            }
                        ) {
                            Text("Yükle")
                        }
                    }

                    AddOptionTab.SINGLE -> {
                        Button(
                            enabled = singleUrlInput.isNotBlank(),
                            onClick = {
                                val cleanUrl = singleUrlInput.trim()
                                val cleanName = singleNameInput.trim().ifEmpty { "Özel Kanal" }
                                val cleanGroup = singleGroupInput.trim().ifEmpty { "Canlı TV" }
                                val newChannel = ChannelItem(
                                    id = "custom-${System.currentTimeMillis()}",
                                    name = cleanName,
                                    url = cleanUrl,
                                    group = cleanGroup
                                )
                                selectedChannel = newChannel
                                mergeChannels(listOf(newChannel), "Tek Kanal")
                                singleNameInput = ""
                                singleUrlInput = ""
                            }
                        ) {
                            Text("Kaydet")
                        }
                    }

                    AddOptionTab.FILE -> {
                        TextButton(onClick = { if (!isLoading) showAddDialog = false }) {
                            Text("Kapat")
                        }
                    }
                }
            },
            dismissButton = {
                if (selectedTab != AddOptionTab.FILE) {
                    TextButton(
                        enabled = !isLoading,
                        onClick = { showAddDialog = false }
                    ) {
                        Text("İptal")
                    }
                }
            }
        )
    }
}

private fun fetchRemotePlaylist(urlString: String): String {
    val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15000
        readTimeout = 15000
        requestMethod = "GET"
        setRequestProperty("User-Agent", "JOE-Player/1.0")
    }
    return try {
        connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
        connection.disconnect()
    }
}

private fun parseM3uContent(content: String, defaultGroup: String): List<ChannelItem> {
    val lines = content.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val result = mutableListOf<ChannelItem>()
    var pendingTitle: String? = null
    var pendingGroup: String = defaultGroup

    for (line in lines) {
        if (line.startsWith("#EXTINF:", ignoreCase = true)) {
            val groupMatch = Regex("""group-title="([^"]*)"""", RegexOption.IGNORE_CASE).find(line)
            pendingGroup = groupMatch?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() } ?: defaultGroup
            val commaIndex = line.lastIndexOf(',')
            pendingTitle = if (commaIndex != -1 && commaIndex + 1 < line.length) {
                line.substring(commaIndex + 1).trim()
            } else {
                null
            }
        } else if (!line.startsWith("#")) {
            val name = pendingTitle ?: line.substringAfterLast('/').substringBefore('?').ifBlank { "Kanal" }
            result.add(
                ChannelItem(
                    id = "m3u-${result.size}-${line.hashCode()}",
                    name = name,
                    url = line,
                    group = pendingGroup
                )
            )
            pendingTitle = null
            pendingGroup = defaultGroup
        }
    }

    return result
}
