package com.boulderbuddy.sync.nearby

import android.content.Context
import android.os.ParcelFileDescriptor
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Was auf der Verbindung passiert. */
sealed interface Funkereignis {
    /** Ein Gerät wurde gefunden; die Verbindung wird angefragt. */
    data class Gefunden(val endpunkt: String, val name: String) : Funkereignis

    /**
     * Beide Seiten müssen dieselbe vierstellige Zahl bestätigen. Das ist der Schutz davor,
     * versehentlich das Tablet des Nachbarn zu füttern — Nearby prüft die Zahl nicht, der
     * Mensch tut es.
     */
    data class BestaetigungNoetig(
        val endpunkt: String,
        val name: String,
        val zahl: String,
    ) : Funkereignis

    data class Verbunden(val endpunkt: String) : Funkereignis
    data class Getrennt(val endpunkt: String) : Funkereignis
    data class Fehlgeschlagen(val grund: String) : Funkereignis

    /** Eine kleine Nachricht (BYTES). */
    data class NachrichtDa(val endpunkt: String, val nachricht: Nachricht) : Funkereignis

    /** Eine Datei ist vollständig angekommen. */
    data class DateiDa(val endpunkt: String, val payloadId: Long, val datei: File) : Funkereignis

    /** Fortschritt einer laufenden Übertragung. */
    data class Fortschritt(val uebertragen: Long, val gesamt: Long) : Funkereignis
}

/**
 * Der Funkweg (Sync-Plan S4) — und nur der.
 *
 * Hier steht ausschließlich, wie zwei Geräte einander finden, sich verbinden und Bytes
 * austauschen. **Was** gesprochen wird, steht in [Protokoll]; **was** damit passiert, in
 * `AbgleichSitzung`. Diese Trennung ist Absicht: der Funkteil ist der einzige, der ohne zwei
 * echte Geräte nicht zu prüfen ist, und er soll deshalb so wenig Entscheidungen wie möglich
 * enthalten.
 *
 * Beide Geräte werben **und** suchen gleichzeitig. Das ist die einfachste Art, ohne
 * Vorabsprache zueinanderzufinden: wer zuerst den anderen sieht, fragt an, und
 * `P2P_POINT_TO_POINT` lässt ohnehin nur eine Verbindung zu.
 */
