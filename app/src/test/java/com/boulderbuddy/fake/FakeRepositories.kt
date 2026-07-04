package com.boulderbuddy.fake

import com.boulderbuddy.data.db.entity.GradeEntity
import com.boulderbuddy.data.db.entity.GradeSystemEntity
import com.boulderbuddy.data.db.entity.HangboardSessionEntity
import com.boulderbuddy.data.db.entity.HangboardTemplateEntity
import com.boulderbuddy.data.db.entity.RouteEntity
import com.boulderbuddy.data.db.entity.SessionEntity
import com.boulderbuddy.data.repository.GradeRepository
import com.boulderbuddy.data.repository.HangboardRepository
import com.boulderbuddy.data.repository.HangboardSessionRepository
import com.boulderbuddy.data.repository.RouteRepository
import com.boulderbuddy.data.repository.SessionRepository
import com.boulderbuddy.data.settings.SettingsRepository
import com.boulderbuddy.data.settings.TimerConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-Memory-Fakes der Repository-/Settings-Interfaces für JVM-ViewModel-Tests (Phase 7.6.4).
 * Bewusst schlank: nur so viel Verhalten, wie die Tests brauchen; Schreibpfade sammeln, was
 * das ViewModel erzeugt (z.B. [FakeHangboardSessionRepository.created]).
 */
class FakeSettingsRepository(
    initialTimerConfig: TimerConfig = TimerConfig(),
) : SettingsRepository {
    private val _selectedGradeSystemId = MutableStateFlow<Int?>(null)
    val timerConfigState = MutableStateFlow(initialTimerConfig)
    val darkModeState = MutableStateFlow<Boolean?>(null)

    override val selectedGradeSystemId: Flow<Int?> = _selectedGradeSystemId
    override val timerConfig: Flow<TimerConfig> = timerConfigState
    override val darkMode: Flow<Boolean?> = darkModeState

    override suspend fun setSelectedGradeSystem(systemId: Int) {
        _selectedGradeSystemId.value = systemId
    }

    override suspend fun setTimerConfig(config: TimerConfig) {
        timerConfigState.value = config
    }

    override suspend fun setDarkMode(enabled: Boolean) {
        darkModeState.value = enabled
    }
}

class FakeSessionRepository : SessionRepository {
    val active = MutableStateFlow<SessionEntity?>(null)
    val all = MutableStateFlow<List<SessionEntity>>(emptyList())
    private var nextId = 1

    override fun observeActive(): Flow<SessionEntity?> = active
    override fun observeAll(): Flow<List<SessionEntity>> = all
    override suspend fun getById(sessionId: Int): SessionEntity? = all.value.find { it.id == sessionId }

    override suspend fun create(session: SessionEntity): Int {
        val id = nextId++
        all.value = all.value + session.copy(id = id)
        return id
    }

    override suspend fun update(session: SessionEntity) {
        all.value = all.value.map { if (it.id == session.id) session else it }
    }

    override suspend fun endSession(sessionId: Int, endedAt: Long) {
        all.value = all.value.map { if (it.id == sessionId) it.copy(endedAt = endedAt) else it }
        if (active.value?.id == sessionId) active.value = null
    }
}

class FakeRouteRepository : RouteRepository {
    val all = MutableStateFlow<List<RouteEntity>>(emptyList())

    override fun observeBySession(sessionId: Int): Flow<List<RouteEntity>> =
        all.map { list -> list.filter { it.sessionId == sessionId } }

    override fun observeAll(): Flow<List<RouteEntity>> = all
    override suspend fun getById(routeId: Int): RouteEntity? = all.value.find { it.id == routeId }
    override suspend fun create(route: RouteEntity): Int = 0
    override suspend fun update(route: RouteEntity) {}
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

class FakeHangboardSessionRepository : HangboardSessionRepository {
    val all = MutableStateFlow<List<HangboardSessionEntity>>(emptyList())
    /** Alles, was das ViewModel über [create] getrackt hat (Assertion-Ziel). */
    val created = mutableListOf<HangboardSessionEntity>()

    override fun observeBySession(sessionId: Int): Flow<List<HangboardSessionEntity>> =
        all.map { list -> list.filter { it.sessionId == sessionId } }

    override fun observeAll(): Flow<List<HangboardSessionEntity>> = all

    override suspend fun create(session: HangboardSessionEntity): Int {
        created += session
        all.value = all.value + session
        return created.size
    }
}

class FakeHangboardRepository : HangboardRepository {
    val all = MutableStateFlow<List<HangboardTemplateEntity>>(emptyList())
    val created = mutableListOf<HangboardTemplateEntity>()
    val deleted = mutableListOf<HangboardTemplateEntity>()

    override fun observeAll(): Flow<List<HangboardTemplateEntity>> = all

    override suspend fun create(template: HangboardTemplateEntity): Int {
        created += template
        all.value = all.value + template
        return created.size
    }

    override suspend fun update(template: HangboardTemplateEntity) {}

    override suspend fun delete(template: HangboardTemplateEntity) {
        deleted += template
        all.value = all.value - template
    }
}
