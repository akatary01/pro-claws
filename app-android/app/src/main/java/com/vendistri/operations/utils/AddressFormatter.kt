package com.vendistri.operations.utils

import com.vendistri.operations.features.location.Address

object AddressFormatter {
    fun singleLine(address: Address?): String? {
        if (address == null) return null
        return singleLine(
            streetLine = address.street,
            cityStateZipLine = cityStateZipLine(address)
        )
    }

    fun singleLineWithoutCountry(address: Address?): String? = singleLine(address)

    fun cityStateZipLine(address: Address?): String? {
        if (address == null) return null
        return cityStateZipLine(address.city, address.state, address.zipCode)
    }

    fun cityStateZipLine(city: String?, state: String?, zipCode: String?): String? {
        val cityLine = trimmed(city)
        val stateZip = listOfNotNull(trimmed(state), trimmed(zipCode)).joinToString(" ")
        return listOfNotNull(cityLine, stateZip.takeIf { it.isNotBlank() })
            .joinToString(", ")
            .takeIf { it.isNotBlank() }
    }

    fun singleLine(streetLine: String?, cityStateZipLine: String?): String? {
        return listOfNotNull(trimmed(streetLine), trimmed(cityStateZipLine))
            .joinToString(", ")
            .takeIf { it.isNotBlank() }
    }

    private fun trimmed(value: String?): String? {
        return value?.trim()?.takeIf { it.isNotEmpty() }
    }
}
