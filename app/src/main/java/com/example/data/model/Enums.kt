package com.example.data.model

enum class ReadingStatus(val label: String) {
    TO_READ("To-Read"),
    READING("Reading"),
    ON_HOLD("On-Hold"),
    COMPLETED("Completed"),
    ABANDONED("Abandoned");

    companion object {
        fun fromName(name: String): ReadingStatus =
            entries.firstOrNull { it.name == name } ?: TO_READ
    }
}

enum class AppThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark"),
    AMOLED("AMOLED Black");

    companion object {
        fun fromName(name: String): AppThemeMode =
            entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

enum class PdfColorMode(val label: String) {
    NORMAL("Original"),
    DARK_INVERT("Dark Mode"),
    SEPIA("Warm Sepia"),
    HIGH_CONTRAST("High Contrast");

    companion object {
        fun fromName(name: String): PdfColorMode =
            entries.firstOrNull { it.name == name } ?: NORMAL
    }
}

enum class ScrollLayoutMode(val label: String, val shortLabel: String) {
    VERTICAL("Vertical Continuous", "Continuous"),
    HORIZONTAL("Horizontal Scroll", "Horizontal"),
    SINGLE_PAGE("Single Page (Book Flip)", "Single Page"),
    DUAL_PAGE("Dual-Page Book Spread", "Dual Page");

    companion object {
        fun fromName(name: String): ScrollLayoutMode =
            entries.firstOrNull { it.name == name } ?: VERTICAL
    }
}

enum class PageFitPolicy(val label: String) {
    FIT_WIDTH("Fit Width"),
    FIT_HEIGHT("Fit Height"),
    FIT_PAGE("Whole Page");

    companion object {
        fun fromName(name: String): PageFitPolicy =
            entries.firstOrNull { it.name == name } ?: FIT_WIDTH
    }
}

enum class TextModeTheme(val label: String) {
    LIGHT("Paper"),
    SEPIA("Sepia"),
    DARK("Dark"),
    DRACULA("Dracula");

    companion object {
        fun fromName(name: String): TextModeTheme =
            entries.firstOrNull { it.name == name } ?: LIGHT
    }
}

enum class QuoteCardTheme(val label: String) {
    LIGHT("Paper Light"),
    DARK("Obsidian Dark"),
    SEPIA("Warm Sepia"),
    DRACULA("Dracula Purple")
}

enum class TextFontChoice(val label: String) {
    SANS("Sans"),
    SERIF("Serif"),
    MONO("Mono");

    companion object {
        fun fromName(name: String): TextFontChoice =
            entries.firstOrNull { it.name == name } ?: SERIF
    }
}

enum class AnnotationType(val label: String) {
    HIGHLIGHT("Highlight"),
    NOTE("Note"),
    SIGNATURE("Signature");

    companion object {
        fun fromName(name: String): AnnotationType =
            entries.firstOrNull { it.name == name } ?: HIGHLIGHT
    }
}

enum class HighlightColorOption(val label: String, val hexColor: String) {
    YELLOW("Amber Yellow", "#FACC15"),
    GREEN("Mint Green", "#4ADE80"),
    BLUE("Sky Blue", "#38BDF8"),
    PINK("Rose Pink", "#F472B6"),
    ORANGE("Sunset Orange", "#FB923C");

    companion object {
        fun fromHex(hex: String): HighlightColorOption =
            entries.firstOrNull { it.hexColor.equals(hex, ignoreCase = true) } ?: YELLOW
    }
}

enum class LibrarySortOrder(val label: String) {
    RECENTLY_OPENED("Recently Opened"),
    TITLE_ASC("Title (A–Z)"),
    PROGRESS_DESC("Reading Progress"),
    PAGE_COUNT_DESC("Page Count"),
    FILE_SIZE_DESC("File Size")
}

enum class RenderQuality(val label: String, val scaleFactor: Float) {
    STANDARD("Standard (Fast)", 0.95f),
    HIGH("High DPI (Crisp)", 1.25f);

    companion object {
        fun fromName(name: String): RenderQuality =
            entries.firstOrNull { it.name == name } ?: HIGH
    }
}
