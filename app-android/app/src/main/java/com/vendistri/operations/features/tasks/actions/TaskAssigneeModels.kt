package com.vendistri.operations.features.tasks.actions

import org.json.JSONArray
import org.json.JSONObject

data class TaskAssignee(
    val id: String,
    val email: String,
    val firstName: String?,
    val lastName: String?
) {
    val label: String
        get() = listOfNotNull(firstName, lastName).joinToString(" ").ifBlank { email }

    val displayLabel: String
        get() = if (email.isBlank() || label == email) label else "$label ($email)"

    companion object {
        fun fromJson(json: JSONObject): TaskAssignee {
            return TaskAssignee(
                id = json.getString("id"),
                email = json.optString("email"),
                firstName = json.optString("first_name", json.optString("firstName")).ifBlank { null },
                lastName = json.optString("last_name", json.optString("lastName")).ifBlank { null }
            )
        }

        fun listFromJson(rawJson: String): List<TaskAssignee> {
            val array = JSONArray(rawJson)
            return List(array.length()) { index -> fromJson(array.getJSONObject(index)) }
                .sortedBy { it.label.lowercase() }
        }
    }
}
