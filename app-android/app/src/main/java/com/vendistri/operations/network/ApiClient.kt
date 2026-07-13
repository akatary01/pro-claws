package com.vendistri.operations.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpCookie
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

interface ApiTransport {
    suspend fun request(
        method: HttpMethod,
        path: String,
        body: String? = null,
        headers: Map<String, String> = emptyMap()
    ): ApiResponse

    suspend fun requestMultipart(
        method: HttpMethod,
        path: String,
        file: MultipartFile,
        headers: Map<String, String> = emptyMap()
    ): ApiResponse {
        throw UnsupportedOperationException("Multipart requests are not supported by this transport.")
    }
}

data class MultipartFile(
    val fieldName: String,
    val fileName: String,
    val mimeType: String,
    val data: ByteArray
)

class ApiClient(
    private val baseUrl: String = NetworkConfig.backendUrl,
    private val cookieJar: BackendCookieJar = BackendCookieJar()
) : ApiTransport {
    fun cookieHeader(): String? = cookieJar.cookieHeader()

    fun clearCookies() {
        cookieJar.clear()
    }

    override suspend fun request(
        method: HttpMethod,
        path: String,
        body: String?,
        headers: Map<String, String>
    ): ApiResponse = withContext(Dispatchers.IO) {
        val url = URL(baseUrl + path)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method.rawValue
            useCaches = false
            connectTimeout = 20_000
            readTimeout = 30_000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Cache-Control", "no-cache")
            cookieJar.cookieHeader()?.let { setRequestProperty("Cookie", it) }
            cookieJar.csrfToken()?.let { setRequestProperty("X-CSRFToken", it) }
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
            if (body != null) {
                doOutput = true
                OutputStreamWriter(outputStream, Charsets.UTF_8).use { it.write(body) }
            }
        }

        val statusCode = connection.responseCode
        cookieJar.store(connection.headerFields["Set-Cookie"])
        val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
        val responseBody = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        connection.disconnect()

        if (statusCode !in 200..299) {
            throw NetworkError.HttpStatus(statusCode, responseBody)
        }

        ApiResponse(statusCode = statusCode, body = responseBody)
    }

    override suspend fun requestMultipart(
        method: HttpMethod,
        path: String,
        file: MultipartFile,
        headers: Map<String, String>
    ): ApiResponse = withContext(Dispatchers.IO) {
        val boundary = "Boundary-${java.util.UUID.randomUUID()}"
        val url = URL(baseUrl + path)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method.rawValue
            useCaches = false
            doOutput = true
            connectTimeout = 20_000
            readTimeout = 30_000
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setRequestProperty("Cache-Control", "no-cache")
            cookieJar.cookieHeader()?.let { setRequestProperty("Cookie", it) }
            cookieJar.csrfToken()?.let { setRequestProperty("X-CSRFToken", it) }
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
        }

        connection.outputStream.use { output ->
            output.write("--$boundary\r\n".toByteArray(Charsets.UTF_8))
            output.write(
                "Content-Disposition: form-data; name=\"${file.fieldName}\"; filename=\"${file.fileName}\"\r\n"
                    .toByteArray(Charsets.UTF_8)
            )
            output.write("Content-Type: ${file.mimeType}\r\n\r\n".toByteArray(Charsets.UTF_8))
            output.write(file.data)
            output.write("\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8))
        }

        val statusCode = connection.responseCode
        cookieJar.store(connection.headerFields["Set-Cookie"])
        val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
        val responseBody = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        connection.disconnect()

        if (statusCode !in 200..299) {
            throw NetworkError.HttpStatus(statusCode, responseBody)
        }

        ApiResponse(statusCode = statusCode, body = responseBody)
    }
}

data class ApiResponse(
    val statusCode: Int,
    val body: String
)

class BackendCookieJar(
    private val storage: BackendCookieStorage? = null
) {
    private val cookiesByName = linkedMapOf<String, HttpCookie>()

    init {
        storage?.load().orEmpty().forEach { cookie ->
            if (!cookie.hasExpired()) cookiesByName[cookie.name] = cookie
        }
    }

    fun store(setCookieHeaders: List<String>?) {
        synchronized(this) {
            setCookieHeaders.orEmpty().forEach { header ->
                HttpCookie.parse(header).forEach { cookie ->
                    if (cookie.maxAge == 0L || cookie.hasExpired()) {
                        cookiesByName.remove(cookie.name)
                    } else {
                        cookiesByName[cookie.name] = cookie
                    }
                }
            }
            persist()
        }
    }

    fun cookieHeader(): String? {
        return synchronized(this) {
            removeExpiredCookies()
            if (cookiesByName.isEmpty()) null else cookiesByName.values.joinToString("; ") { "${it.name}=${it.value}" }
        }
    }

    fun csrfToken(): String? {
        return synchronized(this) {
            removeExpiredCookies()
            cookiesByName["csrftoken"]?.value
        }
    }

    fun clear() {
        synchronized(this) {
            cookiesByName.clear()
            storage?.clear()
        }
    }

    private fun removeExpiredCookies() {
        val removedAny = cookiesByName.values.removeAll { it.hasExpired() }
        if (removedAny) persist()
    }

    private fun persist() {
        storage?.save(cookiesByName.values.toList())
    }
}
