package com.vendistri.operations.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.vendistri.operations.design.AppColors
import com.vendistri.operations.design.LocalVendistriPalette

data class SearchableDropdownOption(
    val id: String,
    val title: String,
    val menuTitle: String? = null,
    val subtitle: String? = null,
    val searchText: String,
    val isSuggested: Boolean = false,
    val statusIndicatorColors: List<Color> = emptyList()
)

@Composable
fun SearchableDropdown(
    allLabel: String,
    options: List<SearchableDropdownOption>,
    selectedId: String?,
    onSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
    includesAllOption: Boolean = true,
    enabled: Boolean = true
) {
    val palette = LocalVendistriPalette.current
    var isOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val selectedOption = options.firstOrNull { it.id == selectedId }
    val selectedIndicatorColors = selectedOption?.statusIndicatorColors.orEmpty()
    val filteredOptions = remember(options, query) {
        val trimmed = query.trim().lowercase()
        if (trimmed.isEmpty()) {
            options
        } else {
            options.filter { option ->
                option.title.lowercase().contains(trimmed) ||
                    option.subtitle.orEmpty().lowercase().contains(trimmed) ||
                    option.searchText.lowercase().contains(trimmed)
            }
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val menuWidth = maxWidth
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, palette.border),
            color = palette.surface,
            onClick = { if (enabled) isOpen = !isOpen },
            enabled = enabled
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    selectedOption?.title ?: allLabel,
                    modifier = Modifier.weight(1f),
                    color = palette.textPrimary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                if (selectedIndicatorColors.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        selectedIndicatorColors.forEach { color ->
                            Surface(modifier = Modifier.size(8.dp), shape = CircleShape, color = color) {}
                        }
                    }
                }
                Text(if (isOpen) "⌃" else "⌄", color = palette.textSecondary, fontWeight = FontWeight.SemiBold)
            }
        }

        DropdownMenu(
            expanded = isOpen,
            onDismissRequest = {
                isOpen = false
                query = ""
            },
            modifier = Modifier
                .width(menuWidth)
                .widthIn(max = 560.dp)
                .heightIn(max = 320.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Search...") },
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = palette.surface,
                        unfocusedContainerColor = palette.surface,
                        focusedBorderColor = AppColors.vendBlue,
                        unfocusedBorderColor = palette.border,
                        focusedTextColor = palette.textPrimary,
                        unfocusedTextColor = palette.textPrimary,
                        focusedPlaceholderColor = palette.textSecondary,
                        unfocusedPlaceholderColor = palette.textSecondary
                    )
                )
            }
            if (includesAllOption) {
                DropdownMenuItem(
                    text = {
                        SearchableDropdownRow(
                            title = allLabel,
                            subtitle = null,
                            isSuggested = false,
                            statusIndicatorColors = emptyList(),
                            isSelected = selectedId == null
                        )
                    },
                    onClick = {
                        onSelected(null)
                        isOpen = false
                        query = ""
                    }
                )
            }
            filteredOptions.forEach { option ->
                DropdownMenuItem(
                    text = {
                        SearchableDropdownRow(
                            title = option.menuTitle ?: option.title,
                            subtitle = option.subtitle,
                            isSuggested = option.isSuggested,
                            statusIndicatorColors = option.statusIndicatorColors,
                            isSelected = selectedId == option.id
                        )
                    },
                    onClick = {
                        onSelected(option.id)
                        isOpen = false
                        query = ""
                    }
                )
            }
        }
    }
}

@Composable
fun SearchableMultiSelectDropdown(
    label: String,
    options: List<SearchableDropdownOption>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    searchPlaceholder: String = "Search..."
) {
    val palette = LocalVendistriPalette.current
    var isOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val filteredOptions = remember(options, query) {
        val trimmed = query.trim().lowercase()
        if (trimmed.isEmpty()) {
            options
        } else {
            options.filter { option ->
                option.title.lowercase().contains(trimmed) ||
                    option.subtitle.orEmpty().lowercase().contains(trimmed) ||
                    option.searchText.lowercase().contains(trimmed)
            }
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val menuWidth = maxWidth
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, palette.border),
            color = palette.surface,
            onClick = { if (enabled) isOpen = !isOpen },
            enabled = enabled
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    color = palette.textPrimary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(if (isOpen) "⌃" else "⌄", color = palette.textSecondary, fontWeight = FontWeight.SemiBold)
            }
        }

        DropdownMenu(
            expanded = isOpen,
            onDismissRequest = {
                isOpen = false
                query = ""
            },
            modifier = Modifier
                .width(menuWidth)
                .widthIn(max = 560.dp)
                .heightIn(max = 320.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(searchPlaceholder) },
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = palette.surface,
                        unfocusedContainerColor = palette.surface,
                        focusedBorderColor = AppColors.vendBlue,
                        unfocusedBorderColor = palette.border,
                        focusedTextColor = palette.textPrimary,
                        unfocusedTextColor = palette.textPrimary,
                        focusedPlaceholderColor = palette.textSecondary,
                        unfocusedPlaceholderColor = palette.textSecondary
                    )
                )
            }
            if (filteredOptions.isEmpty()) {
                DropdownMenuItem(
                    text = {
                        Text(
                            "No results",
                            color = palette.textSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    onClick = {}
                )
            }
            filteredOptions.forEach { option ->
                DropdownMenuItem(
                    text = {
                        SearchableDropdownRow(
                            title = option.menuTitle ?: option.title,
                            subtitle = option.subtitle,
                            isSuggested = option.isSuggested,
                            statusIndicatorColors = option.statusIndicatorColors,
                            isSelected = option.id in selectedIds
                        )
                    },
                    onClick = { onToggle(option.id) }
                )
            }
        }
    }
}

@Composable
private fun SearchableDropdownRow(
    title: String,
    subtitle: String?,
    isSuggested: Boolean,
    statusIndicatorColors: List<Color>,
    isSelected: Boolean
) {
    val palette = LocalVendistriPalette.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = suggestedTitleText(title, isSuggested, palette.textPrimary),
                color = palette.textPrimary,
                fontWeight = FontWeight.SemiBold
            )
            subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = palette.textSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (isSelected) {
            Text("✓", color = AppColors.vendBlue, fontWeight = FontWeight.Bold)
        }
        if (statusIndicatorColors.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                statusIndicatorColors.forEach { color ->
                    Surface(modifier = Modifier.size(8.dp), shape = CircleShape, color = color) {}
                }
            }
        }
    }
}

private fun suggestedTitleText(title: String, isSuggested: Boolean, defaultColor: Color) = buildAnnotatedString {
    val suggestedPrefix = "(Suggested)"
    if (isSuggested && title.startsWith(suggestedPrefix)) {
        withStyle(SpanStyle(color = AppColors.vendBlue)) {
            append(suggestedPrefix)
        }
        append(title.removePrefix(suggestedPrefix))
    } else {
        withStyle(SpanStyle(color = defaultColor)) {
            append(title)
        }
    }
}
