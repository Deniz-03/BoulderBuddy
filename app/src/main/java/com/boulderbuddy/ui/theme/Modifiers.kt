package com.boulderbuddy.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

fun Modifier.dotPattern(
    dotColor: Color,
    dotRadius: Dp = Dimens.dotRadius,
    spacing: Dp = Dimens.dotSpacing,
): Modifier = this.drawBehind {
    val r = dotRadius.toPx()
    val s = spacing.toPx()
    var y = s / 2f
    while (y < size.height) {
        var x = s / 2f
        while (x < size.width) {
            drawCircle(dotColor, r, Offset(x, y))
            x += s
        }
        y += s
    }
}