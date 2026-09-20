package com.leo.checkertic.core.backup

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.room.withTransaction
import com.leo.checkertic.data.AppDatabase
import com.leo.checkertic.data.entity.CategoryEntity
import com.leo.checkertic.data.entity.NoteActivityEntity
import com.leo.checkertic.data.entity.NoteEntity
import com.leo.checkertic.data.entity.ReminderEntity
import com.leo.checkertic.data.entity.TaskCompletionEntity
import com.leo.checkertic.data.entity.TaskEntity
import com.leo.checkertic.data.entity.VoiceNoteEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.security.spec.KeySpec
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * ============================================================================
 *  LOCAL BACKUP / RESTORE
 * ============================================================================
 *
 * A backup is one self-describing JSON document, written through the Storage
 * Access Framework to wherever the user points it. No permissions, no
 * hardcoded path, works with local storage and any cloud provider the device
 * has.
 *
 * ## Why JSON and not a copy of the .db file
 *
 * Copying the SQLite file is faster to implement and much worse to live with:
 * it is only restorable into the exact schema version it came from, it needs
 * WAL checkpointing to be consistent, and it is unreadable if the app is ever
 * gone. A versioned JSON document survives schema changes (see
 * [FORMAT_VERSION]) and can be read by anything.
 *
 * ## Encryption is asked, not assumed
 *
 * The brief was explicit that this is the user's call at backup time, and
 * both defaults are wrong for somebody: an unencrypted file is readable by
 * anything with access to the folder, and an encrypted one is worthless if
 * the passphrase is forgotten. So the toggle sits in the export sheet with
 * the consequence spelled out next to it.
 *
 * Backup encryption deliberately does **not** use the Keystore vault key.
 * That key is device-bound and non-exportable, so a Keystore-sealed backup
 * could not be restored onto a new phone — which is most of the reason to
 * take a backup. Instead, backup encryption derives a key from a passphrase
 * the user chooses, via PBKDF2-HMAC-SHA256 with a random per-file salt.
 *
 * Note this means locked content is written to the backup as the ciphertext
 * already sitting in the database, and that ciphertext is only openable by
 * the original device's Keystore. That is stated plainly in the UI rather
 * than silently producing a restore with unreadable notes.
 */
object BackupManager {

    const val FORMAT_VERSION = 2
    private const val MAGIC = "flip.backup"

    private const val PBKDF2_ITERATIONS = 210_000
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val GCM_TAG_BITS = 128

    /**
     * Cap on what will be parsed during restore.
     *
     * A restore reads the whole document into memory to validate it before
     * touching live data, so an unbounded read is an OOM waiting to happen
     * if the user picks the wrong file. 64 MB is far beyond any realistic
     * backup of this app and far below anything that would kill the process.
     */
    private const val MAX_RESTORE_BYTES = 64L * 1024 * 1024

    sealed interface Outcome {
        data class Success(val message: String) : Outcome
        data class Failure(val message: String) : Outcome
    }

    data class Preview(
        val formatVersion: Int,
        val createdAt: Long,
        val encrypted: Boolean,
        val categories: Int,
        val tasks: Int,
        val notes: Int,
        val completions: Int
    )

    // ------------------------------------------------------------------
    // Export
    // ------------------------------------------------------------------

    suspend fun export(
        context: Context,
        destination: Uri,
        passphrase: String?
    ): Outcome = withContext(Dispatchers.IO) {
        runCatching {
            val db = AppDatabase.getInstance(context)
            val payload = buildPayload(db)

            val body: ByteArray
            val envelope = JSONObject().apply {
                put("magic", MAGIC)
                put("formatVersion", FORMAT_VERSION)
                put("createdAt", System.currentTimeMillis())
            }

            if (passphrase.isNullOrEmpty()) {
                envelope.put("encrypted", false)
                body = payload.toString().toByteArray(Charsets.UTF_8)
                envelope.put("payload", String(body, Charsets.UTF_8))
            } else {
                val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
                val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
                val key = deriveKey(passphrase, salt)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
                val sealed = cipher.doFinal(payload.toString().toByteArray(Charsets.UTF_8))
                envelope.put("encrypted", true)
                envelope.put("kdf", "PBKDF2WithHmacSHA256")
                envelope.put("iterations", PBKDF2_ITERATIONS)
                envelope.put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
                envelope.put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
                envelope.put("payload", Base64.encodeToString(sealed, Base64.NO_WRAP))
            }

            context.contentResolver.openOutputStream(destination, "wt")?.use { out ->
                out.write(envelope.toString().toByteArray(Charsets.UTF_8))
                out.flush()
            } ?: return@runCatching Outcome.Failure("Couldn't open the chosen location for writing.")

            val counts = payload.getJSONObject("counts")
            Outcome.Success(
                "Backed up ${counts.getInt("tasks")} tasks, " +
                    "${counts.getInt("notes")} notes and " +
                    "${counts.getInt("completions")} completions."
            )
        }.getOrElse { Outcome.Failure(it.message ?: "Backup failed.") }
    }

