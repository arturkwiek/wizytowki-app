package com.wizytowki.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.wizytowki.data.db.entities.CompanyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CompanyDao {
    @Insert
    suspend fun insert(company: CompanyEntity): Long

    @Update
    suspend fun update(company: CompanyEntity)

    @Query("SELECT * FROM companies WHERE id = :id")
    fun getCompanyById(id: Long): Flow<CompanyEntity?>

    @Query("SELECT * FROM companies ORDER BY name")
    fun getAllCompanies(): Flow<List<CompanyEntity>>

    @Query("DELETE FROM companies WHERE id = :id")
    suspend fun delete(id: Long)
}
