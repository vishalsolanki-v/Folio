package com.folio.app.formats.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.view.View
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.folio.app.R
import com.folio.app.core.designsystem.CodeTextStyle
import com.folio.app.core.designsystem.Spacing
import com.folio.app.core.model.FileFamily
import com.folio.app.core.model.OpenedFile
import com.folio.app.core.viewer.FormatViewer
import com.folio.app.core.viewer.ViewerError
import com.folio.app.core.viewer.ViewerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Audio and video through Media3 ExoPlayer (plan M8). Plays from the content URI; nothing is copied or uploaded. */
object MediaViewer : FormatViewer {
    override val families = setOf(FileFamily.AUDIO, FileFamily.VIDEO)
    override val supportsSearch = false
    override val supportsPaper = false

    @OptIn(UnstableApi::class)
    @Composable
    override fun Content(file: OpenedFile, state: ViewerState, onTap: () -> Unit, modifier: Modifier) {
        val context = LocalContext.current
        val video = file.family == FileFamily.VIDEO
        // Survive rotation: where we were and whether it was playing.
        var position by rememberSaveable(file.uri) { mutableLongStateOf(0L) }
        var playWhenReady by rememberSaveable(file.uri) { mutableStateOf(video) }
        var failed by remember(file.uri) { mutableStateOf(false) }

        val player = remember(file.uri) {
            // Decoder fallback: if a phone's hardware decoder fails (seen on the emulator's H.264 decoder), retry in software.
            ExoPlayer.Builder(context, DefaultRenderersFactory(context).setEnableDecoderFallback(true))
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(if (video) C.AUDIO_CONTENT_TYPE_MOVIE else C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    true, // handle audio focus
                )
                .setHandleAudioBecomingNoisy(true) // pause when headphones are unplugged
                .build()
                .apply {
                    setMediaItem(MediaItem.fromUri(file.uri))
                    seekTo(position)
                    this.playWhenReady = playWhenReady
                    prepare()
                }
        }
        DisposableEffect(player) {
            val listener = object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    failed = true
                    state.immersive = false // the video controls may have hidden the bars; the error screen needs them
                }
                override fun onPlayWhenReadyChanged(ready: Boolean, reason: Int) { playWhenReady = ready }
            }
            player.addListener(listener)
            onDispose {
                position = player.currentPosition
                player.removeListener(listener)
                player.release()
            }
        }
        LifecycleEventEffect(Lifecycle.Event.ON_STOP) { player.pause() }
        LaunchedEffect(player) {
            while (true) {
                position = player.currentPosition
                delay(250)
            }
        }
        LaunchedEffect(file.uri) { state.infoExtras = mediaInfo(context, file).extras }

        when {
            failed -> ViewerError("Folio can't play this file.", modifier)
            video -> VideoPlayer(player, state, modifier)
            else -> AudioCard(file, player, position, playWhenReady, modifier)
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayer(player: ExoPlayer, state: ViewerState, modifier: Modifier) {
    AndroidView(
        modifier = modifier.background(Color.Black),
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                setShowNextButton(false)
                setShowPreviousButton(false)
                // Controls showing = Folio's bars showing; controls hiding = immersive.
                setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { v -> state.immersive = v != View.VISIBLE })
            }
        },
        onRelease = { it.player = null },
    )
}

@Composable
private fun AudioCard(file: OpenedFile, player: ExoPlayer, position: Long, playing: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val info by produceState<MediaInfo?>(null, file.uri) { value = mediaInfo(context, file) }
    var seeking by remember { mutableStateOf<Float?>(null) }
    val duration = info?.durationMs?.takeIf { it > 0 } ?: player.duration.takeIf { it > 0 } ?: 0L

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val art = info?.art
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.size(240.dp)) {
                if (art != null) {
                    Image(art.asImageBitmap(), contentDescription = "Cover art", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)))
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_music_note), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(64.dp))
                    }
                }
            }
            Spacer(Modifier.height(Spacing.xl))
            Text(info?.title ?: file.name.substringBeforeLast('.'), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            info?.artist?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Spacer(Modifier.height(Spacing.lg))
            Slider(
                value = seeking ?: if (duration > 0) position.toFloat() / duration else 0f,
                onValueChange = { seeking = it },
                onValueChangeFinished = { seeking?.let { player.seekTo((it * duration).toLong()) }; seeking = null },
                enabled = duration > 0,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(seeking?.let { (it * duration).toLong() } ?: position), style = CodeTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatTime(duration), style = CodeTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(Spacing.md))
            Surface(
                onClick = { if (player.isPlaying || player.playWhenReady) player.pause() else { if (player.playbackState == Player.STATE_ENDED) player.seekTo(0); player.play() } },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(64.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play_arrow),
                        contentDescription = if (playing) "Pause" else "Play",
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }
    }
}

private class MediaInfo(val title: String?, val artist: String?, val durationMs: Long, val art: Bitmap?, val extras: Map<String, String>)

private suspend fun mediaInfo(context: Context, file: OpenedFile): MediaInfo = withContext(Dispatchers.IO) {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, file.uri)
        fun meta(key: Int) = retriever.extractMetadata(key)?.takeIf { it.isNotBlank() }
        val duration = meta(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        val title = meta(MediaMetadataRetriever.METADATA_KEY_TITLE)
        val artist = meta(MediaMetadataRetriever.METADATA_KEY_ARTIST)
        val album = meta(MediaMetadataRetriever.METADATA_KEY_ALBUM)
        val width = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
        val height = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
        val art = retriever.embeddedPicture?.let { bytes ->
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1024) sample *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        }
        val extras = buildMap {
            if (duration > 0) put("Length", formatTime(duration))
            if (width != null && height != null) put("Resolution", "$width × $height")
            artist?.let { put("Artist", it) }
            album?.let { put("Album", it) }
        }
        MediaInfo(title, artist, duration, art, extras)
    } catch (_: Exception) {
        MediaInfo(null, null, 0, null, emptyMap())
    } finally {
        runCatching { retriever.release() }
    }
}

private fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
