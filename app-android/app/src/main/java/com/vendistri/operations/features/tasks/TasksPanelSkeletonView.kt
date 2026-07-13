package com.vendistri.operations.features.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.vendistri.operations.components.SkeletonBlock
import com.vendistri.operations.components.SkeletonLine
import com.vendistri.operations.components.SkeletonList

@Composable
internal fun TasksPanelSkeletonView(tab: TasksHomePanelTab) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (tab == TasksHomePanelTab.Overview) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                SkeletonLine(width = 136.dp)
            }
            SkeletonBlock(height = 64.dp)
            MetricSkeletonGrid()
            SkeletonLine(width = 190.dp, modifier = Modifier.padding(top = 36.dp))
        } else {
            SkeletonBlock(height = 48.dp)
            MetricSkeletonGrid()
            SkeletonBlock(height = 46.dp)
            SkeletonList(rows = 3)
        }
    }
}

@Composable
private fun MetricSkeletonGrid() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(2) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                repeat(3) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        SkeletonLine(width = 86.dp, height = 12.dp)
                        SkeletonLine(width = 48.dp, height = 18.dp)
                    }
                }
            }
        }
    }
}