    private suspend fun buildPayload(db: AppDatabase): JSONObject {
        val categories = db.categoryDao().getAllOnce()
        val tasks = db.taskDao().getAllTasksOnce()
        val completions = db.analyticsDao().getAllOnce()
        val notes = db.noteDao().getAllOnce()
        val voice = db.voiceNoteDao().getAllOnce()
        val activity = db.noteDao().allActivityOnce()
        val reminders = db.reminderDao().getAllOnce()

        return JSONObject().apply {
            put("counts", JSONObject().apply {
                put("categories", categories.size)
                put("tasks", tasks.size)
                put("notes", notes.size)
                put("completions", completions.size)
            })
            put("categories", JSONArray().apply {
                categories.forEach { c ->
                    put(JSONObject().apply {
                        put("id", c.id); put("name", c.name); put("orderIndex", c.orderIndex)
                        put("recurrenceType", c.recurrenceType)
                        put("recurrenceCustomDays", c.recurrenceCustomDays)
                        put("lastPeriodKey", c.lastPeriodKey)
                        put("pinned", c.pinned); put("locked", c.locked)
                        put("background", c.background ?: JSONObject.NULL)
                        put("sortMode", c.sortMode)
                        put("createdAt", c.createdAt); put("updatedAt", c.updatedAt)
                    })
                }
            })
            put("tasks", JSONArray().apply {
                tasks.forEach { t ->
                    put(JSONObject().apply {
                        put("id", t.id); put("title", t.title); put("categoryId", t.categoryId)
                        put("completed", t.completed); put("pinned", t.pinned)
                        put("encrypted", t.encrypted); put("orderIndex", t.orderIndex)
                        put("createdAt", t.createdAt); put("updatedAt", t.updatedAt)
                        put("completedAt", t.completedAt ?: JSONObject.NULL)
                    })
                }
            })
            put("completions", JSONArray().apply {
                completions.forEach { c ->
                    put(JSONObject().apply {
                        put("id", c.id); put("taskId", c.taskId)
                        put("completedAt", c.completedAt); put("categoryId", c.categoryId)
                    })
                }
            })
            put("notes", JSONArray().apply {
                notes.forEach { n ->
                    put(JSONObject().apply {
                        put("id", n.id); put("title", n.title); put("content", n.content)
                        put("updatedAt", n.updatedAt); put("createdAt", n.createdAt)
                        put("pinned", n.pinned); put("locked", n.locked)
                        put("encrypted", n.encrypted)
                        put("background", n.background ?: JSONObject.NULL)
                        put("orderIndex", n.orderIndex)
                    })
                }
            })
            put("voiceNotes", JSONArray().apply {
                voice.forEach { v ->
                    put(JSONObject().apply {
                        put("id", v.id); put("noteId", v.noteId); put("fileName", v.fileName)
                        put("durationMs", v.durationMs); put("createdAt", v.createdAt)
                        put("encrypted", v.encrypted)
                    })
                }
            })
            put("noteActivity", JSONArray().apply {
                activity.forEach { a ->
                    put(JSONObject().apply {
                        put("id", a.id); put("noteId", a.noteId)
                        put("kind", a.kind); put("at", a.at)
                    })
                }
            })
            put("reminders", JSONArray().apply {
                reminders.forEach { r ->
                    put(JSONObject().apply {
                        put("id", r.id); put("ownerType", r.ownerType); put("ownerId", r.ownerId)
                        put("triggerAt", r.triggerAt); put("repeatMode", r.repeatMode)
                        put("repeatIntervalDays", r.repeatIntervalDays)
                        put("enabled", r.enabled)
                        put("lastFiredAt", r.lastFiredAt ?: JSONObject.NULL)
                    })
                }
            })
        }
    }

    // ------------------------------------------------------------------
    // Validation
    // ------------------------------------------------------------------

    /**
     * Reads and checks a backup file *without* touching live data.
     *
     * This is the whole point of a separate preview step: the user gets told
     * what they are about to overwrite themselves with, and a corrupt or
     * wrong-app file is rejected while their real data is still intact. A
     * restore that validates as it writes is a restore that can leave the
     * database half-replaced.
     */
    suspend fun inspect(
        context: Context,
        source: Uri,
        passphrase: String?
    ): Result<Preview> = withContext(Dispatchers.IO) {
        runCatching {
            val envelope = readEnvelope(context, source)
            val encrypted = envelope.optBoolean("encrypted", false)
            val payload = decodePayload(envelope, passphrase)
                ?: error(
                    if (encrypted) "Wrong passphrase, or the file is damaged."
                    else "The file is damaged."
                )
            Preview(
                formatVersion = envelope.optInt("formatVersion", 1),
                createdAt = envelope.optLong("createdAt", 0L),
                encrypted = encrypted,
                categories = payload.optJSONArray("categories")?.length() ?: 0,
                tasks = payload.optJSONArray("tasks")?.length() ?: 0,
                notes = payload.optJSONArray("notes")?.length() ?: 0,
                completions = payload.optJSONArray("completions")?.length() ?: 0
            )
        }
    }

