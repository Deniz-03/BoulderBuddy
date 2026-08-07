package com.boulderbuddy.sync

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Der Vergleich aus Sync-Plan S2, geprüft an den Abläufen, die ihn geformt haben (S9).
 *
 * Jeder Test benennt den Ablauf, aus dem er stammt — wer eine Zeile hier ändert, soll im
 * Plan nachlesen können, warum sie so lautet.
 */
class AbgleichTest {

    // -- Hilfen ------------------------------------------------------------

    private fun text(s: String) = Feld.Text(s)
    private fun zahl(n: Int) = Feld.Zahl(n.toLong())

    private fun gym(name: String, ort: String? = null): Zeile = mapOf(
        "name" to text(name),
        "location" to (ort?.let { text(it) } ?: Feld.Leer),
    )

    private fun session(gymId: Int, datum: Int, notiz: String? = null): Zeile = mapOf(
        "gymId" to zahl(gymId),
        "gradeSystemId" to Feld.Leer,
        "date" to zahl(datum),
        "durationMin" to Feld.Leer,
        "notes" to (notiz?.let { text(it) } ?: Feld.Leer),
        "endedAt" to Feld.Leer,
    )

    private fun route(sessionId: Int, name: String, gradeId: Int? = null): Zeile = mapOf(
        "sessionId" to zahl(sessionId),
        "gradeId" to (gradeId?.let { zahl(it) } ?: Feld.Leer),
        "name" to text(name),
        "sektor" to Feld.Leer,
        "attempts" to zahl(1),
        "status" to text("TOP"),
        "color" to Feld.Leer,
        "mediaUri" to Feld.Leer,
        "notes" to Feld.Leer,
    )

    private fun grade(systemId: Int, label: String): Zeile = mapOf(
        "systemId" to zahl(systemId),
        "label" to text(label),
        "sortOrder" to zahl(0),
    )

    private fun gradeSystem(gymId: Int?, name: String): Zeile = mapOf(
        "gymId" to (gymId?.let { zahl(it) } ?: Feld.Leer),
        "name" to text(name),
    )

    private fun ghost(refPfad: String, cmpPfad: String): Zeile = mapOf(
        "refMediaUri" to text("content://x/a.mp4"),
        "cmpMediaUri" to text("content://x/b.mp4"),
        "refKeypointsPath" to text(refPfad),
        "cmpKeypointsPath" to text(cmpPfad),
        "homographyCmpJson" to text("[]"),
        "routePathJson" to text("[]"),
        "suggestedMode" to text("OVERLAY"),
        "createdAt" to zahl(1000),
    )

    private fun stand(vararg tabellen: Pair<String, Map<Int, Zeile>>) = Stand(tabellen.toMap())

    private fun workout(sessionId: Int?, start: Int): Zeile = mapOf(
        "sessionId" to (sessionId?.let { zahl(it) } ?: Feld.Leer),
        "mode" to text("MANUAL"),
        "origin" to text("PHONE"),
        "startedAt" to zahl(start),
        "endedAt" to zahl(start + 100),
        "plannedSets" to zahl(6),
        "plannedHangSec" to zahl(7),
        "plannedRestSec" to zahl(3),
    )

    private fun segment(workoutId: Int, index: Int): Zeile = mapOf(
        "workoutId" to zahl(workoutId),
        "setIndex" to zahl(index),
        "hangMs" to zahl(7000),
        "restMs" to zahl(3000),
    )

    private fun urteilFuer(plan: Abgleichplan, tabelle: String, id: Int): Urteil? =
        plan.befunde.firstOrNull { it.tabelle == tabelle && it.id == id }?.urteil

    /**
     * Spielt Operationen auf einem Stand nach — nur für die Tests, die über **zwei**
     * Abgleiche laufen. Ohne das ließe sich nicht prüfen, was der zweite Durchlauf sagt,
     * und genau dort schlugen in Runde 4 und 5 die teuersten Fehler zu.
     */
    private fun wendeAn(stand: Stand, operationen: List<Operation>): Stand {
        val tabellen = STAND_TABELLEN.associate { it.name to stand.zeilen(it.name).toMutableMap() }
        for (op in operationen) {
            val zeilen = tabellen.getValue(op.tabelle)
            when (op) {
                is Operation.Einfuegen -> zeilen[op.id] = op.zeile
                is Operation.Aendern -> zeilen[op.id] = op.zeile
                is Operation.Loeschen -> zeilen.remove(op.id)
            }
        }
        return Stand(tabellen.mapValues { (_, v) -> v.toMap() })
    }

