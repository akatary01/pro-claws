package com.vendistri.operations.features.tasks

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskAssetParsingTest {
    @Test
    fun parsesPhotoConfirmationAssetAliases() {
        val task = VendiTask.fromJson(JSONObject(taskJson(assetType = "confirmation_photo")))

        assertEquals("asset-1", task.photoConfirmationAsset?.id)
        assertEquals(TaskAssetType.PhotoConfirmation, task.photoConfirmationAsset?.type)
        assertEquals("https://cdn.example.com/photo.jpg", task.photoConfirmationAsset?.url)

        val alternate = VendiTask.fromJson(JSONObject(taskJson(assetType = "photo_confirmation")))
        assertEquals(TaskAssetType.PhotoConfirmation, alternate.photoConfirmationAsset?.type)
    }

    @Test
    fun ignoresUnknownAssetTypes() {
        val task = VendiTask.fromJson(JSONObject(taskJson(assetType = "receipt")))

        assertEquals(emptyList<TaskAsset>(), task.assets)
        assertEquals(null, task.photoConfirmationAsset)
    }

    private fun taskJson(assetType: String): String {
        return """
            {
              "id": "task-1",
              "type": "machine_refill",
              "status": "pending",
              "isPublic": false,
              "machine": "machine-1",
              "machineName": "Machine",
              "location": "location-1",
              "locationName": "Location",
              "scheduledFor": "2026-07-06",
              "assets": [
                {
                  "id": "asset-1",
                  "task_id": "task-1",
                  "type": "$assetType",
                  "url": "https://cdn.example.com/photo.jpg",
                  "created_at": "2026-07-06T10:00:00Z",
                  "uploaded_by": "user-1"
                }
              ]
            }
        """.trimIndent()
    }
}
