# ExKinet — estado del proyecto

ExKinet es una **versión personal de ARMSX2** (emulador de PS2 para ARM64, GPL-3.0) con una interfaz propia estilo consola y una pantalla de Controles nueva. Todo el trabajo vive en la rama **`exkinet`** de `edgarlozada2k-sys/armsx2`.

- Dispositivo objetivo: **Lenovo Yoga Tab 13** (Snapdragon 870, Adreno 650, 8 GB, horizontal, táctil + teclado + ratón + mando).
- Juego prioritario: **Rogue Galaxy** (versión del usuario: **SCES-54552, PAL**).
- Identificador de la app: `com.exkinet.app`. Nombre: `ExKinet`. Se instala **junto** a ARMSX2 oficial (no lo reemplaza).

> Última actualización: 2026-10-17. Este documento describe el estado exacto del último commit de la rama.

---

## 1. Estado en una línea

**El código compila** (Kotlin y el motor C++ completo, en local). **La APK todavía no se ha probado en un Android real**: ninguna afirmación de este documento sobre «funciona en la tablet» está comprobada. Ver §5.

---

## 2. Arquitectura

```
platforms/android/app/src/main/java/com/armsx2/
├── exkinet/                      ← TODO lo nuevo de ExKinet
│   ├── ExkRoot.kt                  Rutas y raíz de pantallas (Inicio, Biblioteca, Ajustes, Controles…)
│   ├── data/
│   │   ├── ExkStore.kt             Fichas, favoritos, encuadres, vídeos y preferencias (files/exkinet/meta.json, escritura atómica + .bak)
│   │   ├── ExkMedia.kt             Importar portadas/fondos (copiados y reducidos) y validar vídeos (permiso persistente)
│   │   ├── ExkBackup.kt            Copia completa .zip (ARMSX2 + ExKinet + opcional texturas/juegos) y restauración
│   │   └── ExkDefaults.kt          Texto inicial de Rogue Galaxy
│   ├── input/
│   │   ├── ExkInput.kt             Tabla de TECLADO propia, captura de teclas/ejes, «Probar controles», liberación de entradas
│   │   ├── Ps2Button.kt            Botones de PS2 ↔ acciones reales de ARMSX2
│   │   └── ControlsDraft.kt        Guardar / Cancelar / Restablecer sobre las preferencias reales
│   └── ui/
│       ├── ExkTheme.kt, ExkComponents.kt, ExkNav.kt   Paleta, botones, píldoras, navegación con mando
│       ├── home/ExkHomeScreen.kt   Inicio (fondo con fundido, carrusel con elevación, Jugar/Continuar…)
│       ├── home/ExkMediaViews.kt   Portadas, fondo continuo con máscara, vídeo de fondo (MediaPlayer + TextureView)
│       ├── library/ExkLibraryScreen.kt   Biblioteca: buscar, filtrar, ordenar, favoritos
│       ├── settings/ExkSettingsScreen.kt Ajustes: General, Apariencia, Gráficos por juego, Fondos y vídeos, Texturas HD, Copias
│       ├── controls/ExkControlsScreen.kt Controles: Teclado / Mando / Pantalla táctil, ámbito general o por juego
│       ├── controls/ExkTouchEditor.kt    «Editar distribución» táctil (horizontal y vertical por separado)
│       ├── controls/Ps2Diagram.kt        Esquema del mando de PS2 (selección y prueba)
│       ├── controls/ControlsLogic.kt     Conflictos, reasignar, restablecer, perfil por juego
│       └── edit/ExkGameEditor.kt, edit/ExkSavesScreen.kt   Editar ficha (nombre, descripción, portada, fondo) y Partidas
└── (motor ARMSX2, con cambios mínimos — ver §3)
```

**Por qué integrado y no un lanzador externo:** los controles, texturas, ajustes por juego y partidas de ARMSX2 viven en su almacenamiento privado. Solo dentro del mismo APK se pueden leer y modificar de verdad.

**Navegación con mando:** ARMSX2 no usa el foco de Compose (la vista del juego es un SurfaceView), sino un registro propio (`SettingsControllerNav` / `Modifier.controllerFocusable`). Todos los controles de ExKinet se registran con `Modifier.exkNav(id, …)`. Los diálogos usan `PadModal` (una tarea de compilación, `checkNoWindowModals`, prohíbe los `Dialog` de ventana).

---

## 3. Cambios en el motor ARMSX2 (Kotlin, mínimos)

Se aplican con `platforms/android/exkinet-tools/patch_engine.py` y `patch_nav.py`. Son idempotentes y ya están aplicados en este commit; sirven para reaplicarlos sobre una versión nueva de ARMSX2.

