package com.joe.player

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

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

enum class PlaylistType(val label: String) {
    XTREAM("Xtream Codes"),
    M3U("M3U / M3U8"),
    GITHUB("GitHub Liste")
}

data class JoePlaylist(
    val id: String,
    val name: String,
    val type: PlaylistType,
    val serverUrl: String,
    val username: String = "",
    val password: String = "",
    val lastUpdated: String = ""
)

enum class MediaCategory {
    LIVE, MOVIE, SERIES
}

data class JoeChannelItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val group: String = "Genel",
    val streamUrl: String = "",
    val category: MediaCategory = MediaCategory.LIVE,
    val isLive: Boolean = true
)

enum class JoeTab(val label: String, val icon: ImageVector) {
    LIVE("Canlı TV", Icons.Rounded.LiveTv),
    MOVIES("Filmler", Icons.Rounded.Movie),
    SERIES("Diziler", Icons.Rounded.Tv),
    PLAYLISTS("Listeler", Icons.Rounded.PlaylistPlay)
}

object JoePlaylistStorage {
    private const val PREFS_NAME = "joe_player_prefs"
    private const val KEY_PLAYLISTS = "saved_playlists_json"
    private const val KEY_ACTIVE_ID = "active_playlist_id"
    private const val KEY_CACHE_PREFIX = "playlist_items_"

    fun loadPlaylists(context: Context): List<JoePlaylist> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_PLAYLISTS, null) ?: return emptyList()
        val result = mutableListOf<JoePlaylist>()
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val typeName = obj.optString("type", PlaylistType.M3U.name)
                val type = runCatching { PlaylistType.valueOf(typeName) }.getOrDefault(PlaylistType.M3U)
                result.add(
                    JoePlaylist(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        type = type,
                        serverUrl = obj.getString("serverUrl"),
                        username = obj.optString("username", ""),
                        password = obj.optString("password", ""),
                        lastUpdated = obj.optString("lastUpdated", "")
                    )
                )
            }
        } catch (_: Exception) {
        }
        return result
    }

    fun savePlaylists(context: Context, playlists: List<JoePlaylist>) {
        val array = JSONArray()
        playlists.forEach { item ->
            val obj = JSONObject().apply {
                put("id", item.id)
                put("name", item.name)
                put("type", item.type.name)
                put("serverUrl", item.serverUrl)
                put("username", item.username)
                put("password", item.password)
                put("lastUpdated", item.lastUpdated)
            }
            array.put(obj)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PLAYLISTS, array.toString())
            .apply()
    }

    fun saveCachedItems(context: Context, playlistId: String, items: List<JoeChannelItem>) {
        val array = JSONArray()
        items.forEach { item ->
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("subtitle", item.subtitle)
                put("group", item.group)
                put("streamUrl", item.streamUrl)
                put("category", item.category.name)
                put("isLive", item.isLive)
            }
            array.put(obj)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CACHE_PREFIX + playlistId, array.toString())
            .apply()
    }

    fun loadCachedItems(context: Context, playlistId: String): List<JoeChannelItem> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CACHE_PREFIX + playlistId, null) ?: return emptyList()
        val result = mutableListOf<JoeChannelItem>()
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val catName = obj.optString("category", MediaCategory.LIVE.name)
                val category = runCatching { MediaCategory.valueOf(catName) }.getOrDefault(MediaCategory.LIVE)
                result.add(
                    JoeChannelItem(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        subtitle = obj.optString("subtitle", ""),
                        group = obj.optString("group", "Genel"),
                        streamUrl = obj.optString("streamUrl", ""),
                        category = category,
                        isLive = obj.optBoolean("isLive", true)
                    )
                )
            }
        } catch (_: Exception) {
        }
        return result
    }

    fun deleteCachedItems(context: Context, playlistId: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_CACHE_PREFIX + playlistId)
            .apply()
    }

    fun getActivePlaylistId(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ACTIVE_ID, null)
    }

    fun setActivePlaylistId(context: Context, id: String?) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ACTIVE_ID, id)
            .apply()
    }
}

object JoePlaylistFetcher {
    suspend fun fetchAndParse(playlist: JoePlaylist): Result<List<JoeChannelItem>> = withContext(Dispatchers.IO) {
        try {
            val items = when (playlist.type) {
                PlaylistType.XTREAM -> fetchXtream(playlist)
                PlaylistType.M3U, PlaylistType.GITHUB -> fetchM3u(playlist.serverUrl)
            }
            if (items.isEmpty()) {
                Result.failure(Exception("Listeden içerik alınamadı. Bağlantı veya giriş bilgilerini kontrol edin."))
            } else {
                Result.success(items)
            }
        } catch (e: Exception) {
            Result.failure(Exception("Bağlantı hatası: ${e.message ?: "Bilinmeyen hata"}"))
        }
    }