    // -- Schema und Lage ---------------------------------------------------

    @Test
    fun hoeheres_schema_der_gegenseite_bricht_ab_statt_zu_raten() {
        assertThat(darfIchLesen(meinSchema = 7, fremdesSchema = 8))
            .isEqualTo(Schemapruefung.DiesesGeraetAktualisieren)
        assertThat(darfIchLesen(meinSchema = 8, fremdesSchema = 7))
            .isEqualTo(Schemapruefung.GegenseiteAktualisieren)
        assertThat(darfIchLesen(meinSchema = 7, fremdesSchema = 7))
            .isEqualTo(Schemapruefung.Passt)
    }

    @Test
    fun ohne_herkunft_auf_einer_seite_ist_es_eine_erstbegegnung() {
        val meta = StandMeta(generation = 3, erzeugtVon = "phone", basiertAuf = 2)

        assertThat(lage(null, null)).isEqualTo(Lage.Erstbegegnung)
        assertThat(lage(meta, null)).isEqualTo(Lage.Erstbegegnung)
        assertThat(lage(null, meta)).isEqualTo(Lage.Erstbegegnung)
    }

    @Test
    fun gleiche_herkunft_heisst_gemeinsamer_stand() {
        // Ablauf 32: nach einem Abgleich tragen BEIDE Geräte dieselbe Herkunft. Setzte
        // jedes sein eigenes erzeugtVon, läse die Lagebestimmung hier „auseinandergelaufen".
        val meta = StandMeta(generation = 3, erzeugtVon = "phone", basiertAuf = 2)

        assertThat(lage(meta, meta.copy())).isEqualTo(Lage.GemeinsamerStand(3, "phone"))
        assertThat(lage(meta, meta.copy(erzeugtVon = "tablet")))
            .isEqualTo(Lage.KeinGemeinsamerStand)
        assertThat(lage(meta, meta.copy(generation = 4)))
            .isEqualTo(Lage.KeinGemeinsamerStand)
    }

    @Test
    fun der_dateiweg_erkennt_dass_die_gegenseite_einen_schritt_weiter_ist() {
        // Über die Datei läuft der Abgleich zwangsläufig in zwei Durchgängen: erst liest
        // ein Gerät ein und rechnet (Generation 4), dann das andere. Beim zweiten Durchgang
        // steht hier noch Generation 3 — das ist kein Auseinanderlaufen, sondern der
        // Normalfall dieses Weges.
        val meine = StandMeta(generation = 3, erzeugtVon = "phone", basiertAuf = 2)
        val fremde = StandMeta(generation = 4, erzeugtVon = "tablet", basiertAuf = 3)

        assertThat(lage(meine, fremde)).isEqualTo(Lage.GegenseiteWeiter(4))
        // Und andersherum: die eingelesene Datei ist älter als der eigene Stand.
        assertThat(lage(fremde, meine)).isEqualTo(Lage.IchBinWeiter)
    }

