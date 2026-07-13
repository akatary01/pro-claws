package com.vendistri.operations.storage

import android.content.Context
import com.vendistri.operations.features.map.LocationCoordinate
import com.vendistri.operations.features.work.ActiveWorkSession
import com.vendistri.operations.features.work.LocalActiveExecutionSession
import com.vendistri.operations.features.work.PostPickupDestination
import com.vendistri.operations.features.work.WorkDestinationKind
import com.vendistri.operations.features.work.WorkPhase
import org.json.JSONArray
import org.json.JSONObject

class SharedPreferencesRestoreSnapshotStorage(context: Context) : RestoreSnapshotStorage {
    private val preferences = context.applicationContext.getSharedPreferences(
        "vendistri_restore",
        Context.MODE_PRIVATE
    )

    override suspend fun read(): RestoreSnapshot {
        val rawJson = preferences.getString(Keys.Snapshot, null) ?: return RestoreSnapshot()
        val json = runCatching { JSONObject(rawJson) }.getOrNull() ?: return RestoreSnapshot()
        return RestoreSnapshot(
            schemaVersion = json.optInt("schemaVersion", 0),
            activeWorkSession = json.optJSONObject("activeWorkSession")?.toActiveWorkSession(),
            activeWorkPhase = json.optString("activeWorkPhase").takeIf { it.isNotBlank() }?.let(::workPhaseFromName),
            activeNavigationStopId = json.optString("activeNavigationStopId").takeIf { it.isNotBlank() },
            localActiveExecutionSession = json.optJSONObject("localActiveExecutionSession")?.toLocalActiveExecutionSession(),
            postPickupDestination = json.optJSONObject("postPickupDestination")?.toPostPickupDestination()
        )
    }

    override suspend fun write(snapshot: RestoreSnapshot) {
        preferences.edit()
            .putString(Keys.Snapshot, snapshot.toJson().toString())
            .apply()
    }

    override suspend fun clear() {
        preferences.edit().remove(Keys.Snapshot).apply()
    }

    private fun RestoreSnapshot.toJson(): JSONObject {
        return JSONObject().apply {
            put("schemaVersion", schemaVersion)
            activeWorkSession?.let { put("activeWorkSession", it.toJson()) }
            activeWorkPhase?.let { put("activeWorkPhase", it.name) }
            activeNavigationStopId?.let { put("activeNavigationStopId", it) }
            localActiveExecutionSession?.let { put("localActiveExecutionSession", it.toJson()) }
            postPickupDestination?.let { put("postPickupDestination", it.toJson()) }
        }
    }

