package com.boulderbuddy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import coil3.compose.AsyncImage
import com.boulderbuddy.R
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.theme.Dimens

// Gestrichelter Foto-Slot für "Route hinzufügen" und Boulder-Detail.
// Teilt sich das Dash-Rahmen-Muster mit AddRouteCard (drawBehind + PathEffect),
// weil BorderStroke kein PathEffect unterstützt.
// onClick gehört zum Kern-UI: der Slot IST der Auslöser für Kamera/Galerie —
// das Öffnen des Pickers selbst bleibt Sache des Screens.
// imageUri (Phase 6.11): ist es gesetzt, zeigt der Slot das gewählte Foto via Coil.
// isVideo (Phase 7.3c): ist die Auswahl ein Video, zeigt der Slot statt eines Frames einen
// klaren Video-Indikator (Coil rendert ohne Video-Decoder kein Vorschaubild).
@Composable
fun PhotoPicker(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Foto/Video aufnehmen",
    imageUri: String? = null,
    isVideo: Boolean = false,
) {
    val borderColor = BoulderBuddy.colors.borderSubtle
    // 16:9 als natürliches Foto-Seitenverhältnis. aspectRatio statt fester Höhe,
    // damit der Slot mit der Bildschirmbreite skaliert.
    val photoRatio = 16f / 9f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(photoRatio)
            // clip vor background: schneidet die Ecken des Hintergrunds rund.
            .clip(MaterialTheme.shapes.medium)
            .background(BoulderBuddy.colors.surfaceCard)
            .clickable(onClick = onClick)
            // Gestrichelter Rahmen via drawBehind (nach background, damit er sichtbar
            // über dem Hintergrund liegt). Werte kommen aus Dimens.
            .drawBehind {
                drawRoundRect(
                    color = borderColor,
                    cornerRadius = CornerRadius(Dimens.radiusMedium.toPx()),
                    style = Stroke(
                        width = Dimens.borderAccent.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(Dimens.dashLength.toPx(), Dimens.dashGap.toPx()), 0f
                        )
                    )
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (imageUri != null && isVideo) {
            // Video ausgewählt: Play-Symbol + Hinweis statt (nicht vorhandenem) Frame.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Dimens.paddingS),
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayCircleOutline,
                    contentDescription = null,
                    tint = BoulderBuddy.colors.textTertiary,
                    modifier = Modifier.size(Dimens.iconL),
                )
                Text(
                    text = stringResource(R.string.medien_video_gewaehlt),
                    style = MaterialTheme.typography.labelMedium,
                    color = BoulderBuddy.colors.textTertiary,
                )
            }
        } else if (imageUri != null) {
            AsyncImage(
                model = imageUri,
                contentDescription = stringResource(R.string.medien_foto_gewaehlt),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(photoRatio)
                    .clip(MaterialTheme.shapes.medium),
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Dimens.paddingS),
            ) {
                Icon(
                    imageVector = Icons.Outlined.PhotoCamera,
                    // null: der sichtbare Label-Text beschreibt die Aktion bereits,
                    // doppelte Ansage für TalkBack vermeiden.
                    contentDescription = null,
                    tint = borderColor,
                    modifier = Modifier.size(Dimens.iconL),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = borderColor,
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFCF6E4, widthDp = 360)
@Composable
private fun PhotoPickerPreview() {
    BoulderBuddyTheme {
        PhotoPicker(
            onClick = {},
            modifier = Modifier.padding(Dimens.paddingL),
        )
    }
}
