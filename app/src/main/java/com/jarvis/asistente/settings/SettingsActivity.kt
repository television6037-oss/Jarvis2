package com.jarvis.asistente.settings

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.jarvis.asistente.R
import com.jarvis.asistente.brain.ClaudeBrain
import com.jarvis.asistente.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var settings: SecureSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        settings = SecureSettings(this)

        refreshKeyState()
        if (settings.model == ClaudeBrain.MODEL_SONNET) binding.modelSonnet.isChecked = true
        else binding.modelHaiku.isChecked = true

        binding.saveButton.setOnClickListener {
            val typed = binding.apiKeyInput.text?.toString()?.trim().orEmpty()
            if (typed.isNotEmpty()) {
                if (!typed.startsWith("sk-ant-")) {
                    binding.apiKeyLayout.error = getString(R.string.settings_key_format)
                    return@setOnClickListener
                }
                settings.apiKey = typed
            }
            settings.model = if (binding.modelSonnet.isChecked) ClaudeBrain.MODEL_SONNET else ClaudeBrain.MODEL_HAIKU
            binding.apiKeyInput.setText("")
            binding.apiKeyLayout.error = null
            refreshKeyState()
            Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
            finish()
        }

        binding.deleteKeyButton.setOnClickListener {
            settings.apiKey = null
            refreshKeyState()
            Toast.makeText(this, R.string.settings_key_deleted, Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshKeyState() {
        val key = settings.apiKey
        binding.keyState.text = if (key == null) {
            getString(R.string.settings_no_key)
        } else {
            getString(R.string.settings_key_saved, key.takeLast(4))
        }
    }
}
