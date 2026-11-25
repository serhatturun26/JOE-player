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

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
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
            kotlinx.coroutines.delay(500L)
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

    LaunchedEffect(streamUrl) {
        playerError = null
        isBuffering = true
        currentPosMs = 0L
        durationMs = 0L
        val fallbackUrl = if (streamUrl.endsWith(".m3u8")) {
            streamUrl.removeSuffix(".m3u8") + ".ts"
        } else {
            streamUrl
        }
        exoPlayer.setMediaItem(MediaItem.fromUri(streamUrl))
        exoPlayer.playbackParameters = PlaybackParameters(speedOptions[playbackSpeedIndex].first)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true

        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

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
            "720p" -> paramsBuilder.setMaxVideoSize(1280, 720)
            "1080p" -> paramsBuilder.setMaxVideoSize(1920, 1080)
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
                color = Color(0xFF0095FF),
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
                    tint = Color.White,
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
                                Color.Black.copy(alpha = 0.72f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.85f)
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
                                .background(Color.Black.copy(alpha = 0.55f))
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ArrowBack,
                                contentDescription = "Geri",
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = currentChannel.title,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = currentChannel.subtitle,
                                color = Color.White.copy(alpha = 0.75f),
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
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color(0xFF0095FF))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = selectedQualityLabel,
                                    color = Color.White,
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
                                        if (active) Color(0xFF0095FF)
                                        else Color.Black.copy(alpha = 0.65f)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (active) Color(0xFF0095FF) else Color.White.copy(alpha = 0.35f),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .clickable { selectedTrackOption = option }
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = option,
                                    color = Color.White,
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
                            thumbColor = Color(0xFF0095FF),
                            activeTrackColor = Color(0xFF0095FF),
                            inactiveTrackColor = Color.White.copy(alpha = 0.35f)
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
                                tint = Color.White,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clickable {
                                        if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                                    }
                            )
                            Icon(
                                imageVector = Icons.Rounded.VolumeUp,
                                contentDescription = "Ses",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(22.dp)
                                    .clickable {
                                        exoPlayer.volume = if (exoPlayer.volume > 0f) 0f else 1f
                                    }
                            )
                            Text(
                                text = "${formatDuration(currentPosMs)} / ${formatDuration(durationMs)}",
                                color = Color.White,
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
                                tint = Color.White,
                                modifier = Modifier
                                    .size(22.dp)
                                    .clickable {
                                        exoPlayer.seekTo((exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L))
                                    }
                            )
                            Icon(
                                imageVector = Icons.Rounded.Forward10,
                                contentDescription = "10sn İleri",
                                tint = Color.White,
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
                                tint = Color.White,
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
                                tint = if (selectedTrackOption.contains("Altyazı")) Color(0xFF0095FF) else Color.White,
                                modifier = Modifier
                                    .size(22.dp)
                                    .clickable {
                                        selectedTrackOption = if (selectedTrackOption == "TR Altyazı") "TR Dublaj" else "TR Altyazı"
                                    }
                            )
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = "Hız ve Oran",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(22.dp)
                                    .clickable {
                                        playbackSpeedIndex = (playbackSpeedIndex + 1) % speedOptions.size
                                    }
                            )
                            Icon(
                                imageVector = Icons.Rounded.PictureInPictureAlt,
                                contentDescription = "Oran",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(22.dp)
                                    .clickable {
                                        resizeModeIndex = (resizeModeIndex + 1) % resizeModes.size
                                    }
                            )
                            Icon(
                                imageVector = if (isFullLandscape) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                                contentDescription = "Tam Ekran",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(22.dp)
                                    .clickable { isFullLandscape = !isFullLandscape }
                            )
                        }
                    }

                    if (channels.isNotEmpty() && session.episodes.isEmpty()) {
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
                                    tint = if (keepScreenOn) JoeColors.RedBright else Color.White
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
                    .border(1.dp, Color(0xFF0095FF).copy(alpha = 0.6f), RoundedCornerShape(12.dp)),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(channels, key = { it.id }) { item ->
                    val isCurrent = item.id == currentChannel.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isCurrent) Color(0xFF0095FF).copy(alpha = 0.35f) else JoeColors.CardDark)
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
                            color = if (isCurrent) Color(0xFF66C2FF) else JoeColors.TextPrimary,
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
