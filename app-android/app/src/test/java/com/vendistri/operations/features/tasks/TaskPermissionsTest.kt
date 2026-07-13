package com.vendistri.operations.features.tasks

import com.vendistri.operations.features.auth.User
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskPermissionsTest {
    @Test
    fun ownerAndAdminCanManageScheduledTasksAndIncludeClaimableTasks() {
        assertTrue(TaskPermissions.canManageScheduledTasks(user(isOwner = true)))
        assertTrue(TaskPermissions.canManageScheduledTasks(user(isAdmin = true)))
        assertTrue(TaskPermissions.canIncludeClaimableUnassignedTasks(user(isOwner = true), operatorTaskClaimingEnabled = false))
        assertTrue(TaskPermissions.canIncludeClaimableUnassignedTasks(user(isAdmin = true), operatorTaskClaimingEnabled = false))
    }

    @Test
    fun operatorCannotManageScheduledTasksAndNeedsOrgSettingToIncludeClaimableTasks() {
        val operator = user(isOperator = true)

        assertFalse(TaskPermissions.canManageScheduledTasks(operator))
        assertFalse(TaskPermissions.canIncludeClaimableUnassignedTasks(operator, operatorTaskClaimingEnabled = false))
        assertTrue(TaskPermissions.canIncludeClaimableUnassignedTasks(operator, operatorTaskClaimingEnabled = true))
    }

    private fun user(
        isOperator: Boolean = false,
        isAdmin: Boolean = false,
        isOwner: Boolean = false
    ): User {
        return User(
            id = "user-1",
            email = "user@example.com",
            isOperator = isOperator,
            isAdmin = isAdmin,
            isOwner = isOwner,
            firstName = "User",
            lastName = "One"
        )
    }
}
