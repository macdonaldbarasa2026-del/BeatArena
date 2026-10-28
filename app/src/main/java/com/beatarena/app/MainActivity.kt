package com.beatarena.app

import android.media.AudioAttributes
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.audiofx.Equalizer
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.darkColorScheme
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val StudioBackground = Color(0xFF090D13)
private val PanelBackground = Color(0xFF111923)
private val PanelBorder = Color(0xFF263442)
private val TextPrimary = Color(0xFFE7F0F6)
private val TextMuted = Color(0xFF8193A2)
private val DeckAColor = Color(0xFF32D8E8)
private val DeckBColor = Color(0xFFFF4FA3)

data class AudioTrack(
    val uri: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
)

data class DeckState(
    val track: AudioTrack? = null,
    val isLoading: Boolean = false,
    val isPrepared: Boolean = false,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val cueMs: Long? = null,
    val equalizerAvailable: Boolean = false,
    val error: String? = null,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = DeckAColor,
                    secondary = DeckBColor,
                    background = StudioBackground,
                    surface = PanelBackground,
                    onBackground = TextPrimary,
                    onSurface = TextPrimary,
                ),
            ) {
                BeatArenaApp()
            }
        }
    }
}

@Composable
private fun BeatArenaApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val deckA = remember(context) { DeckController(context) }
    val deckB = remember(context) { DeckController(context) }
    var library by remember { mutableStateOf(LibraryStore.read(context)) }
    var libraryOpen by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var targetDeck by remember { mutableStateOf(DeckSide.A) }
    var status by remember { mutableStateOf("READY") }
    var master by remember { mutableFloatStateOf(0.85f) }
    var channelA by remember { mutableFloatStateOf(1f) }
    var channelB by remember { mutableFloatStateOf(1f) }
    var crossfader by remember { mutableFloatStateOf(0.5f) }
    var gainA by remember { mutableFloatStateOf(0.8f) }
    var gainB by remember { mutableFloatStateOf(0.8f) }
    var highA by remember { mutableFloatStateOf(0.5f) }
    var midA by remember { mutableFloatStateOf(0.5f) }
    var lowA by remember { mutableFloatStateOf(0.5f) }
    var highB by remember { mutableFloatStateOf(0.5f) }
    var midB by remember { mutableFloatStateOf(0.5f) }
    var lowB by remember { mutableFloatStateOf(0.5f) }

    DisposableEffect(deckA, deckB) {
        onDispose {
            deckA.release()
            deckB.release()
        }
    }

    LaunchedEffect(deckA.state.isPlaying) {
        while (deckA.state.isPlaying) {
            delay(250)
            deckA.refreshPosition()
        }
    }
    LaunchedEffect(deckB.state.isPlaying) {
        while (deckB.state.isPlaying) {
            delay(250)
            deckB.refreshPosition()
        }
    }
    LaunchedEffect(deckA.state.isPrepared, deckB.state.isPrepared, deckA.state.error, deckB.state.error) {
        val error = deckA.state.error ?: deckB.state.error
        when {
            error != null -> status = error.uppercase()
            deckA.state.isPrepared || deckB.state.isPrepared -> status = "TRACK READY"
        }
    }
    LaunchedEffect(master, channelA, channelB, gainA, gainB, crossfader) {
        deckA.setOutputLevel(MixMath.outputGain(DeckSide.A, master, channelA * gainA, crossfader))
        deckB.setOutputLevel(MixMath.outputGain(DeckSide.B, master, channelB * gainB, crossfader))
    }
    LaunchedEffect(highA, midA, lowA) { deckA.setEqualizer(highA, midA, lowA) }
    LaunchedEffect(highB, midB, lowB) { deckB.setEqualizer(highB, midB, lowB) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    try {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    } catch (_: SecurityException) {
                        status = "FILE ACCESS IS TEMPORARY"
                    }
                    val track = withContext(Dispatchers.IO) { readTrack(context, uri) }
                    library = (listOf(track) + library.filterNot { it.uri == track.uri })
                    LibraryStore.write(context, library)
                    loadTrack(targetDeck, track, deckA, deckB)
                    status = "LOADING TRACK"
                } catch (error: IOException) {
                    status = "FILE UNAVAILABLE"
                } catch (error: SecurityException) {
                    status = "FILE ACCESS DENIED"
                } catch (error: IllegalArgumentException) {
                    status = "UNSUPPORTED AUDIO FILE"
                } catch (error: IllegalStateException) {
                    status = "FILE PROVIDER ERROR"
                }
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = StudioBackground,
    ) {
        Column(
            Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Header(
                deckA = deckA.state,
                deckB = deckB.state,
                master = master,
                status = status,
                onLibrary = { libraryOpen = true },
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DeckPanel(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    side = DeckSide.A,
                    accent = DeckAColor,
                    state = deckA.state,
                    onChoose = {
                        targetDeck = DeckSide.A
                        picker.launch(arrayOf("audio/*"))
                    },
                    onPlayPause = { deckA.togglePlayback() },
                    onSeek = { deckA.seekTo(it) },
                    onCueSet = { deckA.setCue() },
                    onCueReturn = { deckA.returnToCue() },
                    onRate = { deckA.setPlaybackRate(it) },
                )
                MixerPanel(
                    modifier = Modifier.widthIn(min = 142.dp, max = 206.dp).fillMaxHeight(),
                    master = master,
                    onMaster = { master = it },
                    crossfader = crossfader,
                    onCrossfader = { crossfader = it },
                    gainA = gainA,
                    onGainA = { gainA = it },
                    gainB = gainB,
                    onGainB = { gainB = it },
                    channelA = channelA,
                    onChannelA = { channelA = it },
                    channelB = channelB,
                    onChannelB = { channelB = it },
                    highA = highA,
                    onHighA = { highA = it },
                    midA = midA,
                    onMidA = { midA = it },
                    lowA = lowA,
                    onLowA = { lowA = it },
                    highB = highB,
                    onHighB = { highB = it },
                    midB = midB,
                    onMidB = { midB = it },
                    lowB = lowB,
                    onLowB = { lowB = it },
                    eqAAvailable = deckA.state.equalizerAvailable,
                    eqBAvailable = deckB.state.equalizerAvailable,
                )
                DeckPanel(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    side = DeckSide.B,
                    accent = DeckBColor,
                    state = deckB.state,
                    onChoose = {
                        targetDeck = DeckSide.B
                        picker.launch(arrayOf("audio/*"))
                    },
                    onPlayPause = { deckB.togglePlayback() },
                    onSeek = { deckB.seekTo(it) },
                    onCueSet = { deckB.setCue() },
                    onCueReturn = { deckB.returnToCue() },
                    onRate = { deckB.setPlaybackRate(it) },
                )
            }
        }
    }

    if (libraryOpen) {
        LibraryDialog(
            library = library,
            searchText = searchText,
            onSearch = { searchText = it },
            onDismiss = { libraryOpen = false },
            onImport = {
                libraryOpen = false
                targetDeck = DeckSide.A
                picker.launch(arrayOf("audio/*"))
            },
            onLoad = { side, track ->
                libraryOpen = false
                scope.launch {
                    loadTrack(side, track, deckA, deckB)
                    status = "LOADING TRACK"
                }
            },
        )
    }
}

