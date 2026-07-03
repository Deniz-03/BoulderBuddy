package com.boulderbuddy.data.repository

import com.boulderbuddy.data.db.dao.GradeDao
import com.boulderbuddy.data.db.dao.GradeSystemDao
import com.boulderbuddy.data.db.entity.GradeEntity
import com.boulderbuddy.data.db.entity.GradeSystemEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Kapselt Gradsysteme ([GradeSystemEntity]) und deren Grade ([GradeEntity]).
 *
 * Bündelt beide DAOs, weil Custom-Gradsystem & Session-Erstellung sie stets zusammen brauchen
 * (ein System mit seinen farbigen Graden).
 */
interface GradeRepository {
    /** Gradsysteme einer Halle. */
    fun observeSystemsByGym(gymId: Int): Flow<List<GradeSystemEntity>>

    /** Alle Gradsysteme (hallenübergreifend). */
    fun observeAllSystems(): Flow<List<GradeSystemEntity>>

    /** Die Grade eines Systems, nach `sortOrder`. */
    fun observeGradesBySystem(systemId: Int): Flow<List<GradeEntity>>

    /** Alle Grade (hallenübergreifend) — zum Auflösen von `Route.gradeId`. */
    fun observeAllGrades(): Flow<List<GradeEntity>>

    /** Einzelnen Grad laden; `null`, wenn keiner mit [gradeId] existiert. */
    suspend fun getGradeById(gradeId: Int): GradeEntity?

    /** Legt ein Gradsystem an und gibt seine neue ID zurück. */
    suspend fun createSystem(system: GradeSystemEntity): Int

    /** Aktualisiert ein bestehendes Gradsystem. */
    suspend fun updateSystem(system: GradeSystemEntity)

    /** Legt einen einzelnen Grad an und gibt seine neue ID zurück. */
    suspend fun createGrade(grade: GradeEntity): Int

    /** Legt mehrere Grade auf einmal an (z.B. beim Erzeugen eines Custom-Gradsystems). */
    suspend fun createGrades(grades: List<GradeEntity>)

    /** Aktualisiert einen bestehenden Grad. */
    suspend fun updateGrade(grade: GradeEntity)
}

class GradeRepositoryImpl @Inject constructor(
    private val gradeSystemDao: GradeSystemDao,
    private val gradeDao: GradeDao,
) : GradeRepository {

    override fun observeSystemsByGym(gymId: Int): Flow<List<GradeSystemEntity>> =
        gradeSystemDao.observeByGym(gymId)

    override fun observeAllSystems(): Flow<List<GradeSystemEntity>> = gradeSystemDao.observeAll()

    override fun observeGradesBySystem(systemId: Int): Flow<List<GradeEntity>> =
        gradeDao.observeBySystem(systemId)

    override fun observeAllGrades(): Flow<List<GradeEntity>> = gradeDao.observeAll()

    override suspend fun getGradeById(gradeId: Int): GradeEntity? = gradeDao.getById(gradeId)

    override suspend fun createSystem(system: GradeSystemEntity): Int =
        gradeSystemDao.insert(system).toInt()

    override suspend fun updateSystem(system: GradeSystemEntity) = gradeSystemDao.update(system)

    override suspend fun createGrade(grade: GradeEntity): Int = gradeDao.insert(grade).toInt()

    override suspend fun createGrades(grades: List<GradeEntity>) = gradeDao.insertAll(grades)

    override suspend fun updateGrade(grade: GradeEntity) = gradeDao.update(grade)
}
