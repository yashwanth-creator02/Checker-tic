package com.leo.checkertic.ui.components

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leo.checkertic.core.audio.VoiceStore
import com.leo.checkertic.data.entity.VoiceNoteEntity
import com.leo.checkertic.ui.theme.AppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * ============================================================================
 *  VOICE NOTES (feature 11)
 * ============================================================================
 *
 * A note holds several recordings rather than one.
 *
 * ## The level meter, and why it is drawn this way
 *
 * The meter samples `MediaRecorder.maxAmplitude` on a timer while recording.
 * Two things keep that from being a jank source:
 *
 *  - It samples at 60 ms, not every frame. The eye cannot distinguish the
 *    difference on a bar meter, and it is a third of the wakeups.
 *  - The bars are one `Canvas`, and the amplitude history is a fixed-size
 *    `FloatArray` written in a ring, so nothing allocates per sample. A
 *    `List<Float>` with `drop(1) + newValue` — the obvious version — would
 *    allocate two lists every 60 ms for as long as the user is recording.
 */
@Composable
fun VoiceRecorderBar(
    encryptRecordings: Boolean,
    onSaved: (VoiceStore.Saved) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = AppTheme.colors
    val spacing = AppTheme.spacing
    val scope = rememberCoroutineScope()

    var recording by remember { mutableStateOf<VoiceStore.Recording?>(null) }
    var elapsedMs by remember { mutableStateOf(0L) }
    var permissionDenied by remember { mutableStateOf(false) }

    val levels = remember { FloatArray(LEVEL_BARS) }
    var levelCursor by remember { mutableStateOf(0) }
    var levelTick by remember { mutableStateOf(0) }

    val startRecording = {
        VoiceStore.startRecording(context)
            .onSuccess {
                recording = it
                permissionDenied = false
                java.util.Arrays.fill(levels, 0f)
            }
            .onFailure { permissionDenied = true }
        Unit
    }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startRecording() else permissionDenied = true
    }

    // Sampling loop. Keyed on the recording instance, so it starts and stops
    // exactly with it and can never outlive the recorder it reads from.
    LaunchedEffect(recording) {
        val active = recording ?: return@LaunchedEffect
        while (true) {
            delay(SAMPLE_INTERVAL_MS)
            levels[levelCursor] = active.amplitude()
            levelCursor = (levelCursor + 1) % LEVEL_BARS
            levelTick++
            elapsedMs = System.currentTimeMillis() - active.startedAt
        }
    }

    // A recording left running when the screen is destroyed would leak both
    // the encoder and the microphone.
    DisposableEffect(Unit) {
        onDispose { recording?.let { VoiceStore.cancelRecording(it) } }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(AppTheme.radius.md))
                .background(colors.surface)
                .padding(spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isRecording = recording != null
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isRecording) colors.danger else colors.accentContainer)
                    .clickable {
                        val active = recording
                        if (active == null) {
                            micPermission.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            recording = null
                            scope.launch {
                                VoiceStore.finishRecording(context, active, encryptRecordings)
                                    ?.let(onSaved)
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = if (isRecording) "Stop recording" else "Record a voice note",
                    tint = if (isRecording) colors.textPrimary else colors.onAccentContainer,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(Modifier.width(spacing.md))

            if (isRecording) {
                LevelMeter(
                    levels = levels,
                    cursor = levelCursor,
                    tick = levelTick,
                    modifier = Modifier.weight(1f).height(24.dp)
                )
                Spacer(Modifier.width(spacing.sm))
                Text(
                    text = VoiceStore.formatDuration(elapsedMs),
                    color = colors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            } else {
                Text(
                    text = if (permissionDenied) {
                        "Microphone access is needed to record"
                    } else {
                        "Record a voice note"
                    },
                    color = if (permissionDenied) colors.danger else colors.textSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun LevelMeter(
    levels: FloatArray,
    cursor: Int,
    tick: Int,
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors
    Canvas(modifier = modifier) {
        // `tick` is read so the draw invalidates when a sample lands — the
        // array itself is mutated in place and would not trigger a redraw.
        @Suppress("UNUSED_EXPRESSION") tick
        val barWidth = size.width / (LEVEL_BARS * 2f)
        for (i in 0 until LEVEL_BARS) {
            // Read oldest-first from the ring so the meter scrolls left.
            val value = levels[(cursor + i) % LEVEL_BARS]
            val barHeight = (value.coerceIn(0.04f, 1f)) * size.height
            drawRoundRect(
                color = colors.accent.copy(alpha = 0.35f + value * 0.65f),
                topLeft = Offset(i * barWidth * 2f, (size.height - barHeight) / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

/** One saved recording, with play/stop and delete. */
@Composable
fun VoiceNoteRow(
    voiceNote: VoiceNoteEntity,
    player: VoiceStore.Player,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = AppTheme.colors
    val spacing = AppTheme.spacing
    val scope = rememberCoroutineScope()

    var playing by remember(voiceNote.id) { mutableStateOf(false) }
    var progress by remember(voiceNote.id) { mutableFloatStateOf(0f) }

    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        while (true) {
            delay(PROGRESS_INTERVAL_MS)
            val duration = player.durationMs()
            progress = if (duration > 0) player.positionMs().toFloat() / duration else 0f
        }
    }

    DisposableEffect(Unit) {
        onDispose { if (playing) player.stop() }
    }

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = AppTheme.motion.fastSpec(),
        label = "voice-progress"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppTheme.radius.md))
            .background(colors.surface)
            .padding(horizontal = spacing.md, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = {
                if (playing) {
                    player.stop()
                    playing = false
                    progress = 0f
                } else {
                    scope.launch {
                        playing = player.play(
                            context = context,
                            fileName = voiceNote.fileName,
                            encrypted = voiceNote.encrypted
                        ) {
                            playing = false
                            progress = 0f
                        }
                    }
                }
            },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = if (playing) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                contentDescription = if (playing) "Stop" else "Play",
                tint = colors.accent,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(Modifier.width(spacing.sm))

        Canvas(modifier = Modifier.weight(1f).height(4.dp)) {
            val radius = CornerRadius(size.height / 2f, size.height / 2f)
            drawRoundRect(color = colors.surfaceSunken, cornerRadius = radius)
            drawRoundRect(
                color = colors.accent,
                size = Size(size.width * animatedProgress, size.height),
                cornerRadius = radius
            )
        }

        Spacer(Modifier.width(spacing.sm))
        Text(
            text = VoiceStore.formatDuration(voiceNote.durationMs),
            color = colors.textSecondary,
            fontSize = 12.sp
        )

        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = "Delete recording",
                tint = colors.textMuted,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

private const val LEVEL_BARS = 28
private const val SAMPLE_INTERVAL_MS = 60L
private const val PROGRESS_INTERVAL_MS = 120L
