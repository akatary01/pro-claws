package com.vendistri.operations.features.tasks

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun SharedTaskNotesSection(
    text: String,
    onTextChange: (String) -> Unit,
    placeholder: String,
    isSaving: Boolean,
    modifier: Modifier = Modifier,
    height: Dp = 104.dp,
    onFocusChanged: (Boolean) -> Unit = {}
) {
    SharedTaskNotesTextField(
        text = text,
        onTextChange = onTextChange,
        placeholder = placeholder,
        modifier = modifier,
        height = height,
        isSaving = isSaving,
        onFocusChanged = onFocusChanged
    )
}
