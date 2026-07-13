package com.vendistri.operations.network

sealed class NetworkError(message: String) : Exception(message) {
    data object InvalidResponse : NetworkError("We could not read the server response. Please try again.")

    class HttpStatus(
        val code: Int,
        val body: String
    ) : NetworkError(userFacingHttpErrorMessage(code, body))
}

fun userFacingHttpErrorMessage(code: Int, body: String): String {
    if (code in 500..599) {
        return "The server had a problem. Please try again in a moment."
    }

    val detail = backendErrorDetail(body)
    if (!detail.isNullOrBlank()) {
        return detail
    }

    return when (code) {
        400 -> "Some information was invalid. Please check it and try again."
        401 -> "Please sign in again."
        403 -> "You do not have permission to do that."
        404 -> "We could not find that item."
        409 -> "This changed somewhere else. Please refresh and try again."
        429 -> "Too many requests. Please wait a moment and try again."
        else -> "The request failed. Please try again."
    }
}

private fun backendErrorDetail(body: String): String? {
    val marker = "\"detail\""
    val markerIndex = body.indexOf(marker)
    if (markerIndex < 0) return null
    val colonIndex = body.indexOf(':', startIndex = markerIndex + marker.length)
    if (colonIndex < 0) return null
    val firstQuote = body.indexOf('"', startIndex = colonIndex + 1)
    if (firstQuote < 0) return null
    val secondQuote = body.indexOf('"', startIndex = firstQuote + 1)
    if (secondQuote < 0) return null
    return body.substring(firstQuote + 1, secondQuote).trim()
}