    @Test
    fun zwei_durchgaenge_ueber_die_datei_bringen_beide_geraete_zusammen() {
        // Der vollständige Datei-Weg: Phone gibt ab, Tablet liest ein und rechnet, dann
        // gibt das Tablet ab und das Phone liest ein. Danach müssen beide gleich sein.
        val basis = stand(
            "gym" to mapOf(1 to gym("Halle Nord")),
        )
        val phone = stand(
            "gym" to mapOf(1 to gym("Halle Nord")),
            "session" to mapOf(2 to session(1, 100)),
        )
        val tablet = stand(
            "gym" to mapOf(1 to gym("Halle Nord")),
            "ghost_analysis" to mapOf(1_000_001 to ghost("ghost/a.json", "ghost/b.json")),
        )

        // Durchgang 1: das Tablet liest die Datei des Phones ein und rechnet.
        val amTablet = anwenden(abgleich(basis, tablet, phone), Seite.MEINS)
        val tabletNachher = wendeAn(tablet, amTablet.fuerMich)

        // Das Phone weiß davon noch nichts — seine Basis ist unverändert die alte.
        // Durchgang 2: das Phone liest die Datei des Tablets ein.
        val amPhone = anwenden(abgleich(basis, phone, tabletNachher), Seite.MEINS)
        val phoneNachher = wendeAn(phone, amPhone.fuerMich)

        for (tabelle in STAND_TABELLEN) {
            assertThat(phoneNachher.zeilen(tabelle.name))
                .isEqualTo(tabletNachher.zeilen(tabelle.name))
        }
        // Beide haben beides — niemand hat etwas verloren.
        assertThat(phoneNachher.zeile("session", 2)).isNotNull()
        assertThat(phoneNachher.zeile("ghost_analysis", 1_000_001)).isNotNull()
    }

    // -- Ablauf 4: beide ergänzen, beides bleibt ---------------------------

    @Test
    fun beidseitige_ergaenzungen_sind_kein_konflikt() {
        // Ablauf 4 — die gewollte Arbeitsteilung: Analyse am Tablet, Session am Phone.
        // Ein reines Ganz-Ersetzen ließe hier zwischen beidem wählen.
        val basis = stand("gym" to mapOf(1 to gym("Halle Nord")))
        val meine = stand(
            "gym" to mapOf(1 to gym("Halle Nord")),
            "session" to mapOf(2 to session(1, 100)),
        )
        val fremde = stand(
            "gym" to mapOf(1 to gym("Halle Nord")),
            "ghost_analysis" to mapOf(1_000_000_001 to ghost("ghost/a.json", "ghost/b.json")),
        )

        val plan = abgleich(basis, meine, fremde)
        assertThat(plan.konflikte).isEmpty()
        assertThat(plan.veraenderung).isEqualTo(Veraenderung.BEIDE)

        val an = anwenden(plan, Seite.MEINS)
        // Jede Seite bekommt genau das, was ihr fehlt — und niemand verliert etwas.
        assertThat(an.fuerMich).containsExactly(
            Operation.Einfuegen(
                "ghost_analysis",
                1_000_000_001,
                ghost("ghost/a.json", "ghost/b.json"),
            ),
        )
        assertThat(an.fuerDieGegenseite).containsExactly(
            Operation.Einfuegen("session", 2, session(1, 100)),
        )
        assertThat(an.bilanz.geloescht).isEqualTo(0)
    }

    // -- Ablauf 3: Löschung propagiert ------------------------------------

    @Test
    fun eine_loeschung_gilt_fuer_beide_geraete() {
        val basis = stand(
            "gym" to mapOf(1 to gym("Halle Nord")),
            "session" to mapOf(2 to session(1, 100)),
        )
        val meine = stand("gym" to mapOf(1 to gym("Halle Nord")))
        val fremde = basis

        val plan = abgleich(basis, meine, fremde)
        assertThat(urteilFuer(plan, "session", 2)).isEqualTo(Urteil.MEINS_GELOESCHT)

        val an = anwenden(plan, Seite.MEINS)
        assertThat(an.fuerMich).isEmpty()
        assertThat(an.fuerDieGegenseite).containsExactly(Operation.Loeschen("session", 2))
        // Die Bilanz muss Löschungen benennen — sonst wirkt es wie ein Fehler (Ablauf 3).
        assertThat(an.bilanz.geloescht).isEqualTo(1)
    }

