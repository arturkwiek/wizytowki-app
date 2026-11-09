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

    @Query("DELETE FROM business_cards WHERE id = :id")
    suspend fun delete(id: Long)
}
