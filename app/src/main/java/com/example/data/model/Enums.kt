package com.example.data.model

import androidx.annotation.StringRes
import com.example.R
import com.example.ui.theme.ContentColors

enum class ReadingStatus(@StringRes val labelRes: Int) {
    TO_READ(R.string.enum_status_to_read),
    READING(R.string.enum_status_reading),
    ON_HOLD(R.string.enum_status_on_hold),
    COMPLETED(R.string.enum_status_completed),
    ABANDONED(R.string.enum_status_abandoned);

    companion object {
        fun fromName(name: String): ReadingStatus =
            entries.firstOrNull { it.name == name } ?: TO_READ
    }
}

enum class AppThemeMode(@StringRes val labelRes: Int) {
    SYSTEM(R.string.theme_system),
    LIGHT(R.string.theme_light),
    DARK(R.string.theme_dark),
    AMOLED(R.string.theme_amoled);

    companion object {
        fun fromName(name: String): AppThemeMode =
            entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

enum class PdfColorMode(@StringRes val labelRes: Int) {
    NORMAL(R.string.pdf_color_normal),
    DARK_INVERT(R.string.pdf_color_dark),
    SEPIA(R.string.pdf_color_sepia),
    HIGH_CONTRAST(R.string.pdf_color_high_contrast);

    companion object {
        fun fromName(name: String): PdfColorMode =
            entries.firstOrNull { it.name == name } ?: NORMAL
    }
}

enum class ScrollLayoutMode(@StringRes val labelRes: Int) {
    VERTICAL(R.string.layout_vertical),
    HORIZONTAL(R.string.layout_horizontal),
    SINGLE_PAGE(R.string.layout_single_page),
    DUAL_PAGE(R.string.layout_dual_page);

    companion object {
        fun fromName(name: String): ScrollLayoutMode =
            entries.firstOrNull { it.name == name } ?: VERTICAL
    }
}

enum class PageFitPolicy(@StringRes val labelRes: Int) {
    FIT_WIDTH(R.string.fit_width),
    FIT_HEIGHT(R.string.fit_height),
    FIT_PAGE(R.string.fit_page);

    companion object {
        fun fromName(name: String): PageFitPolicy =
            entries.firstOrNull { it.name == name } ?: FIT_WIDTH
    }
}

enum class TextModeTheme(@StringRes val labelRes: Int) {
    LIGHT(R.string.text_theme_light),
    SEPIA(R.string.text_theme_sepia),
    DARK(R.string.theme_dark),
    DRACULA(R.string.text_theme_dracula);

    companion object {
        fun fromName(name: String): TextModeTheme =
            entries.firstOrNull { it.name == name } ?: LIGHT
    }
}

enum class QuoteCardTheme(@StringRes val labelRes: Int) {
    LIGHT(R.string.quote_theme_light),
    DARK(R.string.quote_theme_dark),
    SEPIA(R.string.pdf_color_sepia),
    DRACULA(R.string.quote_theme_dracula)
}

enum class TextFontChoice(@StringRes val labelRes: Int) {
    SANS(R.string.font_sans),
    SERIF(R.string.font_serif),
    MONO(R.string.font_mono);

    companion object {
        fun fromName(name: String): TextFontChoice =
            entries.firstOrNull { it.name == name } ?: SERIF
    }
}

enum class AnnotationType(@StringRes val labelRes: Int) {
    HIGHLIGHT(R.string.annotation_highlight),
    NOTE(R.string.annotation_note),
    SIGNATURE(R.string.annotation_signature);

    companion object {
        fun fromName(name: String): AnnotationType =
            entries.firstOrNull { it.name == name } ?: HIGHLIGHT
    }
}

enum class HighlightColorOption(@StringRes val labelRes: Int, val hexColor: String) {
    YELLOW(R.string.highlight_yellow, ContentColors.highlightYellowHex),
    GREEN(R.string.highlight_green, ContentColors.highlightGreenHex),
    BLUE(R.string.highlight_blue, ContentColors.highlightBlueHex),
    PINK(R.string.highlight_pink, ContentColors.highlightPinkHex),
    ORANGE(R.string.highlight_orange, ContentColors.highlightOrangeHex);

    companion object {
        fun fromHex(hex: String): HighlightColorOption =
            entries.firstOrNull { it.hexColor.equals(hex, ignoreCase = true) } ?: YELLOW
    }
}

enum class LibrarySortOrder(@StringRes val labelRes: Int) {
    RECENTLY_OPENED(R.string.library_sort_recent),
    TITLE_ASC(R.string.library_sort_title),
    PROGRESS_DESC(R.string.library_sort_progress),
    PAGE_COUNT_DESC(R.string.library_sort_pages),
    FILE_SIZE_DESC(R.string.library_sort_size)
}

enum class RenderQuality(@StringRes val labelRes: Int, val scaleFactor: Float) {
    STANDARD(R.string.render_quality_standard, 0.95f),
    HIGH(R.string.render_quality_high, 1.25f);

    companion object {
        fun fromName(name: String): RenderQuality =
            entries.firstOrNull { it.name == name } ?: HIGH
    }
}
