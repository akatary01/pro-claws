package com.vendistri.operations.features.tasks

import com.vendistri.operations.features.map.LocationCoordinate
import com.vendistri.operations.features.pickup.PickupInventoryCompletionLine
import com.vendistri.operations.features.refill.RefillInventoryCompletionLine
import com.vendistri.operations.network.ApiResponse
import com.vendistri.operations.network.ApiTransport
import com.vendistri.operations.network.HttpMethod
import com.vendistri.operations.network.MultipartFile
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TasksApiTest {
    @Test
    fun startMachineTaskUsesMachineStartEndpoint() = runBlocking {
        val transport = FakeTransport("{}")
        val api = TasksApi(transport)

        api.startMachineTask(
            task = VendiTask.fromJson(JSONObject(taskJson(id = "task 1", type = "machine_collection"))),
            coordinate = LocationCoordinate(latitude = 40.7, longitude = -74.0)
        )

        assertEquals(HttpMethod.Put, transport.lastMethod)
        assertEquals("/task/machine/machine_collection/start?id=task+1&lat=40.7&lng=-74.0", transport.lastPath)
    }

    @Test
    fun updateMachineTaskStatusIncludesDistanceWhenProvided() = runBlocking {
        val transport = FakeTransport("{}")
        val api = TasksApi(transport)

        api.updateMachineTaskStatus(
            task = VendiTask.fromJson(JSONObject(taskJson(type = "machine_repair"))),
            status = TaskStatus.Done,
            distanceMiles = 2.25
        )

        assertEquals(HttpMethod.Put, transport.lastMethod)
        assertEquals("/task/machine/machine_repair/status/update?id=task-1&status=done&distance=2.25", transport.lastPath)
    }

    @Test
    fun completeRefillTaskWithInventoryBuildsStructuredJsonBody() = runBlocking {
        val transport = FakeTransport(taskJson())
        val api = TasksApi(transport)

        api.completeRefillTaskWithInventory(
            taskId = "task-1",
            lines = listOf(RefillInventoryCompletionLine(itemId = "item-1", refilledQuantity = 4, finalStock = 8)),
            distanceMiles = null
        )

        val body = JSONObject(requireNotNull(transport.lastBody))
        assertTrue(body.isNull("distance"))
        val item = body.getJSONArray("items").getJSONObject(0)
        assertEquals("item-1", item.getString("itemId"))
        assertEquals(4, item.getInt("refilledQuantity"))
        assertEquals(8, item.getInt("finalStock"))
    }

    @Test
    fun completePickupInventoryTaskBuildsStructuredJsonBody() = runBlocking {
        val transport = FakeTransport(taskJson(type = "machine_pickup_inventory"))
        val api = TasksApi(transport)

        api.completePickupInventoryTask(
            taskId = "task-1",
            lines = listOf(PickupInventoryCompletionLine(lineId = "line-1", pickedUpQuantity = 2)),
            distanceMiles = 1.5
        )

        val body = JSONObject(requireNotNull(transport.lastBody))
        assertEquals(1.5, body.getDouble("distance"), 0.0001)
        val item = body.getJSONArray("items").getJSONObject(0)
        assertEquals("line-1", item.getString("lineId"))
        assertEquals(2, item.getInt("pickedUpQuantity"))
    }

    @Test
    fun bulkAssignUsesJsonNullForUnassigned() = runBlocking {
        val transport = FakeTransport("[${taskJson()}]")
        val api = TasksApi(transport)

        api.bulkAssign(listOf("task-1"), assigneeId = null)

        val body = JSONObject(requireNotNull(transport.lastBody))
        assertEquals("task-1", body.getJSONArray("ids").getString(0))
        assertTrue(body.isNull("assignee"))
    }

    @Test
    fun bulkCreateBuildsTaskCreatePayload() = runBlocking {
        val transport = FakeTransport("[${taskJson(type = "machine_service")}]")
        val api = TasksApi(transport)

        api.bulkCreate(
            listOf(
                TaskCreateRequest(
                    type = TaskType.MachineService,
                    machineId = "machine-1",
                    scheduledFor = LocalDate.of(2026, 7, 3),
                    assigneeId = "operator-1",
                    notes = "Front entrance",
                    childTaskTypes = listOf(TaskType.MachineCollection, TaskType.MachineRefill)
                )
            )
        )

        val item = JSONObject(requireNotNull(transport.lastBody))
            .getJSONArray("items")
            .getJSONObject(0)
        assertEquals("machine_service", item.getString("type"))
        assertEquals("pending", item.getString("status"))
        assertEquals("machine-1", item.getString("machine"))
        assertEquals("operator-1", item.getString("assignee"))
        assertEquals("Front entrance", item.getString("notes"))
        assertEquals("2026-07-03", item.getString("scheduledFor"))
        assertEquals("machine_collection", item.getJSONArray("childTaskTypes").getString(0))
        assertEquals("machine_refill", item.getJSONArray("childTaskTypes").getString(1))
    }

    @Test
    fun bulkDeleteUsesStructuredIdsBody() = runBlocking {
        val transport = FakeTransport("{}")
        val api = TasksApi(transport)

        api.bulkDelete(listOf("task-1", "task-2"))

        val body = JSONObject(requireNotNull(transport.lastBody))
        assertEquals("task-1", body.getJSONArray("ids").getString(0))
        assertEquals("task-2", body.getJSONArray("ids").getString(1))
        assertEquals(HttpMethod.Delete, transport.lastMethod)
        assertEquals("/task/bulk/delete", transport.lastPath)
    }

    @Test
    fun uploadTaskPhotoConfirmationUsesMultipartFileEndpoint() = runBlocking {
        val transport = FakeTransport("{}")
        val api = TasksApi(transport)

        api.uploadTaskPhotoConfirmation(
            taskId = "task 1",
            fileName = "photo.jpg",
            mimeType = "image/jpeg",
            fileData = byteArrayOf(1, 2, 3)
        )

        assertEquals(HttpMethod.Post, transport.lastMultipartMethod)
        assertEquals("/task/asset/confirmation_photo/add?task_id=task+1", transport.lastMultipartPath)
        assertEquals("file", transport.lastMultipartFile?.fieldName)
        assertEquals("photo.jpg", transport.lastMultipartFile?.fileName)
        assertEquals("image/jpeg", transport.lastMultipartFile?.mimeType)
        assertEquals(listOf<Byte>(1, 2, 3), transport.lastMultipartFile?.data?.toList())
    }

    @Test
    fun removeTaskPhotoConfirmationUsesAssetDeleteEndpoint() = runBlocking {
        val transport = FakeTransport("{}")
        val api = TasksApi(transport)

        api.removeTaskPhotoConfirmation("asset 1")

        assertEquals(HttpMethod.Delete, transport.lastMethod)
        assertEquals("/task/asset/delete?id=asset+1", transport.lastPath)
    }

    @Test
    fun taskParserAcceptsSnakeCaseInventoryAndWarehouseAliases() {
        val task = VendiTask.fromJson(
            JSONObject(
                """
                    {
                      "id": "task-1",
                      "type": "machine_pickup_inventory",
                      "status": "pending",
                      "is_public": false,
                      "scheduled_for": "2026-07-03",
                      "inventory_source_mode": "warehouse",
                      "inventory_source_warehouse_id": "warehouse-1",
                      "inventory_source_warehouse_name": "Main Warehouse",
                      "warehouse_id": "warehouse-1",
                      "warehouse_name": "Main Warehouse",
                      "warehouse_address": {
                        "street": "10 Stock St",
                        "city": "Brooklyn",
                        "state": "NY",
                        "zip": "11228"
                      },
                      "inventory_completion": {
                        "items": [
                          {
                            "item_id": "item-1",
                            "product": { "id": "product-1", "name": "Chips" },
                            "stock_before": 2,
                            "refilled_quantity": 4,
                            "final_stock": 6,
                            "movement_id": "movement-1"
                          }
                        ]
                      }
                    }
                """.trimIndent()
            )
        )

        assertEquals(RefillInventorySourceMode.Warehouse, task.inventorySourceMode)
        assertEquals("warehouse-1", task.inventorySourceWarehouseId)
        assertEquals("Main Warehouse", task.inventorySourceWarehouseName)
        assertEquals("warehouse-1", task.warehouseId)
        assertEquals("Main Warehouse", task.warehouseName)
        assertEquals("11228", task.warehouseAddress?.zipCode)
        val line = requireNotNull(task.inventoryCompletion).items.single()
        assertEquals("item-1", line.itemId)
        assertEquals(2, line.stockBefore)
        assertEquals(4, line.refilledQuantity)
        assertEquals(6, line.finalStock)
        assertEquals("movement-1", line.movementId)
    }

    private class FakeTransport(private val responseBody: String) : ApiTransport {
        var lastMethod: HttpMethod? = null
        var lastPath: String? = null
        var lastBody: String? = null
        var lastMultipartMethod: HttpMethod? = null
        var lastMultipartPath: String? = null
        var lastMultipartFile: MultipartFile? = null

        override suspend fun request(
            method: HttpMethod,
            path: String,
            body: String?,
            headers: Map<String, String>
        ): ApiResponse {
            lastMethod = method
            lastPath = path
            lastBody = body
            return ApiResponse(statusCode = 200, body = responseBody)
        }

        override suspend fun requestMultipart(
            method: HttpMethod,
            path: String,
            file: MultipartFile,
            headers: Map<String, String>
        ): ApiResponse {
            lastMultipartMethod = method
            lastMultipartPath = path
            lastMultipartFile = file
            return ApiResponse(statusCode = 200, body = responseBody)
        }
    }

    private fun taskJson(id: String = "task-1", type: String = "machine_refill"): String {
        return """
            {
              "id": "$id",
              "type": "$type",
              "status": "pending",
              "isPublic": false,
              "scheduledFor": "2026-07-03"
            }
        """.trimIndent()
    }
}
