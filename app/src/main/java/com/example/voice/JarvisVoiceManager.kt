package com.example.voice

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.security.JarvisLanguage
import com.example.security.SecureVault
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

enum class AssistantVoiceState {
    IDLE,
    WAKE_WORD_ARMED,
    LISTENING_COMMAND,
    GEMINI_LIVE_STREAMING,
    PROCESSING,
    SPEAKING,
    ERROR
}

class JarvisVoiceManager(
    private val context: Context,
    private val secureVault: SecureVault,
    private val onCommandRecognized: (String) -> Unit,
    private val onWakeWordDetected: () -> Unit,
    private val onVoiceNotice: (String) -> Unit
) {
    companion object {
        private const val TAG = "JarvisVoiceManager"
        private val WAKE_PHRASES = listOf(
            "hey jarvis",
            "hi jarvis",
            "ok jarvis",
            "okay jarvis",
            "hello jarvis",
            "jarvis",
            "हे जार्विस",
            "जार्विस"
        )
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsInitialized = false
    private var mediaPlayer: MediaPlayer? = null

    private val _voiceState = MutableStateFlow(AssistantVoiceState.IDLE)
    val voiceState: StateFlow<AssistantVoiceState> = _voiceState.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _rmsLevel = MutableStateFlow(0f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    private val _activeVoiceProviderLabel = MutableStateFlow("Android System TTS")
    val activeVoiceProviderLabel: StateFlow<String> = _activeVoiceProviderLabel.asStateFlow()

    private var isContinuousWakeMode = false
    private var isWaitingForFollowUpCommand = false

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .build()
    }

    init {
        initTextToSpeech()
        refreshVoiceProviderLabel()
    }

    fun refreshVoiceProviderLabel() {
        _activeVoiceProviderLabel.value = if (secureVault.isElevenLabsEnabled && secureVault.elevenLabsApiKey.isNotBlank()) {
            "ElevenLabs (${secureVault.elevenLabsVoice.displayName})"
        } else {
            "Android System TTS"
        }
    }

    private fun initTextToSpeech() {
        try {
            textToSpeech = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    isTtsInitialized = true
                    updateTtsConfig()
                    textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            _voiceState.value = AssistantVoiceState.SPEAKING
                        }

                        override fun onDone(utteranceId: String?) {
                            mainHandler.post {
                                onSpeakingFinished()
                            }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            mainHandler.post {
                                onSpeakingFinished()
                            }
                        }
                    })
                } else {
                    Log.w(TAG, "Android TTS initialization returned status: $status")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Android TTS", e)
        }
    }

    private fun updateTtsConfig(sampleText: String = "") {
        val tts = textToSpeech ?: return
        if (!isTtsInitialized) return
        val hasDevanagari = sampleText.any { it in '\u0900'..'\u097F' }
        val targetLocale = when {
            hasDevanagari || secureVault.language == JarvisLanguage.HINDI -> Locale("hi", "IN")
            else -> Locale("en", "IN")
        }
        val res = tts.setLanguage(targetLocale)
        if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.language = Locale.US
        }
        tts.setSpeechRate(secureVault.speechRate)
        tts.setPitch(secureVault.speechPitch)
    }

    private fun onSpeakingFinished() {
        if (isContinuousWakeMode && secureVault.isWakeWordEnabled) {
            startWakeWordMonitoring()
        } else {
            _voiceState.value = AssistantVoiceState.IDLE
        }
    }

    /**
     * Starts a single Tap-to-Talk voice command listening session.
     */
    fun startSingleCommandListening() {
        mainHandler.post {
            stopSpeaking()
            isWaitingForFollowUpCommand = true
            _partialTranscript.value = "Listening for your command..."
            _voiceState.value = AssistantVoiceState.LISTENING_COMMAND
            startSpeechRecognizerInternal()
        }
    }

    /**
     * Enables or disables "Hey JARVIS" continuous wake-word listening while the app/service is active.
     */
    fun setWakeWordMonitoring(enabled: Boolean) {
        mainHandler.post {
            isContinuousWakeMode = enabled
            if (enabled) {
                startWakeWordMonitoring()
            } else {
                stopAllListening()
            }
        }
    }

    private fun startWakeWordMonitoring() {
        mainHandler.post {
            if (!isContinuousWakeMode) return@post
            isWaitingForFollowUpCommand = false
            _partialTranscript.value = "Say \"Hey JARVIS\" to activate..."
            _voiceState.value = AssistantVoiceState.WAKE_WORD_ARMED
            startSpeechRecognizerInternal()
        }
    }

    fun stopAllListening() {
        mainHandler.post {
            isContinuousWakeMode = false
            isWaitingForFollowUpCommand = false
            _rmsLevel.value = 0f
            _partialTranscript.value = ""
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping SpeechRecognizer", e)
            } finally {
                speechRecognizer = null
            }
            if (_voiceState.value == AssistantVoiceState.LISTENING_COMMAND ||
                _voiceState.value == AssistantVoiceState.WAKE_WORD_ARMED
            ) {
                _voiceState.value = AssistantVoiceState.IDLE
            }
        }
    }

    fun setProcessingState() {
        _voiceState.value = AssistantVoiceState.PROCESSING
    }

    fun setGeminiLiveStreamingState(active: Boolean) {
        _voiceState.value = if (active) {
            AssistantVoiceState.GEMINI_LIVE_STREAMING
        } else {
            AssistantVoiceState.IDLE
        }
    }

    private fun startSpeechRecognizerInternal() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _voiceState.value = AssistantVoiceState.ERROR
            onVoiceNotice("Speech recognition service is not available on this device/emulator. You can use Text Input or Gemini Live Voice.")
            return
        }

        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _rmsLevel.value = 0.15f
                    }

                    override fun onBeginningOfSpeech() {}

                    override fun onRmsChanged(rmsdB: Float) {
                        val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.05f, 1.0f)
                        _rmsLevel.value = normalized
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        _rmsLevel.value = 0f
                    }

                    override fun onError(error: Int) {
                        _rmsLevel.value = 0f
                        // If in continuous wake-word mode, restart listening gently on silence/timeout
                        if (isContinuousWakeMode && !isWaitingForFollowUpCommand) {
                            mainHandler.postDelayed({
                                if (isContinuousWakeMode && _voiceState.value == AssistantVoiceState.WAKE_WORD_ARMED) {
                                    startSpeechRecognizerInternal()
                                }
                            }, 900L)
                            return
                        }

                        val message = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                                "I didn't catch that clearly. Tap the microphone to try again or type your command."
                            SpeechRecognizer.ERROR_AUDIO ->
                                "Microphone audio error. Please check if another app is using the microphone."
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                                "Microphone permission is required for voice commands."
                            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                                "Network error during speech recognition. Check internet or offline speech pack."
                            else -> "Voice recognition stopped (code $error)."
                        }
                        _partialTranscript.value = message
                        _voiceState.value = if (isContinuousWakeMode) AssistantVoiceState.WAKE_WORD_ARMED else AssistantVoiceState.IDLE
                        if (isContinuousWakeMode) {
                            mainHandler.postDelayed({ startWakeWordMonitoring() }, 1200L)
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val best = matches?.firstOrNull()?.trim().orEmpty()
                        handleRecognizedUtterance(best)
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val partial = matches?.firstOrNull()?.trim().orEmpty()
                        if (partial.isNotEmpty()) {
                            _partialTranscript.value = partial
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val recognizerLocale = secureVault.language.sttLocale
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, recognizerLocale)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start SpeechRecognizer", e)
            _voiceState.value = AssistantVoiceState.IDLE
            onVoiceNotice("Could not start microphone listener: ${e.localizedMessage}")
        }
    }

    private fun handleRecognizedUtterance(utterance: String) {
        if (utterance.isBlank()) {
            if (isContinuousWakeMode) startWakeWordMonitoring() else _voiceState.value = AssistantVoiceState.IDLE
            return
        }

        val lower = utterance.lowercase(Locale.getDefault())

        // If we were in Wake-Word Armed state, check if the utterance contains "Hey JARVIS"
        if (!isWaitingForFollowUpCommand && isContinuousWakeMode) {
            val matchedWake = WAKE_PHRASES.firstOrNull { lower.contains(it) }
            if (matchedWake != null) {
                onWakeWordDetected()
                val afterWake = utterance.substringAfter(
                    utterance.substring(lower.indexOf(matchedWake), lower.indexOf(matchedWake) + matchedWake.length),
                    ""
                ).trim(' ', ',', '.', '?', '!')

                if (afterWake.length >= 2) {
                    _partialTranscript.value = afterWake
                    _voiceState.value = AssistantVoiceState.PROCESSING
                    onCommandRecognized(afterWake)
                } else {
                    // User just said "Hey JARVIS" — listen immediately for the command
                    isWaitingForFollowUpCommand = true
                    _partialTranscript.value = "Yes? I'm listening..."
                    _voiceState.value = AssistantVoiceState.LISTENING_COMMAND
                    mainHandler.postDelayed({ startSpeechRecognizerInternal() }, 250L)
                }
            } else {
                // Keep monitoring for wake phrase
                mainHandler.postDelayed({ startWakeWordMonitoring() }, 400L)
            }
            return
        }

        // Standard command recognized
        val cleaned = WAKE_PHRASES.fold(utterance) { acc, wake ->
            if (acc.lowercase(Locale.getDefault()).startsWith(wake)) {
                acc.drop(wake.length).trim(' ', ',', '.', '!')
            } else {
                acc
            }
        }.ifBlank { utterance }

        _partialTranscript.value = cleaned
        _voiceState.value = AssistantVoiceState.PROCESSING
        isWaitingForFollowUpCommand = false
        onCommandRecognized(cleaned)
    }

    /**
     * Speaks the response using ElevenLabs if enabled & configured, or falls back transparently to Android TTS.
     */
    fun speak(text: String) {
        val cleanText = text
            .replace(Regex("""[*_`#~>\[\]]"""), "")
            .trim()
        if (cleanText.isBlank()) {
            onSpeakingFinished()
            return
        }

        refreshVoiceProviderLabel()
        stopSpeaking()

        if (secureVault.isElevenLabsEnabled && secureVault.elevenLabsApiKey.isNotBlank()) {
            scope.launch {
                _voiceState.value = AssistantVoiceState.SPEAKING
                val elevenResult = synthesizeWithElevenLabs(
                    apiKey = secureVault.elevenLabsApiKey,
                    voiceId = secureVault.elevenLabsVoice.voiceId,
                    text = cleanText.take(900)
                )
                if (elevenResult.isFailure) {
                    val reason = elevenResult.exceptionOrNull()?.localizedMessage ?: "ElevenLabs unavailable"
                    onVoiceNotice("ElevenLabs voice synthesis failed ($reason). Using Android System TTS fallback.")
                    _activeVoiceProviderLabel.value = "Fallback: Android System TTS"
                    speakWithAndroidTts(cleanText)
                }
            }
        } else {
            speakWithAndroidTts(cleanText)
        }
    }

    private fun speakWithAndroidTts(cleanText: String) {
        mainHandler.post {
            val tts = textToSpeech
            if (tts != null && isTtsInitialized) {
                updateTtsConfig(cleanText)
                _voiceState.value = AssistantVoiceState.SPEAKING
                val params = Bundle().apply {
                    putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, UUID.randomUUID().toString())
                }
                tts.speak(cleanText.take(1500), TextToSpeech.QUEUE_FLUSH, params, UUID.randomUUID().toString())
            } else {
                onSpeakingFinished()
            }
        }
    }

    fun stopSpeaking() {
        mainHandler.post {
            try {
                textToSpeech?.stop()
            } catch (_: Exception) {}
            try {
                mediaPlayer?.stop()
                mediaPlayer?.release()
            } catch (_: Exception) {}
            mediaPlayer = null
            if (_voiceState.value == AssistantVoiceState.SPEAKING) {
                _voiceState.value = if (isContinuousWakeMode) AssistantVoiceState.WAKE_WORD_ARMED else AssistantVoiceState.IDLE
            }
        }
    }

    private suspend fun synthesizeWithElevenLabs(
        apiKey: String,
        voiceId: String,
        text: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.elevenlabs.io/v1/text-to-speech/$voiceId"
            val payload = JSONObject()
                .put("text", text)
                .put("model_id", "eleven_multilingual_v2")
            val request = Request.Builder()
                .url(url)
                .header("xi-api-key", apiKey.trim())
                .header("Accept", "audio/mpeg")
                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("HTTP ${response.code}")
                    )
                }
                val bytes = response.body?.bytes()
                    ?: return@withContext Result.failure(IllegalStateException("Empty audio stream"))
                val tempFile = File(context.cacheDir, "jarvis_elevenlabs_tts.mp3")
                FileOutputStream(tempFile).use { it.write(bytes) }

                withContext(Dispatchers.Main) {
                    mediaPlayer?.release()
                    mediaPlayer = MediaPlayer().apply {
                        setDataSource(tempFile.absolutePath)
                        setOnCompletionListener {
                            onSpeakingFinished()
                        }
                        setOnErrorListener { _, _, _ ->
                            onSpeakingFinished()
                            true
                        }
                        prepare()
                        start()
                    }
                }
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Log.e(TAG, "ElevenLabs TTS failed", e)
            Result.failure(e)
        }
    }

    /**
     * Verifies an ElevenLabs API key against the official ElevenLabs voices endpoint.
     */
    suspend fun testElevenLabsConnection(candidateKey: String): Result<String> = withContext(Dispatchers.IO) {
        val clean = candidateKey.trim()
        if (clean.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Please enter an ElevenLabs API key."))
        }
        try {
            val request = Request.Builder()
                .url("https://api.elevenlabs.io/v1/voices")
                .header("xi-api-key", clean)
                .get()
                .build()
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("ElevenLabs API returned HTTP ${response.code}. Verify your key.")
                    )
                }
                val voicesArr = JSONObject(body).optJSONArray("voices")
                val count = voicesArr?.length() ?: 0
                Result.success("ElevenLabs API connected ($count voices available).")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun release() {
        stopAllListening()
        stopSpeaking()
        try {
            textToSpeech?.shutdown()
        } catch (_: Exception) {}
    }
}
