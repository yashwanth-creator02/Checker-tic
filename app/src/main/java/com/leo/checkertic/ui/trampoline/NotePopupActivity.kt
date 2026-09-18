package com.leo.checkertic.ui.trampoline

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leo.checkertic.MainActivity
import com.leo.checkertic.data.AppDatabase
import com.leo.checkertic.data.entity.NoteEntity
import com.leo.checkertic.ui.theme.CheckerTicTheme
import com.leo.checkertic.ui.theme.ElectricBlue
import com.leo.checkertic.ui.theme.SubtleGrayLine
import com.leo.checkertic.ui.theme.TextPrimaryDark
import com.leo.checkertic.ui.theme.TextSecondaryDark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A dialog-themed Activity launched when tapping a note in the widget.
 * Displays the note title and scrollable content in a popup dialog.
 */
class NotePopupActivity : ComponentActivity() {

    companion object {
        const val EXTRA_NOTE_ID = "note_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val noteId = intent.getLongExtra(EXTRA_NOTE_ID, -1L)
        val db = AppDatabase.getInstance(applicationContext)

        setContent {
            CheckerTicTheme {
                var note by remember { mutableStateOf<NoteEntity?>(null) }
                var loaded by remember { mutableStateOf(false) }

                LaunchedEffect(noteId) {
                    if (noteId > 0) {
                        val fetched = withContext(Dispatchers.IO) {
                            db.noteDao().getById(noteId)
                        }
                        note = fetched
                    }
                    loaded = true
                }

                NotePopupContent(
                    note = note,
                    loaded = loaded,
                    onDismiss = { finish() },
                    onEditInApp = { id ->
                        val intent = Intent(this@NotePopupActivity, MainActivity::class.java).apply {
                            putExtra("open_note_id", id)
                            putExtra("initial_tab", "notes")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        startActivity(intent)
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
private fun NotePopupContent(
    note: NoteEntity?,
    loaded: Boolean,
    onDismiss: () -> Unit,
    onEditInApp: (Long) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header with Title and Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = note?.title ?: if (loaded) "Note Not Found" else "Loading...",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryDark,
                        maxLines = 2,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondaryDark,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = SubtleGrayLine, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable note content
                val content = note?.content.orEmpty()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp, max = 340.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (content.isNotBlank()) {
                        Text(
                            text = content,
                            fontSize = 14.sp,
                            color = TextSecondaryDark,
                            lineHeight = 22.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else if (loaded && note != null) {
                        Text(
                            text = "This note has no additional content.",
                            fontSize = 13.sp,
                            color = Color(0xFF71717A),
                            fontStyle = FontStyle.Italic,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = SubtleGrayLine, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(8.dp))

                // Actions row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (note != null) {
                        TextButton(
                            onClick = { onEditInApp(note.id) }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = null,
                                tint = ElectricBlue,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Edit in App", color = ElectricBlue)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Close", color = TextSecondaryDark)
                    }
                }
            }
        }
    }
}