    @Test
    fun geloescht_unterscheidet_sich_von_nie_gehabt() {
        // Ohne das Gedächtnis aus basis.db wäre beides „die eine Seite hat es nicht".
        val zeileDa = stand(
            "gym" to mapOf(1 to gym("Halle Nord")),
            "session" to mapOf(2 to session(1, 100)),
        )
        val zeileWeg = stand("gym" to mapOf(1 to gym("Halle Nord")))

        // Die Basis kannte sie ⇒ gelöscht ⇒ verschwindet überall.
        val geloescht = anwenden(abgleich(zeileDa, zeileWeg, zeileDa), Seite.MEINS)
        assertThat(geloescht.fuerDieGegenseite).containsExactly(Operation.Loeschen("session", 2))

        // Die Basis kannte sie nicht ⇒ neu ⇒ kommt herüber.
        val neu = anwenden(abgleich(zeileWeg, zeileWeg, zeileDa), Seite.MEINS)
        assertThat(neu.fuerMich).containsExactly(
            Operation.Einfuegen("session", 2, session(1, 100)),
        )
    }

    // -- Ablauf 15: Abbruch nach dem Anwenden ------------------------------

    @Test
    fun gleiche_nummer_gleicher_inhalt_der_basis_unbekannt_ist_einig() {
        // Ablauf 15: der Abgleich brach ab, bevor basis.db geschrieben war. Die Zeilen
        // sähen sonst beim nächsten Mal „neu" aus, obwohl beide sie längst haben.
        val basis = stand("gym" to mapOf(1 to gym("Halle Nord")))
        val beide = stand(
            "gym" to mapOf(1 to gym("Halle Nord")),
            "session" to mapOf(2 to session(1, 100)),
        )

        val plan = abgleich(basis, beide, beide)
        assertThat(urteilFuer(plan, "session", 2)).isEqualTo(Urteil.EINIG)
        assertThat(plan.konflikte).isEmpty()

        val an = anwenden(plan, Seite.MEINS)
        assertThat(an.fuerMich).isEmpty()
        assertThat(an.fuerDieGegenseite).isEmpty()
        assertThat(an.bilanz.nichtsZuTun).isTrue()
    }

    // -- Ablauf 7: gleiche Nummer, verschiedene Zeilen ---------------------

    @Test
    fun gleiche_nummer_fuer_verschiedene_zeilen_wird_gefragt_statt_geraten() {
        val basis = stand("gym" to mapOf(1 to gym("Halle Nord")))
        val meine = stand(
            "gym" to mapOf(1 to gym("Halle Nord"), 2 to gym("Halle Süd")),
        )
        val fremde = stand(
            "gym" to mapOf(1 to gym("Halle Nord"), 2 to gym("Kletterwerk")),
        )

        val plan = abgleich(basis, meine, fremde)
        assertThat(plan.konflikte).hasSize(1)
        assertThat(plan.konflikte.single().art).isEqualTo(KonfliktArt.GLEICHE_NUMMER)
    }

    // -- Ablauf 8: Teilbaum gelöscht gegen Teilbaum ergänzt ----------------

    @Test
    fun session_geloescht_gegen_boulder_ergaenzt_ist_ein_konflikt() {
        // Zeilenweise wäre das kein Widerspruch — es ergäbe einen Boulder ohne Session.
        val basis = stand(
            "gym" to mapOf(1 to gym("Halle Nord")),
            "session" to mapOf(2 to session(1, 100)),
            "route" to mapOf(3 to route(2, "Dachrinne")),
        )
        val meine = stand(
            "gym" to mapOf(1 to gym("Halle Nord")),
        )
        val fremde = stand(
            "gym" to mapOf(1 to gym("Halle Nord")),
            "session" to mapOf(2 to session(1, 100)),
            "route" to mapOf(
                3 to route(2, "Dachrinne"),
                1_000_000_004 to route(2, "Kante"),
            ),
        )

        val plan = abgleich(basis, meine, fremde)
        val konflikt = plan.konflikte.single()
        assertThat(konflikt.tabelle).isEqualTo("session")
        assertThat(konflikt.id).isEqualTo(2)
        assertThat(konflikt.art).isEqualTo(KonfliktArt.TEILBAUM)
    }

