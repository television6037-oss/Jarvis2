package com.jarvis.asistente.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Resultado de escuchar una frase. */
sealed interface Heard {
    data class Text(val text: String) : Heard
    data object Nothing : Heard
    data class Error(val message: String) : Heard
}

/**
 * Voz a texto con el reconocedor de Android. Debe usarse desde el hilo
 * principal. `onPartial` recibe el texto mientras uno va hablando.
 */
class Listener(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null

    val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    suspend fun listen(onPartial: (String) -> Unit = {}): Heard = suspendCancellableCoroutine { cont ->
        val sr = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also { recognizer = it }

        fun finish(result: Heard) {
            if (cont.isActive) cont.resume(result)
        }

        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            override fun onPartialResults(partialResults: Bundle) {
                partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.let(onPartial)
            }

            override fun onResults(results: Bundle) {
                val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                finish(if (text.isNullOrBlank()) Heard.Nothing else Heard.Text(text))
            }

            override fun onError(error: Int) {
                finish(
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> Heard.Nothing
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> Heard.Error("Falta el permiso del micrófono.")
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                            Heard.Error("Sin conexión para reconocer la voz.")
                        else -> Heard.Error("No pude escuchar bien (código $error).")
                    }
                )
            }
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-CO")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        cont.invokeOnCancellation { sr.cancel() }
        sr.startListening(intent)
    }

    fun cancel() {
        recognizer?.cancel()
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
    }
}
