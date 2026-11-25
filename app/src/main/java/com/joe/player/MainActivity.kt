package com.joe.player

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.LruCache
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
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

data class JoeMediaTrackOption(
    val id: String,
    val label: String,
    val language: String?,
    val groupIndex: Int,
    val trackIndex: Int
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
    var activePlayerChannel by remember { mutableStateOf<JoeChannelItem?>(null) }
    var activePlayableUrl by remember { mutableStateOf("") }
    var showAddPlaylistDialog by remember { mutableStateOf(false) }

    val allTabCategories by remember(allItems, selectedTab) {
        derivedStateOf {
            val tabItems = when (selectedTab) {
                JoeTab.LIVE -> allItems.filter { it.category == MediaCategory.LIVE }
                JoeTab.MOVIES -> allItems.filter { it.category == MediaCategory.MOVIE }
                JoeTab.SERIES -> allItems.filter { it.category == MediaCategory.SERIES }
                JoeTab.PLAYLISTS -> emptyList()
            }
            val groups = tabItems.map { it.group }.filter { it.isNotBlank() }.distinct()
            listOf("Tümü") + groups
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
            val q = categorySearchQuery.trim()
            val matchingGroups = if (q.isEmpty()) emptySet() else {
                allItems.asSequence()
                    .filter { it.category == MediaCategory.LIVE && it.group.contains(q, ignoreCase = true) }
                    .map { it.group }
                    .toSet()
            }
            allItems.filter { item ->
                if (item.category != MediaCategory.LIVE) return@filter false
                val catOk = selectedCategory == "Tümü" || item.group.equals(selectedCategory, ignoreCase = true)
                val searchOk = q.isEmpty() ||
                    item.group in matchingGroups ||
                    item.title.contains(q, ignoreCase = true)
                catOk && searchOk
            }
        }
    }

    val movieItems by remember(allItems, selectedCategory, categorySearchQuery) {
        derivedStateOf {
            val q = categorySearchQuery.trim()
            val matchingGroups = if (q.isEmpty()) emptySet() else {
                allItems.asSequence()
                    .filter { it.category == MediaCategory.MOVIE && it.group.contains(q, ignoreCase = true) }
                    .map { it.group }
                    .toSet()
            }
            allItems.filter { item ->
                if (item.category != MediaCategory.MOVIE) return@filter false
                val catOk = selectedCategory == "Tümü" || item.group.equals(selectedCategory, ignoreCase = true)
                val searchOk = q.isEmpty() ||
                    item.group in matchingGroups ||
                    item.title.contains(q, ignoreCase = true)
                catOk && searchOk
            }
        }
    }

    val seriesItems by remember(allItems, selectedCategory, categorySearchQuery) {
        derivedStateOf {
            val q = categorySearchQuery.trim()
            val matchingGroups = if (q.isEmpty()) emptySet() else {
                allItems.asSequence()
                    .filter { it.category == MediaCategory.SERIES && it.group.contains(q, ignoreCase = true) }
                    .map { it.group }
                    .toSet()
            }
            allItems.filter { item ->
                if (item.category != MediaCategory.SERIES) return@filter false
                val catOk = selectedCategory == "Tümü" || item.group.equals(selectedCategory, ignoreCase = true)
                val searchOk = q.isEmpty() ||
                    item.group in matchingGroups ||
                    item.title.contains(q, ignoreCase = true)
                catOk && searchOk
            }
        }
    }

    fun nowFormatted(): String {
        return SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
    }

    fun startStream(item: JoeChannelItem, url: String) {
        if (url.isNotBlank()) {
            activePlayableUrl = url
            activePlayerChannel = item
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

    if (activePlayerChannel != null && activePlayableUrl.isNotEmpty()) {
        JoeVideoPlayerScreen(
            currentChannel = activePlayerChannel!!,
            streamUrl = activePlayableUrl,
            channels = liveChannels,
            onSelectChannel = { newChannel -> startStream(newChannel, newChannel.streamUrl) },
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
                    categorySearchQuery = ""
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

            if (selectedTab != JoeTab.PLAYLISTS && activePlaylist != null && allTabCategories.size > 1) {
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
                onPlayUrl = { playItem, playUrl ->
                    detailTarget = null
                    startStream(playItem, playUrl)
                }
            )
        }
    }
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
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = JoeColors.CardDark,
                unfocusedContainerColor = JoeColors.CardDark,
                focusedBorderColor = JoeColors.RedBright,
                unfocusedBorderColor = JoeColors.RedMuted,
                focusedTextColor = JoeColors.TextPrimary,
                unfocusedTextColor = JoeColors.TextPrimary,
                cursorColor = JoeColors.RedBright
            ),
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
    onPlayUrl: (JoeChannelItem, String) -> Unit
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
                        episodes.forEach { ep ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(JoeColors.SurfaceDark)
                                    .border(1.dp, JoeColors.RedMuted, RoundedCornerShape(8.dp))
                                    .clickable {
                                        val playMeta = detailItem.copy(
                                            title = "${detailItem.title} - ${ep.title}",
                                            subtitle = detailItem.group
                                        )
                                        onPlayUrl(playMeta, ep.streamUrl)
                                    }
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
                    onClick = { onPlayUrl(detailItem, detailItem.streamUrl) },
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
                    onClick = {
                        val first = episodes.first()
                        val playMeta = detailItem.copy(
                            title = "${detailItem.title} - ${first.title}",
                            subtitle = detailItem.group
                        )
                        onPlayUrl(playMeta, first.streamUrl)
                    },
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

private fun formatDurationMs(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}

private fun formatLanguageDisplay(lang: String?, label: String?, fallbackIndex: Int): String {
    val cleanCode = lang?.trim()?.lowercase(Locale.ROOT).orEmpty()
    val mappedLang = when {
        cleanCode.startsWith("tr") || cleanCode == "tur" -> "Türkçe"
        cleanCode.startsWith("en") || cleanCode == "eng" -> "İngilizce"
        cleanCode.startsWith("de") || cleanCode == "deu" || cleanCode == "ger" -> "Almanca"
        cleanCode.startsWith("fr") || cleanCode == "fra" || cleanCode == "fre" -> "Fransızca"
        cleanCode.startsWith("es") || cleanCode == "spa" -> "İspanyolca"
        cleanCode.startsWith("it") || cleanCode == "ita" -> "İtalyanca"
        cleanCode.startsWith("ar") || cleanCode == "ara" -> "Arapça"
        cleanCode.startsWith("ru") || cleanCode == "rus" -> "Rusça"
        cleanCode.startsWith("ja") || cleanCode == "jpn" -> "Japonca"
        cleanCode.startsWith("ko") || cleanCode == "kor" -> "Korece"
        cleanCode.isNotBlank() -> cleanCode.uppercase(Locale.ROOT)
        else -> ""
    }
    val cleanLabel = label?.trim().orEmpty()
    return when {
        mappedLang.isNotBlank() && cleanLabel.isNotBlank() && !cleanLabel.equals(mappedLang, true) ->
            "$mappedLang ($cleanLabel)"
        mappedLang.isNotBlank() -> mappedLang
        cleanLabel.isNotBlank() -> cleanLabel
        else -> "Paket #${fallbackIndex + 1}"
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
    var keepScreenOn by remember { mutableStateOf(true) }
    var isFullLandscape by remember { mutableStateOf(true) }
    var resizeModeIndex by remember { mutableIntStateOf(0) }
    var playerError by remember { mutableStateOf<String?>(null) }
    var isBuffering by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }

    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableLongStateOf(0L) }

    var audioPackages by remember { mutableStateOf<List<JoeMediaTrackOption>>(emptyList()) }
    var subtitlePackages by remember { mutableStateOf<List<JoeMediaTrackOption>>(emptyList()) }
    var selectedAudioId by remember { mutableStateOf<String?>(null) }
    var selectedSubtitleId by remember { mutableStateOf<String>("off") }

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

    fun refreshTracksFromPlayer(tracks: Tracks) {
        val foundAudio = mutableListOf<JoeMediaTrackOption>()
        val foundSubs = mutableListOf<JoeMediaTrackOption>()
        var activeAudioOptionId: String? = null
        var activeSubOptionId: String? = null

        tracks.groups.forEachIndexed { gIdx, group ->
            when (group.type) {
                C.TRACK_TYPE_AUDIO -> {
                    for (tIdx in 0 until group.length) {
                        val fmt = group.getTrackFormat(tIdx)
                        val optId = "aud_${gIdx}_$tIdx"
                        val displayName = formatLanguageDisplay(
                            lang = fmt.language,
                            label = fmt.label,
                            fallbackIndex = foundAudio.size
                        )
                        foundAudio.add(
                            JoeMediaTrackOption(
                                id = optId,
                                label = displayName,
                                language = fmt.language,
                                groupIndex = gIdx,
                                trackIndex = tIdx
                            )
                        )
                        if (group.isTrackSelected(tIdx)) {
                            activeAudioOptionId = optId
                        }
                    }
                }
                C.TRACK_TYPE_TEXT -> {
                    for (tIdx in 0 until group.length) {
                        val fmt = group.getTrackFormat(tIdx)
                        val optId = "sub_${gIdx}_$tIdx"
                        val displayName = formatLanguageDisplay(
                            lang = fmt.language,
                            label = fmt.label,
                            fallbackIndex = foundSubs.size
                        )
                        foundSubs.add(
                            JoeMediaTrackOption(
                                id = optId,
                                label = displayName,
                                language = fmt.language,
                                groupIndex = gIdx,
                                trackIndex = tIdx
                            )
                        )
                        if (group.isTrackSelected(tIdx)) {
                            activeSubOptionId = optId
                        }
                    }
                }
            }
        }

        audioPackages = foundAudio
        subtitlePackages = foundSubs
        if (activeAudioOptionId != null) {
            selectedAudioId = activeAudioOptionId
        } else if (foundAudio.isNotEmpty() && selectedAudioId == null) {
            selectedAudioId = foundAudio.first().id
        }
        selectedSubtitleId = if (foundSubs.isEmpty()) "off" else (activeSubOptionId ?: "off")
    }

    fun applyAudioPackage(option: JoeMediaTrackOption) {
        selectedAudioId = option.id
        val tracks = exoPlayer.currentTracks
        if (option.groupIndex < tracks.groups.size) {
            val group = tracks.groups[option.groupIndex]
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                .buildUpon()
                .setOverrideForType(
                    androidx.media3.common.TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex)
                )
                .build()
        } else if (!option.language.isNullOrBlank()) {
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                .buildUpon()
                .setPreferredAudioLanguage(option.language)
                .build()
        }
    }

    fun applySubtitlePackage(optionId: String) {
        selectedSubtitleId = optionId
        if (optionId == "off") {
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
            return
        }
        val target = subtitlePackages.find { it.id == optionId } ?: return
        val tracks = exoPlayer.currentTracks
        val builder = exoPlayer.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
        if (target.groupIndex < tracks.groups.size) {
            val group = tracks.groups[target.groupIndex]
            builder.setOverrideForType(
                androidx.media3.common.TrackSelectionOverride(group.mediaTrackGroup, target.trackIndex)
            )
        } else if (!target.language.isNullOrBlank()) {
            builder.setPreferredTextLanguage(target.language)
        }
        exoPlayer.trackSelectionParameters = builder.build()
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

    LaunchedEffect(exoPlayer) {
        while (true) {
            if (!isScrubbing) {
                currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            }
            val rawDur = exoPlayer.duration
            durationMs = if (rawDur != C.TIME_UNSET && rawDur > 0L) rawDur else 0L
            isPlaying = exoPlayer.isPlaying
            delay(500L)
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
        currentPositionMs = 0L
        durationMs = 0L
        audioPackages = emptyList()
        subtitlePackages = emptyList()
        selectedAudioId = null
        selectedSubtitleId = "off"

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
                val rawDur = exoPlayer.duration
                if (rawDur != C.TIME_UNSET && rawDur > 0L) {
                    durationMs = rawDur
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onTracksChanged(tracks: Tracks) {
                refreshTracksFromPlayer(tracks)
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

    val currentResizeMode = resizeModes[resizeModeIndex].first
    val currentResizeLabel = resizeModes[resizeModeIndex].second
    val showSeekBar = !currentChannel.isLive && durationMs > 0L

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
                                Color.Black.copy(alpha = 0.84f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.90f)
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
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (showSeekBar) {
                        val displayPos = if (isScrubbing) scrubPositionMs else currentPositionMs
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    if (exoPlayer.isPlaying) {
                                        exoPlayer.pause()
                                    } else {
                                        exoPlayer.play()
                                    }
                                },
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(JoeColors.CardDark.copy(alpha = 0.92f))
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (isPlaying) "Duraklat" else "Oynat",
                                    tint = JoeColors.TextPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Text(
                                text = formatDurationMs(displayPos),
                                color = JoeColors.TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            Slider(
                                value = displayPos.toFloat().coerceIn(0f, durationMs.toFloat().coerceAtLeast(1f)),
                                onValueChange = { newVal ->
                                    isScrubbing = true
                                    scrubPositionMs = newVal.toLong()
                                },
                                onValueChangeFinished = {
                                    exoPlayer.seekTo(scrubPositionMs)
                                    currentPositionMs = scrubPositionMs
                                    isScrubbing = false
                                },
                                valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = JoeColors.RedBright,
                                    activeTrackColor = JoeColors.RedPrimary,
                                    inactiveTrackColor = JoeColors.CardDark
                                ),
                                modifier = Modifier.weight(1f)
                            )

                            Text(
                                text = formatDurationMs(durationMs),
                                color = JoeColors.TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (audioPackages.isNotEmpty()) {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            item {
                                Text(
                                    text = "Dil:",
                                    color = JoeColors.TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(end = 2.dp)
                                )
                            }
                            items(audioPackages, key = { it.id }) { pkg ->
                                val active = selectedAudioId == pkg.id
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
                                        .clickable { applyAudioPackage(pkg) }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = pkg.label,
                                        color = JoeColors.TextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    if (subtitlePackages.isNotEmpty()) {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            item {
                                Text(
                                    text = "Altyazı:",
                                    color = JoeColors.TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(end = 2.dp)
                                )
                                val offSelected = selectedSubtitleId == "off"
                                Box(
                                    modifier = Modifier
                                        .padding(end = 6.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (offSelected) JoeColors.RedPrimary
                                            else JoeColors.CardDark.copy(alpha = 0.92f)
                                        )
                                        .border(
                                            1.dp,
                                            if (offSelected) JoeColors.RedBright else JoeColors.RedMuted,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable { applySubtitlePackage("off") }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = "Kapalı",
                                        color = JoeColors.TextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = if (offSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                            items(subtitlePackages, key = { it.id }) { sub ->
                                val active = selectedSubtitleId == sub.id
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
                                        .clickable { applySubtitlePackage(sub.id) }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = sub.label,
                                        color = JoeColors.TextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    if (channels.isNotEmpty() && currentChannel.isLive) {
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
)