    private fun ActiveWorkSession.toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("title", title)
            locationId?.let { put("locationId", it) }
            addressStreetLine?.let { put("addressStreetLine", it) }
            addressCityStateZipLine?.let { put("addressCityStateZipLine", it) }
            coordinate?.let {
                put(
                    "coordinate",
                    JSONObject()
                        .put("latitude", it.latitude)
                        .put("longitude", it.longitude)
                )
            }
            put("destinationKind", destinationKind.name)
            put("activeTaskIds", JSONArray(activeTaskIds.toList()))
        }
    }

    private fun JSONObject.toActiveWorkSession(): ActiveWorkSession {
        val taskIdsJson = optJSONArray("activeTaskIds")
        val taskIds = buildSet {
            if (taskIdsJson != null) {
                for (index in 0 until taskIdsJson.length()) {
                    taskIdsJson.optString(index).takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }
        return ActiveWorkSession(
            id = getString("id"),
            title = optString("title").ifBlank { "Work session" },
            locationId = optString("locationId").takeIf { it.isNotBlank() },
            activeTaskIds = taskIds,
            addressStreetLine = optString("addressStreetLine").takeIf { it.isNotBlank() },
            addressCityStateZipLine = optString("addressCityStateZipLine").takeIf { it.isNotBlank() },
            coordinate = optJSONObject("coordinate")?.let {
                LocationCoordinate(
                    latitude = it.optDouble("latitude"),
                    longitude = it.optDouble("longitude")
                )
            },
            destinationKind = optString("destinationKind")
                .takeIf { it.isNotBlank() }
                ?.let { value -> WorkDestinationKind.entries.firstOrNull { it.name == value } }
                ?: WorkDestinationKind.Location
        )
    }

    private fun LocalActiveExecutionSession.toJson(): JSONObject {
        return JSONObject().apply {
            put("deviceId", deviceId)
            put("userId", userId)
            put("stopId", stopId)
            locationId?.let { put("locationId", it) }
            put("taskIds", JSONArray(taskIds.toList()))
            currentTaskId?.let { put("currentTaskId", it) }
            put("phase", phase.name)
            put("startedAtEpochMillis", startedAtEpochMillis)
            put("distanceMiles", distanceMiles)
            put("taskStartDistanceMilesByTaskId", JSONObject().apply {
                taskStartDistanceMilesByTaskId.forEach { (taskId, distanceMiles) ->
                    put(taskId, distanceMiles)
                }
            })
            postPickupDestination?.let { put("postPickupDestination", it.toJson()) }
        }
    }

    private fun JSONObject.toLocalActiveExecutionSession(): LocalActiveExecutionSession? {
        val phase = workPhaseFromName(optString("phase")) ?: return null
        val taskIdsJson = optJSONArray("taskIds")
        val taskIds = buildSet {
            if (taskIdsJson != null) {
                for (index in 0 until taskIdsJson.length()) {
                    taskIdsJson.optString(index).takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }
        return LocalActiveExecutionSession(
            deviceId = optString("deviceId").takeIf { it.isNotBlank() } ?: return null,
            userId = optString("userId").takeIf { it.isNotBlank() } ?: return null,
            stopId = optString("stopId").takeIf { it.isNotBlank() } ?: return null,
            locationId = optString("locationId").takeIf { it.isNotBlank() },
            taskIds = taskIds,
            currentTaskId = optString("currentTaskId").takeIf { it.isNotBlank() },
            phase = phase,
            startedAtEpochMillis = optLong("startedAtEpochMillis", 0L),
            distanceMiles = optDouble("distanceMiles", 0.0).coerceAtLeast(0.0),
            taskStartDistanceMilesByTaskId = optJSONObject("taskStartDistanceMilesByTaskId")
                ?.toDoubleMap()
                .orEmpty(),
            postPickupDestination = optJSONObject("postPickupDestination")?.toPostPickupDestination()
        )
    }

    private fun JSONObject.toDoubleMap(): Map<String, Double> {
        return keys().asSequence()
            .mapNotNull { key -> key.takeIf { it.isNotBlank() }?.let { it to optDouble(it, 0.0).coerceAtLeast(0.0) } }
            .toMap()
    }

    private fun PostPickupDestination.toJson(): JSONObject {
        return JSONObject().apply {
            refillTaskId?.let { put("refillTaskId", it) }
            stopId?.let { put("stopId", it) }
            put("sessionTaskIds", JSONArray(sessionTaskIds.toList()))
        }
    }

    private fun JSONObject.toPostPickupDestination(): PostPickupDestination {
        val taskIdsJson = optJSONArray("sessionTaskIds")
        val taskIds = buildSet {
            if (taskIdsJson != null) {
                for (index in 0 until taskIdsJson.length()) {
                    taskIdsJson.optString(index).takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }
        return PostPickupDestination(
            refillTaskId = optString("refillTaskId").takeIf { it.isNotBlank() },
            stopId = optString("stopId").takeIf { it.isNotBlank() },
            sessionTaskIds = taskIds
        )
    }

    private fun workPhaseFromName(name: String): WorkPhase? {
        if (name == "EnRoute") return WorkPhase.NavigatingToLocation
        return WorkPhase.entries.firstOrNull { it.name == name }
    }

    private object Keys {
        const val Snapshot = "vendistri.restore.snapshot"
    }
}
