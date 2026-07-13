package com.vendistri.operations.features.location

import com.vendistri.operations.features.tasks.CommissionPaymentType
import com.vendistri.operations.features.tasks.optNullableBoolean
import com.vendistri.operations.features.tasks.optNullableDouble
import com.vendistri.operations.features.tasks.optNullableString
import com.vendistri.operations.features.tasks.toJsonObjects
import org.json.JSONArray
import org.json.JSONObject

data class Address(
    val street: String?,
    val city: String?,
    val state: String?,
    val zipCode: String?,
    val latitude: Double?,
    val longitude: Double?
) {
    val singleLine: String
        get() {
            val stateZip = listOfNotNull(state, zipCode)
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .joinToString(" ")
            return listOfNotNull(street, city, stateZip)
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .joinToString(", ")
        }

    companion object {
        fun fromJson(json: JSONObject?): Address? {
            if (json == null) return null
            return Address(
                street = json.optNullableString("street") ?: json.optNullableString("address"),
                city = json.optNullableString("city"),
                state = json.optNullableString("state"),
                zipCode = json.optNullableString("zip_code")
                    ?: json.optNullableString("zipCode")
                    ?: json.optNullableString("zip")
                    ?: json.optNullableString("postal_code")
                    ?: json.optNullableString("postalCode"),
                latitude = if (json.has("lat")) json.optDouble("lat") else json.optDouble("latitude").takeIf { json.has("latitude") },
                longitude = if (json.has("lng")) json.optDouble("lng") else json.optDouble("longitude").takeIf { json.has("longitude") }
            )
        }
    }
}

enum class LocationCommissionType(val rawValue: String) {
    PercentGrossSales("%_gross_sales"),
    PercentGrossProfit("%_gross_profit"),
    Monthly("monthly"),
    None("none");

    companion object {
        fun from(rawValue: String?): LocationCommissionType {
            return entries.firstOrNull { it.rawValue == rawValue } ?: None
        }
    }
}

enum class LocationCommissionRoundingMode(val rawValue: String) {
    None("none"),
    NearestDollar("nearest_dollar"),
    UpToDollar("up_to_dollar"),
    DownToDollar("down_to_dollar");

    companion object {
        fun from(rawValue: String?): LocationCommissionRoundingMode? {
            return entries.firstOrNull { it.rawValue == rawValue }
        }
    }
}

data class LocationCommission(
    val type: LocationCommissionType,
    val value: Double?,
    val roundingMode: LocationCommissionRoundingMode?,
    val profitMarginPercent: Double?,
    val lastCommissionPaidAt: String?,
    val paymentType: CommissionPaymentType?,
    val excludeRefundsFromCommission: Boolean?,
    val excludeCardTransactionSurchargeFromCommission: Boolean?
) {
    companion object {
        fun fromJson(json: JSONObject?): LocationCommission? {
            if (json == null) return null
            return LocationCommission(
                type = LocationCommissionType.from(json.optNullableString("type")),
                value = json.optNullableDouble("value"),
                roundingMode = LocationCommissionRoundingMode.from(
                    json.optNullableString("rounding_mode") ?: json.optNullableString("roundingMode")
                ),
                profitMarginPercent = json.optNullableDouble("profit_margin_percent")
                    ?: json.optNullableDouble("profitMarginPercent"),
                lastCommissionPaidAt = json.optNullableString("last_commission_paid_at")
                    ?: json.optNullableString("lastCommissionPaidAt"),
                paymentType = CommissionPaymentType.from(
                    json.optNullableString("payment_type") ?: json.optNullableString("paymentType")
                ),
                excludeRefundsFromCommission = json.optNullableBoolean("exclude_refunds_from_commission")
                    ?: json.optNullableBoolean("excludeRefundsFromCommission"),
                excludeCardTransactionSurchargeFromCommission =
                    json.optNullableBoolean("exclude_card_transaction_surcharge_from_commission")
                        ?: json.optNullableBoolean("excludeCardTransactionSurchargeFromCommission")
            )
        }
    }
}

data class LocationDayHours(
    val day: String?,
    val closed: Boolean?,
    val open: String?,
    val close: String?
) {
    companion object {
        fun fromJson(json: JSONObject): LocationDayHours {
            return LocationDayHours(
                day = json.optNullableString("day"),
                closed = json.optNullableBoolean("closed"),
                open = json.optNullableString("open"),
                close = json.optNullableString("close")
            )
        }

        fun mapFromJson(json: JSONObject?): Map<String, LocationDayHours>? {
            if (json == null) return null
            return json.keys().asSequence().associateWith { key ->
                fromJson(json.getJSONObject(key))
            }
        }
    }
}

