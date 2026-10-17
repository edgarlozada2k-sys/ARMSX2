package com.armsx2.exkinet

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.armsx2.GameInfo
import com.armsx2.PlayTime
import com.armsx2.exkinet.data.ExkStore
import com.armsx2.exkinet.data.ExkThemeMode
import com.armsx2.exkinet.ui.ExkThemeProvider
import com.armsx2.exkinet.ui.ExkToastHost
import com.armsx2.exkinet.ui.controls.ExkControlsScreen
import com.armsx2.exkinet.ui.controls.ExkTouchEditor
import com.armsx2.exkinet.ui.edit.ExkGameEditor
import com.armsx2.exkinet.ui.edit.ExkSavesScreen
import com.armsx2.exkinet.ui.home.ExkHomeScreen
import com.armsx2.exkinet.ui.home.ExkHomeState
import com.armsx2.exkinet.ui.home.ExkTab
import com.armsx2.exkinet.ui.library.ExkLibraryScreen
import com.armsx2.exkinet.ui.settings.ExkSettingsActions
import com.armsx2.exkinet.ui.settings.ExkSettingsPage
import com.armsx2.exkinet.ui.settings.ExkSettingsScreen
import com.armsx2.navigation.AppRoute
import com.armsx2.navigation.SettingsCategory
import com.armsx2.navigation.UiNavigator
import com.armsx2.runtime.MainActivityRuntime
import com.armsx2.ui.home.HomeViewModel

/** Pantallas de ExKinet dentro de la navegación de ARMSX2. */
sealed interface ExkScreen {
    data class Library(val favorites: Boolean = false, val search: Boolean = false) : ExkScreen
    data class Settings(val page: ExkSettingsPage = ExkSettingsPage.GENERAL, val game: GameInfo? = null) : ExkScreen
    data class Controls(val game: GameInfo?) : ExkScreen
    data class TouchEditor(val serial: String?, val title: String?, val game: GameInfo?) : ExkScreen
    data class Edit(val game: GameInfo) : ExkScreen
    data class Saves(val game: GameInfo) : ExkScreen
}

object ExkUi {
    fun go(screen: ExkScreen, parent: AppRoute = UiNavigator.route.value) = UiNavigator.navigate(AppRoute.Exk(screen, parent))
    fun home() = UiNavigator.navigate(AppRoute.Home)
    fun classicEnabled(): Boolean = ExkStore.prefs.value.classicUi
}

@Composable
private fun rememberLibrary(): HomeViewModel {
    val vm: HomeViewModel = viewModel()
    val dirs = MainActivityRuntime.romsDirs.value
    val ready = MainActivityRuntime.nativeReady.value
    LaunchedEffect(dirs, ready) { vm.load(dirs, ready) }
    val refresh = com.armsx2.data.library.LibraryRefresh.requests.intValue
    LaunchedEffect(refresh, ready, dirs) { if (ready) vm.onRefreshRequested(refresh, dirs) }
    return vm
}

/** Juegos para ExKinet: recientes primero, luego por nombre. */
private fun ordered(vm: HomeViewModel): List<GameInfo> {
    val en = com.armsx2.EnglishTitles.enabled.value
    return vm.state.value.visibleGames.sortedWith(
        compareByDescending<GameInfo> { PlayTime.lastPlayedMillis(it.serial) }.thenBy { it.sortKey(en).lowercase() },
    )
}

private fun tabTo(t: ExkTab) = when (t) {
    ExkTab.HOME -> ExkUi.home()
    ExkTab.LIBRARY -> ExkUi.go(ExkScreen.Library(), AppRoute.Home)
    ExkTab.FAVORITES -> ExkUi.go(ExkScreen.Library(favorites = true), AppRoute.Home)
    ExkTab.SETTINGS -> ExkUi.go(ExkScreen.Settings(), AppRoute.Home)
}

