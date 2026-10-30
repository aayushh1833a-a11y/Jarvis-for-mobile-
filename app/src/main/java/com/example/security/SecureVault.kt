package com.example.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class JarvisLanguage(val code: String, val label: String, val sttLocale: String, val ttsLocale: String) {
    AUTO("auto", "Follow User Language (Auto)", "en-IN", "en-IN"),
    ENGLISH("en", "English", "en-IN", "en-IN"),
    HINDI("hi", "Hindi (हिन्दी)", "hi-IN", "hi-IN"),
    HINGLISH("hinglish", "Hinglish (Hindi + English)", "en-IN", "en-IN")
}

enum class GeminiModelOption(val modelId: String, val displayName: String, val subtitle: String, val requiresBilling: Boolean) {
    GEMINI_3_8_FLASH("gemini-3.8-flash", "Gemini 3.8 Flash", "Default • Fast Multimodal & Structured Tasks", false),
    GEMINI_3_5_FLASH_LITE("gemini-3.5-flash-lite", "Gemini 3.5 Flash-Lite", "Ultra-low latency everyday responses", false),
    GEMINI_4_ARGON("gemini-4-argon", "Gemini 4 Argon", "Complex reasoning & STEM (Requires Blaze Plan)", true)
}

enum class ElevenLabsVoiceOption(val voiceId: String, val displayName: String, val style: String) {
    ADAM("pNInz6obpgDQGcFmaJgB", "Adam (Deep Cinematic)", "Authoritative Assistant"),
    ANTONI("ErXwobaYiN019PkySvjV", "Antoni (Crisp & Calm)", "Balanced Personal AI"),
    RACHEL("21m00Tcm4TlvDq8ikWAM", "Rachel (Clear Articulate)", "Warm Conversational"),
    JOSH("TxGEqnHWrfWFTfGW9XjX", "Josh (Modern Tech)", "Dynamic & Natural")
}

/**
 * Android Keystore AES-GCM Encrypted Vault for user-configured credentials, OAuth tokens,
 * and MCP server secrets. Never stores Google account passwords.
 */
