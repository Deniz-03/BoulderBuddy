package com.boulderbuddy.util

import android.content.Context
import androidx.core.net.toUri

/**
 * Medientyp einer Routen-`mediaUri`. Bewusst KEIN DB-Feld (Phase 7.3c-Entscheidung):
 * `RouteEntity.mediaUri` bleibt die Single Source of Truth, der Typ wird zur Laufzeit
 * aus der URI abgeleitet — so kann er nie mit der tatsächlichen Datei driften.
 */
enum class MediaType { IMAGE, VIDEO }

/**
 * Leitet den [MediaType] einer content-URI über ihren MIME-Typ ab
 * (`content://`-URIs vom PhotoPicker beantworten `getType` zuverlässig).
 *
 * Fallback = [MediaType.IMAGE]: ist der MIME-Typ unbekannt (z.B. abgelaufene URI ohne
 * Leserecht), rendert die App wie bisher als Bild — der sichere, bestehende Pfad.
 */
fun mediaTypeOf(context: Context, uri: String?): MediaType {
    if (uri == null) return MediaType.IMAGE
    val mime = runCatching { context.contentResolver.getType(uri.toUri()) }.getOrNull()
    return if (mime?.startsWith("video/") == true) MediaType.VIDEO else MediaType.IMAGE
}
