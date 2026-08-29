# Kommentarpflege

Ein Durchgang durch **jede** Code-Datei mit einer einzigen Frage: stimmt noch, was
dort behauptet wird?

## Warum

Ein Kommentar, der einmal richtig war, wird durch spätere Änderungen still falsch —
niemand bemerkt es, weil nichts bricht. Er richtet dann mehr Schaden an als gar
keiner: er wird geglaubt.

Der Anlass ist ein konkreter Fall. In `SessionsListDetail.kt` stand jahrelang, der
Screen werde „nur auf Medium/Expanded-Breiten (Tablet)" verwendet. Seit dem Wegfall
der `isWideLayout`-Verzweigung in `AppNavigation` stimmte das nicht mehr — der Screen
läuft auf allen Breiten. Bei der Suche nach einem Navigationsfehler hat dieser Satz
zuerst in die falsche Richtung gezeigt.

## Was gemacht wird — und was nicht

**Gemacht:**
- Jeder vorhandene Kommentar wird gegen den Code gelesen. Was nicht mehr stimmt,
  wird richtiggestellt oder entfernt.
- Wo eine Datei keinen hat, kommt ein kurzer Kopfkommentar dazu: was diese Datei
  ist, und welche Entscheidung dahintersteckt, wenn es eine gibt.
- Kommentare, die noch passen, bleiben unangetastet. Auch die langen — sie tragen
  die Begründungen, die man sonst nirgends findet.

**Nicht gemacht:**
- Kein Umbau, keine Umbenennung, keine Verhaltensänderung. Der Diff besteht aus
  Kommentaren und Doc-Kommentaren, sonst nichts.

## Was ein guter Kopfkommentar hier leistet