class SecureVault(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "SecureVault"
        private const val PREFS_NAME = "jarvis_secure_vault"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "jarvis_master_aes_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128

        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_LANGUAGE = "preferred_language"
        private const val KEY_SELECTED_MODEL = "selected_gemini_model"
        private const val KEY_USE_CUSTOM_GEMINI_KEY = "use_custom_gemini_key"
        private const val KEY_CUSTOM_GEMINI_API_KEY = "enc_custom_gemini_api_key"
        private const val KEY_ELEVENLABS_ENABLED = "elevenlabs_enabled"
        private const val KEY_ELEVENLABS_API_KEY = "enc_elevenlabs_api_key"
        private const val KEY_ELEVENLABS_VOICE_ID = "elevenlabs_voice_id"
        private const val KEY_SECONDARY_AI_ENABLED = "secondary_ai_enabled"
        private const val KEY_SECONDARY_AI_NAME = "secondary_ai_name"
        private const val KEY_SECONDARY_AI_KEY = "enc_secondary_ai_key"
        private const val KEY_WAKE_WORD_ENABLED = "wake_word_enabled"
        private const val KEY_SPEECH_RATE = "speech_rate"
        private const val KEY_SPEECH_PITCH = "speech_pitch"
        private const val KEY_AUTO_SPEAK = "auto_speak_responses"
        private const val KEY_MEMORY_ENABLED = "memory_system_enabled"
        private const val KEY_WEB_SEARCH_ENABLED = "web_search_enabled"
        private const val KEY_EXCLUDED_NOTIF_PACKAGES = "excluded_notif_packages"

        // Google OAuth 2.0 Encrypted Token & Scope Keys
        private const val KEY_GOOGLE_CONNECTED_EMAIL = "google_connected_email"
        private const val KEY_GOOGLE_OAUTH_ACCESS_TOKEN = "enc_google_oauth_access_token"
        private const val KEY_GOOGLE_OAUTH_TOKEN_EXPIRY = "google_oauth_token_expiry"
        private const val KEY_GOOGLE_GRANTED_SCOPES = "google_granted_scopes"
        private const val KEY_ENABLED_GOOGLE_SERVICES = "enabled_google_services"

        // MCP Per-Server Encrypted Secret Prefix
        private const val PREFIX_MCP_SECRET = "enc_mcp_secret_"
    }

    private fun getOrCreateSecretKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            val existing = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (existing != null) {
                existing.secretKey
            } else {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    KEYSTORE_PROVIDER
                )
                val spec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGenerator.init(spec)
                keyGenerator.generateKey()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Keystore unavailable in current environment; falling back to local encoding")
            null
        }
    }

    private fun encrypt(plainText: String): String {
        if (plainText.isBlank()) return ""
        val secretKey = getOrCreateSecretKey()
        if (secretKey == null) {
            return "FALLBACK:" + Base64.encodeToString(plainText.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        }
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
            val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val encrypted = Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
            "$iv:$encrypted"
        } catch (e: Exception) {
            Log.e(TAG, "Encryption failed", e)
            ""
        }
    }

    private fun decrypt(stored: String): String {
        if (stored.isBlank()) return ""
        if (stored.startsWith("FALLBACK:")) {
            return try {
                String(Base64.decode(stored.removePrefix("FALLBACK:"), Base64.NO_WRAP), Charsets.UTF_8)
            } catch (e: Exception) {
                ""
            }
        }
        val secretKey = getOrCreateSecretKey() ?: return ""
        return try {
            val parts = stored.split(":")
            if (parts.size != 2) return ""
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val cipherBytes = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Decryption failed", e)
            ""
        }
    }

    var isOnboardingCompleted: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, value).apply()

    var language: JarvisLanguage
        get() {
            val code = prefs.getString(KEY_LANGUAGE, JarvisLanguage.AUTO.code)
            return JarvisLanguage.entries.firstOrNull { it.code == code } ?: JarvisLanguage.AUTO
        }
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value.code).apply()

    var selectedModel: GeminiModelOption
        get() {
            val id = prefs.getString(KEY_SELECTED_MODEL, GeminiModelOption.GEMINI_3_8_FLASH.modelId)
            return GeminiModelOption.entries.firstOrNull { it.modelId == id }
                ?: GeminiModelOption.GEMINI_3_8_FLASH
        }
        set(value) = prefs.edit().putString(KEY_SELECTED_MODEL, value.modelId).apply()

    var useCustomGeminiKey: Boolean
        get() = prefs.getBoolean(KEY_USE_CUSTOM_GEMINI_KEY, false)
        set(value) = prefs.edit().putBoolean(KEY_USE_CUSTOM_GEMINI_KEY, value).apply()

    var customGeminiApiKey: String
        get() = decrypt(prefs.getString(KEY_CUSTOM_GEMINI_API_KEY, "").orEmpty())
        set(value) {
            if (value.isBlank()) {
                prefs.edit().remove(KEY_CUSTOM_GEMINI_API_KEY).apply()
            } else {
                prefs.edit().putString(KEY_CUSTOM_GEMINI_API_KEY, encrypt(value.trim())).apply()
            }
        }

    var isElevenLabsEnabled: Boolean
        get() = prefs.getBoolean(KEY_ELEVENLABS_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ELEVENLABS_ENABLED, value).apply()

    var elevenLabsApiKey: String
        get() = decrypt(prefs.getString(KEY_ELEVENLABS_API_KEY, "").orEmpty())
        set(value) {
            if (value.isBlank()) {
                prefs.edit().remove(KEY_ELEVENLABS_API_KEY).apply()
            } else {
                prefs.edit().putString(KEY_ELEVENLABS_API_KEY, encrypt(value.trim())).apply()
            }
        }

    var elevenLabsVoice: ElevenLabsVoiceOption
        get() {
            val id = prefs.getString(KEY_ELEVENLABS_VOICE_ID, ElevenLabsVoiceOption.ADAM.voiceId)
            return ElevenLabsVoiceOption.entries.firstOrNull { it.voiceId == id }
                ?: ElevenLabsVoiceOption.ADAM
        }
        set(value) = prefs.edit().putString(KEY_ELEVENLABS_VOICE_ID, value.voiceId).apply()

    var isSecondaryAiEnabled: Boolean
        get() = prefs.getBoolean(KEY_SECONDARY_AI_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_SECONDARY_AI_ENABLED, value).apply()

    var secondaryAiName: String
        get() = prefs.getString(KEY_SECONDARY_AI_NAME, "OpenRouter (OpenAI-Compatible)").orEmpty()
        set(value) = prefs.edit().putString(KEY_SECONDARY_AI_NAME, value.trim()).apply()

    var secondaryAiApiKey: String
        get() = decrypt(prefs.getString(KEY_SECONDARY_AI_KEY, "").orEmpty())
        set(value) {
            if (value.isBlank()) {
                prefs.edit().remove(KEY_SECONDARY_AI_KEY).apply()
            } else {
                prefs.edit().putString(KEY_SECONDARY_AI_KEY, encrypt(value.trim())).apply()
            }
        }

    var isWakeWordEnabled: Boolean
        get() = prefs.getBoolean(KEY_WAKE_WORD_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_WAKE_WORD_ENABLED, value).apply()

    var speechRate: Float
        get() = prefs.getFloat(KEY_SPEECH_RATE, 1.0f).coerceIn(0.5f, 2.0f)
        set(value) = prefs.edit().putFloat(KEY_SPEECH_RATE, value.coerceIn(0.5f, 2.0f)).apply()

    var speechPitch: Float
        get() = prefs.getFloat(KEY_SPEECH_PITCH, 1.0f).coerceIn(0.5f, 1.5f)
        set(value) = prefs.edit().putFloat(KEY_SPEECH_PITCH, value.coerceIn(0.5f, 1.5f)).apply()

    var autoSpeakResponses: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SPEAK, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_SPEAK, value).apply()

    var isMemoryEnabled: Boolean
        get() = prefs.getBoolean(KEY_MEMORY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_MEMORY_ENABLED, value).apply()

    var isWebSearchEnabled: Boolean
        get() = prefs.getBoolean(KEY_WEB_SEARCH_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_WEB_SEARCH_ENABLED, value).apply()

    var excludedNotificationPackages: Set<String>
        get() = prefs.getStringSet(KEY_EXCLUDED_NOTIF_PACKAGES, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_EXCLUDED_NOTIF_PACKAGES, value).apply()

    // Google OAuth 2.0 Encrypted Token & Account State
    var googleConnectedEmail: String
        get() = prefs.getString(KEY_GOOGLE_CONNECTED_EMAIL, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_GOOGLE_CONNECTED_EMAIL, value.trim()).apply()

    var googleOAuthAccessToken: String
        get() = decrypt(prefs.getString(KEY_GOOGLE_OAUTH_ACCESS_TOKEN, "").orEmpty())
        set(value) {
            if (value.isBlank()) {
                prefs.edit().remove(KEY_GOOGLE_OAUTH_ACCESS_TOKEN).apply()
            } else {
                prefs.edit().putString(KEY_GOOGLE_OAUTH_ACCESS_TOKEN, encrypt(value.trim())).apply()
            }
        }

    var googleAccessToken: String
        get() = googleOAuthAccessToken
        set(value) { googleOAuthAccessToken = value }

    var googleOAuthTokenExpiryMillis: Long
        get() = prefs.getLong(KEY_GOOGLE_OAUTH_TOKEN_EXPIRY, 0L)
        set(value) = prefs.edit().putLong(KEY_GOOGLE_OAUTH_TOKEN_EXPIRY, value).apply()

    var googleTokenExpiryMillis: Long
        get() = googleOAuthTokenExpiryMillis
        set(value) { googleOAuthTokenExpiryMillis = value }

    var grantedGoogleScopes: Set<String>
        get() = prefs.getStringSet(KEY_GOOGLE_GRANTED_SCOPES, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_GOOGLE_GRANTED_SCOPES, value).apply()

    var googleGrantedScopes: Set<String>
        get() = grantedGoogleScopes
        set(value) { grantedGoogleScopes = value }

    var enabledGoogleServices: Set<String>
        get() = prefs.getStringSet(KEY_ENABLED_GOOGLE_SERVICES, setOf("DRIVE", "GMAIL", "CALENDAR", "CONTACTS", "YOUTUBE", "PHOTOS"))
            ?: setOf("DRIVE", "GMAIL", "CALENDAR", "CONTACTS", "YOUTUBE", "PHOTOS")
        set(value) = prefs.edit().putStringSet(KEY_ENABLED_GOOGLE_SERVICES, value).apply()

    // Per-MCP-Server Encrypted Credentials (Unlimited Dynamic Servers)
    fun setMcpServerSecret(serverId: Long, secret: String) {
        val key = "$PREFIX_MCP_SECRET$serverId"
        if (secret.isBlank()) {
            prefs.edit().remove(key).apply()
        } else {
            prefs.edit().putString(key, encrypt(secret.trim())).apply()
        }
    }

    fun getMcpServerSecret(serverId: Long): String {
        val key = "$PREFIX_MCP_SECRET$serverId"
        return decrypt(prefs.getString(key, "").orEmpty())
    }

    fun removeMcpServerSecret(serverId: Long) {
        prefs.edit().remove("$PREFIX_MCP_SECRET$serverId").apply()
    }

    fun clearGoogleOAuthCredentials() {
        prefs.edit()
            .remove(KEY_GOOGLE_CONNECTED_EMAIL)
            .remove(KEY_GOOGLE_OAUTH_ACCESS_TOKEN)
            .remove(KEY_GOOGLE_OAUTH_TOKEN_EXPIRY)
            .remove(KEY_GOOGLE_GRANTED_SCOPES)
            .apply()
    }

    fun clearAllCredentialsAndPreferences() {
        prefs.edit().clear().commit()
        try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not delete keystore alias", e)
        }
    }
}
