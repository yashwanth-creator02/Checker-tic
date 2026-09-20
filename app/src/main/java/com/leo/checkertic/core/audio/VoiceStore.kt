package com.leo.checkertic.core.audio

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import com.leo.checkertic.core.crypto.Vault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * ============================================================================
 *  VOICE NOTES
 * ============================================================================
 *
 * Recording, storage and playback for note voice memos.
 *
 * ## Format
 *
 * AAC in an MPEG-4 container (`.m4a`), mono, 44.1 kHz, 96 kbps. Roughly
 * 0.7 MB per minute, plays natively on every Android device since 3.1 and on
 * iOS and desktop without conversion — which matters because these files end
 * up in the share sheet. `setPrivacySensitive(true)` on API 30+ tells the
 * platform this is a private recording, so the system mutes other apps' access
 * to the mic rather than letting them capture concurrently.
 *
 * ## Encryption
 *
 * A locked note's recordings are encrypted at rest with the same vault key
 * that protects its text — otherwise "lock this note" would be a half-truth,
 * with the words hidden and the audio sitting in plain AAC next to it.
 * Playback decrypts to a cache file which is deleted the moment playback
 * stops, and filenames are random UUIDs so the directory listing itself
 * leaks nothing.
 *
 * ## Why MediaRecorder/MediaPlayer rather than Media3
 *
 * Media3 is the right answer for a media *app* — playlists, background
 * playback, notifications, gapless. A voice memo is a single short local file
 * played inline while the user looks at it. The platform classes do that in
 * a few dozen lines with no dependency; ExoPlayer would add ~1.5 MB and a
 * session lifecycle to manage for no behaviour the user would notice.
 */
object VoiceStore {

    private const val DIR = "voice"
    private const val PLAIN_EXT = ".m4a"
    private const val SEALED_EXT = ".m4a.enc"

    private const val SAMPLE_RATE = 44_100
    private const val BIT_RATE = 96_000

    fun dir(context: Context): File =
        File(context.filesDir, DIR).apply { if (!exists()) mkdirs() }

    fun fileFor(context: Context, name: String): File = File(dir(context), name)

    fun newFileName(encrypted: Boolean): String =
        UUID.randomUUID().toString() + if (encrypted) SEALED_EXT else PLAIN_EXT

    // ------------------------------------------------------------------
    // Recording
    // ------------------------------------------------------------------

    class Recording internal constructor(
        private val recorder: MediaRecorder,
        val workingFile: File,
        val startedAt: Long
    ) {
        /** Peak amplitude 0f..1f, for the live level meter. Never throws. */
        fun amplitude(): Float = runCatching {
            (recorder.maxAmplitude / 32768f).coerceIn(0f, 1f)
        }.getOrDefault(0f)

        internal fun finish(): Long {
            runCatching { recorder.stop() }
            runCatching { recorder.release() }
            return System.currentTimeMillis() - startedAt
        }

        internal fun abandon() {
            runCatching { recorder.stop() }
            runCatching { recorder.release() }
            workingFile.delete()
        }
    }

