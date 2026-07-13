package com.vendistri.operations.components.sheets

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vendistri.operations.design.AppShapes
import com.vendistri.operations.design.AppSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberVendistriActionSheetState(
    contentSize: Int = 0
): SheetState {
    val usesLargeContentHeight = contentSize >= VendistriActionSheetLargeContentThreshold
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = false
    )
    LaunchedEffect(usesLargeContentHeight, sheetState) {
        withFrameNanos { }
        withFrameNanos { }
        if (usesLargeContentHeight) {
            sheetState.expand()
        } else if (sheetState.hasPartiallyExpandedState) {
            sheetState.partialExpand()
        }
    }
    return sheetState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VendistriActionSheet(
    onDismissRequest: () -> Unit,
    contentSize: Int = 0,
    sheetState: SheetState = rememberVendistriActionSheetState(contentSize),
    content: @Composable ColumnScope.() -> Unit
) {
    val usesLargeContentHeight = contentSize >= VendistriActionSheetLargeContentThreshold
    val mediumContentHeightFraction = 0.5f
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = AppShapes.sheet,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(top = 8.dp, bottom = 4.dp)
                    .width(34.dp)
                    .height(4.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f)
            ) {}
        },
        contentWindowInsets = { WindowInsets.navigationBars },
        modifier = Modifier
            .padding(top = 34.dp)
            .imePadding()
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (usesLargeContentHeight) {
                            Modifier.height(maxHeight)
                        } else {
                            Modifier.heightIn(
                                min = maxHeight * mediumContentHeightFraction,
                                max = maxHeight
                            )
                        }
                    )
                    .padding(horizontal = AppSpacing.sheetHorizontal)
                    .padding(bottom = AppSpacing.lg),
                content = content
            )
        }
    }
}

const val VendistriActionSheetLargeContentThreshold = 6