data class AppLocation(
    val id: String,
    val name: String,
    val timeZone: String?,
    val address: Address?,
    val hours: Map<String, LocationDayHours>? = null,
    val commission: LocationCommission? = null,
    val defaultAssigneeId: String?,
    val discontinued: Boolean,
    val contactTransactionVisibility: ContactTransactionVisibility = ContactTransactionVisibility.Visible,
    val contactFinancialVisibility: ContactFinancialVisibility = ContactFinancialVisibility.CommissionOnly,
    val contactRefillInventoryVisible: Boolean = true,
    val contactTaskMetricsVisible: Boolean = true,
    val contactTaskPhotoVisible: Boolean = true,
    val contactLocationPhotoVisible: Boolean = true,
    val assets: List<LocationPhotoAsset> = emptyList()
) {
    companion object {
        fun fromJson(json: JSONObject): AppLocation {
            return AppLocation(
                id = json.getString("id"),
                name = json.optString("name"),
                timeZone = json.optNullableString("time_zone") ?: json.optNullableString("timeZone"),
                address = Address.fromJson(json.optJSONObject("address")),
                hours = LocationDayHours.mapFromJson(json.optJSONObject("hours")),
                commission = LocationCommission.fromJson(json.optJSONObject("commission")),
                defaultAssigneeId = json.optNullableString("default_assignee_id")
                    ?: json.optNullableString("defaultAssigneeId"),
                discontinued = json.optBoolean("discontinued", false),
                contactTransactionVisibility = ContactTransactionVisibility.from(
                    json.optNullableString("contact_transaction_visibility")
                        ?: json.optNullableString("contactTransactionVisibility")
                ),
                contactFinancialVisibility = ContactFinancialVisibility.from(
                    json.optNullableString("contact_financial_visibility")
                        ?: json.optNullableString("contactFinancialVisibility")
                ),
                contactRefillInventoryVisible = json.optNullableBoolean("contact_refill_inventory_visible")
                    ?: json.optNullableBoolean("contactRefillInventoryVisible")
                    ?: true,
                contactTaskMetricsVisible = json.optNullableBoolean("contact_task_metrics_visible")
                    ?: json.optNullableBoolean("contactTaskMetricsVisible")
                    ?: true,
                contactTaskPhotoVisible = json.optNullableBoolean("contact_task_photo_visible")
                    ?: json.optNullableBoolean("contactTaskPhotoVisible")
                    ?: true,
                contactLocationPhotoVisible = json.optNullableBoolean("contact_location_photo_visible")
                    ?: json.optNullableBoolean("contactLocationPhotoVisible")
                    ?: true,
                assets = json.optJSONArray("assets")?.toJsonObjects()?.mapNotNull(LocationPhotoAsset::fromJson).orEmpty()
            )
        }

        fun listFromJson(rawJson: String): List<AppLocation> {
            return JSONArray(rawJson).toJsonObjects().map(::fromJson)
        }
    }
}

enum class ContactTransactionVisibility(val rawValue: String) {
    Hidden("hidden"),
    Visible("visible");

    companion object {
        fun from(rawValue: String?): ContactTransactionVisibility {
            return entries.firstOrNull { it.rawValue == rawValue } ?: Visible
        }
    }
}

enum class ContactFinancialVisibility(val rawValue: String) {
    CommissionOnly("commission_only"),
    GrossAndCommission("gross_and_commission");

    companion object {
        fun from(rawValue: String?): ContactFinancialVisibility {
            return entries.firstOrNull { it.rawValue == rawValue } ?: CommissionOnly
        }
    }
}

data class LocationPhotoAsset(
    val id: String,
    val url: String?,
    val type: String?,
    val createdAt: String? = null
) {
    companion object {
        fun fromJson(json: JSONObject): LocationPhotoAsset? {
            val id = json.optNullableString("id") ?: return null
            return LocationPhotoAsset(
                id = id,
                url = json.optNullableString("url")
                    ?: json.optNullableString("downloadUrl")
                    ?: json.optNullableString("download_url"),
                type = json.optNullableString("type"),
                createdAt = json.optNullableString("created_at")
                    ?: json.optNullableString("createdAt")
            )
        }
    }
}

data class PortalLocationMachine(
    val id: String,
    val name: String,
    val cancelledAutomationTaskPolicy: String?
) {
    companion object {
        fun fromJson(json: JSONObject): PortalLocationMachine? {
            val id = json.optNullableString("id") ?: return null
            return PortalLocationMachine(
                id = id,
                name = json.optNullableString("name").orEmpty(),
                cancelledAutomationTaskPolicy = json.optNullableString("cancelled_automation_task_policy")
                    ?: json.optNullableString("cancelledAutomationTaskPolicy")
            )
        }

        fun listFromJson(rawJson: String): List<PortalLocationMachine> {
            return JSONArray(rawJson).toJsonObjects().mapNotNull(::fromJson)
        }
    }
}

data class WarehouseOption(
    val id: String,
    val name: String,
    val inventoryId: String,
    val address: Address?,
    val organization: String?,
    val isPublic: Boolean,
    val isActive: Boolean
) {
    companion object {
        fun fromJson(json: JSONObject): WarehouseOption {
            return WarehouseOption(
                id = json.getString("id"),
                name = json.optString("name"),
                inventoryId = json.optString("inventoryId"),
                address = Address.fromJson(json.optJSONObject("address")),
                organization = json.optNullableString("organization"),
                isPublic = json.optBoolean("isPublic"),
                isActive = json.optBoolean("isActive", true)
            )
        }

        fun listFromJson(rawJson: String): List<WarehouseOption> {
            return JSONArray(rawJson).toJsonObjects().map(::fromJson)
        }
    }
}
