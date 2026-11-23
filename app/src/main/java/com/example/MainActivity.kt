package com.example

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ReaderScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TextModeScreen
import com.example.ui.theme.MjPdfTheme
import com.example.ui.viewmodel.HomeViewModel
import com.example.ui.viewmodel.ReaderViewModel
import com.example.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.flow.MutableStateFlow

private const val ROUTE_HOME = "home"
private const val ROUTE_READER = "reader"
private const val ROUTE_TEXT_MODE = "text_mode"
private const val ROUTE_SETTINGS = "settings"

class MainActivity : ComponentActivity() {

    private lateinit var appContainer: AppContainer
    private val incomingExternalPdfUri = MutableStateFlow<Uri?>(null)
    private var volumeKeyHandler: ((Int) -> Boolean)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appContainer = AppContainer(applicationContext)
        handleIncomingIntent(intent)
        enableEdgeToEdge()

        setContent {
            MjPdfApp(
                appContainer = appContainer,
                incomingPdfUriFlow = incomingExternalPdfUri,
                onConsumeIncomingUri = { incomingExternalPdfUri.value = null },
                onRegisterVolumeHandler = { handler -> volumeKeyHandler = handler },
                onRotateScreen = ::toggleScreenOrientation,
                onSharePdf = ::sharePdf,
                onPrintPdf = ::printPdf
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (volumeKeyHandler?.invoke(keyCode) == true) {
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            intent.data?.let { uri ->
                incomingExternalPdfUri.value = uri
            }
        }
    }

    private fun toggleScreenOrientation() {
        val currentOrientation = resources.configuration.orientation
        requestedOrientation = if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }

    private fun sharePdf(uriString: String, title: String) {
        val shareIntent = appContainer.libraryRepository.buildShareIntent(uriString, title) ?: return
        startActivity(Intent.createChooser(shareIntent, title))
    }

    private fun printPdf(uriString: String, title: String) {
        appContainer.libraryRepository.launchPrintJob(this, uriString, title)
    }
}

@Composable
private fun MjPdfApp(
    appContainer: AppContainer,
    incomingPdfUriFlow: MutableStateFlow<Uri?>,
    onConsumeIncomingUri: () -> Unit,
    onRegisterVolumeHandler: (((Int) -> Boolean)?) -> Unit,
    onRotateScreen: () -> Unit,
    onSharePdf: (String, String) -> Unit,
    onPrintPdf: (String, String) -> Unit
) {
    val homeViewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.factory(
            libraryRepository = appContainer.libraryRepository,
            preferencesRepository = appContainer.preferencesRepository
        )
    )
    val readerViewModel: ReaderViewModel = viewModel(
        factory = ReaderViewModel.factory(
            libraryRepository = appContainer.libraryRepository,
            preferencesRepository = appContainer.preferencesRepository,
            pdfEngine = appContainer.pdfEngine,
            pdfTextExtractor = appContainer.pdfTextExtractor
        )
    )
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(
            preferencesRepository = appContainer.preferencesRepository,
            libraryRepository = appContainer.libraryRepository,
            pdfEngine = appContainer.pdfEngine
        )
    )

    val preferences by settingsViewModel.preferences.collectAsStateWithLifecycle()
    val incomingUri by incomingPdfUriFlow.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    var isOnReaderOrTextRoute by remember { mutableStateOf(false) }

    fun navigateToReader(uriString: String, pageIndex: Int? = null) {
        val encodedUri = Uri.encode(uriString)
        val pageArg = pageIndex ?: -1
        navController.navigate("$ROUTE_READER?uri=$encodedUri&page=$pageArg") {
            launchSingleTop = true
        }
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { pickedUri ->
        if (pickedUri != null) {
            homeViewModel.importAndOpenPdf(pickedUri) { importedUri ->
                navigateToReader(importedUri)
            }
        }
    }

    // Handle external PDF VIEW intents directly in the Reader
    LaunchedEffect(incomingUri) {
        val uri = incomingUri ?: return@LaunchedEffect
        homeViewModel.importAndOpenPdf(uri) { importedUri ->
            navigateToReader(importedUri)
        }
        onConsumeIncomingUri()
    }

    // Wire volume key navigation when Reader or Text Mode is active
    LaunchedEffect(isOnReaderOrTextRoute, preferences.volumeKeyNavigation) {
        if (isOnReaderOrTextRoute && preferences.volumeKeyNavigation) {
            onRegisterVolumeHandler { keyCode ->
                when (keyCode) {
                    KeyEvent.KEYCODE_VOLUME_DOWN -> {
                        readerViewModel.stepPage(1)
                        true
                    }
                    KeyEvent.KEYCODE_VOLUME_UP -> {
                        readerViewModel.stepPage(-1)
                        true
                    }
                    else -> false
                }
            }
        } else {
            onRegisterVolumeHandler(null)
        }
    }

    MjPdfTheme(
        themeMode = preferences.appThemeMode,
        dynamicColor = preferences.dynamicColor
    ) {
        NavHost(
            navController = navController,
            startDestination = ROUTE_HOME
        ) {
            composable(ROUTE_HOME) {
                LaunchedEffect(Unit) { isOnReaderOrTextRoute = false }
                HomeScreen(
                    viewModel = homeViewModel,
                    onPickPdfClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                    onOpenDocument = { uriString, pageIndex ->
                        navigateToReader(uriString, pageIndex)
                    },
                    onOpenSettings = { navController.navigate(ROUTE_SETTINGS) }
                )
            }

            composable(
                route = "$ROUTE_READER?uri={uri}&page={page}",
                arguments = listOf(
                    navArgument("uri") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument("page") {
                        type = NavType.IntType
                        defaultValue = -1
                    }
                )
            ) { backStackEntry ->
                LaunchedEffect(Unit) { isOnReaderOrTextRoute = true }
                val rawUri = backStackEntry.arguments?.getString("uri").orEmpty()
                val decodedUri = Uri.decode(rawUri)
                val rawPage = backStackEntry.arguments?.getInt("page") ?: -1
                val initialPage = rawPage.takeIf { it >= 0 }

                ReaderScreen(
                    uriString = decodedUri,
                    initialPage = initialPage,
                    viewModel = readerViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onOpenTextMode = { navController.navigate(ROUTE_TEXT_MODE) },
                    onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
                    onRotateScreen = onRotateScreen,
                    onSharePdf = onSharePdf,
                    onPrintPdf = onPrintPdf
                )
            }

            composable(ROUTE_TEXT_MODE) {
                LaunchedEffect(Unit) { isOnReaderOrTextRoute = true }
                TextModeScreen(
                    viewModel = readerViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(ROUTE_SETTINGS) {
                LaunchedEffect(Unit) { isOnReaderOrTextRoute = false }
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