@Singleton
class NearbyVerbindung @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val client by lazy { Nearby.getConnectionsClient(context) }
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Ereignisse als **Warteschlange**, nicht als `SharedFlow`.
     *
     * Ein `SharedFlow` mit `replay = 0` liefert nur an, wer gerade zuhört. Die Sitzung wartet
     * aber mit `first { … }`, und das bestellt zwischen zwei Aufrufen ab — jedes Ereignis in
     * so einer Lücke wäre verloren. Genau das passiert im Normalfall: während dieses Gerät
     * seinen eigenen Handschlag baut (Checkpoint plus DB-Lesen), trifft der Handschlag der
     * Gegenseite ein und fiele ins Leere. Der Abgleich wartete danach bis zum Zeitlimit auf
     * eine Nachricht, die längst da war.
     *
     * Ein Kanal puffert dagegen: Nichts geht verloren, und die Sitzung liest in ihrem Tempo.
     * Genau ein Leser (die Sitzung) — mehr braucht es nicht.
     */
    private var kanal = Channel<Funkereignis>(Channel.UNLIMITED)

    /** Nimmt das nächste Ereignis; wartet, wenn gerade keins da ist. */
    suspend fun naechstes(): Funkereignis = kanal.receive()

    /** Was gerade an Dateien unterwegs ist — Payload-Nummer → Zieldatei. */
    private val laufendeDateien = mutableMapOf<Long, Payload>()

    /**
     * Für das Übernehmen empfangener Dateien über den Dateideskriptor.
     *
     * Die Nearby-Rückrufe kommen auf dem Haupt-Thread an, und die kopierte Datei ist im
     * Zweifel der ganze Datenbestand — das darf dort nicht laufen. Ein eigener Scope statt
     * eines übergebenen: die Verbindung lebt als Singleton länger als jede Sitzung, und
     * [beende] soll ein laufendes Kopieren abbrechen können, ohne den Aufrufer zu betreffen.
     */
    private val kopierScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Wohin über den Deskriptor gerettete Dateien gelegt werden.
     *
     * Bewusst `cacheDir` und nicht der Empfangsordner des Abgleichs: dieser Weg gehört dem
     * Funkteil, nicht der Sitzung, und wenn ein Abbruch die Aufräum-Runde verpasst, holt das
     * System den Platz von allein zurück.
     */
    private val empfangsOrdner: File
        get() = File(context.cacheDir, EMPFANG_ORDNER).apply { mkdirs() }

    private var eigenerName: String = ""

    /**
     * Beginnt zu werben und zu suchen.
     *
     * @param anzeigename was das andere Gerät sieht. Der Geräte-Name, nicht die Geräte-ID —
     *   der Nutzer soll erkennen, mit wem er sich verbindet.
     */
    fun starte(anzeigename: String) {
        // Frischer Kanal je Sitzung: Reste eines abgebrochenen Laufs wuerden sonst als
        // Antworten des neuen durchgehen.
        kanal.close()
        kanal = Channel(Channel.UNLIMITED)
        eigenerName = anzeigename
        val optionen = Strategy.P2P_POINT_TO_POINT

        client.startAdvertising(
            anzeigename,
            DIENST_ID,
            verbindungsRueckruf,
            AdvertisingOptions.Builder().setStrategy(optionen).build(),
        ).addOnFailureListener { melde(Funkereignis.Fehlgeschlagen(werbenFehler(it))) }

        client.startDiscovery(
            DIENST_ID,
            suchRueckruf,
            DiscoveryOptions.Builder().setStrategy(optionen).build(),
        ).addOnFailureListener { melde(Funkereignis.Fehlgeschlagen(suchenFehler(it))) }
    }

    /** Nimmt die Verbindung an, nachdem der Nutzer die Zahl bestätigt hat. */
    fun bestaetige(endpunkt: String) {
        client.acceptConnection(endpunkt, nutzdatenRueckruf)
    }

    fun lehneAb(endpunkt: String) {
        client.rejectConnection(endpunkt)
    }

    fun sende(endpunkt: String, nachricht: Nachricht) {
        val bytes = json.encodeToString<Nachricht>(nachricht).toByteArray()
        require(bytes.size < BYTES_OBERGRENZE) {
            "Nachricht zu groß für eine BYTES-Payload — sie gehört als Datei übertragen."
        }
        client.sendPayload(endpunkt, Payload.fromBytes(bytes))
    }

    /**
     * Schickt eine Datei und kündigt sie vorher an.
     *
     * Die Ankündigung muss vor der Datei losgehen, aber Nearby garantiert die Reihenfolge
     * ohnehin nur je Payload — deshalb trägt die Ankündigung die Payload-Nummer, und die
     * Gegenseite kann beides in beliebiger Reihenfolge zusammenbringen.
     */
    fun sendeDatei(endpunkt: String, datei: File, art: DateiArt, name: String = "") {
        val payload = Payload.fromFile(datei)
        sende(
            endpunkt,
            Nachricht.DateiFolgt(
                payloadId = payload.id,
                art = art,
                name = name.ifEmpty { datei.name },
                groesse = datei.length(),
            ),
        )
        client.sendPayload(endpunkt, payload)
    }

    /** Beendet alles: Werben, Suchen, Verbindungen. Auch im Fehlerfall aufzurufen. */
    fun beende() {
        runCatching { client.stopAdvertising() }
        runCatching { client.stopDiscovery() }
        runCatching { client.stopAllEndpoints() }
        laufendeDateien.clear()
        // Erst das Kopieren stoppen, dann wegräumen — andersherum legt eine noch laufende
        // Kopie gleich wieder eine Datei an.
        kopierScope.coroutineContext.cancelChildren()
        runCatching { empfangsOrdner.listFiles()?.forEach { it.delete() } }
    }

    private val suchRueckruf = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpunkt: String, info: DiscoveredEndpointInfo) {
            melde(Funkereignis.Gefunden(endpunkt, info.endpointName))
            client.requestConnection(eigenerName, endpunkt, verbindungsRueckruf)
                .addOnFailureListener {
                    // Häufig und harmlos: beide haben gleichzeitig angefragt, eine Anfrage
                    // gewinnt. Der Verbindungs-Rückruf meldet den Erfolg dann trotzdem.
                }
        }

        override fun onEndpointLost(endpunkt: String) {
            melde(Funkereignis.Getrennt(endpunkt))
        }
    }

    private val verbindungsRueckruf = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpunkt: String, info: ConnectionInfo) {
            melde(
                Funkereignis.BestaetigungNoetig(
                    endpunkt = endpunkt,
                    name = info.endpointName,
                    zahl = info.authenticationDigits,
                ),
            )
        }

        override fun onConnectionResult(endpunkt: String, ergebnis: ConnectionResolution) {
            when (ergebnis.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    // Ab hier kommt niemand mehr dazu — und das Suchen kostet nur noch Akku.
                    runCatching { client.stopDiscovery() }
                    runCatching { client.stopAdvertising() }
                    melde(Funkereignis.Verbunden(endpunkt))
                }

                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED ->
                    melde(Funkereignis.Fehlgeschlagen("Die Verbindung wurde abgelehnt."))

                else -> melde(
                    Funkereignis.Fehlgeschlagen(
                        "Die Verbindung ist nicht zustande gekommen. " +
                            "Halte die Geräte nah beieinander und versuche es erneut.",
                    ),
                )
            }
        }

        override fun onDisconnected(endpunkt: String) {
            melde(Funkereignis.Getrennt(endpunkt))
        }
    }

    private val nutzdatenRueckruf = object : PayloadCallback() {
        override fun onPayloadReceived(endpunkt: String, payload: Payload) {
            when (payload.type) {
                Payload.Type.BYTES -> {
                    val bytes = payload.asBytes() ?: return
                    val nachricht = runCatching {
                        json.decodeFromString<Nachricht>(String(bytes))
                    }.getOrNull() ?: return
                    melde(Funkereignis.NachrichtDa(endpunkt, nachricht))
                }

                // Dateien werden erst gemeldet, wenn sie VOLLSTÄNDIG da sind. Hier ist die
                // Übertragung gerade erst angekündigt; der Inhalt kommt später.
                Payload.Type.FILE -> laufendeDateien[payload.id] = payload

                else -> Unit
            }
        }

        override fun onPayloadTransferUpdate(endpunkt: String, update: PayloadTransferUpdate) {
            when (update.status) {
                PayloadTransferUpdate.Status.IN_PROGRESS ->
                    melde(Funkereignis.Fortschritt(update.bytesTransferred, update.totalBytes))

                PayloadTransferUpdate.Status.SUCCESS -> {
                    val payload = laufendeDateien.remove(update.payloadId) ?: return
                    uebernimmDatei(endpunkt, update.payloadId, payload)
                }

                PayloadTransferUpdate.Status.FAILURE,
                PayloadTransferUpdate.Status.CANCELED,
                -> {
                    laufendeDateien.remove(update.payloadId)
                    melde(Funkereignis.Fehlgeschlagen("Eine Übertragung ist abgebrochen."))
                }
            }
        }
    }

    /**
     * Macht aus einer fertig übertragenen FILE-Payload eine Datei, die der Abgleich lesen kann.
     *
     * Zwei Wege, in dieser Reihenfolge:
     *
     * 1. **`asJavaFile()`** — die billige Variante ohne Kopie. Sie liefert aber **ab Android 11
     *    `null`**, weil Nearby die Payload dann nicht mehr in einen für die App direkt
     *    erreichbaren Pfad legt. Genau hier wäre der Abgleich am echten Gerät gescheitert:
     *    ohne Fallback endete jede empfangene Datenbank als „ließ sich nicht öffnen".
     * 2. **`asParcelFileDescriptor()`** — der Deskriptor gilt auf allen Versionen. Er lässt
     *    sich aber nicht herumreichen, also wird der Inhalt einmal in den eigenen Cache
     *    kopiert. Das kostet Platz in Höhe der Datei; deshalb prüft [AbgleichDateien.genugPlatz]
     *    ohnehin gegen ein Vielfaches.
     *
     * Das Kopieren läuft im Hintergrund und meldet erst danach — der Rückruf kommt auf dem
     * Haupt-Thread, und die Datei ist im Zweifel der ganze Datenbestand. Die Sitzung wartet
     * ohnehin auf das Ereignis aus der Warteschlange, für sie ändert sich dadurch nichts.
     */
    private fun uebernimmDatei(endpunkt: String, payloadId: Long, payload: Payload) {
        val datei = payload.asFile()
        // Veraltet, aber bewusst zuerst versucht: solange Nearby einen erreichbaren Pfad
        // liefert, spart dieser Weg das Kopieren der ganzen Datenbank.
        @Suppress("DEPRECATION")
        val direkt = runCatching { datei?.asJavaFile() }.getOrNull()
        if (direkt != null) {
            melde(Funkereignis.DateiDa(endpunkt, payloadId, direkt))
            return
        }

        val deskriptor = runCatching { datei?.asParcelFileDescriptor() }.getOrNull()
        if (deskriptor == null) {
            melde(Funkereignis.Fehlgeschlagen("Eine empfangene Datei ließ sich nicht öffnen."))
            return
        }

        val erwartet = datei?.size ?: 0L
        kopierScope.launch {
            val ziel = File(empfangsOrdner, "payload-$payloadId")
            val ergebnis = runCatching {
                ParcelFileDescriptor.AutoCloseInputStream(deskriptor).use { quelle ->
                    kopiereGepruft(quelle, ziel, erwartet)
                }
                ziel
            }
            ergebnis.fold(
                onSuccess = { melde(Funkereignis.DateiDa(endpunkt, payloadId, it)) },
                onFailure = { fehler ->
                    ziel.delete()
                    melde(
                        Funkereignis.Fehlgeschlagen(
                            "Eine empfangene Datei ließ sich nicht ablegen: ${fehler.message}",
                        ),
                    )
                },
            )
        }
    }

    private fun melde(ereignis: Funkereignis) {
        kanal.trySend(ereignis)
    }

    private fun werbenFehler(fehler: Exception): String =
        "Dieses Gerät konnte sich nicht sichtbar machen: ${fehler.message}"

    private fun suchenFehler(fehler: Exception): String =
        "Die Suche nach dem anderen Gerät ist fehlgeschlagen: ${fehler.message}"

    companion object {
        /**
         * Kennung des Dienstes. Nur Geräte mit derselben Kennung finden sich — deshalb der
         * Paketname, und deshalb muss sie beim Ändern auf beiden Geräten gleich bleiben.
         */
        const val DIENST_ID = "com.boulderbuddy.abgleich"

        /** Unterordner in `cacheDir` für Dateien, die über den Deskriptor kommen. */
        const val EMPFANG_ORDNER = "nearby-empfang"
    }
}

/**
 * Kopiert einen Strom in eine Datei und **besteht auf der erwarteten Länge**.
 *
 * Der Zähler ist kein Zierrat: die empfangene Datei ist beim Abgleich die Datenbank der
 * Gegenseite, und eine abgeschnittene Kopie sieht aus wie eine gültige — SQLite öffnet sie,
 * es fehlen nur die letzten Einträge. Lieber ein Abbruch mit Meldung als ein Stand, dem
 * hinterher niemand ansieht, dass er unvollständig ist.
 *
 * @param erwartet die Sollgröße; `0` oder kleiner heißt „unbekannt" und schaltet die Prüfung ab.
 */
internal fun kopiereGepruft(quelle: InputStream, ziel: File, erwartet: Long): Long {
    ziel.parentFile?.mkdirs()
    val geschrieben = ziel.outputStream().use { quelle.copyTo(it) }
    if (erwartet > 0 && geschrieben != erwartet) {
        throw IOException("unvollständig: $geschrieben von $erwartet Bytes")
    }
    return geschrieben
}
