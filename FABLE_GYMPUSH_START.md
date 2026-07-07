# 🏁 Fable-5 START-KONTEXT — Gym-Näherungs-Push (Variante B)

> **Lies NUR dieses Dokument + [Anhang C](PHASE7_PLAN.md#anhang-c--fable-5-auftrag-gym-naeherungs-push-variante-b) in [`PHASE7_PLAN.md`](PHASE7_PLAN.md).**
> Es fasst alle Repo-Fakten und finalen Entscheidungen (Stand 2026-07-07) zusammen, damit du
> **nicht explorieren musst**. Branch: **`PushNot`** (bereits ausgecheckt). DB steht auf **v5**.

---

## 0. Ziel in einem Satz

**Variante B (bewusst gewählt gegen ein automatisches Gym-Discovery):** Die manuelle Gym-Liste
bleibt. Die App lernt, **wann und wie oft** der Nutzer in welcher Halle war, und schickt eine
**Push-Benachrichtigung „Session starten?"**, wenn erkannt wird, dass er sich **in der Nähe einer
hinterlegten Halle** befindet — auch bei geschlossener App.

**Nicht-Ziele:** kein Karten-/POI-Discovery, kein Auto-Anlegen von Gyms, kein Server/Cloud, keine
Freunde-/Social-Features, keine Live-Tracking-Historie der Bewegung. Nur: Koordinaten je Gym →
Geofence → gedrosselte, musterbewusste Erinnerung.

---

## 1. Finale Entscheidungen (mit Deniz geklärt, verbindlich — 2026-07-07)

| # | Frage | Entscheidung |
|---|-------|-------------|
| 1 | **Woher die Gym-Koordinaten?** | **Standort-Button im Gym-Editor.** „Aktuellen Standort übernehmen", während der Nutzer an der Halle steht (`FusedLocationProviderClient.getCurrentLocation`). **Kein** Maps-SDK, **kein** API-Key, **kein** Geocoding, **keine** manuellen lat/lng-Felder als Primärweg. |
| 2 | **Erkennungs-Mechanismus?** | **Android Geofencing-API + Hintergrund-Standort.** Batterieschonend, funktioniert bei geschlossener App. Braucht `ACCESS_BACKGROUND_LOCATION`-Runtime-Flow (Nicht nur-Foreground-Polling). |
| 3 | **Wann pusht die App?** | **Smart: gedrosselt + musterbasiert.** Max **1×/Tag pro Gym**, Cooldown, **unterdrückt wenn eine Session bereits aktiv ist** oder kürzlich beendet wurde. Gelernte Häufigkeit priorisiert/dämpft. **Nicht** bei jeder Geofence-Ankunft. |
| 4 | **Was ist ein „Besuch" fürs Lernen?** | **Geofence-Ankünfte UND gestartete Sessions.** Physische Ankunft wird geloggt (auch ohne Session-Start); Session-Start zählt ebenfalls. Reichstes Signal. |

---

## 2. Der wichtigste Repo-Fakt vorweg: **es gibt KEINE Gym-Verwaltungs-UI**

Heute werden Gyms **implizit „find-or-create by name"** angelegt — es gibt **keinen Screen**, in dem
man eine Halle bearbeitet, geschweige denn Koordinaten setzt:

- [`SessionErstellenViewModel.createSession()`](app/src/main/java/com/boulderbuddy/ui/viewmodel/SessionErstellenViewModel.kt:54)
  sucht per Name (case-insensitive) oder legt `GymEntity(name = …)` neu an.
- [`EinstellungenViewModel.createGradeSystem()`](app/src/main/java/com/boulderbuddy/ui/viewmodel/EinstellungenViewModel.kt:124)
  legt als Fallback eine `GymEntity(name = "Meine Halle")` an.
- `GymEntity` hat aktuell **nur** `id, name, location: String?` — **`location` ist Freitext, keine
  Koordinaten** ([`GymEntity.kt`](app/src/main/java/com/boulderbuddy/data/db/entity/GymEntity.kt)).

**→ Ein Gym-Verwaltungs-Screen (Liste + Editor) ist Teil dieses Auftrags** (M1). Ohne ihn kann der
Nutzer keine Koordinaten hinterlegen und das Feature hat keine Datengrundlage.

---

## 3. Datenmodell (M0) — DB v5 → v6, **destruktiv**

**DB-Fakten** ([`BoulderBuddyDatabase.kt`](app/src/main/java/com/boulderbuddy/data/db/BoulderBuddyDatabase.kt),
[`DatabaseModule.kt`](app/src/main/java/com/boulderbuddy/di/DatabaseModule.kt)):
- Aktuelle `version = 5`. Provider nutzt **`.fallbackToDestructiveMigration(dropAllTables = true)`**.
- **→ KEINE handgeschriebene `Migration`.** Entities ergänzen, `version = 5` → `6`, DAO-Getter +
  `@Provides` ergänzen. `exportSchema = true` ist an → neues Schema landet in `app/schemas/6.json`.

**Persistenz-Muster (überall im Projekt gleich):** Entity in `data/db/entity/`, DAO in
`data/db/dao/`, DAO-Getter in `BoulderBuddyDatabase` + `@Provides` in `DatabaseModule`,
Repository-Interface+Impl in `data/repository/`, `@Binds` in
[`di/RepositoryModule.kt`](app/src/main/java/com/boulderbuddy/di/RepositoryModule.kt).

### 3.1 `GymEntity` erweitern
```kotlin
@Entity(tableName = "gym")
data class GymEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val location: String? = null,          // Freitext bleibt (Adresse o.Ä.)
    val latitude: Double? = null,          // NEU — null = nicht geofenced
    val longitude: Double? = null,         // NEU
    val geofenceRadiusMeters: Int = 150,   // NEU — Default 150 m, im Editor anpassbar
    val proximityAlertsEnabled: Boolean = true, // NEU — pro Gym abschaltbar
)
```
Ein Gym **ohne** lat/lng wird schlicht **nicht** als Geofence registriert (kein Zwang).

### 3.2 Neue `GymVisitEntity` (Besuchs-Log)
```kotlin
@Entity(
    tableName = "gym_visit",
    foreignKeys = [ForeignKey(entity = GymEntity::class, parentColumns = ["id"],
        childColumns = ["gymId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("gymId")],
)
data class GymVisitEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val gymId: Int,
    val timestamp: Long,        // epoch millis (Ankunft bzw. Session-Start)
    val source: String,         // "GEOFENCE" | "SESSION" (String, kein Enum-Converter nötig)
)
```
- **Geofence-Ankunft** (DWELL, s. §5) → `GymVisitEntity(source = "GEOFENCE")`.
- **Session-Start** → beim `sessionRepository.create(...)` zusätzlich einen
  `GymVisitEntity(source = "SESSION")` schreiben. **Dedupe:** wenn für dasselbe Gym schon ein Besuch
  am selben **Kalendertag** existiert, keinen zweiten anlegen (ein Besuch = ein Tag).
- **Kein** BLOB, **keine** JSON-Datei nötig — reine Skalar-Spalten.

### 3.3 Häufigkeits-/Muster-Modell (reine Kotlin-Logik, JVM-testbar)
Ein `GymVisitStats`-Wert je Gym, berechnet im Repository aus den Roh-Besuchen:
- `totalVisits`, `lastVisit: Long?`
- `visitsByDayOfWeek: Map<DayOfWeek, Int>`, `visitsByHour: Map<Int, Int>` (Histogramme)
- `isTypicalSlot(now): Boolean` — trifft die aktuelle Ankunft ein „übliches" Zeitfenster
  (z.B. Wochentag+Stunde gehört zu den Top-Slots)? Nutzt Push-Politik M4 zum **Priorisieren/Dämpfen**,
  ist aber **kein** hartes Gate (erste Ankünfte an einem neuen Gym sollen auch pushen).

Die Histogramm-/`isTypicalSlot`-Funktionen als **pure functions** halten (Eingabe:
`List<GymVisitEntity>` + `now`), damit sie ohne Android in JVM-Unit-Tests laufen (Muster: bestehende
`app/src/test/…`-Tests, Truth + coroutines-test schon im Katalog).

---

## 4. Standort-Erfassung im Gym-Editor (M1)

- Dependency: **`play-services-location`** (in Katalog eintragen, s. §8). Liefert
  `FusedLocationProviderClient` und die Geofencing-API aus **einem** Artefakt.
- Button „**Aktuellen Standort übernehmen**": `getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, …)`
  (moderner als das deprecated `lastLocation`), Ergebnis in die Editor-Felder `latitude/longitude`.
- Braucht **Foreground**-Location-Permission: `ACCESS_FINE_LOCATION` (+ `ACCESS_COARSE_LOCATION`).
  Runtime-Flow mit `rememberLauncherForActivityResult(RequestMultiplePermissions)`; bei Ablehnung
  klare Rationale + Fallback (manuelle lat/lng-Eingabe als Notnagel anbieten, nicht als Primärweg).
- Editor zeigt erfasste Koordinaten (+ optional Radius-Slider, Default 150 m) und einen
  Pro-Gym-Toggle „Erinnerungen aktiv".

---

## 5. Geofencing-Infrastruktur (M2)

- **`GeofenceManager`** (Singleton, `@Inject`): registriert für **jedes Gym mit lat/lng** einen
  `Geofence` via `GeofencingClient.addGeofences(...)`.
  - `requestId = gymId.toString()` (zum Rückmapping im Receiver).
  - Trigger: **`GEOFENCE_TRANSITION_DWELL`** mit `setLoiteringDelay(~120_000)` (nicht purer ENTER) —
    verhindert Drive-by-Fehlauslöser; pusht erst, wenn der Nutzer wirklich **bleibt**.
  - `setInitialTriggerTypes(INITIAL_TRIGGER_DWELL)`.
- **`GeofenceBroadcastReceiver`** (`BroadcastReceiver`, im Manifest, `exported=false`): empfängt das
  `PendingIntent`, liest `GeofencingEvent`, mappt `requestId` → Gym, schreibt `GymVisitEntity` und
  ruft die Push-Politik (M4) auf. Für DB-Zugriff aus dem Receiver: `EntryPointAccessors` /
  `goAsync()` (Hilt hat keinen `@AndroidEntryPoint`-Receiver-Automatismus für Room hier — Muster:
  Repository via Hilt-EntryPoint holen).
- **Geofences überleben keinen Reboot** → `RECEIVE_BOOT_COMPLETED`-Receiver, der `GeofenceManager`
  neu registrieren lässt. Ebenso **neu registrieren, wenn Gym-Koordinaten sich ändern** (im Repo-Write
  triggern) und wenn der Master-Toggle umgelegt wird.
- **Hintergrund-Standort:** `ACCESS_BACKGROUND_LOCATION` ist ab **API 29** eine **separate** Runtime-
  Permission; ab **API 30** nur noch über Settings-Redirect erteilbar (nicht im normalen Dialog).
  `minSdk = 26` → auf 26–28 deckt `ACCESS_FINE_LOCATION` den Hintergrund ab. **Beide Pfade behandeln.**
  Reihenfolge: erst Foreground gewähren lassen, **dann** Background anfragen (Android erzwingt das).
- **Android-Limit** 100 Geofences/App — für eine handverlesene Gym-Liste unkritisch.

---

## 6. Notification + Smart-Politik (M4)

- **`NotificationChannel`** `"gym_proximity"` (einmalig anlegen; `POST_NOTIFICATIONS` ist ab **API 33**
  Runtime-Permission → beim Feature-Opt-in mit anfragen).
- Notification: „**Bist du im <Gym-Name>? Session starten?**" mit Action → öffnet die App und
  **springt in `SessionErstellen`, vorbefüllt mit dem Gym** (s. §7 Deep-Link).
- **Politik (`ProximityNotificationPolicy`, pure Kotlin, JVM-testbar):** pusht nur wenn
  1. `proximityAlertsEnabled` global (Master-Toggle) **und** pro Gym `true`,
  2. **keine aktive Session** läuft (`SessionRepository.observeActive()` != null → unterdrücken),
  3. **Cooldown** ok: kein Push für dieses Gym in den letzten 24 h (bzw. seit letztem gemerkten Push),
  4. und die letzte Session an diesem Gym nicht gerade eben endete (Nach-Session-Ruhe).
     → `isTypicalSlot` moduliert, ist aber kein hartes Gate für neue Gyms (§3.3).
- **Cooldown-State persistieren:** kleinstmöglich — pro Gym `lastNotifiedAt: Long` in DataStore
  (Muster: [`SettingsRepository`](app/src/main/java/com/boulderbuddy/data/settings/SettingsRepository.kt)
  nutzt `Preferences`-DataStore; Key pro Gym z.B. `long "gym_notified_<id>"`) **oder** eine Spalte
  `lastNotifiedAt` auf `GymEntity`. Entscheide dich für **eins** und dokumentiere warum.

---

## 7. Deep-Link „Session starten" (M4) — vorhandenes Muster nutzen

Es gibt bereits einen Intent-Extra-Deep-Link vom Homescreen-Widget — **exakt dasselbe Muster** für
die Notification verwenden:
- [`MainActivity`](app/src/main/java/com/boulderbuddy/MainActivity.kt:27) liest
  `intent.getStringExtra(WidgetIntent.EXTRA_NAV_TARGET)` und reicht `initialNavTarget` an
  `AppNavigation`.
- [`AppNavigation`](app/src/main/java/com/boulderbuddy/ui/navigation/AppNavigation.kt:75) springt per
  `LaunchedEffect` ins Ziel (`WidgetIntent.TARGET_NEW_SESSION` → `navigate(SessionErstellen)`).
- **Aufgabe:** analoges `EXTRA` für „neue Session an Gym X". Da `SessionErstellen` heute ein
  **argumentloses** `@Serializable object` ist ([`Destinations.kt`](app/src/main/java/com/boulderbuddy/ui/navigation/Destinations.kt:39)),
  erweitere es zu `data class SessionErstellen(val gymId: Int? = null)` und lass
  `SessionErstellenViewModel`/‑`Screen` das **Ort-Feld vorbefüllen** (Gym-Name via `gymRepository.getById`).
  Bestehende argumentlose Aufrufe (`navigate(SessionErstellen)`, Widget-Pfad) laufen dank Default
  `null` weiter.

---

## 8. Dependency & Berechtigungen — was einzutragen ist

**`gradle/libs.versions.toml`** (im `[versions]` + `[libraries]`, Stil wie die anderen Einträge):
```toml
playServicesLocation = "21.3.0"   # Geofencing + FusedLocation aus einem Artefakt
# ...
play-services-location = { group = "com.google.android.gms", name = "play-services-location", version.ref = "playServicesLocation" }
```
`app/build.gradle.kts`: `implementation(libs.play.services.location)`. **Kein** weiteres native
Dependency. (`play-services-wearable 19.0.0` ist bereits drin — Play-Services generell kompatibel.)

**`app/src/main/AndroidManifest.xml`** — Permissions + Receiver ergänzen:
```xml
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<!-- + <receiver> für GeofenceBroadcastReceiver (exported=false) und Boot-Receiver -->
```
Das Manifest hat schon einen `<service>`-Block (Wear-Listener) als Formatvorlage.

---

## 9. Meilensteine (jeder = grüner Build = ein Commit)

| M | Inhalt | Testbar ohne Hardware? |
|---|--------|------------------------|
| **M0** | Datenmodell: `GymEntity` +coords/radius/toggle, `GymVisitEntity`, DAO, Repository, DB v5→v6, Schema-Export. `GymVisitStats`-Logik (pure). | **Ja** — JVM-Unit-Tests (Stats/Histogramme) + instrumented DAO-Tests (Muster: `app/src/androidTest/…/dao`). |
| **M1** | Gym-Verwaltungs-Screen (Liste + Editor) ab Einstellungen; „Standort übernehmen" (FusedLocation) + Foreground-Permission-Flow. | Teilweise — UI baubar, echte Koordinaten brauchen Gerät. |
| **M2** | `GeofenceManager` (register/unregister aller Gyms), `GeofenceBroadcastReceiver`, Boot-Re-Register, Background-Permission-Flow, Manifest, Master-Toggle in Settings. | Nein — Geofencing ist Integration (Gerät/Emulator-Standort). |
| **M3** | Besuchs-Logging (Geofence-DWELL + Session-Start, Tages-Dedupe), `GymVisitStats` ans UI (optional „meistens dienstags"). | **Ja** — Logging-Entscheidung + Dedupe als pure Logik JVM-testbar. |
| **M4** | Notification-Channel + Deep-Link-Action (SessionErstellen vorbefüllt), `ProximityNotificationPolicy` (Cooldown/Suppression/Muster) + Persistenz. | **Ja** — Policy ist pure Kotlin, komplett JVM-testbar. |
| **M5** | Verifikation auf echtem Gerät + Kalibrierung Radius/Cooldown/LoiteringDelay. | **Nein — braucht Deniz** (s. §10). |

Baue die **pure Logik** (Stats §3.3, Policy §6) früh und teste sie hart in der JVM — das ist der
Teil, der ohne Hardware demonstrierbar korrekt sein kann. Android-Geofencing/Notifications drumherum
sind dünne Adapter.

---

## 10. Was echte Hardware braucht (Blocker für M5, mit Deniz)

- **Geofence-Auslösung** lässt sich nur mit echtem Standortwechsel prüfen: physisch zum Gym gehen
  **oder** Emulator „Extended Controls → Location" / `adb emu geo fix <lon> <lat>` bzw. Mock-Location.
- **Hintergrund-Verhalten** (Doze, App geschlossen, Hersteller-Akku-Killer) ist geräteabhängig —
  Batterieoptimierung-Ausnahme evtl. nötig; nur am echten Gerät seriös testbar.
- **Kalibrierung:** finaler Radius (150 m Startwert), `loiteringDelay`, Cooldown-Länge, was ein
  „typischer Slot" ist → empirisch mit Deniz, nicht raten. Als benannte Default-Konstanten mit
  Kommentar „empirisch zu kalibrieren" halten.

---

## 11. Arbeitsweise (wie im restlichen Projekt)

- **Build:** `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"`, dann
  `& ".\gradlew.bat" :app:assembleDebug --console=plain`. AGP-9-Fallen (Hilt ≥ 2.60,
  `android.disallowKotlinSourceSets=false`) gelten projektweit — nichts extra nötig.
- **Ein Meilenstein = ein grüner Build = ein Commit** (Branch `PushNot`).
- **UI:** semantische Tokens (`BoulderBuddy.colors.*`, `Dimens.*`, `MaterialTheme.colorScheme.*`),
  bestehende Bausteine in `ui/components/` (`PrimaryButton`, `TextField`, `SettingsRow`,
  `BoulderBuddyScaffold`, `TopBar`, `ToggleSwitch`) wiederverwenden. Dark Mode ist aktiv → keine
  hartkodierten Farben.
- **Doku-Pflicht (Obsidian-Vault):** jede Architektur-Entscheidung (Geofencing statt Polling,
  `GymVisitEntity`, DB v6, Deep-Link-Erweiterung `SessionErstellen`) →
  `04 – Entwicklung/Code-Entscheidungen.md`; Stolpersteine (Background-Location-Permission-Quirks,
  Receiver+Hilt) → `04 – Entwicklung/Bugs & Fixes.md`.

---

## 12. Haupt-Risiken, früh adressieren

1. **Hintergrund-Standort-Permission-UX** (API 29/30-Bruch) ist der reibungsreichste Punkt — baue den
   Flow (Foreground → dann Background → Settings-Redirect ab API 30) sauber mit Rationale, sonst
   bekommt das Feature nie Daten. Master-Toggle „aus" muss alle Geofences entfernen.
2. **Keine Gym-Verwaltungs-UI heute** (§2) — ohne den Editor (M1) gibt es keine Koordinaten. Nicht
   überspringen.
3. **Receiver + Room/Hilt:** DB-Zugriff aus `BroadcastReceiver` braucht `EntryPointAccessors` +
   `goAsync()`; nicht auf dem Main-Thread blockieren.
