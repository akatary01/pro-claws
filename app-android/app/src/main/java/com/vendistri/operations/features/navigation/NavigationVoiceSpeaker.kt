package com.vendistri.operations.features.navigation

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

interface NavigationVoiceSpeaker {
    fun speak(text: String)
    fun shutdown()
}

class AndroidNavigationVoiceSpeaker(context: Context) : NavigationVoiceSpeaker {
    private var isReady = false
    private var pendingText: String? = null
    private var textToSpeech: TextToSpeech? = null

    init {
        textToSpeech = TextToSpeech(context.applicationContext) { status ->
            isReady = status == TextToSpeech.SUCCESS
            if (isReady) {
                textToSpeech?.language = Locale.getDefault()
                pendingText?.let(::speak)
                pendingText = null
            }
        }
    }

    override fun speak(text: String) {
        val cleanText = text.trim()
        if (cleanText.isBlank()) return
        if (!isReady) {
            pendingText = cleanText
            return
        }
        textToSpeech?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, "vendistri-navigation")
    }

    override fun shutdown() {
        pendingText = null
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
    }
}