    private fun normalizeBaseUrl(rawUrl: String): String {
        var url = rawUrl.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://$url"
        }
        return url.trimEnd('/')
    }

    private fun downloadText(urlString: String): String {
        val url = URL(urlString)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 20000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "JOE-Player/1.0")
        }
        val code = conn.responseCode
        if (code !in 200..299) {
            throw Exception("HTTP $code")
        }
        return conn.inputStream.bufferedReader().use { it.readText() }
    }

    private fun fetchM3u(rawUrl: String): List<JoeChannelItem> {
        val url = normalizeBaseUrl(rawUrl)
        val content = downloadText(url)
        val lines = content.lines()
        val items = mutableListOf<JoeChannelItem>()

        var currentTitle = ""
        var currentGroup = "Genel"

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            if (line.startsWith("#EXTINF:", ignoreCase = true)) {
                val groupMatch = Regex("""group-title="([^"]*)"""", RegexOption.IGNORE_CASE).find(line)
                currentGroup = groupMatch?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() } ?: "Genel"
                val commaIndex = line.lastIndexOf(',')
                currentTitle = if (commaIndex != -1 && commaIndex + 1 < line.length) {
                    line.substring(commaIndex + 1).trim()
                } else {
                    "Kanal ${items.size + 1}"
                }
            } else if (!line.startsWith("#") && (line.startsWith("http://") || line.startsWith("https://"))) {
                val lowerUrl = line.lowercase(Locale.ROOT)
                val lowerGroup = currentGroup.lowercase(Locale.ROOT)
                val category = when {
                    lowerUrl.contains("/series/") || lowerGroup.contains("dizi") || lowerGroup.contains("series") -> MediaCategory.SERIES
                    lowerUrl.contains("/movie/") || lowerUrl.endsWith(".mp4") || lowerUrl.endsWith(".mkv") || lowerUrl.endsWith(".avi") || lowerGroup.contains("film") || lowerGroup.contains("vod") || lowerGroup.contains("movie") -> MediaCategory.MOVIE
                    else -> MediaCategory.LIVE
                }
                val title = currentTitle.ifBlank { "Yayın ${items.size + 1}" }
                items.add(
                    JoeChannelItem(
                        id = "${items.size}_${title.hashCode()}",
                        title = title,
                        subtitle = currentGroup,
                        group = currentGroup,
                        streamUrl = line,
                        category = category,
                        isLive = category == MediaCategory.LIVE
                    )
                )
                currentTitle = ""
            }
        }
        return items
    }

    private fun fetchXtream(playlist: JoePlaylist): List<JoeChannelItem> {
        val baseUrl = normalizeBaseUrl(playlist.serverUrl)
        val user = URLEncoder.encode(playlist.username.trim(), "UTF-8")
        val pass = URLEncoder.encode(playlist.password.trim(), "UTF-8")
        val items = mutableListOf<JoeChannelItem>()

        runCatching {
            val liveJson = downloadText("$baseUrl/player_api.php?username=$user&password=$pass&action=get_live_streams")
            val array = JSONArray(liveJson)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val streamId = obj.optString("stream_id", "")
                val name = obj.optString("name", "Kanal ${i + 1}")
                val categoryName = obj.optString("category_name", "Canlı TV").ifBlank { "Canlı TV" }
                if (streamId.isNotEmpty()) {
                    items.add(
                        JoeChannelItem(
                            id = "live_$streamId",
                            title = name,
                            subtitle = categoryName,
                            group = categoryName,
                            streamUrl = "$baseUrl/live/${playlist.username}/${playlist.password}/$streamId.m3u8",
                            category = MediaCategory.LIVE,
                            isLive = true
                        )
                    )
                }
            }
        }

        runCatching {
            val vodJson = downloadText("$baseUrl/player_api.php?username=$user&password=$pass&action=get_vod_streams")
            val array = JSONArray(vodJson)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val streamId = obj.optString("stream_id", "")
                val name = obj.optString("name", "Film ${i + 1}")
                val ext = obj.optString("container_extension", "mp4").ifBlank { "mp4" }
                val categoryName = obj.optString("category_name", "Filmler").ifBlank { "Filmler" }
                if (streamId.isNotEmpty()) {
                    items.add(
                        JoeChannelItem(
                            id = "vod_$streamId",
                            title = name,
                            subtitle = categoryName,
                            group = categoryName,
                            streamUrl = "$baseUrl/movie/${playlist.username}/${playlist.password}/$streamId.$ext",
                            category = MediaCategory.MOVIE,
                            isLive = false
                        )
                    )
                }
            }
        }

        runCatching {
            val seriesJson = downloadText("$baseUrl/player_api.php?username=$user&password=$pass&action=get_series")
            val array = JSONArray(seriesJson)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val seriesId = obj.optString("series_id", "")
                val name = obj.optString("name", "Dizi ${i + 1}")
                val categoryName = obj.optString("category_name", "Diziler").ifBlank { "Diziler" }
                if (seriesId.isNotEmpty()) {
                    items.add(
                        JoeChannelItem(
                            id = "series_$seriesId",
                            title = name,
                            subtitle = categoryName,
                            group = categoryName,
                            streamUrl = "$baseUrl/player_api.php?username=$user&password=$pass&action=get_series_info&series_id=$seriesId",
                            category = MediaCategory.SERIES,
                            isLive = false
                        )
                    )
                }
            }
        }

        if (items.isEmpty()) {
            return fetchM3u("$baseUrl/get.php?username=$user&password=$pass&type=m3u_plus&output=m3u8")
        }

        return items
    }

    suspend fun resolvePlayableUrl(item: JoeChannelItem, playlist: JoePlaylist?): String = withContext(Dispatchers.IO) {
        if (item.category != MediaCategory.SERIES || !item.streamUrl.contains("get_series_info")) {
            return@withContext item.streamUrl
        }
        try {
            val infoText = downloadText(item.streamUrl)
            val root = JSONObject(infoText)
            val episodesObj = root.optJSONObject("episodes") ?: return@withContext item.streamUrl
            val seasonKeys = episodesObj.keys()
            if (seasonKeys.hasNext()) {
                val firstSeason = seasonKeys.next()
                val epArray = episodesObj.optJSONArray(firstSeason)
                if (epArray != null && epArray.length() > 0 && playlist != null) {
                    val firstEp = epArray.getJSONObject(0)
                    val epId = firstEp.optString("id", "")
                    val ext = firstEp.optString("container_extension", "mp4").ifBlank { "mp4" }
                    val baseUrl = normalizeBaseUrl(playlist.serverUrl)
                    if (epId.isNotEmpty()) {
                        return@withContext "$baseUrl/series/${playlist.username}/${playlist.password}/$epId.$ext"
                    }
                }
            }
        } catch (_: Exception) {
        }
        item.streamUrl
    }
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
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var playlists by remember { mutableStateOf(JoePlaylistStorage.loadPlaylists(context)) }
    var activePlaylistId by remember { mutableStateOf(JoePlaylistStorage.getActivePlaylistId(context)) }

    val activePlaylist = remember(playlists, activePlaylistId) {
        playlists.find { it.id == activePlaylistId } ?: playlists.firstOrNull()
    }

    var allItems by remember(activePlaylist?.id) {
        mutableStateOf(
            activePlaylist?.let { JoePlaylistStorage.loadCachedItems(context, it.id) } ?: emptyList()
        )
    }

    var isRefreshing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    var selectedTab by remember {
        mutableStateOf(if (playlists.isEmpty()) JoeTab.PLAYLISTS else JoeTab.LIVE)
    }
    var selectedCategory by remember { mutableStateOf("Tümü") }
    var selectedChannel by remember { mutableStateOf<JoeChannelItem?>(null) }
    var activePlayerChannel by remember { mutableStateOf<JoeChannelItem?>(null) }
    var activePlayableUrl by remember { mutableStateOf("") }
    var showAddPlaylistDialog by remember { mutableStateOf(false) }

    val liveChannels = remember(allItems, selectedCategory) {
        val base = allItems.filter { it.category == MediaCategory.LIVE }
        if (selectedCategory == "Tümü") base else base.filter { it.group.equals(selectedCategory, ignoreCase = true) }
    }
    val movieItems = remember(allItems, selectedCategory) {
        val base = allItems.filter { it.category == MediaCategory.MOVIE }
        if (selectedCategory == "Tümü") base else base.filter { it.group.equals(selectedCategory, ignoreCase = true) }
    }
    val seriesItems = remember(allItems, selectedCategory) {
        val base = allItems.filter { it.category == MediaCategory.SERIES }
        if (selectedCategory == "Tümü") base else base.filter { it.group.equals(selectedCategory, ignoreCase = true) }
    }

    val dynamicCategories = remember(allItems, selectedTab) {
        val tabItems = when (selectedTab) {
            JoeTab.LIVE -> allItems.filter { it.category == MediaCategory.LIVE }
            JoeTab.MOVIES -> allItems.filter { it.category == MediaCategory.MOVIE }
            JoeTab.SERIES -> allItems.filter { it.category == MediaCategory.SERIES }
            JoeTab.PLAYLISTS -> emptyList()
        }
        val groups = tabItems.map { it.group }.filter { it.isNotBlank() }.distinct().take(20)
        listOf("Tümü") + groups
    }

    fun nowFormatted(): String {
        return SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
    }

    fun launchPlayer(item: JoeChannelItem) {
        coroutineScope.launch {
            val resolvedUrl = JoePlaylistFetcher.resolvePlayableUrl(item, activePlaylist)
            activePlayableUrl = resolvedUrl
            activePlayerChannel = item
        }
    }

    fun refreshPlaylist(playlist: JoePlaylist, switchToLiveOnSuccess: Boolean = false) {
        if (isRefreshing) return
        isRefreshing = true
        statusMessage = "\"${playlist.name}\" güncelleniyor..."

        coroutineScope.launch {
            val result = JoePlaylistFetcher.fetchAndParse(playlist)
            isRefreshing = false
            result.onSuccess { fetchedItems ->
                JoePlaylistStorage.saveCachedItems(context, playlist.id, fetchedItems)
                val updatedList = playlists.map {
                    if (it.id == playlist.id) it.copy(lastUpdated = nowFormatted()) else it
                }
                playlists = updatedList
                JoePlaylistStorage.savePlaylists(context, updatedList)

                if (activePlaylist?.id == playlist.id || activePlaylistId == playlist.id) {
                    allItems = fetchedItems
                    selectedCategory = "Tümü"
                }
                statusMessage = "${fetchedItems.size} içerik yüklendi."
                if (switchToLiveOnSuccess) {
                    selectedTab = JoeTab.LIVE
                }
            }.onFailure { err ->
                statusMessage = err.message ?: "Liste güncellenemedi."
            }
        }
    }

    fun addPlaylist(newPlaylist: JoePlaylist) {
        val updated = playlists + newPlaylist
        playlists = updated
        JoePlaylistStorage.savePlaylists(context, updated)
        activePlaylistId = newPlaylist.id
        JoePlaylistStorage.setActivePlaylistId(context, newPlaylist.id)
        refreshPlaylist(newPlaylist, switchToLiveOnSuccess = true)
    }

    fun deletePlaylist(id: String) {
        val updated = playlists.filterNot { it.id == id }
        playlists = updated
        JoePlaylistStorage.savePlaylists(context, updated)
        JoePlaylistStorage.deleteCachedItems(context, id)
        if (activePlaylistId == id) {
            val nextId = updated.firstOrNull()?.id
            activePlaylistId = nextId
            JoePlaylistStorage.setActivePlaylistId(context, nextId)
        }
    }

    fun selectActivePlaylist(id: String) {
        activePlaylistId = id
        JoePlaylistStorage.setActivePlaylistId(context, id)
        selectedCategory = "Tümü"
        val cached = JoePlaylistStorage.loadCachedItems(context, id)
        allItems = cached
        if (cached.isEmpty()) {
            playlists.find { it.id == id }?.let { refreshPlaylist(it) }
        }
    }

    if (activePlayerChannel != null && activePlayableUrl.isNotEmpty()) {
        JoeVideoPlayerScreen(
            currentChannel = activePlayerChannel!!,
            streamUrl = activePlayableUrl,
            channels = liveChannels,
            onSelectChannel = { newChannel -> launchPlayer(newChannel) },
            onClosePlayer = {
                activePlayerChannel = null
                activePlayableUrl = ""
            }
        )
        return
    }

    Scaffold(
        containerColor = JoeColors.BackgroundDark,
        topBar = {
            JoeTopBar(
                activePlaylistName = activePlaylist?.name,
                isRefreshing = isRefreshing,
                onRefreshClick = {
                    activePlaylist?.let { refreshPlaylist(it) }
                }
            )
        },
        bottomBar = {
            JoeBottomBar(
                selectedTab = selectedTab,
                onSelectTab = {
                    selectedTab = it
                    selectedCategory = "Tümü"
                }
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
            statusMessage?.let { msg ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(JoeColors.RedMuted.copy(alpha = 0.85f))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = msg,
                        color = JoeColors.TextPrimary,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Kapat",
                        tint = JoeColors.TextPrimary,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { statusMessage = null }
                    )
                }
            }

            if (isRefreshing) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = JoeColors.RedBright,
                    trackColor = JoeColors.CardDark
                )
            }

            if (selectedTab != JoeTab.PLAYLISTS && activePlaylist != null && dynamicCategories.size > 1) {
                CategoryFilterRow(
                    categories = dynamicCategories,
                    selectedCategory = selectedCategory,
                    onSelectCategory = { selectedCategory = it }
                )
            }

            when (selectedTab) {
                JoeTab.LIVE -> {
                    if (activePlaylist == null || liveChannels.isEmpty()) {
                        EmptyMediaState(
                            hasPlaylist = activePlaylist != null,
                            playlistName = activePlaylist?.name,
                            sectionTitle = "Canlı TV",
                            isRefreshing = isRefreshing,
                            onGoToPlaylists = { selectedTab = JoeTab.PLAYLISTS },
                            onRefreshActive = { activePlaylist?.let { refreshPlaylist(it) } }
                        )
                    } else {
                        LiveContentSection(
                            channels = liveChannels,
                            onChannelClick = { launchPlayer(it) }
                        )
                    }
                }
                JoeTab.MOVIES -> {
                    if (activePlaylist == null || movieItems.isEmpty()) {
                        EmptyMediaState(
                            hasPlaylist = activePlaylist != null,
                            playlistName = activePlaylist?.name,
                            sectionTitle = "Filmler",
                            isRefreshing = isRefreshing,
                            onGoToPlaylists = { selectedTab = JoeTab.PLAYLISTS },
                            onRefreshActive = { activePlaylist?.let { refreshPlaylist(it) } }
                        )
                    } else {
                        MediaGridSection(
                            title = "Filmler (${movieItems.size})",
                            items = movieItems,
                            onItemClick = { selectedChannel = it }
                        )
                    }
                }
                JoeTab.SERIES -> {
                    if (activePlaylist == null || seriesItems.isEmpty()) {
                        EmptyMediaState(
                            hasPlaylist = activePlaylist != null,
                            playlistName = activePlaylist?.name,
                            sectionTitle = "Diziler",
                            isRefreshing = isRefreshing,
                            onGoToPlaylists = { selectedTab = JoeTab.PLAYLISTS },
                            onRefreshActive = { activePlaylist?.let { refreshPlaylist(it) } }
                        )
                    } else {
                        MediaGridSection(
                            title = "Diziler (${seriesItems.size})",
                            items = seriesItems,
                            onItemClick = { selectedChannel = it }
                        )
                    }
                }
                JoeTab.PLAYLISTS -> {
                    PlaylistManagementSection(
                        playlists = playlists,
                        activePlaylistId = activePlaylist?.id,
                        isRefreshing = isRefreshing,
                        onOpenAddDialog = { showAddPlaylistDialog = true },
                        onSelectPlaylist = { selectActivePlaylist(it) },
                        onRefreshPlaylist = { id ->
                            playlists.find { it.id == id }?.let { refreshPlaylist(it) }
                        },
                        onDeletePlaylist = { deletePlaylist(it) }
                    )
                }
            }
        }

        if (showAddPlaylistDialog) {
            AddPlaylistDialog(
                onDismiss = { showAddPlaylistDialog = false },
                onSave = { name, type, url, user, pass ->
                    addPlaylist(
                        JoePlaylist(
                            id = UUID.randomUUID().toString(),
                            name = name.trim(),
                            type = type,
                            serverUrl = url.trim(),
                            username = user.trim(),
                            password = pass.trim(),
                            lastUpdated = nowFormatted()
                        )
                    )
                    showAddPlaylistDialog = false
                }
            )
        }

        selectedChannel?.let { item ->
            QuickLaunchDialog(
                item = item,
                onDismiss = { selectedChannel = null },
                onStartPlayback = {
                    val target = item
                    selectedChannel = null
                    launchPlayer(target)
                }
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun JoeVideoPlayerScreen(
    currentChannel: JoeChannelItem,
    streamUrl: String,
    channels: List<JoeChannelItem>,
    onSelectChannel: (JoeChannelItem) -> Unit,
    onClosePlayer: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    var isOverlayVisible by remember { mutableStateOf(true) }
    var showChannelDrawer by remember { mutableStateOf(false) }
    var selectedAudioMode by remember { mutableStateOf("Türkçe (TR)") }
    var keepScreenOn by remember { mutableStateOf(true) }
    var isFullLandscape by remember { mutableStateOf(true) }
    var resizeModeIndex by remember { mutableIntStateOf(0) }
    var playerError by remember { mutableStateOf<String?>(null) }
    var isBuffering by remember { mutableStateOf(true) }

    // Sağdaki hızlı kanal listesinin kaydırma konumunu koru
    val channelListState = rememberLazyListState()
    var lastScrolledChannelId by remember { mutableStateOf<String?>(null) }

    val resizeModes = listOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT to "Sığdır",
        AspectRatioFrameLayout.RESIZE_MODE_FILL to "Tam Ekran",
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Yakınlaştır"
    )

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }

    // Kanal listesi açıldığında veya aktif kanal değiştiğinde seçili kanalı görünür konuma getir
    LaunchedEffect(showChannelDrawer, currentChannel.id, channels.size) {
        if (showChannelDrawer && channels.isNotEmpty() && lastScrolledChannelId != currentChannel.id) {
            val targetIndex = channels.indexOfFirst { it.id == currentChannel.id }
            if (targetIndex >= 0) {
                val scrollIndex = (targetIndex - 1).coerceAtLeast(0)
                channelListState.scrollToItem(scrollIndex)
                lastScrolledChannelId = currentChannel.id
            }
        }
    }

    fun exitFullScreenAndClose() {
        activity?.let { act ->
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            WindowCompat.setDecorFitsSystemWindows(act.window, true)
            WindowInsetsControllerCompat(act.window, act.window.decorView).show(
                WindowInsetsCompat.Type.systemBars()
            )
        }
        onClosePlayer()
    }

    BackHandler {
        exitFullScreenAndClose()
    }

    LaunchedEffect(isFullLandscape) {
        activity?.let { act ->
            val insetsController = WindowInsetsControllerCompat(act.window, act.window.decorView)
            if (isFullLandscape) {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                WindowCompat.setDecorFitsSystemWindows(act.window, false)
                insetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                WindowCompat.setDecorFitsSystemWindows(act.window, true)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    LaunchedEffect(streamUrl) {
        playerError = null
        isBuffering = true
        val fallbackUrl = if (streamUrl.endsWith(".m3u8")) {
            streamUrl.removeSuffix(".m3u8") + ".ts"
        } else {
            streamUrl
        }
        exoPlayer.setMediaItem(MediaItem.fromUri(streamUrl))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true

        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
            }

            override fun onPlayerError(error: PlaybackException) {
                if (fallbackUrl != streamUrl && exoPlayer.currentMediaItem?.localConfiguration?.uri.toString() == streamUrl) {
                    exoPlayer.setMediaItem(MediaItem.fromUri(fallbackUrl))
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = true
                } else {
                    isBuffering = false
                    playerError = "Yayın açılamadı: ${error.errorCodeName}"
                }
            }
        })
    }

    LaunchedEffect(selectedAudioMode) {
        val paramsBuilder = exoPlayer.trackSelectionParameters.buildUpon()
        when (selectedAudioMode) {
            "Türkçe (TR)" -> {
                paramsBuilder
                    .setPreferredAudioLanguages("tr", "tur", "tr-TR")
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            }
            "Orijinal" -> {
                paramsBuilder
                    .setPreferredAudioLanguages()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            }
            "Türkçe Altyazı" -> {
                paramsBuilder
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setPreferredTextLanguages("tr", "tur", "tr-TR")
            }
        }
        exoPlayer.trackSelectionParameters = paramsBuilder.build()
    }

    DisposableEffect(keepScreenOn) {
        val window = activity?.window
        if (keepScreenOn) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            activity?.let { act ->
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                WindowCompat.setDecorFitsSystemWindows(act.window, true)
                WindowInsetsControllerCompat(act.window, act.window.decorView).show(
                    WindowInsetsCompat.Type.systemBars()
                )
            }
            exoPlayer.release()
        }
    }

    val audioTracks = listOf("Türkçe (TR)", "Orijinal", "Türkçe Altyazı")
    val currentResizeMode = resizeModes[resizeModeIndex].first
    val currentResizeLabel = resizeModes[resizeModeIndex].second

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { isOverlayVisible = !isOverlayVisible }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = currentResizeMode
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { view ->
                view.resizeMode = currentResizeMode
            },
            modifier = Modifier.fillMaxSize()
        )

        if (isBuffering && playerError == null) {
            CircularProgressIndicator(
                color = JoeColors.RedBright,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        playerError?.let { errText ->
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(JoeColors.CardDark.copy(alpha = 0.95f))
                    .border(1.dp, JoeColors.RedBright, RoundedCornerShape(14.dp))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = errText,
                    color = JoeColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Button(
                    onClick = {
                        playerError = null
                        isBuffering = true
                        exoPlayer.setMediaItem(MediaItem.fromUri(streamUrl))
                        exoPlayer.prepare()
                        exoPlayer.playWhenReady = true
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = JoeColors.RedPrimary,
                        contentColor = JoeColors.TextPrimary
                    )
                ) {
                    Text("Tekrar Dene", fontWeight = FontWeight.Bold)
                }
            }
        }

        AnimatedVisibility(
            visible = isOverlayVisible,
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
                            onClick = { exitFullScreenAndClose() },
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

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(JoeColors.CardDark.copy(alpha = 0.9f))
                                .border(1.dp, JoeColors.RedMuted, RoundedCornerShape(10.dp))
                                .clickable {
                                    resizeModeIndex = (resizeModeIndex + 1) % resizeModes.size
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AspectRatio,
                                contentDescription = "Oran",
                                tint = JoeColors.RedBright,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = currentResizeLabel,
                                color = JoeColors.TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isFullLandscape) JoeColors.RedPrimary
                                    else JoeColors.CardDark.copy(alpha = 0.9f)
                                )
                                .border(
                                    1.dp,
                                    if (isFullLandscape) JoeColors.RedBright else JoeColors.RedMuted,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { isFullLandscape = !isFullLandscape }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = if (isFullLandscape) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                                contentDescription = "Geniş Ekran",
                                tint = JoeColors.TextPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isFullLandscape) "Geniş" else "Dikey",
                                color = JoeColors.TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
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
                        }
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
                            val active = selectedAudioMode == track
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
                                    .clickable { selectedAudioMode = track }
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

                    if (channels.isNotEmpty()) {
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
        }

        AnimatedVisibility(
            visible = isOverlayVisible && showChannelDrawer && channels.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
                .width(260.dp)
                .heightIn(max = 260.dp)
        ) {
            LazyColumn(
                state = channelListState,
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
                                lastScrolledChannelId = item.id
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

@Composable
private fun JoeTopBar(
    activePlaylistName: String?,
    isRefreshing: Boolean,
    onRefreshClick: () -> Unit
) {
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
                    text = if (activePlaylistName != null) "Aktif: $activePlaylistName" else "Kayıtlı liste yok",
                    color = JoeColors.TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        IconButton(
            onClick = onRefreshClick,
            enabled = !isRefreshing && activePlaylistName != null,
            modifier = Modifier
                .clip(CircleShape)
                .background(JoeColors.CardDark)
        ) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = "Listeyi Güncelle",
                tint = if (isRefreshing) JoeColors.TextSecondary else JoeColors.RedBright
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
private fun EmptyMediaState(
    hasPlaylist: Boolean,
    playlistName: String?,
    sectionTitle: String,
    isRefreshing: Boolean,
    onGoToPlaylists: () -> Unit,
    onRefreshActive: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(JoeColors.CardDark)
                .border(1.dp, JoeColors.RedMuted, RoundedCornerShape(16.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = if (hasPlaylist) Icons.Rounded.Sync else Icons.Rounded.PlaylistAdd,
                contentDescription = null,
                tint = JoeColors.RedBright,
                modifier = Modifier.size(44.dp)
            )
            Text(
                text = if (hasPlaylist) "$sectionTitle İçeriği Bulunamadı" else "Henüz Liste Eklenmedi",
                color = JoeColors.TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
            Text(
                text = if (hasPlaylist) {
                    "\"${playlistName ?: ""}\" listesi seçili. Sunucudan içerikleri çekmek için Listeyi Güncelle düğmesine dokun."
                } else {
                    "$sectionTitle bölümünü kullanmak için Listeler sekmesinden Xtream Codes, M3U/M3U8 veya GitHub bağlantını ekle."
                },
                color = JoeColors.TextSecondary,
                fontSize = 13.sp
            )
            Button(
                onClick = if (hasPlaylist) onRefreshActive else onGoToPlaylists,
                enabled = !isRefreshing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = JoeColors.RedPrimary,
                    contentColor = JoeColors.TextPrimary
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = if (hasPlaylist) Icons.Rounded.Refresh else Icons.Rounded.PlaylistPlay,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when {
                        isRefreshing -> "Güncelleniyor..."
                        hasPlaylist -> "Listeyi Güncelle"
                        else -> "Listeler Bölümüne Git"
                    },
                    fontWeight = FontWeight.Bold
                )
            }
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
                text = "Canlı Kanallar (${channels.size})",
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
private fun PlaylistManagementSection(
    playlists: List<JoePlaylist>,
    activePlaylistId: String?,
    isRefreshing: Boolean,
    onOpenAddDialog: () -> Unit,
    onSelectPlaylist: (String) -> Unit,
    onRefreshPlaylist: (String) -> Unit,
    onDeletePlaylist: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Listelerim",
                        color = JoeColors.TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Xtream Codes • M3U/M3U8 • GitHub",
                        color = JoeColors.TextSecondary,
                        fontSize = 12.sp
                    )
                }

                Button(
                    onClick = onOpenAddDialog,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = JoeColors.RedPrimary,
                        contentColor = JoeColors.TextPrimary
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Rounded.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Liste Ekle", fontWeight = FontWeight.Bold)
                }
            }
        }

        if (playlists.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = JoeColors.CardDark),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Kayıtlı Liste Bulunmuyor",
                            color = JoeColors.RedBright,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Yeni liste eklediğinde bilgilerin cihaz hafızasına kaydedilir; tekrar giriş yapmadan güncelleyebilir veya silebilirsin.",
                            color = JoeColors.TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        } else {
            items(playlists, key = { it.id }) { item ->
                val isActive = item.id == activePlaylistId
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(JoeColors.CardDark)
                        .border(
                            width = if (isActive) 1.5.dp else 1.dp,
                            color = if (isActive) JoeColors.RedBright else JoeColors.RedMuted,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { onSelectPlaylist(item.id) }
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(JoeColors.RedMuted)
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = item.type.label,
                                    color = JoeColors.RedBright,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (isActive) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(JoeColors.RedPrimary)
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "AKTİF",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Row {
                            IconButton(
                                onClick = { onRefreshPlaylist(item.id) },
                                enabled = !isRefreshing,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = "Güncelle",
                                    tint = if (isRefreshing) JoeColors.TextSecondary else JoeColors.RedBright
                                )
                            }
                            IconButton(
                                onClick = { onDeletePlaylist(item.id) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "Sil",
                                    tint = JoeColors.TextSecondary
                                )
                            }
                        }
                    }

                    Text(
                        text = item.name,
                        color = JoeColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = item.serverUrl,
                        color = JoeColors.TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (item.lastUpdated.isNotEmpty()) {
                        Text(
                            text = "Son güncelleme: ${item.lastUpdated}",
                            color = JoeColors.TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddPlaylistDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, type: PlaylistType, url: String, user: String, pass: String) -> Unit
) {
    var selectedType by remember { mutableStateOf(PlaylistType.XTREAM) }
    var name by remember { mutableStateOf("") }
    var serverUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val canSave = name.isNotBlank() && serverUrl.isNotBlank() &&
        (selectedType != PlaylistType.XTREAM || (username.isNotBlank() && password.isNotBlank()))

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = JoeColors.CardDark,
        titleContentColor = JoeColors.TextPrimary,
        title = {
            Text(text = "Yeni Liste Ekle", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PlaylistType.entries.forEach { type ->
                        val active = selectedType == type
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (active) JoeColors.RedPrimary else JoeColors.SurfaceDark)
                                .border(
                                    1.dp,
                                    if (active) JoeColors.RedBright else JoeColors.RedMuted,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { selectedType = type }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = when (type) {
                                    PlaylistType.XTREAM -> "Xtream"
                                    PlaylistType.M3U -> "M3U"
                                    PlaylistType.GITHUB -> "GitHub"
                                },
                                color = JoeColors.TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Liste Adı") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = {
                        Text(
                            when (selectedType) {
                                PlaylistType.XTREAM -> "Sunucu URL (http://...)"
                                PlaylistType.M3U -> "M3U / M3U8 Bağlantısı"
                                PlaylistType.GITHUB -> "GitHub Raw Liste URL"
                            }
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (selectedType == PlaylistType.XTREAM) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Kullanıcı Adı") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Şifre") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, selectedType, serverUrl, username, password) },
                enabled = canSave,
                colors = ButtonDefaults.buttonColors(
                    containerColor = JoeColors.RedPrimary,
                    contentColor = JoeColors.TextPrimary
                )
            ) {
                Text("Kaydet", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal", color = JoeColors.TextSecondary)
            }
        }
    )
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