    @Test
    fun beim_teilbaum_konflikt_nimmt_die_loeschung_den_ganzen_ast_mit() {
        val basis = stand(
            "gym" to mapOf(1 to gym("Halle Nord")),
            "session" to mapOf(2 to session(1, 100)),
            "route" to mapOf(3 to route(2, "Dachrinne")),
        )
        val meine = stand("gym" to mapOf(1 to gym("Halle Nord")))
        val fremde = stand(
            "gym" to mapOf(1 to gym("Halle Nord")),
            "session" to mapOf(2 to session(1, 100)),
            "route" to mapOf(
                3 to route(2, "Dachrinne"),
                1_000_000_004 to route(2, "Kante"),
            ),
        )

        val loeschenGewinnt = anwenden(abgleich(basis, meine, fremde), Seite.MEINS)
        // Nichts bleibt hängen: der neue Boulder der Gegenseite geht mit der Session.
        assertThat(loeschenGewinnt.fuerMich).isEmpty()
        assertThat(loeschenGewinnt.fuerDieGegenseite).containsExactly(
            Operation.Loeschen("route", 1_000_000_004),
            Operation.Loeschen("route", 3),
            Operation.Loeschen("session", 2),
        ).inOrder()

        val behaltenGewinnt = anwenden(abgleich(basis, meine, fremde), Seite.FREMDES)
        // Die Session kommt zurück, bevor die Boulder auf sie zeigen.
        assertThat(behaltenGewinnt.fuerMich).containsExactly(
            Operation.Einfuegen("session", 2, session(1, 100)),
            Operation.Einfuegen("route", 3, route(2, "Dachrinne")),
            Operation.Einfuegen("route", 1_000_000_004, route(2, "Kante")),
        ).inOrder()
    }

    @Test
    fun eine_verlorene_loeschung_bringt_auch_die_enkel_zurueck() {
        // Session → Workout → Sätze: die Löschung der Session riss lokal alles mit. Verliert
        // sie den Konflikt, muss der ganze Ast zurückkommen, nicht nur die erste Ebene.
        val vollstaendig = stand(
            "gym" to mapOf(1 to gym("Halle Nord")),
            "session" to mapOf(2 to session(1, 100)),
            "hangboard_workout" to mapOf(3 to workout(2, 100)),
            "hangboard_segment" to mapOf(4 to segment(3, 0)),
        )
        // Auf diesem Gerät wurde die Session gelöscht — alles darunter ging per CASCADE mit.
        val meine = stand("gym" to mapOf(1 to gym("Halle Nord")))
        // Auf dem anderen Gerät kam ein Boulder dazu ⇒ Teilbaum-Konflikt.
        val fremde = stand(
            "gym" to mapOf(1 to gym("Halle Nord")),
            "session" to mapOf(2 to session(1, 100)),
            "hangboard_workout" to mapOf(3 to workout(2, 100)),
            "hangboard_segment" to mapOf(4 to segment(3, 0)),
            "route" to mapOf(1_000_000_005 to route(2, "Kante")),
        )

        val plan = abgleich(vollstaendig, meine, fremde)
        assertThat(plan.konflikte.single().art).isEqualTo(KonfliktArt.TEILBAUM)

        val an = anwenden(plan, Seite.FREMDES)
        val zurueck = an.fuerMich.filterIsInstance<Operation.Einfuegen>().map { it.tabelle to it.id }
        assertThat(zurueck).containsAtLeast(
            "session" to 2,
            "hangboard_workout" to 3,
            "hangboard_segment" to 4,
        )
        // Und die Gegenseite verliert nichts — sie hatte den Ast ja durchgehend.
        assertThat(an.fuerDieGegenseite).isEmpty()
    }

    // -- Ablauf 23: die Antwort gilt nur den Konflikten --------------------

