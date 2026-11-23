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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

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

data class RecentWatchEntry(
    val channel: ChannelEntry,
    val lastPositionMs: Long = 0L,
    val lastWatchedAt: Long = System.currentTimeMillis()
)

data class SeriesEpisodeItem(
    val id: String,
    val season: Int,
    val episodeNum: Int,
    val title: String,
    val streamUrl: String
)

private data class selectableTrackOption(
    val trackType: @C.TrackType Int,
    val mediaTrackGroup: TrackGroup,
    val trackIndex: Int,
    val languageCode: String,
    val title: String,
    val isSelected: Boolean
)

enum class PlaylistSourceType {
    STREAM,
    M3U8,
    JSON,
    HTML,
    XTREAM,
    MAC_STB,
    MHUB,
    GITHUB,
    LOCAL_FILE
}

data class SavedPlaylist(
    val id: String,
    val title: String,
    val sourceInfo: String,
    val channels: List<ChannelEntry>,
    val itemCountSummary: Int = channels.size,
    val sourceType: PlaylistSourceType = PlaylistSourceType.M3U8,
    val preferredKind: MediaKind = MediaKind.LIVE,
    val xtreamCreds: XtreamCredentials? = null,
    val macPortalUrl: String = "",
    val macAddress: String = ""
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
private const val PREFS_NAME = "joe_player_prefs_v28"
private const val KEY_STATE_JSON = "saved_state_json_v28"
private const val STATE_FILE_NAME = "joe_channels_v28.json"
private const val STATE_TEMP_FILE_NAME = "joe_channels_v28.tmp"
private const val RECENT_FILE_NAME = "recent_watch_v28.json"
private const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Linux; Android 14) JOE-Player/1.0"
private const val RECENT_CATEGORY_LABEL = "Son İzlenenler"

private val posterExecutor = Executors.newFixedThreadPool(2)
private val activePosterJobs = AtomicInteger(0)

private fun normalizeLangTag(raw: String?): String {
    val clean = raw?.trim().orEmpty()
    if (clean.isEmpty() || clean.equals("und", ignoreCase = true)) return ""
    return clean.replace('_', '-').substringBefore('-').lowercase(Locale.ROOT)
}

private fun formatLanguageLabel(format: Format, fallbackIndex: Int, isAudio: Boolean): String {
    val rawLang = format.language?.trim().orEmpty()
    val locale = if (rawLang.isNotEmpty() && !rawLang.equals("und", ignoreCase = true)) {
        Locale.forLanguageTag(rawLang.replace('_', '-'))
    } else {
        null
    }
    val displayLang = locale?.getDisplayLanguage(Locale("tr", "TR"))
        ?.takeIf { it.isNotBlank() && !it.equals(rawLang, ignoreCase = true) }
        ?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("tr", "TR")) else it.toString() }

    val baseName = format.label?.trim()?.takeIf { it.isNotEmpty() }
        ?: displayLang
        ?: if (rawLang.isNotEmpty() && !rawLang.equals("und", ignoreCase = true)) {
            rawLang.uppercase(Locale.getDefault())
        } else {
            if (isAudio) "Ses ${fallbackIndex + 1}" else "Altyazı ${fallbackIndex + 1}"
        }

    val details = buildList {
        if (displayLang != null && !baseName.contains(displayLang, ignoreCase = true)) {
            add(displayLang)
        }
        if (isAudio && format.channelCount > 0) {
            add("${format.channelCount}ch")
        }
    }
    return if (details.isEmpty()) baseName else "$baseName (${details.joinToString(", ")})"
}

private fun extractTrackOptions(tracks: Tracks): Pair<List<selectableTrackOption>, List<selectableTrackOption>> {
    val audioList = mutableListOf<selectableTrackOption>()
    val textList = mutableListOf<selectableTrackOption>()

    tracks.groups.forEach { group ->
        if (!group.isSupported) return@forEach
        when (group.type) {
            C.TRACK_TYPE_AUDIO -> {
                for (i in 0 until group.length) {
                    if (!group.isTrackSupported(i)) continue
                    val fmt = group.getTrackFormat(i)
                    audioList.add(
                        selectableTrackOption(
                            trackType = C.TRACK_TYPE_AUDIO,
                            mediaTrackGroup = group.mediaTrackGroup,
                            trackIndex = i,
                            languageCode = normalizeLangTag(fmt.language),
                            title = formatLanguageLabel(fmt, audioList.size, isAudio = true),
                            isSelected = group.isTrackSelected(i)
                        )
                    )
                }
            }
            C.TRACK_TYPE_TEXT -> {
                for (i in 0 until group.length) {
                    if (!group.isTrackSupported(i)) continue
                    val fmt = group.getTrackFormat(i)
                    textList.add(
                        selectableTrackOption(
                            trackType = C.TRACK_TYPE_TEXT,
                            mediaTrackGroup = group.mediaTrackGroup,
                            trackIndex = i,
                            languageCode = normalizeLangTag(fmt.language),
                            title = formatLanguageLabel(fmt, textList.size, isAudio = false),
                            isSelected = group.isTrackSelected(i)
                        )
                    )
                }
            }
        }
    }
    return audioList to textList
}

private fun buildTrackBadgeText(tracks: Tracks, isTextDisabled: Boolean): String {
    val (audioOptions, textOptions) = extractTrackOptions(tracks)
    val activeAudio = audioOptions.firstOrNull { it.isSelected } ?: audioOptions.firstOrNull()
    val audioTag = when {
        activeAudio != null && activeAudio.languageCode.isNotBlank() ->
            activeAudio.languageCode.uppercase(Locale.ROOT)
        activeAudio != null -> "SES"
        else -> "DİL"
    }
    val hasActiveText = !isTextDisabled && textOptions.any { it.isSelected }
    return if (hasActiveText) "$audioTag • ALT" else audioTag
}

private fun formatDurationBadge(positionMs: Long): String? {
    if (positionMs < 5_000L) return null
    val totalSeconds = positionMs / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}

private fun formatPlayerClock(ms: Long): String {
    val safeMs = ms.coerceAtLeast(0L)
    val totalSeconds = safeMs / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}

