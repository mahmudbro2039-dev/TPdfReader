package com.example.data.ocr

object OcrConfig {
    const val LANGUAGES = "ben+eng"
    const val FALLBACK_LANGUAGES = "eng"
    const val RENDER_MAX_SIDE_PX = 3000
    const val RENDER_MAX_PIXELS = 9_000_000
    const val MIN_CONFIDENCE = 35
    const val PAGE_BACKGROUND = 0xFFFFFFFF.toInt()
    const val DATA_DIR_NAME = "tesseract"
}
