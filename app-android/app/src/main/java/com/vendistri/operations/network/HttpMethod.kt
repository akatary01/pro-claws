package com.vendistri.operations.network

enum class HttpMethod {
    Get,
    Post,
    Put,
    Delete;

    val rawValue: String
        get() = name.uppercase()
}
