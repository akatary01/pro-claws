package com.vendistri.operations.features.tasks

import com.vendistri.operations.network.ApiResponse
import com.vendistri.operations.network.ApiTransport
import com.vendistri.operations.network.HttpMethod
import com.vendistri.operations.network.MultipartFile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class TasksStoreTest {
    @Test
    fun scopedLoadsMergeIntoCanonicalTasksAndReplaceOnlyLoadedScope() = runBlocking {
        val transport = QueueTransport(
            listOf(
                tasksJson(taskJson(id = "today", scheduledFor = "2026-07-06")),
                tasksJson(
                    taskJson(id = "last-week-a", scheduledFor = "2026-06-30"),
                    taskJson(id = "last-week-b", scheduledFor = "2026-07-02")
                ),
                tasksJson(taskJson(id = "last-week-b", scheduledFor = "2026-07-02"))
            )
        )
        val store = TasksStore(TasksApi(transport))

        store.loadTasksForDate(java.time.LocalDate.parse("2026-07-06"), force = true)
        assertEquals(listOf("today"), store.state.value.tasks.map { it.id })

        store.loadTasksForWeek(java.time.LocalDate.parse("2026-06-30"), force = true)
        assertEquals(setOf("today", "last-week-a", "last-week-b"), store.state.value.tasks.map { it.id }.toSet())

        store.loadTasksForWeek(java.time.LocalDate.parse("2026-06-30"), force = true)
        assertEquals(setOf("today", "last-week-b"), store.state.value.tasks.map { it.id }.toSet())
    }

    @Test
    fun optimisticMachineStartPreservesStartedAtUntilServerReturnsOne() = runBlocking {
        val optimisticStartedAt = "2026-07-08T12:56:00Z"
        val store = TasksStore(
            TasksApi(
                QueueTransport(
                    listOf(
                        tasksJson(taskJson(id = "task-1", scheduledFor = "2026-07-08")),
                        "{}",
                        taskJson(id = "task-1", scheduledFor = "2026-07-08")
                    )
                )
            )
        )
        store.loadTasksForDate(java.time.LocalDate.parse("2026-07-08"), force = true)
        val task = store.state.value.tasksById.getValue("task-1")

        store.markMachineTaskStartedOptimistically(task, Instant.parse(optimisticStartedAt))

        assertEquals(optimisticStartedAt, store.state.value.tasksById["task-1"]?.startedAt)

        store.startMachineTask(
            task = task,
            coordinate = com.vendistri.operations.features.map.LocationCoordinate(40.7, -74.0),
            startedAt = Instant.parse(optimisticStartedAt)
        )

        assertEquals(optimisticStartedAt, store.state.value.tasksById["task-1"]?.startedAt)
    }

    @Test
    fun scopedLoadsPreservePositiveTaskMetricsWhenServerOmitsThem() = runBlocking {
        val store = TasksStore(
            TasksApi(
                QueueTransport(
                    listOf(
                        tasksJson(taskJson(id = "task-1", scheduledFor = "2026-07-08", duration = 342.0, distance = 0.3)),
                        tasksJson(taskJson(id = "task-1", scheduledFor = "2026-07-08"))
                    )
                )
            )
        )

        store.loadTasksForDate(java.time.LocalDate.parse("2026-07-08"), force = true)
        store.loadTasksForDate(java.time.LocalDate.parse("2026-07-08"), force = true)

        assertEquals(342.0, store.state.value.tasksById["task-1"]?.duration ?: 0.0, 0.0001)
        assertEquals(0.3, store.state.value.tasksById["task-1"]?.distance ?: 0.0, 0.0001)
    }

    @Test
    fun fullLoadsPreservePositiveTaskMetricsWhenServerReturnsZeroes() = runBlocking {
        val store = TasksStore(
            TasksApi(
                QueueTransport(
                    listOf(
                        tasksJson(taskJson(id = "task-1", scheduledFor = "2026-07-08", duration = 342.0, distance = 0.3)),
                        tasksJson(taskJson(id = "task-1", scheduledFor = "2026-07-08", duration = 0.0, distance = 0.0))
                    )
                )
            )
        )

        store.loadTasks(force = true)
        store.loadTasks(force = true)

        assertEquals(342.0, store.state.value.tasksById["task-1"]?.duration ?: 0.0, 0.0001)
        assertEquals(0.3, store.state.value.tasksById["task-1"]?.distance ?: 0.0, 0.0001)
    }

    @Test
    fun missingScopedLoadQueuesBehindActiveLoad() = runBlocking {
        val transport = BlockingFirstRequestTransport(
            listOf(
                tasksJson(taskJson(id = "today", scheduledFor = "2026-07-06")),
                tasksJson(taskJson(id = "previous-week", scheduledFor = "2026-06-30"))
            )
        )
        val store = TasksStore(TasksApi(transport))

        val initialLoad = async {
            store.loadTasksForDate(java.time.LocalDate.parse("2026-07-06"), force = true)
        }
        transport.firstRequestStarted.await()

        store.loadTasksForWeek(java.time.LocalDate.parse("2026-06-30"), force = false)
        transport.releaseFirstRequest.complete(Unit)
        initialLoad.await()

        assertEquals(setOf("today", "previous-week"), store.state.value.tasks.map { it.id }.toSet())
        assertEquals(2, transport.requestCount)
    }

    private class QueueTransport(responses: List<String>) : ApiTransport {
        private val queuedResponses = ArrayDeque(responses)

        override suspend fun request(
            method: HttpMethod,
            path: String,
            body: String?,
            headers: Map<String, String>
        ): ApiResponse {
            return ApiResponse(statusCode = 200, body = queuedResponses.removeFirst())
        }

        override suspend fun requestMultipart(
            method: HttpMethod,
            path: String,
            file: MultipartFile,
            headers: Map<String, String>
        ): ApiResponse {
            return ApiResponse(statusCode = 200, body = "{}")
        }
    }

    private class BlockingFirstRequestTransport(responses: List<String>) : ApiTransport {
        private val queuedResponses = ArrayDeque(responses)
        val firstRequestStarted = CompletableDeferred<Unit>()
        val releaseFirstRequest = CompletableDeferred<Unit>()
        var requestCount: Int = 0
            private set

        override suspend fun request(
            method: HttpMethod,
            path: String,
            body: String?,
            headers: Map<String, String>
        ): ApiResponse {
            requestCount += 1
            if (requestCount == 1) {
                firstRequestStarted.complete(Unit)
                releaseFirstRequest.await()
            }
            return ApiResponse(statusCode = 200, body = queuedResponses.removeFirst())
        }

        override suspend fun requestMultipart(
            method: HttpMethod,
            path: String,
            file: MultipartFile,
            headers: Map<String, String>
        ): ApiResponse {
            return ApiResponse(statusCode = 200, body = "{}")
        }
    }

    private fun tasksJson(vararg tasks: String): String = "[${tasks.joinToString(",")}]"

    private fun taskJson(
        id: String,
        scheduledFor: String,
        duration: Double? = null,
        distance: Double? = null
    ): String {
        val metrics = listOfNotNull(
            duration?.let { """"duration": $it""" },
            distance?.let { """"distance": $it""" }
        )
            .takeIf { it.isNotEmpty() }
            ?.joinToString(prefix = ",\n              ", separator = ",\n              ")
            .orEmpty()
        return """
            {
              "id": "$id",
              "type": "machine_refill",
              "status": "pending",
              "isPublic": false,
              "scheduledFor": "$scheduledFor"$metrics
            }
        """.trimIndent()
    }
}
