package com.wizytowki.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.wizytowki.data.db.entities.PersonEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao {
    @Insert
    suspend fun insert(person: PersonEntity): Long

    @Update
    suspend fun update(person: PersonEntity)

    @Query("SELECT * FROM persons WHERE id = :id")
    fun getPersonById(id: Long): Flow<PersonEntity?>

    @Query("SELECT * FROM persons ORDER BY lastName, firstName")
    fun getAllPersons(): Flow<List<PersonEntity>>

    @Query("SELECT COUNT(*) FROM persons")
    fun countPersons(): Flow<Int>

    @Query("SELECT * FROM persons WHERE firstName LIKE :query OR lastName LIKE :query")
    fun searchPersons(query: String): Flow<List<PersonEntity>>

    @Query("DELETE FROM persons WHERE id = :id")
    suspend fun delete(id: Long)
}
