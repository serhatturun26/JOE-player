package com.joe.player

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun JoePlayerOverlay(
    currentChannel: JoeChannelItem,
    channels: List<JoeChannelItem>,
    isVisible: Boolean,
    onToggleVisibility: () -> Unit,
    onSelectChannel: (JoeChannelItem) -> Unit,
    onClosePlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showChannelDrawer by remember { mutableStateOf(false) }
    var selectedAudioTrack by remember { mutableStateOf("Türkçe (TR)") }
    var keepScreenOn by remember { mutableStateOf(true) }

    val audioTracks = listOf("Türkçe (TR)", "Orijinal", "Türkçe Altyazı")

    // İzleme sırasında ekranın kapanmasını engelleme (wake-lock)
    DisposableEffect(keepScreenOn) {
        val window = (context as? Activity)?.window
        if (keepScreenOn) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable { onToggleVisibility() }
    ) {
        AnimatedVisibility(
            visible = isVisible,
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
                // Üst Bilgi ve Kontrol Çubuğu
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
                            onClick = onClosePlayer,
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

                    // Ekran Uyanık Kalma Anahtarı
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
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (keepScreenOn) "Açık" else "Normal",
                            color = JoeColors.TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Alt Hızlı İşlemler: Ses Dili Seçimi ve Kanal Listesi Butonu
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
                            val active = selectedAudioTrack == track
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
                                    .clickable {
                                        selectedAudioTrack = track
                                        // ExoPlayer / Media3 ses veya altyazı parça seçimi buraya bağlanır
                                    }
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

        // Sağ/Üst Hızlı Kanal Geçiş Paneli
        AnimatedVisibility(
            visible = isVisible && showChannelDrawer,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
                .width(260.dp)
                .heightIn(max = 320.dp)
        ) {
            LazyColumn(
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
