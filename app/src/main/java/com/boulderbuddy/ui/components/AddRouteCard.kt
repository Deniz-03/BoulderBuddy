package com.boulderbuddy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.theme.Dimens

// Gestrichelte Platzhalter-Kachel im RouteCard-Grid der aktiven Session.
// Sie belegt dieselbe Grid-Zelle wie eine RouteCard und öffnet "Route hinzufügen".
// onClick gehört zum Kern-UI dieser Komponente — sie IST ein Button,
// anders als RouteCard, wo die Navigation Screen-Sache ist.
@Composable
fun AddRouteCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = BoulderBuddy.colors.borderSubtle

    Box(
        modifier = modifier
            .fillMaxWidth()
            // clip vor background: schneidet Ecken des Hintergrunds korrekt rund
            .clip(MaterialTheme.shapes.medium)
            .background(BoulderBuddy.colors.surfaceCard)
            .clickable(onClick = onClick)
            // BorderStroke unterstützt kein PathEffect, daher gestrichelter Rahmen via drawBehind.
            // drawBehind kommt nach background, damit der Strich sichtbar über dem Hintergrund liegt.
            // Der Eckradius (14dp) ist hartkodiert, weil DrawScope keinen Zugriff auf
            // MaterialTheme hat — entspricht shapes.medium aus Shape.kt.
            .drawBehind {
                drawRoundRect(
                    color = borderColor,
                    cornerRadius = CornerRadius(14.dp.toPx()),
                    style = Stroke(
                        width = Dimens.borderAccent.toPx(),
                        // 10dp Strich, 6dp Lücke: lesbar, aber nicht zu kleinteilig
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(10.dp.toPx(), 6.dp.toPx()), 0f
                        )
                    )
                )
            }
            .padding(Dimens.paddingXL),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.paddingS),
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = "Route hinzufügen",
                tint = borderColor,
                modifier = Modifier.size(28.dp),
            )
            Text(
                text = "Boulder",
                style = MaterialTheme.typography.labelMedium,
                color = borderColor,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF9F4E3)
@Composable
private fun AddRouteCardPreview() {
    BoulderBuddyTheme {
        AddRouteCard(onClick = {})
    }
}
