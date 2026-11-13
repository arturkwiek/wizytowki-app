package com.wizytowki.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.wizytowki.data.db.entities.EmploymentHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EmploymentHistoryDao {
    @Insert
    suspend fun insert(history: EmploymentHistoryEntity): Long

    @Update
    suspend fun update(history: EmploymentHistoryEntity)

    @Query("SELECT * FROM employment_history WHERE personId = :personId ORDER BY dateFrom DESC")
    fun getHistoryByPerson(personId: Long): Flow<List<EmploymentHistoryEntity>>

    @Query("DELETE FROM employment_history WHERE id = :id")
    suspend fun delete(id: Long)
}