    private fun readEnvelope(context: Context, source: Uri): JSONObject {
        val bytes = context.contentResolver.openInputStream(source)?.use { input ->
            val buffer = java.io.ByteArrayOutputStream()
            val chunk = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val read = input.read(chunk)
                if (read <= 0) break
                total += read
                require(total <= MAX_RESTORE_BYTES) { "That file is too large to be a Flip backup." }
                buffer.write(chunk, 0, read)
            }
            buffer.toByteArray()
        } ?: error("Couldn't read the chosen file.")

        val envelope = runCatching { JSONObject(String(bytes, Charsets.UTF_8)) }
            .getOrElse { error("That isn't a Flip backup file.") }
        require(envelope.optString("magic") == MAGIC) { "That isn't a Flip backup file." }
        require(envelope.optInt("formatVersion", 0) in 1..FORMAT_VERSION) {
            "This backup was made by a newer version of Flip."
        }
        return envelope
    }

    private fun decodePayload(envelope: JSONObject, passphrase: String?): JSONObject? {
        val raw = envelope.optString("payload").takeIf { it.isNotEmpty() } ?: return null
        return if (!envelope.optBoolean("encrypted", false)) {
            runCatching { JSONObject(raw) }.getOrNull()
        } else {
            if (passphrase.isNullOrEmpty()) return null
            runCatching {
                val salt = Base64.decode(envelope.getString("salt"), Base64.NO_WRAP)
                val iv = Base64.decode(envelope.getString("iv"), Base64.NO_WRAP)
                val iterations = envelope.optInt("iterations", PBKDF2_ITERATIONS)
                val key = deriveKey(passphrase, salt, iterations)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
                val plain = cipher.doFinal(Base64.decode(raw, Base64.NO_WRAP))
                JSONObject(String(plain, Charsets.UTF_8))
            }.getOrNull()
        }
    }

    /** True when this file needs a passphrase, checked before prompting for one. */
    suspend fun isEncrypted(context: Context, source: Uri): Boolean =
        withContext(Dispatchers.IO) {
            runCatching { readEnvelope(context, source).optBoolean("encrypted", false) }
                .getOrDefault(false)
        }

    // ------------------------------------------------------------------
    // Restore
    // ------------------------------------------------------------------

    /**
     * Replaces live data with the backup's contents.
     *
     * Everything happens inside one Room transaction, so a failure part-way
     * rolls the database back to exactly where it started rather than leaving
     * the user with half of one dataset and half of another.
     */
    suspend fun restore(
        context: Context,
        source: Uri,
        passphrase: String?
    ): Outcome = withContext(Dispatchers.IO) {
        runCatching {
            val envelope = readEnvelope(context, source)
            val payload = decodePayload(envelope, passphrase)
                ?: return@runCatching Outcome.Failure(
                    if (envelope.optBoolean("encrypted", false))
                        "Wrong passphrase, or the file is damaged."
                    else "The file is damaged."
                )

            val db = AppDatabase.getInstance(context)
            val categories = parseCategories(payload)
            val tasks = parseTasks(payload)
            val completions = parseCompletions(payload)
            val notes = parseNotes(payload)
            val voice = parseVoiceNotes(payload)
            val activity = parseActivity(payload)
            val reminders = parseReminders(payload)

            // Referential sanity: a task pointing at a category that isn't in
            // the file would violate the foreign key and abort the whole
            // transaction. Drop the orphans instead and say so.
            val categoryIds = categories.map { it.id }.toSet()
            val noteIds = notes.map { it.id }.toSet()
            val taskIds = tasks.map { it.id }.toSet()
            val keptTasks = tasks.filter { it.categoryId in categoryIds }
            val keptTaskIds = keptTasks.map { it.id }.toSet()
            val keptCompletions = completions.filter { it.taskId in keptTaskIds }
            val keptVoice = voice.filter { it.noteId in noteIds }
            val keptActivity = activity.filter { it.noteId in noteIds }
            val dropped = (tasks.size - keptTasks.size) +
                (completions.size - keptCompletions.size) +
                (voice.size - keptVoice.size) +
                (activity.size - keptActivity.size) +
                (taskIds.size - keptTaskIds.size).coerceAtLeast(0)

            db.withTransaction {
                db.reminderDao().deleteAll()
                db.noteDao().deleteAllActivity()
                db.voiceNoteDao().deleteAll()
                db.analyticsDao().deleteAll()
                db.taskDao().deleteAll()
                db.noteDao().deleteAll()
                db.categoryDao().deleteAll()

                db.categoryDao().insertAll(categories)
                db.noteDao().insertAll(notes)
                db.taskDao().insertAll(keptTasks)
                db.taskDao().insertCompletions(keptCompletions)
                db.voiceNoteDao().insertAll(keptVoice)
                db.noteDao().insertActivities(keptActivity)
                db.reminderDao().insertAll(reminders)
            }

            val suffix = if (dropped > 0) " ($dropped orphaned rows skipped.)" else ""
            Outcome.Success(
                "Restored ${keptTasks.size} tasks and ${notes.size} notes.$suffix"
            )
        }.getOrElse { Outcome.Failure(it.message ?: "Restore failed.") }
    }

    // ------------------------------------------------------------------
    // Parsing
    // ------------------------------------------------------------------

    private inline fun <T> JSONArray?.map(block: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        val out = ArrayList<T>(length())
        for (i in 0 until length()) out.add(block(getJSONObject(i)))
        return out
    }

    private fun JSONObject.stringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

    private fun JSONObject.longOrNull(key: String): Long? =
        if (isNull(key)) null else optLong(key)

    private fun parseCategories(p: JSONObject) = p.optJSONArray("categories").map { o ->
        CategoryEntity(
            id = o.getLong("id"),
            name = o.getString("name"),
            orderIndex = o.optInt("orderIndex"),
            recurrenceType = o.optString("recurrenceType", "once"),
            recurrenceCustomDays = o.optInt("recurrenceCustomDays"),
            lastPeriodKey = o.optString("lastPeriodKey", ""),
            pinned = o.optBoolean("pinned"),
            locked = o.optBoolean("locked"),
            background = o.stringOrNull("background"),
            sortMode = o.optString("sortMode", "manual"),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
        )
    }

    private fun parseTasks(p: JSONObject) = p.optJSONArray("tasks").map { o ->
        TaskEntity(
            id = o.getLong("id"),
            title = o.getString("title"),
            categoryId = o.getLong("categoryId"),
            completed = o.optBoolean("completed"),
            pinned = o.optBoolean("pinned"),
            encrypted = o.optBoolean("encrypted"),
            orderIndex = o.optInt("orderIndex"),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
            completedAt = o.longOrNull("completedAt")
        )
    }

    private fun parseCompletions(p: JSONObject) = p.optJSONArray("completions").map { o ->
        TaskCompletionEntity(
            id = o.getLong("id"),
            taskId = o.getLong("taskId"),
            completedAt = o.getLong("completedAt"),
            categoryId = o.optLong("categoryId", -1L)
        )
    }

    private fun parseNotes(p: JSONObject) = p.optJSONArray("notes").map { o ->
        NoteEntity(
            id = o.getLong("id"),
            title = o.optString("title", ""),
            content = o.optString("content", ""),
            updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            pinned = o.optBoolean("pinned"),
            locked = o.optBoolean("locked"),
            encrypted = o.optBoolean("encrypted"),
            background = o.stringOrNull("background"),
            orderIndex = o.optInt("orderIndex")
        )
    }

    private fun parseVoiceNotes(p: JSONObject) = p.optJSONArray("voiceNotes").map { o ->
        VoiceNoteEntity(
            id = o.getLong("id"),
            noteId = o.getLong("noteId"),
            fileName = o.getString("fileName"),
            durationMs = o.optLong("durationMs"),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            encrypted = o.optBoolean("encrypted")
        )
    }

    private fun parseActivity(p: JSONObject) = p.optJSONArray("noteActivity").map { o ->
        NoteActivityEntity(
            id = o.getLong("id"),
            noteId = o.getLong("noteId"),
            kind = o.optString("kind", "edited"),
            at = o.optLong("at")
        )
    }

    private fun parseReminders(p: JSONObject) = p.optJSONArray("reminders").map { o ->
        ReminderEntity(
            id = o.getLong("id"),
            ownerType = o.getString("ownerType"),
            ownerId = o.getLong("ownerId"),
            triggerAt = o.optLong("triggerAt"),
            repeatMode = o.optString("repeatMode", ReminderEntity.REPEAT_NONE),
            repeatIntervalDays = o.optInt("repeatIntervalDays"),
            enabled = o.optBoolean("enabled", true),
            lastFiredAt = o.longOrNull("lastFiredAt")
        )
    }

    private fun deriveKey(
        passphrase: String,
        salt: ByteArray,
        iterations: Int = PBKDF2_ITERATIONS
    ): SecretKeySpec {
        val spec: KeySpec = PBEKeySpec(passphrase.toCharArray(), salt, iterations, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }
}
