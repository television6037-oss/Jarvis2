package com.jarvis.asistente.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/** Texto a voz en español. `speak` espera hasta que Jarvis termina de hablar. */
class Speaker(context: Context) {
    private val ready = CompletableDeferred<Boolean>()
    private val pending = ConcurrentHashMap<String, () -> Unit>()

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready.complete(status == TextToSpeech.SUCCESS)
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) = Unit
            override fun onDone(utteranceId: String) {
                pending.remove(utteranceId)?.invoke()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String) {
                pending.remove(utteranceId)?.invoke()
            }

            override fun onError(utteranceId: String, errorCode: Int) {
                pending.remove(utteranceId)?.invoke()
            }
        })
    }

    /** Devuelve false si el celular no tiene voz en español instalada. */
    suspend fun speak(text: String): Boolean {
        if (!ready.await()) return false
        val result = tts.setLanguage(Locale("es", "CO"))
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            if (tts.setLanguage(Locale("es")) < TextToSpeech.LANG_AVAILABLE) return false
        }
        tts.setSpeechRate(1.05f)
        val id = UUID.randomUUID().toString()
        suspendCancellableCoroutine { cont ->
            pending[id] = { if (cont.isActive) cont.resume(Unit) }
            cont.invokeOnCancellation {
                pending.remove(id)
                tts.stop()
            }
            if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) == TextToSpeech.ERROR) {
                pending.remove(id)
                cont.resume(Unit)
            }
        }
        return true
    }

    fun stop() = tts.stop()

    fun shutdown() = tts.shutdown()
}
