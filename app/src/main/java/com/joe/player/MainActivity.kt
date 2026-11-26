package com.joe.player

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.LruCache
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object JoeColors {
    val BackgroundDark = Color(0xFF1D2E36)
    val SurfaceDark = Color(0xFF152228)
    val CardDark = Color(0xFF263B45)
    val RedPrimary = Color(0xFF0088CC)
    val RedBright = Color(0xFF29B6F6)
    val RedMuted = Color(0xFF334E5C)
    val TextPrimary = Color(0xFFF1F5F7)
    val TextSecondary = Color(0xFF9EB3BD)
    val LiveBadge = Color(0xFFE53935)
}

object JoeImageCache {
    private val cache = object : LruCache<String, ImageBitmap>(120) {}

    suspend fun load(urlString: String): ImageBitmap? {
        if (urlString.isBlank()) return null
        cache.get(urlString)?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "JOE-Player/1.0")
                }
                if (conn.responseCode in 200..299) {
                    val options = BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.RGB_565
                    }
                    val bmp = conn.inputStream.use { BitmapFactory.decodeStream(it, null, options) }
                    val img = bmp?.asImageBitmap()
                    if (img != null) {
                        cache.put(urlString, img)
                    }
                    img
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}

@Composable
fun JoeNetworkPoster(
    url: String,
    modifier: Modifier = Modifier,
    fallbackIcon: ImageVector = Icons.Rounded.PlayCircleFilled
) {
    var imageBitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(url) {
        imageBitmap = JoeImageCache.load(url)
    }

    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                listOf(JoeColors.RedMuted, JoeColors.SurfaceDark)
            )
        ),
        contentAlignment = Alignment.Center
    ) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap!!,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = fallbackIcon,
                contentDescription = null,
                tint = JoeColors.RedBright,
                modifier = Modifier.size(40.dp)
            )
        }
    }
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

data class JoeEpisodeItem(
    val id: String,
    val title: String,
    val seasonNumber: String,
    val streamUrl: String
)

data class JoeChannelItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val group: String = "Genel",
    val streamUrl: String = "",
    val category: MediaCategory = MediaCategory.LIVE,
    val isLive: Boolean = true,
    val posterUrl: String = "",
    val plot: String = "",
    val year: String = "",
    val rating: String = "",
    val genre: String = "",
    val duration: String = "",
    val director: String = "",
    val cast: String = "",
    val seriesInfoUrl: String = ""
)

data class JoePlaybackSession(
    val channel: JoeChannelItem,
    val streamUrl: String,
    val episodes: List<JoeEpisodeItem> = emptyList(),
    val currentEpisodeIndex: Int = -1,
    val refererUrl: String = "",
    val userAgent: String = "",
    val cookieHeader: String = "",
    val returnToBrowserOnClose: Boolean = false
)

enum class JoeTab(val label: String, val icon: ImageVector) {
    LIVE("Canlı TV", Icons.Rounded.LiveTv),
    MOVIES("Filmler", Icons.Rounded.Movie),
    SERIES("Diziler", Icons.Rounded.Tv),
    BROWSER("Tarayıcı", Icons.Rounded.Language),
    PLAYLISTS("Listeler", Icons.Rounded.PlaylistPlay)
}

data class BrowserBookmark(
    val title: String,
    val url: String
)

data class BrowserHistoryEntry(
    val title: String,
    val url: String,
    val visitedAt: String
)

object JoeBrowserStorage {
    private const val PREFS_NAME = "joe_browser_prefs"
    private const val KEY_BOOKMARKS = "bookmarks_json"
    private const val KEY_HISTORY = "history_json"

    private val defaultBookmarks = listOf(
        BrowserBookmark("Google", "https://www.google.com"),
        BrowserBookmark("YouTube", "https://m.youtube.com"),
        BrowserBookmark("Canlı TV", "https://www.google.com/search?q=canli+tv+izle"),
        BrowserBookmark("Film İzle", "https://www.google.com/search?q=film+izle")
    )