private fun formatClockTime(millis: Long): String {
    if (millis <= 0L) return "Henüz yok"
    return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(millis))
}

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
    var defaultStartKind by remember { mutableStateOf(savedSnapshot.defaultKind) }
    var selectedMediaKind by remember { mutableStateOf(savedSnapshot.defaultKind) }
    var playlists by remember { mutableStateOf(savedSnapshot.playlists) }
    var recentWatches by remember { mutableStateOf(savedSnapshot.recentWatches) }

    var allChannels by remember { mutableStateOf(savedSnapshot.channels) }
    var lastSaveTimestamp by remember { mutableLongStateOf(savedSnapshot.savedAtMillis) }
    var lastSaveError by remember { mutableStateOf<String?>(null) }

    var activeChannel by remember { mutableStateOf<ChannelEntry?>(null) }
    var isPlayerFullScreen by remember { mutableStateOf(false) }
    var showFullScreenOverlay by remember { mutableStateOf(true) }
    var showTrackSelectorDialog by remember { mutableStateOf(false) }
    var preferredAudioLangOverride by remember { mutableStateOf<String?>(null) }
    var preferredTextLangOverride by remember { mutableStateOf<String?>(null) }
    var liveAutoRetryCount by remember { mutableIntStateOf(0) }
    var fillScreenAspect by remember { mutableStateOf(savedSnapshot.zoomToFillDefault) }
    var playbackStatusLabel by remember { mutableStateOf("Hazır") }
    var playbackErrorMessage by remember { mutableStateOf<String?>(null) }

    var selectedSeriesForEpisodes by remember { mutableStateOf<ChannelEntry?>(null) }
    var seriesEpisodes by remember { mutableStateOf<List<SeriesEpisodeItem>>(emptyList()) }
    var isFetchingEpisodes by remember { mutableStateOf(false) }
    var episodeErrorText by remember { mutableStateOf<String?>(null) }

    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var onlyFavoritesFilter by remember { mutableStateOf(false) }
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
    var showResetAllDialog by remember { mutableStateOf(false) }
    var settingsFeedback by remember { mutableStateOf<String?>(null) }
    var statusText by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var refreshingPlaylistId by remember { mutableStateOf<String?>(null) }

    val trackSelector = remember {
        DefaultTrackSelector(context).apply {
            parameters = buildUponParameters()
                .setPreferredAudioLanguage("tr")
                .setPreferredTextLanguage("tr")
                .build()
        }
    }
    val exoPlayer = remember {
        ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .build()
    }
    var isPlayingState by remember { mutableStateOf(false) }
    var currentTracksState by remember { mutableStateOf(Tracks.EMPTY) }

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
        recents: List<RecentWatchEntry> = recentWatches,
        autoPlay: Boolean = autoPlayOnSelect,
        zoomDefault: Boolean = fillScreenAspect,
        startKind: MediaKind = defaultStartKind
    ): Boolean {
        val currentXtream = if (xtreamServerInput.isNotBlank() || xtreamUserInput.isNotBlank() || xtreamPassInput.isNotBlank()) {
            XtreamCredentials(
                serverUrl = xtreamServerInput.trim(),
                username = xtreamUserInput.trim(),
                password = xtreamPassInput.trim()
            )
        } else {
            null
        }
        val nowMillis = System.currentTimeMillis()
        val snapshot = PersistentSnapshot(
            channels = channels,
            playlists = lists,
            recentWatches = recents,
            xtream = currentXtream,
            headers = currentHeaders(),
            lastListName = xtreamNameInput,
            autoPlay = autoPlay,
            zoomToFillDefault = zoomDefault,
            defaultKind = startKind,
            savedAtMillis = nowMillis
        )
        val result = savePersistentStateWithError(context = context, snapshot = snapshot)
        if (result.first) {
            lastSaveTimestamp = nowMillis
            lastSaveError = null
        } else {
            lastSaveError = result.second ?: "Yazma hatası"
        }
        return result.first
    }

    fun recordRecentWatch(channel: ChannelEntry, positionMs: Long = 0L, persistDisk: Boolean = false) {
        if (channel.url.isBlank()) return
        val key = "${channel.kind.name}-${channel.id}-${channel.url}"
        val prevPosition = recentWatches.firstOrNull {
            "${it.channel.kind.name}-${it.channel.id}-${it.channel.url}" == key
        }?.lastPositionMs ?: 0L
        val effectivePos = if (positionMs > 0L) positionMs else prevPosition
        val updated = listOf(
            RecentWatchEntry(
                channel = channel,
                lastPositionMs = effectivePos,
                lastWatchedAt = System.currentTimeMillis()
            )
        ) + recentWatches.filterNot {
            "${it.channel.kind.name}-${it.channel.id}-${it.channel.url}" == key
        }
        val capped = updated.take(20)
        recentWatches = capped
        if (persistDisk) {
            saveRecentWatchesOnly(context, capped)
        }
    }

    fun retryCurrentPlayback() {
        val current = activeChannel ?: return
        val target = current.url
        if (target.isBlank()) {
            playbackErrorMessage = "Bu içerik için geçerli bir yayın bağlantısı bulunamadı."
            return
        }
        runCatching {
            playbackErrorMessage = null
            playbackStatusLabel = "Yeniden bağlanılıyor..."
            exoPlayer.setMediaItem(MediaItem.fromUri(target))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
            isPlayingState = true
        }
    }

    fun deletePlaylist(targetPlaylist: SavedPlaylist) {
        val removedKeys = targetPlaylist.channels.map { "${it.kind.name}-${it.id}-${it.url}" }.toSet()
        val updatedChannels = if (removedKeys.isNotEmpty()) {
            allChannels.filter { "${it.kind.name}-${it.id}-${it.url}" !in removedKeys }
        } else {
            emptyList()
        }
        val updatedPlaylists = playlists.filterNot { it.id == targetPlaylist.id }
        val updatedRecents = if (removedKeys.isNotEmpty()) {
            recentWatches.filterNot {
                "${it.channel.kind.name}-${it.channel.id}-${it.channel.url}" in removedKeys
            }
        } else {
            recentWatches
        }

        if (activeChannel != null) {
            val activeKey = "${activeChannel!!.kind.name}-${activeChannel!!.id}-${activeChannel!!.url}"
            if (activeKey in removedKeys) {
                activeChannel = null
                isPlayerFullScreen = false
            }
        }

        allChannels = updatedChannels
        playlists = updatedPlaylists
        recentWatches = updatedRecents
        statusText = "\"${targetPlaylist.title}\" listesi silindi."
        persistNow(channels = updatedChannels, lists = updatedPlaylists, recents = updatedRecents)
    }

    fun refreshPlaylist(targetPlaylist: SavedPlaylist) {
        if (targetPlaylist.sourceType == PlaylistSourceType.LOCAL_FILE || targetPlaylist.sourceType == PlaylistSourceType.STREAM) {
            statusText = "\"${targetPlaylist.title}\" yerel/tekil kaynak olduğu için doğrudan yenilenemez."
            return
        }
        val hdrs = currentHeaders()
        scope.launch {
            refreshingPlaylistId = targetPlaylist.id
            statusText = "\"${targetPlaylist.title}\" yenileniyor..."
            try {
                val refreshedItems = withContext(Dispatchers.IO) {
                    when (targetPlaylist.sourceType) {
                        PlaylistSourceType.XTREAM -> {
                            val creds = targetPlaylist.xtreamCreds
                                ?: throw IllegalStateException("Xtream bilgileri bulunamadı.")
                            fetchAllXtreamMedia(creds, hdrs)
                        }
                        PlaylistSourceType.MAC_STB -> {
                            fetchMacPortalChannels(
                                portalUrl = targetPlaylist.macPortalUrl,
                                macAddress = targetPlaylist.macAddress,
                                headers = hdrs,
                                fallbackKind = targetPlaylist.preferredKind
                            )
                        }
                        PlaylistSourceType.M3U8 -> {
                            val raw = downloadText(targetPlaylist.sourceInfo, hdrs)
                            parsePlaylistText(raw, "M3U8 Liste", targetPlaylist.preferredKind)
                        }
                        PlaylistSourceType.JSON -> {
                            val raw = downloadText(targetPlaylist.sourceInfo, hdrs)
                            parseJsonPlaylist(raw, "JSON Liste", targetPlaylist.preferredKind)
                        }
                        PlaylistSourceType.HTML -> {
                            val raw = downloadText(targetPlaylist.sourceInfo, hdrs)
                            parseHtmlStreams(raw, targetPlaylist.sourceInfo, "HTML Yayınlar", targetPlaylist.preferredKind)
                        }
                        PlaylistSourceType.MHUB -> {
                            val raw = downloadText(targetPlaylist.sourceInfo, hdrs)
                            parseMhubPlugin(raw, targetPlaylist.preferredKind)
                        }
                        PlaylistSourceType.GITHUB -> {
                            fetchGithubLists(targetPlaylist.sourceInfo, hdrs, targetPlaylist.preferredKind)
                        }
                        PlaylistSourceType.STREAM, PlaylistSourceType.LOCAL_FILE -> emptyList()
                    }
                }

                if (refreshedItems.isEmpty()) {
                    statusText = "Yenileme sırasında içerik bulunamadı."
                } else {
                    val favMap = allChannels.associate { "${it.kind.name}-${it.id}-${it.url}" to it.isFavorite }
                    val oldKeys = targetPlaylist.channels.map { "${it.kind.name}-${it.id}-${it.url}" }.toSet()
                    val enriched = refreshedItems.map { ch ->
                        val fav = favMap["${ch.kind.name}-${ch.id}-${ch.url}"] ?: false
                        ch.copy(isFavorite = fav)
                    }
                    val remaining = if (oldKeys.isNotEmpty()) {
                        allChannels.filter { "${it.kind.name}-${it.id}-${it.url}" !in oldKeys }
                    } else {
                        emptyList()
                    }
                    val mergedChannels = (enriched + remaining).distinctBy { "${it.kind.name}-${it.id}-${it.url}" }
                    val updatedPlaylists = playlists.map { pl ->
                        if (pl.id == targetPlaylist.id) {
                            pl.copy(
                                channels = if (enriched.size > 4000) emptyList() else enriched,
                                itemCountSummary = enriched.size
                            )
                        } else {
                            pl
                        }
                    }
                    allChannels = mergedChannels
                    playlists = updatedPlaylists
                    val savedOk = withContext(Dispatchers.IO) {
                        persistNow(channels = mergedChannels, lists = updatedPlaylists)
                    }
                    statusText = if (savedOk) {
                        "\"${targetPlaylist.title}\" güncellendi ve kaydedildi (${enriched.size} içerik)."
                    } else {
                        "\"${targetPlaylist.title}\" güncellendi ama kayıt hatası: ${lastSaveError ?: ""}"
                    }
                }
            } catch (e: Exception) {
                statusText = "Liste yenilenemedi: ${e.localizedMessage ?: "Bağlantı hatası"}"
            } finally {
                refreshingPlaylistId = null
            }
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

    LaunchedEffect(activeChannel, isPlayingState) {
        val shouldKeepAwake = activeChannel != null && (isPlayingState || exoPlayer.playWhenReady)
        applyKeepScreenOn(activity, shouldKeepAwake)
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                playbackStatusLabel = when (playbackState) {
                    Player.STATE_IDLE -> "Hazır"
                    Player.STATE_BUFFERING -> "Yükleniyor..."
                    Player.STATE_READY -> if (exoPlayer.isPlaying) "Oynatılıyor" else "Duraklatıldı"
                    Player.STATE_ENDED -> "Tamamlandı"
                    else -> "Hazır"
                }
                if (playbackState == Player.STATE_READY) {
                    playbackErrorMessage = null
                    liveAutoRetryCount = 0
                } else if (playbackState == Player.STATE_BUFFERING) {
                    playbackErrorMessage = null
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                currentTracksState = tracks
                val (audioOpts, textOpts) = extractTrackOptions(tracks)
                val targetAudioLang = preferredAudioLangOverride
                if (!targetAudioLang.isNullOrBlank() && targetAudioLang != "tr") {
                    val matchingAudio = audioOpts.firstOrNull {
                        it.languageCode.equals(targetAudioLang, ignoreCase = true)
                    }
                    if (matchingAudio == null) {
                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                            .buildUpon()
                            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                            .setPreferredAudioLanguage("tr")
                            .build()
                    }
                }
                val targetTextPref = preferredTextLangOverride
                if (!targetTextPref.isNullOrBlank() && targetTextPref != "OFF" && targetTextPref != "tr") {
                    val matchingText = textOpts.firstOrNull {
                        it.languageCode.equals(targetTextPref, ignoreCase = true)
                    }
                    if (matchingText == null) {
                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                            .buildUpon()
                            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                            .setPreferredTextLanguage("tr")
                            .build()
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val current = activeChannel
                if (current?.kind == MediaKind.LIVE && liveAutoRetryCount < 2 && current.url.isNotBlank()) {
                    liveAutoRetryCount += 1
                    playbackStatusLabel = "Yeniden deneniyor ($liveAutoRetryCount/2)..."
                    playbackErrorMessage = null
                    runCatching {
                        exoPlayer.setMediaItem(MediaItem.fromUri(current.url))
                        exoPlayer.prepare()
                        exoPlayer.playWhenReady = true
                        isPlayingState = true
                    }
                    return
                }
                playbackStatusLabel = "Hata"
                playbackErrorMessage = error.localizedMessage?.takeIf { it.isNotBlank() }
                    ?: "Yayın akışı açılamadı veya bağlantı kesildi."
                isPlayingState = false
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                isPlayingState = isPlaying
                if (isPlaying) {
                    playbackErrorMessage = null
                    playbackStatusLabel = "Oynatılıyor"
                } else if (exoPlayer.playbackState == Player.STATE_READY) {
                    playbackStatusLabel = "Duraklatıldı"
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                isPlayingState = playWhenReady || exoPlayer.isPlaying
            }
        }
        exoPlayer.addListener(listener)
        runCatching { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
        applyFullScreenSystemBars(activity, false)
        onDispose {
            val ch = activeChannel
            if (ch != null && ch.url.isNotBlank()) {
                val pos = runCatching { exoPlayer.currentPosition.coerceAtLeast(0L) }.getOrDefault(0L)
                recordRecentWatch(ch, pos, persistDisk = true)
            }
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
            playbackErrorMessage = null
            showTrackSelectorDialog = false
            liveAutoRetryCount = 0
            if (current != null && !target.isNullOrBlank()) {
                playbackStatusLabel = "Yükleniyor..."
                currentTracksState = Tracks.EMPTY
                val audioPref = preferredAudioLangOverride?.takeIf { it.isNotBlank() } ?: "tr"
                val textPref = preferredTextLangOverride
                val builder = exoPlayer.trackSelectionParameters
                    .buildUpon()
                    .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .setPreferredAudioLanguage(audioPref)
                if (textPref == "OFF") {
                    builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                } else {
                    builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .setPreferredTextLanguage(textPref?.takeIf { it.isNotBlank() } ?: "tr")
                }
                exoPlayer.trackSelectionParameters = builder.build()

                val savedPos = recentWatches.firstOrNull {
                    it.channel.kind == current.kind &&
                        it.channel.id == current.id &&
                        it.channel.url == current.url
                }?.lastPositionMs ?: 0L
                recordRecentWatch(current, savedPos, persistDisk = false)
                exoPlayer.setMediaItem(MediaItem.fromUri(target))
                if (current.kind != MediaKind.LIVE && savedPos > 2_000L) {
                    exoPlayer.seekTo(savedPos)
                }
                exoPlayer.prepare()
                exoPlayer.playWhenReady = autoPlayOnSelect
                isPlayingState = autoPlayOnSelect
            } else {
                playbackStatusLabel = "Hazır"
                currentTracksState = Tracks.EMPTY
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
                isPlayingState = false
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
        sourceType: PlaylistSourceType = PlaylistSourceType.M3U8,
        preferredKind: MediaKind = MediaKind.LIVE,
        xtreamCreds: XtreamCredentials? = null,
        macPortalUrl: String = "",
        macAddress: String = "",
        openPlayerFirst: ChannelEntry? = null,
        targetKind: MediaKind? = null
    ) {
        if (newChannels.isEmpty()) {
            statusText = "Geçerli içerik bulunamadı."
            return
        }
        val cappedNew = if (newChannels.size > 15_000) newChannels.take(15_000) else newChannels
        val mergedChannels = (cappedNew + allChannels)
            .distinctBy { "${it.kind.name}-${it.id}-${it.url}" }
            .take(25_000)
        allChannels = mergedChannels

        val newPlaylist = SavedPlaylist(
            id = "pl-${System.currentTimeMillis()}-${playlists.size}",
            title = playlistTitle.ifBlank { "Liste ${playlists.size + 1}" },
            sourceInfo = sourceInfo,
            channels = if (cappedNew.size > 3_000) emptyList() else cappedNew,
            itemCountSummary = cappedNew.size,
            sourceType = sourceType,
            preferredKind = preferredKind,
            xtreamCreds = xtreamCreds,
            macPortalUrl = macPortalUrl,
            macAddress = macAddress
        )
        val mergedPlaylists = (listOf(newPlaylist) + playlists).distinctBy { it.id }.take(15)
        playlists = mergedPlaylists

        val liveCount = cappedNew.count { it.kind == MediaKind.LIVE }
        val movieCount = cappedNew.count { it.kind == MediaKind.MOVIES }
        val seriesCount = cappedNew.count { it.kind == MediaKind.SERIES }
        val savedOk = persistNow(channels = mergedChannels, lists = mergedPlaylists)
        statusText = if (savedOk) {
            "Kaydedildi: $liveCount Canlı TV, $movieCount Film, $seriesCount Dizi"
        } else {
            "Kayıt uyarısı (${lastSaveError ?: "Bilinmeyen"}): $liveCount TV, $movieCount Film, $seriesCount Dizi"
        }

        if (targetKind != null) {
            selectedMediaKind = targetKind
            selectedCategoryFilter = "Tümü"
            onlyFavoritesFilter = false
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
                    sourceType = PlaylistSourceType.LOCAL_FILE,
                    preferredKind = defaultImportKind,
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
                            addImportedChannels(
                                newChannels = parsed,
                                playlistTitle = title,
                                sourceInfo = "Yerel .m3u / .m3u8 dosyası",
                                sourceType = PlaylistSourceType.LOCAL_FILE,
                                preferredKind = defaultImportKind,
                                targetKind = defaultImportKind
                            )
                            m3uNameInput = ""
                        }
                        FileImportTarget.JSON -> {
                            val parsed = parseJsonPlaylist(content, "JSON Liste", defaultImportKind)
                            val title = jsonNameInput.trim().ifEmpty { "JSON Dosyası" }
                            addImportedChannels(
                                newChannels = parsed,
                                playlistTitle = title,
                                sourceInfo = "Yerel .json dosyası",
                                sourceType = PlaylistSourceType.LOCAL_FILE,
                                preferredKind = defaultImportKind,
                                targetKind = defaultImportKind
                            )
                            jsonNameInput = ""
                        }
                        FileImportTarget.HTML -> {
                            val parsed = parseHtmlStreams(content, "", "HTML Yayınlar", defaultImportKind)
                            val title = htmlNameInput.trim().ifEmpty { "HTML Dosyası" }
                            addImportedChannels(
                                newChannels = parsed,
                                playlistTitle = title,
                                sourceInfo = "Yerel .html dosyası",
                                sourceType = PlaylistSourceType.LOCAL_FILE,
                                preferredKind = defaultImportKind,
                                targetKind = defaultImportKind
                            )
                            htmlNameInput = ""
                        }
                        FileImportTarget.MHUB -> {
                            val parsed = parseMhubPlugin(content, defaultImportKind)
                            val title = mhubNameInput.trim().ifEmpty { "mHub Eklentisi" }
                            addImportedChannels(
                                newChannels = parsed,
                                playlistTitle = title,
                                sourceInfo = "Yerel mHub dosyası",
                                sourceType = PlaylistSourceType.LOCAL_FILE,
                                preferredKind = defaultImportKind,
                                targetKind = defaultImportKind
                            )
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

    if (showResetAllDialog) {
        AlertDialog(
            onDismissRequest = { showResetAllDialog = false },
            title = { Text("Tüm Verileri Sıfırla", fontWeight = FontWeight.Bold) },
            text = {
                Text("Kayıtlı tüm listeler, favoriler, son izlenenler ve hesap bilgileri silinecek. Emin misin?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetAllDialog = false
                        activeChannel = null
                        isPlayerFullScreen = false
                        allChannels = emptyList()
                        playlists = emptyList()
                        recentWatches = emptyList()
                        xtreamNameInput = ""
                        xtreamServerInput = ""
                        xtreamUserInput = ""
                        xtreamPassInput = ""
                        userAgentInput = ""
                        refererInput = ""
                        originInput = ""
                        selectedCategoryFilter = "Tümü"
                        onlyFavoritesFilter = false
                        lastSaveTimestamp = 0L
                        lastSaveError = null
                        settingsFeedback = "Tüm kayıtlı listeler ve geçmiş sıfırlandı."
                        clearAllPersistentState(context)
                    }
                ) {
                    Text("Sıfırla", color = AccentRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetAllDialog = false }) {
                    Text("İptal")
                }
            }
        )
    }

    if (showTrackSelectorDialog && activeChannel != null) {
        val (audioOptions, textOptions) = remember(currentTracksState) {
            extractTrackOptions(currentTracksState)
        }
        val hasTextOverride = remember(exoPlayer.trackSelectionParameters) {
            exoPlayer.trackSelectionParameters.overrides.entries.any { (_, ov) ->
                textOptions.any { it.mediaTrackGroup == ov.mediaTrackGroup }
            }
        }
        val isTextDisabled = exoPlayer.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)

        AlertDialog(
            onDismissRequest = { showTrackSelectorDialog = false },
            title = { Text("Ses Dili ve Altyazı", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text(
                            text = "Ses Dili",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentRed
                        )
                    }

                    if (audioOptions.isEmpty()) {
                        item {
                            Text(
                                text = "Bu yayında ayrı ses dili bilgisi bulunamadı (varsayılan ses aktif).",
                                fontSize = 12.sp,
                                color = Color.DarkGray
                            )
                        }
                    } else {
                        itemsIndexed(audioOptions, key = { idx, opt -> "aud-$idx-${opt.title}" }) { _, opt ->
                            Surface(
                                onClick = {
                                    runCatching {
                                        preferredAudioLangOverride = opt.languageCode.ifBlank { "tr" }
                                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                            .buildUpon()
                                            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                                            .setOverrideForType(
                                                TrackSelectionOverride(opt.mediaTrackGroup, listOf(opt.trackIndex))
                                            )
                                            .build()
                                    }
                                    showTrackSelectorDialog = false
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = if (opt.isSelected) AccentRed.copy(alpha = 0.14f) else Color(0xFFF3F3F3),
                                border = BorderStroke(
                                    width = if (opt.isSelected) 1.5.dp else 1.dp,
                                    color = if (opt.isSelected) AccentRed else Color.Transparent
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = opt.title,
                                        fontSize = 13.sp,
                                        fontWeight = if (opt.isSelected) FontWeight.Bold else FontWeight.Medium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (opt.isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = AccentRed,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Altyazı",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentRed
                        )
                    }

                    if (textOptions.isEmpty()) {
                        item {
                            Text(
                                text = "Bu yayında altyazı izi bulunamadı.",
                                fontSize = 12.sp,
                                color = Color.DarkGray
                            )
                        }
                    } else {
                        item {
                            val offSelected = isTextDisabled || !hasTextOverride
                            Surface(
                                onClick = {
                                    runCatching {
                                        preferredTextLangOverride = "OFF"
                                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                            .buildUpon()
                                            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                            .build()
                                    }
                                    showTrackSelectorDialog = false
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = if (offSelected) AccentRed.copy(alpha = 0.14f) else Color(0xFFF3F3F3),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Altyazı Kapalı",
                                        fontSize = 13.sp,
                                        fontWeight = if (offSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                    if (offSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = AccentRed,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        itemsIndexed(textOptions, key = { idx, opt -> "sub-$idx-${opt.title}" }) { _, opt ->
                            val selectedNow = !isTextDisabled && opt.isSelected
                            Surface(
                                onClick = {
                                    runCatching {
                                        preferredTextLangOverride = opt.languageCode.ifBlank { "tr" }
                                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                            .buildUpon()
                                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                                            .setOverrideForType(
                                                TrackSelectionOverride(opt.mediaTrackGroup, listOf(opt.trackIndex))
                                            )
                                            .build()
                                    }
                                    showTrackSelectorDialog = false
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = if (selectedNow) AccentRed.copy(alpha = 0.14f) else Color(0xFFF3F3F3),
                                border = BorderStroke(
                                    width = if (selectedNow) 1.5.dp else 1.dp,
                                    color = if (selectedNow) AccentRed else Color.Transparent
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = opt.title,
                                        fontSize = 13.sp,
                                        fontWeight = if (selectedNow) FontWeight.Bold else FontWeight.Medium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (selectedNow) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = AccentRed,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTrackSelectorDialog = false }) {
                    Text("Kapat", color = AccentRed)
                }
            }
        )
    }

    if (isPlayerFullScreen && activeChannel != null) {
        val currentCh = activeChannel!!
        val sameCategoryChannels = remember(allChannels, currentCh.kind, currentCh.group) {
            val sameGroup = allChannels.filter { it.kind == currentCh.kind && it.group == currentCh.group }
            if (sameGroup.isNotEmpty()) sameGroup else allChannels.filter { it.kind == currentCh.kind }
        }
        val currentIdxInGroup = sameCategoryChannels.indexOfFirst {
            it.id == currentCh.id && it.kind == currentCh.kind && it.url == currentCh.url
        }.let { idx ->
            if (idx >= 0) idx else sameCategoryChannels.indexOfFirst { it.id == currentCh.id && it.kind == currentCh.kind }
        }
        val prevChannel = if (currentIdxInGroup > 0) sameCategoryChannels[currentIdxInGroup - 1] else null
        val nextChannel = if (currentIdxInGroup >= 0 && currentIdxInGroup < sameCategoryChannels.lastIndex) {
            sameCategoryChannels[currentIdxInGroup + 1]
        } else {
            null
        }
        val categoryRowState = rememberLazyListState()

        var currentPosMs by remember(currentCh.url) { mutableLongStateOf(0L) }
        var totalDurationMs by remember(currentCh.url) { mutableLongStateOf(0L) }
        var isSeeking by remember { mutableStateOf(false) }
        var seekTargetMs by remember { mutableLongStateOf(0L) }

        LaunchedEffect(activeChannel?.url, isPlayerFullScreen, showFullScreenOverlay) {
            while (isPlayerFullScreen && activeChannel != null) {
                runCatching {
                    val dur = exoPlayer.duration
                    totalDurationMs = if (dur != C.TIME_UNSET && dur > 0L) dur else 0L
                    if (!isSeeking) {
                        currentPosMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                    }
                }
                delay(500L)
            }
        }

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

            if (playbackErrorMessage != null) {
                Surface(
                    color = Color.Black.copy(alpha = 0.88f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, AccentRed.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp)
                        .widthIn(max = 420.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = AccentRed,
                            modifier = Modifier.size(30.dp)
                        )
                        Text(
                            text = "Oynatma Hatası",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = playbackErrorMessage!!,
                            color = Color.LightGray,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { retryCurrentPlayback() },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Tekrar Dene")
                            }
                            if (nextChannel != null) {
                                OutlinedButton(
                                    onClick = { activeChannel = nextChannel }
                                ) {
                                    Text("Sonraki Yayın", color = Color.White)
                                }
                            }
                        }
                    }
                }
            }

            if (showFullScreenOverlay) {
                val trackBadge = remember(currentTracksState, exoPlayer.trackSelectionParameters) {
                    val textDisabled = exoPlayer.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
                    buildTrackBadgeText(currentTracksState, textDisabled)
                }

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
                                    color = if (playbackErrorMessage != null) Color(0xFF8E1616) else AccentRed,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = playbackStatusLabel.uppercase(),
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

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        onClick = { showTrackSelectorDialog = true },
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White.copy(alpha = 0.16f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = "Ses Dili",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Dil / Ses ($trackBadge)",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                softWrap = false
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 16.dp, bottom = 76.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    IconButton(
                        onClick = { prevChannel?.let { activeChannel = it } },
                        enabled = prevChannel != null,
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                if (prevChannel != null) Color.Black.copy(alpha = 0.72f) else Color.Black.copy(alpha = 0.35f),
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Önceki Kanal",
                            tint = if (prevChannel != null) Color.White else Color.Gray
                        )
                    }
                    IconButton(
                        onClick = { nextChannel?.let { activeChannel = it } },
                        enabled = nextChannel != null,
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                if (nextChannel != null) Color.Black.copy(alpha = 0.72f) else Color.Black.copy(alpha = 0.35f),
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Sonraki Kanal",
                            tint = if (nextChannel != null) Color.White else Color.Gray
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    IconButton(
                        onClick = { fillScreenAspect = !fillScreenAspect },
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color.Black.copy(alpha = 0.72f), CircleShape)
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
                            .size(44.dp)
                            .background(AccentRed, CircleShape)
                    ) {
                        Icon(
                            imageVector = if (exoPlayer.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Oynat/Duraklat",
                            tint = Color.White
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha = 0.88f))
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val hasSeekableDuration = totalDurationMs > 0L
                    val displayedPosMs = if (isSeeking) seekTargetMs else currentPosMs
                    val sliderFraction = if (hasSeekableDuration) {
                        (displayedPosMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = formatPlayerClock(displayedPosMs),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            softWrap = false
                        )

                        Slider(
                            value = sliderFraction,
                            onValueChange = { frac ->
                                if (hasSeekableDuration) {
                                    isSeeking = true
                                    seekTargetMs = (frac * totalDurationMs).toLong().coerceIn(0L, totalDurationMs)
                                }
                            },
                            onValueChangeFinished = {
                                if (hasSeekableDuration) {
                                    val target = seekTargetMs.coerceIn(0L, totalDurationMs)
                                    runCatching {
                                        exoPlayer.seekTo(target)
                                        currentPosMs = target
                                        recordRecentWatch(currentCh, target, persistDisk = true)
                                    }
                                }
                                isSeeking = false
                            },
                            enabled = hasSeekableDuration,
                            colors = SliderDefaults.colors(
                                thumbColor = AccentRed,
                                activeTrackColor = AccentRed,
                                inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(26.dp)
                        )

                        Text(
                            text = if (hasSeekableDuration) formatPlayerClock(totalDurationMs) else "CANLI",
                            color = if (hasSeekableDuration) Color.LightGray else AccentRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            softWrap = false
                        )

                        IconButton(
                            onClick = {
                                val target = (currentPosMs - 10_000L).coerceAtLeast(0L)
                                runCatching {
                                    exoPlayer.seekTo(target)
                                    currentPosMs = target
                                }
                            },
                            enabled = hasSeekableDuration,
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Replay10,
                                contentDescription = "10 Saniye Geri",
                                tint = if (hasSeekableDuration) Color.White else Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                val maxPos = if (totalDurationMs > 0L) totalDurationMs else Long.MAX_VALUE
                                val target = (currentPosMs + 10_000L).coerceAtMost(maxPos)
                                runCatching {
                                    exoPlayer.seekTo(target)
                                    currentPosMs = target
                                }
                            },
                            enabled = hasSeekableDuration,
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Forward10,
                                contentDescription = "10 Saniye İleri",
                                tint = if (hasSeekableDuration) Color.White else Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

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
                        itemsIndexed(
                            items = sameCategoryChannels,
                            key = { rowIdx, ch -> "${currentCh.kind.name}-${rowIdx}-${ch.id}" }
                        ) { _, ch ->
                            val isCurrent = ch.id == currentCh.id && ch.kind == currentCh.kind && ch.url == currentCh.url

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
                                itemsIndexed(seriesEpisodes, key = { idx, ep -> "${idx}-${ep.id}" }) { _, ep ->
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
                                                tint = AccentRed
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
                    onClick = {
                        settingsFeedback = null
                        currentRoute = BottomRoute.SETTINGS
                    },
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

                val favoriteCountInKind = remember(kindChannels) {
                    kindChannels.count { it.isFavorite }
                }

                val resumeBadgeByKey = remember(recentWatches) {
                    recentWatches.associate { rec ->
                        val badge = if (rec.channel.kind != MediaKind.LIVE) {
                            formatDurationBadge(rec.lastPositionMs)
                        } else {
                            null
                        }
                        "${rec.channel.kind.name}-${rec.channel.id}-${rec.channel.url}" to badge
                    }
                }

                val kindRecentChannels = remember(recentWatches, allChannels, selectedMediaKind, onlyFavoritesFilter) {
                    recentWatches
                        .filter { it.channel.kind == selectedMediaKind }
                        .map { recent ->
                            allChannels.firstOrNull {
                                it.id == recent.channel.id && it.kind == recent.channel.kind
                            } ?: recent.channel
                        }
                        .filter { !onlyFavoritesFilter || it.isFavorite }
                }

                val categories = remember(kindChannels, kindRecentChannels) {
                    val baseCats = kindChannels.map { it.group }.distinct()
                    if (kindRecentChannels.isNotEmpty()) {
                        listOf("Tümü", RECENT_CATEGORY_LABEL) + baseCats
                    } else {
                        listOf("Tümü") + baseCats
                    }
                }

                LaunchedEffect(selectedMediaKind, categories) {
                    if (selectedCategoryFilter !in categories) {
                        selectedCategoryFilter = "Tümü"
                    }
                }

                val filteredAndSorted = remember(
                    kindChannels,
                    kindRecentChannels,
                    searchQuery,
                    onlyFavoritesFilter,
                    selectedCategoryFilter,
                    sortOrder
                ) {
                    val sourcePool = if (selectedCategoryFilter == RECENT_CATEGORY_LABEL) {
                        kindRecentChannels
                    } else {
                        kindChannels.filter { ch ->
                            val matchesCat = selectedCategoryFilter == "Tümü" || ch.group == selectedCategoryFilter
                            val matchesFav = !onlyFavoritesFilter || ch.isFavorite
                            matchesCat && matchesFav
                        }
                    }
                    val base = sourcePool.filter { ch ->
                        searchQuery.isBlank() ||
                            ch.name.contains(searchQuery, ignoreCase = true) ||
                            ch.group.contains(searchQuery, ignoreCase = true)
                    }
                    if (selectedCategoryFilter == RECENT_CATEGORY_LABEL && sortOrder == SortOrder.DEFAULT) {
                        base
                    } else {
                        when (sortOrder) {
                            SortOrder.DEFAULT -> base
                            SortOrder.AZ -> base.sortedBy { it.name.lowercase() }
                            SortOrder.FAVORITES_FIRST -> base.sortedByDescending { it.isFavorite }
                        }
                    }
                }

                val groupedChannels = remember(
                    filteredAndSorted,
                    kindRecentChannels,
                    selectedCategoryFilter,
                    searchQuery,
                    onlyFavoritesFilter
                ) {
                    val baseGroups = filteredAndSorted.groupBy { it.group }
                    if (selectedCategoryFilter == "Tümü" && searchQuery.isBlank() && kindRecentChannels.isNotEmpty()) {
                        linkedMapOf(RECENT_CATEGORY_LABEL to kindRecentChannels).apply {
                            putAll(baseGroups)
                        }
                    } else {
                        baseGroups
                    }
                }

                val hasActiveSubFilter = onlyFavoritesFilter || selectedCategoryFilter != "Tümü"

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
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LazyRow(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(MediaKind.entries, key = { it.name }) { kind ->
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

                        FilterChip(
                            selected = onlyFavoritesFilter,
                            onClick = { onlyFavoritesFilter = !onlyFavoritesFilter },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (onlyFavoritesFilter) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = "Favoriler ($favoriteCountInKind)",
                                    maxLines = 1,
                                    softWrap = false,
                                    fontSize = 12.sp,
                                    fontWeight = if (onlyFavoritesFilter) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentRed,
                                selectedLabelColor = Color.White,
                                selectedLeadingIconColor = Color.White
                            )
                        )
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

                    if (hasActiveSubFilter) {
                        val summaryLabel = buildString {
                            if (onlyFavoritesFilter) append("Favoriler")
                            if (selectedCategoryFilter != "Tümü") {
                                if (isNotEmpty()) append(" • ")
                                append("Kategori: $selectedCategoryFilter")
                            }
                            append(" (${filteredAndSorted.size})")
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(AccentRed.copy(alpha = 0.12f))
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = summaryLabel,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF1A1A1A),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = {
                                    selectedCategoryFilter = "Tümü"
                                    onlyFavoritesFilter = false
                                },
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
                                text = if (onlyFavoritesFilter) {
                                    "${selectedMediaKind.label} bölümünde henüz favori içerik yok. Karttaki kalp simgesine dokunarak ekleyebilirsin."
                                } else {
                                    "${selectedMediaKind.label} bölümünde içerik bulunamadı. Listelerim sekmesinden kaynak ekleyebilirsiniz."
                                },
                                color = Color.DarkGray,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(24.dp)
                            )
                        }
                    } else if (selectedCategoryFilter != "Tümü" || onlyFavoritesFilter) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(
                                items = filteredAndSorted,
                                key = { ch -> "grid-${ch.kind.name}-${ch.id}-${ch.url.hashCode()}" }
                            ) { channel ->
                                val badge = resumeBadgeByKey["${channel.kind.name}-${channel.id}-${channel.url}"]
                                MediaShelfCard(
                                    channel = channel,
                                    isSelected = activeChannel?.id == channel.id && activeChannel?.kind == channel.kind,
                                    resumeBadge = badge,
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
                            itemsIndexed(
                                items = categoryEntries,
                                key = { idx, entry -> "cat-${selectedMediaKind.name}-${idx}-${entry.key}" }
                            ) { _, (categoryName, channelList) ->
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
                                            color = if (categoryName == RECENT_CATEGORY_LABEL) AccentRed else Color(0xFF1A1A1A),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = "${channelList.size} >",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = AccentRed,
                                            softWrap = false
                                        )
                                    }

                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        itemsIndexed(
                                            items = channelList,
                                            key = { chIdx, ch -> "row-${categoryName}-${ch.kind.name}-${chIdx}-${ch.id}" }
                                        ) { _, channel ->
                                            val badge = resumeBadgeByKey["${channel.kind.name}-${channel.id}-${channel.url}"]
                                            MediaShelfCard(
                                                channel = channel,
                                                isSelected = activeChannel?.id == channel.id && activeChannel?.kind == channel.kind,
                                                resumeBadge = badge,
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
                                            selectedContainerColor = AccentRed,
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }
                            }
                        }
                    }

                    if (isLoading || refreshingPlaylistId != null) {
                        item {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = AccentRed
                            )
                        }
                    }

                    if (statusText != null) {
                        item {
                            Surface(
                                color = AccentRed.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = statusText!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = AccentRed,
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
                                                                sourceType = PlaylistSourceType.STREAM,
                                                                preferredKind = defaultImportKind,
                                                                openPlayerFirst = item,
                                                                targetKind = defaultImportKind
                                                            )
                                                        },
                                                        enabled = !isLoading && streamUrlInput.isNotBlank(),
                                                        colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
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
                                                                        sourceType = PlaylistSourceType.M3U8,
                                                                        preferredKind = defaultImportKind,
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
                                                        colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
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
                                                                        sourceType = PlaylistSourceType.JSON,
                                                                        preferredKind = defaultImportKind,
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
                                                        colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
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
                                                                        sourceType = PlaylistSourceType.HTML,
                                                                        preferredKind = defaultImportKind,
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
                                                        colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
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
                                                                    sourceType = PlaylistSourceType.XTREAM,
                                                                    preferredKind = MediaKind.LIVE,
                                                                    xtreamCreds = creds,
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
                                                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
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
                                                                    sourceType = PlaylistSourceType.MAC_STB,
                                                                    preferredKind = defaultImportKind,
                                                                    macPortalUrl = portal,
                                                                    macAddress = mac,
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
                                                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
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
                                                                        sourceType = PlaylistSourceType.MHUB,
                                                                        preferredKind = defaultImportKind,
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
                                                        colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
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
                                                                    sourceType = PlaylistSourceType.GITHUB,
                                                                    preferredKind = defaultImportKind,
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
                                                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
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

                    if (playlists.isNotEmpty()) {
                        item {
                            Text(
                                text = "Kayıtlı Listeler (${playlists.size})",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1A1A1A),
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }

                    itemsIndexed(playlists, key = { idx, pl -> "${idx}-${pl.id}" }) { _, pl ->
                        val sourceList = if (pl.channels.isNotEmpty()) pl.channels else allChannels
                        val liveCnt = sourceList.count { it.kind == MediaKind.LIVE }
                        val movieCnt = sourceList.count { it.kind == MediaKind.MOVIES }
                        val seriesCnt = sourceList.count { it.kind == MediaKind.SERIES }
                        val canRefresh = pl.sourceType != PlaylistSourceType.LOCAL_FILE &&
                            pl.sourceType != PlaylistSourceType.STREAM

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { currentRoute = BottomRoute.HOME },
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(pl.title, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        text = "TV: $liveCnt • Film: $movieCnt • Dizi: $seriesCnt",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = AccentRed,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = pl.sourceInfo,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (canRefresh) {
                                        IconButton(
                                            onClick = { refreshPlaylist(pl) },
                                            enabled = refreshingPlaylistId == null && !isLoading
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = "Listeyi Yenile",
                                                tint = Color(0xFF333333)
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { deletePlaylist(pl) }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Listeyi Sil",
                                            tint = AccentRed
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            BottomRoute.SETTINGS -> {
                val diskKb = runCatching {
                    val f = File(context.filesDir, STATE_FILE_NAME)
                    if (f.exists()) f.length() / 1024L else 0L
                }.getOrDefault(0L)

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
                            text = "Ayarlar",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1A1A1A)
                        )
                    }

                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("Kayıt Durumu", fontWeight = FontWeight.Bold, color = AccentRed)
                                Text(
                                    text = "İçerik: ${allChannels.size} • Liste: ${playlists.size} • Disk: ${diskKb} KB • Saat: ${formatClockTime(lastSaveTimestamp)}",
                                    fontSize = 12.sp,
                                    color = Color(0xFF222222)
                                )
                                if (lastSaveError != null) {
                                    Text(
                                        text = "Kayıt Uyarısı: $lastSaveError",
                                        fontSize = 12.sp,
                                        color = AccentRed,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    if (settingsFeedback != null) {
                        item {
                            Surface(
                                color = AccentRed.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = settingsFeedback!!,
                                    color = AccentRed,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }

                    item {
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
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Otomatik Oynat", fontWeight = FontWeight.SemiBold)
                                    Text(
                                        text = "İçerik seçildiğinde oynatmayı hemen başlat",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray
                                    )
                                }
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

                    item {
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
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Varsayılan Ekran Sığdırma", fontWeight = FontWeight.SemiBold)
                                    Text(
                                        text = if (fillScreenAspect) "Tam Ekran Sığdır (Zoom)" else "Orijinal En/Boy Oranı",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray
                                    )
                                }
                                Switch(
                                    checked = fillScreenAspect,
                                    onCheckedChange = {
                                        fillScreenAspect = it
                                        persistNow(zoomDefault = it)
                                    }
                                )
                            }
                        }
                    }

                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Varsayılan Açılış Bölümü", fontWeight = FontWeight.SemiBold)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    MediaKind.entries.forEach { kind ->
                                        FilterChip(
                                            selected = defaultStartKind == kind,
                                            onClick = {
                                                defaultStartKind = kind
                                                selectedMediaKind = kind
                                                selectedCategoryFilter = "Tümü"
                                                persistNow(startKind = kind)
                                            },
                                            label = { Text(kind.label, maxLines = 1, fontSize = 12.sp) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = AccentRed,
                                                selectedLabelColor = Color.White
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("Veri ve Geçmiş Yönetimi", fontWeight = FontWeight.SemiBold)

                                OutlinedButton(
                                    onClick = {
                                        recentWatches = emptyList()
                                        saveRecentWatchesOnly(context, emptyList())
                                        settingsFeedback = "Son izlenenler geçmişi temizlendi."
                                    },
                                    enabled = recentWatches.isNotEmpty(),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.History, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Son İzlenenleri Temizle (${recentWatches.size})")
                                }

                                Button(
                                    onClick = { showResetAllDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.DeleteForever, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Tüm Listeleri ve Verileri Sıfırla")
                                }
                            }
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
    resumeBadge: String? = null,
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
            color = if (isSelected) AccentRed else AccentRed.copy(alpha = 0.55f)
        ),
        modifier = modifier
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (resumeBadge != null) {
                Surface(
                    color = AccentRed,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                ) {
                    Text(
                        text = resumeBadge,
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }

            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(34.dp)
            ) {
                Icon(
                    imageVector = if (channel.isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "Favori",
                    tint = if (channel.isFavorite) AccentRed else Color.LightGray,
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
                        tint = if (isSelected) AccentRed else Color.LightGray,
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
                                if (activePosterJobs.get() < 4) {
                                    activePosterJobs.incrementAndGet()
                                    posterExecutor.execute {
                                        try {
                                            if (iv.tag == posterUrl) {
                                                val bmp = runCatching { downloadBitmapSafe(posterUrl) }.getOrNull()
                                                if (bmp != null) {
                                                    iv.post {
                                                        if (iv.tag == posterUrl) {
                                                            iv.setImageBitmap(bmp)
                                                        }
                                                    }
                                                }
                                            }
                                        } finally {
                                            activePosterJobs.decrementAndGet()
                                        }
                                    }
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
    while (redirects < 2) {
        val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 1800
            readTimeout = 2200
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
            if (bytes.isEmpty() || bytes.size > 1_000_000) return null
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
    val recentWatches: List<RecentWatchEntry> = emptyList(),
    val xtream: XtreamCredentials? = null,
    val headers: CustomHeadersConfig = CustomHeadersConfig(),
    val lastListName: String = "",
    val autoPlay: Boolean = true,
    val zoomToFillDefault: Boolean = true,
    val defaultKind: MediaKind = MediaKind.LIVE,
    val savedAtMillis: Long = 0L
)

private fun parseRecentWatchesJson(raw: String, fallbackXtream: XtreamCredentials?): List<RecentWatchEntry> {
    if (raw.isBlank()) return emptyList()
    return runCatching {
        val arr = JSONArray(raw)
        val out = mutableListOf<RecentWatchEntry>()
        for (i in 0 until arr.length()) {
            val rObj = arr.optJSONObject(i) ?: continue
            val chObj = rObj.optJSONObject("channel") ?: continue
            val parsedCh = parseChannelsArray(JSONArray().put(chObj), fallbackXtream).firstOrNull() ?: continue
            out.add(
                RecentWatchEntry(
                    channel = parsedCh,
                    lastPositionMs = rObj.optLong("lastPositionMs", 0L),
                    lastWatchedAt = rObj.optLong("lastWatchedAt", 0L)
                )
            )
        }
        out
    }.getOrDefault(emptyList())
}

private fun saveRecentWatchesOnly(context: Context, recents: List<RecentWatchEntry>) {
    runCatching {
        val recArr = JSONArray()
        recents.take(20).forEach { rec ->
            val chJson = channelsToJsonArray(listOf(rec.channel)).optJSONObject(0)
            if (chJson != null) {
                recArr.put(
                    JSONObject().apply {
                        put("channel", chJson)
                        put("lastPositionMs", rec.lastPositionMs)
                        put("lastWatchedAt", rec.lastWatchedAt)
                    }
                )
            }
        }
        File(context.filesDir, RECENT_FILE_NAME).writeText(recArr.toString(), StandardCharsets.UTF_8)
    }
}

private fun parseSnapshotJson(raw: String): PersistentSnapshot? {
    if (raw.isBlank()) return null
    return runCatching {
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
            val srcType = runCatching {
                PlaylistSourceType.valueOf(pObj.optString("sourceType", PlaylistSourceType.M3U8.name))
            }.getOrDefault(PlaylistSourceType.M3U8)
            val prefKind = runCatching {
                MediaKind.valueOf(pObj.optString("preferredKind", MediaKind.LIVE.name))
            }.getOrDefault(MediaKind.LIVE)
            val plXtObj = pObj.optJSONObject("xtreamCreds")
            val plXtream = if (plXtObj != null) {
                XtreamCredentials(
                    serverUrl = plXtObj.optString("serverUrl"),
                    username = plXtObj.optString("username"),
                    password = plXtObj.optString("password")
                )
            } else if (srcType == PlaylistSourceType.XTREAM) {
                xtream
            } else {
                null
            }

            val parsedPlChannels = parseChannelsArray(pObj.optJSONArray("channels") ?: JSONArray(), plXtream ?: xtream)
            val summaryCount = pObj.optInt("itemCountSummary", parsedPlChannels.size.coerceAtLeast(channels.size))

            playlists.add(
                SavedPlaylist(
                    id = pObj.optString("id", "pl-$i"),
                    title = pObj.optString("title", "Liste"),
                    sourceInfo = pObj.optString("sourceInfo", ""),
                    channels = parsedPlChannels,
                    itemCountSummary = summaryCount,
                    sourceType = srcType,
                    preferredKind = prefKind,
                    xtreamCreds = plXtream,
                    macPortalUrl = pObj.optString("macPortalUrl", ""),
                    macAddress = pObj.optString("macAddress", "")
                )
            )
        }

        val recentWatches = parseRecentWatchesJson(
            root.optJSONArray("recentWatches")?.toString().orEmpty(),
            xtream
        )

        val kindName = root.optString("defaultKind", MediaKind.LIVE.name)
        val parsedKind = runCatching { MediaKind.valueOf(kindName) }.getOrDefault(MediaKind.LIVE)

        PersistentSnapshot(
            channels = channels,
            playlists = playlists,
            recentWatches = recentWatches,
            xtream = xtream,
            headers = headers,
            lastListName = root.optString("lastListName", ""),
            autoPlay = root.optBoolean("autoPlay", true),
            zoomToFillDefault = root.optBoolean("zoomToFillDefault", true),
            defaultKind = parsedKind,
            savedAtMillis = root.optLong("savedAtMillis", 0L)
        )
    }.getOrNull()
}

private fun loadPersistentState(context: Context): PersistentSnapshot {
    val stateFile = File(context.filesDir, STATE_FILE_NAME)
    val tempFile = File(context.filesDir, STATE_TEMP_FILE_NAME)
    val recentFile = File(context.filesDir, RECENT_FILE_NAME)

    var baseSnapshot: PersistentSnapshot? = null

    if (stateFile.exists() && stateFile.length() > 0L) {
        baseSnapshot = runCatching { stateFile.readText(StandardCharsets.UTF_8) }
            .getOrNull()
            ?.let(::parseSnapshotJson)
    }

    if (baseSnapshot == null && tempFile.exists() && tempFile.length() > 0L) {
        baseSnapshot = runCatching { tempFile.readText(StandardCharsets.UTF_8) }
            .getOrNull()
            ?.let(::parseSnapshotJson)
    }

    if (baseSnapshot == null) {
        val prefRaw = runCatching {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_STATE_JSON, null)
        }.getOrNull().orEmpty()
        baseSnapshot = parseSnapshotJson(prefRaw)
    }

    if (baseSnapshot == null) {
        val legacyFiles = listOf("joe_channels_v27.json", "joe_player_state_v26.json", "joe_player_state.json")
        for (fileName in legacyFiles) {
            val f = File(context.filesDir, fileName)
            if (f.exists() && f.length() > 0L) {
                val parsed = runCatching { f.readText(StandardCharsets.UTF_8) }.getOrNull()?.let(::parseSnapshotJson)
                if (parsed != null && (parsed.channels.isNotEmpty() || parsed.playlists.isNotEmpty())) {
                    baseSnapshot = parsed
                    savePersistentStateWithError(context, parsed)
                    break
                }
            }
        }
    }

    val resolved = baseSnapshot ?: PersistentSnapshot()
    val recentsFromFile = if (recentFile.exists() && recentFile.length() > 0L) {
        val rawRec = runCatching { recentFile.readText(StandardCharsets.UTF_8) }.getOrNull().orEmpty()
        parseRecentWatchesJson(rawRec, resolved.xtream)
    } else {
        resolved.recentWatches
    }

    return resolved.copy(recentWatches = recentsFromFile)
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

private fun savePersistentStateWithError(
    context: Context,
    snapshot: PersistentSnapshot
): Pair<Boolean, String?> {
    return try {
        val root = JSONObject()
        root.put("lastListName", snapshot.lastListName)
        root.put("autoPlay", snapshot.autoPlay)
        root.put("zoomToFillDefault", snapshot.zoomToFillDefault)
        root.put("defaultKind", snapshot.defaultKind.name)
        root.put("savedAtMillis", snapshot.savedAtMillis)

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
                    put("itemCountSummary", pl.itemCountSummary)
                    put("sourceType", pl.sourceType.name)
                    put("preferredKind", pl.preferredKind.name)
                    put("macPortalUrl", pl.macPortalUrl)
                    put("macAddress", pl.macAddress)
                    pl.xtreamCreds?.let { x ->
                        put(
                            "xtreamCreds",
                            JSONObject().apply {
                                put("serverUrl", x.serverUrl)
                                put("username", x.username)
                                put("password", x.password)
                            }
                        )
                    }
                    put("channels", channelsToJsonArray(pl.channels))
                }
            )
        }
        root.put("playlists", plArr)

        val jsonText = root.toString()
        val stateFile = File(context.filesDir, STATE_FILE_NAME)
        val tempFile = File(context.filesDir, STATE_TEMP_FILE_NAME)
        tempFile.writeText(jsonText, StandardCharsets.UTF_8)
        if (stateFile.exists() && !stateFile.delete()) {
            stateFile.writeText(jsonText, StandardCharsets.UTF_8)
        } else {
            val moved = tempFile.renameTo(stateFile)
            if (!moved) {
                stateFile.writeText(jsonText, StandardCharsets.UTF_8)
                tempFile.delete()
            }
        }

        if (jsonText.length < 500_000) {
            runCatching {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_STATE_JSON, jsonText)
                    .commit()
            }
        }

        saveRecentWatchesOnly(context, snapshot.recentWatches)

        if (stateFile.exists() && stateFile.length() > 0L) {
            true to null
        } else {
            false to "Dosya oluşturulamadı veya boyutu 0"
        }
    } catch (e: Exception) {
        false to (e.localizedMessage ?: e.javaClass.simpleName)
    }
}

private fun clearAllPersistentState(context: Context) {
    runCatching {
        listOf(
            STATE_FILE_NAME,
            STATE_TEMP_FILE_NAME,
            RECENT_FILE_NAME,
            "joe_channels_v27.json",
            "joe_channels_v27.tmp",
            "recent_watch_v27.json",
            "joe_player_state_v26.json",
            "joe_player_state_v26.tmp",
            "joe_player_state.json",
            "joe_player_state.tmp"
        ).forEach { name ->
            File(context.filesDir, name).delete()
        }
        listOf(PREFS_NAME, "joe_player_prefs_v27", "joe_player_prefs_v26", "joe_player_prefs_v25").forEach { prefName ->
            context.getSharedPreferences(prefName, Context.MODE_PRIVATE).edit().clear().commit()
        }
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
    for (i in 0 until minOf(liveArray.length(), 8_000)) {
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
    for (i in 0 until minOf(vodArray.length(), 5_000)) {
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
    for (i in 0 until minOf(seriesArray.length(), 3_000)) {
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
