package com.helpmeread.app.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.helpmeread.app.data.Language
import com.helpmeread.app.data.RecognizedText
import com.helpmeread.app.data.TextBlock
import com.helpmeread.app.data.HistoryManager
import com.helpmeread.app.services.TesseractOcr
import com.helpmeread.app.services.VoiceCommandManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*
import kotlin.coroutines.resume

sealed class AppState {
    object Camera : AppState()
    object Processing : AppState()
    data class Result(val recognizedText: RecognizedText) : AppState()
    data class Error(val message: String) : AppState()
    object History : AppState()
}

class AppViewModel(private val context: Context) : ViewModel() {
    
    val historyManager = HistoryManager(context)
    val voiceCommandManager = VoiceCommandManager(context)
    
    private val _appState = MutableStateFlow<AppState>(AppState.Camera)
    val appState: StateFlow<AppState> = _appState.asStateFlow()
    
    private val _selectedLanguage = MutableStateFlow(Language.default)
    val selectedLanguage: StateFlow<Language> = _selectedLanguage.asStateFlow()
    
    private val _ttsVolume = MutableStateFlow(1.0f)
    val ttsVolume: StateFlow<Float> = _ttsVolume.asStateFlow()
    
    private val _speechRate = MutableStateFlow(1.0f)
    val speechRate: StateFlow<Float> = _speechRate.asStateFlow()
    
    private val _currentlySpeakingIndex = MutableStateFlow<Int?>(null)
    val currentlySpeakingIndex: StateFlow<Int?> = _currentlySpeakingIndex.asStateFlow()
    
    private val _ttsReady = MutableStateFlow(false)
    val ttsReady: StateFlow<Boolean> = _ttsReady.asStateFlow()
    
    private var textToSpeech: TextToSpeech? = null
    private val translators = mutableMapOf<String, Translator>()
    private val languageIdentifier = LanguageIdentification.getClient()
    private var readingJob: Job? = null
    private val translationCache = mutableMapOf<Pair<String, String>, String>()
    private val utteranceCallbacks = mutableMapOf<String, (Boolean) -> Unit>()
    private var scriptRecognizers: Map<String, TextRecognizer>? = null
    private var lastDetectedSourceLanguage: String? = null
    private var currentImageFile: File? = null
    private val tesseractOcr = TesseractOcr(context)
    
    init {
        initTextToSpeech()
    }
    
    private fun initTextToSpeech() {
        try {
            textToSpeech = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    textToSpeech?.let { tts ->
                        val langResult = tts.setLanguage(Locale.forLanguageTag(_selectedLanguage.value.code))
                        if (langResult == TextToSpeech.LANG_MISSING_DATA || 
                            langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                            Log.w("AppViewModel", "Language not supported for TTS: ${_selectedLanguage.value.code}")
                            tts.setLanguage(Locale.ENGLISH)
                        }
                        
                        tts.setSpeechRate(_speechRate.value)
                        
                        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) {
                                Log.d("AppViewModel", "TTS started: $utteranceId")
                            }
                            
                            override fun onDone(utteranceId: String?) {
                                _currentlySpeakingIndex.value = null
                                utteranceId?.let { id ->
                                    utteranceCallbacks.remove(id)?.invoke(true)
                                }
                                Log.d("AppViewModel", "TTS done: $utteranceId")
                            }
                            
                            @Deprecated("Deprecated in Java", ReplaceWith("onError(utteranceId, -1)"))
                            override fun onError(utteranceId: String?) {
                                _currentlySpeakingIndex.value = null
                                utteranceId?.let { id ->
                                    utteranceCallbacks.remove(id)?.invoke(false)
                                }
                                Log.e("AppViewModel", "TTS error: $utteranceId")
                            }
                            
                            override fun onError(utteranceId: String?, errorCode: Int) {
                                _currentlySpeakingIndex.value = null
                                utteranceId?.let { id ->
                                    utteranceCallbacks.remove(id)?.invoke(false)
                                }
                                Log.e("AppViewModel", "TTS error: $utteranceId, code: $errorCode")
                            }
                        })
                        
