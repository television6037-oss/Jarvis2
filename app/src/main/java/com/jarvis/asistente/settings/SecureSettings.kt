package com.jarvis.asistente.settings

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.jarvis.asistente.brain.ClaudeBrain
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Ajustes de la app. La clave de la API se cifra con una llave AES guardada
 * en el Android Keystore (hardware seguro del celular): nunca sale del
 * dispositivo ni se guarda en texto plano.
 */
class SecureSettings(context: Context) {
    private val prefs = context.getSharedPreferences("jarvis_settings", Context.MODE_PRIVATE)

    var apiKey: String?
        get() = prefs.getString(KEY_API, null)?.let { decrypt(it) }
        set(value) {
            if (value.isNullOrBlank()) {
                prefs.edit().remove(KEY_API).apply()
            } else {
                prefs.edit().putString(KEY_API, encrypt(value.trim())).apply()
            }
        }

    var model: String
        get() = prefs.getString(KEY_MODEL, ClaudeBrain.MODEL_HAIKU) ?: ClaudeBrain.MODEL_HAIKU
        set(value) = prefs.edit().putString(KEY_MODEL, value).apply()

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val data = Base64.encodeToString(cipher.doFinal(plain.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        return "$iv:$data"
    }

    private fun decrypt(stored: String): String? = try {
        val (iv, data) = stored.split(":", limit = 2)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
        String(cipher.doFinal(Base64.decode(data, Base64.NO_WRAP)), Charsets.UTF_8)
    } catch (e: Exception) {
        null
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "jarvis_api_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_API = "api_key"
        private const val KEY_MODEL = "model"
    }
}