    @Test
    fun die_konfliktantwort_laesst_unstrittige_ergaenzungen_unangetastet() {
        // Sonst verlöre der Nutzer einen ganzen Klettertag, weil er bei EINEM Eintrag
        // „Tablet" gesagt hat.
        val basis = stand(
            "gym" to mapOf(1 to gym("Halle Nord")),
        )
        val meine = stand(
            "gym" to mapOf(1 to gym("Halle Nord", "Berlin")),
            "session" to mapOf(2 to session(1, 100)),
        )
        val fremde = stand(
            "gym" to mapOf(1 to gym("Halle Nord", "Potsdam")),
            "ghost_analysis" to mapOf(1_000_000_001 to ghost("ghost/a.json", "ghost/b.json")),
        )

        val plan = abgleich(basis, meine, fremde)
        assertThat(plan.konflikte.single().tabelle).isEqualTo("gym")

        val an = anwenden(plan, Seite.FREMDES)
        // Der Konflikt geht an die Gegenseite …
        assertThat(an.fuerMich).contains(
            Operation.Aendern("gym", 1, gym("Halle Nord", "Potsdam")),
        )
        // … meine unstrittige Session bleibt trotzdem und wandert hinüber.
        assertThat(an.fuerDieGegenseite).contains(
            Operation.Einfuegen("session", 2, session(1, 100)),
        )
        assertThat(an.fuerMich).contains(
            Operation.Einfuegen(
                "ghost_analysis",
                1_000_000_001,
                ghost("ghost/a.json", "ghost/b.json"),
            ),
        )
    }

    // -- Ablauf 19: Metatabellen bleiben außen vor ------------------------

    @Test
    fun stand_meta_taucht_in_keinem_befund_auf() {
        val basis = stand("gym" to mapOf(1 to gym("Halle Nord")))
        val plan = abgleich(basis, basis, basis)

        assertThat(plan.befunde.map { it.tabelle }.toSet()).doesNotContain("stand_meta")
        assertThat(plan.befunde.map { it.tabelle }.toSet())
            .containsNoneIn(META_TABELLEN)
    }

    // -- Ablauf 31: gerätelokale Spalten -----------------------------------

    @Test
    fun zwei_abgleiche_mit_einer_ghost_analyse_melden_beim_zweiten_keinen_konflikt() {
        // Ablauf 31 — der Fehler, der jeden Abgleich betroffen hätte. Weil die Pfade seit
        // v7 relativ sind, trägt dieselbe Analyse auf beiden Geräten denselben Wert.
        val analyse = ghost("ghost/pose_aaa.json", "ghost/pose_bbb.json")

        // Erster Abgleich: die Analyse ist auf dem Tablet neu.
        val basis1 = stand("gym" to mapOf(1 to gym("Halle Nord")))
        val meine1 = basis1
        val fremde1 = stand(
            "gym" to mapOf(1 to gym("Halle Nord")),
            "ghost_analysis" to mapOf(1_000_000_001 to analyse),
        )
        val ersterAbgleich = anwenden(abgleich(basis1, meine1, fremde1), Seite.MEINS)
        assertThat(ersterAbgleich.fuerMich).hasSize(1)

        // Nach dem Anwenden haben beide denselben Stand, und der wird zur neuen Basis.
        val gemeinsam = fremde1
        val zweiterPlan = abgleich(gemeinsam, gemeinsam, gemeinsam)

        // Genau das ist der Punkt: kein Konflikt, keine Rückfrage, nichts zu tun.
        assertThat(zweiterPlan.konflikte).isEmpty()
        assertThat(anwenden(zweiterPlan, Seite.MEINS).bilanz.nichtsZuTun).isTrue()
    }

    @Test
    fun ein_geraetelokaler_wert_kaeme_als_dauerkonflikt_durch() {
        // Der Gegenbeweis zum vorigen Test: wären die Pfade weiter absolut, sähe dieselbe
        // Analyse auf beiden Geräten anders aus — und der Vergleich meldete das ewig.
        val basis = stand(
            "ghost_analysis" to mapOf(1 to ghost("ghost/a.json", "ghost/b.json")),
        )
        val meine = stand(
            "ghost_analysis" to mapOf(
                1 to ghost("/data/user/0/com.boulderbuddy/files/ghost/a.json", "ghost/b.json"),
            ),
        )
        val fremde = stand(
            "ghost_analysis" to mapOf(
                1 to ghost("/data/user/10/com.boulderbuddy/files/ghost/a.json", "ghost/b.json"),
            ),
        )

        assertThat(abgleich(basis, meine, fremde).konflikte).hasSize(1)
    }

    // -- SET NULL: die Route verliert ihren Grad --------------------------

