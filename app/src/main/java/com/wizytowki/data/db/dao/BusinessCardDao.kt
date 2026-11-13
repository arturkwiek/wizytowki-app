package com.wizytowki.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.wizytowki.data.db.entities.BusinessCardEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BusinessCardDao {
    @Insert
    suspend fun insert(card: BusinessCardEntity): Long

    @Query("SELECT * FROM business_cards WHERE id = :id")
    fun getCardById(id: Long): Flow<BusinessCardEntity?>

    @Query("SELECT * FROM business_cards WHERE personId = :personId ORDER BY scannedAt DESC")
    fun getCardsByPerson(personId: Long): Flow<List<BusinessCardEntity>>

    @Query("SELECT * FROM business_cards ORDER BY scannedAt DESC")
    fun getAllCards(): Flow<List<BusinessCardEntity>>

    /** All cards of [from] now belong to [to] (manual merge, WF-35). */
    @Query("UPDATE business_cards SET personId = :to WHERE personId = :from")
    suspend fun moveCards(from: Long, to: Long)

    /** One card moved to another person (manual split, WF-35). */
    @Query("UPDATE business_cards SET personId = :to WHERE id = :cardId")
    suspend fun moveCard(cardId: Long, to: Long)

    @Query("DELETE FROM business_cards WHERE id = :id")
    suspend fun delete(id: Long)
}
