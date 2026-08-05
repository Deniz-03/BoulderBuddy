# Tablet-UI — Plan

Branch `TabletPolish`. Stand 05.08.2026.

## Ausgangslage (am Pixel-Tablet-Emulator gemessen, nicht gerechnet)

Gerät: 2560 × 1600 px bei Density 320 → **1280 × 800 dp quer**, 800 × 1280 dp hoch.

| Befund | Messwert | Soll |
|---|---|---|
| Schnellaktionen Home (`weight(1f).aspectRatio(1f)`) | 410 dp Quadrate, ~60 % der Bildhöhe | ≤ 160 dp hoch |
| Heatmap-Zellen (`fillWidth = true`, 7 Spalten) | ~83 dp | 16 dp |
| Balkendiagramm bei 2 Balken | 2 Farbflächen à 615 dp | gedeckelte Balkenbreite |
| Einstellungs-Zeile | Label bei x = 0, Wert bei x = 1240 dp | ≤ 600 dp Spalte |
| Formularfeld „Halle/Ort" | 1248 dp breit | ≤ 600 dp |
| Boulder-Karten (`chunked(2)`) | 615 dp für drei Wörter | 3–4 Spalten à ~300 dp |
| Sessions-TopBar im List-Detail | endet bei 360 dp, Detail-Bereich ohne Chrome | durchgehende Kante |

Zwei Screens waren schon adaptiv (Sessions als `ListDetailPaneScaffold`, Statistik über ein
`wide`-Flag). Ausgerechnet der Sessions-Screen sieht am schlechtesten aus: die TopBar reißt
mitten im Bild ab, weil sie **im** List-Pane sitzt statt darüber.

**Kernbefund:** Es gibt im gesamten UI-Baum kein einziges `widthIn`, `sizeIn` oder
`BoxWithConstraints`. Jede Breite ist entweder `fillMaxWidth()` oder `weight(1f)` — beides
wächst unbegrenzt mit dem Fenster. Das ist keine Sammlung von Einzelfehlern, sondern **ein
fehlendes Konzept**: die App kennt keine Obergrenze für Inhalt.

## Leitlinien (Android/Material-3-Vorgaben)

1. **Breakpoints**: Kompakt < 600 dp, Mittel 600–839 dp, Weit ≥ 840 dp.
2. **Navigation folgt der Breite**: BottomNav nur bei Kompakt, ab Mittel eine seitliche Rail.
3. **Nie feste Spaltenzahlen** — `GridCells.Adaptive(minSize)` statt `chunked(n)`.
4. **Inhalt wird nicht gestreckt, sondern begrenzt oder in Panes aufgeteilt.** Eine Textspalte
   über ~600 dp ist nicht mehr lesbar.
5. **Chip-Gruppen fließen** (`FlowRow`), sie werden nicht in Raster gezwungen.
6. Geprüft wird in allen drei Klassen und in beiden Orientierungen.

## Schritte

### S1 — Breiten-Token statt durchgereichtem Boolean
`ui/theme/Breite.kt`: Enum `Kompakt | Mittel | Weit` + `@Composable fun aktuelleBreite()` auf
Basis von `currentWindowAdaptiveInfo()`. Screens bekommen einen Parameter mit Default daraus —
so bleiben die `@Preview`s steuerbar. Ersetzt das heutige `isWideLayout`-Boolean, das nur zwei
Zustände kennt und nur an zwei Stellen ankommt.

### S2 — Inhaltsbreite deckeln
`Modifier.inhaltsBreite()` in `ui/theme/Modifiers.kt`: `widthIn(max = …)` + horizontal zentriert.
Zwei Stufen als Token in `Dimens`: `spaltenBreiteText = 600.dp` (Formulare, Einstellungen,
Notizen) und `spaltenBreiteWeit = 1040.dp` (Dashboards, Listen-Raster).

Betrifft: `SessionErstellenScreen`, `RouteHinzufuegenScreen`, `AlteSessionScreen`,
`EinstellungenScreen`, `HangboardHistorieScreen`, `BoulderDetailScreen`.

### S3 — Navigation: Rail ab Mittel
`SideNav.kt` als Geschwister zu `BottomNav.kt` — **gleiche Tokens, gleicher Aktiv-Punkt, gleiches
Chrome**, nur vertikal. In `AppNavigation` wird bei Kompakt eine `Column` (Inhalt + BottomNav),
ab Mittel eine `Row` (SideNav + Inhalt) aufgebaut.

> **Entscheidung, die ich treffe:** Der Standardweg wäre `NavigationSuiteScaffold` aus
> `material3-adaptive-navigation-suite`. Der bringt aber Materials eigene Item-Optik mit
> (Pillen-Indikator, eigene Farbrollen) und würde die BottomNav ersetzen, die über fünf
> Design-Runden auf die Palette abgestimmt wurde — inklusive des Aktiv-Punkts und
> `textTertiary` für inaktiv statt eines Alpha-Werts. Materials Vorgabe ist eine Aussage
> über **Verhalten** (Rail ab Mittel), nicht über Bauteile. Deshalb der eigene Nachbau.
> Sag Bescheid, wenn du lieber Materials Komponente willst — dann wird es die Optik von
> Material und nicht deine.

### S4 — Adaptive Raster statt `chunked(2)`
Drei Stellen: `BoulderUebersichtScreen:227`, `SessionDetailScreen:148`,
`SessionErstellenScreen:109`. Die ersten beiden auf `LazyVerticalGrid` /
`GridCells.Adaptive(minSize = 280.dp)`, die Chips im dritten auf `FlowRow`.

### S5 — Gedeckelte Kacheln und Diagramme
- `QuickActionButton` / `HomeScreen`: `aspectRatio(1f)` nur solange die Kachel schmal ist;
  darüber feste Maximalhöhe. Quadrate sind ein Phone-Kompromiss, kein Gestaltungsprinzip.
- `ActivityHeatmap`: Zellgröße nach oben deckeln, Raster linksbündig statt gestreckt.
- `BarChart`: Balkenbreite deckeln.
- `StatCard`: Maximalbreite, sonst steht eine einstellige Zahl in 400 dp Weiß.

### S6 — Home als mehrspaltiger Feed
Ab Weit zwei Spalten: links Statistik + Schnellaktionen, rechts letzte Session + Verlauf.
Heute endet der Inhalt nach einem Drittel und darunter ist nichts.

### S7 — Sessions-List-Detail: den Chrome-Riss schließen
Die TopBar aus dem List-Pane herausziehen, sodass sie über beiden Panes durchläuft; der
Zurück-Pfeil im Detail-Pane entfällt, solange beide Panes sichtbar sind (er führt dort nur
in den Leerzustand). Platzhalter-Pane gestalten statt zentriertem Fließtext.

### S8 — Timer zweispaltig ab Weit
Ring links, Presets/Konfiguration rechts. Heute ist der Ring zentriert und der Rest leer.

### S9 — Absichern
- `ShapeScaleTest`-Muster fortsetzen: ein Test, der prüft, dass keine Kachel- oder
  Zellgröße ohne Obergrenze definiert ist, wäre nur eine Aussage über Werte — der eigentliche
  Nachweis sind Screenshots bei 800/1280 dp in beiden Orientierungen.
- JVM-Tests grün halten.
- **Phone-Darstellung auf dem Pixel 6a gegenprüfen** — jeder Schritt darf Kompakt nicht ändern.

## Reihenfolge

S1 und S2 zuerst (Fundament, wirken sofort auf mehrere Screens), dann S3, dann S4/S5 (die
sichtbarsten Ausreißer), danach S6–S8. S9 begleitend nach jedem Schritt.
