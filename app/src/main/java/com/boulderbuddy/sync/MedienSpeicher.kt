package com.boulderbuddy.sync

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ablage der Medien unter ihrem Inhalt (Sync-Plan E5): `filesDir/aufnahmen/<sha256>.<endung>`.
 *
 * Alles, was mit Dateien und `ContentResolver` zu tun hat, liegt hier; die Namensregeln
 * stehen android-frei in [MedienNamen].
 */
@Singleton
class MedienSpeicher @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private val ordner = File(context.filesDir, MEDIEN_ORDNER)

    /** Alle vorhandenen Mediendateien — die Liste, die beim Abgleich getauscht wird. */
    fun vorhandeneNamen(): Set<String> =
        ordner.listFiles()?.map { it.name }?.toSet().orEmpty()

    fun datei(name: String): File = File(ordner, name)

    fun uriFuer(name: String): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", datei(name))

    /**
     * Holt ein Medium in die App und gibt seine neue URI zurück.
     *
     * Liegt es schon inhaltsadressiert vor, passiert nichts — der Aufruf ist idempotent und
     * der Umzug damit wiederholbar. Ist die Quelle nicht mehr lesbar (abgelaufene
     * Galerie-Berechtigung, gelöschte Datei), kommt `null` zurück; der Aufrufer lässt die
     * Zeile dann in Ruhe, statt sie kaputtzuschreiben.
     */
    suspend fun uebernehme(quelle: String): String? = withContext(Dispatchers.IO) {
        val uri = runCatching { quelle.toUri() }.getOrNull() ?: return@withContext null
        if (istSchonUebernommen(uri)) return@withContext quelle

        ordner.mkdirs()
        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        val anzeigename = anzeigenameVon(uri)

        // Erst in eine Zwischendatei schreiben und dabei hashen: der endgültige Name steht
        // ja erst fest, wenn der Inhalt durchgelaufen ist.
        val zwischen = File(ordner, "uebernahme_${System.nanoTime()}.tmp")
        val hash = try {
            oeffne(uri)?.use { ein -> schreibeUndHashe(ein, zwischen) }
        } catch (e: Exception) {
            zwischen.delete()
            return@withContext null
        } ?: run {
            zwischen.delete()
            return@withContext null
        }

        val ziel = File(ordner, medienDateiname(hash, endungFuer(mime, anzeigename)))
        if (ziel.exists()) {
            // Dieselbe Datei war schon da — zweimal dasselbe Video gewählt, oder der Umzug
            // lief schon einmal. Der Inhalt ist per Definition identisch.
            zwischen.delete()
        } else if (!zwischen.renameTo(ziel)) {
            zwischen.copyTo(ziel, overwrite = true)
            zwischen.delete()
        }

        // Die alte app-eigene Datei wird hier NICHT gelöscht: solange „Letzten Abgleich
        // rückgängig machen" möglich sein soll, hält `vorher.db` sie noch (E11/E13).
        // Aufräumen ist eine eigene, ausdrückliche Entscheidung des Nutzers.
        uriFuer(ziel.name).toString()
    }

    private fun istSchonUebernommen(uri: Uri): Boolean {
        val name = uri.lastPathSegment ?: return false
        return istInhaltsadressiert(name) && datei(name).exists()
    }

    private fun oeffne(uri: Uri): InputStream? =
        runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()

    private fun anzeigenameVon(uri: Uri): String? {
        uri.lastPathSegment?.takeIf { it.contains('.') }?.let { return it }
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()
    }

    private fun schreibeUndHashe(ein: InputStream, ziel: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        ziel.outputStream().use { aus ->
            val puffer = ByteArray(64 * 1024)
            while (true) {
                val gelesen = ein.read(puffer)
                if (gelesen <= 0) break
                digest.update(puffer, 0, gelesen)
                aus.write(puffer, 0, gelesen)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
