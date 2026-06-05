# Stoic Launcher

![Android](https://img.shields.io/badge/Android-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?logo=kotlin&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-green)

Launcher minimalista, monocromático y productivo para Android. Diseñado para reducir distracciones con una estética estoica — pocas categorías, burbujas elegantes y sin scroll innecesario.

---

## 🎯 Filosofía

- **Minimalismo:** solo 6 categorías, pantalla fija sin scroll, sin elementos superfluos
- **Monocromático:** todos los íconos se renderizan en blanco/gris sobre fondo oscuro
- **Productividad:** burbujas agrupadas por tipo, búsqueda rápida, gestos configurables
- **Estoicismo:** columna dórica como ícono del launcher; diseño calmado y funcional

---

## 🧱 Arquitectura

```
app/src/main/java/com/diez/stoiclauncher/
├── core/di/
│   └── AppContainer.kt          # Inyección manual de dependencias
├── data/repository/
│   ├── AppPreferencesRepository.kt  # DataStore (favoritos, aliases, grupos)
│   ├── AppRepositoryImpl.kt         # Carga de apps instaladas + íconos
│   └── SettingsRepositoryImpl.kt    # SharedPreferences (tema, wallpaper, widgets)
├── domain/
│   ├── model/
│   │   ├── AppModel.kt             # Modelo de app (label, icon, package, groupId)
│   │   └── WidgetConfig.kt         # Configuración de widget persistido
│   ├── repository/
│   │   ├── AppRepository.kt        # Interfaz de repositorio de apps
│   │   └── SettingsRepository.kt   # Interfaz de settings + categorías ocultas
│   ├── usecase/                    # Casos de uso (Clean Architecture)
│   │   ├── GetInstalledAppsUseCase.kt
│   │   ├── RefreshAppsUseCase.kt
│   │   ├── FilterAppsUseCase.kt
│   │   ├── ToggleAppFavoriteUseCase.kt
│   │   ├── ManageAppGroupsUseCase.kt
│   │   ├── HideAppUseCase.kt
│   │   └── RenameAppUseCase.kt
│   ├── usage/
│   │   └── AppUsageManager.kt      # Límites de uso por app
│   └── util/
│       ├── AppCategorizer.kt       # Categorización heurística (6 categorías)
│       ├── IconPackManager.kt      # Carga de packs de íconos (built-in + externos)
│       └── TextIconDrawable.kt     # Ícono de texto (fallback)
└── presentation/
    ├── MainActivity.kt             # Activity principal (ViewPager2 + 3 páginas)
    ├── common/
    │   └── BottomSheetMenu.kt      # Menú bottom sheet reutilizable
    ├── controller/
    │   └── WidgetController.kt     # Controlador de widgets (añadir/quitar/resize)
    ├── home/
    │   ├── HomeViewModel.kt        # ViewModel compartido (AppModel, búsqueda, filtros)
    │   ├── AppAdapter.kt           # Adapter de app en grid (ícono + label)
    │   ├── AppListAdapter.kt       # Adapter de app en lista (búsqueda/drawer)
    │   └── fragments/
    │       ├── HomeFragment.kt     # Pantalla principal — burbujas por categoría
    │       ├── DrawerFragment.kt   # Cajón de apps con búsqueda
    │       ├── WidgetsFragment.kt  # Página de widgets (carrusel)
    │       ├── FavoritesFragment.kt # Página de favoritos
    │       └── Interfaces.kt       # AppActionListener + WidgetContainerProvider
    ├── manager/
    │   └── WallpaperSettingsManager.kt  # Fondo de pantalla + color de acento
    ├── services/
    │   └── StoicAccessibilityService.kt # Servicio de accesibilidad (gestos)
    ├── settings/
    │   ├── SettingsActivity.kt     # Configuración (tema, wallpaper, icon pack, etc.)
    │   ├── GesturesActivity.kt     # Configuración de gestos
    │   └── ThemeAdapter.kt         # Selector de temas (11 colores)
    ├── util/
    │   ├── AppLaunchHelper.kt      # Lanzar apps con seguridad
    │   ├── ColorHelper.kt          # Contraste, luminancia, colores de texto
    │   ├── LaunchHelper.kt         # Atajos (reloj, calendario)
    │   ├── UiHelper.kt             # Utilidades de UI (bottom sheet tint)
    │   └── ViewModelFactory.kt     # Factory para ViewModels con parámetros
    └── widget/
        ├── WidgetManager.kt        # Gestión de widgets del sistema
        ├── WidgetPickerAdapter.kt  # Selector de widgets disponibles
        └── calendar/
            └── StoicCalendarWidget.kt  # Widget de calendario personalizado
```

### Stack tecnológico

| Capa | Tecnología |
|------|-----------|
| UI | View system + ViewPager2 + RecyclerView + ConstraintLayout |
| Arquitectura | MVVM + Clean Architecture (ViewModel + UseCase + Repository) |
| DI | Manual (AppContainer) |
| Persistencia | DataStore (preferencias de apps) + SharedPreferences (settings) |
| Concurrencia | Kotlin Coroutines + Flow (StateFlow, SharedFlow) |
| Íconos | VectorDrawable + WebP (Arcticons integrado) + ColorMatrix (monocromo) |

---

## 🏠 Pantalla principal (Burbujas)

La home muestra **6 burbujas** en una cuadrícula de 2 columnas **sin scroll**:

| Categoría | Apps típicas |
|-----------|-------------|
| **Social** | WhatsApp, Instagram, Telegram, Discord, Twitter/X, Reddit |
| **Finanzas** | Ualá, Mercado Pago, Binance, TradingView, bancos, delivery |
| **Entretenimiento** | Spotify, YouTube, Netflix, Disney+, juegos, fotos |
| **Trabajo** | ChatGPT, Chrome, Gmail, Drive, GitHub, VS Code, ofimática |
| **Sistema** | Configuración, Cámara, Reloj, Calculadora, utilidades |
| **Otros** | Apps sin categoría específica |

### Interacciones
- **Toque en burbuja:** abre diálogo fullscreen con todas las apps de esa categoría
- **Toque largo en burbuja:** menú con opciones para renombrar, ocultar o editar apps
- **Toque largo en fondo:** añadir widget o ir a configuración

### Categorización

`AppCategorizer` usa dos estrategias:
1. **API nativa (Android 8+):** `ApplicationInfo.category` para apps del sistema
2. **Heurística:** análisis de `packageName` con palabras clave

El orden de evaluación es: Social → Finanzas → Entretenimiento → Trabajo → Sistema → Otros.
Las categorías se pueden **ocultar** o **renombrar** desde el menú de burbuja.

---

## 🎨 Sistema de íconos

### IconPackManager

Soporta 3 modos seleccionables desde Configuración:

| Modo | Descripción |
|------|------------|
| **Stoic Pack** | Íconos de Arcticons integrados (82 WebP, 160+ mapeos por paquete) |
| **Stoic Minimal** | Extrae capa monocromática de `AdaptiveIconDrawable` (API 33+) |
| **Pack externo** | Carga packs de íconos compatibles con Nova/Apex Launcher |

### Carga de íconos

```
AppRepositoryImpl.refreshApps()
  └─ IconPackManager.getIcon(componentName)
       ├─ isStoicBuiltin → busca en builtinIconMap (appfilter.xml)
       ├─ isStoicMinimal → extrae capa monocromática del sistema
       └─ external pack → parsea appfilter.xml del pack externo
```

El `IconPackManager` carga la base de datos de íconos built-in en su `init` para evitar race conditions.

### Monocromático

Todos los adapters aplican `ColorMatrixColorFilter` con `saturation=0` a los íconos renderizados, garantizando estética monocromática uniforme.

---

## ⚙️ Configuración

Accesible desde el toque largo en la home o desde el cajón de apps:

- **Tema:** 11 colores de acento (Ónix, Abedul, Ceniza, Ronchi, Galápagos, Lavanda, Sakura, Nórdico, Matcha, Ámbar, Océano)
- **Wallpaper:** fondo personalizado desde galería o sistema
- **Pack de íconos:** Stoic Pack, Stoic Minimal, Predeterminado, o packs externos
- **Apps ocultas:** ocultar apps del cajón sin desinstalarlas
- **Límites de uso:** restringir tiempo por app (requiere permiso de estadísticas)
- **Gestos:** doble toque y swipe down configurables (búsqueda, notificaciones, linterna)
- **Atajos del dock:** personalizar las apps de la barra inferior
- **Idioma:** español / inglés

---

## 📱 Pantallas

El launcher usa un **ViewPager2 horizontal** con 3 páginas:

```
[ Widgets ]  ←→  [ Home (burbujas) ]  ←→  [ Drawer (búsqueda) ]
```

- **Widgets:** carrusel de widgets del sistema + widget de calendario Stoic
- **Home:** reloj, fecha, área de widgets, burbujas de categorías
- **Drawer:** buscador universal con teclado automático + lista de todas las apps

### Dock inferior
Barra flotante con 4 íconos rápidos (teléfono, mapas, música, mensajes). Se oculta al deslizar hacia el drawer.

---

## 🧪 Widgets

El launcher soporta **widgets del sistema Android** mediante `AppWidgetHost`:

- **Añadir:** toque largo en home → "Añadir Widget" → selector de widgets
- **Redimensionar:** bordes arrastrables en modo edición
- **Persistencia:** configuración guardada en SharedPreferences (JSON)
- **Widget Stoic Calendar:** widget de calendario personalizado con provider propio

---

## 🔒 Seguridad

- `release-key.jks` excluido del repositorio (`.gitignore`)
- `keystore.properties.example` como plantilla (sin secretos reales)
- Permisos mínimos: `REQUEST_DELETE_PACKAGES`, `READ_CALENDAR`, `PACKAGE_USAGE_STATS`

---

## 🚀 Build & Instalación

### Requisitos
- Android Studio Hedgehog+ o CLI con Gradle 8.x
- JDK 17
- compileSdk 35, minSdk 26, targetSdk 35

### Build
```bash
./gradlew :app:assembleDebug
```

### Instalar
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Firmar release
```bash
cp keystore.properties.example keystore.properties
# Editar keystore.properties con las rutas y credenciales reales
./gradlew :app:assembleRelease
```

---

## 📦 Dependencias principales

```toml
# build.gradle.kts / libs.versions.toml
androidx-core-ktx       # Extensiones Android
androidx-activity-ktx   # Activity KTX
androidx-fragment-ktx   # Fragment KTX
androidx-recyclerview   # RecyclerView
androidx-viewpager2     # ViewPager2
androidx-constraintlayout # ConstraintLayout
androidx-datastore      # DataStore Preferences
androidx-palette        # Palette (análisis de colores de wallpaper)
material                # Material Design 3
kotlinx-coroutines      # Corrutinas + Flow
kotlinx-serialization   # Serialización JSON
gson                    # GSON (migración de DataStore)
```

---

## 📄 Licencia

MIT License — ver [LICENSE](LICENSE)

Los íconos de Arcticons están bajo [Apache 2.0](https://github.com/Arcticons-Team/Arcticons) © Arcticons Team.

---

## ⚡ Rendimiento

Cada decisión de arquitectura fue evaluada para mantener el launcher **ligero, fluido y con bajo consumo de batería**.

### Carga de apps

| Técnica | Detalle |
|---------|---------|
| `Dispatchers.IO` | La carga de íconos y el parseo de `appfilter.xml` se ejecutan en hilos de I/O, sin bloquear el main thread |
| `setHasFixedSize(true)` | Todos los `RecyclerView` declaran tamaño fijo para evitar recálculos de layout |
| `setItemViewCacheSize(30)` | Cache de 30 vistas recicladas — cubre la pantalla completa sin crear nuevas |
| `ViewPager2.offscreenPageLimit = 2` | Las 3 páginas se mantienen en memoria para transiciones instantáneas |
| `MutableStateFlow` | Las listas de apps usan `StateFlow` con `WhileSubscribed(5000)` que cancela el upstream tras 5s de inactividad |
| `DistinctUntilChanged` implícito | `combine` de flows solo emite cuando algún valor cambia realmente |

### Íconos

| Técnica | Detalle |
|---------|---------|
| `ConstantState.newDrawable()` | Los drawables monocromáticos se clonan desde el `ConstantState` sin re-decodificar el recurso |
| `ColorMatrix` nativo | El filtro de saturación=0 usa `ColorMatrixColorFilter` sobre el GPU, sin overhead de CPU |
| WebP lossless | Los 82 íconos de Arcticons son WebP comprimidos (~3 KB c/u, total ~250 KB) |
| VectorDrawable | Los íconos del dock y del launcher son vectores puros — escalan sin pérdida y pesan ~1 KB c/u |
| `loadBuiltinPackSync()` en `init` | El `appfilter.xml` se parsea una sola vez al construir el `IconPackManager`, nunca en runtime |
| `iconMap` en memoria | El mapeo paquete→ícono (160 entradas) pesa ~20 KB en RAM — insignificante |

### UI

| Técnica | Detalle |
|---------|---------|
| `notifyDataSetChanged()` mínimo | Solo se llama cuando cambian los datos reales (apps instaladas/desinstaladas) |
| `clipToPadding = false` | Evita recortes durante scroll, pero con `overScrollMode = NEVER` eliminamos el overscroll innecesario |
| `isNestedScrollingEnabled = false` | La grilla de burbujas no hace scroll — elimina procesamiento de gestos anidados |
| ViewBinding (`ItemAppBinding`) | El adapter principal usa ViewBinding para evitar `findViewById` repetitivo |
| `DataStore` en vez de Room | Para preferencias simples (favoritos, aliases, grupos) no se necesita SQLite; DataStore es asíncrono y ligero |
| `SharedPreferences` para settings | Solo settings de UI (tema, wallpaper, widgets) usan SharedPreferences; cambios infrecuentes |

### Startup

| Técnica | Detalle |
|---------|---------|
| `AppContainer` lazy init | Las dependencias se crean en `StoicApplication.onCreate()` en background |
| `refreshApps()` asíncrono | La primera carga de apps se dispara en `Dispatchers.IO` y emite vía `StateFlow` |
| Sin `SplashScreen` artificial | El launcher arranca directo a la home — sin pantallas intermedias |
| `singleTask` launch mode | El `MainActivity` usa `singleTask` para reutilizar la instancia existente |
| `stateNotNeeded = true` | Android no guarda/restaura el estado de la activity en rotación — el ViewModel lo maneja |

### Memoria

| Técnica | Detalle |
|---------|---------|
| `AppModel` data class | Liviana (8 campos), inmutable, `copy()` para modificaciones sin reasignar |
| `TextIconDrawable` lazy | Solo se instancia como fallback cuando no hay ícono disponible |
| Sin leaks de Context | El `AppContainer` usa `applicationContext`; los repositories no retienen Activities |
| `destroy()` explícito | `AppRepositoryImpl.destroy()` desregistra el callback de paquetes y cancela el scope de corrutinas |
| `SupervisorJob` | Si una corrutina hija falla, no cancela a las demás |

### APK

| Métrica | Valor |
|---------|-------|
| Tamaño debug | ~7 MB |
| Íconos Arcticons | 82 archivos, ~250 KB comprimidos |
| Vector drawables | 60 archivos, ~60 KB |
| Código Kotlin | ~35 archivos, ~200 KB compilado |
| Sin ProGuard/R8 en debug | Se habilita solo en release para reducción adicional (~40%) |