| Archivo | Cambio |
|---|---|
| `runtime/MainActivityRuntime.kt` | Gancho `ExkInput.interceptKey` / `interceptMotion` (captura y prueba). Teclado físico con tabla propia (`kb.map.*`) y jugador propio. Al desconectar un mando se sueltan sus botones y ejes. Al perder el foco o pausar se suelta toda la entrada. |
| `input/PadRouter.kt` | `claimedPortOf(deviceId)` (puerto sin reclamar uno nuevo). |
| `input/ControllerMappings.kt` | Zona muerta por ámbito explícito (`stickDeadzoneScope`/`setStickDeadzoneScope`) y `exkDropTuningCaches()`. |
| `ui/touch/TouchControls.kt` | `exkLayoutFor` / `exkSaveLayout` / `exkHasGameLayout` / `exkReload`: leer y guardar la distribución por ámbito y orientación sin un juego en marcha. |
| `navigation/*` | Ruta `AppRoute.Exk`, Inicio de ExKinet (o el clásico si se activa), «Atrás» interceptable, entrada «Interfaz ExKinet» en el menú lateral. |
| `settings.gradle.kts` | Sin el plugin foojay (no se usan toolchains automáticas). |
| `app/build.gradle.kts` | `-Parmsx2.disableLibrashader=true` (compilar sin Rust). `lint.checkReleaseBuilds = false`. |

---

## 4. Implementado (compila; NO probado en Android real)

### Interfaz (diseño `docs/exkinet/diseno/`)
- Inicio: navegación superior, título y descripción a la izquierda, **Jugar** (azul), **Continuar** (último estado rápido), favorito, Ajustes HD, Texturas, Partidas, Editar ficha.
- Fondo a pantalla completa con máscara y velos continuos. Fundido de unos 800 ms con precarga; si se eligen juegos rápido, solo aparece el último.
- Portada y fondo **independientes**: nunca se amplía la portada como fondo.
- Carrusel: la tarjeta anterior baja y la nueva sube (−16 dp), se asienta en −6 dp con escala 1,035 en 650 ms (curva .22,.75,.18,1). Las tarjetas se conservan por clave. El brillo se dibuja con trazos para no recortarse. El carrusel solo se desplaza si la tarjeta queda fuera.
- Vídeo de fondo opcional: imagen → espera de 2/3/5 s → vídeo sin sonido, en bucle y con fundido. Se libera al cambiar de juego, salir de Inicio, jugar o pasar a segundo plano. Se valida al añadirlo y se guarda con permiso persistente.
- Reducir animaciones (desactiva movimiento y vídeos). Tema oscuro/claro (el Inicio siempre es oscuro).
- Biblioteca: búsqueda por nombre o serial, filtros, orden, favoritos, añadir carpeta, abrir archivo, actualizar.
- Editar ficha: nombre (usa `CustomNames` de ARMSX2), descripción, portada y fondo con encuadre por arrastre o pellizco, sin deformar.
- Ajustes en pantalla completa con categorías y «Ajustes avanzados de ARMSX2».
- Gráficos por juego **reales**: perfiles Rendimiento/Equilibrado/Calidad (1×/2×/3×), resolución 1–4×, renderizador, filtrado, FXAA y vídeos por software. Ámbito por juego o general. Restaurar predeterminados. Explica la diferencia entre resolución 3D, texturas y vídeos.
- Texturas HD **reales**: usa el instalador de ARMSX2 (ZIP / tar.zst, sin rutas peligrosas, reversión). Muestra tamaño y espacio libre, activa/desactiva por juego (`LoadTextureReplacements`), elimina y avisa si el ZIP declara otro serial.
- Partidas: estados rápidos por ranura (cargar), tarjetas de memoria y gestor de ARMSX2.
- Copias: un .zip con la copia oficial de ARMSX2 (partidas, ajustes y **perfiles de controles**) más los datos de ExKinet. Opcionalmente incluye texturas y juegos, con tamaño estimado. Restauración validada. Nunca incluye BIOS ni claves.

### Controles (Ajustes → Controles)
- Pestañas **Teclado / Mando / Pantalla táctil**. Ámbito **Configuración general** o **Solo para este juego** (hereda la general), más **Volver a usar la configuración general**.
- Esquema de PS2 interactivo junto a la lista de asignaciones: cruceta, ambos sticks y sus direcciones, L3/R3, botones frontales, L1/L2/R1/R2, Start, Select y Analog.
- Captura con «Pulsa la nueva tecla». No dispara otras funciones, se cancela con un botón y permite borrar la asignación. Nombres en español (Espacio, Intro, Flecha arriba…).
- **Configurar paso a paso**, con opción de omitir cada botón.
- Conflictos: Reemplazar, Conservar ambas (solo cuando el motor lo admite: modificador de un atajo combinado) o Cancelar. Nada cambia sin confirmación.
- Atajos del emulador separados (Pausa, Guardado/Carga rápida, Ranura, Avance rápido, Captura, Estadísticas). ARMSX2 ya los dispara una sola vez por pulsación (`repeatCount == 0`).
- Mando: lista en vivo de mandos, asignación a Jugador 1/2/Automático (`PadRouter.setPin`), asignación por jugador, automática estándar, captura de botón o eje, zona muerta, inversión e intercambio de ejes. Aviso si no hay mando. El perfil se conserva al desconectar.
- Táctil: editor con vista de ejemplo. Permite arrastrar, cambiar tamaño, mostrar u ocultar, ajustar transparencia y separación, mover con flechas, probar y restaurar. Horizontal y vertical van por separado y nunca fuera de la zona segura.
- **Probar controles**: traduce cada entrada con las mismas funciones del motor (`targetForPhysical`, tabla de teclado, zona muerta e inversión) e ilumina el esquema. Admite varias entradas simultáneas.
- Guardar cambios / Cancelar / Restablecer (pestaña actual o perfil completo). Aviso al salir con cambios pendientes.

