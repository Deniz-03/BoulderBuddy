package com.boulderbuddy.fake

import com.boulderbuddy.data.db.entity.GradeEntity
import com.boulderbuddy.data.haptics.HapticPattern
import com.boulderbuddy.data.haptics.HapticPlayer
import com.boulderbuddy.data.db.entity.GradeSystemEntity
import com.boulderbuddy.data.db.entity.GymEntity
import com.boulderbuddy.data.db.entity.HangboardSegmentEntity
import com.boulderbuddy.data.db.entity.HangboardTemplateEntity
import com.boulderbuddy.data.db.entity.HangboardWorkoutEntity
import com.boulderbuddy.data.db.entity.HangboardWorkoutWithSegments
import com.boulderbuddy.data.db.entity.RouteEntity
import com.boulderbuddy.data.db.entity.SessionEntity
import com.boulderbuddy.data.repository.GradeRepository
import com.boulderbuddy.data.repository.GymRepository
import com.boulderbuddy.data.repository.HangboardRepository
import com.boulderbuddy.data.repository.HangboardWorkoutRepository
import com.boulderbuddy.data.repository.RouteRepository
import com.boulderbuddy.data.repository.SessionRepository
import com.boulderbuddy.data.settings.SettingsRepository
import com.boulderbuddy.data.settings.TimerConfig
import com.boulderbuddy.wearsync.WearConnection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-Memory-Fakes der Repository-/Settings-Interfaces für JVM-ViewModel-Tests (Phase 7.6.4).
 * Bewusst schlank: nur so viel Verhalten, wie die Tests brauchen; Schreibpfade sammeln, was
 * das ViewModel erzeugt (z.B. [FakeHangboardWorkoutRepository.created]).
 *
 * Mehrere Fakes tragen ein `schreibfehler`-Flag. Es stellt den Fall nach, für den es den
 * [com.boulderbuddy.ui.Fehlerkanal] gibt: Room wirft beim Schreiben — volle Platte, verletzte
 * Bedingung, geschlossene Datenbank. Ohne dieses Flag lässt sich die einzige interessante
 * Frage nicht prüfen, nämlich was die App dann tut.
 */

/** Was ein Fake wirft, wenn `schreibfehler` gesetzt ist. */
class SchreibfehlerZumTesten : RuntimeException("Schreiben fehlgeschlagen (Test)")
class FakeSettingsRepository(
    initialTimerConfig: TimerConfig = TimerConfig(),
) : SettingsRepository {
    private val _selectedGradeSystemId = MutableStateFlow<Int?>(null)
    val timerConfigState = MutableStateFlow(initialTimerConfig)
    val darkModeState = MutableStateFlow<Boolean?>(null)
    val hapticFeedbackState = MutableStateFlow(true)
    val userNameState = MutableStateFlow("")
    val proximityAlertsState = MutableStateFlow(false)

    override val selectedGradeSystemId: Flow<Int?> = _selectedGradeSystemId
    override val timerConfig: Flow<TimerConfig> = timerConfigState
    override val darkMode: Flow<Boolean?> = darkModeState
    override val hapticFeedback: Flow<Boolean> = hapticFeedbackState
    override val userName: Flow<String> = userNameState
    override val proximityAlertsEnabled: Flow<Boolean> = proximityAlertsState

    override suspend fun setSelectedGradeSystem(systemId: Int) {
        _selectedGradeSystemId.value = systemId
    }

    override suspend fun setTimerConfig(config: TimerConfig) {
        timerConfigState.value = config
    }

    override suspend fun setDarkMode(enabled: Boolean) {
        darkModeState.value = enabled
    }

    override suspend fun setHapticFeedback(enabled: Boolean) {
        hapticFeedbackState.value = enabled
    }

    override suspend fun setUserName(name: String) {
        userNameState.value = name.trim()
    }

    override suspend fun setProximityAlertsEnabled(enabled: Boolean) {
        proximityAlertsState.value = enabled
    }
}

/** Sammelt die abgespielten Vibrationsmuster, statt das Gerät anzusprechen. */
class FakeHapticPlayer : HapticPlayer {
    val played = mutableListOf<HapticPattern>()

    override fun play(pattern: HapticPattern) {
        played += pattern
    }
}

/** Uhr-Verbindung mit umschaltbarem Zustand (Default: nicht verbunden). */
class FakeWearConnection(initiallyConnected: Boolean = false) : WearConnection {
    val connectedState = MutableStateFlow(initiallyConnected)
    override val connected: Flow<Boolean> = connectedState
}

class FakeSessionRepository : SessionRepository {
    val active = MutableStateFlow<SessionEntity?>(null)
    val all = MutableStateFlow<List<SessionEntity>>(emptyList())
    private var nextId = 1

    /** `true` = jeder Schreibweg wirft. Lesen bleibt heil. */
    var schreibfehler = false

    override fun observeActive(): Flow<SessionEntity?> = active
    override fun observeAll(): Flow<List<SessionEntity>> = all
    override suspend fun getById(sessionId: Int): SessionEntity? = all.value.find { it.id == sessionId }

    override suspend fun create(session: SessionEntity): Int {
        if (schreibfehler) throw SchreibfehlerZumTesten()
        val id = nextId++
        all.value = all.value + session.copy(id = id)
        return id
    }

    override suspend fun update(session: SessionEntity) {
        all.value = all.value.map { if (it.id == session.id) session else it }
    }

    override suspend fun updateNotes(sessionId: Int, notes: String?) {
        if (schreibfehler) throw SchreibfehlerZumTesten()
        all.value = all.value.map { if (it.id == sessionId) it.copy(notes = notes) else it }
    }

