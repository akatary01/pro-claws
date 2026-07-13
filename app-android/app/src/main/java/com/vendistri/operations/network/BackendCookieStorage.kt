package com.vendistri.operations.network

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpCookie
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface BackendCookieStorage {
    fun load(): List<HttpCookie>
    fun save(cookies: List<HttpCookie>)
    fun clear()
}

class EncryptedSharedPreferencesBackendCookieStorage(
    context: Context
) : BackendCookieStorage {
    private val preferences = context.applicationContext.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    override fun load(): List<HttpCookie> {
        val encryptedPayload = preferences.getString(CookiesKey, null) ?: return emptyList()
        val payload = runCatching { decrypt(encryptedPayload) }.getOrElse {
            clear()
            return emptyList()
        }
        return runCatching {
            val array = JSONArray(payload)
            List(array.length()) { index -> array.getJSONObject(index).toCookie() }
                .filterNot(HttpCookie::hasExpired)
        }.getOrElse {
            clear()
            emptyList()
        }
    }

    override fun save(cookies: List<HttpCookie>) {
        val activeCookies = cookies.filterNot(HttpCookie::hasExpired)
        if (activeCookies.isEmpty()) {
            clear()
            return
        }
        val array = JSONArray()
        activeCookies.forEach { cookie -> array.put(cookie.toJson()) }
        preferences.edit()
            .putString(CookiesKey, encrypt(array.toString()))
            .apply()
    }

    override fun clear() {
        preferences.edit().remove(CookiesKey).apply()
    }

    private fun encrypt(rawValue: String): String {
        val cipher = Cipher.getInstance(CipherTransformation)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(rawValue.toByteArray(Charsets.UTF_8))
        return JSONObject()
            .put("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .put("data", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .toString()
    }

    private fun decrypt(payload: String): String {
        val json = JSONObject(payload)
        val iv = Base64.decode(json.getString("iv"), Base64.NO_WRAP)
        val encrypted = Base64.decode(json.getString("data"), Base64.NO_WRAP)
        val cipher = Cipher.getInstance(CipherTransformation)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GcmTagBits, iv))
        return cipher.doFinal(encrypted).toString(Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(AndroidKeyStore).apply { load(null) }
        (keyStore.getEntry(KeyAlias, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, AndroidKeyStore)
        val spec = KeyGenParameterSpec.Builder(
            KeyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private fun JSONObject.toCookie(): HttpCookie {
        return HttpCookie(getString("name"), getString("value")).apply {
            domain = optString("domain").takeIf { it.isNotBlank() }
            path = optString("path").takeIf { it.isNotBlank() }
            secure = optBoolean("secure", false)
            isHttpOnly = optBoolean("httpOnly", false)
            maxAge = optLong("maxAge", -1)
        }
    }

    private fun HttpCookie.toJson(): JSONObject {
        return JSONObject()
            .put("name", name)
            .put("value", value)
            .put("domain", domain)
            .put("path", path)
            .put("secure", secure)
            .put("httpOnly", isHttpOnly)
            .put("maxAge", maxAge)
    }

    private companion object {
        const val PreferencesName = "vendistri_backend_cookies"
        const val CookiesKey = "cookies"
        const val AndroidKeyStore = "AndroidKeyStore"
        const val KeyAlias = "vendistri_backend_cookie_key"
        const val CipherTransformation = "AES/GCM/NoPadding"
        const val GcmTagBits = 128
    }
}