private fun loadTrack(side: DeckSide, track: AudioTrack, deckA: DeckController, deckB: DeckController) {
    (if (side == DeckSide.A) deckA else deckB).load(track)
}

private class DeckController(private val context: android.content.Context) {
    var state by mutableStateOf(DeckState())
        private set

    private var player: MediaPlayer? = null
    private var equalizer: Equalizer? = null
    private var outputLevel = 0f
    private var rate = 1f
    private var eqValues = floatArrayOf(0.5f, 0.5f, 0.5f)

    fun load(track: AudioTrack) {
        releasePlayer()
        state = DeckState(track = track, isLoading = true)
        val nextPlayer = MediaPlayer()
        player = nextPlayer
        nextPlayer.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
        )
        nextPlayer.setOnPreparedListener { prepared ->
            state = state.copy(
                isLoading = false,
                isPrepared = true,
                durationMs = prepared.duration.toLong(),
                error = null,
            )
            attachEqualizer(prepared.audioSessionId)
            applyOutput()
            applyRate()
        }
        nextPlayer.setOnCompletionListener {
            state = state.copy(isPlaying = false, positionMs = state.durationMs)
        }
        nextPlayer.setOnErrorListener { _, what, _ ->
            state = state.copy(
                isLoading = false,
                isPrepared = false,
                isPlaying = false,
                error = "Playback failed ($what)",
            )
            releasePlayer()
            true
        }
        try {
            nextPlayer.setDataSource(context, Uri.parse(track.uri))
            nextPlayer.prepareAsync()
        } catch (error: IOException) {
            failLoad("Could not read this audio file")
        } catch (error: IllegalArgumentException) {
            failLoad("Unsupported audio file")
        } catch (error: SecurityException) {
            failLoad("File access was revoked")
        } catch (error: IllegalStateException) {
            failLoad("Audio player could not be prepared")
        }
    }

    fun togglePlayback() {
        val current = player ?: return
        if (!state.isPrepared) return
        try {
            if (state.isPlaying) {
                current.pause()
                state = state.copy(isPlaying = false)
            } else {
                current.start()
                state = state.copy(isPlaying = true, error = null)
            }
        } catch (error: IllegalStateException) {
            state = state.copy(isPlaying = false, error = "Player is not ready")
        }
    }

    fun seekTo(positionMs: Long) {
        val current = player ?: return
        if (!state.isPrepared) return
        val target = positionMs.coerceIn(0L, state.durationMs)
        try {
            current.seekTo(target.toInt())
            state = state.copy(positionMs = target)
        } catch (error: IllegalStateException) {
            state = state.copy(error = "Could not seek in this track")
        }
    }

    fun setCue() {
        if (state.isPrepared) state = state.copy(cueMs = state.positionMs)
    }

    fun returnToCue() {
        state.cueMs?.let(::seekTo)
    }

    fun refreshPosition() {
        val current = player ?: return
        if (!state.isPrepared || !state.isPlaying) return
        try {
            state = state.copy(positionMs = current.currentPosition.toLong())
        } catch (error: IllegalStateException) {
            state = state.copy(isPlaying = false, error = "Playback position unavailable")
        }
    }

    fun setOutputLevel(level: Float) {
        outputLevel = level.coerceIn(0f, 1f)
        applyOutput()
    }

    fun setEqualizer(high: Float, mid: Float, low: Float) {
        eqValues = floatArrayOf(high, mid, low)
        val effect = equalizer ?: return
        if (!state.equalizerAvailable) return
        try {
            val centerFrequencies = intArrayOf(60_000, 1_000_000, 10_000_000)
            for (index in centerFrequencies.indices) {
                val range = effect.bandLevelRange
                val band = effect.getBand(centerFrequencies[index])
                val level = ((eqValues[index].coerceIn(0f, 1f) * 24f) - 12f)
                val millibels = (level * 100).toInt().coerceIn(range[0].toInt(), range[1].toInt())
                effect.setBandLevel(band, millibels.toShort())
            }
        } catch (error: IllegalArgumentException) {
            state = state.copy(equalizerAvailable = false)
        } catch (error: IllegalStateException) {
            state = state.copy(equalizerAvailable = false)
        }
    }

    fun setPlaybackRate(value: Float) {
        rate = value.coerceIn(0.75f, 1.25f)
        applyRate()
    }

    fun release() {
        releasePlayer()
        state = DeckState()
    }

    private fun attachEqualizer(audioSessionId: Int) {
        try {
            equalizer = Equalizer(0, audioSessionId).also { it.enabled = true }
            state = state.copy(equalizerAvailable = true)
            setEqualizer(eqValues[0], eqValues[1], eqValues[2])
        } catch (error: IllegalArgumentException) {
            equalizer = null
            state = state.copy(equalizerAvailable = false)
        } catch (error: UnsupportedOperationException) {
            equalizer = null
            state = state.copy(equalizerAvailable = false)
        } catch (error: IllegalStateException) {
            equalizer = null
            state = state.copy(equalizerAvailable = false)
        }
    }

    private fun applyOutput() {
        try {
            player?.setVolume(outputLevel, outputLevel)
        } catch (error: IllegalStateException) {
            state = state.copy(error = "Could not update channel level")
        }
    }

    private fun applyRate() {
        val current = player ?: return
        if (!state.isPrepared) return
        try {
            current.playbackParams = current.playbackParams.setSpeed(rate)
        } catch (error: IllegalArgumentException) {
            state = state.copy(error = "Playback rate is not supported")
        } catch (error: IllegalStateException) {
            state = state.copy(error = "Playback rate could not be changed")
        }
    }

    private fun failLoad(message: String) {
        releasePlayer()
        state = state.copy(isLoading = false, isPrepared = false, isPlaying = false, error = message)
    }

    private fun releasePlayer() {
        equalizer?.release()
        equalizer = null
        player?.release()
        player = null
    }
}