    override suspend fun endSession(sessionId: Int, endedAt: Long) {
        if (schreibfehler) throw SchreibfehlerZumTesten()
        all.value = all.value.map { if (it.id == sessionId) it.copy(endedAt = endedAt) else it }
        if (active.value?.id == sessionId) active.value = null
    }
}

class FakeRouteRepository : RouteRepository {
    val all = MutableStateFlow<List<RouteEntity>>(emptyList())

    /** `true` = [create] und [update] werfen. */
    var schreibfehler = false

    /** Was über [update] geschrieben wurde (Assertion-Ziel). */
    val updated = mutableListOf<RouteEntity>()

    override fun observeBySession(sessionId: Int): Flow<List<RouteEntity>> =
        all.map { list -> list.filter { it.sessionId == sessionId } }

    override fun observeAll(): Flow<List<RouteEntity>> = all
    override suspend fun getById(routeId: Int): RouteEntity? = all.value.find { it.id == routeId }

    override suspend fun create(route: RouteEntity): Int {
        if (schreibfehler) throw SchreibfehlerZumTesten()
        return 0
    }

    override suspend fun update(route: RouteEntity) {
        if (schreibfehler) throw SchreibfehlerZumTesten()
        updated += route
        all.value = all.value.map { if (it.id == route.id) route else it }
    }
}

class FakeGymRepository : GymRepository {
    val all = MutableStateFlow<List<GymEntity>>(emptyList())
    private var nextId = 1

    override fun observeAll(): Flow<List<GymEntity>> = all
    override suspend fun getById(gymId: Int): GymEntity? = all.value.find { it.id == gymId }

    override suspend fun create(gym: GymEntity): Int {
        val id = nextId++
        all.value = all.value + gym.copy(id = id)
        return id
    }

    override suspend fun update(gym: GymEntity) {
        all.value = all.value.map { if (it.id == gym.id) gym else it }
    }

    /**
     * Löscht nur die Halle. Die Fremdschlüssel-Folgen (Session behält ihren Namen, Gradsystem
     * wird global) macht in der echten App SQLite — hier gibt es keine, weil der Fake keine
     * Sessions kennt.
     */
    override suspend fun delete(gymId: Int) {
        all.value = all.value.filterNot { it.id == gymId }
    }

    /** Sessions kennt dieser Fake nicht; Tests, die zählen wollen, setzen [sessionCount]. */
    var sessionCount: Int = 0

    override suspend fun countSessions(gymId: Int): Int = sessionCount
}

class FakeGradeRepository : GradeRepository {
    val systems = MutableStateFlow<List<GradeSystemEntity>>(emptyList())
    val grades = MutableStateFlow<List<GradeEntity>>(emptyList())

    override fun observeSystemsByGym(gymId: Int): Flow<List<GradeSystemEntity>> =
        systems.map { list -> list.filter { it.gymId == gymId } }

    override fun observeAllSystems(): Flow<List<GradeSystemEntity>> = systems

    override fun observeGradesBySystem(systemId: Int): Flow<List<GradeEntity>> =
        grades.map { list -> list.filter { it.systemId == systemId } }

    override fun observeAllGrades(): Flow<List<GradeEntity>> = grades
    override suspend fun getGradeById(gradeId: Int): GradeEntity? = grades.value.find { it.id == gradeId }
    override suspend fun createSystem(system: GradeSystemEntity): Int = 0
    override suspend fun updateSystem(system: GradeSystemEntity) {}
    override suspend fun deleteSystem(systemId: Int) {}
    override suspend fun createGrade(grade: GradeEntity): Int = 0
    override suspend fun createGrades(grades: List<GradeEntity>) {}
    override suspend fun updateGrade(grade: GradeEntity) {}
}

class FakeHangboardWorkoutRepository : HangboardWorkoutRepository {
    val all = MutableStateFlow<List<HangboardWorkoutWithSegments>>(emptyList())
    /** Alles, was das ViewModel über [create] gespeichert hat (Assertion-Ziel). */
    val created = mutableListOf<HangboardWorkoutWithSegments>()

    override fun observeBySession(sessionId: Int): Flow<List<HangboardWorkoutWithSegments>> =
        all.map { list -> list.filter { it.workout.sessionId == sessionId } }

    override fun observeAll(): Flow<List<HangboardWorkoutWithSegments>> = all

    /** `true` = [create] wirft. */
    var schreibfehler = false

    /** Wie oft [create] gerufen wurde - auch die Versuche, die geworfen haben. */
    var versuche = 0
        private set

    override suspend fun create(
        workout: HangboardWorkoutEntity,
        segments: List<HangboardSegmentEntity>,
    ): Int {
        versuche++
        if (schreibfehler) throw SchreibfehlerZumTesten()
        val id = created.size + 1
        val withSegments = HangboardWorkoutWithSegments(
            workout = workout.copy(id = id),
            segments = segments.map { it.copy(workoutId = id) },
        )
        created += withSegments
        all.value = all.value + withSegments
        return id
    }
}

class FakeHangboardRepository : HangboardRepository {
    val all = MutableStateFlow<List<HangboardTemplateEntity>>(emptyList())
    val created = mutableListOf<HangboardTemplateEntity>()
    val deleted = mutableListOf<HangboardTemplateEntity>()

    /** `true` = [create] und [delete] werfen. */
    var schreibfehler = false

    override fun observeAll(): Flow<List<HangboardTemplateEntity>> = all

    override suspend fun create(template: HangboardTemplateEntity): Int {
        if (schreibfehler) throw SchreibfehlerZumTesten()
        created += template
        all.value = all.value + template
        return created.size
    }

    override suspend fun update(template: HangboardTemplateEntity) {}

    override suspend fun delete(template: HangboardTemplateEntity) {
        if (schreibfehler) throw SchreibfehlerZumTesten()
        deleted += template
        all.value = all.value - template
    }
}
