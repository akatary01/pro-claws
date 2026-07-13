package com.vendistri.operations.features.location_contact

import com.vendistri.operations.features.auth.User
import org.junit.Assert.assertEquals
import org.junit.Test

class AppModeStoreTest {
    @Test
    fun contactOnlyUserDefaultsToContactModeWhenLocationsExist() {
        val store = AppModeStore()

        store.syncDefaultMode(user = user(), hasContactLocations = true)

        assertEquals(AppViewMode.LocationContact, store.state.value.mode)
    }

    @Test
    fun organizationUserStaysInOrganizationModeWhenBothViewsExist() {
        val store = AppModeStore()

        store.syncDefaultMode(user = user(isOperator = true), hasContactLocations = true)

        assertEquals(AppViewMode.Organization, store.state.value.mode)
    }

    @Test
    fun contactModeFallsBackWhenContactLocationsDisappear() {
        val store = AppModeStore()
        store.setMode(AppViewMode.LocationContact)

        store.syncDefaultMode(user = user(isAdmin = true), hasContactLocations = false)

        assertEquals(AppViewMode.Organization, store.state.value.mode)
    }

    private fun user(
        isOperator: Boolean = false,
        isAdmin: Boolean = false,
        isOwner: Boolean = false
    ): User {
        return User(
            id = "user-1",
            email = "demo@example.com",
            isOperator = isOperator,
            isAdmin = isAdmin,
            isOwner = isOwner,
            firstName = "Demo",
            lastName = "User"
        )
    }
}
