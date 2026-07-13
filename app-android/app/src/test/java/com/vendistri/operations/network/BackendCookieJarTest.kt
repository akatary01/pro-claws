package com.vendistri.operations.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.HttpCookie

class BackendCookieJarTest {
    @Test
    fun loadsPersistedCookiesIntoHeaders() {
        val storage = InMemoryCookieStorage(
            listOf(
                HttpCookie("sessionid", "abc"),
                HttpCookie("csrftoken", "csrf")
            )
        )

        val jar = BackendCookieJar(storage)

        assertEquals("sessionid=abc; csrftoken=csrf", jar.cookieHeader())
        assertEquals("csrf", jar.csrfToken())
    }

    @Test
    fun persistsSetCookieHeaders() {
        val storage = InMemoryCookieStorage()
        val jar = BackendCookieJar(storage)

        jar.store(listOf("sessionid=abc; Path=/; HttpOnly", "csrftoken=csrf; Path=/"))

        assertEquals("sessionid=abc; csrftoken=csrf", BackendCookieJar(storage).cookieHeader())
    }

    @Test
    fun removesExpiredSetCookieHeadersFromStorage() {
        val storage = InMemoryCookieStorage(listOf(HttpCookie("sessionid", "abc")))
        val jar = BackendCookieJar(storage)

        jar.store(listOf("sessionid=; Max-Age=0; Path=/"))

        assertNull(jar.cookieHeader())
        assertNull(BackendCookieJar(storage).cookieHeader())
    }

    @Test
    fun clearRemovesPersistedCookies() {
        val storage = InMemoryCookieStorage(listOf(HttpCookie("sessionid", "abc")))
        val jar = BackendCookieJar(storage)

        jar.clear()

        assertNull(jar.cookieHeader())
        assertNull(BackendCookieJar(storage).cookieHeader())
    }

    private class InMemoryCookieStorage(
        initialCookies: List<HttpCookie> = emptyList()
    ) : BackendCookieStorage {
        private var cookies = initialCookies

        override fun load(): List<HttpCookie> = cookies

        override fun save(cookies: List<HttpCookie>) {
            this.cookies = cookies
        }

        override fun clear() {
            cookies = emptyList()
        }
    }
}
