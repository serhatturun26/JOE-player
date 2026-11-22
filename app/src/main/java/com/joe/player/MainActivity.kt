package com.joe.player

import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
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
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
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
    val xtreamCreds: XtreamCredentials? = null,
    val lastWatchedAt: Long = 0L,
    val lastPositionMs: Long = 0L
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

private val AccentRed = Color(0xFFE50914)
private val CardDarkBg = Color(0xFF242020)
private val ChipDarkBg = Color(0xFF181212)
private val LightPageBg = Color(0xFFF8F6F6)
private const val PREFS_NAME = "joe_player_prefs_v11"
private const val KEY_STATE_JSON = "saved_state_json"
private const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Linux; Android 14) JOE-Player/1.0"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = AccentRed,
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

private fun applyKeepScreenOn(activity: ComponentActivity?, keepScreenOn: Boolean) {
    val act = activity ?: return
    runCatching {
        if (keepScreenOn) {
            act.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            act.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
    var isPlayingState by remember { mutableStateOf(false) }

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

    fun recordWatchProgress(channel: ChannelEntry?, positionMs: Long, durationMs: Long) {
        val target = channel ?: return
        val now = System.currentTimeMillis()
        val safePos = if (target.kind == MediaKind.LIVE) {
            0L
        } else if (durationMs > 0L && positionMs >= (durationMs * 0.95).toLong()) {
            0L
        } else if (positionMs > 5_000L) {
            positionMs
        } else {
            0L
        }

        val updated = allChannels.map { item ->
            if (item.id == target.id && item.kind == target.kind) {
                item.copy(
                    lastWatchedAt = now,
                    lastPositionMs = safePos
                )
            } else {
                item
            }
        }
        allChannels = updated
        persistNow(channels = updated)
    }

    fun deletePlaylist(targetPlaylist: SavedPlaylist) {
        val removedKeys = targetPlaylist.channels.map { "${it.kind.name}-${it.id}-${it.url}" }.toSet()
        val updatedChannels = allChannels.filter { "${it.kind.name}-${it.id}-${it.url}" !in removedKeys }
        val updatedPlaylists = playlists.filterNot { it.id == targetPlaylist.id }

        if (activeChannel != null) {
            val activeKey = "${activeChannel!!.kind.name}-${activeChannel!!.id}-${activeChannel!!.url}"
            if (activeKey in removedKeys) {
                activeChannel = null
                isPlayerFullScreen = false
            }
        }

        allChannels = updatedChannels
        playlists = updatedPlaylists
        statusText = "\"${targetPlaylist.title}\" listesi silindi."
        persistNow(channels = updatedChannels, lists = updatedPlaylists)
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

    LaunchedEffect(activeChannel, isPlayingState) {
        val shouldKeepAwake = activeChannel != null && (isPlayingState || exoPlayer.playWhenReady)
        applyKeepScreenOn(activity, shouldKeepAwake)
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                isPlayingState = isPlaying
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                isPlayingState = playWhenReady || exoPlayer.isPlaying
            }
        }
        exoPlayer.addListener(listener)
        runCatching { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
        applyFullScreenSystemBars(activity, false)
        onDispose {
            val curPos = runCatching { exoPlayer.currentPosition }.getOrDefault(0L)
            val curDur = runCatching {
                val d = exoPlayer.duration
                if (d == C.TIME_UNSET) 0L else d
            }.getOrDefault(0L)
            recordWatchProgress(activeChannel, curPos, curDur)
            exoPlayer.removeListener(listener)
            applyKeepScreenOn(activity, false)
            runCatching { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
            applyFullScreenSystemBars(activity, false)
            runCatching { exoPlayer.release() }
        }
    }

    LaunchedEffect(activeChannel?.id, activeChannel?.url, autoPlayOnSelect) {
        val current = activeChannel
        val target = current?.url
        runCatching {
            if (current != null && !target.isNullOrBlank()) {
                val latestSaved = allChannels.firstOrNull { it.id == current.id && it.kind == current.kind }
                val resumeMs = if (current.kind != MediaKind.LIVE) {
                    (latestSaved?.lastPositionMs ?: current.lastPositionMs).coerceAtLeast(0L)
                } else {
                    0L
                }

                exoPlayer.setMediaItem(MediaItem.fromUri(target))
                if (resumeMs > 5_000L) {
                    exoPlayer.seekTo(resumeMs)
                }
                exoPlayer.prepare()
                exoPlayer.playWhenReady = autoPlayOnSelect
                isPlayingState = autoPlayOnSelect

                val now = System.currentTimeMillis()
                val updated = allChannels.map { item ->
                    if (item.id == current.id && item.kind == current.kind) {
                        item.copy(lastWatchedAt = now)
                    } else {
                        item
                    }
                }
                allChannels = updated
                persistNow(channels = updated)
            } else {
                exoPlayer.stop()
                isPlayingState = false
            }
        }
    }

    fun openChannelWithProgressSave(nextChannel: ChannelEntry) {
        val prevChannel = activeChannel
        if (prevChannel != null && (prevChannel.id != nextChannel.id || prevChannel.kind != nextChannel.kind)) {
            val prevPos = runCatching { exoPlayer.currentPosition }.getOrDefault(0L)
            val prevDur = runCatching {
                val d = exoPlayer.duration
                if (d == C.TIME_UNSET) 0L else d
            }.getOrDefault(0L)
            recordWatchProgress(prevChannel, prevPos, prevDur)
        }
        activeChannel = nextChannel
        isPlayerFullScreen = true
        showFullScreenOverlay = true
    }

    fun closeActivePlayerWithSave() {
        val prevChannel = activeChannel
        if (prevChannel != null) {
            val prevPos = runCatching { exoPlayer.currentPosition }.getOrDefault(0L)
            val prevDur = runCatching {
                val d = exoPlayer.duration
                if (d == C.TIME_UNSET) 0L else d
            }.getOrDefault(0L)
            recordWatchProgress(prevChannel, prevPos, prevDur)
        }
        activeChannel = null
        isPlayerFullScreen = false
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
            openChannelWithProgressSave(openPlayerFirst)
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
            openChannelWithProgressSave(item)
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
                        keepScreenOn = true
                        applyPlayerResizeMode(this, fillScreenAspect)
                    }
                },
                update = { view ->
                    view.keepScreenOn = true
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
                                    color = AccentRed,
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
                                .background(AccentRed, CircleShape)
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
                                        openChannelWithProgressSave(ch)
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isCurrent) AccentRed.copy(alpha = 0.25f) else CardDarkBg,
                                border = BorderStroke(
                                    width = if (isCurrent) 2.5.dp else 1.dp,
                                    color = if (isCurrent) AccentRed else Color.White.copy(alpha = 0.2f)
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
                                            tint = if (isCurrent) AccentRed else Color.LightGray,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        if (isCurrent) {
                                            Text(
                                                text = "ŞU ANKİ KANAL",
                                                color = AccentRed,
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
                                CircularProgressIndicator(color = AccentRed)
                                Text("Sezon ve bölümler yükleniyor...")
                            }
                        }

                        episodeErrorText != null -> {
                            Text(episodeErrorText!!, color = MaterialTheme.colorScheme.error)
                        }

                        else -> {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(seriesEpisodes, key = { it.id }) { ep ->
                                    val epId = "ep-${ep.id}"
                                    val savedEp = allChannels.firstOrNull { it.id == epId && it.kind == MediaKind.SERIES }
                                    Surface(
                                        onClick = {
                                            val episodeEntry = ChannelEntry(
                                                id = epId,
                                                name = "${seriesItem.name} - S${ep.season}E${ep.episodeNum}: ${ep.title}",
                                                url = ep.streamUrl,
                                                group = seriesItem.group,
                                                kind = MediaKind.SERIES,
                                                posterUrl = seriesItem.posterUrl,
                                                lastPositionMs = savedEp?.lastPositionMs ?: 0L
                                            )
                                            selectedSeriesForEpisodes = null
                                            openChannelWithProgressSave(episodeEntry)
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
                                                tint = AccentRed
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "Sezon ${ep.season} • Bölüm ${ep.episodeNum} - ${ep.title}",
                                                    fontSize = 13.sp,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if ((savedEp?.lastPositionMs ?: 0L) > 5_000L) {
                                                    Text(
                                                        text = "Kaldığın yer: ${formatDurationShort(savedEp!!.lastPositionMs)}",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = AccentRed
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
            },
            confirmButton = {
                TextButton(onClick = { selectedSeriesForEpisodes = null }) {
                    Text("Kapat", color = AccentRed)
                }
            }
        )
    }

    Scaffold(
        containerColor = LightPageBg,
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF181212),
                contentColor = Color.White
            ) {
                NavigationBarItem(
                    selected = currentRoute == BottomRoute.HOME,
                    onClick = { currentRoute = BottomRoute.HOME },
                    icon = { Icon(Icons.Default.LiveTv, contentDescription = null) },
                    label = { Text("Ana Sayfa") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AccentRed,
                        selectedTextColor = AccentRed,
                        indicatorColor = CardDarkBg,
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
                        selectedIconColor = AccentRed,
                        selectedTextColor = AccentRed,
                        indicatorColor = CardDarkBg,
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
                        selectedIconColor = AccentRed,
                        selectedTextColor = AccentRed,
                        indicatorColor = CardDarkBg,
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

                val recentlyWatched = remember(kindChannels) {
                    kindChannels
                        .filter { it.lastWatchedAt > 0L && it.url.isNotBlank() }
                        .sortedByDescending { it.lastWatchedAt }
                        .take(12)
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
                                    selectedContainerColor = AccentRed,
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
                                .background(AccentRed.copy(alpha = 0.12f))
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
                                    color = AccentRed,
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
                                        keepScreenOn = true
                                    }
                                },
                                update = { view ->
                                    view.keepScreenOn = true
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
                                    onClick = { closeActivePlayerWithSave() },
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
                                    isSelected = activeChannel
