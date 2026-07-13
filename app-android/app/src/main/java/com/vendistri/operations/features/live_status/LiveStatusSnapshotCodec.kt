package com.vendistri.operations.features.live_status

import org.json.JSONObject

internal object LiveStatusSnapshotCodec {
    fun encode(snapshot: LiveStatusSnapshot): String = JSONObject()
        .put("sessionId", snapshot.sessionId)
        .put("stopId", snapshot.stopId)
        .put("mode", snapshot.mode.name)
        .put("title", snapshot.title)
        .put("destination", snapshot.destination)
        .putNullable("address", snapshot.address)
        .put("primaryStatus", snapshot.primaryStatus)
        .putNullable("secondaryStatus", snapshot.secondaryStatus)
        .putNullable("nextInstruction", snapshot.nextInstruction)
        .putNullable("etaText", snapshot.etaText)
        .putNullable("distanceRemainingText", snapshot.distanceRemainingText)
        .putNullable("progressCurrent", snapshot.progressCurrent)
        .putNullable("progressTotal", snapshot.progressTotal)
        .put("isRerouting", snapshot.isRerouting)
        .toString()

    fun decode(value: String?): LiveStatusSnapshot? {
        if (value.isNullOrBlank()) return null
        return runCatching {
            val json = JSONObject(value)
            LiveStatusSnapshot(
                sessionId = json.getString("sessionId"),
                stopId = json.getString("stopId"),
                mode = LiveStatusMode.valueOf(json.getString("mode")),
                title = json.getString("title"),
                destination = json.getString("destination"),
                address = json.optNullableString("address"),
                primaryStatus = json.getString("primaryStatus"),
                secondaryStatus = json.optNullableString("secondaryStatus"),
                nextInstruction = json.optNullableString("nextInstruction"),
                etaText = json.optNullableString("etaText"),
                distanceRemainingText = json.optNullableString("distanceRemainingText"),
                progressCurrent = json.optNullableInt("progressCurrent"),
                progressTotal = json.optNullableInt("progressTotal"),
                isRerouting = json.optBoolean("isRerouting", false)
            )
        }.getOrNull()
    }

    private fun JSONObject.putNullable(key: String, value: Any?): JSONObject = put(key, value ?: JSONObject.NULL)
    private fun JSONObject.optNullableString(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
    private fun JSONObject.optNullableInt(key: String): Int? =
        if (isNull(key) || !has(key)) null else optInt(key)
}