@Composable
fun ExkHomeRoot() {
    val context = LocalContext.current
    LaunchedEffect(Unit) { ExkStore.init(context.applicationContext) }
    val vm = rememberLibrary()
    val addFolder = rememberAddFolder(vm)
    val games = ordered(vm)
    ExkThemeProvider(dark = true) {
        Box(Modifier.fillMaxSize()) {
            ExkHomeScreen(
                games = games,
                onPlay = { vm.launch(it) },
                onContinue = { g, slot -> MainActivityRuntime.launchGameFromSaveSlot(g, slot) },
                onTab = ::tabTo,
                onSearch = { ExkUi.go(ExkScreen.Library(search = true)) },
                onGraphics = { ExkUi.go(ExkScreen.Settings(ExkSettingsPage.GRAPHICS, it)) },
                onTextures = { ExkUi.go(ExkScreen.Settings(ExkSettingsPage.TEXTURES, it)) },
                onSaves = { ExkUi.go(ExkScreen.Saves(it)) },
                onEdit = { ExkUi.go(ExkScreen.Edit(it)) },
                onAddFolder = addFolder,
            )
            ExkToastHost(Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun rememberAddFolder(vm: HomeViewModel): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        val dirs = MainActivityRuntime.romsDirs.value
        if (uri.toString() !in dirs) MainActivityRuntime.setRomsDirs(dirs + uri.toString())
        vm.refresh()
    }
    return { launcher.launch(null) }
}

@Composable
fun ExkScreenRoot(screen: ExkScreen) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { ExkStore.init(context.applicationContext) }
    val vm = rememberLibrary()
    val addFolder = rememberAddFolder(vm)
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            MainActivityRuntime.launchGame(uri.toString(), null)
        }
    }
    val games = ordered(vm)
    val dark = screen is ExkScreen.TouchEditor || ExkStore.prefs.value.theme == ExkThemeMode.DARK
    ExkThemeProvider(dark = dark) {
        Box(Modifier.fillMaxSize()) {
            when (screen) {
                is ExkScreen.Library -> ExkLibraryScreen(
                    games, screen.favorites, vm.state.value.scanning, screen.search, onTab = ::tabTo,
                    onSelect = { ExkHomeState.selectedKey.value = ExkStore.key(it); ExkUi.home() },
                    onEdit = { ExkUi.go(ExkScreen.Edit(it)) },
                    onAddFolder = addFolder, onOpenFile = { openFile.launch(arrayOf("*/*")) }, onRescan = { vm.refresh() },
                )
                is ExkScreen.Settings -> ExkSettingsScreen(
                    screen.page, screen.game ?: games.firstOrNull { ExkStore.key(it) == ExkHomeState.selectedKey.value }, games,
                    ExkSettingsActions(
                        onTab = ::tabTo,
                        onOpenControls = { g -> ExkUi.go(ExkScreen.Controls(g)) },
                        onOpenArmsx2Settings = { g -> UiNavigator.navigate(AppRoute.Settings(SettingsCategory.General, g)) },
                        onOpenBios = { UiNavigator.navigate(AppRoute.BiosManager()) },
                        onEditGame = { g -> ExkUi.go(ExkScreen.Edit(g)) },
                        onAddFolder = addFolder,
                        onRefreshLibrary = { vm.refresh() },
                    ),
                )
                is ExkScreen.Controls -> ExkControlsScreen(
                    screen.game ?: games.firstOrNull { ExkStore.key(it) == ExkHomeState.selectedKey.value },
                    onBack = { UiNavigator.back() },
                )
                is ExkScreen.TouchEditor -> ExkTouchEditor(screen.serial, screen.title, onClose = { UiNavigator.back() })
                is ExkScreen.Edit -> ExkGameEditor(screen.game, onClose = { UiNavigator.back() })
                is ExkScreen.Saves -> ExkSavesScreen(
                    screen.game,
                    onLoadSlot = { slot -> MainActivityRuntime.launchGameFromSaveSlot(screen.game, slot) },
                    onManageStates = { UiNavigator.navigate(AppRoute.SaveManager) },
                    onMemoryCards = { UiNavigator.navigate(AppRoute.MemoryCardManager(screen.game)) },
                    onClose = { UiNavigator.back() },
                )
            }
            ExkToastHost(Modifier.align(Alignment.BottomCenter))
        }
    }
}
