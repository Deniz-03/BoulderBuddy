package com.boulderbuddy.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.boulderbuddy.ui.components.BottomNav
import com.boulderbuddy.ui.components.BottomNavTab
import com.boulderbuddy.ui.components.SideNav
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.Breite
import com.boulderbuddy.ui.theme.aktuelleBreite
import com.boulderbuddy.ui.screens.BoulderDetailRoute
import com.boulderbuddy.ui.screens.BoulderUebersichtScreen
import com.boulderbuddy.ui.screens.AbgleichScreen
import com.boulderbuddy.ui.screens.EinstellungenScreen
import com.boulderbuddy.ui.screens.GhostClimberScreen
import com.boulderbuddy.ui.screens.HangboardHistorieScreen
import com.boulderbuddy.ui.screens.HangboardTimerScreen
import com.boulderbuddy.ui.screens.HomeScreen
import com.boulderbuddy.ui.screens.KameraScreen
import com.boulderbuddy.ui.screens.RouteHinzufuegenScreen
import com.boulderbuddy.ui.screens.SessionErstellenScreen
import com.boulderbuddy.ui.screens.SessionRoute
import com.boulderbuddy.ui.screens.SessionUebersichtScreen
import com.boulderbuddy.ui.screens.StatistikScreen
import com.boulderbuddy.ui.viewmodel.BoulderUebersichtViewModel
import com.boulderbuddy.ui.viewmodel.AbgleichViewModel
import com.boulderbuddy.ui.viewmodel.EinstellungenViewModel
import com.boulderbuddy.ui.viewmodel.GhostClimberViewModel
import com.boulderbuddy.ui.viewmodel.HangboardHistorieViewModel
import com.boulderbuddy.ui.viewmodel.HangboardTimerViewModel
import com.boulderbuddy.ui.viewmodel.HomeViewModel
import com.boulderbuddy.ui.viewmodel.RouteHinzufuegenViewModel
import com.boulderbuddy.ui.viewmodel.SessionErstellenViewModel
import com.boulderbuddy.ui.viewmodel.SessionListViewModel
import com.boulderbuddy.ui.viewmodel.StatistikViewModel
import com.boulderbuddy.data.camera.CaptureAuftrag
import com.boulderbuddy.widget.WidgetIntent

/**
 * Schlüssel, unter dem der Kamera-Screen seine fertige Aufnahme im `savedStateHandle` des
 * **vorigen** Back-Stack-Eintrags ablegt (7.4d).
 *
 * Nav-Compose kennt keine Rückgabewerte einer Route — der Weg über den `savedStateHandle` des
 * Aufrufers ist der vorgesehene Ersatz. Der Aufrufer liest den Wert, verarbeitet ihn und
 * **löscht ihn** wieder; sonst käme dieselbe Aufnahme bei jedem erneuten Betreten des Screens
 * noch einmal an.
 */
const val KAMERA_ERGEBNIS = "kamera_ergebnis_uri"

// =============================================================================
// AppNavigation — der NavHost: verbindet jede Route aus Destinations.kt mit
//                  dem passenden Screen und stellt die gemeinsame BottomNav.
// =============================================================================
//
// Phase 1: Navigations-Gerüst mit Platzhalter-Daten (noch keine ViewModels/Room).
//   - Type-safe Routen (composable<Route> / toRoute()).
//   - BottomNav ist HOCHGEZOGEN: nicht mehr in den Tab-Screens, sondern hier —
//     sichtbar nur bei den 4 Tab-Zielen (Home/Sessions/Stats/Timer).
//   - Tab-Wechsel mit launchSingleTop + saveState/restoreState -> kein Stacking.
//
// Push-Navigation (Screen-Callbacks) folgt in Phase 2.

