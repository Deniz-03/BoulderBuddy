package com.boulderbuddy.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.boulderbuddy.data.db.entity.GymEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GymDao {
    @Insert
    suspend fun insert(gym: GymEntity): Long

    @Update
    suspend fun update(gym: GymEntity)

    /**
     * Löscht die Halle. Was daran hängt, regeln die Fremdschlüssel: Sessions und
     * hallenspezifische Gradsysteme werden auf `NULL` gesetzt (bleiben also), das Besuchs-Log
     * verschwindet mit (CASCADE) — es beschreibt nur diese eine Halle.
     *
     * **Nicht direkt aufrufen**, sondern über [deleteAndKeepName]: allein gelassen nimmt dieser
     * Aufruf den Hallennamen mit, wo er noch nicht in der Session steht.
     */
    @Query("DELETE FROM gym WHERE id = :gymId")
    suspend fun deleteById(gymId: Int)

    /**
     * Schreibt den aktuellen Hallennamen in alle Sessions dieser Halle.
     *
     * Muss **vor** dem Löschen laufen, solange die Zeile in `gym` noch existiert. Bewusst ohne
     * `AND gymName = ''`: solange `gymId` zeigt, wohin es soll, ist die Halle die Wahrheit
     * (Umbenennungen schlagen auf alte Sessions durch, siehe `SessionEntity.hallenName`) — der
     * Name, der die Löschung überdauern soll, ist deshalb der *jetzige*, nicht der von damals.
     */
    @Query(
        "UPDATE session SET gymName = (SELECT name FROM gym WHERE id = :gymId) " +
            "WHERE gymId = :gymId"
    )
    suspend fun schreibeHallennamenInSessions(gymId: Int)

    /**
     * Löscht die Halle, ohne ihren Namen aus der Historie zu nehmen — beides in einer
     * Transaktion, sonst stünde nach einem Abbruch dazwischen eine Halle ohne Sessions
     * oder eine Session ohne Namen.
     *
     * Das Sichern hier ist der **Fangnetz-Teil**: neu angelegte Sessions bringen ihren
     * `gymName` seit `SessionErstellenViewModel.createSession` selbst mit. Zeilen, die vor
     * dieser Korrektur entstanden sind, haben ihn nicht — und für die ist der Moment des
     * Löschens die letzte Gelegenheit, ihn noch zu erfahren. Deshalb braucht es dafür keine
     * eigene Migration.
     */
    @Transaction
    suspend fun deleteAndKeepName(gymId: Int) {
        schreibeHallennamenInSessions(gymId)
        deleteById(gymId)
    }

    /** Wie viele Sessions hängen an dieser Halle — für die Rückfrage vor dem Löschen. */
    @Query("SELECT COUNT(*) FROM session WHERE gymId = :gymId")
    suspend fun countSessions(gymId: Int): Int

    @Query("SELECT * FROM gym WHERE id = :gymId")
    suspend fun getById(gymId: Int): GymEntity?

    @Query("SELECT * FROM gym ORDER BY name")
    fun observeAll(): Flow<List<GymEntity>>
}
