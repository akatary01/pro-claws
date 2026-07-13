package com.vendistri.operations.storage

interface RestoreSnapshotStorage {
    suspend fun read(): RestoreSnapshot
    suspend fun write(snapshot: RestoreSnapshot)
    suspend fun clear()
}
