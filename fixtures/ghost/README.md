# Ghost-Climber-Regressions-Fixtures (Stufe 0)

5-Sekunden-On-Wall-Ausschnitte der beiden Referenzvideos (Diagnose-Stufe 0
der Ghost-Climber-Stabilisierung) — Kletterer an der Wand,
Rücken zur Kamera, Überkopf-Reach, graues Shirt vor grauer Wand (der
Out-of-Distribution-Fall, an dem die Pose-Qualität gemessen wird).

| Datei | Quelle | Ausschnitt | Eigenschaften |
|---|---|---|---|
| `referenz_onwall_5s.mp4` | `…/Downloads/Videos/Referenz.MOV` | Sekunde 20–25 | H.264, 1080×1920 Portrait (Rotation eingebrannt), 30 fps, ohne Ton |
| `vergleich_onwall_5s.mp4` | `…/Downloads/Videos/Vergleich.MOV` | Sekunde 20–25 | dito |

Erzeugt mit: `ffmpeg -ss 20 -t 5 -i <MOV> -c:v libx264 -crf 26 -preset fast -an <mp4>`
(ffmpeg rotiert beim Re-Encode selbst — die Fixtures haben KEIN `rotation=90`-Flag
mehr; wer den Rotations-Pfad testen will, braucht die Original-MOVs.)

## Verwendung
- Aufs Gerät kopieren, im Ghost Climber als Referenz + Vergleich wählen und mit dem
  **Debug-Chip** im Vorschau-Schritt die Kennzahlen ablesen (HUD + Logcat-Tag
  `GhostPoseMetrics`).
- Kennzahlen-Definition: `ghost/analysis/PoseQualityMetrics.kt`
  (Jitter px · Dropout-Quote · Links/Rechts-Flip-Anteil · mittlere Confidence).

## Baseline (ML Kit accurate, SINGLE_IMAGE_MODE, 6 fps, vor Stufe 1/3)
> Auf einem echten Gerät messen und hier eintragen — Ausgangswerte, an denen jede
> Stabilisierungs-Stufe gemessen wird.

| Spur | Jitter (px) | Dropout | Flip | Ø Confidence |
|---|---|---|---|---|
| Referenz | _tbd (Gerät)_ | _tbd_ | _tbd_ | _tbd_ |
| Vergleich | _tbd (Gerät)_ | _tbd_ | _tbd_ | _tbd_ |
