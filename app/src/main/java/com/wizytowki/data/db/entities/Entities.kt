package com.wizytowki.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "persons")
data class PersonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val firstName: String,
    val lastName: String,
    val phones: String = "",
    val emails: String = "",
    val websites: String = "",
    val socialProfiles: String = "",
    val comment: String = "",
    val currentCompanyId: Long? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
)

@Entity(tableName = "companies")
data class CompanyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val nip: String = "",
    val website: String = "",
    val emailDomain: String = "",
    val addressPl: String = "",
    val addressEn: String = "",
    val descriptionPl: String = "",
    val descriptionEn: String = "",
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
)

@Entity(tableName = "employment_history")
data class EmploymentHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long,
    val companyId: Long,
    val positionPl: String = "",
    val positionEn: String = "",
    val departmentPl: String = "",
    val departmentEn: String = "",
    val dateFrom: LocalDateTime? = null,
    val dateTo: LocalDateTime? = null,
    val isCurrent: Boolean = true
)

@Entity(tableName = "business_cards")
data class BusinessCardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long,
    val frontPhotoPath: String,
    val backPhotoPath: String = "",
    val rawOcrText: String = "",
    val scannedAt: LocalDateTime = LocalDateTime.now(),
    val location: String = "",
    val event: String = ""
)
