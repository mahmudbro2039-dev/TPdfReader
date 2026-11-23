package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.AppThemeMode
import com.example.data.model.PageFitPolicy
import com.example.data.model.PdfColorMode
import com.example.data.model.RenderQuality
import com.example.data.model.ScrollLayoutMode
import com.example.data.model.TextFontChoice
import com.example.data.model.TextModeTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.mjPdfDataStore: DataStore<Preferences> by preferencesDataStore(name = "mj_pdf_preferences")

data class UserPreferences(
    val appThemeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val defaultPdfColorMode: PdfColorMode = PdfColorMode.NORMAL,
    val defaultLayoutMode: ScrollLayoutMode = ScrollLayoutMode.VERTICAL,
    val defaultPageFitPolicy: PageFitPolicy = PageFitPolicy.FIT_WIDTH,
    val hideMarginsByDefault: Boolean = false,
    val incognitoMode: Boolean = false,
    val rtlReading: Boolean = false,
    val keepScreenOn: Boolean = true,
    val volumeKeyNavigation: Boolean = true,
    val showPageIndicatorPill: Boolean = true,
    val showEdgeShortcutButtons: Boolean = true,
    val pageGapSpacing: Boolean = true,
    val renderQuality: RenderQuality = RenderQuality.HIGH,
    val isLibraryGridView: Boolean = true,
    val textModeTheme: TextModeTheme = TextModeTheme.LIGHT,
    val textModeFontSizeSp: Float = 17f,
    val textModeLineHeightMultiplier: Float = 1.6f,
    val textModeFontChoice: TextFontChoice = TextFontChoice.SERIF,
    val autoScrollSpeed: Int = 3,
    val savedSignatureVector: String = "",
    val deepSearch: Boolean = false,
    val sortOrderName: String = "LastOpened",
    val startupFilePromptShown: Boolean = false,
    val startupBatteryPromptShown: Boolean = false
)

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val APP_THEME = stringPreferencesKey("app_theme")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val DEFAULT_PDF_COLOR = stringPreferencesKey("default_pdf_color")
        val DEFAULT_LAYOUT = stringPreferencesKey("default_layout")
        val DEFAULT_FIT_POLICY = stringPreferencesKey("default_fit_policy")
        val HIDE_MARGINS_DEFAULT = booleanPreferencesKey("hide_margins_default")
        val INCOGNITO_MODE = booleanPreferencesKey("incognito_mode")
        val RTL_READING = booleanPreferencesKey("rtl_reading")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val VOLUME_KEY_NAV = booleanPreferencesKey("volume_key_nav")
        val SHOW_PAGE_PILL = booleanPreferencesKey("show_page_pill")
        val SHOW_EDGE_SHORTCUTS = booleanPreferencesKey("show_edge_shortcuts")
        val PAGE_GAP_SPACING = booleanPreferencesKey("page_gap_spacing")
        val RENDER_QUALITY = stringPreferencesKey("render_quality")
        val LIBRARY_GRID_VIEW = booleanPreferencesKey("library_grid_view")
        val TEXT_MODE_THEME = stringPreferencesKey("text_mode_theme")
        val TEXT_MODE_FONT_SIZE = floatPreferencesKey("text_mode_font_size")
        val TEXT_MODE_LINE_HEIGHT = floatPreferencesKey("text_mode_line_height")
        val TEXT_MODE_FONT_CHOICE = stringPreferencesKey("text_mode_font_choice")
        val AUTO_SCROLL_SPEED = intPreferencesKey("auto_scroll_speed")
        val SAVED_SIGNATURE_VECTOR = stringPreferencesKey("saved_signature_vector")
        val DEEP_SEARCH = booleanPreferencesKey("deep_search")
        val SORT_ORDER = stringPreferencesKey("sort_order")
        val STARTUP_FILE_PROMPT_SHOWN = booleanPreferencesKey("startup_file_prompt_shown")
        val STARTUP_BATTERY_PROMPT_SHOWN = booleanPreferencesKey("startup_battery_prompt_shown")
    }

    val preferencesFlow: Flow<UserPreferences> = context.mjPdfDataStore.data.map { prefs ->
        UserPreferences(
            appThemeMode = AppThemeMode.fromName(prefs[Keys.APP_THEME] ?: AppThemeMode.SYSTEM.name),
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: false,
            defaultPdfColorMode = PdfColorMode.fromName(prefs[Keys.DEFAULT_PDF_COLOR] ?: PdfColorMode.NORMAL.name),
            defaultLayoutMode = ScrollLayoutMode.fromName(prefs[Keys.DEFAULT_LAYOUT] ?: ScrollLayoutMode.VERTICAL.name),
            defaultPageFitPolicy = PageFitPolicy.fromName(prefs[Keys.DEFAULT_FIT_POLICY] ?: PageFitPolicy.FIT_WIDTH.name),
            hideMarginsByDefault = prefs[Keys.HIDE_MARGINS_DEFAULT] ?: false,
            incognitoMode = prefs[Keys.INCOGNITO_MODE] ?: false,
            rtlReading = prefs[Keys.RTL_READING] ?: false,
            keepScreenOn = prefs[Keys.KEEP_SCREEN_ON] ?: true,
            volumeKeyNavigation = prefs[Keys.VOLUME_KEY_NAV] ?: true,
            showPageIndicatorPill = prefs[Keys.SHOW_PAGE_PILL] ?: true,
            showEdgeShortcutButtons = prefs[Keys.SHOW_EDGE_SHORTCUTS] ?: true,
            pageGapSpacing = prefs[Keys.PAGE_GAP_SPACING] ?: true,
            renderQuality = RenderQuality.fromName(prefs[Keys.RENDER_QUALITY] ?: RenderQuality.HIGH.name),
            isLibraryGridView = prefs[Keys.LIBRARY_GRID_VIEW] ?: true,
            textModeTheme = TextModeTheme.fromName(prefs[Keys.TEXT_MODE_THEME] ?: TextModeTheme.LIGHT.name),
            textModeFontSizeSp = prefs[Keys.TEXT_MODE_FONT_SIZE] ?: 17f,
            textModeLineHeightMultiplier = prefs[Keys.TEXT_MODE_LINE_HEIGHT] ?: 1.6f,
            textModeFontChoice = TextFontChoice.fromName(prefs[Keys.TEXT_MODE_FONT_CHOICE] ?: TextFontChoice.SERIF.name),
            autoScrollSpeed = prefs[Keys.AUTO_SCROLL_SPEED] ?: 3,
            savedSignatureVector = prefs[Keys.SAVED_SIGNATURE_VECTOR] ?: "",
            deepSearch = prefs[Keys.DEEP_SEARCH] ?: false,
            sortOrderName = prefs[Keys.SORT_ORDER] ?: "LastOpened",
            startupFilePromptShown = prefs[Keys.STARTUP_FILE_PROMPT_SHOWN] ?: false,
            startupBatteryPromptShown = prefs[Keys.STARTUP_BATTERY_PROMPT_SHOWN] ?: false
        )
    }

    suspend fun setDeepSearch(enabled: Boolean) {
        context.mjPdfDataStore.edit { it[Keys.DEEP_SEARCH] = enabled }
    }

    suspend fun setSortOrderName(name: String) {
        context.mjPdfDataStore.edit { it[Keys.SORT_ORDER] = name }
    }

    suspend fun setAppThemeMode(mode: AppThemeMode) {
        context.mjPdfDataStore.edit { it[Keys.APP_THEME] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.mjPdfDataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun setDefaultPdfColorMode(mode: PdfColorMode) {
        context.mjPdfDataStore.edit { it[Keys.DEFAULT_PDF_COLOR] = mode.name }
    }

    suspend fun setDefaultLayoutMode(mode: ScrollLayoutMode) {
        context.mjPdfDataStore.edit { it[Keys.DEFAULT_LAYOUT] = mode.name }
    }

    suspend fun setDefaultPageFitPolicy(policy: PageFitPolicy) {
        context.mjPdfDataStore.edit { it[Keys.DEFAULT_FIT_POLICY] = policy.name }
    }

    suspend fun setHideMarginsByDefault(enabled: Boolean) {
        context.mjPdfDataStore.edit { it[Keys.HIDE_MARGINS_DEFAULT] = enabled }
    }

    suspend fun setIncognitoMode(enabled: Boolean) {
        context.mjPdfDataStore.edit { it[Keys.INCOGNITO_MODE] = enabled }
    }

    suspend fun setRtlReading(enabled: Boolean) {
        context.mjPdfDataStore.edit { it[Keys.RTL_READING] = enabled }
    }

    suspend fun setKeepScreenOn(enabled: Boolean) {
        context.mjPdfDataStore.edit { it[Keys.KEEP_SCREEN_ON] = enabled }
    }

    suspend fun setVolumeKeyNavigation(enabled: Boolean) {
        context.mjPdfDataStore.edit { it[Keys.VOLUME_KEY_NAV] = enabled }
    }

    suspend fun setShowPageIndicatorPill(enabled: Boolean) {
        context.mjPdfDataStore.edit { it[Keys.SHOW_PAGE_PILL] = enabled }
    }

    suspend fun setShowEdgeShortcutButtons(enabled: Boolean) {
        context.mjPdfDataStore.edit { it[Keys.SHOW_EDGE_SHORTCUTS] = enabled }
    }

    suspend fun setPageGapSpacing(enabled: Boolean) {
        context.mjPdfDataStore.edit { it[Keys.PAGE_GAP_SPACING] = enabled }
    }

    suspend fun setRenderQuality(quality: RenderQuality) {
        context.mjPdfDataStore.edit { it[Keys.RENDER_QUALITY] = quality.name }
    }

    suspend fun setLibraryGridView(isGrid: Boolean) {
        context.mjPdfDataStore.edit { it[Keys.LIBRARY_GRID_VIEW] = isGrid }
    }

    suspend fun setTextModeTheme(theme: TextModeTheme) {
        context.mjPdfDataStore.edit { it[Keys.TEXT_MODE_THEME] = theme.name }
    }

    suspend fun setTextModeFontSize(sizeSp: Float) {
        context.mjPdfDataStore.edit { it[Keys.TEXT_MODE_FONT_SIZE] = sizeSp.coerceIn(13f, 28f) }
    }

    suspend fun setTextModeLineHeight(multiplier: Float) {
        context.mjPdfDataStore.edit { it[Keys.TEXT_MODE_LINE_HEIGHT] = multiplier.coerceIn(1.2f, 2.2f) }
    }

    suspend fun setTextModeFontChoice(choice: TextFontChoice) {
        context.mjPdfDataStore.edit { it[Keys.TEXT_MODE_FONT_CHOICE] = choice.name }
    }

    suspend fun setAutoScrollSpeed(speed: Int) {
        context.mjPdfDataStore.edit { it[Keys.AUTO_SCROLL_SPEED] = speed.coerceIn(1, 10) }
    }

    suspend fun setSavedSignatureVector(vectorData: String) {
        context.mjPdfDataStore.edit { it[Keys.SAVED_SIGNATURE_VECTOR] = vectorData }
    }

    suspend fun setStartupFilePromptShown(shown: Boolean) {
        context.mjPdfDataStore.edit { it[Keys.STARTUP_FILE_PROMPT_SHOWN] = shown }
    }

    suspend fun setStartupBatteryPromptShown(shown: Boolean) {
        context.mjPdfDataStore.edit { it[Keys.STARTUP_BATTERY_PROMPT_SHOWN] = shown }
    }
}
