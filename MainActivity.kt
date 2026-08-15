package com.example.livetranslator

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import java.util.Locale

/**
 * Live speech -> translation -> speech app.
 *
 * Flow:
 *  1. Android's built-in SpeechRecognizer listens to the mic and converts speech to text.
 *  2. The recognized text is sent to an on-device ML Kit Translator (source -> target language).
 *  3. The translated text is shown on screen and spoken aloud with TextToSpeech.
 *  4. As long as "listening" is on, the recognizer automatically restarts after each
 *     utterance, giving a continuous "live" translation experience.
 */
class MainActivity : AppCompatActivity() {

    data class Language(val code: String, val label: String, val locale: String)

    // Languages shown in the spinners. `code` is an ML Kit TranslateLanguage constant,
    // `locale` is the BCP-47 tag used by SpeechRecognizer / TextToSpeech.
    private val languages = listOf(
        Language(TranslateLanguage.ENGLISH, "English", "en-US"),
        Language(TranslateLanguage.HINDI, "Hindi", "hi-IN"),
        Language(TranslateLanguage.BENGALI, "Bengali", "bn-IN"),
        Language(TranslateLanguage.GUJARATI, "Gujarati", "gu-IN"),
        Language(TranslateLanguage.KANNADA, "Kannada", "kn-IN"),
        Language(TranslateLanguage.MARATHI, "Marathi", "mr-IN"),
        Language(TranslateLanguage.TAMIL, "Tamil", "ta-IN"),
        Language(TranslateLanguage.TELUGU, "Telugu", "te-IN"),
        Language(TranslateLanguage.URDU, "Urdu", "ur-IN"),
        Language(TranslateLanguage.FRENCH, "French", "fr-FR"),
        Language(TranslateLanguage.GERMAN, "German", "de-DE"),
        Language(TranslateLanguage.SPANISH, "Spanish", "es-ES"),
        Language(TranslateLanguage.ARABIC, "Arabic", "ar-SA"),
        Language(TranslateLanguage.JAPANESE, "Japanese", "ja-JP"),
        Language(TranslateLanguage.KOREAN, "Korean", "ko-KR"),
        Language(TranslateLanguage.PORTUGUESE, "Portuguese", "pt-PT"),
        Language(TranslateLanguage.RUSSIAN, "Russian", "ru-RU"),
        Language(TranslateLanguage.CHINESE, "Chinese", "zh-CN")
    )

    private lateinit var spinnerSource: Spinner
    private lateinit var spinnerTarget: Spinner
    private lateinit var btnMic: ImageButton
    private lateinit var btnSwap: ImageButton
    private lateinit var tvStatus: TextView
    private lateinit var tvOriginal: TextView
    private lateinit var tvTranslated: TextView

    private var speechRecognizer: SpeechRecognizer? = null
    private var translator: Translator? = null
    private var tts: TextToSpeech? = null

    private var isListening = false
    private var wantsListening = false // true while the user has the mic toggled on
    private var ttsReady = false

