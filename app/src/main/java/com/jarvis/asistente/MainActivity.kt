package com.jarvis.asistente

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.jarvis.asistente.brain.AssistantUi
import com.jarvis.asistente.brain.ClaudeBrain
import com.jarvis.asistente.databinding.ActivityMainBinding
import com.jarvis.asistente.settings.SecureSettings
import com.jarvis.asistente.settings.SettingsActivity
import com.jarvis.asistente.tools.ToolRegistry
import com.jarvis.asistente.voice.Heard
import com.jarvis.asistente.voice.Listener
import com.jarvis.asistente.voice.Speaker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Normalizer

class MainActivity : AppCompatActivity(), AssistantUi {
    private lateinit var binding: ActivityMainBinding
    private lateinit var settings: SecureSettings
    private lateinit var speaker: Speaker
    private lateinit var listener: Listener
    private lateinit var brain: ClaudeBrain
    private var job: Job? = null

    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startConversation() else setStatus(getString(R.string.status_need_mic))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settings = SecureSettings(this)
        speaker = Speaker(this)
        listener = Listener(this)
        brain = ClaudeBrain(ToolRegistry.all(this))

        binding.micButton.setOnClickListener { onMicPressed() }
        binding.settingsButton.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        binding.newChatButton.setOnClickListener {
            brain.resetConversation()
            binding.log.text = ""
            setStatus(getString(R.string.status_new_chat))
        }
        setStatus(getString(R.string.status_idle))
    }

    override fun onResume() {
        super.onResume()
        binding.keyWarning.visibility = if (settings.apiKey == null) View.VISIBLE else View.GONE
    }

    override fun onDestroy() {
        job?.cancel()
        listener.destroy()
        speaker.shutdown()
        super.onDestroy()
    }

    private fun onMicPressed() {
        // Si Jarvis está ocupado, el botón sirve para interrumpirlo.
        if (job?.isActive == true) {
            job?.cancel()
            listener.cancel()
            speaker.stop()
            setStatus(getString(R.string.status_idle))
            return
        }
        if (settings.apiKey == null) {
            startActivity(Intent(this, SettingsActivity::class.java))
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        startConversation()
    }

    private fun startConversation() {
        if (!listener.isAvailable) {
            setStatus(getString(R.string.status_no_recognizer))
            return
        }
        job = lifecycleScope.launch {
            binding.micButton.isActivated = true
            try {
                setStatus(getString(R.string.status_listening))
                when (val heard = listener.listen { partial -> setStatus("“$partial”") }) {
                    is Heard.Nothing -> setStatus(getString(R.string.status_heard_nothing))
                    is Heard.Error -> setStatus(heard.message)
                    is Heard.Text -> {
                        appendLog(getString(R.string.log_you, heard.text))
                        setStatus(getString(R.string.status_thinking))
                        val apiKey = settings.apiKey ?: return@launch
                        val answer = brain.ask(heard.text, apiKey, settings.model, this@MainActivity)
                        appendLog(getString(R.string.log_jarvis, answer))
                        setStatus(getString(R.string.status_speaking))
                        say(answer)
                        setStatus(getString(R.string.status_idle))
                    }
                }
            } finally {
                binding.micButton.isActivated = false
            }
        }
    }

    // --- AssistantUi: lo que las herramientas pueden pedirle a la pantalla ---

    override suspend fun say(text: String) {
        withContext(Dispatchers.Main) {
            if (!speaker.speak(text)) setStatus(getString(R.string.status_no_tts))
        }
    }

    override suspend fun confirm(question: String): Boolean = withContext(Dispatchers.Main) {
        appendLog(getString(R.string.log_jarvis, question))
        say(question)
        setStatus(getString(R.string.status_confirm))
        val heard = listener.listen { partial -> setStatus("“$partial”") }
        val answer = (heard as? Heard.Text)?.text.orEmpty()
        if (answer.isNotEmpty()) appendLog(getString(R.string.log_you, answer))
        setStatus(getString(R.string.status_thinking))
        isYes(answer)
    }

    private fun isYes(text: String): Boolean {
        val clean = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
        val words = clean.split(Regex("[^a-z]+")).filter { it.isNotEmpty() }
        if (words.any { it == "no" || it == "cancela" || it == "cancelar" }) return false
        return words.any { it in YES_WORDS }
    }

    private fun setStatus(text: String) {
        binding.status.text = text
    }

    private fun appendLog(line: String) {
        binding.log.append(if (binding.log.text.isEmpty()) line else "\n\n$line")
        binding.logScroll.post { binding.logScroll.fullScroll(View.FOCUS_DOWN) }
    }

    companion object {
        private val YES_WORDS = setOf(
            "si", "dale", "claro", "envia", "enviar", "envialo", "mandalo", "manda", "hazlo", "confirmo",
            "confirmar", "listo", "ok", "okay", "correcto", "adelante", "llama", "llamalo", "exacto", "afirmativo",
        )
    }
}
