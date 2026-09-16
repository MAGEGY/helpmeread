package com.helpmeread.app.services

import android.annotation.SuppressLint
import android.graphics.Rect
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.helpmeread.app.data.TextBlock
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Real-time OCR analyzer for CameraX ImageAnalysis.
 * Runs ML Kit text recognition on camera frames and reports detected text blocks.
 * Uses an AtomicBoolean to skip frames while a previous analysis is in progress.
 */
class RealtimeOcrAnalyzer(
    private val onTextDetected: (List<TextBlock>, Int, Int) -> Unit
) : ImageAnalysis.Analyzer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val isProcessing = AtomicBoolean(false)

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        if (!isProcessing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            isProcessing.set(false)
            imageProxy.close()
            return
        }

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val inputImage = InputImage.fromMediaImage(
            mediaImage,
            rotationDegrees
        )

        recognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                val blocks = visionText.textBlocks.map { block ->
                    TextBlock(
                        text = block.text,
                        boundingBox = block.boundingBox ?: Rect(),
                        confidence = 1.0f
                    )
                }.filter { it.text.isNotBlank() && it.boundingBox.width() > 0 }

                // Bounding boxes are reported in upright coordinates — swap dims when rotated
                val upright = rotationDegrees == 90 || rotationDegrees == 270
                val reportWidth = if (upright) imageProxy.height else imageProxy.width
                val reportHeight = if (upright) imageProxy.width else imageProxy.height
                onTextDetected(blocks, reportWidth, reportHeight)
            }
            .addOnCompleteListener {
                isProcessing.set(false)
                imageProxy.close()
            }
    }

    fun close() {
        recognizer.close()
    }
}
