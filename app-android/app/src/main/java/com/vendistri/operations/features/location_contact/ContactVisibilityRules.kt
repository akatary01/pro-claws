package com.vendistri.operations.features.location_contact

import com.vendistri.operations.features.location.AppLocation
import com.vendistri.operations.features.location.ContactFinancialVisibility
import com.vendistri.operations.features.tasks.TaskFinancialDisplayMode
import com.vendistri.operations.features.tasks.TaskType
import com.vendistri.operations.features.tasks.VendiTask

object ContactVisibilityRules {
    fun visibleTasks(tasks: List<VendiTask>): List<VendiTask> {
        return tasks
            .filter { it.type != TaskType.MachinePickupInventory }
            // Internal task notes must never cross into location-contact presentation.
            .map { it.copy(notes = null) }
    }

    fun visibleTasks(tasks: List<VendiTask>, locationIds: Set<String>): List<VendiTask> {
        return visibleTasks(tasks).filter { it.location in locationIds }
    }

    fun canSeeRefillInventory(location: AppLocation?): Boolean {
        return location?.contactRefillInventoryVisible == true
    }

    fun canSeeTaskMetrics(location: AppLocation?): Boolean {
        return location?.contactTaskMetricsVisible == true
    }

    fun canSeeTaskPhoto(location: AppLocation?): Boolean {
        return location?.contactTaskPhotoVisible != false
    }

    fun canSeeLocationPhoto(location: AppLocation?): Boolean {
        return location?.contactLocationPhotoVisible != false
    }

    fun financialDisplay(location: AppLocation?): TaskFinancialDisplayMode {
        return if (location?.contactFinancialVisibility == ContactFinancialVisibility.GrossAndCommission) {
            TaskFinancialDisplayMode.Full
        } else {
            TaskFinancialDisplayMode.CommissionOnly
        }
    }
}
