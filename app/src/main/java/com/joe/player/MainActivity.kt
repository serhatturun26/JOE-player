package com.joe.player

import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
import java.nio.charset.StandardCharsets

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

data class CustomHeadersConfig(
    val userAgent: String = "",
    val referer: String = "",
    val origin: String = ""
)

data class ChannelEntry(
    val id: String,
    val name: String,
    val url: String,
    val group: String = "Diğer",
    val kind: MediaKind = MediaKind.LIVE,
    val posterUrl: String? = null,
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

private enum class SourceSection(val title: String) {
    STREAM("Ağ Akışı / Video Aç"),
    M3U8("M3U8 (URL / Dosya) ile Ekle"),
    JSON("JSON (URL/Dosya) ile Ekle"),
    HTML("HTML (URL / Dosya) ile Ekle"),
    XTREAM("Xtream Hesabı ile Ekle"),
    MAC_STB("MAC/STB Portal ile Ekle"),
    MHUB("MediaHub / Açık Eklenti (mHub) ile Ekle"),
    GITHUB("Toplu Liste Ekle (GitHub)")
}

private enum class FileImportTarget {
    M3U8,
    JSON,
    HTML,
    MHUB,
    VIDEO
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
private const val PREFS_NAME = "joe_player_prefs_v7"
private const val KEY_STATE_JSON = "saved_state_json"
private const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Linux; Android 14) JOE-Player/1.0"

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

private fun applyFullScreenSystemBars(activity: ComponentActivity?, fullScreen: Boolean) {
    val act = activity ?: return
    runCatching {
        val window = act.window
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (fullScreen) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            WindowCompat.setDecorFitsSystemWindows(window, true)
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

private fun applyPlayerResizeMode(playerView: PlayerView, zoomToFill: Boolean) {
    runCatching {
        val aspectLayout = playerView.videoSurfaceView?.parent as? android.widget.FrameLayout
        if (aspectLayout != null) {
            val clazz = aspectLayout.javaClass
            val setModeMethod = clazz.getMethod("setResizeMode", Int::class.javaPrimitiveType)
            val modeValue = if (zoomToFill) 3 else 0
            setModeMethod.invoke(aspectLayout, modeValue)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JoeApp() {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val scope = rememberCoroutineScope()

    val savedSnapshot = remember { loadPersistentState(context) }

    var currentRoute by remember { mutableStateOf(BottomRoute.HOME) }
    var selectedMediaKind by remember { mutableStateOf(MediaKind.LIVE) }
    var playlists by remember { mutableStateOf(savedSnapshot.playlists) }

    var allChannels by remember {
        mutableStateOf(
            if (savedSnapshot.channels.isNotEmpty()) {
                savedSnapshot.channels
            } else {
                listOf(
                    ChannelEntry(
                        id = "demo-live-1",
                        name = "Test Yayın 1 | FHD",
                        url = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
                        group = "Demo Kanallar",
                        kind = MediaKind.LIVE
                    )
                )
            }
        )
    }

    var activeChannel by remember { mutableStateOf<ChannelEntry?>(null) }
    var isPlayerFullScreen by remember { mutableStateOf(false) }
    var showFullScreenOverlay by remember { mutableStateOf(true) }
    var fillScreenAspect by remember { mutableStateOf(true) }

    var selectedSeriesForEpisodes by remember { mutableStateOf<ChannelEntry?>(null) }
    var seriesEpisodes by remember { mutableStateOf<List<SeriesEpisodeItem>>(emptyList()) }
    var isFetchingEpisodes by remember { mutableStateOf(false) }
    var episodeErrorText by remember { mutableStateOf<String?>(null) }

    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("Tümü") }
    var showCategoryMenu by remember { mutableStateOf(false) }
    var sortOrder by remember { mutableStateOf(SortOrder.DEFAULT) }
    var showSortMenu by remember { mutableStateOf(false) }

    var expandedSection by remember { mutableStateOf<SourceSection?>(SourceSection.XTREAM) }
    var defaultImportKind by remember { mutableStateOf(MediaKind.LIVE) }
    var activeFileTarget by remember { mutableStateOf(FileImportTarget.M3U8) }

    var streamTitleInput by remember { mutableStateOf("") }
    var streamUrlInput by remember { mutableStateOf("") }
    var streamGroupInput by remember { mutableStateOf("Ağ Akışı") }

    var m3uNameInput by remember { mutableStateOf("") }
    var m3uUrlInput by remember { mutableStateOf("") }

    var jsonNameInput by remember { mutableStateOf("") }
    var jsonUrlInput by remember { mutableStateOf("") }

    var htmlNameInput by remember { mutableStateOf("") }
    var htmlUrlInput by remember { mutableStateOf("") }

    var xtreamNameInput by remember { mutableStateOf(savedSnapshot.lastListName) }
    var xtreamServerInput by remember { mutableStateOf(savedSnapshot.xtream?.serverUrl ?: "") }
    var xtreamUserInput by remember { mutableStateOf(savedSnapshot.xtream?.username ?: "") }
    var xtreamPassInput by remember { mutableStateOf(savedSnapshot.xtream?.password ?: "") }
    var showXtreamPassword by remember { mutableStateOf(false) }

    var macPortalNameInput by remember { mutableStateOf("") }
    var macPortalUrlInput by remember { mutableStateOf("") }
    var macAddressInput by remember { mutableStateOf("") }

    var mhubNameInput by remember { mutableStateOf("") }
    var mhubUrlInput by remember { mutableStateOf("") }

    var githubNameInput by remember { mutableStateOf("") }
    var githubUrlInput by remember { mutableStateOf("") }

    var userAgentInput by remember { mutableStateOf(savedSnapshot.headers.userAgent) }
    var refererInput by remember { mutableStateOf(savedSnapshot.headers.referer) }
    var originInput by remember { mutableStateOf(savedSnapshot.headers.origin) }

    var autoPlayOnSelect by remember { mutableStateOf(savedSnapshot.autoPlay) }
    var statusText by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val exoPlayer = remember { ExoPlayer.Builder(context).build() }

    fun currentHeaders(): CustomHeadersConfig {
        return CustomHeadersConfig(
            userAgent = userAgentInput.trim(),
            referer = refererInput.trim(),
            origin = originInput.trim()
        )
    }

    fun persistNow(
        channels: List<ChannelEntry> = allChannels,
        lists: List<SavedPlaylist> = playlists,
        autoPlay: Boolean = autoPlayOnSelect
    ) {
        val currentXtream = if (xtreamServerInput.isNotBlank() || xtreamUserInput.isNotBlank() || xtreamPassInput.isNotBlank()) {
            XtreamCredentials(
                serverUrl = xtreamServerInput.trim(),
                username = xtreamUserInput.trim(),
                password = xtreamPassInput.trim()
            )
        } else {
            null
        }
        val snapshot = PersistentSnapshot(
            channels = channels,
            playlists = lists,
            xtream = currentXtream,
            headers = currentHeaders(),
            lastListName = xtreamNameInput,
            autoPlay = autoPlay
        )
        scope.launch(Dispatchers.IO) {
            savePersistentState(context = context, snapshot = snapshot)
        }
    }

    LaunchedEffect(isPlayerFullScreen, activeChannel) {
        val effectiveFullScreen = isPlayerFullScreen && activeChannel != null
        runCatching {
            activity?.requestedOrientation = if (effectiveFullScreen) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
        applyFullScreenSystemBars(activity, effectiveFullScreen)
        if (effectiveFullScreen) {
            showFullScreenOverlay = true
        }
    }

    DisposableEffect(exoPlayer) {
        runCatching { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
        applyFullScreenSystemBars(activity, false)
        onDispose {
            runCatching { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
            applyFullScreenSystemBars(activity, false)
            runCatching { exoPlayer.release() }
        }
    }

    LaunchedEffect(activeChannel?.url, autoPlayOnSelect) {
        val target = activeChannel?.url
        runCatching {
            if (!target.isNullOrBlank()) {
                exoPlayer.setMediaItem(MediaItem.fromUri(target))
                exoPlayer.prepare()
                exoPlayer.playWhenReady = autoPlayOnSelect
            } else {
                exoPlayer.stop()
            }
        }
    }

    fun toggleFavorite(channelId: String) {
        val updated = allChannels.map {
            if (it.id == channelId) it.copy(isFavorite = !it.isFavorite) else it
        }
        allChannels = updated
        persistNow(channels = updated)
    }

    fun addImportedChannels(
        newChannels: List<ChannelEntry>,
        playlistTitle: String,
        sourceInfo: String,
        openPlayerFirst: ChannelEntry? = null,
        targetKind: MediaKind? = null
    ) {
        if (newChannels.isEmpty()) {
            statusText = "Geçerli içerik bulunamadı."
            return
        }
        val existingKeys = allChannels.map { "${it.kind.name}-${it.id}-${it.url}" }.toSet()
        val uniqueChannels = newChannels.filter { "${it.kind.name}-${it.id}-${it.url}" !in existingKeys }
        val mergedChannels = uniqueChannels + allChannels
        allChannels = mergedChannels

        val newPlaylist = SavedPlaylist(
            id = "pl-${System.currentTimeMillis()}-${playlists.size}",
            title = playlistTitle.ifBlank { "Liste ${playlists.size + 1}" },
            sourceInfo = sourceInfo,
            channels = newChannels
        )
        val mergedPlaylists = listOf(newPlaylist) + playlists
        playlists = mergedPlaylists

        val liveCount = newChannels.count { it.kind == MediaKind.LIVE }
        val movieCount = newChannels.count { it.kind == MediaKind.MOVIES }
        val seriesCount = newChannels.count { it.kind == MediaKind.SERIES }
        statusText = "Yüklendi: $liveCount Canlı TV, $movieCount Film, $seriesCount Dizi"
        persistNow(channels = mergedChannels, lists = mergedPlaylists)

        if (targetKind != null) {
            selectedMediaKind = targetKind
            selectedCategoryFilter = "Tümü"
        }
        if (openPlayerFirst != null) {
            activeChannel = openPlayerFirst
            isPlayerFullScreen = true
            showFullScreenOverlay = true
        } else {
            currentRoute = BottomRoute.HOME
        }
    }

    fun handleCardClick(item: ChannelEntry) {
        if (item.kind == MediaKind.SERIES && item.xtreamSeriesId != null && item.xtreamCreds != null) {
            selectedSeriesForEpisodes = item
            seriesEpisodes = emptyList()
            episodeErrorText = null
            scope.launch {
                isFetchingEpisodes = true
                try {
                    val hdrs = currentHeaders()
                    seriesEpisodes = withContext(Dispatchers.IO) {
                        fetchXtreamSeriesEpisodes(item.xtreamCreds, item.xtreamSeriesId, hdrs)
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
            isPlayerFullScreen = true
            showFullScreenOverlay = true
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            if (activeFileTarget == FileImportTarget.VIDEO) {
                val chosenName = streamTitleInput.trim().ifEmpty {
                    uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { "Yerel Video" } ?: "Yerel Video"
                }
                val entry = ChannelEntry(
                    id = "local-vid-${System.currentTimeMillis()}",
                    name = chosenName,
                    url = uri.toString(),
                    group = streamGroupInput.trim().ifEmpty { "Yerel Medya" },
                    kind = defaultImportKind
                )
                addImportedChannels(
                    newChannels = listOf(entry),
                    playlistTitle = chosenName,
                    sourceInfo = "Yerel Video Dosyası",
                    openPlayerFirst = entry,
                    targetKind = defaultImportKind
                )
                return@rememberLauncherForActivityResult
            }

            scope.launch {
                isLoading = true
                statusText = null
                try {
                    val content = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).readText()
                        }.orEmpty()
                    }
                    when (activeFileTarget) {
                        FileImportTarget.M3U8 -> {
                            val parsed = parsePlaylistText(content, "Diğer", defaultImportKind)
                            val title = m3uNameInput.trim().ifEmpty { "M3U8 Dosyası" }
                            addImportedChannels(parsed, title, "Yerel .m3u / .m3u8 dosyası", targetKind = defaultImportKind)
                            m3uNameInput = ""
                        }
                        FileImportTarget.JSON -> {
                            val parsed = parseJsonPlaylist(content, "JSON Liste", defaultImportKind)
                            val title = jsonNameInput.trim().ifEmpty { "JSON Dosyası" }
                            addImportedChannels(parsed, title, "Yerel .json dosyası", targetKind = defaultImportKind)
                            jsonNameInput = ""
                        }
                        FileImportTarget.HTML -> {
                            val parsed = parseHtmlStreams(content, "", "HTML Yayınlar", defaultImportKind)
                            val title = htmlNameInput.trim().ifEmpty { "HTML Dosyası" }
                            addImportedChannels(parsed, title, "Yerel .html dosyası", targetKind = defaultImportKind)
                            htmlNameInput = ""
                        }
                        FileImportTarget.MHUB -> {
                            val parsed = parseMhubPlugin(content, defaultImportKind)
                            val title = mhubNameInput.trim().ifEmpty { "mHub Eklentisi" }
                            addImportedChannels(parsed, title, "Yerel mHub dosyası", targetKind = defaultImportKind)
                            mhubNameInput = ""
                        }
                        FileImportTarget.VIDEO -> Unit
                    }
                } catch (e: Exception) {
                    statusText = "Dosya okunamadı: ${e.localizedMessage ?: "Hata"}"
                } finally {
                    isLoading = false
                }
            }
        }
    }

    if (isPlayerFullScreen && activeChannel != null) {
        val currentCh = activeChannel!!
        val sameCategoryChannels = remember(allChannels, currentCh.kind, currentCh.group) {
            val sameGroup = allChannels.filter { it.kind == currentCh.kind && it.group == currentCh.group }
            if (sameGroup.isNotEmpty()) sameGroup else allChannels.filter { it.kind == currentCh.kind }
        }
        val categoryRowState = rememberLazyListState()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable {
                    showFullScreenOverlay = !showFullScreenOverlay
                }
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        applyPlayerResizeMode(this, fillScreenAspect)
                    }
                },
                update = { view ->
                    applyPlayerResizeMode(view, fillScreenAspect)
                },
                modifier = Modifier.fillMaxSize()
            )

            if (showFullScreenOverlay) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(Color.Black.copy(alpha = 0.78f))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = { isPlayerFullScreen = false },
                            modifier = Modifier
                                .size(38.dp)
                                .background(Color.White.copy(alpha = 0.14f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Küçült",
                                tint = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    color = AccentOrange,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "İZLENİYOR",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Text(
                                    text = currentCh.group,
                                    color = Color.LightGray,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = currentCh.name,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = { fillScreenAspect = !fillScreenAspect },
                            modifier = Modifier
                                .size(38.dp)
                                .background(Color.White.copy(alpha = 0.14f), CircleShape)
                        ) {
                            Icon(
                                imageVector = if (fillScreenAspect) Icons.Default.FitScreen else Icons.Default.Fullscreen,
                                contentDescription = "Ekran Sığdırma",
                                tint = Color.White
                            )
                        }
                        IconButton(
                            onClick = {
                                runCatching {
                                    if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                }
                            },
                            modifier = Modifier
                                .size(38.dp)
                                .background(AccentOrange, CircleShape)
                        ) {
                            Icon(
                                imageVector = if (exoPlayer.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Oynat/Duraklat",
                                tint = Color.White
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha = 0.85f))
                        .padding(vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${currentCh.group} (${sameCategoryChannels.size} kanal)",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "Gizlemek için ekrana dokunun",
                            color = Color.LightGray,
                            fontSize = 11.sp
                        )
                    }

                    LazyRow(
                        state = categoryRowState,
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            items = sameCategoryChannels,
                            key = { idx -> "${currentCh.kind.name}-${idx.id}" }
                        ) { ch ->
                            val isCurrent = ch.id == currentCh.id && ch.kind == currentCh.kind

                            Surface(
                                onClick = {
                                    if (ch.kind == MediaKind.SERIES && ch.xtreamSeriesId != null && ch.xtreamCreds != null) {
                                        isPlayerFullScreen = false
                                        handleCardClick(ch)
                                    } else {
                                        activeChannel = ch
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isCurrent) AccentOrange.copy(alpha = 0.25f) else CardDarkBg,
                                border = BorderStroke(
                                    width = if (isCurrent) 2.5.dp else 1.dp,
                                    color = if (isCurrent) AccentOrange else Color.White.copy(alpha = 0.2f)
                                ),
                                modifier = Modifier
                                    .width(150.dp)
                                    .height(78.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFF1A1A1A)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.LiveTv,
                                            contentDescription = null,
                                            tint = if (isCurrent) AccentOrange else Color.LightGray,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        if (isCurrent) {
                                            Text(
                                                text = "ŞU ANKİ KANAL",
                                                color = AccentOrange,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        }
                                        Text(
                                            text = ch.name,
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            lineHeight = 13.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return
    }

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
                                                kind = MediaKind.SERIES,
                                                posterUrl = seriesItem.posterUrl
                                            )
                                            selectedSeriesForEpisodes = null
                                            isPlayerFullScreen = true
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
                                        softWrap = false,
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

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                            Box(modifier = Modifier.weight(1f, fill = false)) {
                                Surface(
                                    onClick = { showCategoryMenu = true },
                                    shape = RoundedCornerShape(24.dp),
                                    color = ChipDarkBg
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
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
                                            text = if (selectedCategoryFilter == "Tümü") {
                                                "Kategori (${categories.size - 1})"
                                            } else {
                                                selectedCategoryFilter
                                            },
                                            color = Color.White,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            softWrap = false,
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
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
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
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            softWrap = false
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

                    if (selectedCategoryFilter != "Tümü") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(AccentOrange.copy(alpha = 0.12f))
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Kategori: $selectedCategoryFilter (${filteredAndSorted.size})",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF1A1A1A),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = { selectedCategoryFilter = "Tümü" },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text(
                                    text = "Tümünü Göster",
                                    color = AccentOrange,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    softWrap = false
                                )
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

                    if (filteredAndSorted.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${selectedMediaKind.label} bölümünde içerik bulunamadı. Listelerim sekmesinden kaynak ekleyebilirsiniz.",
                                color = Color.DarkGray,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(24.dp)
                            )
                        }
                    } else if (selectedCategoryFilter != "Tümü") {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(
                                items = filteredAndSorted,
                                key = { ch -> "${ch.kind.name}-${ch.id}" }
                            ) { channel ->
                                MediaShelfCard(
                                    channel = channel,
                                    isSelected = activeChannel?.id == channel.id && activeChannel?.kind == channel.kind,
                                    onClick = { handleCardClick(channel) },
                                    onToggleFavorite = { toggleFavorite(channel.id) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(118.dp)
                                )
                            }
                        }
                    } else {
                        val categoryEntries = remember(groupedChannels) {
                            groupedChannels.entries.toList()
                        }
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            items(
                                items = categoryEntries,
                                key = { entry -> "${selectedMediaKind.name}-${entry.key}" }
                            ) { (categoryName, channelList) ->
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedCategoryFilter = if (selectedCategoryFilter == categoryName) {
                                                    "Tümü"
                                                } else {
                                                    categoryName
                                                }
                                            }
                                            .padding(horizontal = 16.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = categoryName,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF1A1A1A),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = "${channelList.size} >",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = AccentOrange,
                                            softWrap = false
                                        )
                                    }

                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(
                                            items = channelList,
                                            key = { ch -> "${ch.kind.name}-${ch.id}" }
                                        ) { channel ->
                                            MediaShelfCard(
                                                channel = channel,
                                                isSelected = activeChannel?.id == channel.id && activeChannel?.kind == channel.kind,
                                                onClick = { handleCardClick(channel) },
                                                onToggleFavorite = { toggleFavorite(channel.id) },
                                                modifier = Modifier
                                                    .width(155.dp)
                                                    .height(118.dp)
                                            )
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
                    contentPadding = PaddingValues(top = 14.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
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
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Varsayılan Hedef Bölüm",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.DarkGray
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                MediaKind.entries.forEach { kind ->
                                    FilterChip(
                                        selected = defaultImportKind == kind,
                                        onClick = { defaultImportKind = kind },
                                        label = { Text(kind.label, maxLines = 1, fontSize = 12.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = AccentOrange,
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }
                            }
                        }
                    }

                    if (isLoading) {
                        item {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = AccentOrange
                            )
                        }
                    }

                    if (statusText != null) {
                        item {
                            Surface(
                                color = AccentOrange.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = statusText!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = AccentOrange,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }

                    items(SourceSection.entries, key = { it.name }) { section ->
                        val isExpanded = expandedSection == section
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            expandedSection = if (isExpanded) null else section
                                            statusText = null
                                        }
                                        .padding(horizontal = 16.dp, vertical = 15.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = section.title,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF1A1A1A),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        tint = Color(0xFF333333)
                                    )
                                }

                                if (isExpanded) {
                                    HorizontalDivider(color = Color(0xFFEEEEEE))
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        when (section) {
                                            SourceSection.STREAM -> {
                                                OutlinedTextField(
                                                    value = streamTitleInput,
                                                    onValueChange = { streamTitleInput = it },
                                                    label = { Text("Yayın / Video Adı") },
                                                    singleLine = true,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                                OutlinedTextField(
                                                    value = streamUrlInput,
                                                    onValueChange = { streamUrlInput = it },
                                                    label = { Text("Akış / Video Bağlantısı (.m3u8 / .mp4 / URL)") },
                                                    singleLine = true,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                                OutlinedTextField(
                                                    value = streamGroupInput,
                                                    onValueChange = { streamGroupInput = it },
                                                    label = { Text("Kategori") },
                                                    singleLine = true,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Button(
                                                        onClick = {
                                                            val cleanUrl = streamUrlInput.trim()
                                                            val cleanName = streamTitleInput.trim().ifEmpty { "Ağ Yayını" }
                                                            val cleanGroup = streamGroupInput.trim().ifEmpty { "Ağ Akışı" }
                                                            val item = ChannelEntry(
                                                                id = "stream-${System.currentTimeMillis()}",
                                                                name = cleanName,
                                                                url = cleanUrl,
                                                                group = cleanGroup,
                                                                kind = defaultImportKind
                                                            )
                                                            addImportedChannels(
                                                                newChannels = listOf(item),
                                                                playlistTitle = cleanName,
                                                                sourceInfo = "Ağ Akışı",
                                                                openPlayerFirst = item,
                                                                targetKind = defaultImportKind
                                                            )
                                                        },
                                                        enabled = !isLoading && streamUrlInput.isNotBlank(),
                                                        colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text("Aç ve Oynat")
                                                    }

                                                    OutlinedButton(
                                                        onClick = {
                                                            activeFileTarget = FileImportTarget.VIDEO
                                                            filePickerLauncher.launch(arrayOf("video/*", "*/*"))
                                                        },
                                                        enabled = !isLoading
                                                    ) {
                                                        Icon(Icons.Default.FolderOpen, contentDescription = null)
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Video Seç")
                                                    }
                                                }
                                            }

                                            SourceSection.M3U8 -> {
                                                OutlinedTextField(
                                                    value = m3uNameInput,
                                                    onValueChange = { m3uNameInput = it },
                                                    label = { Text("Liste Adı (İsteğe bağlı)") },
                                                    singleLine = true,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                                OutlinedTextField(
                                                    value = m3uUrlInput,
                                                    onValueChange = { m3uUrlInput = it },
                                                    label = { Text("M3U / M3U8 URL") },
                                                    singleLine = true,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Button(
                                                        onClick = {
                                                            val url = m3uUrlInput.trim()
                                                            val hdrs = currentHeaders()
                                                            scope.launch {
                                                                isLoading = true
                                                                statusText = null
                                                                try {
                                                                    val raw = withContext(Dispatchers.IO) {
                                                                        downloadText(url, hdrs)
                                                                    }
                                                                    val parsed = parsePlaylistText(raw, "M3U8 Liste", defaultImportKind)
                                                                    addImportedChannels(
                                                                        newChannels = parsed,
                                                                        playlistTitle = m3uNameInput.trim().ifEmpty { "M3U8 URL Listesi" },
                                                                        sourceInfo = url,
                                                                        targetKind = defaultImportKind
                                                                    )
                                                                    m3uUrlInput = ""
                                                                } catch (e: Exception) {
                                                                    statusText = "M3U8 yüklenemedi: ${e.localizedMessage ?: "Hata"}"
                                                                } finally {
                                                                    isLoading = false
                                                                }
                                                            }
                                                        },
                                                        enabled = !isLoading && m3uUrlInput.isNotBlank(),
                                                        colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Text("URL Yükle")
                                                    }

                                                    OutlinedButton(
                                                        onClick = {
                                                            activeFileTarget = FileImportTarget.M3U8
                                                            filePickerLauncher.launch(arrayOf("*/*"))
                                                        },
                                                        enabled = !isLoading
                                                    ) {
                                                        Text("Dosya Seç")
                                                    }
                                                }
                                            }

                                            SourceSection.JSON -> {
                                                OutlinedTextField(
                                                    value = jsonNameInput,
                                                    onValueChange = { jsonNameInput = it },
                                                    label = { Text("JSON Liste Adı (İsteğe bağlı)") },
                                                    singleLine = true,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                                OutlinedTextField(
                                                    value = jsonUrlInput,
                                                    onValueChange = { jsonUrlInput = it },
                                                    label = { Text("JSON URL") },
                                                    singleLine = true,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Button(
                                                        onClick = {
                                                            val url = jsonUrlInput.trim()
                                                            val hdrs = currentHeaders()
                                                            scope.launch {
                                                                isLoading = true
                                                                statusText = null
                                                                try {
                                                                    val raw = withContext(Dispatchers.IO) {
                                                                        downloadText(url, hdrs)
                                                                    }
                                                                    val parsed = parseJsonPlaylist(raw, "JSON Liste", defaultImportKind)
                                                                    addImportedChannels(
                                                                        newChannels = parsed,
                                                                        playlistTitle = jsonNameInput.trim().ifEmpty { "JSON Listesi" },
                                                                        sourceInfo = url,
                                                                        targetKind = defaultImportKind
                                                                    )
                                                                    jsonUrlInput = ""
                                                                } catch (e: Exception) {
                                                                    statusText = "JSON yüklenemedi: ${e.localizedMessage ?: "Hata"}"
                                                                } finally {
                                                                    isLoading = false
                                                                }
                                                            }
                                                        },
                                                        enabled = !isLoading && jsonUrlInput.isNotBlank(),
                                                        colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Text("JSON Yükle")
                                                    }

                                                    OutlinedButton(
                                                        onClick = {
                                                            activeFileTarget = FileImportTarget.JSON
                                                            filePickerLauncher.launch(arrayOf("application/json", "*/*"))
                                                        },
                                                        enabled = !isLoading
                                                    ) {
                                                        Text("Dosya Seç")
                                                    }
                                                }
                                            }

                                            SourceSection.HTML -> {
                                                OutlinedTextField(
                                                    value = htmlNameInput,
                                                    onValueChange = { htmlNameInput = it },
                                                    label = { Text("HTML Liste Adı (İsteğe bağlı)") },
                                                    singleLine = true,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                                OutlinedTextField(
                                                    value = htmlUrlInput,
                                                    onValueChange = { htmlUrlInput = it },
                                                    label = { Text("HTML Sayfa URL") },
                                                    singleLine = true,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Button(
                                                        onClick = {
                                                            val url = htmlUrlInput.trim()
                                                            val hdrs = currentHeaders()
                                                            scope.launch {
                                                                isLoading = true
                                                                statusText = null
                                                                try {
                                                                    val raw = withContext(Dispatchers.IO) {
                                                                        downloadText(url, hdrs)
                                                                    }
                                                                    val parsed = parseHtmlStreams(raw, url, "HTML Yayınlar", defaultImportKind)
                                                                    addImportedChannels(
                                                                        newChannels = parsed,
                                                                        playlistTitle = htmlNameInput.trim().ifEmpty { "HTML Listesi" },
                                                                        sourceInfo = url,
                                                                        targetKind = defaultImportKind
                                                                    )
                                                                    htmlUrlInput = ""
                                                                } catch (e: Exception) {
                                                                    statusText = "HTML taranamadı: ${e.localizedMessage ?: "Hata"}"
                                                                } finally {
                                                                    isLoading = false
                                                                }
                                                            }
                                                        },
                                                        enabled = !isLoading && htmlUrlInput.isNotBlank(),
                                                        colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Text("HTML Tara")
                                                    }

                                                    OutlinedButton(
                                                        onClick = {
                                                            activeFileTarget = FileImportTarget.HTML
                                                            filePickerLauncher.launch(arrayOf("text/html", "*/*"))
                                                        },
                                                        enabled = !isLoading
                                                    ) {
                                                        Text("Dosya Seç")
                                                    }
                                                }
                                            }

                                            SourceSection.XTREAM -> {
                                                OutlinedTextField(
                                                    value = xtreamNameInput,
                                                    onValueChange = { xtreamNameInput = it },
                                                    label = { Text("Hesap / Liste Adı (İsteğe bağlı)") },
                                                    singleLine = true,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
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
                                                        val hdrs = currentHeaders()
                                                        scope.launch {
                                                            isLoading = true
                                                            statusText = "Canlı TV, Filmler ve Diziler çekiliyor..."
                                                            try {
                                                                val imported = withContext(Dispatchers.IO) {
                                                                    fetchAllXtreamMedia(creds, hdrs)
                                                                }
                                                                val title = xtreamNameInput.trim().ifEmpty {
                                                                    "Xtream (${creds.username})"
                                                                }
                                                                addImportedChannels(
                                                                    newChannels = imported,
                                                                    playlistTitle = title,
                                                                    sourceInfo = creds.serverUrl,
                                                                    targetKind = MediaKind.LIVE
                                                                )
                                                            } catch (e: Exception) {
                                                                statusText = "Xtream girişi başarısız: ${e.localizedMessage ?: "Bağlantı hatası"}"
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
                                                        .height(46.dp)
                                                ) {
                                                    Icon(Icons.Default.Login, contentDescription = null)
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("Xtream Bağlan ve Çek")
                                                }
                                            }

                                            SourceSection.MAC_STB -> {
                                                OutlinedTextField(
                                                    value = macPortalNameInput,
                                                    onValueChange = { macPortalNameInput = it },
                                                    label = { Text("Portal Adı (İsteğe bağlı)") },
                                                    singleLine = true,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                                OutlinedTextField(
                                                    value = macPortalUrlInput,
                                                    onValueChange = { macPortalUrlInput = it },
                                                    label = { Text("Portal URL (http://portal.com/c/)") },
                                                    singleLine = true,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                                OutlinedTextField(
                                                    value = macAddressInput,
                                                    onValueChange = { macAddressInput = it },
                                                    label = { Text("MAC Adresi (00:1A:79:XX:XX:XX)") },
                                                    singleLine = true,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                                Button(
                                                    onClick = {
                                                        val portal = normalizeServerUrl(macPortalUrlInput)
                                                        val mac = macAddressInput.trim().uppercase()
                                                        val hdrs = currentHeaders()
                                                        scope.launch {
                                                            isLoading = true
                                                            statusText = "MAC/STB portal kanalları çekiliyor..."
                                                            try {
                                                                val parsed = withContext(Dispatchers.IO) {
                                                                    fetchMacPortalChannels(portal, mac, hdrs, defaultImportKind)
                                                                }
                                                                val title = macPortalNameInput.trim().ifEmpty { "MAC Portal ($mac)" }
                                                                addImportedChannels(
                                                                    newChannels = parsed,
                                                                    playlistTitle = title,
                                                                    sourceInfo = "$portal • $mac",
                                                                    targetKind = defaultImportKind
                                                                )
                                                            } catch (e: Exception) {
                                                                statusText = "MAC Portal hatası: ${e.localizedMessage ?: "Bağlantı kurulamadı"}"
                                                            } finally {
                                                                isLoading = false
                                                            }
                                                        }
                                                    },
                                                    enabled = !isLoading && macPortalUrlInput.isNotBlank() && macAddressInput.isNotBlank(),
                                                    colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(46.dp)
                                                ) {
                                                    Icon(Icons.Default.Tv, contentDescription = null)
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("MAC Portal Bağlan")
                                                }
                                            }

                                            SourceSection.MHUB -> {
                                                OutlinedTextField(
                                                    value = mhubNameInput,
                                                    onValueChange = { mhubNameInput = it },
                                                    label = { Text("mHub Eklenti Adı (İsteğe bağlı)") },
                                                    singleLine = true,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                                OutlinedTextField(
                                                    value = mhubUrlInput,
                                                    onValueChange = { mhubUrlInput = it },
                                                    label = { Text("mHub / Eklenti URL (.json / .m3u)") },
                                                    singleLine = true,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Button(
                                                        onClick = {
                                                            val url = mhubUrlInput.trim()
                                                            val hdrs = currentHeaders()
                                                            scope.launch {
                                                                isLoading = true
                                                                statusText = null
                                                                try {
                                                                    val raw = withContext(Dispatchers.IO) {
                                                                        downloadText(url, hdrs)
                                                                    }
                                                                    val parsed = parseMhubPlugin(raw, defaultImportKind)
                                                                    addImportedChannels(
                                                                        newChannels = parsed,
                                                                        playlistTitle = mhubNameInput.trim().ifEmpty { "mHub Eklentisi" },
                                                                        sourceInfo = url,
                                                                        targetKind = defaultImportKind
                                                                    )
                                                                    mhubUrlInput = ""
                                                                } catch (e: Exception) {
                                                                    statusText = "mHub yüklenemedi: ${e.localizedMessage ?: "Hata"}"
                                                                } finally {
                                                                    isLoading = false
                                                                }
                                                            }
                                                        },
                                                        enabled = !isLoading && mhubUrlInput.isNotBlank(),
                                                        colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Text("Eklentiyi Yükle")
                                                    }

                                                    OutlinedButton(
                                                        onClick = {
                                                            activeFileTarget = FileImportTarget.MHUB
                                                            filePickerLauncher.launch(arrayOf("*/*"))
                                                        },
                                                        enabled = !isLoading
                                                    ) {
                                                        Text("Dosya Seç")
                                                    }
                                                }
                                            }

                                            SourceSection.GITHUB -> {
                                                OutlinedTextField(
                                                    value = githubNameInput,
                                                    onValueChange = { githubNameInput = it },
                                                    label = { Text("GitHub Paket Adı (İsteğe bağlı)") },
                                                    singleLine = true,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                                OutlinedTextField(
                                                    value = githubUrlInput,
                                                    onValueChange = { githubUrlInput = it },
                                                    label = { Text("GitHub Repo veya Raw Dosya URL") },
                                                    singleLine = true,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                                Button(
                                                    onClick = {
                                                        val url = githubUrlInput.trim()
                                                        val hdrs = currentHeaders()
                                                        scope.launch {
                                                            isLoading = true
                                                            statusText = "GitHub listeleri taranıyor..."
                                                            try {
                                                                val parsed = withContext(Dispatchers.IO) {
                                                                    fetchGithubLists(url, hdrs, defaultImportKind)
                                                                }
                                                                addImportedChannels(
                                                                    newChannels = parsed,
                                                                    playlistTitle = githubNameInput.trim().ifEmpty { "GitHub Toplu Liste" },
                                                                    sourceInfo = url,
                                                                    targetKind = defaultImportKind
                                                                )
                                                                githubUrlInput = ""
                                                            } catch (e: Exception) {
                                                                statusText = "GitHub listesi alınamadı: ${e.localizedMessage ?: "Hata"}"
                                                            } finally {
                                                                isLoading = false
                                                            }
                                                        }
                                                    },
                                                    enabled = !isLoading && githubUrlInput.isNotBlank(),
                                                    colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(46.dp)
                                                ) {
                                                    Icon(Icons.Default.CloudDownload, contentDescription = null)
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("GitHub Listelerini Çek")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "İsteğe Bağlı: Özel Kimlik Bilgileri",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF4A4A4A)
                            )

                            OutlinedTextField(
                                value = userAgentInput,
                                onValueChange = {
                                    userAgentInput = it
                                    persistNow()
                                },
                                placeholder = { Text("User-Agent (gerekliyse)") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFE5E5E5), RoundedCornerShape(6.dp))
                            )

                            OutlinedTextField(
                                value = refererInput,
                                onValueChange = {
                                    refererInput = it
                                    persistNow()
                                },
                                placeholder = { Text("Referer (gerekliyse)") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFE5E5E5), RoundedCornerShape(6.dp))
                            )

                            OutlinedTextField(
                                value = originInput,
                                onValueChange = {
                                    originInput = it
                                    persistNow()
                                },
                                placeholder = { Text("Origin (gerekliyse)") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFE5E5E5), RoundedCornerShape(6.dp))
                            )
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
                                onCheckedChange = {
                                    autoPlayOnSelect = it
                                    persistNow(autoPlay = it)
                                }
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
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shelfIcon = when (channel.kind) {
        MediaKind.LIVE -> Icons.Default.LiveTv
        MediaKind.MOVIES -> Icons.Default.Movie
        MediaKind.SERIES -> Icons.Default.Tv
    }

    val posterUrl = channel.posterUrl?.trim()?.takeIf {
        it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true)
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = CardDarkBg,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) AccentOrange else AccentOrange.copy(alpha = 0.55f)
        ),
        modifier = modifier
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
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Spacer(modifier = Modifier.height(4.dp))

                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1A1A1A)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = shelfIcon,
                        contentDescription = null,
                        tint = if (isSelected) AccentOrange else Color.LightGray,
                        modifier = Modifier.size(22.dp)
                    )

                    if (posterUrl != null) {
                        AndroidView(
                            factory = { ctx ->
                                ImageView(ctx).apply {
                                    scaleType = ImageView.ScaleType.CENTER_CROP
                                }
                            },
                            update = { iv ->
                                iv.setImageDrawable(null)
                                iv.tag = posterUrl
                                iv.post {
                                    if (iv.tag != posterUrl) return@post
                                    Thread {
                                        val bmp = runCatching { downloadBitmapSafe(posterUrl) }.getOrNull()
                                        if (bmp != null) {
                                            iv.post {
                                                if (iv.tag == posterUrl) {
                                                    iv.setImageBitmap(bmp)
                                                }
                                            }
                                        }
                                    }.start()
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Text(
                    text = channel.name,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 13.sp
                )
            }
        }
    }
}

private fun downloadBitmapSafe(imageUrl: String): android.graphics.Bitmap? {
    var currentUrl = imageUrl
    var redirects = 0
    while (redirects < 3) {
        val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 3500
            readTimeout = 4500
            requestMethod = "GET"
            instanceFollowRedirects = false
            setRequestProperty("User-Agent", DEFAULT_USER_AGENT)
        }
        try {
            val code = conn.responseCode
            if (code in 300..399) {
                val loc = conn.getHeaderField("Location") ?: break
                currentUrl = URL(URL(currentUrl), loc).toString()
                redirects++
                continue
            }
            if (code !in 200..299) return null
            val bytes = conn.inputStream.use { it.readBytes() }
            if (bytes.isEmpty() || bytes.size > 2_000_000) return null
            val opts = BitmapFactory.Options().apply {
                inSampleSize = 4
            }
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        } finally {
            conn.disconnect()
        }
    }
    return null
}

private data class PersistentSnapshot(
    val channels: List<ChannelEntry> = emptyList(),
    val playlists: List<SavedPlaylist> = emptyList(),
    val xtream: XtreamCredentials? = null,
    val headers: CustomHeadersConfig = CustomHeadersConfig(),
    val lastListName: String = "",
    val autoPlay: Boolean = true
)

private fun loadPersistentState(context: Context): PersistentSnapshot {
    return runCatching {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_STATE_JSON, null) ?: return@runCatching PersistentSnapshot()
        val root = JSONObject(raw)

        val xtreamObj = root.optJSONObject("xtream")
        val xtream = if (xtreamObj != null) {
            val server = xtreamObj.optString("serverUrl")
            val user = xtreamObj.optString("username")
            val pass = xtreamObj.optString("password")
            if (server.isNotBlank() || user.isNotBlank() || pass.isNotBlank()) {
                XtreamCredentials(server, user, pass)
            } else {
                null
            }
        } else {
            null
        }

        val hdrObj = root.optJSONObject("headers")
        val headers = if (hdrObj != null) {
            CustomHeadersConfig(
                userAgent = hdrObj.optString("userAgent", ""),
                referer = hdrObj.optString("referer", ""),
                origin = hdrObj.optString("origin", "")
            )
        } else {
            CustomHeadersConfig()
        }

        val channels = parseChannelsArray(root.optJSONArray("channels") ?: JSONArray(), xtream)

        val playlists = mutableListOf<SavedPlaylist>()
        val plArr = root.optJSONArray("playlists") ?: JSONArray()
        for (i in 0 until plArr.length()) {
            val pObj = plArr.optJSONObject(i) ?: continue
            playlists.add(
                SavedPlaylist(
                    id = pObj.optString("id", "pl-$i"),
                    title = pObj.optString("title", "Liste"),
                    sourceInfo = pObj.optString("sourceInfo", ""),
                    channels = parseChannelsArray(pObj.optJSONArray("channels") ?: JSONArray(), xtream)
                )
            )
        }

        PersistentSnapshot(
            channels = channels,
            playlists = playlists,
            xtream = xtream,
            headers = headers,
            lastListName = root.optString("lastListName", ""),
            autoPlay = root.optBoolean("autoPlay", true)
        )
    }.getOrDefault(PersistentSnapshot())
}

private fun parseChannelsArray(arr: JSONArray, fallbackXtream: XtreamCredentials?): List<ChannelEntry> {
    val out = mutableListOf<ChannelEntry>()
    for (i in 0 until arr.length()) {
        val obj = arr.optJSONObject(i) ?: continue
        val kindName = obj.optString("kind", MediaKind.LIVE.name)
        val kind = runCatching { MediaKind.valueOf(kindName) }.getOrDefault(MediaKind.LIVE)
        val seriesId = obj.optString("xtreamSeriesId").takeIf { it.isNotBlank() }
        val creds = if (seriesId != null) fallbackXtream else null
        out.add(
            ChannelEntry(
                id = obj.optString("id", "ch-$i"),
                name = obj.optString("name", "İçerik"),
                url = obj.optString("url", ""),
                group = obj.optString("group", "Diğer"),
                kind = kind,
                posterUrl = obj.optString("posterUrl").takeIf { it.isNotBlank() },
                isFavorite = obj.optBoolean("isFavorite", false),
                xtreamSeriesId = seriesId,
                xtreamCreds = creds
            )
        )
    }
    return out
}

private fun savePersistentState(context: Context, snapshot: PersistentSnapshot) {
    runCatching {
        val root = JSONObject()
        root.put("lastListName", snapshot.lastListName)
        root.put("autoPlay", snapshot.autoPlay)

        snapshot.xtream?.let { x ->
            root.put(
                "xtream",
                JSONObject().apply {
                    put("serverUrl", x.serverUrl)
                    put("username", x.username)
                    put("password", x.password)
                }
            )
        }

        root.put(
            "headers",
            JSONObject().apply {
                put("userAgent", snapshot.headers.userAgent)
                put("referer", snapshot.headers.referer)
                put("origin", snapshot.headers.origin)
            }
        )

        root.put("channels", channelsToJsonArray(snapshot.channels))

        val plArr = JSONArray()
        snapshot.playlists.forEach { pl ->
            plArr.put(
                JSONObject().apply {
                    put("id", pl.id)
                    put("title", pl.title)
                    put("sourceInfo", pl.sourceInfo)
                    put("channels", channelsToJsonArray(pl.channels))
                }
            )
        }
        root.put("playlists", plArr)

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_STATE_JSON, root.toString())
            .apply()
    }
}

private fun channelsToJsonArray(list: List<ChannelEntry>): JSONArray {
    val arr = JSONArray()
    list.forEach { ch ->
        arr.put(
            JSONObject().apply {
                put("id", ch.id)
                put("name", ch.name)
                put("url", ch.url)
                put("group", ch.group)
                put("kind", ch.kind.name)
                put("posterUrl", ch.posterUrl ?: "")
                put("isFavorite", ch.isFavorite)
                put("xtreamSeriesId", ch.xtreamSeriesId ?: "")
            }
        )
    }
    return arr
}

private fun JSONObject.optAnyString(key: String, default: String = ""): String {
    if (!has(key) || isNull(key)) return default
    val raw = opt(key) ?: return default
    val str = raw.toString().trim()
    return if (str.isEmpty() || str.equals("null", ignoreCase = true)) default else str
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

private fun fetchAllXtreamMedia(
    creds: XtreamCredentials,
    headers: CustomHeadersConfig
): List<ChannelEntry> {
    val authRaw = downloadText(buildPlayerApiUrl(creds), headers)
    val authJson = JSONObject(authRaw)
    val userInfo = authJson.optJSONObject("user_info")
    val authStatus = userInfo?.optInt("auth", 0) ?: 0
    if (authStatus == 0) {
        throw IllegalStateException("Kullanıcı adı veya şifre geçersiz.")
    }

    val results = mutableListOf<ChannelEntry>()

    val liveCatMap = parseCategoryMap(safeDownload(buildPlayerApiUrl(creds, "get_live_categories"), headers))
    val liveArray = parseJsonArrayFlexible(safeDownload(buildPlayerApiUrl(creds, "get_live_streams"), headers, "[]"))
    for (i in 0 until liveArray.length()) {
        val obj = liveArray.optJSONObject(i) ?: continue
        val streamId = obj.optAnyString("stream_id", i.toString())
        val name = obj.optAnyString("name", "Canlı Kanal")
        val catId = obj.optAnyString("category_id")
        val group = liveCatMap[catId] ?: "Canlı TV"
        val icon = obj.optAnyString("stream_icon").takeIf { it.isNotBlank() }
        val streamUrl = "${creds.serverUrl}/live/${creds.username}/${creds.password}/$streamId.m3u8"
        results.add(
            ChannelEntry(
                id = "xt-live-$streamId-$i",
                name = name,
                url = streamUrl,
                group = group,
                kind = MediaKind.LIVE,
                posterUrl = icon
            )
        )
    }

    val vodCatMap = parseCategoryMap(safeDownload(buildPlayerApiUrl(creds, "get_vod_categories"), headers))
    val vodArray = parseJsonArrayFlexible(safeDownload(buildPlayerApiUrl(creds, "get_vod_streams"), headers, "[]"))
    for (i in 0 until vodArray.length()) {
        val obj = vodArray.optJSONObject(i) ?: continue
        val streamId = obj.optAnyString("stream_id", i.toString())
        val name = obj.optAnyString("name", "Film")
        val catId = obj.optAnyString("category_id")
        val ext = obj.optAnyString("container_extension", "mp4")
        val group = vodCatMap[catId] ?: "Filmler"
        val poster = obj.optAnyString("stream_icon")
            .ifBlank { obj.optAnyString("movie_image") }
            .takeIf { it.isNotBlank() }
        val movieUrl = "${creds.serverUrl}/movie/${creds.username}/${creds.password}/$streamId.$ext"
        results.add(
            ChannelEntry(
                id = "xt-vod-$streamId-$i",
                name = name,
                url = movieUrl,
                group = group,
                kind = MediaKind.MOVIES,
                posterUrl = poster
            )
        )
    }

    val seriesCatMap = parseCategoryMap(safeDownload(buildPlayerApiUrl(creds, "get_series_categories"), headers))
    val seriesArray = parseJsonArrayFlexible(safeDownload(buildPlayerApiUrl(creds, "get_series"), headers, "[]"))
    for (i in 0 until seriesArray.length()) {
        val obj = seriesArray.optJSONObject(i) ?: continue
        val seriesId = obj.optAnyString("series_id", i.toString())
        val name = obj.optAnyString("name", "Dizi")
        val catId = obj.optAnyString("category_id")
        val group = seriesCatMap[catId] ?: "Diziler"
        val cover = obj.optAnyString("cover")
            .ifBlank { obj.optAnyString("stream_icon") }
            .takeIf { it.isNotBlank() }
        results.add(
            ChannelEntry(
                id = "xt-series-$seriesId-$i",
                name = name,
                url = "",
                group = group,
                kind = MediaKind.SERIES,
                posterUrl = cover,
                xtreamSeriesId = seriesId,
                xtreamCreds = creds
            )
        )
    }

    return results
}

private fun fetchXtreamSeriesEpisodes(
    creds: XtreamCredentials,
    seriesId: String,
    headers: CustomHeadersConfig
): List<SeriesEpisodeItem> {
    val raw = downloadText(buildPlayerApiUrl(creds, "get_series_info", "&series_id=$seriesId"), headers)
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
            val epId = epObj.optAnyString("id")
            if (epId.isBlank()) continue
            val epNum = epObj.optInt("episode_num", i + 1)
            val title = epObj.optAnyString("title", "Bölüm $epNum")
            val ext = epObj.optAnyString("container_extension", "mp4")
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

private fun fetchMacPortalChannels(
    portalUrl: String,
    macAddress: String,
    headers: CustomHeadersConfig,
    fallbackKind: MediaKind
): List<ChannelEntry> {
    val baseRoot = portalUrl.removeSuffix("/c").removeSuffix("/c/")
    val apiUrl = "$baseRoot/server/load.php?type=itv&action=get_all_channels&mac=${URLEncoder.encode(macAddress, "UTF-8")}"
    val raw = downloadText(apiUrl, headers)
    val root = JSONObject(raw)
    val dataObj = root.optJSONObject("js") ?: root
    val itemsArr = parseJsonArrayFlexible(dataObj.opt("data")?.toString() ?: raw)
    val list = mutableListOf<ChannelEntry>()
    for (i in 0 until itemsArr.length()) {
        val obj = itemsArr.optJSONObject(i) ?: continue
        val name = obj.optAnyString("name", "Portal Kanal ${i + 1}")
        val cmd = obj.optAnyString("cmd").removePrefix("ffmpeg ").trim()
        val streamUrl = if (cmd.startsWith("http://", true) || cmd.startsWith("https://", true)) {
            cmd
        } else {
            val streamId = obj.optAnyString("id", i.toString())
            "$baseRoot/play/live.php?mac=${URLEncoder.encode(macAddress, "UTF-8")}&stream=$streamId&extension=ts"
        }
        val group = obj.optAnyString("tv_genre_id", "MAC Portal")
        val logo = obj.optAnyString("logo").takeIf { it.isNotBlank() }
        list.add(
            ChannelEntry(
                id = "mac-$i-${name.hashCode()}",
                name = name,
                url = streamUrl,
                group = group,
                kind = detectMediaKind(group, name, fallbackKind),
                posterUrl = logo
            )
        )
    }
    if (list.isEmpty()) {
        throw IllegalStateException("MAC portaldan kanal listesi alınamadı.")
    }
    return list
}

private fun parseJsonPlaylist(
    rawText: String,
    defaultGroup: String,
    preferredKind: MediaKind
): List<ChannelEntry> {
    val trimmed = rawText.trim()
    val arr = if (trimmed.startsWith("[")) {
        parseJsonArrayFlexible(trimmed)
    } else {
        val obj = JSONObject(trimmed)
        val candidate = obj.opt("channels")
            ?: obj.opt("items")
            ?: obj.opt("streams")
            ?: obj.opt("data")
        parseJsonArrayFlexible(candidate?.toString() ?: "[]")
    }

    val out = mutableListOf<ChannelEntry>()
    for (i in 0 until arr.length()) {
        val item = arr.optJSONObject(i) ?: continue
        val name = item.optAnyString("name").ifBlank {
            item.optAnyString("title", "İçerik ${i + 1}")
        }
        val url = item.optAnyString("url").ifBlank {
            item.optAnyString("streamUrl").ifBlank {
                item.optAnyString("link")
            }
        }
        if (url.isBlank()) continue
        val group = item.optAnyString("group").ifBlank {
            item.optAnyString("category", defaultGroup)
        }
        val poster = item.optAnyString("poster").ifBlank {
            item.optAnyString("logo").ifBlank {
                item.optAnyString("icon")
            }
        }.takeIf { it.isNotBlank() }

        out.add(
            ChannelEntry(
                id = "json-$i-${url.hashCode()}",
                name = name,
                url = url,
                group = group,
                kind = detectMediaKind(group, name, preferredKind),
                posterUrl = poster
            )
        )
    }
    return out
}

private fun parseHtmlStreams(
    htmlText: String,
    baseUrl: String,
    defaultGroup: String,
    preferredKind: MediaKind
): List<ChannelEntry> {
    val regex = Regex("""https?://[^\s"'<>)]+\.(?:m3u8|m3u|mp4|mkv|ts|mp3)(?:\?[^\s"'<>)]*)?""", RegexOption.IGNORE_CASE)
    val matches = regex.findAll(htmlText).map { it.value.trim() }.distinct().toList()
    val list = mutableListOf<ChannelEntry>()
    matches.forEachIndexed { idx, rawUrl ->
        val resolved = runCatching {
            if (baseUrl.isNotBlank()) URL(URL(baseUrl), rawUrl).toString() else rawUrl
        }.getOrDefault(rawUrl)
        val title = resolved.substringAfterLast('/').substringBefore('?').ifBlank { "Yayın ${idx + 1}" }
        list.add(
            ChannelEntry(
                id = "html-$idx-${resolved.hashCode()}",
                name = title,
                url = resolved,
                group = defaultGroup,
                kind = detectMediaKind(defaultGroup, title, preferredKind)
            )
        )
    }
    return list
}

private fun parseMhubPlugin(
    rawText: String,
    preferredKind: MediaKind
): List<ChannelEntry> {
    val trimmed = rawText.trim()
    if (trimmed.startsWith("#EXTM3U") || trimmed.contains("#EXTINF:")) {
        return parsePlaylistText(trimmed, "mHub Eklenti", preferredKind)
    }
    return parseJsonPlaylist(trimmed, "mHub Eklenti", preferredKind)
}

private fun fetchGithubLists(
    inputUrl: String,
    headers: CustomHeadersConfig,
    preferredKind: MediaKind
): List<ChannelEntry> {
    val clean = inputUrl.trim().trimEnd('/')
    if (clean.isEmpty()) {
        throw IllegalArgumentException("GitHub adresi veya repo adı girin.")
    }

    val normalizedUrl = when {
        clean.startsWith("http://", ignoreCase = true) || clean.startsWith("https://", ignoreCase = true) -> clean
        clean.contains("/") -> "https://github.com/$clean"
        else -> {
            val searchRaw = runCatching {
                downloadText(
                    "https://api.github.com/search/repositories?q=${URLEncoder.encode(clean, "UTF-8")}&per_page=1",
                    headers
                )
            }.getOrNull()
            val fullName = searchRaw?.let {
                runCatching {
                    JSONObject(it).optJSONArray("items")?.optJSONObject(0)?.optString("full_name")
                }.getOrNull()
            }
            if (!fullName.isNullOrBlank()) {
                "https://github.com/$fullName"
            } else {
                "https://github.com/$clean/$clean"
            }
        }
    }

    if (normalizedUrl.contains("raw.githubusercontent.com", ignoreCase = true)) {
        val content = downloadText(normalizedUrl, headers)
        return parseGithubFileContent(normalizedUrl, content, preferredKind)
    }

    if (normalizedUrl.contains("github.com/", ignoreCase = true) && normalizedUrl.contains("/blob/")) {
        val rawUrl = normalizedUrl
            .replace("github.com/", "raw.githubusercontent.com/")
            .replace("/blob/", "/")
        val content = downloadText(rawUrl, headers)
        return parseGithubFileContent(rawUrl, content, preferredKind)
    }

    val repoPath = normalizedUrl
        .removePrefix("https://github.com/")
        .removePrefix("http://github.com/")
        .substringBefore("/tree/")
        .trim('/')
    val parts = repoPath.split("/").filter { it.isNotBlank() }
    if (parts.size < 2) {
        val content = downloadText(normalizedUrl, headers)
        return parseGithubFileContent(normalizedUrl, content, preferredKind)
    }

    val owner = parts[0]
    val repo = parts[1]
    val treeApiUrl = "https://api.github.com/repos/$owner/$repo/git/trees/HEAD?recursive=1"
    val treeJson = JSONObject(downloadText(treeApiUrl, headers))
    val treeArr = treeJson.optJSONArray("tree") ?: JSONArray()

    val playlistPaths = mutableListOf<String>()
    for (i in 0 until treeArr.length()) {
        val item = treeArr.optJSONObject(i) ?: continue
        val type = item.optString("type")
        val path = item.optString("path")
        if (type == "blob" && (
                path.endsWith(".m3u8", true) ||
                    path.endsWith(".m3u", true) ||
                    path.endsWith(".json", true) ||
                    path.endsWith(".html", true)
                )
        ) {
            playlistPaths.add(path)
        }
    }

    if (playlistPaths.isEmpty()) {
        throw IllegalStateException("Bu depoda (.m3u, .m3u8, .json, .html) liste dosyası bulunamadı.")
    }

    val branch = treeJson.optString("default_branch").ifBlank {
        runCatching {
            JSONObject(downloadText("https://api.github.com/repos/$owner/$repo", headers))
                .optString("default_branch")
        }.getOrNull().orEmpty().ifBlank { "main" }
    }

    val collected = mutableListOf<ChannelEntry>()
    for (path in playlistPaths.take(15)) {
        val rawFileUrl = "https://raw.githubusercontent.com/$owner/$repo/$branch/$path"
        val body = runCatching { downloadText(rawFileUrl, headers) }.getOrNull() ?: continue
        val fileName = path.substringAfterLast('/')
        collected.addAll(parseGithubFileContent(rawFileUrl, body, preferredKind, fileName))
    }

    if (collected.isEmpty()) {
        throw IllegalStateException("GitHub liste dosyaları okunamadı.")
    }
    return collected
}

private fun parseGithubFileContent(
    sourceUrl: String,
    content: String,
    preferredKind: MediaKind,
    groupLabel: String = "GitHub Liste"
): List<ChannelEntry> {
    val trimmed = content.trim()
    return when {
        sourceUrl.endsWith(".json", true) || trimmed.startsWith("{") || trimmed.startsWith("[") -> {
            parseJsonPlaylist(content, groupLabel, preferredKind)
        }
        sourceUrl.endsWith(".html", true) || trimmed.contains("<html", true) -> {
            parseHtmlStreams(content, sourceUrl, groupLabel, preferredKind)
        }
        else -> {
            parsePlaylistText(content, groupLabel, preferredKind)
        }
    }
}

private fun parseJsonArrayFlexible(raw: String): JSONArray {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return JSONArray()
    if (trimmed.startsWith("[")) {
        return try {
            JSONArray(trimmed)
        } catch (_: Exception) {
            JSONArray()
        }
    }
    if (trimmed.startsWith("{")) {
        return try {
            val obj = JSONObject(trimmed)
            val arr = JSONArray()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val itemObj = obj.optJSONObject(keys.next())
                if (itemObj != null) arr.put(itemObj)
            }
            arr
        } catch (_: Exception) {
            JSONArray()
        }
    }
    return JSONArray()
}

private fun parseCategoryMap(rawJson: String): Map<String, String> {
    val map = mutableMapOf<String, String>()
    val arr = parseJsonArrayFlexible(rawJson)
    for (i in 0 until arr.length()) {
        val obj = arr.optJSONObject(i) ?: continue
        val id = obj.optAnyString("category_id")
        val name = obj.optAnyString("category_name")
        if (id.isNotBlank() && name.isNotBlank()) {
            map[id] = name
        }
    }
    return map
}

private fun safeDownload(
    url: String,
    headers: CustomHeadersConfig,
    fallback: String = "[]"
): String {
    return try {
        downloadText(url, headers)
    } catch (_: Exception) {
        fallback
    }
}

private fun downloadText(
    urlString: String,
    headers: CustomHeadersConfig = CustomHeadersConfig()
): String {
    var currentUrl = urlString
    var redirects = 0
    while (redirects < 5) {
        val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20000
            readTimeout = 30000
            requestMethod = "GET"
            instanceFollowRedirects = false
            setRequestProperty(
                "User-Agent",
                headers.userAgent.ifBlank { DEFAULT_USER_AGENT }
            )
            if (headers.referer.isNotBlank()) {
                setRequestProperty("Referer", headers.referer)
            }
            if (headers.origin.isNotBlank()) {
                setRequestProperty("Origin", headers.origin)
            }
            setRequestProperty("Accept", "application/json, text/plain, text/html, */*")
        }
        try {
            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location") ?: break
                currentUrl = URL(URL(currentUrl), location).toString()
                redirects++
                continue
            }
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code yanıtı alındı")
            }
            return body
        } finally {
            conn.disconnect()
        }
    }
    throw IllegalStateException("Çok fazla yönlendirme (redirect) yapıldı")
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
    var nextLogo: String? = null

    for (line in lines) {
        if (line.startsWith("#EXTINF:", ignoreCase = true)) {
            val groupRegex = Regex("""group-title="([^"]*)"""", RegexOption.IGNORE_CASE).find(line)
            nextGroup = groupRegex?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() } ?: defaultGroup
            val logoRegex = Regex("""tvg-logo="([^"]*)"""", RegexOption.IGNORE_CASE).find(line)
            nextLogo = logoRegex?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
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
                    kind = resolvedKind,
                    posterUrl = nextLogo
                )
            )
            nextTitle = null
            nextGroup = defaultGroup
            nextLogo = null
        }
    }
    return items
}
