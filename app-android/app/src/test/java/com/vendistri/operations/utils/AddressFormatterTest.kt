package com.vendistri.operations.utils

import com.vendistri.operations.features.location.Address
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class AddressFormatterTest {
    @Test
    fun singleLineFormatsStateAndZipTogether() {
        val address = Address(
            street = "1410 86th St",
            city = "Brooklyn",
            state = "NY",
            zipCode = "11228",
            latitude = null,
            longitude = null
        )

        assertEquals("1410 86th St, Brooklyn, NY 11228", AddressFormatter.singleLineWithoutCountry(address))
        assertEquals("1410 86th St, Brooklyn, NY 11228", address.singleLine)
    }

    @Test
    fun addressParsesPostalCodeAliases() {
        val address = Address.fromJson(
            JSONObject()
                .put("street", "1410 86th St")
                .put("city", "Brooklyn")
                .put("state", "NY")
                .put("postal_code", "11228")
        )

        assertEquals("11228", address?.zipCode)
        assertEquals("1410 86th St, Brooklyn, NY 11228", AddressFormatter.singleLineWithoutCountry(address))
    }

    @Test
    fun addressParsesBackendZipField() {
        val address = Address.fromJson(
            JSONObject()
                .put("street", "2754 Hylan Blvd")
                .put("city", "Staten Island")
                .put("state", "NY")
                .put("zip", 10306)
        )

        assertEquals("10306", address?.zipCode)
        assertEquals("2754 Hylan Blvd, Staten Island, NY 10306", AddressFormatter.singleLineWithoutCountry(address))
    }
}
