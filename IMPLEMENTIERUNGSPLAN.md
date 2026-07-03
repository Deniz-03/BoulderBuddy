# 🧗 BoulderBuddy — Implementierungsplan

> **Zweck:** Kleinschrittige Roadmap vom aktuellen UI-Prototyp zur lauffähigen MVP-App.
> Diese Datei ist Arbeitsgrundlage **für Deniz, Peer und für Claude** — beim Programmieren Schritt
> für Schritt abarbeiten, nach jeder Phase Häkchen setzen und bauen.
>
> Erstellt: 2026-07-03 · Abgabe-Ziel: ~2026-08-01

---

## 0. Ausgangslage (Stand 2026-07-03)

**Was existiert:**
- Vollständige **UI-Schicht** in Jetpack Compose: alle Screens + wiederverwendbare Komponenten
  (`ui/screens/`, `ui/components/`), Theme/Tokens (`ui/theme/`).
- Alle Screens laufen mit **hartkodierten Platzhalter-Daten** und `@Preview`.
- Navigations-**Gerüst nur als Kommentar** (`ui/navigation/Destinations.kt`, `AppNavigation.kt`).

**Was fehlt komplett:**
- ❌ `MainActivity` rendert nichts → App startet leer.
- ❌ Keine Navigation verdrahtet, Screens haben keine Navigations-Callbacks.
- ❌ Keine Datenschicht (kein Room, keine Entities/DAOs/Repository).
- ❌ Keine ViewModels.
- ❌ Gradle: nur Compose + Navigation-Compose. Room / Hilt / Coil / ViewModel-Compose /
  kotlinx-serialization **nicht** eingebunden (obwohl im Tech-Stack-Doc „entschieden").

**Getroffene Architektur-Entscheidungen (2026-07-03):**
| Thema | Entscheidung |
|-------|--------------|
| Reihenfolge | **Navigation zuerst** → klickbarer Prototyp mit Platzhaltern, danach Datenschicht |
| Routen-Stil | **Type-safe `@Serializable`** (Nav-Compose 2.9), braucht kotlinx-serialization |
| Bottom-Nav | **Ins Navigations-Gerüst hochziehen** (gemeinsames Scaffold), Tab-Screens rendern sie nicht mehr selbst |
| DI | **Hilt** (wie im Tech-Stack-Doc) |

---

## Arbeitsweise (bitte einhalten)

1. **Eine Phase = ein Branch/Commit-Block.** Nach jeder Phase `./gradlew assembleDebug` grün, dann committen.
2. **Placeholder-Daten bleiben,** bis die jeweilige Datenschicht darunter steht (Phase 3–6). Nicht vorzeitig entfernen.
3. **Keine großen Sprünge:** immer nur die Dateien der aktuellen Teilaufgabe anfassen.
4. Nach Erledigung einer Aufgabe hier das `[ ]` → `[x]` setzen (Datei ist die Single Source of Truth für den Fortschritt).
5. Doku-Kultur beibehalten: relevante Entscheidungen in `04 – Entwicklung/` (Obsidian) nachtragen.

---

## Phase 0 — Dependencies & Gradle-Setup

> Ziel: Alle im Tech-Stack entschiedenen Libs einbinden, damit die folgenden Phasen bauen.
> ⚠️ Versionen sind Vorschläge — beim Sync auf Kompatibilität mit Kotlin `2.2.10` / Compose-BOM `2026.02.01` prüfen.

- [x] **0.1** `gradle/libs.versions.toml` — Versionen ergänzt: `ksp = 2.2.10-2.0.2`, `hilt = 2.60`, `hiltNavigationCompose = 1.2.0`, `room = 2.8.1`, `coil = 3.3.0`, `lifecycleViewmodelCompose = 2.10.0`, `kotlinxSerializationJson = 1.9.0`.
- [x] **0.2** `libs.versions.toml` — `[libraries]` ergänzt: `androidx-room-runtime`, `androidx-room-ktx`, `androidx-room-compiler`, `hilt-android`, `hilt-compiler`, `androidx-hilt-navigation-compose`, `androidx-lifecycle-viewmodel-compose`, `coil-compose`, `coil-network-okhttp`, `kotlinx-serialization-json`.
- [x] **0.3** `libs.versions.toml` — `[plugins]` ergänzt: `ksp`, `hilt`, `kotlin-serialization`.
- [x] **0.4** Root-`build.gradle.kts` — `ksp`, `hilt`, `kotlin-serialization` als `apply false` registriert.
- [x] **0.5** `app/build.gradle.kts` — `plugins { }` um `ksp`, `hilt`, `kotlin.serialization` erweitert.
- [x] **0.6** `app/build.gradle.kts` — `dependencies { }` um room-runtime/ktx + `ksp(room-compiler)`, hilt-android + `ksp(hilt-compiler)`, hilt-navigation-compose, lifecycle-viewmodel-compose, coil-compose/-network-okhttp, kotlinx-serialization-json erweitert.
- [x] **0.7** Room-Schema-Export aktiviert: `ksp { arg("room.schemaLocation", "$projectDir/schemas") }`.
- **✅ Done:** `./gradlew assembleDebug` grün (2026-07-03). **Zwei AGP-9-bedingte Abweichungen vom Plan nötig:**
  1. **Hilt `2.57.2` → `2.60`.** Ältere Hilt-Gradle-Plugins suchen die in AGP 9 entfernte `BaseExtension`-API → `Android BaseExtension not found`. 2.60 ist AGP-9-kompatibel.
  2. **`android.disallowKotlinSourceSets=false` in `gradle.properties`.** AGP 9 nutzt Built-in-Kotlin (kein `kotlin-android`-Plugin); KSP registriert generierte Sources über die alte `kotlin.sourceSets`-DSL, was sonst blockiert wird (Flag ist noch „experimental").

---

## Phase 1 — Navigations-Gerüst (mit Platzhalter-Daten)

> Ziel: App startet, ist klickbar. Noch keine echten Daten. Reihenfolge bewusst vor der Datenschicht.

- [x] **1.1** `ui/navigation/Destinations.kt` — Kommentar ersetzt durch **type-safe Routen**:
  - Ein Objekt/`@Serializable`-Datentyp pro Ziel.
  - Ohne Argument: `Home`, `Sessions`, `Stats`, `Timer`, `Einstellungen`, `SessionErstellen`, `BoulderUebersicht`.
  - Mit Argument: `BoulderDetail(boulderId: Int)`, `Session(sessionId: Int)`, `RouteHinzufuegen(sessionId: Int?)`.
  - Bottom-Nav-Tabs (`Home/Sessions/Stats/Timer`) zusätzlich als `topLevelDestinations`-Liste (Route ↔ `BottomNavTab`, Icon/Label liefert der Enum). `GhostClimber` = Post-MVP, **nicht** aufgenommen.
- [x] **1.2** `ui/navigation/AppNavigation.kt` — `NavHost` implementiert:
  - `rememberNavController()`, `startDestination = Home`.
  - Je `composable<Route>` mit dem passenden Screen.
  - Argument-Routen: `boulderId` / `sessionId` typsicher aus `toRoute()` gelesen und weitergereicht.
  - `Session(sessionId)` → ruft `SessionRoute(sessionId)` (Dispatcher bleibt).
- [x] **1.3** **Bottom-Nav hochgezogen:** In `AppNavigation` eine gemeinsame `BottomNav`, die nur bei den 4 Tab-Zielen sichtbar ist (aktueller Tab aus `navController.currentBackStackEntryAsState()` via `hasRoute`). `onTabSelect` → `navigateToTab()` mit `launchSingleTop = true`, `restoreState`, `popUpTo(Home) { saveState = true }`. **Interne `BottomNav` aus den 4 Tab-Screens (`HomeScreen`, `SessionUebersichtScreen`, `StatistikScreen`, `HangboardTimerScreen`) entfernt** (sonst doppelte Nav) — die Screen-Entfernung aus Phase 2.1–2.4 ist damit hier bereits erledigt.
- [x] **1.4** `MainActivity.kt` — ruft `setContent { BoulderBuddyTheme { AppNavigation() } }` auf; alte TODO-Kommentare/leere Box entfernt.
- **✅ Done:** `./gradlew assembleDebug` grün (2026-07-03). App startet auf Home, Bottom-Nav wechselt zwischen Home/Sessions/Stats/Timer (saveState/restoreState/launchSingleTop → kein Tab-Stacking). Push-Navigation folgt in Phase 2. Hinweis: `HangboardTimerScreen` bekommt vorerst einen Platzhalter-`HangboardTimerUiState` aus dem NavHost (ViewModel erst Phase 6.9).

---

## Phase 2 — Screens mit Navigations-Callbacks ausstatten

> Ziel: Alle `/* TODO: Navigation ... */` in echten Push-Navigation umwandeln. Ein Screen nach dem anderen.
> Muster: Screen bekommt Lambda-Parameter (`onOpenX: () -> Unit`), NavHost übergibt `{ navController.navigate(...) }`.
> **Bottom-Nav aus den 4 Tab-Screens entfernen** (wird jetzt vom Scaffold in Phase 1.3 gestellt).

- [x] **2.1** `HomeScreen` — Params `onOpenSettings`, `onStartSession`, `onAddBoulderToActiveSession`, `onOpenAllBoulders`, `onOpenLastSession`. Alle als `() -> Unit` (Home kennt vor dem ViewModel keine echten IDs → die konkrete Ziel-`sessionId` liefert der NavHost). Eigene `BottomNav` war bereits in Phase 1.3 entfernt.
- [x] **2.2** `SessionUebersichtScreen` — `onOpenSession(sessionId)`, `onOpenBoulderOverview` (Dropdown→Boulder), `onOpenSettings`. Platzhalter-Sessions haben jetzt `id` (Konvention: id 0 = aktiv). **`onCreateSession` weggelassen:** der Screen hat keinen Erstellen-Button — Session-Erstellung läuft über Home „Session starten".
- [x] **2.3** `StatistikScreen` — nur `onOpenSettings` (keine weiteren Push-Ziele).
- [x] **2.4** `HangboardTimerScreen` — keine Screen-Änderung nötig (BottomNav schon in 1.3 weg, `onSettings`-Param existiert); im NavHost `onSettings` → Einstellungen verdrahtet.
- [x] **2.5** `EinstellungenScreen` — `onBack`.
- [x] **2.6** `SessionErstellenScreen` — `onBack`, `onSessionCreated(sessionId)`; Button ruft `onSessionCreated(0)` (Platzhalter-ID der neuen aktiven Session), NavHost nimmt das Formular per `popUpTo(inclusive)` vom Back-Stack.
- [x] **2.7** `BoulderUebersichtScreen` — `onOpenBoulder(boulderId)`, `onOpenSessionOverview` (Dropdown→Sessions), `onOpenSettings`. **Kein `onBack`** (die `UebersichtTopBar` hat keinen Zurück-Pfeil → System-Back genügt). **Übrig gebliebene, funktionslose `BottomNav` entfernt** (Push-Ziel, kein Top-Level-Tab). Platzhalter-Boulder haben jetzt `id`.
- [x] **2.8** `RouteHinzufuegenScreen` — `onBack`, `onSaved`, plus `sessionId: Int?` aus der Route (`RouteHinzufuegen(sessionId)`). Offene Frage (Boulder ohne aktive Session?) bleibt via nullable `sessionId` bewusst offen.
- [x] **2.9** `BoulderDetailScreen` — `onBack`, `onEdit` (öffnet vorerst „Boulder hinzufügen" als Platzhalter, Edit-Modus folgt Phase 6.5/6.7).
- [x] **2.10** `SessionDetailScreen` / `AlteSessionScreen` — `onBack`, `onOpenBoulder(boulderId)`, `onAddRoute` (nur aktiv, von `SessionRoute` an die `sessionId` gebunden). `SessionRoute` reicht die Callbacks durch. **`BoulderListRow` um optionales `onClick` erweitert** (analog `RouteCard`), damit auch read-only-Boulder öffenbar sind.
- [x] **2.11** NavHost (`AppNavigation`) — alle Callbacks verdrahtet; Tab-Wechsel via `navigateToTab` (kein Stacking), Push-Ziele via `navigate`, Zurück via `popBackStack`.
- **✅ Done:** `./gradlew assembleDebug` grün (2026-07-03). Klick-Navigation durch alle Screens (Platzhalter-Daten), Back überall. Platzhalter-ID-Konvention: `sessionId` 0 = aktive Session (→ `SessionDetailScreen`), sonst abgeschlossen (→ `AlteSessionScreen`).

---

## Phase 3 — Datenschicht: Room

> Ziel: DB gemäß dokumentiertem Schema (`03 – Architektur & Tech/Datenbankschema.md`). Package `data/`.

- [x] **3.1** `data/model/RouteStatus.kt` — `RouteStatus { OPEN, SENT, PROJECT, SKIP }`. UI-Enum `BoulderStatus` (TOP/FLASH/PROJEKT) **bewusst nicht gedoppelt** — sie ist eine abgeleitete Darstellung (z.B. Flash = SENT mit `attempts == 1`); das Mapping folgt in Phase 6.
- [x] **3.2** `data/db/entity/` — `@Entity`-Klassen 1:1 zum Schema:
  - `GymEntity` (id, name, location?)
  - `GradeSystemEntity` (id, gymId FK, name)
  - `GradeEntity` (id, systemId FK, label, color, order)
  - `SessionEntity` (id, gymId FK, date: Long, durationMin?, notes?, **endedAt: Long?** ← Aktiv-Marker)
  - `RouteEntity` (id, sessionId FK, gradeId FK?, attempts, status, mediaUri?, notes?)
  - `HangboardTemplateEntity` (id, name, sets, hangSec, restSec, repRestSec)
  - Bei allen FKs `@ForeignKey` + `@Index` setzen.
- [x] **3.3** `data/db/Converters.kt` — TypeConverter für `RouteStatus` ↔ String (Datum bleibt `Long`, kein Converter nötig).
- [x] **3.4** `data/db/dao/` — je ein DAO mit den für die Screens nötigen Queries (`Flow<List<…>>` für reaktive Listen):
  - `SessionDao` (insert, update/endSession, `getById`, `observeAll`, `observeActive` = `WHERE endedAt IS NULL`)
  - `RouteDao` (insert, update, `observeBySession`, `getById`, `observeAll` für Boulder-Übersicht)
  - `GymDao`, `GradeSystemDao`, `GradeDao`, `HangboardTemplateDao`.
- [x] **3.5** `data/db/BoulderBuddyDatabase.kt` — `@Database(entities=[...], version=1)`, `@TypeConverters`, abstrakte DAO-Getter.
- [x] **3.6** (Optional) `data/db/SeedData.kt` — `RoomDatabase.Callback`, das in `onCreate` ein Beispiel-Gym + Gradsystem (5 Farbgrade) + eine aktive Session einfügt. Registrierung beim DB-Aufbau folgt in Phase 4 (Hilt).
- **✅ Done:** `./gradlew assembleDebug` grün (2026-07-03). Room-Codegen (`kspDebugKotlin`) fehlerfrei, Schema nach `app/schemas/…/1.json` exportiert. **Abweichungen vom Plan:** (1) `GradeEntity.order` ist als Spalte `sortOrder` abgelegt (`@ColumnInfo`), da `order` ein SQL-Schlüsselwort ist. (2) FK-`onDelete`: GradeSystem/Grade/Route→Session = `CASCADE`, Route→Grade = `SET_NULL` (Route bleibt bei gelöschtem Grad erhalten, `gradeId` ist ohnehin nullable).

---

## Phase 4 — Dependency Injection: Hilt

- [x] **4.1** `BoulderBuddyApp.kt` — `@HiltAndroidApp`-Application-Klasse.
- [x] **4.2** `AndroidManifest.xml` — `android:name=".BoulderBuddyApp"` eingetragen.
- [x] **4.3** `MainActivity.kt` — `@AndroidEntryPoint` annotiert.
- [x] **4.4** `di/DatabaseModule.kt` — `@Module @InstallIn(SingletonComponent::class)`: provide `BoulderBuddyDatabase` (Room.databaseBuilder mit `SeedData`-Callback aus Phase 3.6) + jedes DAO.
- [ ] **4.5** `di/RepositoryModule.kt` — Repository-Interfaces an Implementierungen binden (`@Binds`). **Auf Phase 5 verschoben:** noch keine Repositories zum Binden; leeres Modul wäre nutzlos. Wird zusammen mit den Repos in Phase 5 erstellt.
- **✅ Done:** `./gradlew assembleDebug` grün (2026-07-03). Hilt-Codegen (`hiltAggregateDepsDebug`/`hiltJavaCompileDebug`) fehlerfrei, DI-Graph baut. DB + alle 6 DAOs app-weit als Singletons injizierbar. 4.5 folgt in Phase 5.

---

## Phase 5 — Repository-Schicht

> Ziel: DAOs hinter Repositories kapseln (MVVM + Repository laut Tech-Stack). Package `data/repository/`.

- [ ] **5.1** `SessionRepository` (Interface + Impl) — aktive Session beobachten, anlegen, beenden (`endedAt` setzen), einzelne laden.
- [ ] **5.2** `RouteRepository` — Routen einer Session, alle Routen, anlegen, aktualisieren, einzelne laden.
- [ ] **5.3** `GymRepository` / `GradeRepository` — Gyms + Gradsysteme/Grades (für Custom-Gradsystem & Session-Erstellung).
- [ ] **5.4** `HangboardRepository` — Timer-Templates (Should-Have; kann zunächst statisch/leer bleiben).
- [ ] **5.5** (Optional) `domain/`-Mapper: Entity → UI-Modell, falls UI-Datentypen von den Entities abweichen.
- **✅ Done wenn:** Repositories per Hilt injizierbar, kompiliert.

---

## Phase 6 — ViewModels + Screens an echte Daten anbinden

> Ziel: Platzhalter durch `StateFlow` aus ViewModels ersetzen — **Screen für Screen**. Muster:
> `@HiltViewModel class XViewModel @Inject constructor(repo) : ViewModel()` mit `uiState: StateFlow<XUiState>`;
> im NavHost per `hiltViewModel()` holen und `collectAsStateWithLifecycle()`.

- [ ] **6.1** `HomeViewModel` + `HomeScreen` — Stats (Sessions/Woche, Tops, Top-Grade), aktive Session (`hasActiveSession`), letzte Session. Platzhalter in `HomeScreen.kt` entfernen.
- [ ] **6.2** `SessionListViewModel` + `SessionUebersichtScreen` — Session-Liste aus Room.
- [ ] **6.3** `SessionErstellenViewModel` + `SessionErstellenScreen` — Gym/Gradsystem wählen, Session anlegen → `endedAt = null`.
- [ ] **6.4** `SessionViewModel` + `SessionRoute`/`SessionDetailScreen`/`AlteSessionScreen` — echte Session laden; `ladeSessionMeta`-Platzhalter durch Repository ersetzen; Session beenden (setzt `endedAt`).
- [ ] **6.5** `RouteHinzufuegenViewModel` + `RouteHinzufuegenScreen` — Route mit Foto (Coil/PhotoPicker), Grad, Versuche, Status, Notiz zur `sessionId` speichern.
- [ ] **6.6** `BoulderUebersichtViewModel` + `BoulderUebersichtScreen` — alle Routen, Filter (FilterChip).
- [ ] **6.7** `BoulderDetailViewModel` + `BoulderDetailScreen` — einzelnen Boulder per `boulderId` laden.
- [ ] **6.8** `StatistikViewModel` + `StatistikScreen` — Grade-Verteilung (BarChart) + Activity-Heatmap aus echten Sessions/Routen.
- [ ] **6.9** `HangboardTimerViewModel` + `HangboardTimerScreen` — Timer-Logik (Sets/Hang/Rest), optional Templates.
- [ ] **6.10** `EinstellungenScreen` — Gym-/Gradsystem-CRUD (Custom-Gradsystem, MVP-Must-Have) an Repository anbinden.
- [ ] **6.11** Coil einrichten (`AsyncImage`) für `RouteEntity.mediaUri` in Detail-/Übersicht-Screens.
- **✅ Done wenn:** Kein hartkodierter Platzhalter mehr in den Screens; Daten überleben App-Neustart (Room).

---

## Phase 7 — Loose Enden / Post-MVP (nach lauffähigem MVP)

> Erst angehen, wenn Phase 0–6 stehen. Reihenfolge nach Requirements-Priorität.

- [ ] **7.1** Tablet-Layout: `WindowSizeClass` + `ListDetailPaneScaffold` (Sessions-Liste + Detail).
- [ ] **7.2** Wear-OS-Modul (`wear/`): Hangboard-Timer, Session-Log, Vibrations-Feedback, Data Layer API.
- [ ] **7.3** Should-Haves: Video-Support, Session-Export, Timer-Voreinstellungen.
- [ ] **7.4** Nice-to-Haves: Dark Mode, Speech-to-Text-Notizen, Homescreen-Widget.
- [ ] **7.5** Ghost Climber (bewusst Post-MVP) — erst wenn Kern steht.
- [ ] **7.6** Tests: Repository-/DAO-Tests (Room in-memory), ein paar Compose-UI-Tests für Doku.

---

## Offene Fragen, die unterwegs geklärt werden müssen

- **RouteHinzufuegen:** braucht es zwingend `sessionId`, oder soll man Boulder auch ohne aktive Session anlegen können? (Aktuell Annahme: mit `sessionId`.)
- **Charting:** eigene Compose-Charts (`BarChart.kt` existiert schon) beibehalten oder Vico/MPAndroidChart? → Vorschlag: eigene behalten, keine neue Dependency.
- **User-Profil:** `HomeScreen` zeigt „Hallo, Deniz". Woher kommt der Name? Eigene `UserPrefs` (DataStore) nötig oder fest? → für MVP evtl. fest / einfache Einstellung.
- **Seed-Daten:** ja/nein für die Doku-Screenshots (empfohlen: ja).

---

*Fortschritt bitte direkt in dieser Datei pflegen (Häkchen). Bei Architektur-Änderungen: kurze Notiz in `04 – Entwicklung/` im Obsidian-Vault.*