    @Test
    fun ein_geloeschter_grad_leert_den_verweis_statt_die_route_zu_loeschen() {
        val basis = stand(
            "gym" to mapOf(1 to gym("Halle Nord")),
            "grade_system" to mapOf(1 to gradeSystem(1, "Farbsystem")),
            "grade" to mapOf(1 to grade(1, "6b")),
            "session" to mapOf(2 to session(1, 100)),
            "route" to mapOf(3 to route(2, "Dachrinne", gradeId = 1)),
        )
        val meine = stand(
            "gym" to mapOf(1 to gym("Halle Nord")),
            "grade_system" to mapOf(1 to gradeSystem(1, "Farbsystem")),
            "grade" to emptyMap(),
            "session" to mapOf(2 to session(1, 100)),
            "route" to mapOf(3 to route(2, "Dachrinne", gradeId = 1)),
        )
        val fremde = basis

        val an = anwenden(abgleich(basis, meine, fremde), Seite.MEINS)

        assertThat(an.bilanz.bezuegeGeloest).isEqualTo(1)
        // Die Route überlebt — sie verliert nur den Grad.
        val ohneGrad = route(2, "Dachrinne", gradeId = null)
        assertThat(an.fuerMich).contains(Operation.Aendern("route", 3, ohneGrad))
        assertThat(an.fuerDieGegenseite).contains(Operation.Aendern("route", 3, ohneGrad))
        // Und der Verweis ist gelöst, BEVOR der Grad verschwindet.
        val gegen = an.fuerDieGegenseite
        assertThat(gegen.indexOfFirst { it is Operation.Aendern && it.tabelle == "route" })
            .isLessThan(gegen.indexOfFirst { it is Operation.Loeschen && it.tabelle == "grade" })
    }

    // -- Konfliktfreier Alltag --------------------------------------------

    @Test
    fun ohne_aenderung_gibt_es_nichts_zu_tun() {
        val gleicherStand = stand(
            "gym" to mapOf(1 to gym("Halle Nord")),
            "session" to mapOf(2 to session(1, 100)),
        )

        val plan = abgleich(gleicherStand, gleicherStand, gleicherStand)
        assertThat(plan.veraenderung).isEqualTo(Veraenderung.KEINE)
        assertThat(anwenden(plan, Seite.MEINS).bilanz).isEqualTo(Bilanz.NICHTS)
    }

    @Test
    fun dieselbe_aenderung_auf_beiden_seiten_ist_kein_konflikt() {
        val basis = stand("gym" to mapOf(1 to gym("Halle Nord")))
        val beide = stand("gym" to mapOf(1 to gym("Halle Nord", "Berlin")))

        val plan = abgleich(basis, beide, beide)
        assertThat(urteilFuer(plan, "gym", 1)).isEqualTo(Urteil.BEIDE_GLEICH_GEAENDERT)
        assertThat(anwenden(plan, Seite.MEINS).bilanz.nichtsZuTun).isTrue()
    }

    @Test
    fun geloescht_gegen_geaendert_wird_gefragt() {
        val basis = stand(
            "gym" to mapOf(1 to gym("Halle Nord")),
            "session" to mapOf(2 to session(1, 100)),
        )
        val meine = stand("gym" to mapOf(1 to gym("Halle Nord")))
        val fremde = stand(
            "gym" to mapOf(1 to gym("Halle Nord")),
            "session" to mapOf(2 to session(1, 100, notiz = "guter Tag")),
        )

        val plan = abgleich(basis, meine, fremde)
        assertThat(plan.konflikte.single().art)
            .isEqualTo(KonfliktArt.GELOESCHT_GEGEN_GEAENDERT)

        // Entscheidet der Nutzer für die Gegenseite, kommt die Zeile hier zurück.
        val an = anwenden(plan, Seite.FREMDES)
        assertThat(an.fuerMich).containsExactly(
            Operation.Einfuegen("session", 2, session(1, 100, notiz = "guter Tag")),
        )
    }

    // -- Zwei Abgleiche hintereinander (die Regel aus Runde 4/5) -----------