    private val requestMicPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startListening()
            } else {
                Toast.makeText(this, "Microphone permission is required.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        spinnerSource = findViewById(R.id.spinnerSource)
        spinnerTarget = findViewById(R.id.spinnerTarget)
        btnMic = findViewById(R.id.btnMic)
        btnSwap = findViewById(R.id.btnSwap)
        tvStatus = findViewById(R.id.tvStatus)
        tvOriginal = findViewById(R.id.tvOriginal)
        tvTranslated = findViewById(R.id.tvTranslated)

        setupLanguageSpinners()
        setupTranslator()
        setupTextToSpeech()
        setupSpeechRecognizer()

        btnMic.setOnClickListener { toggleListening() }
        btnSwap.setOnClickListener { swapLanguages() }
    }

    // ---------- UI setup ----------

    private fun setupLanguageSpinners() {
        val names = languages.map { it.label }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
        spinnerSource.adapter = adapter
        spinnerTarget.adapter = adapter

        // Sensible defaults: English -> Hindi
        spinnerSource.setSelection(languages.indexOfFirst { it.code == TranslateLanguage.ENGLISH })
        spinnerTarget.setSelection(languages.indexOfFirst { it.code == TranslateLanguage.HINDI })

        val onLangChanged = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long
            ) {
                setupTranslator()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
        spinnerSource.onItemSelectedListener = onLangChanged
        spinnerTarget.onItemSelectedListener = onLangChanged
    }

    private fun swapLanguages() {
        val src = spinnerSource.selectedItemPosition
        val tgt = spinnerTarget.selectedItemPosition
        spinnerSource.setSelection(tgt)
        spinnerTarget.setSelection(src)
    }

    private fun selectedSource() = languages[spinnerSource.selectedItemPosition]
    private fun selectedTarget() = languages[spinnerTarget.selectedItemPosition]

    // ---------- ML Kit Translator ----------

    private fun setupTranslator() {
        translator?.close()
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(selectedSource().code)
            .setTargetLanguage(selectedTarget().code)
            .build()
        translator = Translation.getClient(options)

        // Download the model over any network the first time this language pair is used.
        val conditions = DownloadConditions.Builder().build()
        translator?.downloadModelIfNeeded(conditions)
            ?.addOnSuccessListener {
                Log.d(TAG, "Model ready for ${selectedSource().label} -> ${selectedTarget().label}")
            }
            ?.addOnFailureListener { e ->
                Toast.makeText(this, "Could not download language model: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun translateAndSpeak(text: String) {
        val t = translator ?: return
        t.translate(text)
            .addOnSuccessListener { translated ->
                tvTranslated.text = translated
                speak(translated)
            }
            .addOnFailureListener { e ->
                tvTranslated.text = "(translation failed: ${e.message})"
            }
    }

    // ---------- Text to speech ----------

    private fun setupTextToSpeech() {
        tts = TextToSpeech(this) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
        }
    }

    private fun speak(text: String) {
        if (!ttsReady) return
        val locale = Locale.forLanguageTag(selectedTarget().locale)
        val result = tts?.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "TTS voice for ${locale.displayName} not available on this device")
            return
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    // ---------- Speech recognition ----------

    private fun setupSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Speech recognition isn't available on this device.", Toast.LENGTH_LONG).show()
            return
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    tvStatus.text = "Listening…"
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    tvStatus.text = "Processing…"
                }

                override fun onError(error: Int) {
                    isListening = false
                    // Silence / no-match / timeout are expected in "live" mode; just restart.
                    val recoverable = error == SpeechRecognizer.ERROR_NO_MATCH ||
                        error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                        error == SpeechRecognizer.ERROR_CLIENT
                    if (wantsListening && recoverable) {
                        startListening()
                    } else if (wantsListening) {
                        tvStatus.text = "Error (code $error). Tap mic to retry."
                        wantsListening = false
                        updateMicButton()
                    }
                }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                    if (!text.isNullOrBlank()) {
                        tvOriginal.text = text
                        translateAndSpeak(text)
                    }
                    if (wantsListening) startListening()
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val text = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                    if (!text.isNullOrBlank()) tvOriginal.text = text
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    private fun buildRecognizerIntent() =
        android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, selectedSource().locale)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

    private fun toggleListening() {
        if (wantsListening) {
            wantsListening = false
            speechRecognizer?.stopListening()
            tvStatus.text = "Stopped"
            updateMicButton()
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
                return
            }
            wantsListening = true
            startListening()
            updateMicButton()
        }
    }

    private fun startListening() {
        if (isListening) return
        isListening = true
        speechRecognizer?.startListening(buildRecognizerIntent())
    }

    private fun updateMicButton() {
        btnMic.backgroundTintList = ContextCompat.getColorStateList(
            this, if (wantsListening) R.color.mic_active else R.color.mic_inactive
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        translator?.close()
        tts?.stop()
        tts?.shutdown()
    }

    companion object {
        private const val TAG = "LiveTranslator"
    }
}
