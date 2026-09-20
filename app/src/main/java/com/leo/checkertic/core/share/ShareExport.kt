package com.leo.checkertic.core.share

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.content.getSystemService
import com.leo.checkertic.data.entity.CategoryEntity
import com.leo.checkertic.data.entity.NoteEntity
import com.leo.checkertic.data.entity.TaskEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Share, copy and export.
 *
 * Shapes were taken from what comparable apps actually do:
 *  - Keep / Todoist share a task as one line, and a note as `title` + blank
 *    line + `content`. No app branding appended — a shared note that says
 *    "Sent from Flip" is noise in someone else's inbox.
 *  - Obsidian exports a note as Markdown with the title as an H1, which is
 *    what makes an exported file useful in any other editor.
 *  - Keep's "copy" puts the same text on the clipboard that "share" would
 *    send, so the two are never subtly different.
 */
object ShareExport {

    private const val AUTHORITY_SUFFIX = ".fileprovider"
    private const val EXPORT_DIR = "exports"

    // -- Text shaping -------------------------------------------------------

    fun taskAsText(task: TaskEntity, categoryName: String?): String {
        val mark = if (task.completed) "[x]" else "[ ]"
        return if (categoryName.isNullOrBlank()) {
            "$mark ${task.title}"
        } else {
            "$mark ${task.title}  ($categoryName)"
        }
    }

    /** A whole category as a Markdown checklist. */
    fun categoryAsText(category: CategoryEntity, tasks: List<TaskEntity>): String =
        buildString {
            appendLine("# ${category.name}")
            appendLine()
            tasks.filter { !it.completed }.forEach { appendLine("- [ ] ${it.title}") }
            val done = tasks.filter { it.completed }
            if (done.isNotEmpty()) {
                appendLine()
                done.forEach { appendLine("- [x] ${it.title}") }
            }
        }.trimEnd()

    fun noteAsText(note: NoteEntity): String =
        if (note.content.isBlank()) note.title else "${note.title}\n\n${note.content}"

    fun noteAsMarkdown(note: NoteEntity): String =
        buildString {
            appendLine("# ${note.title}")
            appendLine()
            appendLine(note.content)
        }.trimEnd()

    // -- Actions ------------------------------------------------------------

    fun shareText(context: Context, text: String, subject: String? = null) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            if (!subject.isNullOrBlank()) putExtra(Intent.EXTRA_SUBJECT, subject)
        }
        context.startActivity(Intent.createChooser(send, null))
    }

    fun copyToClipboard(context: Context, label: String, text: String) {
        context.getSystemService<ClipboardManager>()
            ?.setPrimaryClip(ClipData.newPlainText(label, text))
        // Android 13+ shows its own copy confirmation; a second toast on top
        // of it is duplicate UI, so only older versions get one.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Writes a note to a file in cache and hands it to the share sheet.
     *
     * Cache rather than files: an export is transient, and the system can
     * reclaim it once the receiving app has read it. `FileProvider` gives the
     * receiver a scoped grant instead of a `file://` URI, which has thrown
     * `FileUriExposedException` since Android 7.
     */
    suspend fun exportNoteToFile(
        context: Context,
        note: NoteEntity,
        asMarkdown: Boolean = true
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
            val safeTitle = note.title.ifBlank { "note" }
                .replace(Regex("[^A-Za-z0-9 _-]"), "")
                .trim()
                .take(48)
                .ifBlank { "note" }
            val extension = if (asMarkdown) "md" else "txt"
            val file = File(dir, "$safeTitle-$stamp.$extension")
            file.writeText(if (asMarkdown) noteAsMarkdown(note) else noteAsText(note))

            val uri = FileProvider.getUriForFile(
                context,
                context.packageName + AUTHORITY_SUFFIX,
                file
            )
            val send = Intent(Intent.ACTION_SEND).apply {
                type = if (asMarkdown) "text/markdown" else "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, note.title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            withContext(Dispatchers.Main) {
                context.startActivity(Intent.createChooser(send, null))
            }
            true
        }.getOrDefault(false)
    }

    /** Deletes previously exported files. Called on app start. */
    suspend fun clearExportCache(context: Context) = withContext(Dispatchers.IO) {
        File(context.cacheDir, EXPORT_DIR).listFiles()?.forEach { it.delete() }
        Unit
    }
}