---

## 5. Pendiente y errores conocidos

### Sin probar en dispositivo (lo más importante)
Nada se ha ejecutado en la Yoga Tab 13 ni en un emulador Android. Hay que comprobar, como mínimo:
1. Arranque, asistente de BIOS de ARMSX2 e inicio de **Rogue Galaxy SCES-54552**.
2. Navegación con mando por todas las pantallas de ExKinet (registro `exkNav`).
3. Que una asignación cambiada en Controles cambie de verdad el control **dentro del juego**, con varias teclas simultáneas, sin entradas atascadas al pausar, cambiar de ventana o desconectar, y aplicando bien los perfiles generales y por juego.
4. Vídeo de fondo (MediaPlayer + TextureView), fundidos y rendimiento.
5. Copias: exportar y restaurar en el dispositivo.

### Limitaciones conocidas (del motor, no corregidas)
- ARMSX2 asigna **una sola acción de PS2 por tecla física**: «Conservar ambas» solo está disponible para modificadores de atajos.
- Los sticks físicos se leen de los ejes estándar de Android: no se puede asignar el stick derecho físico al stick izquierdo de PS2. Sí se puede invertir e intercambiar ejes.
- Los atajos del emulador no tienen versión por juego y son comunes a teclado y mando. «Pantalla completa» no aplica en Android.
- Zona muerta: el juego la aplica al stick izquierdo y al derecho por separado; la prueba usa la misma función.
- La prueba de la pestaña táctil ocurre dentro del editor; no pasa por el núcleo, porque no hay juego en marcha.
- «Importar desde archivos» abre el juego directamente; la biblioteca persistente de ARMSX2 se basa en **carpetas**.
- Las cadenas de shaders de RetroArch (librashader) están desactivadas en esta compilación porque mi entorno no tiene Rust. En GitHub Actions también se desactivan, por coherencia.
- El arte de ejemplo del prototipo (carátulas y fondo, de juegos comerciales) **no** está en el repositorio, que es público. Cada usuario pone sus imágenes en «Editar ficha».

### Errores actuales
- Compilación local de Kotlin: **0 errores** en el último commit.
- La primera ejecución de GitHub Actions falló por un error de YAML (ya corregido).

---

## 6. Compilar

### En GitHub Actions (recomendado)
- Flujo: `.github/workflows/exkinet-android.yml`. Se ejecuta con cada push a `exkinet` o a mano (**Run workflow**).
- Ejecuciones: https://github.com/edgarlozada2k-sys/armsx2/actions/workflows/exkinet-android.yml
- La APK queda como artefacto `exkinet-android-arm64` de cada ejecución.
- **Firma estable (opcional):** crea los secretos del repositorio `EXKINET_KEYSTORE_B64` (keystore JKS en base64, alias `exkinet`) y `EXKINET_KEYSTORE_PASSWORD`. Sin ellos se firma con una clave temporal, y para instalar la siguiente APK habría que desinstalar antes (haz una copia en Ajustes → Copias). **No subas nunca el keystore al repositorio.**

### En local (Linux)
1. Android SDK con `platforms;android-37.0`, `build-tools;37.0.0`, `ndk;29.0.14206865` y `cmake;3.31.6`. JDK 17 o 21. Gradle 9.4.1 (o `./gradlew`).
2. Submódulos: `git submodule update --init --recursive`.
3. Dependencias de shaderc: `python3 platforms/android/app/src/main/cpp/3rdparty/shaderc/utils/git-sync-deps`.
4. `platforms/android/local.properties` con `sdk.dir=/ruta/al/sdk`.
5. (Opcional) `platforms/android/armsx2_keystore.properties` con `storeFile`, `storePassword`, `keyAlias` y `keyPassword` (está en `.gitignore`).
6. Compilar:
   ```
   cd platforms/android
   ./gradlew :app:assembleGithubRelease -Parmsx2.applicationId=com.exkinet.app -Parmsx2.appLabel=ExKinet -Parmsx2.disableLibrashader=true
   ```
   La APK queda en `app/build/outputs/apk/github/release/`. El motor C++ tarda bastante la primera vez (≈30–60 min con 2 núcleos); después solo se recompila lo que cambia.

---

## 7. Legal
- ARMSX2 y PCSX2 son GPL-3.0. Esta versión también lo es: si se distribuye la APK, hay que publicar su código (esta rama).
- No se incluyen juegos, BIOS, paquetes de texturas ni arte comercial. Usa solo copias y paquetes que tengas derecho a usar.
