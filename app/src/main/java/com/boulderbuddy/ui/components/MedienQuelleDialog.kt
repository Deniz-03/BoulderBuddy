package com.boulderbuddy.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import com.boulderbuddy.R
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.theme.Dimens

/**
 * Auswahl zwischen eigener Aufnahme und Galerie (Phase 7.4d).
 *
 * Bis dahin öffnete der Foto-Slot ausschließlich die Galerie, obwohl „Foto/Video aufnehmen"
 * darauf stand — die Beschriftung versprach etwas, das der Slot nicht konnte. Mit dem
 * CameraX-Screen gibt es beide Wege, also muss der Nutzer sie auch unterscheiden können.
 *
 * @param nurVideo blendet die Foto-Formulierung aus (Ghost Climber kann nur Video).
 */
@Composable
fun MedienQuelleDialog(
    onAufnehmen: () -> Unit,
    onGalerie: () -> Unit,
    onDismiss: () -> Unit,
    nurVideo: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (nurVideo) R.string.medien_titel_video else R.string.medien_titel,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.paddingS)) {
                QuellenZeile(
                    icon = Icons.Outlined.PhotoCamera,
                    titel = stringResource(
                        if (nurVideo) R.string.medien_video_aufnehmen
                        else R.string.medien_aufnehmen,
                    ),
                    erklaerung = stringResource(R.string.medien_aufnehmen_erklaerung),
                    onClick = onAufnehmen,
                )
                QuellenZeile(
                    icon = Icons.Outlined.PhotoLibrary,
                    titel = stringResource(R.string.medien_galerie),
                    erklaerung = stringResource(
                        if (nurVideo) R.string.medien_galerie_erklaerung_video
                        else R.string.medien_galerie_erklaerung,
                    ),
                    onClick = onGalerie,
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.aktion_abbrechen)) }
        },
    )
}

@Composable
private fun QuellenZeile(
    icon: ImageVector,
    titel: String,
    erklaerung: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Dimens.paddingS),
        horizontalArrangement = Arrangement.spacedBy(Dimens.paddingL),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            // null: der Titel daneben benennt die Aktion bereits.
            contentDescription = null,
            tint = BoulderBuddy.colors.textSecondary,
            modifier = Modifier.size(Dimens.iconL),
        )
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.paddingXS)) {
            Text(
                text = titel,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = erklaerung,
                style = MaterialTheme.typography.bodySmall,
                color = BoulderBuddy.colors.textSecondary,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFCF6E4)
@Composable
private fun MedienQuelleDialogPreview() {
    BoulderBuddyTheme {
        MedienQuelleDialog(onAufnehmen = {}, onGalerie = {}, onDismiss = {})
    }
}
