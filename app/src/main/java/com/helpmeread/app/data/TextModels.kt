package com.helpmeread.app.data

import android.graphics.Rect

data class RecognizedText(
    val blocks: List<TextBlock>,
    val imagePath: String,
    val imageWidth: Int,
    val imageHeight: Int,
    val sourceLanguage: String = ""
)

data class TextBlock(
    val text: String,
    val boundingBox: Rect,
    val lines: List<TextLine> = emptyList(),
    val confidence: Float = 1.0f
)

data class TextLine(
    val text: String,
    val boundingBox: Rect,
    val elements: List<TextElement> = emptyList()
)

data class TextElement(
    val text: String,
    val boundingBox: Rect,
    val confidence: Float = 1.0f
)