    @Test
    fun nach_dem_anwenden_haben_beide_denselben_stand_und_der_zweite_abgleich_ist_leer() {
        // „Jeder Test läuft über ZWEI Abgleiche": Runde 4 und 5 fanden nur Fehler, die beim
        // ersten Durchlauf harmlos aussahen. Das hier ist die Grundinvariante — nach einem
        // Abgleich gibt es nichts mehr zu tun, egal wie verworren der erste war.
        val basis = stand(
            "gym" to mapOf(1 to gym("Halle Nord")),
            "session" to mapOf(2 to session(1, 100)),
            "route" to mapOf(3 to route(2, "Dachrinne")),
        )
        val meine = stand(
            "gym" to mapOf(1 to gym("Halle Nord", "Berlin")),
            "session" to mapOf(2 to session(1, 100)),
            "route" to mapOf(
                3 to route(2, "Dachrinne"),
                4 to route(2, "Riss"),
            ),
        )
        val fremde = stand(
            "gym" to mapOf(1 to gym("Halle Nord")),
            "session" to mapOf(2 to session(1, 100, notiz = "guter Tag")),
            "route" to mapOf(1_000_000_007 to route(2, "Kante")),
        )

        val erster = anwenden(abgleich(basis, meine, fremde), Seite.MEINS)
        val meineNachher = wendeAn(meine, erster.fuerMich)
        val fremdeNachher = wendeAn(fremde, erster.fuerDieGegenseite)

        // Beide Geräte stehen hinterher gleich da — sonst wäre der Abgleich keiner.
        for (tabelle in STAND_TABELLEN) {
            assertThat(meineNachher.zeilen(tabelle.name))
                .isEqualTo(fremdeNachher.zeilen(tabelle.name))
        }

        // Zweiter Abgleich gegen die frische Basis: nichts mehr zu tun, keine Rückfrage.
        val zweiter = abgleich(meineNachher, meineNachher, fremdeNachher)
        assertThat(zweiter.konflikte).isEmpty()
        assertThat(zweiter.veraenderung).isEqualTo(Veraenderung.KEINE)
        assertThat(anwenden(zweiter, Seite.MEINS).bilanz.nichtsZuTun).isTrue()
    }

    @Test
    fun ein_abbruch_vor_der_basis_kostet_beim_zweiten_versuch_nichts() {
        // Ablauf 15 zu Ende gedacht: angewendet wurde, basis.db fehlt noch. Der nächste
        // Abgleich läuft also gegen die ALTE Basis — und darf trotzdem nichts kaputt machen.
        val alteBasis = stand("gym" to mapOf(1 to gym("Halle Nord")))
        val meine = stand("gym" to mapOf(1 to gym("Halle Nord")))
        val fremde = stand(
            "gym" to mapOf(1 to gym("Halle Nord")),
            "session" to mapOf(1_000_000_002 to session(1, 100)),
        )

        val erster = anwenden(abgleich(alteBasis, meine, fremde), Seite.MEINS)
        val meineNachher = wendeAn(meine, erster.fuerMich)

        // basis.db wurde nicht geschrieben → zweiter Lauf gegen dieselbe alte Basis.
        val zweiter = abgleich(alteBasis, meineNachher, fremde)
        assertThat(urteilFuer(zweiter, "session", 1_000_000_002)).isEqualTo(Urteil.EINIG)
        assertThat(anwenden(zweiter, Seite.MEINS).bilanz.nichtsZuTun).isTrue()
    }

    // -- Reihenfolge -------------------------------------------------------

    @Test
    fun eltern_werden_vor_kindern_eingefuegt_und_nach_ihnen_geloescht() {
        val basis = Stand.LEER
        val meine = Stand.LEER
        val fremde = stand(
            "gym" to mapOf(1 to gym("Halle Nord")),
            "session" to mapOf(2 to session(1, 100)),
            "route" to mapOf(3 to route(2, "Dachrinne")),
        )

        val einfuegen = anwenden(abgleich(basis, meine, fremde), Seite.MEINS).fuerMich
        assertThat(einfuegen.map { it.tabelle })
            .containsExactly("gym", "session", "route").inOrder()

        val loeschen = anwenden(abgleich(fremde, fremde, Stand.LEER), Seite.MEINS).fuerMich
        assertThat(loeschen.map { it.tabelle })
            .containsExactly("route", "session", "gym").inOrder()
    }
}
