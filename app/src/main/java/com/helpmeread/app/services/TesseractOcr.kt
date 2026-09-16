package com.helpmeread.app.services

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.googlecode.tesseract.android.TessBaseAPI
import com.helpmeread.app.data.TextBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL

/**
 * Tesseract-based OCR fallback for scripts ML Kit cannot read —
 * primarily Arabic (also covers Urdu/Persian which share the script).
 *
 * ML Kit Text Recognition v2 supports Latin, Chinese, Devanagari, Japanese
 * and Korean only. When every ML Kit recognizer returns empty, this engine
 * is tried with the "ara+eng" model pair.
 *
 * Traineddata files are downloaded once at runtime (~6MB total from
 * tessdata_fast) and cached in filesDir — keeps the APK small.
 */
class TesseractOcr(private val context: Context) {

    private val tessDir = File(context.filesDir, "tesseract")
    private val tessdataDir = File(tessDir, "tessdata")

    @Volatile
    private var modelsReady: Boolean? = null

    private fun modelPresent(name: String): Boolean {
        val f = File(tessdataDir, "$name.traineddata")
        return f.exists() && f.length() > 100_000
    }

    private fun downloadModel(name: String): Boolean {
        tessdataDir.mkdirs()
        val out = File(tessdataDir, "$name.traineddata")
        return try {
            URL("$TESSDATA_BASE/$name.traineddata").openStream().use { input ->
                FileOutputStream(out).use { input.copyTo(it) }
            }
            Log.d("TesseractOcr", "Downloaded $name.traineddata (${out.length()} bytes)")
            true
        } catch (e: Exception) {
            Log.w("TesseractOcr", "Failed to download $name.traineddata", e)
            out.delete()
            false
        }
    }

    /** Ensure Arabic + English models exist. Downloads once, then cached. */
    suspend fun ensureModels(): Boolean = withContext(Dispatchers.IO) {
        modelsReady?.let { return@withContext it }
        val ok = REQUIRED_MODELS.all { modelPresent(it) || downloadModel(it) }
        modelsReady = ok
        ok
    }

    /** Run Tesseract on a bitmap. Returns text-line-level blocks with boxes. */
    suspend fun recognize(bitmap: Bitmap): List<TextBlock> = withContext(Dispatchers.Default) {
        if (!ensureModels()) return@withContext emptyList()

        val tess = TessBaseAPI()
        try {
            if (!tess.init(tessDir.absolutePath, "ara+eng")) {
                Log.e("TesseractOcr", "Tesseract init failed — models missing or corrupt")
                modelsReady = null // retry next time
                return@withContext emptyList()
            }
            tess.setImage(bitmap)

            val blocks = mutableListOf<TextBlock>()
            val iterator = tess.resultIterator ?: return@withContext emptyList()

            iterator.begin()
            do {
                val level = TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE
                val text = iterator.getUTF8Text(level)?.trim()
                val box = iterator.getBoundingBox(level) // int[4] = left, top, right, bottom
                if (!text.isNullOrEmpty() && box != null && box.size == 4 && box[2] > box[0]) {
                    blocks.add(
                        TextBlock(
                            text = text,
                            boundingBox = android.graphics.Rect(box[0], box[1], box[2], box[3]),
                            confidence = 0.8f
                        )
                    )
                }
            } while (iterator.next(level))

            Log.d("TesseractOcr", "Recognized ${blocks.size} text lines")
            blocks
        } catch (e: Exception) {
            Log.e("TesseractOcr", "Tesseract recognition failed", e)
            emptyList()
        } finally {
            tess.recycle()
        }
    }

    companion object {
        private const val TESSDATA_BASE =
            "https://github.com/tesseract-ocr/tessdata_fast/raw/main"
        private val REQUIRED_MODELS = listOf("ara", "eng")
    }
}