Nicht „was der Code tut" — das steht im Code. Sondern:
- die Aufgabe der Datei in einem Satz,
- die Entscheidung dahinter, wenn eine getroffen wurde („warum so und nicht anders"),
- die Fallstricke, die beim Ändern zu beachten sind.

## Befunde

Fällt beim Lesen ein echter Fehler oder ein begründeter Verdacht auf, wird er an
**zwei** Stellen festgehalten:

1. Im Code an der Fundstelle, als Kommentar mit eindeutigem Marker:
   `// BEFUND B<nr> (Kommentarpflege): <was stimmt nicht>`
   Auffindbar mit `grep -rn "BEFUND B" app wear`.
2. Unten in der Tabelle, mit Datei, Einschätzung und Schweregrad.

Schweregrade: **hoch** = falsches Verhalten für den Nutzer sichtbar ·
**mittel** = falsch, aber im Alltag unauffällig · **niedrig** = Unsauberkeit,
Stolperstein beim Lesen.

Befunde werden **nicht** repariert. Das ist der Punkt der Trennung: ein Durchgang,
der nebenbei Verhalten ändert, ist nicht mehr nachvollziehbar.

| Nr | Datei | Befund | Schwere |
|----|-------|--------|---------|
| B1 | `data/db/dao/GhostAnalysisDao.kt` | `deleteById` entfernt nur die Zeile. Die Pose-Spuren im `GhostArtifactStore` (`filesDir/ghost/pose_<hash>.json`, je nach Videolänge einige hundert kB) bleiben liegen, und es gibt nirgends ein Aufräumen. Als Cache für ein erneut analysiertes Video nützlich — ist das Video weg, totes Gewicht ohne Verfallsdatum. | niedrig |
| B2 | `ui/model/UiMappers.kt` | `parseHexColor` und `Color.toHexRgb` werden nirgends mehr aufgerufen — weder in der App noch in den Tests. Reste aus der Zeit, als ein Grad seine Farbe als Hexwert mitbrachte (bis v4). | niedrig |
| B3 | `ui/components/FilterChip.kt` | `selectedColor` wird von keinem der fünf Aufrufer gesetzt. Der Parameter sieht nach gepflegter Möglichkeit aus und ist keine. | niedrig |

## Reihenfolge

Von unten nach oben entlang der Abhängigkeiten: erst das Datenmodell, zuletzt die
Oberfläche. Wer die Schicht darunter verstanden hat, erkennt in der darüber, ob ein
Kommentar noch stimmt.

Abgehakt wird jede Datei einzeln — die Liste ist der Schutz davor, eine zu
übersehen. Die Zahl in Klammern ist die Zeilenzahl.

## Fortschritt


### Block 1 — Datenmodell


**util/** (1)

- [x] `app/src/main/java/com/boulderbuddy/util/MediaType.kt` (24)

**data/model/** (1)

- [x] `app/src/main/java/com/boulderbuddy/data/model/RouteStatus.kt` (25)

**data/db/entity/** (10)

- [x] `app/src/main/java/com/boulderbuddy/data/db/entity/GhostAnalysisEntity.kt` (58)
- [x] `app/src/main/java/com/boulderbuddy/data/db/entity/GradeEntity.kt` (35)
- [x] `app/src/main/java/com/boulderbuddy/data/db/entity/GradeSystemEntity.kt` (56)
- [x] `app/src/main/java/com/boulderbuddy/data/db/entity/GymEntity.kt` (44)
- [x] `app/src/main/java/com/boulderbuddy/data/db/entity/GymVisitEntity.kt` (43)
- [x] `app/src/main/java/com/boulderbuddy/data/db/entity/HangboardTemplateEntity.kt` (17)
- [x] `app/src/main/java/com/boulderbuddy/data/db/entity/HangboardWorkoutEntity.kt` (90)
- [x] `app/src/main/java/com/boulderbuddy/data/db/entity/RouteEntity.kt` (53)
- [x] `app/src/main/java/com/boulderbuddy/data/db/entity/SessionEntity.kt` (76)
- [x] `app/src/main/java/com/boulderbuddy/data/db/entity/StandMetaEntity.kt` (38)

**data/db/dao/** (11)

- [x] `app/src/main/java/com/boulderbuddy/data/db/dao/GhostAnalysisDao.kt` (27)
- [x] `app/src/main/java/com/boulderbuddy/data/db/dao/GradeDao.kt` (30)
- [x] `app/src/main/java/com/boulderbuddy/data/db/dao/GradeSystemDao.kt` (31)
- [x] `app/src/main/java/com/boulderbuddy/data/db/dao/GymDao.kt` (70)
- [x] `app/src/main/java/com/boulderbuddy/data/db/dao/GymVisitDao.kt` (26)
- [x] `app/src/main/java/com/boulderbuddy/data/db/dao/HangboardTemplateDao.kt` (24)
- [x] `app/src/main/java/com/boulderbuddy/data/db/dao/HangboardWorkoutDao.kt` (41)
- [x] `app/src/main/java/com/boulderbuddy/data/db/dao/MedienDao.kt` (34)
- [x] `app/src/main/java/com/boulderbuddy/data/db/dao/RouteDao.kt` (28)
- [x] `app/src/main/java/com/boulderbuddy/data/db/dao/SessionDao.kt` (40)
- [x] `app/src/main/java/com/boulderbuddy/data/db/dao/StandMetaDao.kt` (30)

**data/db/** (4)

- [x] `app/src/main/java/com/boulderbuddy/data/db/BoulderBuddyDatabase.kt` (100)
- [x] `app/src/main/java/com/boulderbuddy/data/db/Converters.kt` (32)
- [x] `app/src/main/java/com/boulderbuddy/data/db/Migrations.kt` (546)
- [x] `app/src/main/java/com/boulderbuddy/data/db/SeedData.kt` (148)


### Block 2 — Datenzugriff


**data/repository/** (8)

- [x] `app/src/main/java/com/boulderbuddy/data/repository/GhostAnalysisRepository.kt` (43)
- [x] `app/src/main/java/com/boulderbuddy/data/repository/GradeRepository.kt` (80)
- [x] `app/src/main/java/com/boulderbuddy/data/repository/GymRepository.kt` (51)
- [x] `app/src/main/java/com/boulderbuddy/data/repository/GymVisitRepository.kt` (51)
- [x] `app/src/main/java/com/boulderbuddy/data/repository/HangboardRepository.kt` (43)
- [x] `app/src/main/java/com/boulderbuddy/data/repository/HangboardWorkoutRepository.kt` (45)
- [x] `app/src/main/java/com/boulderbuddy/data/repository/RouteRepository.kt` (44)
- [x] `app/src/main/java/com/boulderbuddy/data/repository/SessionRepository.kt` (56)

**data/settings/** (1)

- [x] `app/src/main/java/com/boulderbuddy/data/settings/SettingsRepository.kt` (128)

**data/haptics/** (1)

- [x] `app/src/main/java/com/boulderbuddy/data/haptics/HapticPlayer.kt` (69)

**data/camera/** (3)

- [x] `app/src/main/java/com/boulderbuddy/data/camera/CameraCaptureController.kt` (259)
- [x] `app/src/main/java/com/boulderbuddy/data/camera/CaptureModel.kt` (106)
- [x] `app/src/main/java/com/boulderbuddy/data/camera/EigeneAufnahmen.kt` (82)

**data/speech/** (2)

- [x] `app/src/main/java/com/boulderbuddy/data/speech/SpeechRecognitionClient.kt` (353)
- [x] `app/src/main/java/com/boulderbuddy/data/speech/SpeechRecognitionModel.kt` (263)

**data/export/** (2)

- [x] `app/src/main/java/com/boulderbuddy/data/export/SessionCsv.kt` (97)
- [x] `app/src/main/java/com/boulderbuddy/data/export/SessionExporter.kt` (46)


### Block 3 — Ghost-Pipeline


**ghost/model/** (5)

- [x] `app/src/main/java/com/boulderbuddy/ghost/model/GhostLandmarkTypes.kt` (40)
- [x] `app/src/main/java/com/boulderbuddy/ghost/model/GhostPose.kt` (130)
- [x] `app/src/main/java/com/boulderbuddy/ghost/model/GhostSkeleton.kt` (37)
- [x] `app/src/main/java/com/boulderbuddy/ghost/model/GhostViewMode.kt` (11)
- [x] `app/src/main/java/com/boulderbuddy/ghost/model/PoseGeometry.kt` (165)

**ghost/geometry/** (2)

- [x] `app/src/main/java/com/boulderbuddy/ghost/geometry/GhostPointVec2.kt` (12)
- [x] `app/src/main/java/com/boulderbuddy/ghost/geometry/Homography.kt` (221)

**ghost/analysis/** (7)

- [x] `app/src/main/java/com/boulderbuddy/ghost/analysis/Dtw.kt` (89)
- [x] `app/src/main/java/com/boulderbuddy/ghost/analysis/FallDetection.kt` (64)
- [x] `app/src/main/java/com/boulderbuddy/ghost/analysis/GhostTimeMapping.kt` (155)
- [x] `app/src/main/java/com/boulderbuddy/ghost/analysis/ModeSuggestion.kt` (116)
- [x] `app/src/main/java/com/boulderbuddy/ghost/analysis/PoseQualityMetrics.kt` (397)
- [x] `app/src/main/java/com/boulderbuddy/ghost/analysis/RoutePath.kt` (121)
- [x] `app/src/main/java/com/boulderbuddy/ghost/analysis/Signals.kt` (93)

**ghost/pose/** (7)

- [x] `app/src/main/java/com/boulderbuddy/ghost/pose/PosePlausibility.kt` (325)
- [x] `app/src/main/java/com/boulderbuddy/ghost/pose/PoseRaumwechsel.kt` (59)
- [x] `app/src/main/java/com/boulderbuddy/ghost/pose/PoseSmoothing.kt` (226)
- [x] `app/src/main/java/com/boulderbuddy/ghost/pose/PoseSpurQuelle.kt` (51)
- [x] `app/src/main/java/com/boulderbuddy/ghost/pose/RigidSkeleton.kt` (193)
- [x] `app/src/main/java/com/boulderbuddy/ghost/pose/RoiTracking.kt` (200)
- [x] `app/src/main/java/com/boulderbuddy/ghost/pose/VideoPoseExtractor.kt` (378)

**ghost/video/** (2)

- [x] `app/src/main/java/com/boulderbuddy/ghost/video/GhostFrameDecoder.kt` (31)
- [x] `app/src/main/java/com/boulderbuddy/ghost/video/ScaledFrames.kt` (79)

**ghost/service/** (1)

- [x] `app/src/main/java/com/boulderbuddy/ghost/service/GhostAnalyseService.kt` (257)

**ghost/** (3)

- [x] `app/src/main/java/com/boulderbuddy/ghost/GhostAnalyseRunner.kt` (165)
- [x] `app/src/main/java/com/boulderbuddy/ghost/GhostArtifactStore.kt` (113)
- [x] `app/src/main/java/com/boulderbuddy/ghost/GhostTuning.kt` (412)


### Block 4 — Geraete-Abgleich


**sync/** (13)

- [x] `app/src/main/java/com/boulderbuddy/sync/Abgleich.kt` (591)
- [x] `app/src/main/java/com/boulderbuddy/sync/AbgleichDateien.kt` (157)
- [x] `app/src/main/java/com/boulderbuddy/sync/Abgleicher.kt` (410)
- [x] `app/src/main/java/com/boulderbuddy/sync/GeraeteIdentitaet.kt` (165)
- [x] `app/src/main/java/com/boulderbuddy/sync/GeraeteStore.kt` (11)
- [x] `app/src/main/java/com/boulderbuddy/sync/MedienNamen.kt` (72)
- [x] `app/src/main/java/com/boulderbuddy/sync/MedienSpeicher.kt` (129)
- [x] `app/src/main/java/com/boulderbuddy/sync/MedienUmzug.kt` (99)
- [x] `app/src/main/java/com/boulderbuddy/sync/Sequenzen.kt` (29)
- [x] `app/src/main/java/com/boulderbuddy/sync/StandDatei.kt` (70)
- [x] `app/src/main/java/com/boulderbuddy/sync/StandZugriff.kt` (228)
- [x] `app/src/main/java/com/boulderbuddy/sync/Standmodell.kt` (164)
- [x] `app/src/main/java/com/boulderbuddy/sync/Standtabellen.kt` (158)

**sync/nearby/** (5)

- [x] `app/src/main/java/com/boulderbuddy/sync/nearby/AbgleichService.kt` (164)
- [x] `app/src/main/java/com/boulderbuddy/sync/nearby/AbgleichSitzung.kt` (456)
- [x] `app/src/main/java/com/boulderbuddy/sync/nearby/NearbyBerechtigungen.kt` (65)
- [x] `app/src/main/java/com/boulderbuddy/sync/nearby/NearbyVerbindung.kt` (392)
- [x] `app/src/main/java/com/boulderbuddy/sync/nearby/Protokoll.kt` (222)


### Block 5 — Aussenkanten


**proximity/** (12)

- [x] `app/src/main/java/com/boulderbuddy/proximity/GeofenceBootReceiver.kt` (41)
- [x] `app/src/main/java/com/boulderbuddy/proximity/GeofenceBroadcastReceiver.kt` (58)
- [x] `app/src/main/java/com/boulderbuddy/proximity/GeofenceManager.kt` (127)
- [x] `app/src/main/java/com/boulderbuddy/proximity/GymLocationClient.kt` (40)
- [x] `app/src/main/java/com/boulderbuddy/proximity/GymVisitStats.kt` (100)
- [x] `app/src/main/java/com/boulderbuddy/proximity/LocationPermissions.kt` (33)
- [x] `app/src/main/java/com/boulderbuddy/proximity/ProximityEntryPoint.kt` (17)
- [x] `app/src/main/java/com/boulderbuddy/proximity/ProximityEventHandler.kt` (64)
- [x] `app/src/main/java/com/boulderbuddy/proximity/ProximityNotificationPolicy.kt` (90)
- [x] `app/src/main/java/com/boulderbuddy/proximity/ProximityNotifier.kt` (99)
- [x] `app/src/main/java/com/boulderbuddy/proximity/ProximityPushStateStore.kt` (32)
- [x] `app/src/main/java/com/boulderbuddy/proximity/Tasks.kt` (17)

**wearsync/** (3)

- [x] `app/src/main/java/com/boulderbuddy/wearsync/HangboardPresetPublisher.kt` (68)
- [x] `app/src/main/java/com/boulderbuddy/wearsync/HangboardWearListenerService.kt` (237)
- [x] `app/src/main/java/com/boulderbuddy/wearsync/WearConnection.kt` (62)

**widget/** (5)

- [x] `app/src/main/java/com/boulderbuddy/widget/BoulderWidget.kt` (344)
- [x] `app/src/main/java/com/boulderbuddy/widget/BoulderWidgetReceiver.kt` (9)
- [x] `app/src/main/java/com/boulderbuddy/widget/WidgetData.kt` (125)
- [x] `app/src/main/java/com/boulderbuddy/widget/WidgetIntent.kt` (43)
- [x] `app/src/main/java/com/boulderbuddy/widget/WidgetRefresh.kt` (20)


### Block 6 — App-Gerüst


**di/** (3)

- [x] `app/src/main/java/com/boulderbuddy/di/DatabaseModule.kt` (86)
- [x] `app/src/main/java/com/boulderbuddy/di/RepositoryModule.kt` (92)
- [x] `app/src/main/java/com/boulderbuddy/di/SettingsModule.kt` (47)

**com/boulderbuddy (Wurzel)** (2)

- [x] `app/src/main/java/com/boulderbuddy/BoulderBuddyApp.kt` (30)
- [x] `app/src/main/java/com/boulderbuddy/MainActivity.kt` (164)


### Block 7 — UI-Bausteine


**ui/theme/** (11)

- [x] `app/src/main/java/com/boulderbuddy/ui/theme/BoulderBuddyTokens.kt` (110)
- [x] `app/src/main/java/com/boulderbuddy/ui/theme/Breite.kt` (65)
- [x] `app/src/main/java/com/boulderbuddy/ui/theme/Color.kt` (64)
- [x] `app/src/main/java/com/boulderbuddy/ui/theme/Dimens.kt` (81)
- [x] `app/src/main/java/com/boulderbuddy/ui/theme/Insets.kt` (30)
- [x] `app/src/main/java/com/boulderbuddy/ui/theme/Modifiers.kt` (50)
- [x] `app/src/main/java/com/boulderbuddy/ui/theme/PaletteHex.kt` (214)
- [x] `app/src/main/java/com/boulderbuddy/ui/theme/RouteColors.kt` (36)
- [x] `app/src/main/java/com/boulderbuddy/ui/theme/Shape.kt` (57)
- [x] `app/src/main/java/com/boulderbuddy/ui/theme/Theme.kt` (204)
- [x] `app/src/main/java/com/boulderbuddy/ui/theme/Type.kt` (182)

**ui/model/** (2)

- [x] `app/src/main/java/com/boulderbuddy/ui/model/UiMappers.kt` (160)
- [x] `app/src/main/java/com/boulderbuddy/ui/model/Zeitraum.kt` (67)

**ui/components/** (38)

- [x] `app/src/main/java/com/boulderbuddy/ui/components/ActivityHeatmap.kt` (134)
- [x] `app/src/main/java/com/boulderbuddy/ui/components/AddRouteCard.kt` (92)
- [x] `app/src/main/java/com/boulderbuddy/ui/components/BarChart.kt` (164)
- [x] `app/src/main/java/com/boulderbuddy/ui/components/BottomNav.kt` (182)
- [x] `app/src/main/java/com/boulderbuddy/ui/components/BoulderBuddyScaffold.kt` (121)
- [x] `app/src/main/java/com/boulderbuddy/ui/components/BoulderListRow.kt` (116)
- [x] `app/src/main/java/com/boulderbuddy/ui/components/ColorPicker.kt` (116)
- [x] `app/src/main/java/com/boulderbuddy/ui/components/EingabeDialog.kt` (101)
- [x] `app/src/main/java/com/boulderbuddy/ui/components/EmptyState.kt` (88)
- [x] `app/src/main/java/com/boulderbuddy/ui/components/FeaturedCard.kt` (108)
- [x] `app/src/main/java/com/boulderbuddy/ui/components/FilterChip.kt` (63)
- [x] `app/src/main/java/com/boulderbuddy/ui/components/GhostAnchorEditor.kt` (191)
- [x] `app/src/main/java/com/boulderbuddy/ui/components/GhostPathEditor.kt` (166)
- [x] `app/src/main/java/com/boulderbuddy/ui/components/GhostSideBySidePlayer.kt` (218)
- [x] `app/src/main/java/com/boulderbuddy/ui/components/GhostSkeletonPlayer.kt` (456)
- [x] `app/src/main/java/com/boulderbuddy/ui/components/LineChart.kt` (204)
- [x] `app/src/main/java/com/boulderbuddy/ui/components/MedienQuelleDialog.kt` (141)
- [x] `app/src/main/java/com/boulderbuddy/ui/components/PhotoPicker.kt` (141)
- [x] `app/src/main/java/com/boulderbuddy/ui/components/PrimaryButton.kt` (108)
- [x] `app/src/main/java/com/boulderbuddy/ui/components/QuickActionButton.kt` (98)
- [x] `app/src/main/java/com/boulderbuddy/ui/components/RouteCard.kt` (112)
- [x] `app/src/main/java/com/boulderbuddy/ui/components/SectionHeader.kt` (39)
- [x] `app/src/main/java/com/boulderbuddy/ui/components/SelectableChip.kt` (119)
- [x] `app/src/main/java/com/boulderbuddy/ui/components/SessionListItem.kt` (141)
- [x] `app/src/main/java/com/boulderbuddy/ui/components/SettingsRow.kt` (130)
- [x] `app/src/main/java/com/boulderbuddy/ui/components/SideNav.kt` (156)
- [x] `app/src/main/java/com/boulderbuddy/ui/components/SkeletonDraw.kt` (72)
- [x] `app/src/main/java/com/boulderbuddy/ui/components/SpeechInputDialog.kt` (246)
- [x] `app/src/main/java/com/boulderbuddy/ui/components/SpeechToTextButton.kt` (188)
- [x] `app/src/main/java/com/boulderbuddy/ui/components/StatCard.kt` (62)
- [x] `app/src/main/java/com/boulderbuddy/ui/components/StatusBadge.kt` (58)
- [x] `app/src/main/java/com/boulderbuddy/ui/components/TextField.kt` (137)
- [x] `app/src/main/java/com/boulderbuddy/ui/components/TimerControls.kt` (112)
- [x] `app/src/main/java/com/boulderbuddy/ui/components/TimerRing.kt` (122)
- [x] `app/src/main/java/com/boulderbuddy/ui/components/ToggleSwitch.kt` (97)
- [x] `app/src/main/java/com/boulderbuddy/ui/components/TopBar.kt` (192)
- [x] `app/src/main/java/com/boulderbuddy/ui/components/UebersichtTopBar.kt` (161)
- [x] `app/src/main/java/com/boulderbuddy/ui/components/VideoPlayer.kt` (63)


### Block 8 — UI-Fluss


**ui/viewmodel/** (16)

- [x] `app/src/main/java/com/boulderbuddy/ui/viewmodel/AbgleichViewModel.kt` (264)
- [x] `app/src/main/java/com/boulderbuddy/ui/viewmodel/BoulderDetailViewModel.kt` (98)
- [x] `app/src/main/java/com/boulderbuddy/ui/viewmodel/BoulderUebersichtViewModel.kt` (98)
- [x] `app/src/main/java/com/boulderbuddy/ui/viewmodel/EinstellungenViewModel.kt` (220)
- [x] `app/src/main/java/com/boulderbuddy/ui/viewmodel/GhostClimberViewModel.kt` (671)
- [x] `app/src/main/java/com/boulderbuddy/ui/viewmodel/GymBearbeitenViewModel.kt` (248)
- [x] `app/src/main/java/com/boulderbuddy/ui/viewmodel/GymVerwaltungViewModel.kt` (52)
- [x] `app/src/main/java/com/boulderbuddy/ui/viewmodel/HangboardHistorieViewModel.kt` (86)
- [x] `app/src/main/java/com/boulderbuddy/ui/viewmodel/HangboardTimerViewModel.kt` (344)
- [x] `app/src/main/java/com/boulderbuddy/ui/viewmodel/HomeViewModel.kt` (178)
- [x] `app/src/main/java/com/boulderbuddy/ui/viewmodel/RouteHinzufuegenViewModel.kt` (205)
- [x] `app/src/main/java/com/boulderbuddy/ui/viewmodel/SessionErstellenViewModel.kt` (237)
- [x] `app/src/main/java/com/boulderbuddy/ui/viewmodel/SessionListViewModel.kt` (122)
- [x] `app/src/main/java/com/boulderbuddy/ui/viewmodel/SessionViewModel.kt` (241)
- [x] `app/src/main/java/com/boulderbuddy/ui/viewmodel/StatistikViewModel.kt` (278)
- [x] `app/src/main/java/com/boulderbuddy/ui/viewmodel/ThemeViewModel.kt` (28)

**ui/screens/** (20)

- [x] `app/src/main/java/com/boulderbuddy/ui/screens/AbgleichScreen.kt` (582)
- [x] `app/src/main/java/com/boulderbuddy/ui/screens/AlteSessionScreen.kt` (273)
- [x] `app/src/main/java/com/boulderbuddy/ui/screens/BoulderDetailRoute.kt` (40)
- [x] `app/src/main/java/com/boulderbuddy/ui/screens/BoulderDetailScreen.kt` (319)
- [x] `app/src/main/java/com/boulderbuddy/ui/screens/BoulderUebersichtScreen.kt` (293)
- [x] `app/src/main/java/com/boulderbuddy/ui/screens/EinstellungenScreen.kt` (751)
- [x] `app/src/main/java/com/boulderbuddy/ui/screens/GhostClimberScreen.kt` (923)
- [x] `app/src/main/java/com/boulderbuddy/ui/screens/GymBearbeitenScreen.kt` (478)
- [x] `app/src/main/java/com/boulderbuddy/ui/screens/GymVerwaltungScreen.kt` (198)
- [x] `app/src/main/java/com/boulderbuddy/ui/screens/HangboardHistorieScreen.kt` (127)
- [x] `app/src/main/java/com/boulderbuddy/ui/screens/HangboardTimerScreen.kt` (438)
- [x] `app/src/main/java/com/boulderbuddy/ui/screens/HomeScreen.kt` (255)
- [x] `app/src/main/java/com/boulderbuddy/ui/screens/KameraScreen.kt` (482)
- [x] `app/src/main/java/com/boulderbuddy/ui/screens/RouteHinzufuegenScreen.kt` (440)
- [x] `app/src/main/java/com/boulderbuddy/ui/screens/SessionDetailScreen.kt` (308)
- [x] `app/src/main/java/com/boulderbuddy/ui/screens/SessionErstellenScreen.kt` (364)
- [x] `app/src/main/java/com/boulderbuddy/ui/screens/SessionGhostBlock.kt` (99)
- [x] `app/src/main/java/com/boulderbuddy/ui/screens/SessionRoute.kt` (91)
- [x] `app/src/main/java/com/boulderbuddy/ui/screens/SessionUebersichtScreen.kt` (244)
- [x] `app/src/main/java/com/boulderbuddy/ui/screens/StatistikScreen.kt` (539)

**ui/navigation/** (4)

- [x] `app/src/main/java/com/boulderbuddy/ui/navigation/AppNavigation.kt` (640)
- [x] `app/src/main/java/com/boulderbuddy/ui/navigation/BoulderListDetail.kt` (130)
- [x] `app/src/main/java/com/boulderbuddy/ui/navigation/Destinations.kt` (134)
- [x] `app/src/main/java/com/boulderbuddy/ui/navigation/SessionsListDetail.kt` (196)


### Block 9 — Wear-App


**wear/** (17)

- [ ] `wear/src/main/java/com/boulderbuddy/wear/data/PhoneConnector.kt` (138)
- [ ] `wear/src/main/java/com/boulderbuddy/wear/data/PresetSyncClient.kt` (70)
- [ ] `wear/src/main/java/com/boulderbuddy/wear/data/WearSettings.kt` (45)
- [ ] `wear/src/main/java/com/boulderbuddy/wear/data/WearSyncContract.kt` (50)
- [ ] `wear/src/main/java/com/boulderbuddy/wear/presentation/AutoHangScreen.kt` (220)
- [ ] `wear/src/main/java/com/boulderbuddy/wear/presentation/AutoHangViewModel.kt` (100)
- [ ] `wear/src/main/java/com/boulderbuddy/wear/presentation/MainActivity.kt` (18)
- [ ] `wear/src/main/java/com/boulderbuddy/wear/presentation/SensorLogScreen.kt` (148)
- [ ] `wear/src/main/java/com/boulderbuddy/wear/presentation/SensorLogViewModel.kt` (91)
- [ ] `wear/src/main/java/com/boulderbuddy/wear/presentation/TimerScreen.kt` (217)
- [ ] `wear/src/main/java/com/boulderbuddy/wear/presentation/TimerViewModel.kt` (268)
- [ ] `wear/src/main/java/com/boulderbuddy/wear/presentation/WearApp.kt` (105)
- [ ] `wear/src/main/java/com/boulderbuddy/wear/sensing/AutoHangService.kt` (252)
- [ ] `wear/src/main/java/com/boulderbuddy/wear/sensing/HangDetection.kt` (237)
- [ ] `wear/src/main/java/com/boulderbuddy/wear/sensing/SensorLogParser.kt` (52)
- [ ] `wear/src/main/java/com/boulderbuddy/wear/sensing/SensorLoggingService.kt` (219)
- [ ] `wear/src/test/java/com/boulderbuddy/wear/sensing/HangDetectorTest.kt` (188)


### Block 10 — Tests


**Tests** (60)

- [ ] `app/src/androidTest/java/com/boulderbuddy/data/db/MigrationTest.kt` (419)
- [ ] `app/src/androidTest/java/com/boulderbuddy/data/db/TestDatabase.kt` (14)
- [ ] `app/src/androidTest/java/com/boulderbuddy/data/db/dao/GradeDaoTest.kt` (62)
- [ ] `app/src/androidTest/java/com/boulderbuddy/data/db/dao/GymDaoLoeschenTest.kt` (98)
- [ ] `app/src/androidTest/java/com/boulderbuddy/data/db/dao/GymVisitDaoTest.kt` (86)
- [ ] `app/src/androidTest/java/com/boulderbuddy/data/db/dao/RouteDaoTest.kt` (59)
- [ ] `app/src/androidTest/java/com/boulderbuddy/data/db/dao/SessionDaoTest.kt` (81)
- [ ] `app/src/androidTest/java/com/boulderbuddy/data/repository/SessionRepositoryTest.kt` (58)
- [ ] `app/src/androidTest/java/com/boulderbuddy/sync/StandZugriffTest.kt` (231)
- [ ] `app/src/androidTest/java/com/boulderbuddy/ui/components/DiagrammBreiteTest.kt` (168)
- [ ] `app/src/androidTest/java/com/boulderbuddy/ui/screens/AlteSessionNotizTest.kt` (65)
- [ ] `app/src/androidTest/java/com/boulderbuddy/ui/screens/HangboardTimerScreenTest.kt` (71)
- [ ] `app/src/androidTest/java/com/boulderbuddy/ui/theme/InhaltsBreiteTest.kt` (180)
- [ ] `app/src/test/java/com/boulderbuddy/data/camera/CaptureModelTest.kt` (103)
- [ ] `app/src/test/java/com/boulderbuddy/data/db/entity/HallenNameTest.kt` (59)
- [ ] `app/src/test/java/com/boulderbuddy/data/export/SessionCsvTest.kt` (215)
- [ ] `app/src/test/java/com/boulderbuddy/data/repository/GymVisitRepositoryTest.kt` (87)
- [ ] `app/src/test/java/com/boulderbuddy/data/speech/SpeechRecognitionModelTest.kt` (229)
- [ ] `app/src/test/java/com/boulderbuddy/fake/FakeRepositories.kt` (228)
- [ ] `app/src/test/java/com/boulderbuddy/ghost/GhostAnalyseRunnerTest.kt` (185)
- [ ] `app/src/test/java/com/boulderbuddy/ghost/analysis/DtwTest.kt` (76)
- [ ] `app/src/test/java/com/boulderbuddy/ghost/analysis/FallDetectionTest.kt` (55)
- [ ] `app/src/test/java/com/boulderbuddy/ghost/analysis/GhostTimeMappingTest.kt` (142)
- [ ] `app/src/test/java/com/boulderbuddy/ghost/analysis/ModeSuggestionTest.kt` (76)
- [ ] `app/src/test/java/com/boulderbuddy/ghost/analysis/PoseShapeMetricsTest.kt` (235)
- [ ] `app/src/test/java/com/boulderbuddy/ghost/analysis/RoutePathTest.kt` (68)
- [ ] `app/src/test/java/com/boulderbuddy/ghost/geometry/HomographyTest.kt` (100)
- [ ] `app/src/test/java/com/boulderbuddy/ghost/model/GhostPoseTest.kt` (63)
- [ ] `app/src/test/java/com/boulderbuddy/ghost/model/PoseInterpolationTest.kt` (79)
- [ ] `app/src/test/java/com/boulderbuddy/ghost/pose/OneEuroSweepTest.kt` (167)
- [ ] `app/src/test/java/com/boulderbuddy/ghost/pose/PosePlausibilityTest.kt` (229)
- [ ] `app/src/test/java/com/boulderbuddy/ghost/pose/PoseRaumwechselTest.kt` (119)
- [ ] `app/src/test/java/com/boulderbuddy/ghost/pose/PoseSmoothingTest.kt` (186)
- [ ] `app/src/test/java/com/boulderbuddy/ghost/pose/RigidSkeletonInvariantTest.kt` (223)
- [ ] `app/src/test/java/com/boulderbuddy/ghost/pose/RigidSkeletonTest.kt` (193)
- [ ] `app/src/test/java/com/boulderbuddy/ghost/pose/RoiTrackingTest.kt` (239)
- [ ] `app/src/test/java/com/boulderbuddy/proximity/GymVisitStatsTest.kt` (143)
- [ ] `app/src/test/java/com/boulderbuddy/proximity/ProximityNotificationPolicyTest.kt` (134)
- [ ] `app/src/test/java/com/boulderbuddy/sync/AbgleichDateienTest.kt` (67)
- [ ] `app/src/test/java/com/boulderbuddy/sync/AbgleichTest.kt` (739)
- [ ] `app/src/test/java/com/boulderbuddy/sync/MedienNamenTest.kt` (70)
- [ ] `app/src/test/java/com/boulderbuddy/sync/NummernBandTest.kt` (144)
- [ ] `app/src/test/java/com/boulderbuddy/sync/nearby/EmpfangeneDateiTest.kt` (93)
- [ ] `app/src/test/java/com/boulderbuddy/sync/nearby/ProtokollTest.kt` (253)
- [ ] `app/src/test/java/com/boulderbuddy/ui/components/SideBySideSyncTest.kt` (76)
- [ ] `app/src/test/java/com/boulderbuddy/ui/model/DurationFormatTest.kt` (40)
- [ ] `app/src/test/java/com/boulderbuddy/ui/model/ZeitraumTest.kt` (123)
- [ ] `app/src/test/java/com/boulderbuddy/ui/theme/Kontrast.kt` (47)
- [ ] `app/src/test/java/com/boulderbuddy/ui/theme/PaletteContrastTest.kt` (329)
- [ ] `app/src/test/java/com/boulderbuddy/ui/theme/ShapeScaleTest.kt` (81)
- [ ] `app/src/test/java/com/boulderbuddy/ui/theme/SpaltenFuerTest.kt` (79)
- [ ] `app/src/test/java/com/boulderbuddy/ui/viewmodel/HallenReihenfolgeTest.kt` (83)
- [ ] `app/src/test/java/com/boulderbuddy/ui/viewmodel/HangboardTimerViewModelTest.kt` (217)
- [ ] `app/src/test/java/com/boulderbuddy/ui/viewmodel/LaufendeSessionTest.kt` (94)
- [ ] `app/src/test/java/com/boulderbuddy/ui/viewmodel/SessionListViewModelTest.kt` (145)
- [ ] `app/src/test/java/com/boulderbuddy/ui/viewmodel/StatistikVerlaufTest.kt` (283)
- [ ] `app/src/test/java/com/boulderbuddy/ui/viewmodel/StatistikViewModelTest.kt` (172)
- [ ] `app/src/test/java/com/boulderbuddy/util/MainDispatcherRule.kt` (28)
- [ ] `app/src/test/java/com/boulderbuddy/widget/WidgetDataTest.kt` (120)
- [ ] `app/src/test/java/com/boulderbuddy/widget/WidgetPaletteTest.kt` (95)
