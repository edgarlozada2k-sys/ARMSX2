#!/usr/bin/env python3
"""Integra las rutas de ExKinet en la navegación de ARMSX2. Idempotente."""
import sys
ROOT = sys.argv[1] if len(sys.argv) > 1 else '/root/armsx2-src/platforms/android/app/src/main/java/com/armsx2'

def patch(path, old, new, marker):
    p = f'{ROOT}/{path}'
    s = open(p, encoding='utf-8').read()
    if marker in s:
        print('ya aplicado:', path, marker); return
    if old not in s:
        sys.exit(f'NO ENCONTRADO en {path}: {old[:70]!r}')
    open(p, 'w', encoding='utf-8').write(s.replace(old, new, 1))
    print('ok:', path, marker)

patch('navigation/AppRoute.kt',
"""    data object About : AppRoute
}""",
"""    data object About : AppRoute
    /** ExKinet: pantallas propias. [parent] es a dónde vuelve «Atrás». */
    data class Exk(val screen: com.armsx2.exkinet.ExkScreen, val parent: AppRoute = Home) : AppRoute
}""", 'data class Exk(')

patch('navigation/AppRoute.kt',
"""    fun back(): Boolean {
        if (drawerOpen.value) {""",
"""    fun back(): Boolean {
        // ExKinet: una pantalla con cambios sin guardar (o una captura de tecla) decide primero.
        if (com.armsx2.exkinet.ui.ExkBack.intercept()) return true
        if (drawerOpen.value) {""", 'ExkBack.intercept()')

patch('navigation/AppRoute.kt',
"""            AppRoute.Home -> Unit
            is AppRoute.BiosManager -> {""",
"""            AppRoute.Home -> Unit
            is AppRoute.Exk -> {
                route.value = current.parent
                return true
            }
            is AppRoute.BiosManager -> {""", 'is AppRoute.Exk -> {')

patch('navigation/AppNavigation.kt',
"""                AppRoute.Home -> HomeScreen(
                    onOpenMenu = { UiNavigator.drawerOpen.value = true },
                    onOpenGameSettings = { UiNavigator.navigate(AppRoute.Settings(game = it)) },
                )""",
"""                AppRoute.Home -> if (com.armsx2.exkinet.ExkUi.classicEnabled()) HomeScreen(
                    onOpenMenu = { UiNavigator.drawerOpen.value = true },
                    onOpenGameSettings = { UiNavigator.navigate(AppRoute.Settings(game = it)) },
                ) else com.armsx2.exkinet.ExkHomeRoot()
                is AppRoute.Exk -> com.armsx2.exkinet.ExkScreenRoot(destination.screen)""", 'ExkHomeRoot()')

patch('navigation/AppNavigation.kt',
"""    val route = UiNavigator.route.value
    val drawerOpen = UiNavigator.drawerOpen.value
""",
"""    val route = UiNavigator.route.value
    val drawerOpen = UiNavigator.drawerOpen.value
    // ExKinet: datos propios (fichas, fondos, preferencias) listos antes de decidir la interfaz.
    com.armsx2.exkinet.data.ExkStore.init(androidx.compose.ui.platform.LocalContext.current.applicationContext)
""", 'ExkStore.init(')

patch('navigation/NavigationDrawer.kt',
"""    AppRoute.About -> current is AppRoute.About
}""",
"""    AppRoute.About -> current is AppRoute.About
    is AppRoute.Exk -> current is AppRoute.Exk
}""", 'is AppRoute.Exk -> current is AppRoute.Exk')

patch('navigation/NavigationDrawer.kt',
"""        DrawerItem("games.section.library", "🎮", AppRoute.Home),""",
"""        DrawerItem("games.section.library", "🎮", AppRoute.Home),
        // ExKinet: volver a la interfaz ExKinet (o abrir sus ajustes) desde el menú de ARMSX2.
        DrawerItem("Interfaz ExKinet", "✕", onAction = {
            com.armsx2.exkinet.data.ExkStore.setPrefs { it.copy(classicUi = false) }
            UiNavigator.navigate(AppRoute.Home)
            onDismiss()
        }),""", 'Interfaz ExKinet')
print('listo')