    /**
     * Starts recording into a temporary file.
     *
     * Recording always writes plaintext first and is sealed afterwards if
     * needed: `MediaRecorder` writes the MPEG-4 container's index on stop by
     * seeking back to the header, so it cannot stream into a cipher.
     */
    fun startRecording(context: Context): Result<Recording> = runCatching {
        val working = File(context.cacheDir, "rec_${UUID.randomUUID()}$PLAIN_EXT")
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        recorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioChannels(1)
            setAudioSamplingRate(SAMPLE_RATE)
            setAudioEncodingBitRate(BIT_RATE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) setPrivacySensitive(true)
            setOutputFile(working.absolutePath)
            prepare()
            start()
        }
        Recording(recorder, working, System.currentTimeMillis())
    }

    data class Saved(val fileName: String, val durationMs: Long, val encrypted: Boolean)

    /**
     * Stops the recording and moves it into permanent storage, sealing it if
     * [encrypt] is set. Returns null for a recording too short to be
     * anything but an accidental tap.
     */
    suspend fun finishRecording(
        context: Context,
        recording: Recording,
        encrypt: Boolean
    ): Saved? = withContext(Dispatchers.IO) {
        val duration = recording.finish()
        if (duration < MIN_DURATION_MS || !recording.workingFile.exists()) {
            recording.workingFile.delete()
            return@withContext null
        }
        val name = newFileName(encrypt)
        val target = fileFor(context, name)
        if (encrypt) {
            val sealed = Vault.sealBytes(recording.workingFile.readBytes())
            target.writeBytes(sealed)
        } else {
            recording.workingFile.copyTo(target, overwrite = true)
        }
        recording.workingFile.delete()
        Saved(name, duration, encrypt)
    }

    fun cancelRecording(recording: Recording) = recording.abandon()

    // ------------------------------------------------------------------
    // Playback
    // ------------------------------------------------------------------

    /**
     * Resolves a stored recording to a file that `MediaPlayer` can open,
     * decrypting into cache when necessary.
     */
    private suspend fun playableFile(
        context: Context,
        fileName: String,
        encrypted: Boolean
    ): File? = withContext(Dispatchers.IO) {
        val stored = fileFor(context, fileName)
        if (!stored.exists()) return@withContext null
        if (!encrypted) return@withContext stored
        val plain = Vault.openBytes(stored.readBytes()) ?: return@withContext null
        File(context.cacheDir, "play_${UUID.randomUUID()}$PLAIN_EXT").apply {
            writeBytes(plain)
        }
    }

    /**
     * Single-instance player. Only one memo plays at a time, which is both
     * what users expect and what stops a note with eight recordings from
     * holding eight decoders open.
     */
    class Player {
        private var player: MediaPlayer? = null
        private var temp: File? = null
        var playingFileName: String? = null
            private set

        suspend fun play(
            context: Context,
            fileName: String,
            encrypted: Boolean,
            onComplete: () -> Unit
        ): Boolean {
            stop()
            val file = playableFile(context, fileName, encrypted) ?: return false
            temp = file.takeIf { it.parentFile == context.cacheDir }
            return runCatching {
                MediaPlayer().apply {
                    setDataSource(file.absolutePath)
                    setOnCompletionListener {
                        onComplete()
                        stop()
                    }
                    prepare()
                    start()
                }.also {
                    player = it
                    playingFileName = fileName
                }
                true
            }.getOrElse {
                cleanupTemp()
                false
            }
        }

        fun positionMs(): Int = runCatching { player?.currentPosition ?: 0 }.getOrDefault(0)

        fun durationMs(): Int = runCatching { player?.duration ?: 0 }.getOrDefault(0)

        fun stop() {
            runCatching { player?.stop() }
            runCatching { player?.release() }
            player = null
            playingFileName = null
            cleanupTemp()
        }

        private fun cleanupTemp() {
            temp?.delete()
            temp = null
        }
    }

    // ------------------------------------------------------------------
    // Maintenance
    // ------------------------------------------------------------------

    /** Re-seals or unseals every recording on a note when it is locked/unlocked. */
    suspend fun retarget(
        context: Context,
        fileName: String,
        wasEncrypted: Boolean,
        nowEncrypted: Boolean
    ): String? = withContext(Dispatchers.IO) {
        if (wasEncrypted == nowEncrypted) return@withContext fileName
        val source = fileFor(context, fileName)
        if (!source.exists()) return@withContext null
        val bytes = if (wasEncrypted) {
            Vault.openBytes(source.readBytes()) ?: return@withContext null
        } else {
            source.readBytes()
        }
        val newName = newFileName(nowEncrypted)
        fileFor(context, newName).writeBytes(
            if (nowEncrypted) Vault.sealBytes(bytes) else bytes
        )
        source.delete()
        newName
    }

    suspend fun delete(context: Context, fileName: String) = withContext(Dispatchers.IO) {
        fileFor(context, fileName).delete()
        Unit
    }

    /** Deletes recordings no row points at. Run after a restore. */
    suspend fun pruneUnreferenced(context: Context, referenced: Set<String>) =
        withContext(Dispatchers.IO) {
            dir(context).listFiles()?.forEach { if (it.name !in referenced) it.delete() }
            Unit
        }

    /** Clears decrypted playback scratch files left behind by a process kill. */
    suspend fun clearPlaybackCache(context: Context) = withContext(Dispatchers.IO) {
        context.cacheDir.listFiles()
            ?.filter { it.name.startsWith("play_") || it.name.startsWith("rec_") }
            ?.forEach { it.delete() }
        Unit
    }

    fun formatDuration(ms: Long): String {
        val totalSeconds = (ms / 1000).toInt()
        return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
    }

    private const val MIN_DURATION_MS = 700L
}
