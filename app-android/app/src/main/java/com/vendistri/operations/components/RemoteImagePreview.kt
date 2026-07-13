package com.vendistri.operations.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.vendistri.operations.network.BackendCookieJar
import com.vendistri.operations.network.EncryptedSharedPreferencesBackendCookieStorage
import com.vendistri.operations.network.NetworkConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

private const val MaxRemoteImageBytes = 10 * 1024 * 1024

@Composable
fun RemoteImagePreview(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    placeholder: @Composable () -> Unit
) {
    val context = LocalContext.current
    val cookieHeader = remember(context) {
        BackendCookieJar(EncryptedSharedPreferencesBackendCookieStorage(context.applicationContext)).cookieHeader()
    }
    var image by remember(url) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var didFail by remember(url) { mutableStateOf(false) }

    LaunchedEffect(url, cookieHeader) {
        image = null
        didFail = false
        image = loadRemoteBitmap(url, cookieHeader) ?: run {
            didFail = true
            null
        }
    }

    val bitmap = image
    if (bitmap != null && !didFail) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = modifier
        )
    } else {
        placeholder()
    }
}

private suspend fun loadRemoteBitmap(rawUrl: String?, cookieHeader: String?): android.graphics.Bitmap? = withContext(Dispatchers.IO) {
    val url = normalizedImageUrl(rawUrl) ?: return@withContext null
    runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            useCaches = false
            cookieHeader?.let { setRequestProperty("Cookie", it) }
        }
        try {
            if (connection.responseCode !in 200..299) return@withContext null
            connection.inputStream.use { stream ->
                val bytes = ByteArrayOutputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0
                    while (true) {
                        val read = stream.read(buffer)
                        if (read == -1) break
                        total += read
                        if (total > MaxRemoteImageBytes) return@withContext null
                        output.write(buffer, 0, read)
                    }
                    output.toByteArray()
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}

internal fun normalizedImageUrl(rawUrl: String?): String? {
    val value = rawUrl?.takeIf { it.isNotBlank() } ?: return null
    return when {
        value.startsWith("https://") || value.startsWith("http://") -> rewriteLocalhostForEmulator(value)
        value.startsWith("/") -> backendOrigin() + value
        else -> null
    }
}

private fun rewriteLocalhostForEmulator(value: String): String {
    val uri = runCatching { URI(value) }.getOrNull() ?: return value
    val host = uri.host?.lowercase() ?: return value
    if (host != "localhost" && host != "127.0.0.1") return value
    val replacementOrigin = if (uri.port == 8000) backendOrigin() else appWebOrigin()
    val replacementUri = runCatching { URI(replacementOrigin) }.getOrNull() ?: return value
    return URI(
        replacementUri.scheme,
        replacementUri.userInfo,
        replacementUri.host,
        replacementUri.port,
        uri.path,
        uri.query,
        uri.fragment
    ).toString()
}

private fun backendOrigin(): String {
    val uri = runCatching { URI(NetworkConfig.backendUrl) }.getOrNull() ?: return NetworkConfig.backendUrl
    return URI(uri.scheme, uri.userInfo, uri.host, uri.port, null, null, null).toString().trimEnd('/')
}

private fun appWebOrigin(): String {
    val uri = runCatching { URI(NetworkConfig.appWebUrl) }.getOrNull() ?: return NetworkConfig.appWebUrl
    return URI(uri.scheme, uri.userInfo, uri.host, uri.port, null, null, null).toString().trimEnd('/')
}
