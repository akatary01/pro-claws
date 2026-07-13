package com.vendistri.operations.features.main

import com.vendistri.operations.features.tasks.TaskSummary

data class MainUiState(
    val organizationTitle: String = "Vendistri",
    val operatorTaskClaimingEnabled: Boolean = false,
    val canUseOrganizationView: Boolean = false,
    val canManageScheduledTasks: Boolean = false,
    val weekLabel: String = "This week",
    val taskSummary: TaskSummary = TaskSummary(),
    val selectedTab: MainTab = MainTab.Tasks,
    val selectedMapStopId: String? = null
)

enum class MainTab(val title: String) {
    Tasks("Tasks"),
    Locations("Locations"),
    Notifications("Notifications"),
    Settings("Settings")
}
