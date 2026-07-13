package com.vendistri.operations.features.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vendistri.operations.design.AppColors
import kotlinx.coroutines.launch

@Composable
internal fun SharedTaskNotesFooter(
    tasks: List<VendiTask>,
    focusKey: String,
    placeholder: String,
    onApplySharedNotes: suspend (List<String>, String?) -> Boolean,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var notesText by remember(focusKey) { mutableStateOf("") }
    var initialNotesText by remember(focusKey) { mutableStateOf("") }
    var isFocused by remember(focusKey) { mutableStateOf(false) }
    var isSaving by remember(focusKey) { mutableStateOf(false) }
    var errorMessage by remember(focusKey) { mutableStateOf<String?>(null) }
    val taskIds = remember(tasks) { tasks.map { it.id }.distinct().filter(String::isNotBlank) }
    val taskNotesSignature = remember(tasks) {
        tasks.joinToString("|") { "${it.id}:${it.notes.orEmpty()}" }
    }

    fun syncFromTasks(force: Boolean) {
        val seed = SharedTaskNotes.seed(tasks)
        if (force) {
            initialNotesText = seed
            notesText = seed
            return
        }
        if (isSaving || isFocused) return
        if (SharedTaskNotes.normalizedValue(notesText) != SharedTaskNotes.normalizedValue(initialNotesText)) return
        if (notesText == seed && initialNotesText == seed) return
        initialNotesText = seed
        notesText = seed
    }

    fun commitIfNeeded() {
        val normalizedNotes = SharedTaskNotes.normalizedValue(notesText)
        if (normalizedNotes == SharedTaskNotes.normalizedValue(initialNotesText)) return
        if (taskIds.isEmpty()) return
        scope.launch {
            isSaving = true
            errorMessage = null
            val didSave = onApplySharedNotes(taskIds, normalizedNotes)
            if (didSave) {
                initialNotesText = notesText
            } else {
                errorMessage = "Failed to save notes."
            }
            isSaving = false
        }
    }

    LaunchedEffect(focusKey) {
        syncFromTasks(force = true)
    }
    LaunchedEffect(taskNotesSignature) {
        syncFromTasks(force = false)
    }
    DisposableEffect(focusKey, taskNotesSignature) {
        onDispose {
            commitIfNeeded()
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SharedTaskNotesSection(
            text = notesText,
            onTextChange = {
                notesText = it
                errorMessage = null
            },
            placeholder = placeholder,
            isSaving = isSaving,
            onFocusChanged = { focused ->
                val wasFocused = isFocused
                isFocused = focused
                if (wasFocused && !focused) {
                    commitIfNeeded()
                }
            }
        )

        errorMessage?.let {
            Text(it, color = AppColors.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}