@Composable
fun AppNavigation(
    // Optionales Sprungziel vom Homescreen-Widget (7.4c); null = normaler Start (Home).
    initialNavTarget: String? = null,
    // Session-ID zu TARGET_ACTIVE_SESSION; sonst null.
    initialNavSessionId: Int? = null,
) {
    val navController = rememberNavController()

    // Einmaliger Sprung ins Widget-Ziel (7.4c). key = Zielwert → feuert nur beim Start-Intent,
    // nicht bei jeder Recomposition.
    LaunchedEffect(initialNavTarget, initialNavSessionId) {
        when (initialNavTarget) {
            WidgetIntent.TARGET_TIMER -> navController.navigateToTab(BottomNavTab.Timer)
            WidgetIntent.TARGET_NEW_SESSION -> navController.navigate(SessionErstellen)
            // Direkt in die laufende Session. Der Push liegt über Home, Zurück führt also
            // dorthin. Fehlt die ID (veralteter Widget-Stand), bleiben wir auf Home.
            WidgetIntent.TARGET_ACTIVE_SESSION ->
                initialNavSessionId?.let { navController.navigate(Session(sessionId = it)) }
        }
    }

    // Drei Breitenstufen statt eines Booleans (siehe ui/theme/Breite.kt). `Weit` (≥ 840 dp)
    // schaltet die Zwei-Pane-Layouts, `Mittel` (≥ 600 dp) bereits die seitliche Navigation:
    // eine unten angeheftete Leiste ist ab dieser Breite weder erreichbar noch sinnvoll.
    val breite = aktuelleBreite()
    val isWideLayout = breite == Breite.Weit

    // Aktuelles Ziel beobachten, um den aktiven Tab abzuleiten bzw. die Navigation
    // nur auf den 4 Tab-Zielen einzublenden.
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentTab = backStackEntry?.destination.toBottomNavTabOrNull()

    // Die Navigation wechselt die Achse, der NavHost bleibt derselbe. Deshalb steckt der
    // gesamte Graph in einem Lambda statt zweimal im Baum: zwei NavHost-Aufrufe wären zwei
    // getrennte Back-Stacks, und beim Drehen des Tablets über die 600-dp-Grenze wäre der
    // Verlauf weg.
    val inhalt: @Composable (Modifier) -> Unit = { inhaltModifier ->
        NavHost(
            navController = navController,
            startDestination = Home,
            modifier = inhaltModifier,
            // Kurzer Crossfade (~120 ms): das Default-~700-ms-Fade des NavHost wirkt träge,
            // ein harter Instant-Wechsel lässt kurz alte Komponenten aufblitzen. Der kurze
            // Fade blendet die alte Ansicht sauber aus. Gilt für Push- UND Tab-Wechsel.
            enterTransition = { fadeIn(tween(120)) },
            exitTransition = { fadeOut(tween(120)) },
            popEnterTransition = { fadeIn(tween(120)) },
            popExitTransition = { fadeOut(tween(120)) },
        ) {
            // --- Bottom-Nav-Tabs -------------------------------------------------
            composable<Home> {
                val viewModel: HomeViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                HomeScreen(
                    state = state,
                    onOpenSettings = { navController.navigate(Einstellungen) },
                    onStartSession = { navController.navigate(SessionErstellen) },
                    // Boulder zur AKTIVEN Session hinzufügen (echte sessionId aus dem ViewModel).
                    // Ohne aktive Session tut der Klick nichts (die Kachel erscheint dann ohnehin nicht).
                    onAddBoulderToActiveSession = {
                        state.activeSessionId?.let { navController.navigate(RouteHinzufuegen(sessionId = it)) }
                    },
                    // BoulderUebersicht ist laut Design die "zweite Ansicht des Sessions-Tabs"
                    // (Variante A, BottomNav bleibt sichtbar). Deshalb wie ein Tab-Wechsel
                    // navigieren, NICHT als einfacher Push über Home. Ein einfacher navigate()
                    // würde BoulderUebersicht direkt über Home legen; tippt man dann den Home-Tab,
                    // sichert popUpTo(Home){saveState} die Ansicht unter Home und restoreState
                    // stellt sie sofort wieder her → man käme nicht auf Home zurück (Nav-Quirk,
                    // siehe topLevelNavOptions).
                    onOpenAllBoulders = {
                        navController.navigate(BoulderUebersicht) { topLevelNavOptions() }
                    },
                    // Letzte Session öffnen (echte sessionId aus dem ViewModel).
                    onOpenLastSession = {
                        state.lastSession?.let { navController.navigate(Session(sessionId = it.sessionId)) }
                    },
                )
            }
            composable<Sessions> {
                val viewModel: SessionListViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                // Umschalten auf die Boulder-Ansicht desselben Tabs (Variante A):
                // ersetzt Sessions, statt es zu stapeln → Zurück-Umschalten bleibt möglich.
                val onOpenBoulderOverview = {
                    navController.navigate(BoulderUebersicht) {
                        popUpTo(Sessions) { inclusive = true }
                        launchSingleTop = true
                    }
                }
                if (isWideLayout) {
                    // Tablet (≥ 600 dp): Liste + Detail nebeneinander. Auswahl, Detail-Navigation
                    // und Zurück laufen über den Pane-Navigator im SessionsListDetail.
                    SessionsListDetail(
                        state = state,
                        onSetSortMode = viewModel::setSortMode,
                        onCreateSession = { navController.navigate(SessionErstellen) },
                        onOpenBoulderOverview = onOpenBoulderOverview,
                        onOpenSettings = { navController.navigate(Einstellungen) },
                        onOpenBoulder = { boulderId -> navController.navigate(BoulderDetail(boulderId)) },
                        onAddRoute = { sessionId -> navController.navigate(RouteHinzufuegen(sessionId)) },
                    )
                } else {
                    // Phone (Compact): unverändertes Push-Verhalten.
                    SessionUebersichtScreen(
                        state = state,
                        onSetSortMode = viewModel::setSortMode,
                        onOpenSession = { sessionId -> navController.navigate(Session(sessionId)) },
                        onCreateSession = { navController.navigate(SessionErstellen) },
                        onOpenBoulderOverview = onOpenBoulderOverview,
                        onOpenSettings = { navController.navigate(Einstellungen) },
                    )
                }
            }
            composable<Stats> {
                val viewModel: StatistikViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                StatistikScreen(
                    state = state,
                    // Auf breiten Layouts (Tablet) mehrspaltiges Dashboard.
                    wide = isWideLayout,
                    onOpenSettings = { navController.navigate(Einstellungen) },
                    onOpenHangboardHistorie = { navController.navigate(HangboardHistorie) },
                )
            }
            composable<Timer> {
                val viewModel: HangboardTimerViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                HangboardTimerScreen(
                    state = state,
                    onPlayPause = viewModel::onPlayPause,
                    onReset = viewModel::onReset,
                    onUpdateConfig = viewModel::updateConfig,
                    onSavePreset = viewModel::savePreset,
                    onDeletePreset = viewModel::deletePreset,
                )
            }

            // --- Push-Ziele ohne Argument ---------------------------------------
            composable<Einstellungen> {
                val viewModel: EinstellungenViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val exportMessage by viewModel.exportMessage.collectAsStateWithLifecycle()
                EinstellungenScreen(
                    state = state,
                    // Label-basiertes Custom-Grading-System anlegen (Farbe hängt an der Route).
                    onCreateGradeSystem = viewModel::createGradeSystem,
                    onDeleteGradeSystem = viewModel::deleteGradeSystem,
                    onSelectGradeSystem = viewModel::selectGradeSystem,
                    onExportSessions = viewModel::exportSessions,
                    onSetDarkMode = viewModel::setDarkMode,
                    onSetHapticFeedback = viewModel::setHapticFeedback,
                    onSetUserName = viewModel::setUserName,
                    exportMessage = exportMessage,
                    onExportMessageShown = viewModel::consumeExportMessage,
                    onOpenGhostClimber = { navController.navigate(GhostClimber) },
                    onOpenAbgleich = { navController.navigate(Abgleich) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable<Abgleich> {
                val viewModel: AbgleichViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                AbgleichScreen(
                    state = state,
                    abgabeName = viewModel.abgabeName(),
                    onGibAb = viewModel::gibAb,
                    onLieseEin = viewModel::lieseEin,
                    onEntscheideKonflikt = viewModel::entscheideKonflikt,
                    onUebernimmFremden = viewModel::uebernimmFremdenStand,
                    onBehalteEigenen = viewModel::behalteEigenenStand,
                    onAbbrechen = viewModel::brichAb,
                    onRueckgaengig = viewModel::machRueckgaengig,
                    onMeldungGesehen = viewModel::meldungGesehen,
                    onBack = { navController.popBackStack() },
                )
            }
            composable<GhostClimber> { entry ->
                val viewModel: GhostClimberViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                // Aufnahme aus dem Kamera-Screen; null, solange keine zurückkam.
                val aufnahmeUri by entry.savedStateHandle
                    .getStateFlow<String?>(KAMERA_ERGEBNIS, null)
                    .collectAsStateWithLifecycle()
                GhostClimberScreen(
                    state = state,
                    // Ghost vergleicht zwei Videos — ein Foto wäre hier sinnlos.
                    onOpenKamera = {
                        navController.navigate(
                            KameraAufnahme(CaptureAuftrag.NUR_VIDEO.name),
                        )
                    },
                    aufnahmeUri = aufnahmeUri,
                    onAufnahmeVerbraucht = { entry.savedStateHandle[KAMERA_ERGEBNIS] = null },
                    onSelectVideo = viewModel::onVideoSelected,
                    onAnalyze = viewModel::analyze,
                    onSelectAnchorFrame = viewModel::loadAnchorFrame,
                    onAddAnchor = viewModel::addAnchor,
                    onRemoveLastAnchor = viewModel::removeLastAnchor,
                    onComputeAlignment = viewModel::computeAlignment,
                    onAddPathPoint = viewModel::addPathPoint,
                    onRemoveLastPathPoint = viewModel::removeLastPathPoint,
                    onResetPath = viewModel::resetPathToSuggestion,
                    onConfirmPath = viewModel::confirmPath,
                    onSetViewMode = viewModel::setViewMode,
                    onSaveAnalysis = viewModel::saveAnalysis,
                    onRestoreAnalysis = viewModel::restoreAnalysis,
                    onDeleteAnalysis = viewModel::deleteAnalysis,
                    onBackToSelection = viewModel::backToSelection,
                    onBackToAnchors = viewModel::backToAnchors,
                    onBackToPath = viewModel::backToPath,
                    onBack = { navController.popBackStack() },
                )
            }
            composable<HangboardHistorie> {
                val viewModel: HangboardHistorieViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                HangboardHistorieScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                )
            }
            composable<SessionErstellen> {
                val viewModel: SessionErstellenViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                SessionErstellenScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    // Session anlegen (Room) und danach zur neuen aktiven Session navigieren;
                    // das Erstellen-Formular wird dabei vom Back-Stack genommen, damit Back von
                    // der Session direkt nach Home führt.
                    onCreateSession = { ort, gradeSystemId, notiz ->
                        viewModel.createSession(ort, gradeSystemId, notiz) { newSessionId ->
                            navController.navigate(Session(newSessionId)) {
                                popUpTo(SessionErstellen) { inclusive = true }
                            }
                        }
                    },
                )
            }
            composable<BoulderUebersicht> {
                val viewModel: BoulderUebersichtViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                // Dropdown "Sessions": zurück auf die Sessions-Ansicht desselben Tabs
                // (Variante A); ersetzt Boulder, statt es zu stapeln.
                val onOpenSessionOverview = {
                    navController.navigate(Sessions) {
                        popUpTo(BoulderUebersicht) { inclusive = true }
                        launchSingleTop = true
                    }
                }
                if (isWideLayout) {
                    // Tablet: Raster + Detail nebeneinander, wie im Sessions-Tab.
                    BoulderListDetail(
                        state = state,
                        onOpenSessionOverview = onOpenSessionOverview,
                        onOpenSettings = { navController.navigate(Einstellungen) },
                        onEditBoulder = { boulderId ->
                            navController.navigate(RouteHinzufuegen(boulderId = boulderId))
                        },
                    )
                } else {
                    // Phone (Compact): unverändertes Push-Verhalten.
                    BoulderUebersichtScreen(
                        state = state,
                        onOpenBoulder = { boulderId -> navController.navigate(BoulderDetail(boulderId)) },
                        onOpenSessionOverview = onOpenSessionOverview,
                        onOpenSettings = { navController.navigate(Einstellungen) },
                    )
                }
            }
            composable<RouteHinzufuegen> { entry ->
                val viewModel: RouteHinzufuegenViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val aufnahmeUri by entry.savedStateHandle
                    .getStateFlow<String?>(KAMERA_ERGEBNIS, null)
                    .collectAsStateWithLifecycle()
                RouteHinzufuegenScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    // Boulder-Medien dürfen Foto oder Video sein — der Nutzer entscheidet
                    // im Aufnahme-Screen.
                    onOpenKamera = {
                        navController.navigate(
                            KameraAufnahme(CaptureAuftrag.FOTO_UND_VIDEO.name),
                        )
                    },
                    aufnahmeUri = aufnahmeUri,
                    onAufnahmeVerbraucht = { entry.savedStateHandle[KAMERA_ERGEBNIS] = null },
                    // sessionId/boulderId liest das ViewModel selbst aus den Nav-Argumenten.
                    onSave = { input -> viewModel.save(input) { navController.popBackStack() } },
                )
            }
            composable<KameraAufnahme> { entry ->
                // Unbekannter Auftragsname (etwa nach einem Umbenennen) darf nicht crashen —
                // dann eben beides anbieten.
                val auftrag = runCatching {
                    CaptureAuftrag.valueOf(entry.toRoute<KameraAufnahme>().auftrag)
                }.getOrDefault(CaptureAuftrag.FOTO_UND_VIDEO)
                KameraScreen(
                    auftrag = auftrag,
                    onErgebnis = { uri ->
                        navController.previousBackStackEntry
                            ?.savedStateHandle?.set(KAMERA_ERGEBNIS, uri)
                        navController.popBackStack()
                    },
                    onBack = { navController.popBackStack() },
                )
            }

            // --- Push-Ziele mit Argument (typsicher aus toRoute()) --------------
            composable<BoulderDetail> { entry ->
                BoulderDetailRoute(
                    boulderId = entry.toRoute<BoulderDetail>().boulderId,
                    onBack = { navController.popBackStack() },
                    // Bearbeiten öffnet das Formular im Edit-Modus (vorbefüllt, aktualisiert die Route).
                    onEdit = { boulderId ->
                        navController.navigate(RouteHinzufuegen(boulderId = boulderId))
                    },
                )
            }
            composable<Session> { entry ->
                val args = entry.toRoute<Session>()
                // Dispatcher: entscheidet selbst aktiv (SessionDetail) vs. beendet (AlteSession).
                SessionRoute(
                    sessionId = args.sessionId,
                    onBack = { navController.popBackStack() },
                    onOpenBoulder = { boulderId -> navController.navigate(BoulderDetail(boulderId)) },
                    onAddRoute = { sessionId -> navController.navigate(RouteHinzufuegen(sessionId)) },
                )
            }
        }
    }

    // Die Navigation ist nur auf den 4 Tab-Zielen sichtbar; Push-Screens (Einstellungen,
    // Formulare, Kamera) füllen das Fenster allein.
    when {
        currentTab == null -> Box(modifier = Modifier.fillMaxSize()) { inhalt(Modifier.fillMaxSize()) }

        // Telefon: Leiste unten, wie bisher.
        breite == Breite.Kompakt -> Column(modifier = Modifier.fillMaxSize()) {
            inhalt(Modifier.weight(1f))
            Box(modifier = Modifier.navigationBarsPadding()) {
                BottomNav(
                    selectedTab = currentTab,
                    onTabSelect = { tab -> navController.navigateToTab(tab) },
                )
            }
        }

        /*
         * Ab 600 dp: Leiste an die Seite — und die Statusleiste bekommt ein eigenes,
         * durchgehendes Band darüber.
         *
         * Vorher reichten Rail und Inhalt jeweils bis ganz nach oben und trugen ihr eigenes
         * `statusBarsPadding()`. Die senkrechte Trennlinie der Rail lief damit **bis in die
         * Statusleiste hinein**, und die Systemsymbole des Geräts standen teils links davon,
         * teils rechts — die Uhr im 80 dp schmalen Rail-Streifen, dicht an der Kante. Das
         * sieht aus wie ein Zufall, weil es einer ist: die Statusleiste kennt unser Layout
         * nicht und verteilt ihre Symbole über die volle Fensterbreite.
         *
         * Jetzt liegt über beiden Spalten ein Band in Chrome-Farbe, so hoch wie die
         * Statusleiste. Darunter beginnen Rail und Inhalt gemeinsam; keine senkrechte Kante
         * schneidet mehr durch die Systemsymbole.
         *
         * `consumeWindowInsets` sorgt dafür, dass das Band nicht doppelt gezahlt wird: TopBar
         * und SideNav setzen selbst `statusBarsPadding()`, und das wird dadurch zu 0.
         * Am Telefon (Kompakt) ändert sich nichts — der Zweig oben ist unberührt.
         */
        else -> Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
                    .background(BoulderBuddy.colors.surfaceChrome),
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .consumeWindowInsets(WindowInsets.statusBars),
            ) {
                SideNav(
                    selectedTab = currentTab,
                    onTabSelect = { tab -> navController.navigateToTab(tab) },
                )
                inhalt(Modifier.weight(1f))
            }
        }
    }
}