private fun readTrack(context: android.content.Context, uri: Uri): AudioTrack {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, uri)
        val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            ?.takeIf(String::isNotBlank)
            ?: uri.lastPathSegment?.substringAfterLast(':')?.takeIf(String::isNotBlank)
            ?: "Untitled track"
        val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            ?.takeIf(String::isNotBlank)
            ?: "Unknown artist"
        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull()
            ?: 0L
        return AudioTrack(uri.toString(), title, artist, duration)
    } finally {
        retriever.release()
    }
}

@Composable
private fun Header(
    deckA: DeckState,
    deckB: DeckState,
    master: Float,
    status: String,
    onLibrary: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("BEAT", color = TextPrimary, fontSize = 21.sp, fontWeight = FontWeight.Black)
            Text("ARENA", color = DeckAColor, fontSize = 21.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(14.dp))
            HeaderDeckTag("A", deckA, DeckAColor)
            Spacer(Modifier.width(7.dp))
            HeaderDeckTag("B", deckB, DeckBColor)
        }
        Text(
            text = status,
            color = if (status.contains("FAIL") || status.contains("DENIED")) DeckBColor else TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("MASTER ${(master * 100).toInt()}%", color = TextMuted, fontSize = 10.sp)
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = onLibrary,
                shape = RoundedCornerShape(10.dp),
                contentPadding = ButtonDefaults.ContentPadding,
            ) {
                Text("LIBRARY", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun HeaderDeckTag(label: String, state: DeckState, accent: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(PanelBackground)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(if (state.isPlaying) accent else TextMuted))
        Spacer(Modifier.width(5.dp))
        Text("$label  BPM —", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DeckPanel(
    modifier: Modifier,
    side: DeckSide,
    accent: Color,
    state: DeckState,
    onChoose: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onCueSet: () -> Unit,
    onCueReturn: () -> Unit,
    onRate: (Float) -> Unit,
) {
    var rate by remember(side) { mutableFloatStateOf(1f) }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(PanelBackground)
            .border(1.dp, PanelBorder, RoundedCornerShape(16.dp))
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (side == DeckSide.A) "DECK A" else "DECK B",
                color = accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.weight(1f))
            Text("BPM —", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    state.track?.title ?: "Choose a track",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    state.track?.artist ?: "Local audio files",
                    color = TextMuted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onChoose) { Text("LOAD", color = accent, fontSize = 10.sp) }
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            JogWheel(
                modifier = Modifier.size(112.dp),
                accent = accent,
                playing = state.isPlaying,
                durationMs = state.durationMs,
                positionMs = state.positionMs,
                onSeek = onSeek,
            )
            Column(
                modifier = Modifier.weight(1f).padding(start = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    formatTime(state.positionMs),
                    color = TextPrimary,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Light,
                )
                Text("REMAIN  -${formatTime((state.durationMs - state.positionMs).coerceAtLeast(0L))}",
                    color = TextMuted, fontSize = 9.sp)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = onCueReturn,
                        enabled = state.isPrepared && state.cueMs != null,
                        modifier = Modifier.weight(1f),
                        contentPadding = ButtonDefaults.ContentPadding,
                    ) {
                        Text("CUE", color = accent, fontSize = 10.sp)
                    }
                    Button(
                        onClick = onPlayPause,
                        enabled = state.isPrepared,
                        modifier = Modifier.weight(1.3f),
                        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = StudioBackground),
                        contentPadding = ButtonDefaults.ContentPadding,
                    ) {
                        Text(
                            if (state.isLoading) "…" else if (state.isPlaying) "PAUSE" else "PLAY",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                        )
                    }
                }
                TextButton(onClick = onCueSet, enabled = state.isPrepared) {
                    Text(if (state.cueMs == null) "SET CUE" else "UPDATE CUE", color = TextMuted, fontSize = 9.sp)
                }
            }
        }
        Slider(
            value = state.positionMs.toFloat().coerceIn(0f, state.durationMs.coerceAtLeast(1L).toFloat()),
            onValueChange = { onSeek(it.toLong()) },
            valueRange = 0f..state.durationMs.coerceAtLeast(1L).toFloat(),
            enabled = state.isPrepared && state.durationMs > 0L,
            modifier = Modifier.height(26.dp),
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = PanelBorder,
            ),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("RATE", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Slider(
                value = rate,
                onValueChange = {
                    rate = it
                    onRate(it)
                },
                valueRange = 0.75f..1.25f,
                modifier = Modifier.weight(1f).height(28.dp).padding(horizontal = 6.dp),
                colors = androidx.compose.material3.SliderDefaults.colors(
                    thumbColor = accent,
                    activeTrackColor = accent,
                ),
            )
            Text("${(rate * 100).toInt()}%", color = TextMuted, fontSize = 9.sp)
        }
        state.error?.let {
            Text(it, color = DeckBColor, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun JogWheel(
    modifier: Modifier,
    accent: Color,
    playing: Boolean,
    durationMs: Long,
    positionMs: Long,
    onSeek: (Long) -> Unit,
) {
    val wheel = remember { Animatable(0f) }
    LaunchedEffect(playing) {
        if (playing) {
            while (true) {
                wheel.animateTo(360f, tween(3200))
                wheel.snapTo(0f)
            }
        } else {
            wheel.snapTo(0f)
        }
    }
    var draggedPx by remember { mutableFloatStateOf(0f) }
    var dragStartMs by remember { mutableFloatStateOf(0f) }
    Canvas(
        modifier = modifier
            .pointerInput(durationMs) {
                detectDragGestures(
                    onDragStart = {
                        draggedPx = 0f
                        dragStartMs = positionMs.toFloat()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        draggedPx += dragAmount.x
                        if (durationMs > 0L) {
                            onSeek(
                                (dragStartMs + draggedPx / size.width * durationMs)
                                    .toLong()
                                    .coerceIn(0L, durationMs),
                            )
                        }
                    },
                )
            }
            .pointerInput(durationMs) {
                detectTapGestures { point ->
                    if (durationMs > 0L) {
                        val progress = (point.x / size.width).coerceIn(0f, 1f)
                        onSeek((durationMs * progress).toLong())
                    }
                }
            },
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f
        drawCircle(Color(0xFF080B10), radius = radius, center = center)
        drawCircle(PanelBorder, radius = radius * 0.94f, center = center, style = Stroke(width = 2.dp.toPx()))
        rotate(wheel.value, center) {
            for (index in 0 until 24) {
                val angle = Math.toRadians(index * 15.0)
                val inner = radius * 0.76f
                val outer = radius * if (index % 3 == 0) 0.91f else 0.86f
                drawLine(
                    color = if (index % 3 == 0) PanelBorder else Color(0xFF202C37),
                    start = Offset(center.x + kotlin.math.cos(angle).toFloat() * inner, center.y + kotlin.math.sin(angle).toFloat() * inner),
                    end = Offset(center.x + kotlin.math.cos(angle).toFloat() * outer, center.y + kotlin.math.sin(angle).toFloat() * outer),
                    strokeWidth = if (index % 3 == 0) 2.dp.toPx() else 1.dp.toPx(),
                )
            }
        }
        drawCircle(accent.copy(alpha = 0.14f), radius = radius * 0.54f, center = center)
        drawCircle(accent, radius = radius * 0.12f, center = center)
        val progress = if (durationMs == 0L) 0f else positionMs.toFloat() / durationMs
        drawArc(
            color = accent,
            startAngle = -90f,
            sweepAngle = progress.coerceIn(0f, 1f) * 360f,
            useCenter = false,
            topLeft = Offset(center.x - radius * 0.67f, center.y - radius * 0.67f),
            size = androidx.compose.ui.geometry.Size(radius * 1.34f, radius * 1.34f),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

@Composable
private fun MixerPanel(
    modifier: Modifier,
    master: Float,
    onMaster: (Float) -> Unit,
    crossfader: Float,
    onCrossfader: (Float) -> Unit,
    gainA: Float,
    onGainA: (Float) -> Unit,
    gainB: Float,
    onGainB: (Float) -> Unit,
    channelA: Float,
    onChannelA: (Float) -> Unit,
    channelB: Float,
    onChannelB: (Float) -> Unit,
    highA: Float,
    onHighA: (Float) -> Unit,
    midA: Float,
    onMidA: (Float) -> Unit,
    lowA: Float,
    onLowA: (Float) -> Unit,
    highB: Float,
    onHighB: (Float) -> Unit,
    midB: Float,
    onMidB: (Float) -> Unit,
    lowB: Float,
    onLowB: (Float) -> Unit,
    eqAAvailable: Boolean,
    eqBAvailable: Boolean,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0D131A))
            .border(1.dp, PanelBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("MIXER", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Black)
        Text("CHANNEL", color = TextMuted, fontSize = 8.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ChannelColumn(
                modifier = Modifier.weight(1f),
                label = "A",
                accent = DeckAColor,
                gain = gainA,
                onGain = onGainA,
                channel = channelA,
                onChannel = onChannelA,
                high = highA,
                onHigh = onHighA,
                mid = midA,
                onMid = onMidA,
                low = lowA,
                onLow = onLowA,
                eqAvailable = eqAAvailable,
            )
            ChannelColumn(
                modifier = Modifier.weight(1f),
                label = "B",
                accent = DeckBColor,
                gain = gainB,
                onGain = onGainB,
                channel = channelB,
                onChannel = onChannelB,
                high = highB,
                onHigh = onHighB,
                mid = midB,
                onMid = onMidB,
                low = lowB,
                onLow = onLowB,
                eqAvailable = eqBAvailable,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text("CROSSFADER", color = TextMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        Slider(
            value = crossfader,
            onValueChange = onCrossfader,
            modifier = Modifier.fillMaxWidth().height(30.dp),
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = TextPrimary,
                activeTrackColor = DeckAColor,
                inactiveTrackColor = DeckBColor,
            ),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("A", color = DeckAColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text("B", color = DeckBColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(3.dp))
        Text("MASTER  ${(master * 100).toInt()}%", color = TextMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        Slider(
            value = master,
            onValueChange = onMaster,
            modifier = Modifier.fillMaxWidth().height(30.dp),
            colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = DeckAColor, activeTrackColor = DeckAColor),
        )
    }
}

@Composable
private fun ChannelColumn(
    modifier: Modifier,
    label: String,
    accent: Color,
    gain: Float,
    onGain: (Float) -> Unit,
    channel: Float,
    onChannel: (Float) -> Unit,
    high: Float,
    onHigh: (Float) -> Unit,
    mid: Float,
    onMid: (Float) -> Unit,
    low: Float,
    onLow: (Float) -> Unit,
    eqAvailable: Boolean,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = accent, fontSize = 11.sp, fontWeight = FontWeight.Black)
        MixerSlider("GAIN", gain, onGain, accent)
        MixerSlider("HI", high, onHigh, accent, enabled = eqAvailable)
        MixerSlider("MID", mid, onMid, accent, enabled = eqAvailable)
        MixerSlider("LOW", low, onLow, accent, enabled = eqAvailable)
        MixerSlider("VOL", channel, onChannel, accent)
    }
}

@Composable
private fun MixerSlider(
    label: String,
    value: Float,
    onValue: (Float) -> Unit,
    accent: Color,
    enabled: Boolean = true,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = if (enabled) TextMuted else TextMuted.copy(alpha = 0.45f), fontSize = 7.sp)
        Slider(
            value = value,
            onValueChange = onValue,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().height(28.dp),
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = PanelBorder,
                disabledThumbColor = PanelBorder,
            ),
        )
    }
}

@Composable
private fun LibraryDialog(
    library: List<AudioTrack>,
    searchText: String,
    onSearch: (String) -> Unit,
    onDismiss: () -> Unit,
    onImport: () -> Unit,
    onLoad: (DeckSide, AudioTrack) -> Unit,
) {
    var target by remember { mutableStateOf(DeckSide.A) }
    val filtered = library.filter {
        it.title.contains(searchText, ignoreCase = true) ||
            it.artist.contains(searchText, ignoreCase = true)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("MUSIC LIBRARY", modifier = Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Black)
                TextButton(onClick = onImport) { Text("+ IMPORT", color = DeckAColor, fontSize = 10.sp) }
            }
        },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 430.dp)) {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = onSearch,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Search tracks") },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("LOAD TO", color = TextMuted, fontSize = 9.sp)
                    TextButton(onClick = { target = DeckSide.A }) {
                        Text("A", color = if (target == DeckSide.A) DeckAColor else TextMuted)
                    }
                    TextButton(onClick = { target = DeckSide.B }) {
                        Text("B", color = if (target == DeckSide.B) DeckBColor else TextMuted)
                    }
                    Spacer(Modifier.weight(1f))
                    Text("${library.size} TRACKS", color = TextMuted, fontSize = 9.sp)
                }
                if (filtered.isEmpty()) {
                    Text(
                        if (library.isEmpty()) "Import local audio to start your library." else "No matching tracks.",
                        color = TextMuted,
                        modifier = Modifier.padding(vertical = 14.dp),
                        fontSize = 12.sp,
                    )
                } else {
                    LazyColumn {
                        items(filtered, key = { it.uri }) { track ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onLoad(target, track) }
                                    .padding(horizontal = 8.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(track.title, color = TextPrimary, fontSize = 12.sp, maxLines = 1)
                                    Text(
                                        "${track.artist}  •  ${formatTime(track.durationMs)}",
                                        color = TextMuted,
                                        fontSize = 9.sp,
                                        maxLines = 1,
                                    )
                                }
                                Text("LOAD", color = if (target == DeckSide.A) DeckAColor else DeckBColor, fontSize = 9.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("DONE") } },
        containerColor = PanelBackground,
    )
}

private fun formatTime(milliseconds: Long): String {
    val seconds = (milliseconds.coerceAtLeast(0L) / 1000L).toInt()
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
