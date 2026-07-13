package com.vendistri.operations.features.location_contact

import com.vendistri.operations.features.location.Address
import com.vendistri.operations.features.location.AppLocation
import com.vendistri.operations.features.location.ContactFinancialVisibility
import com.vendistri.operations.features.tasks.TaskFinancialDisplayMode
import com.vendistri.operations.features.tasks.TaskStatus
import com.vendistri.operations.features.tasks.TaskType
import com.vendistri.operations.features.tasks.VendiTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ContactVisibilityRulesTest {
    @Test
    fun visibleTasksKeepCancelledAndErrorTasksLikeIos() {
        val tasks = listOf(
            task("pending", TaskStatus.Pending),
            task("done", TaskStatus.Done),
            task("cancelled", TaskStatus.Cancelled),
            task("error", TaskStatus.Error)
        )

        assertEquals(listOf("pending", "done", "cancelled", "error"), ContactVisibilityRules.visibleTasks(tasks).map { it.id })
    }

    @Test
    fun visibleTasksHidePickupInventoryTasksLikeIos() {
        val tasks = listOf(
            task("refill", TaskStatus.Pending, type = TaskType.MachineRefill),
            task("pickup", TaskStatus.Pending, type = TaskType.MachinePickupInventory),
            task("collection", TaskStatus.Done, type = TaskType.MachineCollection)
        )

        assertEquals(
            listOf("refill", "collection"),
            ContactVisibilityRules.visibleTasks(tasks).map { it.id }
        )
    }

    @Test
    fun visibleTasksAreScopedToContactLocationIds() {
        val tasks = listOf(
            task("contact-task", TaskStatus.Pending, location = "contact-location"),
            task("other-task", TaskStatus.Pending, location = "other-location"),
            task("missing-location-task", TaskStatus.Pending, location = null),
            task("hidden-final-task", TaskStatus.Cancelled, location = "contact-location")
        )

        assertEquals(
            listOf("contact-task", "hidden-final-task"),
            ContactVisibilityRules.visibleTasks(tasks, setOf("contact-location")).map { it.id }
        )
    }

    @Test
    fun financialDisplayDefaultsToFullAndHonorsCommissionOnly() {
        assertEquals(TaskFinancialDisplayMode.CommissionOnly, ContactVisibilityRules.financialDisplay(null))
        assertEquals(
            TaskFinancialDisplayMode.CommissionOnly,
            ContactVisibilityRules.financialDisplay(location(financialVisibility = ContactFinancialVisibility.CommissionOnly))
        )
        assertEquals(
            TaskFinancialDisplayMode.Full,
            ContactVisibilityRules.financialDisplay(location(financialVisibility = ContactFinancialVisibility.GrossAndCommission))
        )
    }

    @Test
    fun visibilityFlagsDefaultLikeIosContactRules() {
        val hidden = location(
            refillInventoryVisible = false,
            taskMetricsVisible = false,
            taskPhotoVisible = false,
            locationPhotoVisible = false
        )

        assertFalse(ContactVisibilityRules.canSeeRefillInventory(hidden))
        assertFalse(ContactVisibilityRules.canSeeTaskMetrics(hidden))
        assertFalse(ContactVisibilityRules.canSeeTaskPhoto(hidden))
        assertFalse(ContactVisibilityRules.canSeeLocationPhoto(hidden))
        assertTrue(ContactVisibilityRules.canSeeTaskPhoto(null))
        assertTrue(ContactVisibilityRules.canSeeLocationPhoto(null))
    }

    @Test
    fun commissionRangesUseExpectedCalendarBounds() {
        val today = LocalDate.of(2026, 7, 9)

        assertEquals(LocalDate.of(2026, 7, 6) to today, ContactCommissionRange.ThisWeek.bounds(today))
        assertEquals(LocalDate.of(2026, 6, 29) to LocalDate.of(2026, 7, 5), ContactCommissionRange.LastWeek.bounds(today))
        assertEquals(LocalDate.of(2026, 6, 26) to today, ContactCommissionRange.Last2Weeks.bounds(today))
        assertEquals(LocalDate.of(2026, 7, 1) to today, ContactCommissionRange.ThisMonth.bounds(today))
        assertEquals(LocalDate.of(2026, 4, 9) to today, ContactCommissionRange.Last3Months.bounds(today))
        assertEquals(LocalDate.of(2026, 1, 9) to today, ContactCommissionRange.Last6Months.bounds(today))
        assertEquals(LocalDate.of(2026, 1, 1) to today, ContactCommissionRange.ThisYear.bounds(today))
        assertTrue(ContactCommissionRange.ThisWeek.contains(LocalDate.of(2026, 7, 9), today))
        assertFalse(ContactCommissionRange.ThisWeek.contains(LocalDate.of(2026, 7, 13), today))
    }

    @Test
    fun serviceFormUrlEncodesMachineId() {
        assertEquals(
            "https://app.example.com/service-forms?machineId=machine%201%2F2",
            ContactServiceFormUrl.forMachine(
                machineId = "machine 1/2",
                appWebUrl = "https://app.example.com/"
            )
        )
    }

    private fun location(
        financialVisibility: ContactFinancialVisibility = ContactFinancialVisibility.GrossAndCommission,
        refillInventoryVisible: Boolean = true,
        taskMetricsVisible: Boolean = true,
        taskPhotoVisible: Boolean = true,
        locationPhotoVisible: Boolean = true
    ): AppLocation {
        return AppLocation(
            id = "location-1",
            name = "Food Lab Bar + Kitchen",
            timeZone = null,
            address = Address(
                street = "2397 Hylan Blvd",
                city = "Staten Island",
                state = "NY",
                zipCode = "10306",
                latitude = 40.0,
                longitude = -74.0
            ),
            defaultAssigneeId = null,
            discontinued = false,
            contactFinancialVisibility = financialVisibility,
            contactRefillInventoryVisible = refillInventoryVisible,
            contactTaskMetricsVisible = taskMetricsVisible,
            contactTaskPhotoVisible = taskPhotoVisible,
            contactLocationPhotoVisible = locationPhotoVisible
        )
    }

    private fun task(
        id: String,
        status: TaskStatus,
        location: String? = "location-1",
        type: TaskType = TaskType.MachineCollection
    ): VendiTask {
        return VendiTask(
            id = id,
            type = type,
            status = status,
            isPublic = false,
            assignee = null,
            assigneeName = null,
            assigneeEmail = null,
            machine = "machine-1",
            machineName = "Machine",
            collectionInputMode = null,
            creditsPerDollar = null,
            location = location,
            locationName = "Location",
            locationAddress = null,
            scheduledFor = "2026-07-09",
            createdAt = null,
            startedAt = null,
            doneAt = null,
            isLive = false,
            duration = 0.0,
            notes = null,
            distance = 0.0,
            gross = 0.0,
            grossCash = 0.0,
            grossCard = 0.0,
            refunds = 0.0,
            commission = 0.0,
            net = 0.0,
            refillTaskId = null,
            refillTaskIds = emptyList(),
            pickupLines = emptyList()
        )
    }
}
