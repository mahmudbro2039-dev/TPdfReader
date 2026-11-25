package com.example

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.system.DevicePermissionsHelper
import com.example.data.system.TempCacheCleanup
import com.example.data.text.PdfBoxTextEngine
import com.example.ui.model.toThemeMode
import com.example.ui.screens.ReaderScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TeenaApp
import com.example.ui.screens.TextModeScreen
import com.example.ui.theme.TeenaTheme
import com.example.ui.viewmodel.AppViewModel
import com.example.ui.viewmodel.ReaderViewModel
import com.example.ui.viewmodel.SettingsViewModel
import com.example.ui.viewmodel.TextModeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

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
        appContainer = (application as com.example.TeenaApp).container
        appContainer.initialPreferences
        val launchedFromHistory =
            (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0
        if (savedInstanceState == null && !launchedFromHistory) {
            handleIncomingIntent(intent)
        }
        enableEdgeToEdge()

        setContent {
            TeenaRoot(
                appContainer = appContainer,
                incomingPdfUriFlow = incomingExternalPdfUri,
                onConsumeIncomingUri = { incomingExternalPdfUri.value = null },
                onRegisterVolumeHandler = { handler -> volumeKeyHandler = handler },
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
            setIntent(Intent(this, MainActivity::class.java))
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
private fun TeenaRoot(
    appContainer: AppContainer,
    incomingPdfUriFlow: MutableStateFlow<Uri?>,
    onConsumeIncomingUri: () -> Unit,
    onRegisterVolumeHandler: (((Int) -> Boolean)?) -> Unit,
    onSharePdf: (String, String) -> Unit,
    onPrintPdf: (String, String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val appViewModel: AppViewModel = viewModel(
        factory = AppViewModel.factory(
            libraryRepository = appContainer.libraryRepository,
            preferencesRepository = appContainer.preferencesRepository,
            searchIndexRepository = appContainer.searchIndexRepository,
            devicePdfScanner = appContainer.devicePdfScanner,
            ocrRunner = appContainer.ocrRunner,
            initialPreferences = appContainer.initialPreferences
        )
    )
    val readerViewModel: ReaderViewModel = viewModel(
        factory = ReaderViewModel.factory(
            libraryRepository = appContainer.libraryRepository,
            preferencesRepository = appContainer.preferencesRepository,
            pdfEngine = appContainer.pdfEngine,
            searchIndexRepository = appContainer.searchIndexRepository,
            searchIndexer = appContainer.searchIndexer,
            textEngine = appContainer.textEngine,
            appContext = context.applicationContext,
            initialPreferences = appContainer.initialPreferences
        )
    )
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(
            preferencesRepository = appContainer.preferencesRepository,
            libraryRepository = appContainer.libraryRepository,
            pdfEngine = appContainer.pdfEngine,
            devicePdfScanner = appContainer.devicePdfScanner,
            searchIndexRepository = appContainer.searchIndexRepository,
            searchIndexer = appContainer.searchIndexer,
            appScope = appContainer.appScope,
            initialPreferences = appContainer.initialPreferences
        )
    )

    val preferences by settingsViewModel.preferences.collectAsStateWithLifecycle()
    val incomingUri by incomingPdfUriFlow.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    var isOnReaderOrTextRoute by remember { mutableStateOf(false) }
    var pendingManualScanPermissionCheck by remember { mutableStateOf(false) }

    fun handleAfterFilePermissionReturned() {
        val updatedStatus = DevicePermissionsHelper.getStatus(context)
        appViewModel.onPermissionsRefreshed(updatedStatus)
        if (pendingManualScanPermissionCheck) {
            pendingManualScanPermissionCheck = false
            appViewModel.onManualAllFilesPermissionResult(updatedStatus.hasAllFilesAccess)
        }
    }

    val allFilesAccessLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        handleAfterFilePermissionReturned()
    }

    val legacyStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        handleAfterFilePermissionReturned()
    }

    fun launchFullFileAccessPrompt(fromManualScan: Boolean = false) {
        pendingManualScanPermissionCheck = fromManualScan
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                allFilesAccessLauncher.launch(DevicePermissionsHelper.buildAllFilesAccessIntent(context))
            }.onFailure {
                pendingManualScanPermissionCheck = false
            }
        } else {
            runCatching {
                legacyStorageLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }.onFailure {
                pendingManualScanPermissionCheck = false
            }
        }
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                appViewModel.onPermissionsRefreshed(DevicePermissionsHelper.getStatus(context))
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        appContainer.searchIndexer.start()
        appViewModel.onPermissionsRefreshed(DevicePermissionsHelper.getStatus(context))
        withFrameNanos { }
        val appCacheDir = context.applicationContext.cacheDir
        val pdfBoxEngine = appContainer.textEngine as? PdfBoxTextEngine
        withContext(Dispatchers.IO) {
            val canCleanPdfBoxTmp = pdfBoxEngine?.hasActiveSession() != true
            TempCacheCleanup.cleanExpiredTempFiles(
                cacheDir = appCacheDir,
                includePdfBoxTmp = canCleanPdfBoxTmp
            )
        }
    }

    fun navigateToReader(uriString: String, pageIndex: Int? = null) {
        readerViewModel.openDocument(uriString, pageIndex)
        val encodedUri = encodeReaderRouteUri(uriString)
        val pageArg = pageIndex ?: -1
        navController.navigate("$ROUTE_READER?uri=$encodedUri&page=$pageArg") {
            launchSingleTop = true
        }
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { pickedUri ->
        if (pickedUri != null) {
            appViewModel.importAndOpenPdf(pickedUri) { importedUri ->
                navigateToReader(importedUri)
            }
        }
    }

    LaunchedEffect(navController) {
        navController.currentBackStackEntryFlow.collect { entry ->
            if (entry.destination.route == ROUTE_HOME) {
                readerViewModel.closeDocument()
            }
        }
    }

    LaunchedEffect(incomingUri) {
        val uri = incomingUri ?: return@LaunchedEffect
        appViewModel.importAndOpenPdf(uri) { importedUri ->
            navigateToReader(importedUri)
        }
        onConsumeIncomingUri()
    }

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

    TeenaTheme(mode = preferences.appThemeMode.toThemeMode()) {
        val motion = TeenaTheme.motion
        NavHost(
            navController = navController,
            startDestination = ROUTE_HOME,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            enterTransition = {
                fadeIn(motion.navFadeSpec())
            },
            exitTransition = {
                fadeOut(motion.navFadeSpec(), targetAlpha = 0.99f)
            },
            popEnterTransition = {
                fadeIn(motion.navFadeSpec(), initialAlpha = 0.96f)
            },
            popExitTransition = {
                fadeOut(motion.navFadeSpec())
            }
        ) {
            composable(ROUTE_HOME) {
                LaunchedEffect(Unit) { isOnReaderOrTextRoute = false }
                TeenaApp(
                    viewModel = appViewModel,
                    onPickPdf = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                    onOpenDocument = { uriString, pageIndex ->
                        navigateToReader(uriString, pageIndex)
                    },
                    onOpenSettings = {
                        navController.navigate(ROUTE_SETTINGS) { launchSingleTop = true }
                    },
                    onRequestAllFilesAccess = { launchFullFileAccessPrompt(fromManualScan = true) }
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
                val decodedUri = decodeReaderRouteUri(rawUri)
                val rawPage = backStackEntry.arguments?.getInt("page") ?: -1
                val initialPage = rawPage.takeIf { it >= 0 }

                ReaderScreen(
                    uriString = decodedUri,
                    initialPage = initialPage,
                    viewModel = readerViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onOpenTextMode = {
                        navController.navigate(ROUTE_TEXT_MODE) { launchSingleTop = true }
                    },
                    onOpenSettings = {
                        navController.navigate(ROUTE_SETTINGS) { launchSingleTop = true }
                    },
                    onSharePdf = onSharePdf,
                    onPrintPdf = onPrintPdf,
                    onRemoveFromLibrary = {
                        if (decodedUri.isNotBlank()) {
                            appViewModel.removeDocument(decodedUri)
                        }
                        navController.popBackStack()
                    }
                )
            }

            composable(ROUTE_TEXT_MODE) {
                LaunchedEffect(Unit) { isOnReaderOrTextRoute = true }
                val readerState by readerViewModel.uiState.collectAsStateWithLifecycle()
                val textModeViewModel: TextModeViewModel = viewModel(
                    factory = TextModeViewModel.factory(
                        uriString = readerState.uriString,
                        textEngine = appContainer.textEngine,
                        preferencesRepository = appContainer.preferencesRepository,
                        initialPreferences = appContainer.initialPreferences,
                    )
                )
                TextModeScreen(
                    viewModel = textModeViewModel,
                    documentTitle = readerState.documentTitle,
                    initialPage = readerState.currentPage,
                    onNavigateBack = { navController.popBackStack() },
                    onViewOriginalPage = { pageIndex ->
                        readerViewModel.jumpToPage(pageIndex)
                        if (!navController.popBackStack()) {
                            navigateToReader(readerState.uriString, pageIndex)
                        }
                    },
                )
            }

            composable(ROUTE_SETTINGS) {
                LaunchedEffect(Unit) { isOnReaderOrTextRoute = false }
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onRequestAllFilesAccess = { launchFullFileAccessPrompt(fromManualScan = false) }
                )
            }
        }
    }
}

internal fun encodeReaderRouteUri(uriString: String): String {
    if (uriString.isBlank()) return ""
    return Base64.encodeToString(
        uriString.toByteArray(Charsets.UTF_8),
        Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
    )
}

internal fun decodeReaderRouteUri(encoded: String): String {
    if (encoded.isBlank()) return ""
    if (encoded.contains("://")) return encoded
    return runCatching {
        String(
            Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING),
            Charsets.UTF_8
        )
    }.getOrElse { encoded }
}