// Ermittelt den aktiven BottomNavTab für das aktuelle Ziel — oder null, wenn das
// Ziel kein Top-Level-Tab ist (Push-Screens zeigen keine BottomNav).
private fun NavDestination?.toBottomNavTabOrNull(): BottomNavTab? {
    val dest = this ?: return null
    // Boulder-Übersicht ist die zweite Ansicht des Sessions-Tabs (Variante A): BottomNav
    // bleibt sichtbar, der Sessions-Tab bleibt markiert.
    if (dest.hasRoute(BoulderUebersicht::class)) return BottomNavTab.Sessions
    return topLevelDestinations.firstOrNull { dest.hasRoute(it.route::class) }?.tab
}

// Wechselt zu einem Top-Level-Tab, ohne den Back-Stack zu stapeln.
private fun NavController.navigateToTab(tab: BottomNavTab) {
    val destination = topLevelDestinations.first { it.tab == tab }
    navigate(destination.route) { topLevelNavOptions() }
}

// Gemeinsame Navigations-Optionen für die Top-Level-Ebene (Tabs + BoulderUebersicht als
// zweite Ansicht des Sessions-Tabs): popUpTo(Home) mit saveState + launchSingleTop +
// restoreState → kein Stacking, Tab-Zustände überleben den Wechsel.
//
// Wichtig gegen einen Nav-Quirk: popUpTo(Home){saveState} trägt Home in die interne
// backStackMap ein ("pinnt" es). Ohne diesen Pin würde ein späterer Home-Tab-Tap die gerade
// gesicherte Ansicht sofort per restoreState wiederherstellen (executePopOperations mappt den
// gesicherten State auf das popUpTo-Ziel Home, und navigate() restauriert ihn direkt wieder)
// — man käme dann nicht auf Home zurück. Alle Wege auf die Top-Level-Ebene müssen daher diese
// Optionen nutzen, sonst bleibt Home ungepinnt und die Falle schnappt zu.
private fun NavOptionsBuilder.topLevelNavOptions() {
    popUpTo(Home) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
