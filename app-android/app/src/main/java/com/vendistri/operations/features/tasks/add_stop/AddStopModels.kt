package com.vendistri.operations.features.tasks.add_stop

import com.vendistri.operations.features.tasks.TaskType
import com.vendistri.operations.features.tasks.TaskBulkPrecheckExistingTask
import com.vendistri.operations.features.tasks.TaskCreateRequest
import com.vendistri.operations.features.tasks.actions.TaskAssignee
import com.vendistri.operations.features.tasks.optNullableString
import com.vendistri.operations.features.tasks.toJsonObjects
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

data class AddStopMachine(
    val id: String,
    val name: String,
    val active: Boolean,
    val assigned: Boolean,
    val type: String,
    val locationId: String?,
    val card: Boolean,
    val cash: Boolean,
    val coin: Boolean,
    val automatedTaskTypes: Set<TaskType>
) {
    val hasPaymentMethod: Boolean
        get() = card || cash || coin

    val defaultTaskTypes: Set<TaskType>
        get() = automatedTaskTypes.ifEmpty { AddStopTypeCatalog.defaultTaskTypes }
            .intersect(AddStopTypeCatalog.defaultTaskTypes)

    companion object {
        fun fromJson(json: JSONObject): AddStopMachine {
            val automatedTypes = json.optJSONArray("automatedTaskTypes")
                ?: json.optJSONArray("automated_task_types")
            return AddStopMachine(
                id = json.getString("id"),
                name = json.optString("name", "Machine"),
                active = json.optBoolean("active", true),
                assigned = json.optBoolean("assigned", false),
                type = json.optString("type"),
                locationId = json.optNullableString("location"),
                card = json.optBoolean("card", false),
                cash = json.optBoolean("cash", false),
                coin = json.optBoolean("coin", false),
                automatedTaskTypes = automatedTypes?.toTaskTypes().orEmpty()
            )
        }

        fun listFromJson(rawJson: String): List<AddStopMachine> {
            return JSONArray(rawJson).toJsonObjects()
                .map(::fromJson)
                .sortedBy { it.name.lowercase() }
        }
    }
}

data class AddStopUiState(
    val machines: List<AddStopMachine> = emptyList(),
    val assignees: List<TaskAssignee> = emptyList(),
    val selectedDate: LocalDate = LocalDate.now(),
    val selectedLocationIds: Set<String> = emptySet(),
    val selectedMachineIds: Set<String> = emptySet(),
    val selectedTaskTypesByMachineId: Map<String, Set<TaskType>> = emptyMap(),
    val selectedAssigneeIdByMachineId: Map<String, String?> = emptyMap(),
    val sharedNotes: String = "",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val didLoadCatalog: Boolean = false,
    val errorMessage: String? = null,
    val warningMessage: String? = null,
    val untouchedMachinesConfirmed: Boolean = false,
    val precheckAlert: AddStopPrecheckAlertState? = null
) {
    val visibleMachines: List<AddStopMachine>
        get() = machines.filter { it.locationId in selectedLocationIds }

    val taskableVisibleMachines: List<AddStopMachine>
        get() = visibleMachines.filter { it.blockReason == null }
}

sealed interface AddStopPrecheckAlertState {
    val title: String
    val message: String
    val items: List<TaskCreateRequest>

    data class Blocked(
        override val title: String,
        override val message: String,
        val existingTask: TaskBulkPrecheckExistingTask?,
        override val items: List<TaskCreateRequest> = emptyList()
    ) : AddStopPrecheckAlertState

    data class Confirm(
        override val title: String,
        override val message: String,
        override val items: List<TaskCreateRequest>
    ) : AddStopPrecheckAlertState

    data class RescheduleExisting(
        override val title: String,
        override val message: String,
        val existingTask: TaskBulkPrecheckExistingTask,
        override val items: List<TaskCreateRequest>
    ) : AddStopPrecheckAlertState
}

val AddStopMachine.blockReason: String?
    get() = when {
        !assigned || locationId == null -> "Unassigned"
        !active -> "Inactive"
        else -> null
    }

object AddStopTypeCatalog {
    val defaultTaskTypes: Set<TaskType> = setOf(
        TaskType.MachineService,
        TaskType.MachineCollection,
        TaskType.MachineRefill,
        TaskType.MachineClean
    )

    val serviceBundleChildTypes: Set<TaskType> = setOf(
        TaskType.MachineCollection,
        TaskType.MachineRefill,
        TaskType.MachineClean
    )

    val all: List<TaskType> = listOf(
        TaskType.MachineService,
        TaskType.MachineCollection,
        TaskType.MachineRefill,
        TaskType.MachineClean,
        TaskType.MachineRepair,
        TaskType.MachineRefund,
        TaskType.MachineInstall,
        TaskType.MachineRemove
    )
}

object AddStopAssigneeValue {
    const val Unassigned = "__unassigned__"
}

private fun JSONArray.toTaskTypes(): Set<TaskType> {
    return List(length()) { index -> TaskType.from(optString(index)) }.toSet()
}
