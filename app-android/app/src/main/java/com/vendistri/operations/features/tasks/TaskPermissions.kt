package com.vendistri.operations.features.tasks

import com.vendistri.operations.features.auth.User

enum class TaskActionContext {
    Scheduled,
    ActiveExecution
}

object TaskPermissions {
    fun canManageScheduledTasks(user: User?): Boolean {
        return user?.let { it.isOwner || it.isAdmin } == true
    }

    fun canViewAllScheduledTasks(user: User?): Boolean {
        return canManageScheduledTasks(user)
    }

    fun canIncludeClaimableUnassignedTasks(user: User?, operatorTaskClaimingEnabled: Boolean): Boolean {
        val currentUser = user ?: return false
        if (currentUser.isOwner || currentUser.isAdmin) return true
        return currentUser.isOperator && operatorTaskClaimingEnabled
    }

    fun canAssignToSelf(user: User?): Boolean {
        return user?.let { it.isOwner || it.isAdmin || it.isOperator } == true
    }

    fun canChangeTaskStatus(user: User?, context: TaskActionContext): Boolean {
        val currentUser = user ?: return false
        return when (context) {
            TaskActionContext.Scheduled -> currentUser.isOwner || currentUser.isAdmin
            TaskActionContext.ActiveExecution -> currentUser.isOwner || currentUser.isAdmin || currentUser.isOperator
        }
    }
}