                        _ttsReady.value = true
                    }
                } else {
                    Log.e("AppViewModel", "TTS initialization failed")
                    _ttsReady.value = false
                }
            }
        } catch (e: Exception) {
            Log.e("AppViewModel", "Error initializing TTS", e)
            _ttsReady.value = false
        }
    }
    
    fun setLanguage(language: Language) {
        _selectedLanguage.value = language
        textToSpeech?.language = Locale.forLanguageTag(language.code)
        translationCache.clear()
    }
    
    fun setVolume(volume: Float) {
        _ttsVolume.value = volume.coerceIn(0f, 1f)
    }
    
    fun setSpeechRate(rate: Float) {
        _speechRate.value = rate.coerceIn(0.5f, 2f)
        textToSpeech?.setSpeechRate(rate)
    }
    
    fun processImage(imageFile: File) {
        viewModelScope.launch {
            _appState.value = AppState.Processing
            
            try {
                // Clean up previous capture so temp files don't accumulate
                currentImageFile?.takeIf { it != imageFile }?.delete()
                currentImageFile = imageFile
                
                val decoded = withContext(Dispatchers.IO) {
                    decodeAndOrient(imageFile)
                }
                
                if (decoded == null) {
                    _appState.value = AppState.Error("Failed to load image")
                    return@launch
                }
                
                val (bitmap, _) = decoded
                val image = InputImage.fromBitmap(bitmap, 0)
                var blocks = recognizeMultiScript(image)
                
                // ML Kit cannot read Arabic script — Tesseract fallback
                if (blocks.isEmpty()) {
                    blocks = tesseractOcr.recognize(bitmap)
                }
                
                bitmap.recycle()
                
                // Detect the language of the recognized text for translation
                val detectedLang = detectSourceLanguage(blocks.joinToString(" ") { it.text })
                lastDetectedSourceLanguage = detectedLang
                
                val recognizedText = RecognizedText(
                    blocks = blocks,
                    imagePath = imageFile.absolutePath,
                    imageWidth = image.width,
                    imageHeight = image.height,
                    sourceLanguage = detectedLang
                )
                
                _appState.value = AppState.Result(recognizedText)
                
                val announcement = if (blocks.isEmpty()) {
                    "No text found"
                } else {
                    "Found ${blocks.size} text ${if (blocks.size == 1) "area" else "areas"}"
                }
                speakText(announcement, "announcement")
                
            } catch (e: Exception) {
                Log.e("AppViewModel", "Error processing image", e)
                _appState.value = AppState.Error("Error: ${e.message ?: "Unknown error"}")
            }
        }
    }
    
    private fun decodeAndOrient(imageFile: File): Pair<Bitmap, Int>? {
        // First pass: bounds only to compute sample size (avoid OOM on large photos)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(imageFile.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        
        val maxDim = 1600
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > maxDim || bounds.outHeight / sampleSize > maxDim) {
            sampleSize *= 2
        }
        
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath, opts) ?: return null
        
        // Apply EXIF rotation so OCR sees upright text
        val rotation = try {
            when (ExifInterface(imageFile.absolutePath)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (e: Exception) { 0 }
        
        if (rotation == 0) return Pair(bitmap, 0)
        
        val matrix = android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) bitmap.recycle()
        return Pair(rotated, rotation)
    }
    
    private fun getScriptRecognizers(): Map<String, TextRecognizer> {
        return scriptRecognizers ?: mapOf(
            "latin" to TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS),
            "chinese" to TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build()),
            "devanagari" to TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build()),
            "japanese" to TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build()),
            "korean" to TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
        ).also { scriptRecognizers = it }
    }
    
    private suspend fun recognizeMultiScript(image: InputImage): List<TextBlock> {
        val recognizers = getScriptRecognizers()
        val latin = recognizers.getValue("latin").process(image).await()
        
        val best = if (latin.textBlocks.isNotEmpty()) {
            // Latin result is good enough — most common case, fast path
            latin
        } else {
            // Nothing found — try other bundled scripts in parallel, keep best
            coroutineScope {
                recognizers
                    .filterKeys { it != "latin" }
                    .map { (_, recognizer) ->
                        async { runCatching { recognizer.process(image).await() }.getOrNull() }
                    }
                    .map { it.await() }
                    .filterNotNull()
                    .filter { it.textBlocks.isNotEmpty() }
                    .maxByOrNull { it.textBlocks.size } ?: latin
            }
        }
        
        return best.textBlocks.map { block ->
            TextBlock(
                text = block.text,
                boundingBox = block.boundingBox ?: android.graphics.Rect(),
                confidence = 1.0f
            )
        }.filter { it.text.isNotBlank() }
    }
    
    private suspend fun detectSourceLanguage(text: String): String {
        if (text.isBlank()) return "und"
        return try {
            languageIdentifier.identifyLanguage(text.take(500)).await()
        } catch (e: Exception) {
            Log.w("AppViewModel", "Language detection failed", e)
            "und"
        }
    }
    
    fun speakTextBlock(block: TextBlock) {
        readingJob?.cancel()
        readingJob = viewModelScope.launch {
            try {
                val textToSpeak = translateIfNeeded(block.text)
                speakText(textToSpeak, "block")
            } catch (e: Exception) {
                Log.e("AppViewModel", "Error speaking text", e)
            }
        }
    }
    
    fun readAllBlocks(blocks: List<TextBlock>, onComplete: () -> Unit) {
        readingJob?.cancel()
        readingJob = viewModelScope.launch {
            try {
                blocks.forEachIndexed { index, block ->
                    if (!isActive) return@forEachIndexed
                    _currentlySpeakingIndex.value = index
                    
                    val textToSpeak = translateIfNeeded(block.text)
                    
                    speakAndWait(textToSpeak, "block_$index")
                    
                    delay(300)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("AppViewModel", "Error reading blocks", e)
            } finally {
                _currentlySpeakingIndex.value = null
                textToSpeech?.stop()
                onComplete()
            }
        }
    }
    
    private suspend fun translateIfNeeded(text: String, sourceLanguage: String? = null): String {
        val targetLanguage = _selectedLanguage.value.code
        if (targetLanguage == "und") return text
        
        // Detect source language — never assume English
        val source = sourceLanguage
            ?: lastDetectedSourceLanguage
            ?: detectSourceLanguage(text)
        if (source == targetLanguage) return text
        
        val cacheKey = Pair(text, targetLanguage)
        translationCache[cacheKey]?.let { return it }
        
        return try {
            val sourceTag = TranslateLanguage.fromLanguageTag(source)
                ?: TranslateLanguage.ENGLISH
            val targetTag = getTranslateLanguageCode(targetLanguage)
            
            val translator = translators.getOrPut(targetTag) {
                Translation.getClient(
                    TranslatorOptions.Builder()
                        .setSourceLanguage(sourceTag)
                        .setTargetLanguage(targetTag)
                        .build()
                )
            }
            translator.downloadModelIfNeeded().await()
            
            val result = translator.translate(text).await()
            
            translationCache[cacheKey] = result
            result
        } catch (e: Exception) {
            Log.e("AppViewModel", "Translation failed: ${e.message}", e)
            text
        }
    }
    
    private fun getTranslateLanguageCode(code: String): String {
        return when (code) {
            "en" -> TranslateLanguage.ENGLISH
            "ar" -> TranslateLanguage.ARABIC
            "fr" -> TranslateLanguage.FRENCH
            "es" -> TranslateLanguage.SPANISH
            "de" -> TranslateLanguage.GERMAN
            "hi" -> TranslateLanguage.HINDI
            "zh" -> TranslateLanguage.CHINESE
            "ja" -> TranslateLanguage.JAPANESE
            "ko" -> TranslateLanguage.KOREAN
            "ru" -> TranslateLanguage.RUSSIAN
            "it" -> TranslateLanguage.ITALIAN
            "pt" -> TranslateLanguage.PORTUGUESE
            "tr" -> TranslateLanguage.TURKISH
            "ur" -> TranslateLanguage.URDU
            "bn" -> TranslateLanguage.BENGALI
            else -> TranslateLanguage.ENGLISH
        }
    }
    
    private fun speakText(text: String, utteranceId: String): Int {
        if (!_ttsReady.value) {
            Log.w("AppViewModel", "TTS not ready")
            return TextToSpeech.ERROR
        }
        
        val tts = textToSpeech ?: return TextToSpeech.ERROR
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val params = android.os.Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, _ttsVolume.value)
            }
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        } else {
            @Suppress("DEPRECATION")
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null)
        }
    }
    
    private suspend fun speakAndWait(text: String, utteranceId: String) {
        if (!_ttsReady.value || textToSpeech == null) return
        
        suspendCancellableCoroutine<Unit> { cont ->
            utteranceCallbacks[utteranceId] = {
                if (cont.isActive) cont.resume(Unit)
            }
            cont.invokeOnCancellation {
                utteranceCallbacks.remove(utteranceId)
            }
            // If speak() fails synchronously, no callback will fire — resume now
            if (speakText(text, utteranceId) == TextToSpeech.ERROR) {
                utteranceCallbacks.remove(utteranceId)
                if (cont.isActive) cont.resume(Unit)
            }
        }
    }
    
    fun stopSpeaking() {
        readingJob?.cancel()
        textToSpeech?.stop()
        _currentlySpeakingIndex.value = null
    }
    
    fun speakTest() {
        val testText = when (_selectedLanguage.value.code) {
            "ar" -> "مرحبا! هذا اختبار الصوت."
            "en" -> "Hello! This is a voice test."
            "fr" -> "Bonjour! C'est un test vocal."
            "es" -> "¡Hola! Esta es una prueba de voz."
            "de" -> "Hallo! Das ist ein Stimmentest."
            "hi" -> "नमस्ते! यह एक आवाज परीक्षण है।"
            "zh" -> "你好！这是一个语音测试。"
            "ja" -> "こんにちは！これは音声テストです。"
            "ko" -> "안녕하세요! 음성 테스트입니다."
            "ru" -> "Привет! Это тест голоса."
            "it" -> "Ciao! Questo è un test vocale."
            "pt" -> "Olá! Este é um teste de voz."
            "tr" -> "Merhaba! Bu bir ses testidir."
            "ur" -> "سلام! یہ آواز کا ٹیسٹ ہے۔"
            "bn" -> "হ্যালো! এটি একটি ভয়েস টেস্ট।"
            else -> "Hello! This is a voice test."
        }
        speakText(testText, "test")
    }
    
    fun resetToCamera() {
        stopSpeaking()
        _appState.value = AppState.Camera
        translationCache.clear()
        currentImageFile?.delete()
        currentImageFile = null
    }
    
    fun showHistory() {
        stopSpeaking()
        _appState.value = AppState.History
    }
    
    fun saveToHistory(recognizedText: RecognizedText) {
        val combinedText = recognizedText.blocks.joinToString("\n") { it.text }
        if (combinedText.isNotBlank()) {
            historyManager.save(
                text = combinedText,
                languageCode = _selectedLanguage.value.code,
                blockCount = recognizedText.blocks.size
            )
        }
    }
    
    fun speakHistoryItem(text: String) {
        readingJob?.cancel()
        readingJob = viewModelScope.launch {
            try {
                val detected = detectSourceLanguage(text)
                val textToSpeak = translateIfNeeded(text, detected)
                speakText(textToSpeak, "history_item")
            } catch (e: Exception) {
                Log.e("AppViewModel", "Error speaking history item", e)
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        stopSpeaking()
        readingJob?.cancel()
        textToSpeech?.shutdown()
        translators.values.forEach { it.close() }
        translators.clear()
        languageIdentifier.close()
        scriptRecognizers?.values?.forEach { it.close() }
        scriptRecognizers = null
        utteranceCallbacks.clear()
        translationCache.clear()
        voiceCommandManager.destroy()
        Log.d("AppViewModel", "ViewModel cleared")
    }
}
