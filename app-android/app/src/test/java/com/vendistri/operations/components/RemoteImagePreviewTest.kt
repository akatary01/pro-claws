package com.vendistri.operations.components

import com.vendistri.operations.network.NetworkConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.URI

class RemoteImagePreviewTest {
    @Test
    fun normalizesRelativeMediaUrlsAgainstBackendOrigin() {
        assertEquals(
            "${backendOriginForTest()}/media/task/photo.jpg",
            normalizedImageUrl("/media/task/photo.jpg")
        )
    }

    @Test
    fun rewritesLocalhostWebUrlsForAndroidEmulator() {
        assertEquals(
            "${NetworkConfig.appWebUrl}/media/task/photo.jpg?x=1",
            normalizedImageUrl("http://localhost:3000/media/task/photo.jpg?x=1")
        )
    }

    @Test
    fun rewritesLocalhostBackendUrlsForAndroidEmulator() {
        assertEquals(
            "${backendOriginForTest()}/media/task/photo.jpg?x=1",
            normalizedImageUrl("http://localhost:8000/media/task/photo.jpg?x=1")
        )
    }

    @Test
    fun leavesRemoteUrlsUnchanged() {
        assertEquals(
            "https://cdn.example.com/photo.jpg",
            normalizedImageUrl("https://cdn.example.com/photo.jpg")
        )
    }

    @Test
    fun ignoresBlankUrls() {
        assertNull(normalizedImageUrl(""))
    }

    private fun backendOriginForTest(): String {
        val uri = URI(NetworkConfig.backendUrl)
        return URI(uri.scheme, uri.userInfo, uri.host, uri.port, null, null, null)
            .toString()
            .trimEnd('/')
    }
}
