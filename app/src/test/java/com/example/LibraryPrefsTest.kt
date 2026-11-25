package com.example

import com.example.data.local.UserPreferences
import com.example.data.local.toLibraryPrefs
import com.example.data.model.AppThemeMode
import com.example.data.model.PageFitPolicy
import com.example.data.model.PdfColorMode
import com.example.data.model.RenderQuality
import com.example.data.model.ScrollLayoutMode
import com.example.data.model.TextFontChoice
import com.example.data.model.TextModeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LibraryPrefsTest {

    @Test
    fun unrelatedPreferenceChangesProduceEqualLibraryPrefs() {
        val base = UserPreferences()
        val editedReaderAndText = base.copy(
            appThemeMode = AppThemeMode.DARK,
            dynamicColor = true,
            defaultPdfColorMode = PdfColorMode.DARK_INVERT,
            defaultLayoutMode = ScrollLayoutMode.HORIZONTAL,
            defaultPageFitPolicy = PageFitPolicy.FIT_PAGE,
            hideMarginsByDefault = true,
            incognitoMode = true,
            rtlReading = true,
            keepScreenOn = false,
            volumeKeyNavigation = false,
            showPageIndicatorPill = false,
            showEdgeShortcutButtons = false,
            pageGapSpacing = false,
            renderQuality = RenderQuality.STANDARD,
            textModeTheme = TextModeTheme.DRACULA,
            textModeFontSizeSp = 24f,
            textModeLineHeightMultiplier = 2.0f,
            textModeFontChoice = TextFontChoice.MONO,
            autoScrollSpeed = 8,
            savedSignatureVector = "M0,0 L10,10"
        )

        assertEquals(base.toLibraryPrefs(), editedReaderAndText.toLibraryPrefs())
    }

    @Test
    fun libraryPreferenceChangesProduceDistinctLibraryPrefs() {
        val base = UserPreferences().toLibraryPrefs()
        assertNotEquals(base, UserPreferences(sortOrderName = "Name").toLibraryPrefs())
        assertNotEquals(base, UserPreferences(isLibraryGridView = false).toLibraryPrefs())
        assertNotEquals(base, UserPreferences(deepSearch = true).toLibraryPrefs())
    }
}