    fun loadBookmarks(context: Context): List<BrowserBookmark> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_BOOKMARKS, null) ?: return defaultBookmarks
        val list = mutableListOf<BrowserBookmark>()
        runCatching {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val url = obj.optString("url", "").trim()
                val title = obj.optString("title", url).trim().ifBlank { url }
                if (url.isNotEmpty()) {
                    list.add(BrowserBookmark(title = title, url = url))
                }
            }
        }
        return if (list.isEmpty()) defaultBookmarks else list
    }

    fun saveBookmarks(context: Context, bookmarks: List<BrowserBookmark>) {
        val arr = JSONArray()
        bookmarks.forEach { bm ->
            arr.put(
                JSONObject().apply {
                    put("title", bm.title)
                    put("url", bm.url)
                }
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BOOKMARKS, arr.toString())
            .apply()
    }

    fun loadHistory(context: Context): List<BrowserHistoryEntry> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        val list = mutableListOf<BrowserHistoryEntry>()
        runCatching {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val url = obj.optString("url", "").trim()
                val title = obj.optString("title", url).trim().ifBlank { url }
                val visitedAt = obj.optString("visitedAt", "")
                if (url.isNotEmpty()) {
                    list.add(BrowserHistoryEntry(title = title, url = url, visitedAt = visitedAt))
                }
            }
        }
        return list
    }

    fun saveHistory(context: Context, history: List<BrowserHistoryEntry>) {
        val arr = JSONArray()
        history.take(80).forEach { entry ->
            arr.put(
                JSONObject().apply {
                    put("title", entry.title)
                    put("url", entry.url)
                    put("visitedAt", entry.visitedAt)
                }
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_HISTORY, arr.toString())
            .apply()
    }
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

    suspend fun saveCachedItems(context: Context, playlistId: String, items: List<JoeChannelItem>) =
        withContext(Dispatchers.Default) {
            try {
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
                        put("posterUrl", item.posterUrl)
                        put("plot", item.plot)
                        put("year", item.year)
                        put("rating", item.rating)
                        put("genre", item.genre)
                        put("duration", item.duration)
                        put("director", item.director)
                        put("cast", item.cast)
                        put("seriesInfoUrl", item.seriesInfoUrl)
                    }
                    array.put(obj)
                }
                val jsonStr = array.toString()
                withContext(Dispatchers.IO) {
                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putString(KEY_CACHE_PREFIX + playlistId, jsonStr)
                        .apply()
                }
            } catch (_: OutOfMemoryError) {
            } catch (_: Exception) {
            }
        }

    suspend fun loadCachedItems(context: Context, playlistId: String): List<JoeChannelItem> =
        withContext(Dispatchers.IO) {
            val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_CACHE_PREFIX + playlistId, null) ?: return@withContext emptyList()
            withContext(Dispatchers.Default) {
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
                                isLive = obj.optBoolean("isLive", true),
                                posterUrl = obj.optString("posterUrl", ""),
                                plot = obj.optString("plot", ""),
                                year = obj.optString("year", ""),
                                rating = obj.optString("rating", ""),
                                genre = obj.optString("genre", ""),
                                duration = obj.optString("duration", ""),
                                director = obj.optString("director", ""),
                                cast = obj.optString("cast", ""),
                                seriesInfoUrl = obj.optString("seriesInfoUrl", "")
                            )
                        )
                    }
                } catch (_: OutOfMemoryError) {
                } catch (_: Exception) {
                }
                result
            }
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
        } catch (_: OutOfMemoryError) {
            Result.failure(Exception("Liste çok büyük olduğu için bellek sınırına ulaşıldı."))
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

    private fun buildSubtitle(group: String, year: String, rating: String, genre: String): String {
        val meta = listOfNotNull(
            year.takeIf { it.isNotBlank() },
            rating.takeIf { it.isNotBlank() && it != "0" && it != "0.0" }?.let { "★ $it" },
            genre.takeIf { it.isNotBlank() } ?: group.takeIf { it.isNotBlank() }
        )
        return if (meta.isEmpty()) group else meta.joinToString(" • ")
    }

    private fun fetchCategoryMap(apiBase: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        runCatching {
            val text = downloadText(apiBase)
            val arr = JSONArray(text)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.optString("category_id", "")
                val name = obj.optString("category_name", "")
                if (id.isNotEmpty() && name.isNotEmpty()) {
                    map[id] = name
                }
            }
        }
        return map
    }

    private fun fetchM3u(rawUrl: String): List<JoeChannelItem> {
        val url = normalizeBaseUrl(rawUrl)
        val content = downloadText(url)
        val lines = content.lines()
        val items = mutableListOf<JoeChannelItem>()

        var currentTitle = ""
        var currentGroup = "Genel"
        var currentLogo = ""
        var currentYear = ""

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            if (line.startsWith("#EXTINF:", ignoreCase = true)) {
                val groupMatch = Regex("""group-title="([^"]*)"""", RegexOption.IGNORE_CASE).find(line)
                currentGroup = groupMatch?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() } ?: "Genel"

                val logoMatch = Regex("""tvg-logo="([^"]*)"""", RegexOption.IGNORE_CASE).find(line)
                currentLogo = logoMatch?.groupValues?.getOrNull(1)?.trim().orEmpty()

                val commaIndex = line.lastIndexOf(',')
                currentTitle = if (commaIndex != -1 && commaIndex + 1 < line.length) {
                    line.substring(commaIndex + 1).trim()
                } else {
                    "Kanal ${items.size + 1}"
                }

                val yearMatch = Regex("""\b(19\d{2}|20\d{2})\b""").find(currentTitle)
                currentYear = yearMatch?.groupValues?.getOrNull(1).orEmpty()
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
                        subtitle = buildSubtitle(currentGroup, currentYear, "", ""),
                        group = currentGroup,
                        streamUrl = line,
                        category = category,
                        isLive = category == MediaCategory.LIVE,
                        posterUrl = currentLogo,
                        year = currentYear
                    )
                )
                currentTitle = ""
                currentLogo = ""
                currentYear = ""
            }
        }
        return items
    }

    private fun fetchXtream(playlist: JoePlaylist): List<JoeChannelItem> {
        val baseUrl = normalizeBaseUrl(playlist.serverUrl)
        val user = URLEncoder.encode(playlist.username.trim(), "UTF-8")
        val pass = URLEncoder.encode(playlist.password.trim(), "UTF-8")
        val items = mutableListOf<JoeChannelItem>()

        val liveCategories = fetchCategoryMap("$baseUrl/player_api.php?username=$user&password=$pass&action=get_live_categories")
        val vodCategories = fetchCategoryMap("$baseUrl/player_api.php?username=$user&password=$pass&action=get_vod_categories")
        val seriesCategories = fetchCategoryMap("$baseUrl/player_api.php?username=$user&password=$pass&action=get_series_categories")

        runCatching {
            val liveJson = downloadText("$baseUrl/player_api.php?username=$user&password=$pass&action=get_live_streams")
            val array = JSONArray(liveJson)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val streamId = obj.optString("stream_id", "")
                val name = obj.optString("name", "Kanal ${i + 1}")
                val catId = obj.optString("category_id", "")
                val categoryName = obj.optString("category_name", "")
                    .ifBlank { liveCategories[catId].orEmpty() }
                    .ifBlank { "Canlı TV" }
                val icon = obj.optString("stream_icon", "")
                if (streamId.isNotEmpty()) {
                    items.add(
                        JoeChannelItem(
                            id = "live_$streamId",
                            title = name,
                            subtitle = categoryName,
                            group = categoryName,
                            streamUrl = "$baseUrl/live/${playlist.username}/${playlist.password}/$streamId.m3u8",
                            category = MediaCategory.LIVE,
                            isLive = true,
                            posterUrl = icon
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
                val catId = obj.optString("category_id", "")
                val categoryName = obj.optString("category_name", "")
                    .ifBlank { vodCategories[catId].orEmpty() }
                    .ifBlank { "Filmler" }
                val poster = obj.optString("stream_icon", obj.optString("cover", ""))
                val plot = obj.optString("plot", obj.optString("description", ""))
                val year = obj.optString("releaseDate", obj.optString("year", ""))
                    .takeIf { it.isNotBlank() }?.take(4).orEmpty()
                val rating = obj.optString("rating", obj.optString("rating_5based", ""))
                val genre = obj.optString("genre", "")
                if (streamId.isNotEmpty()) {
                    items.add(
                        JoeChannelItem(
                            id = "vod_$streamId",
                            title = name,
                            subtitle = buildSubtitle(categoryName, year, rating, genre),
                            group = categoryName,
                            streamUrl = "$baseUrl/movie/${playlist.username}/${playlist.password}/$streamId.$ext",
                            category = MediaCategory.MOVIE,
                            isLive = false,
                            posterUrl = poster,
                            plot = plot,
                            year = year,
                            rating = rating,
                            genre = genre
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
                val catId = obj.optString("category_id", "")
                val categoryName = obj.optString("category_name", "")
                    .ifBlank { seriesCategories[catId].orEmpty() }
                    .ifBlank { "Diziler" }
                val poster = obj.optString("cover", obj.optString("stream_icon", ""))
                val plot = obj.optString("plot", obj.optString("description", ""))
                val year = obj.optString("releaseDate", obj.optString("year", ""))
                    .takeIf { it.isNotBlank() }?.take(4).orEmpty()
                val rating = obj.optString("rating", obj.optString("rating_5based", ""))
                val genre = obj.optString("genre", "")
                val infoUrl = "$baseUrl/player_api.php?username=$user&password=$pass&action=get_series_info&series_id=$seriesId"
                if (seriesId.isNotEmpty()) {
                    items.add(
                        JoeChannelItem(
                            id = "series_$seriesId",
                            title = name,
                            subtitle = buildSubtitle(categoryName, year, rating, genre),
                            group = categoryName,
                            streamUrl = infoUrl,
                            category = MediaCategory.SERIES,
                            isLive = false,
                            posterUrl = poster,
                            plot = plot,
                            year = year,
                            rating = rating,
                            genre = genre,
                            seriesInfoUrl = infoUrl
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

    suspend fun fetchVodDetails(
        item: JoeChannelItem,
        playlist: JoePlaylist?
    ): JoeChannelItem = withContext(Dispatchers.IO) {
        if (playlist == null || playlist.type != PlaylistType.XTREAM || !item.id.startsWith("vod_")) {
            return@withContext item
        }
        val vodId = item.id.removePrefix("vod_")
        if (vodId.isBlank()) return@withContext item

        try {
            val baseUrl = normalizeBaseUrl(playlist.serverUrl)
            val user = URLEncoder.encode(playlist.username.trim(), "UTF-8")
            val pass = URLEncoder.encode(playlist.password.trim(), "UTF-8")
            val infoText = downloadText("$baseUrl/player_api.php?username=$user&password=$pass&action=get_vod_info&vod_id=$vodId")
            val root = JSONObject(infoText)
            val infoObj = root.optJSONObject("info") ?: return@withContext item

            val plot = infoObj.optString("plot", infoObj.optString("description", "")).ifBlank { item.plot }
            val genre = infoObj.optString("genre", "").ifBlank { item.genre }
            val year = infoObj.optString("releasedate", infoObj.optString("releaseDate", ""))
                .takeIf { it.isNotBlank() }?.take(4) ?: item.year
            val rating = infoObj.optString("rating", "").ifBlank { item.rating }
            val duration = infoObj.optString("duration", "")
            val director = infoObj.optString("director", "")
            val cast = infoObj.optString("cast", infoObj.optString("actors", ""))
            val poster = infoObj.optString("movie_image", infoObj.optString("cover_big", "")).ifBlank { item.posterUrl }

            item.copy(
                plot = plot,
                genre = genre,
                year = year,
                rating = rating,
                duration = duration,
                director = director,
                cast = cast,
                posterUrl = poster,
                subtitle = buildSubtitle(item.group, year, rating, genre)
            )
        } catch (_: Exception) {
            item
        }
    }

    suspend fun fetchSeriesDetailsAndEpisodes(
        item: JoeChannelItem,
        playlist: JoePlaylist?
    ): Pair<JoeChannelItem, List<JoeEpisodeItem>> = withContext(Dispatchers.IO) {
        val targetUrl = item.seriesInfoUrl.ifBlank { item.streamUrl }
        if (!targetUrl.contains("get_series_info") || playlist == null) {
            val fallbackEp = if (item.streamUrl.isNotBlank()) {
                listOf(JoeEpisodeItem(item.id, item.title, "1", item.streamUrl))
            } else {
                emptyList()
            }
            return@withContext item to fallbackEp
        }

        try {
            val infoText = downloadText(targetUrl)
            val root = JSONObject(infoText)
            val infoObj = root.optJSONObject("info")

            val updatedPlot = infoObj?.optString("plot", "")?.takeIf { it.isNotBlank() } ?: item.plot
            val updatedYear = infoObj?.optString("releaseDate", "")?.takeIf { it.isNotBlank() }?.take(4) ?: item.year
            val updatedRating = infoObj?.optString("rating", "")?.takeIf { it.isNotBlank() } ?: item.rating
            val updatedGenre = infoObj?.optString("genre", "")?.takeIf { it.isNotBlank() } ?: item.genre
            val updatedDirector = infoObj?.optString("director", "")?.takeIf { it.isNotBlank() } ?: item.director
            val updatedCast = infoObj?.optString("cast", "")?.takeIf { it.isNotBlank() } ?: item.cast
            val updatedPoster = infoObj?.optString("cover", "")?.takeIf { it.isNotBlank() } ?: item.posterUrl

            val enrichedItem = item.copy(
                plot = updatedPlot,
                year = updatedYear,
                rating = updatedRating,
                genre = updatedGenre,
                director = updatedDirector,
                cast = updatedCast,
                posterUrl = updatedPoster,
                subtitle = buildSubtitle(item.group, updatedYear, updatedRating, updatedGenre)
            )

            val episodes = mutableListOf<JoeEpisodeItem>()
            val episodesObj = root.optJSONObject("episodes")
            if (episodesObj != null) {
                val baseUrl = normalizeBaseUrl(playlist.serverUrl)
                val seasonKeys = episodesObj.keys().asSequence().toList().sortedWith { a, b ->
                    val numA = a.toIntOrNull()
                    val numB = b.toIntOrNull()
                    if (numA != null && numB != null) numA.compareTo(numB) else a.compareTo(b)
                }
                for (seasonKey in seasonKeys) {
                    val epArray = episodesObj.optJSONArray(seasonKey) ?: continue
                    for (i in 0 until epArray.length()) {
                        val epObj = epArray.getJSONObject(i)
                        val epId = epObj.optString("id", "")
                        val epTitle = epObj.optString("title", "Bölüm ${i + 1}").ifBlank { "Bölüm ${i + 1}" }
                        val ext = epObj.optString("container_extension", "mp4").ifBlank { "mp4" }
                        if (epId.isNotEmpty()) {
                            episodes.add(
                                JoeEpisodeItem(
                                    id = epId,
                                    title = "S${seasonKey} • ${i + 1}. $epTitle",
                                    seasonNumber = seasonKey,
                                    streamUrl = "$baseUrl/series/${playlist.username}/${playlist.password}/$epId.$ext"
                                )
                            )
                        }
                    }
                }
            }

            enrichedItem to episodes
        } catch (_: Exception) {
            val fallbackEp = if (item.streamUrl.isNotBlank()) {
                listOf(JoeEpisodeItem(item.id, item.title, "1", item.streamUrl))
            } else {
                emptyList()
            }
            item to fallbackEp
        }
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

private fun filterTabItems(
    allItems: List<JoeChannelItem>,
    targetCategory: MediaCategory,
    selectedGroup: String,
    rawQuery: String
): List<JoeChannelItem> {
    val q = rawQuery.trim()
    val matchedGroups: Set<String> = if (q.isEmpty()) {
        emptySet()
    } else {
        allItems.asSequence()
            .filter { it.category == targetCategory && it.group.contains(q, ignoreCase = true) }
            .map { it.group }
            .toSet()
    }

    return allItems.filter { item ->
        if (item.category != targetCategory) return@filter false
        val groupMatch = selectedGroup == "Tümü" || item.group.equals(selectedGroup, ignoreCase = true)
        val searchMatch = q.isEmpty() || item.group in matchedGroups || item.title.contains(q, ignoreCase = true)
        groupMatch && searchMatch
    }
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

    var allItems by remember { mutableStateOf<List<JoeChannelItem>>(emptyList()) }

    LaunchedEffect(activePlaylist?.id) {
        allItems = if (activePlaylist != null) {
            JoePlaylistStorage.loadCachedItems(context, activePlaylist.id)
        } else {
            emptyList()
        }
    }

    var isRefreshing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    var selectedTab by remember {
        mutableStateOf(if (playlists.isEmpty()) JoeTab.PLAYLISTS else JoeTab.LIVE)
    }
    var selectedCategory by remember { mutableStateOf("Tümü") }
    var categorySearchQuery by remember { mutableStateOf("") }
    var detailTarget by remember { mutableStateOf<JoeChannelItem?>(null) }
    var playbackSession by remember { mutableStateOf<JoePlaybackSession?>(null) }
    var showAddPlaylistDialog by remember { mutableStateOf(false) }
    var isBrowserFullscreen by remember { mutableStateOf(false) }

    LaunchedEffect(selectedTab) {
        if (selectedTab != JoeTab.BROWSER) {
            isBrowserFullscreen = false
        }
    }

    val allTabCategories by remember(allItems, selectedTab) {
        derivedStateOf {
            val targetCat = when (selectedTab) {
                JoeTab.LIVE -> MediaCategory.LIVE
                JoeTab.MOVIES -> MediaCategory.MOVIE
                JoeTab.SERIES -> MediaCategory.SERIES
                JoeTab.BROWSER, JoeTab.PLAYLISTS -> null
            }
            if (targetCat == null) {
                emptyList()
            } else {
                val groups = allItems.asSequence()
                    .filter { it.category == targetCat }
                    .map { it.group }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .toList()
                listOf("Tümü") + groups
            }
        }
    }

    val filteredCategories by remember(allTabCategories, categorySearchQuery) {
        derivedStateOf {
            val q = categorySearchQuery.trim()
            if (q.isEmpty()) {
                allTabCategories
            } else {
                val matched = allTabCategories.filter {
                    it == "Tümü" || it.contains(q, ignoreCase = true)
                }
                if (matched.isEmpty()) listOf("Tümü") else matched
            }
        }
    }

    val liveChannels by remember(allItems, selectedCategory, categorySearchQuery) {
        derivedStateOf {
            filterTabItems(allItems, MediaCategory.LIVE, selectedCategory, categorySearchQuery)
        }
    }

    val movieItems by remember(allItems, selectedCategory, categorySearchQuery) {
        derivedStateOf {
            filterTabItems(allItems, MediaCategory.MOVIE, selectedCategory, categorySearchQuery)
        }
    }

    val seriesItems by remember(allItems, selectedCategory, categorySearchQuery) {
        derivedStateOf {
            filterTabItems(allItems, MediaCategory.SERIES, selectedCategory, categorySearchQuery)
        }
    }

    fun nowFormatted(): String {
        return SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
    }

    fun startStream(
        item: JoeChannelItem,
        url: String,
        episodes: List<JoeEpisodeItem> = emptyList(),
        episodeIndex: Int = -1,
        refererUrl: String = "",
        userAgent: String = "",
        cookieHeader: String = "",
        returnToBrowserOnClose: Boolean = false
    ) {
        if (url.isNotBlank()) {
            isBrowserFullscreen = false
            playbackSession = JoePlaybackSession(
                channel = item,
                streamUrl = url,
                episodes = episodes,
                currentEpisodeIndex = episodeIndex,
                refererUrl = refererUrl,
                userAgent = userAgent,
                cookieHeader = cookieHeader,
                returnToBrowserOnClose = returnToBrowserOnClose
            )
        }
    }

    fun refreshPlaylist(playlist: JoePlaylist, switchToLiveOnSuccess: Boolean = false) {
        if (isRefreshing) return
        isRefreshing = true
        statusMessage = "\"${playlist.name}\" güncelleniyor..."

        coroutineScope.launch {
            val result = JoePlaylistFetcher.fetchAndParse(playlist)
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
                    categorySearchQuery = ""
                }
                statusMessage = "${fetchedItems.size} içerik yüklendi."
                if (switchToLiveOnSuccess) {
                    selectedTab = JoeTab.LIVE
                }
            }.onFailure { err ->
                statusMessage = err.message ?: "Liste güncellenemedi."
            }
            isRefreshing = false
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
        categorySearchQuery = ""
        coroutineScope.launch {
            val cached = JoePlaylistStorage.loadCachedItems(context, id)
            allItems = cached
            if (cached.isEmpty()) {
                playlists.find { it.id == id }?.let { refreshPlaylist(it) }
            }
        }
    }

    playbackSession?.let { session ->
        JoeVideoPlayerScreen(
            session = session,
            channels = liveChannels,
            onSelectChannel = { newChannel -> startStream(newChannel, newChannel.streamUrl) },
            onSelectEpisode = { targetIndex, baseSeries, epList ->
                val ep = epList.getOrNull(targetIndex)
                if (ep != null) {
                    val playMeta = baseSeries.copy(
                        title = "${baseSeries.title} - ${ep.title}",
                        subtitle = baseSeries.group
                    )
                    startStream(
                        item = playMeta,
                        url = ep.streamUrl,
                        episodes = epList,
                        episodeIndex = targetIndex
                    )
                }
            },
            onClosePlayer = {
                val shouldReturnBrowser = session.returnToBrowserOnClose
                playbackSession = null
                if (shouldReturnBrowser) {
                    selectedTab = JoeTab.BROWSER
                }
            }
        )
        return
    }

    val hideChromeForBrowserFullscreen = selectedTab == JoeTab.BROWSER && isBrowserFullscreen

    Scaffold(
        containerColor = JoeColors.BackgroundDark,
        topBar = {
            if (selectedTab != JoeTab.BROWSER && !hideChromeForBrowserFullscreen) {
                JoeTopBar(
                    activePlaylistName = activePlaylist?.name,
                    isRefreshing = isRefreshing,
                    onRefreshClick = {
                        activePlaylist?.let { refreshPlaylist(it) }
                    }
                )
            }
        },
        bottomBar = {
            if (!hideChromeForBrowserFullscreen) {
                JoeBottomBar(
                    selectedTab = selectedTab,
                    onSelectTab = {
                        selectedTab = it
                        selectedCategory = "Tümü"
                        categorySearchQuery = ""
                    }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (hideChromeForBrowserFullscreen) PaddingValues(0.dp) else innerPadding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            JoeColors.SurfaceDark,
                            JoeColors.BackgroundDark
                        )
                    )
                )
        ) {
            if (!hideChromeForBrowserFullscreen) {
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

                if (selectedTab != JoeTab.PLAYLISTS &&
                    selectedTab != JoeTab.BROWSER &&
                    activePlaylist != null &&
                    allTabCategories.size > 1
                ) {
                    CategorySearchAndFilterSection(
                        categories = filteredCategories,
                        selectedCategory = selectedCategory,
                        searchQuery = categorySearchQuery,
                        totalCategoryCount = allTabCategories.size - 1,
                        onSearchQueryChange = { query ->
                            categorySearchQuery = query
                            if (selectedCategory != "Tümü" && query.isNotBlank() &&
                                !selectedCategory.contains(query.trim(), ignoreCase = true)
                            ) {
                                selectedCategory = "Tümü"
                            }
                        },
                        onSelectCategory = { selectedCategory = it }
                    )
                }
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
                            onChannelClick = { startStream(it, it.streamUrl) }
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
                            onItemClick = { detailTarget = it }
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
                            onItemClick = { detailTarget = it }
                        )
                    }
                }
                JoeTab.BROWSER -> {
                    JoeLiteBrowserSection(
                        onFullscreenChanged = { fullscreen ->
                            isBrowserFullscreen = fullscreen
                        },
                        onPlayDetectedMedia = { mediaTitle, mediaUrl, pageUrl, ua, cookies ->
                            val webItem = JoeChannelItem(
                                id = "web_${mediaUrl.hashCode()}",
                                title = mediaTitle.ifBlank { "Web Yayını" },
                                subtitle = "Tarayıcı Akışı",
                                group = "Web",
                                streamUrl = mediaUrl,
                                category = MediaCategory.LIVE,
                                isLive = mediaUrl.contains(".m3u8", ignoreCase = true)
                            )
                            startStream(
                                item = webItem,
                                url = mediaUrl,
                                refererUrl = pageUrl,
                                userAgent = ua,
                                cookieHeader = cookies,
                                returnToBrowserOnClose = true
                            )
                        }
                    )
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

        detailTarget?.let { item ->
            MediaDetailDialog(
                item = item,
                playlist = activePlaylist,
                onDismiss = { detailTarget = null },
                onPlayMovie = { playItem, playUrl ->
                    detailTarget = null
                    startStream(playItem, playUrl)
                },
                onPlayEpisode = { baseSeries, epList, epIndex ->
                    detailTarget = null
                    val ep = epList.getOrNull(epIndex)
                    if (ep != null) {
                        val playMeta = baseSeries.copy(
                            title = "${baseSeries.title} - ${ep.title}",
                            subtitle = baseSeries.group
                        )
                        startStream(
                            item = playMeta,
                            url = ep.streamUrl,
                            episodes = epList,
                            episodeIndex = epIndex
                        )
                    }
                }
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun JoeLiteBrowserSection(
    onFullscreenChanged: (Boolean) -> Unit,
    onPlayDetectedMedia: (
        title: String,
        streamUrl: String,
        refererUrl: String,
        userAgent: String,
        cookieHeader: String
    ) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val focusManager = LocalFocusManager.current

    val homeUrl = "https://www.google.com"
    var addressText by remember { mutableStateOf(homeUrl) }
    var currentPageUrl by remember { mutableStateOf(homeUrl) }
    var currentPageTitle by remember { mutableStateOf("JOE Tarayıcı") }
    var pageProgress by remember { mutableIntStateOf(100) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var desktopMode by remember { mutableStateOf(false) }
    var detectedStreams by remember { mutableStateOf<List<String>>(emptyList()) }
    var showStreamsDialog by remember { mutableStateOf(false) }
    var showBookmarksDialog by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showFindBar by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var customFullscreenView by remember { mutableStateOf<View?>(null) }
    var customFullscreenCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    var bookmarks by remember { mutableStateOf(JoeBrowserStorage.loadBookmarks(context)) }
    var history by remember { mutableStateOf(JoeBrowserStorage.loadHistory(context)) }

    val blockedHosts = remember {
        setOf(
            "doubleclick.net", "googlesyndication.com", "googleadservices.com",
            "adservice.google.com", "popads.net", "popcash.net", "propellerads.com",
            "exoclick.com", "taboola.com", "outbrain.com", "adnxs.com"
        )
    }

    fun isMediaStreamUrl(raw: String): Boolean {
        val lower = raw.lowercase(Locale.ROOT)
        if (lower.startsWith("blob:") || lower.startsWith("data:") || lower.startsWith("javascript:")) {
            return false
        }
        if (lower.contains("googlesyndication") || lower.contains("doubleclick") || lower.contains("adsystem")) {
            return false
        }
        return lower.contains(".m3u8") ||
            lower.contains(".mp4") ||
            lower.contains(".mpd") ||
            lower.contains("playlist.m3u8") ||
            lower.contains("master.m3u8")
    }

    fun recordHistory(title: String, url: String) {
        if (url.isBlank() || url == "about:blank") return
        val stamp = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val cleanTitle = title.trim().ifBlank { url }
        val filtered = history.filterNot { it.url == url }
        val updated = (listOf(BrowserHistoryEntry(title = cleanTitle, url = url, visitedAt = stamp)) + filtered)
            .take(80)
        history = updated
        JoeBrowserStorage.saveHistory(context, updated)
    }

    fun toggleCurrentBookmark() {
        val targetUrl = currentPageUrl.trim()
        if (targetUrl.isEmpty()) return
        val exists = bookmarks.any { it.url.equals(targetUrl, ignoreCase = true) }
        val updated = if (exists) {
            bookmarks.filterNot { it.url.equals(targetUrl, ignoreCase = true) }
        } else {
            val shortTitle = currentPageTitle.trim()
                .takeIf { it.isNotBlank() && it != "JOE Tarayıcı" }
                ?.take(22)
                ?: UriHostOrTitle(targetUrl)
            listOf(BrowserBookmark(title = shortTitle, url = targetUrl)) + bookmarks
        }
        bookmarks = updated
        JoeBrowserStorage.saveBookmarks(context, updated)
    }

    fun exitWebFullscreen() {
        val viewToRemove = customFullscreenView
        val cb = customFullscreenCallback
        customFullscreenView = null
        customFullscreenCallback = null
        onFullscreenChanged(false)
        if (viewToRemove != null) {
            (viewToRemove.parent as? ViewGroup)?.removeView(viewToRemove)
        }
        runCatching { cb?.onCustomViewHidden() }
        activity?.let { act ->
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            WindowCompat.setDecorFitsSystemWindows(act.window, true)
            WindowInsetsControllerCompat(act.window, act.window.decorView).show(
                WindowInsetsCompat.Type.systemBars()
            )
        }
    }

    LaunchedEffect(customFullscreenView) {
        val isFs = customFullscreenView != null
        onFullscreenChanged(isFs)
        if (isFs) {
            showFindBar = false
            showMoreMenu = false
            activity?.let { act ->
                val insetsController = WindowInsetsControllerCompat(act.window, act.window.decorView)
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                WindowCompat.setDecorFitsSystemWindows(act.window, false)
                insetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val webView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.parseColor("#1D2E36"))
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = false
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                useWideViewPort = true
                loadWithOverviewMode = true
                mediaPlaybackRequiresUserGesture = false
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    pageProgress = newProgress
                    canGoBack = view?.canGoBack() == true
                    canGoForward = view?.canGoForward() == true
                }

                override fun onReceivedTitle(view: WebView?, title: String?) {
                    if (!title.isNullOrBlank()) {
                        currentPageTitle = title
                        val activeUrl = view?.url ?: currentPageUrl
                        recordHistory(title, activeUrl)
                    }
                }

                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                    if (view == null) {
                        callback?.onCustomViewHidden()
                        return
                    }
                    if (customFullscreenView != null) {
                        callback?.onCustomViewHidden()
                        return
                    }
                    customFullscreenView = view
                    customFullscreenCallback = callback
                }

                override fun onHideCustomView() {
                    exitWebFullscreen()
                }
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val target = request?.url?.toString().orEmpty()
                    if (target.isBlank()) return false
                    if (!target.startsWith("http://") && !target.startsWith("https://")) {
                        return true
                    }
                    addressText = target
                    currentPageUrl = target
                    return false
                }

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val reqUrl = request?.url?.toString().orEmpty()
                    val host = request?.url?.host?.lowercase(Locale.ROOT).orEmpty()
                    if (blockedHosts.any { host == it || host.endsWith(".$it") }) {
                        return WebResourceResponse(
                            "text/plain",
                            "UTF-8",
                            ByteArrayInputStream(ByteArray(0))
                        )
                    }
                    if (isMediaStreamUrl(reqUrl)) {
                        post {
                            if (reqUrl !in detectedStreams) {
                                detectedStreams = (listOf(reqUrl) + detectedStreams).take(12)
                            }
                        }
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    if (!url.isNullOrBlank()) {
                        addressText = url
                        currentPageUrl = url
                    }
                    canGoBack = view?.canGoBack() == true
                    canGoForward = view?.canGoForward() == true
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    if (!url.isNullOrBlank()) {
                        addressText = url
                        currentPageUrl = url
                        recordHistory(view?.title ?: currentPageTitle, url)
                    }
                    canGoBack = view?.canGoBack() == true
                    canGoForward = view?.canGoForward() == true
                }
            }
            loadUrl(homeUrl)
        }
    }

    fun launchStreamWithHeaders(streamUrl: String) {
        exitWebFullscreen()
        val activeUrl = webView.url?.takeIf { it.isNotBlank() } ?: currentPageUrl
        val activeUa = webView.settings.userAgentString.orEmpty()
        val cookies = runCatching {
            CookieManager.getInstance().getCookie(streamUrl)
                ?: CookieManager.getInstance().getCookie(activeUrl)
                ?: ""
        }.getOrDefault("")
        onPlayDetectedMedia(currentPageTitle, streamUrl, activeUrl, activeUa, cookies)
    }

    fun navigateToInput(rawInput: String) {
        exitWebFullscreen()
        showFindBar = false
        showMoreMenu = false
        webView.clearMatches()
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return
        val targetUrl = when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.contains(".") && !trimmed.contains(" ") -> "https://$trimmed"
            else -> "https://www.google.com/search?q=${URLEncoder.encode(trimmed, "UTF-8")}"
        }
        detectedStreams = emptyList()
        addressText = targetUrl
        currentPageUrl = targetUrl
        webView.loadUrl(targetUrl)
        focusManager.clearFocus()
    }

    fun copyCurrentUrl() {
        val target = webView.url?.takeIf { it.isNotBlank() } ?: currentPageUrl
        if (target.isBlank()) return
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText("JOE Tarayıcı Bağlantısı", target))
        Toast.makeText(context, "Bağlantı kopyalandı", Toast.LENGTH_SHORT).show()
    }

    fun shareCurrentUrl() {
        val target = webView.url?.takeIf { it.isNotBlank() } ?: currentPageUrl
        if (target.isBlank()) return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, currentPageTitle)
            putExtra(Intent.EXTRA_TEXT, target)
        }
        context.startActivity(Intent.createChooser(intent, "Bağlantıyı Paylaş"))
    }

    BackHandler(enabled = customFullscreenView != null || showFindBar || canGoBack || webView.canGoBack()) {
        if (customFullscreenView != null) {
            exitWebFullscreen()
        } else if (showFindBar) {
            showFindBar = false
            findQuery = ""
            webView.clearMatches()
        } else if (webView.canGoBack()) {
            webView.goBack()
            canGoBack = webView.canGoBack()
            canGoForward = webView.canGoForward()
        } else {
            canGoBack = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exitWebFullscreen()
            runCatching {
                webView.clearMatches()
                webView.stopLoading()
                webView.clearHistory()
                webView.clearCache(true)
                webView.destroy()
            }
        }
    }

    val isBookmarked = bookmarks.any { it.url.equals(currentPageUrl.trim(), ignoreCase = true) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(JoeColors.BackgroundDark)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(JoeColors.SurfaceDark)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = {
                        if (webView.canGoBack()) {
                            webView.goBack()
                            canGoBack = webView.canGoBack()
                            canGoForward = webView.canGoForward()
                        }
                    },
                    enabled = canGoBack || webView.canGoBack(),
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowBack,
                        contentDescription = "Geri",
                        tint = if (canGoBack || webView.canGoBack()) JoeColors.TextPrimary else JoeColors.TextSecondary.copy(alpha = 0.4f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = {
                        if (webView.canGoForward()) {
                            webView.goForward()
                            canGoBack = webView.canGoBack()
                            canGoForward = webView.canGoForward()
                        }
                    },
                    enabled = canGoForward || webView.canGoForward(),
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowForward,
                        contentDescription = "İleri",
                        tint = if (canGoForward || webView.canGoForward()) JoeColors.TextPrimary else JoeColors.TextSecondary.copy(alpha = 0.4f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                OutlinedTextField(
                    value = addressText,
                    onValueChange = { addressText = it },
                    singleLine = true,
                    placeholder = {
                        Text(
                            text = "Ara veya site adresi gir...",
                            color = JoeColors.TextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    },
                    trailingIcon = {
                        if (addressText.isNotEmpty()) {
                            IconButton(
                                onClick = { addressText = "" },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Temizle",
                                    tint = JoeColors.TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { navigateToInput(addressText) }),
                    shape = RoundedCornerShape(20.dp),
                    textStyle = LocalTextStyle.current.copy(
                        color = JoeColors.TextPrimary,
                        fontSize = 13.sp
                    ),
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = {
                        if (pageProgress < 100) webView.stopLoading() else webView.reload()
                    },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = if (pageProgress < 100) Icons.Rounded.Close else Icons.Rounded.Refresh,
                        contentDescription = "Yenile",
                        tint = JoeColors.RedBright,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            if (pageProgress in 1..99) {
                LinearProgressIndicator(
                    progress = { pageProgress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = JoeColors.RedBright,
                    trackColor = JoeColors.SurfaceDark
                )
            }

            if (showFindBar) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(JoeColors.CardDark)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = findQuery,
                        onValueChange = { q ->
                            findQuery = q
                            if (q.isNotBlank()) {
                                webView.findAllAsync(q)
                            } else {
                                webView.clearMatches()
                            }
                        },
                        singleLine = true,
                        placeholder = {
                            Text(
                                text = "Sayfada kelime ara...",
                                color = JoeColors.TextSecondary,
                                fontSize = 12.sp
                            )
                        },
                        textStyle = LocalTextStyle.current.copy(
                            color = JoeColors.TextPrimary,
                            fontSize = 13.sp
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = { webView.findNext(false) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowUp,
                            contentDescription = "Önceki",
                            tint = JoeColors.TextPrimary
                        )
                    }

                    IconButton(
                        onClick = { webView.findNext(true) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "Sonraki",
                            tint = JoeColors.TextPrimary
                        )
                    }

                    IconButton(
                        onClick = {
                            showFindBar = false
                            findQuery = ""
                            webView.clearMatches()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Kapat",
                            tint = JoeColors.TextSecondary
                        )
                    }
                }
            }

            AndroidView(
                factory = { webView },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(JoeColors.SurfaceDark)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(JoeColors.RedPrimary.copy(alpha = 0.25f))
                        .border(1.dp, JoeColors.RedBright, RoundedCornerShape(14.dp))
                        .clickable { showBookmarksDialog = true }
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Bookmark,
                            contentDescription = null,
                            tint = JoeColors.RedBright,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Yer İmleri (${bookmarks.size})",
                            color = JoeColors.RedBright,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(bookmarks) { bm ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(JoeColors.CardDark)
                                .border(1.dp, JoeColors.RedMuted, RoundedCornerShape(14.dp))
                                .clickable { navigateToInput(bm.url) }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = bm.title,
                                color = JoeColors.TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                }

                if (detectedStreams.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(JoeColors.RedPrimary)
                            .clickable {
                                if (detectedStreams.size == 1) {
                                    launchStreamWithHeaders(detectedStreams.first())
                                } else {
                                    showStreamsDialog = true
                                }
                            }
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "(${detectedStreams.size})",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Box {
                    IconButton(
                        onClick = { showMoreMenu = true },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "Menü",
                            tint = JoeColors.TextPrimary,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (isBookmarked) "Yer İmlerinden Kaldır" else "Yer İmlerine Ekle",
                                    color = JoeColors.TextPrimary,
                                    fontSize = 13.sp
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isBookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkAdd,
                                    contentDescription = null,
                                    tint = JoeColors.RedBright
                                )
                            },
                            onClick = {
                                showMoreMenu = false
                                toggleCurrentBookmark()
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text("Geçmiş (${history.size})", color = JoeColors.TextPrimary, fontSize = 13.sp)
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.History,
                                    contentDescription = null,
                                    tint = JoeColors.RedBright
                                )
                            },
                            onClick = {
                                showMoreMenu = false
                                showHistoryDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text("Sayfada Bul", color = JoeColors.TextPrimary, fontSize = 13.sp)
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = null,
                                    tint = JoeColors.RedBright
                                )
                            },
                            onClick = {
                                showMoreMenu = false
                                showFindBar = !showFindBar
                                if (!showFindBar) {
                                    findQuery = ""
                                    webView.clearMatches()
                                } else if (findQuery.isNotBlank()) {
                                    webView.findAllAsync(findQuery)
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text("Bağlantıyı Kopyala", color = JoeColors.TextPrimary, fontSize = 13.sp)
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.ContentCopy,
                                    contentDescription = null,
                                    tint = JoeColors.RedBright
                                )
                            },
                            onClick = {
                                showMoreMenu = false
                                copyCurrentUrl()
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text("Paylaş", color = JoeColors.TextPrimary, fontSize = 13.sp)
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Share,
                                    contentDescription = null,
                                    tint = JoeColors.RedBright
                                )
                            },
                            onClick = {
                                showMoreMenu = false
                                shareCurrentUrl()
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (desktopMode) "Masaüstü Görünüm: Açık" else "Masaüstü Görünüm: Kapalı",
                                    color = if (desktopMode) JoeColors.RedBright else JoeColors.TextPrimary,
                                    fontSize = 13.sp
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (desktopMode) Icons.Rounded.DesktopWindows else Icons.Rounded.Language,
                                    contentDescription = null,
                                    tint = if (desktopMode) JoeColors.RedBright else JoeColors.TextSecondary
                                )
                            },
                            onClick = {
                                showMoreMenu = false
                                desktopMode = !desktopMode
                                webView.settings.userAgentString = if (desktopMode) {
                                    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                                } else {
                                    null
                                }
                                webView.reload()
                            }
                        )
                    }
                }
            }
        }

        customFullscreenView?.let { fsView ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                AndroidView(
                    factory = {
                        FrameLayout(it).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            setBackgroundColor(android.graphics.Color.BLACK)
                            (fsView.parent as? ViewGroup)?.removeView(fsView)
                            addView(
                                fsView,
                                ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    if (showBookmarksDialog) {
        AlertDialog(
            onDismissRequest = { showBookmarksDialog = false },
            containerColor = JoeColors.CardDark,
            titleContentColor = JoeColors.TextPrimary,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Yer İmleri", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    TextButton(onClick = { toggleCurrentBookmark() }) {
                        Text(
                            text = if (isBookmarked) "Sayfayı Kaldır" else "Bu Sayfayı Ekle",
                            color = JoeColors.RedBright,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (bookmarks.isEmpty()) {
                        Text(
                            text = "Kayıtlı yer imi yok.",
                            color = JoeColors.TextSecondary,
                            fontSize = 12.sp
                        )
                    } else {
                        bookmarks.forEach { bm ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(JoeColors.SurfaceDark)
                                    .border(1.dp, JoeColors.RedMuted, RoundedCornerShape(8.dp))
                                    .clickable {
                                        showBookmarksDialog = false
                                        navigateToInput(bm.url)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = bm.title,
                                        color = JoeColors.TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = bm.url,
                                        color = JoeColors.TextSecondary,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        val updated = bookmarks.filterNot { it.url == bm.url }
                                        bookmarks = updated
                                        JoeBrowserStorage.saveBookmarks(context, updated)
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = "Sil",
                                        tint = JoeColors.TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBookmarksDialog = false }) {
                    Text("Kapat", color = JoeColors.TextSecondary)
                }
            }
        )
    }

    if (showHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showHistoryDialog = false },
            containerColor = JoeColors.CardDark,
            titleContentColor = JoeColors.TextPrimary,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Tarayıcı Geçmişi", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    if (history.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                history = emptyList()
                                JoeBrowserStorage.saveHistory(context, emptyList())
                            }
                        ) {
                            Text(
                                text = "Temizle",
                                color = JoeColors.RedBright,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (history.isEmpty()) {
                        Text(
                            text = "Henüz geçmiş kaydı yok.",
                            color = JoeColors.TextSecondary,
                            fontSize = 12.sp
                        )
                    } else {
                        history.forEach { entry ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(JoeColors.SurfaceDark)
                                    .border(1.dp, JoeColors.RedMuted, RoundedCornerShape(8.dp))
                                    .clickable {
                                        showHistoryDialog = false
                                        navigateToInput(entry.url)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = entry.title,
                                        color = JoeColors.TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = entry.url,
                                        color = JoeColors.TextSecondary,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (entry.visitedAt.isNotBlank()) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = entry.visitedAt,
                                        color = JoeColors.RedBright,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHistoryDialog = false }) {
                    Text("Kapat", color = JoeColors.TextSecondary)
                }
            }
        )
    }

    if (showStreamsDialog && detectedStreams.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showStreamsDialog = false },
            containerColor = JoeColors.CardDark,
            titleContentColor = JoeColors.TextPrimary,
            title = {
                Text("Yakalanan Video Akışları", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    detectedStreams.forEachIndexed { idx, streamLink ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(JoeColors.SurfaceDark)
                                .border(1.dp, JoeColors.RedMuted, RoundedCornerShape(8.dp))
                                .clickable {
                                    showStreamsDialog = false
                                    launchStreamWithHeaders(streamLink)
                                }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${idx + 1}. $streamLink",
                                color = JoeColors.TextPrimary,
                                fontSize = 11.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = "Oynat",
                                tint = JoeColors.RedBright,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showStreamsDialog = false }) {
                    Text("Kapat", color = JoeColors.TextSecondary)
                }
            }
        )
    }
}

private fun UriHostOrTitle(rawUrl: String): String {
    return runCatching {
        URL(rawUrl).host.removePrefix("www.")
    }.getOrDefault(rawUrl).ifBlank { "Yer İmi" }
}

@Composable
private fun CategorySearchAndFilterSection(
    categories: List<String>,
    selectedCategory: String,
    searchQuery: String,
    totalCategoryCount: Int,
    onSearchQueryChange: (String) -> Unit,
    onSelectCategory: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            singleLine = true,
            placeholder = {
                Text(
                    text = "Kategori veya içerik ara ($totalCategoryCount kategori)...",
                    color = JoeColors.TextSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = "Ara",
                    tint = JoeColors.RedBright
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Temizle",
                            tint = JoeColors.TextSecondary
                        )
                    }
                }
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        CategoryFilterRow(
            categories = categories,
            selectedCategory = selectedCategory,
            onSelectCategory = onSelectCategory
        )
    }
}

@Composable
private fun MediaDetailDialog(
    item: JoeChannelItem,
    playlist: JoePlaylist?,
    onDismiss: () -> Unit,
    onPlayMovie: (JoeChannelItem, String) -> Unit,
    onPlayEpisode: (JoeChannelItem, List<JoeEpisodeItem>, Int) -> Unit
) {
    var detailItem by remember(item.id) { mutableStateOf(item) }
    var episodes by remember(item.id) { mutableStateOf<List<JoeEpisodeItem>>(emptyList()) }
    var isLoadingDetails by remember(item.id) { mutableStateOf(true) }

    LaunchedEffect(item.id) {
        isLoadingDetails = true
        if (item.category == MediaCategory.SERIES) {
            val (enriched, epList) = JoePlaylistFetcher.fetchSeriesDetailsAndEpisodes(item, playlist)
            detailItem = enriched
            episodes = epList
        } else if (item.category == MediaCategory.MOVIE) {
            detailItem = JoePlaylistFetcher.fetchVodDetails(item, playlist)
        }
        isLoadingDetails = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = JoeColors.CardDark,
        titleContentColor = JoeColors.TextPrimary,
        textContentColor = JoeColors.TextSecondary,
        title = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                JoeNetworkPoster(
                    url = detailItem.posterUrl,
                    modifier = Modifier
                        .width(74.dp)
                        .height(106.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, JoeColors.RedMuted, RoundedCornerShape(8.dp))
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = detailItem.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = JoeColors.TextPrimary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    val badges = listOfNotNull(
                        detailItem.year.takeIf { it.isNotBlank() },
                        detailItem.rating.takeIf { it.isNotBlank() && it != "0" && it != "0.0" }?.let { "★ $it" },
                        detailItem.duration.takeIf { it.isNotBlank() }
                    )
                    if (badges.isNotEmpty()) {
                        Text(
                            text = badges.joinToString(" • "),
                            color = JoeColors.RedBright,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    val genreText = detailItem.genre.ifBlank { detailItem.group }
                    if (genreText.isNotBlank()) {
                        Text(
                            text = genreText,
                            color = JoeColors.TextSecondary,
                            fontSize = 11.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isLoadingDetails) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            color = JoeColors.RedBright,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "İçerik bilgileri yükleniyor...",
                            color = JoeColors.TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                Text(
                    text = detailItem.plot.ifBlank {
                        if (isLoadingDetails) "" else "Sunucu bu içerik için özet metni sağlamıyor."
                    },
                    color = JoeColors.TextPrimary.copy(alpha = 0.9f),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                if (detailItem.director.isNotBlank()) {
                    Text(
                        text = "Yönetmen: ${detailItem.director}",
                        color = JoeColors.TextSecondary,
                        fontSize = 12.sp
                    )
                }

                if (detailItem.cast.isNotBlank()) {
                    Text(
                        text = "Oyuncular: ${detailItem.cast}",
                        color = JoeColors.TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (detailItem.category == MediaCategory.SERIES && !isLoadingDetails) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Bölümler (${episodes.size})",
                        color = JoeColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    if (episodes.isEmpty()) {
                        Text(
                            text = "Bölüm listesi alınamadı.",
                            color = JoeColors.TextSecondary,
                            fontSize = 12.sp
                        )
                    } else {
                        episodes.forEachIndexed { idx, ep ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(JoeColors.SurfaceDark)
                                    .border(1.dp, JoeColors.RedMuted, RoundedCornerShape(8.dp))
                                    .clickable { onPlayEpisode(detailItem, episodes, idx) }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = ep.title,
                                    color = JoeColors.TextPrimary,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = "Bölümü Oynat",
                                    tint = JoeColors.RedBright,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (detailItem.category == MediaCategory.MOVIE && detailItem.streamUrl.isNotBlank()) {
                Button(
                    onClick = { onPlayMovie(detailItem, detailItem.streamUrl) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = JoeColors.RedPrimary,
                        contentColor = JoeColors.TextPrimary
                    )
                ) {
                    Icon(imageVector = Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Filmi Oynat", fontWeight = FontWeight.Bold)
                }
            } else if (detailItem.category == MediaCategory.SERIES && episodes.isNotEmpty()) {
                Button(
                    onClick = { onPlayEpisode(detailItem, episodes, 0) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = JoeColors.RedPrimary,
                        contentColor = JoeColors.TextPrimary
                    )
                ) {
                    Icon(imageVector = Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("İlk Bölümü Oynat", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Kapat", color = JoeColors.TextSecondary)
            }
        }
    )
}

@OptIn(UnstableApi::class)
@Composable
fun JoeVideoPlayerScreen(
    session: JoePlaybackSession,
    channels: List<JoeChannelItem>,
    onSelectChannel: (JoeChannelItem) -> Unit,
    onSelectEpisode: (Int, JoeChannelItem, List<JoeEpisodeItem>) -> Unit,
    onClosePlayer: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    val currentChannel = session.channel
    val streamUrl = session.streamUrl
    val hasPrevEpisode = session.episodes.isNotEmpty() && session.currentEpisodeIndex > 0
    val hasNextEpisode = session.episodes.isNotEmpty() &&
        session.currentEpisodeIndex in 0 until (session.episodes.size - 1)

    var isOverlayVisible by remember { mutableStateOf(true) }
    var showChannelDrawer by remember { mutableStateOf(false) }
    var selectedTrackOption by remember { mutableStateOf("TR Dublaj") }
    var selectedQualityLabel by remember { mutableStateOf("720p") }
    var playbackSpeedIndex by remember { mutableIntStateOf(0) }
    var keepScreenOn by remember { mutableStateOf(true) }
    var isFullLandscape by remember { mutableStateOf(true) }
    var resizeModeIndex by remember { mutableIntStateOf(0) }
    var playerError by remember { mutableStateOf<String?>(null) }
    var isBuffering by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var currentPosMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekFraction by remember { mutableFloatStateOf(0f) }

    val channelListState = rememberLazyListState()
    var lastScrolledChannelId by remember { mutableStateOf<String?>(null) }

    val resizeModes = listOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT to "Sığdır",
        AspectRatioFrameLayout.RESIZE_MODE_FILL to "Tam Ekran",
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Yakınlaştır"
    )
    val speedOptions = listOf(1.0f to "1.0x", 1.25f to "1.25x", 1.5f to "1.5x")
    val trackOptions = listOf("TR Dublaj", "TR Altyazı", "EN Altyazı", "Orijinal")
    val qualityOptions = listOf("Oto", "720p", "1080p")

    val exoPlayer = remember(session.refererUrl, session.userAgent, session.cookieHeader) {
        val headers = mutableMapOf<String, String>()
        if (session.refererUrl.isNotBlank()) {
            headers["Referer"] = session.refererUrl
            runCatching {
                val uri = URL(session.refererUrl)
                val origin = "${uri.protocol}://${uri.host}" +
                    if (uri.port in 1..65535) ":${uri.port}" else ""
                headers["Origin"] = origin
            }
        }
        if (session.cookieHeader.isNotBlank()) {
            headers["Cookie"] = session.cookieHeader
        }

        val effectiveUa = session.userAgent.ifBlank {
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        }

        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(effectiveUa)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(20000)
            .apply {
                if (headers.isNotEmpty()) {
                    setDefaultRequestProperties(headers)
                }
            }

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .apply {
                playWhenReady = true
            }
    }

    fun formatDuration(ms: Long): String {
        val totalSec = (ms.coerceAtLeast(0L) / 1000L)
        val hours = totalSec / 3600L
        val mins = (totalSec % 3600L) / 60L
        val secs = totalSec % 60L
        return if (hours > 0L) {
            String.format(Locale.ROOT, "%d:%02d:%02d", hours, mins, secs)
        } else {
            String.format(Locale.ROOT, "%02d:%02d", mins, secs)
        }
    }

    LaunchedEffect(exoPlayer, isSeeking) {
        while (true) {
            if (!isSeeking) {
                currentPosMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                val rawDur = exoPlayer.duration
                durationMs = if (rawDur != C.TIME_UNSET && rawDur > 0L) rawDur else 0L
            }
            delay(500L)
        }
    }

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

    LaunchedEffect(streamUrl, exoPlayer) {
        playerError = null
        isBuffering = true
        currentPosMs = 0L
        durationMs = 0L
        exoPlayer.setMediaItem(MediaItem.fromUri(streamUrl))
        exoPlayer.playbackParameters = PlaybackParameters(speedOptions[playbackSpeedIndex].first)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    DisposableEffect(exoPlayer, streamUrl) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
            }

            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
                playerError = "Yayın açılamadı: ${error.errorCodeName}"
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    LaunchedEffect(playbackSpeedIndex) {
        exoPlayer.playbackParameters = PlaybackParameters(speedOptions[playbackSpeedIndex].first)
    }

    LaunchedEffect(selectedTrackOption, selectedQualityLabel) {
        val paramsBuilder = exoPlayer.trackSelectionParameters.buildUpon()
        when (selectedTrackOption) {
            "TR Dublaj" -> {
                paramsBuilder
                    .setPreferredAudioLanguages("tr", "tur", "tr-TR")
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            }
            "TR Altyazı" -> {
                paramsBuilder
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setPreferredTextLanguages("tr", "tur", "tr-TR")
            }
            "EN Altyazı" -> {
                paramsBuilder
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setPreferredTextLanguages("en", "eng", "en-US")
            }
            "Orijinal" -> {
                paramsBuilder
                    .setPreferredAudioLanguages()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            }
        }
        when (selectedQualityLabel) {
            "720p" -> paramsBuilder.setMaxVideoSizeSd()
            "1080p" -> paramsBuilder.clearVideoSizeConstraints()
            else -> paramsBuilder.clearVideoSizeConstraints()
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

    DisposableEffect(exoPlayer) {
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

    val currentResizeMode = resizeModes[resizeModeIndex].first
    val progressFraction = if (isSeeking) {
        seekFraction
    } else if (durationMs > 0L) {
        (currentPosMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(JoeColors.SurfaceDark)
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
                view.player = exoPlayer
                view.resizeMode = currentResizeMode
            },
            modifier = Modifier.fillMaxSize()
        )

        if (isBuffering && playerError == null) {
            CircularProgressIndicator(
                color = JoeColors.RedBright,
                modifier = Modifier.align(Alignment.Center)
            )
        } else if (isOverlayVisible && !isPlaying && playerError == null) {
            IconButton(
                onClick = { exoPlayer.play() },
                modifier = Modifier
                    .size(72.dp)
                    .align(Alignment.Center)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Pause,
                    contentDescription = "Duraklatıldı",
                    tint = JoeColors.TextPrimary,
                    modifier = Modifier.size(64.dp)
                )
            }
        }

        playerError?.let { errText ->
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(JoeColors.CardDark.copy(alpha = 0.96f))
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
                Text(
                    text = "Site koruması doğrudan akışı engelliyor olabilir. Tarayıcıya dönüp sayfadan izleyebilirsin.",
                    color = JoeColors.TextSecondary,
                    fontSize = 12.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
                    OutlinedButton(
                        onClick = { exitFullScreenAndClose() }
                    ) {
                        Text("Tarayıcıya Dön", color = JoeColors.TextPrimary)
                    }
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
                                JoeColors.SurfaceDark.copy(alpha = 0.85f),
                                Color.Transparent,
                                JoeColors.SurfaceDark.copy(alpha = 0.92f)
                            )
                        )
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = { exitFullScreenAndClose() },
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(JoeColors.CardDark.copy(alpha = 0.85f))
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ArrowBack,
                                contentDescription = "Geri",
                                tint = JoeColors.TextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = currentChannel.title,
                                color = JoeColors.TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = currentChannel.subtitle,
                                color = JoeColors.TextSecondary,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable {
                                    val nextIdx = (qualityOptions.indexOf(selectedQualityLabel) + 1) % qualityOptions.size
                                    selectedQualityLabel = qualityOptions[nextIdx]
                                }
                        ) {
                            Text(
                                text = "Oto Kalite:",
                                color = JoeColors.TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(JoeColors.RedPrimary)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = selectedQualityLabel,
                                    color = JoeColors.TextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        trackOptions.forEach { option ->
                            val active = selectedTrackOption == option
                            Box(
                                modifier = Modifier
                                    .width(108.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (active) JoeColors.RedPrimary
                                        else JoeColors.CardDark.copy(alpha = 0.85f)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (active) JoeColors.RedBright else JoeColors.RedMuted,
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .clickable { selectedTrackOption = option }
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = option,
                                    color = JoeColors.TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (session.episodes.size > 1) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    onSelectEpisode(
                                        session.currentEpisodeIndex - 1,
                                        currentChannel,
                                        session.episodes
                                    )
                                },
                                enabled = hasPrevEpisode,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(imageVector = Icons.Rounded.SkipPrevious, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Önceki Bölüm", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }

                            Button(
                                onClick = {
                                    onSelectEpisode(
                                        session.currentEpisodeIndex + 1,
                                        currentChannel,
                                        session.episodes
                                    )
                                },
                                enabled = hasNextEpisode,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = JoeColors.RedPrimary,
                                    contentColor = JoeColors.TextPrimary
                                ),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Sonraki Bölüm", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(imageVector = Icons.Rounded.SkipNext, contentDescription = null)
                            }
                        }
                    }

                    Slider(
                        value = progressFraction,
                        onValueChange = { frac ->
                            isSeeking = true
                            seekFraction = frac
                        },
                        onValueChangeFinished = {
                            if (durationMs > 0L) {
                                exoPlayer.seekTo((seekFraction * durationMs).toLong())
                            }
                            isSeeking = false
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = JoeColors.RedBright,
                            activeTrackColor = JoeColors.RedBright,
                            inactiveTrackColor = JoeColors.TextSecondary.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(22.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = "Oynat/Duraklat",
                                tint = JoeColors.TextPrimary,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clickable {
                                        if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                                    }
                            )
                            Icon(
                                imageVector = Icons.Rounded.VolumeUp,
                                contentDescription = "Ses",
                                tint = JoeColors.TextPrimary,
                                modifier = Modifier
                                    .size(22.dp)
                                    .clickable {
                                        exoPlayer.volume = if (exoPlayer.volume > 0f) 0f else 1f
                                    }
                            )
                            Text(
                                text = "${formatDuration(currentPosMs)} / ${formatDuration(durationMs)}",
                                color = JoeColors.TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Replay10,
                                contentDescription = "10sn Geri",
                                tint = JoeColors.TextPrimary,
                                modifier = Modifier
                                    .size(22.dp)
                                    .clickable {
                                        exoPlayer.seekTo((exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L))
                                    }
                            )
                            Icon(
                                imageVector = Icons.Rounded.Forward10,
                                contentDescription = "10sn İleri",
                                tint = JoeColors.TextPrimary,
                                modifier = Modifier
                                    .size(22.dp)
                                    .clickable {
                                        val target = exoPlayer.currentPosition + 10_000L
                                        val maxDur = if (durationMs > 0L) durationMs else target
                                        exoPlayer.seekTo(target.coerceAtMost(maxDur))
                                    }
                            )
                            Icon(
                                imageVector = Icons.Rounded.Headphones,
                                contentDescription = "Ses Modu",
                                tint = JoeColors.TextPrimary,
                                modifier = Modifier
                                    .size(22.dp)
                                    .clickable {
                                        val nextIdx = (trackOptions.indexOf(selectedTrackOption) + 1) % trackOptions.size
                                        selectedTrackOption = trackOptions[nextIdx]
                                    }
                            )
                            Icon(
                                imageVector = Icons.Rounded.ClosedCaption,
                                contentDescription = "Altyazı",
                                tint = if (selectedTrackOption.contains("Altyazı")) JoeColors.RedBright else JoeColors.TextPrimary,
                                modifier = Modifier
                                    .size(22.dp)
                                    .clickable {
                                        selectedTrackOption = if (selectedTrackOption == "TR Altyazı") "TR Dublaj" else "TR Altyazı"
                                    }
                            )
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = "Hız (${speedOptions[playbackSpeedIndex].second})",
                                tint = JoeColors.TextPrimary,
                                modifier = Modifier
                                    .size(22.dp)
                                    .clickable {
                                        playbackSpeedIndex = (playbackSpeedIndex + 1) % speedOptions.size
                                    }
                            )
                            Icon(
                                imageVector = Icons.Rounded.PictureInPictureAlt,
                                contentDescription = "Oran (${resizeModes[resizeModeIndex].second})",
                                tint = JoeColors.TextPrimary,
                                modifier = Modifier
                                    .size(22.dp)
                                    .clickable {
                                        resizeModeIndex = (resizeModeIndex + 1) % resizeModes.size
                                    }
                            )
                            Icon(
                                imageVector = if (isFullLandscape) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                                contentDescription = "Tam Ekran",
                                tint = JoeColors.TextPrimary,
                                modifier = Modifier
                                    .size(22.dp)
                                    .clickable { isFullLandscape = !isFullLandscape }
                            )
                        }
                    }

                    if (channels.isNotEmpty() && session.episodes.isEmpty() && !session.returnToBrowserOnClose) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { showChannelDrawer = !showChannelDrawer },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = JoeColors.RedPrimary,
                                    contentColor = JoeColors.TextPrimary
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(imageVector = Icons.Rounded.QueueMusic, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (showChannelDrawer) "Kanal Listesini Gizle" else "Hızlı Kanal Listesi",
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                            OutlinedButton(
                                onClick = { keepScreenOn = !keepScreenOn },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Lightbulb,
                                    contentDescription = "Ekran Açık",
                                    tint = if (keepScreenOn) JoeColors.RedBright else JoeColors.TextPrimary
                                )
                            }
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
                .padding(end = 132.dp)
                .width(240.dp)
                .heightIn(max = 240.dp)
        ) {
            LazyColumn(
                state = channelListState,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(JoeColors.SurfaceDark.copy(alpha = 0.96f))
                    .border(1.dp, JoeColors.RedBright.copy(alpha = 0.6f), RoundedCornerShape(12.dp)),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(channels, key = { it.id }) { item ->
                    val isCurrent = item.id == currentChannel.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
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
                        fontSize = 11.sp,
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
            .padding(vertical = 4.dp),
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
                        JoeNetworkPoster(
                            url = item.posterUrl,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(165.dp)
                        )
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
