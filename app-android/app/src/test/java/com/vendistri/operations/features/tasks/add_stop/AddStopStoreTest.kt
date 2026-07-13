package com.vendistri.operations.features.tasks.add_stop

import com.vendistri.operations.features.location.Address
import com.vendistri.operations.features.location.AppLocation
import com.vendistri.operations.features.tasks.CollectionInputMode
import com.vendistri.operations.features.tasks.TaskBulkPrecheckExistingTask
import com.vendistri.operations.features.tasks.TaskBulkPrecheckItem
import com.vendistri.operations.features.tasks.TaskBulkPrecheckResult
import com.vendistri.operations.features.tasks.TaskPickupLine
import com.vendistri.operations.features.tasks.TaskCreateRequest
import com.vendistri.operations.features.tasks.TaskStatus
import com.vendistri.operations.features.tasks.TaskType
import com.vendistri.operations.features.tasks.TasksStore
import com.vendistri.operations.features.tasks.VendiTask
import com.vendistri.operations.features.tasks.actions.TaskAssignee
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AddStopStoreTest {
    @Test
    fun saveBlocksServiceWithoutBundledChildTypes() = runBlocking {
        val source = FakeAddStopDataSource(
            machines = listOf(machine())
        )
        val store = AddStopStore(source, TasksStore())
        store.prepare(locations)
        store.toggleLocation("location-1", locations)
        store.toggleMachine("machine-1")
        store.toggleTaskType("machine-1", TaskType.MachineCollection)
        store.toggleTaskType("machine-1", TaskType.MachineRefill)
        store.toggleTaskType("machine-1", TaskType.MachineClean)

        val didSave = store.save()

        assertFalse(didSave)
        assertTrue(store.state.value.errorMessage?.contains("bundled task") == true)
        assertTrue(source.createdItems.isEmpty())
    }

    @Test
    fun saveCreatesServiceWithBundledChildTypes() = runBlocking {
        val source = FakeAddStopDataSource(machines = listOf(machine()))
        val store = AddStopStore(source, TasksStore())
        store.prepare(locations)
        store.toggleLocation("location-1", locations)
        store.toggleMachine("machine-1")
        store.setAssignee("machine-1", "operator-1")
        store.setNotes("Front entrance")

        val didSave = store.save()

        assertTrue(didSave)
        assertEquals(1, source.createdItems.size)
        val item = source.createdItems.first()
        assertEquals(TaskType.MachineService, item.type)
        assertEquals(listOf(TaskType.MachineCollection, TaskType.MachineRefill, TaskType.MachineClean), item.childTaskTypes)
        assertEquals("operator-1", item.assigneeId)
        assertEquals("Front entrance", item.notes)
    }

    @Test
    fun saveShowsBlockedAlertWhenPrecheckBlocksExistingSameDayTask() = runBlocking {
        val source = FakeAddStopDataSource(
            machines = listOf(machine()),
            precheckResults = listOf(
                TaskBulkPrecheckResult(
                    ok = false,
                    reason = "same_day_existing",
                    existingTask = TaskBulkPrecheckExistingTask(
                        id = "existing-1",
                        type = TaskType.MachineService,
                        scheduledFor = LocalDate.now().toString(),
                        machineId = "machine-1",
                        locationId = "location-1"
                    ),
                    type = TaskType.MachineService,
                    machineId = "machine-1",
                    scheduledFor = LocalDate.now().toString()
                )
            )
        )
        val store = AddStopStore(source, TasksStore())
        store.prepare(locations)
        store.toggleLocation("location-1", locations)
        store.toggleMachine("machine-1")

        val didSave = store.save()

        assertFalse(didSave)
        assertTrue(store.state.value.precheckAlert is AddStopPrecheckAlertState.Blocked)
        assertTrue(source.createdItems.isEmpty())
    }

    @Test
    fun confirmPrecheckCreatesTasksAfterExistingTaskWarning() = runBlocking {
        val source = FakeAddStopDataSource(
            machines = listOf(machine()),
            precheckResults = listOf(
                TaskBulkPrecheckResult(
                    ok = true,
                    reason = null,
                    existingTask = TaskBulkPrecheckExistingTask(
                        id = "existing-1",
                        type = TaskType.MachineClean,
                        scheduledFor = LocalDate.now().toString(),
                        machineId = "machine-1",
                        locationId = "location-1"
                    ),
                    type = TaskType.MachineService,
                    machineId = "machine-1",
                    scheduledFor = LocalDate.now().toString()
                )
            )
        )
        val store = AddStopStore(source, TasksStore())
        store.prepare(locations)
        store.toggleLocation("location-1", locations)
        store.toggleMachine("machine-1")

        assertFalse(store.save())
        assertTrue(store.state.value.precheckAlert is AddStopPrecheckAlertState.Confirm)
        assertTrue(store.confirmPrecheckAndSave())

        assertEquals(1, source.createdItems.size)
    }

    @Test
    fun rescheduleExistingRechecksCompletedSameDayConflictBeforeMovingTask() = runBlocking {
        val today = LocalDate.now()
        val nextWeek = today.plusWeeks(1)
        fun conflict(id: String, scheduledFor: LocalDate) = TaskBulkPrecheckResult(
            ok = false,
            reason = "same_day_existing",
            existingTask = TaskBulkPrecheckExistingTask(
                id = id,
                type = TaskType.MachineService,
                scheduledFor = scheduledFor.toString(),
                machineId = "machine-1",
                locationId = "location-1"
            ),
            type = TaskType.MachineService,
            machineId = "machine-1",
            scheduledFor = today.toString()
        )
        val source = FakeAddStopDataSource(
            machines = listOf(machine()),
            queuedPrecheckResults = ArrayDeque(
                listOf(
                    listOf(conflict("future-service", nextWeek)),
                    listOf(conflict("completed-today", today))
                )
            )
        )
        val store = AddStopStore(source, TasksStore())
        store.prepare(locations)
        store.toggleLocation("location-1", locations)
        store.toggleMachine("machine-1")

        assertFalse(store.save())
        assertTrue(store.state.value.precheckAlert is AddStopPrecheckAlertState.RescheduleExisting)
        assertFalse(store.confirmRescheduleExistingAndSave())

        assertTrue(store.state.value.precheckAlert is AddStopPrecheckAlertState.Blocked)
        assertEquals("future-service", source.precheckCalls.last().single().taskId)
        assertTrue(source.createdItems.isEmpty())
    }

    private val locations = mapOf(
        "location-1" to AppLocation(
            id = "location-1",
            name = "Downtown Office",
            timeZone = "America/New_York",
            address = Address(
                street = "123 Main St",
                city = "New York",
                state = "NY",
                zipCode = "10001",
                latitude = 40.7128,
                longitude = -74.0060
            ),
            defaultAssigneeId = null,
            discontinued = false
        )
    )

    private fun machine(
        card: Boolean = true,
        cash: Boolean = true,
        coin: Boolean = false
    ): AddStopMachine {
        return AddStopMachine(
            id = "machine-1",
            name = "Machine 1",
            active = true,
            assigned = true,
            type = "vending",
            locationId = "location-1",
            card = card,
            cash = cash,
            coin = coin,
            automatedTaskTypes = AddStopTypeCatalog.defaultTaskTypes
        )
    }

    private class FakeAddStopDataSource(
        private val machines: List<AddStopMachine>,
        private val precheckResults: List<TaskBulkPrecheckResult> = emptyList(),
        private val queuedPrecheckResults: ArrayDeque<List<TaskBulkPrecheckResult>> = ArrayDeque()
    ) : AddStopDataSource {
        val createdItems = mutableListOf<TaskCreateRequest>()
        val precheckItems = mutableListOf<TaskBulkPrecheckItem>()
        val precheckCalls = mutableListOf<List<TaskBulkPrecheckItem>>()

        override suspend fun fetchMachines(): List<AddStopMachine> = machines

        override suspend fun fetchAssignees(): List<TaskAssignee> {
            return listOf(TaskAssignee(id = "operator-1", email = "operator@vendistri.com", firstName = "Operator", lastName = null))
        }

        override suspend fun bulkPrecheckTasks(items: List<TaskBulkPrecheckItem>): List<TaskBulkPrecheckResult> {
            precheckItems.addAll(items)
            precheckCalls.add(items)
            return if (queuedPrecheckResults.isEmpty()) precheckResults else queuedPrecheckResults.removeFirst()
        }

        override suspend fun bulkCreateTasks(items: List<TaskCreateRequest>): List<VendiTask> {
            createdItems.addAll(items)
            return items.mapIndexed { index, item ->
                VendiTask(
                    id = "created-$index",
                    type = item.type,
                    status = TaskStatus.Pending,
                    isPublic = false,
                    assignee = item.assigneeId,
                    assigneeName = null,
                    assigneeEmail = null,
                    machine = item.machineId,
                    machineName = "Machine 1",
                    collectionInputMode = CollectionInputMode.Dollars,
                    creditsPerDollar = null,
                    location = "location-1",
                    locationName = "Downtown Office",
                    locationAddress = null,
                    scheduledFor = item.scheduledFor.toString(),
                    createdAt = "2026-07-03T10:00:00Z",
                    startedAt = null,
                    doneAt = null,
                    isLive = false,
                    duration = null,
                    notes = item.notes,
                    distance = null,
                    gross = null,
                    grossCash = null,
                    grossCard = null,
                    refunds = null,
                    commission = null,
                    net = null,
                    refillTaskId = null,
                    refillTaskIds = emptyList(),
                    pickupLines = emptyList<TaskPickupLine>()
                )
            }
        }
    }
}
