package com.wizytowki.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.TypeConverter
import com.wizytowki.data.db.entities.CompanyEntity
import com.wizytowki.data.db.entities.EmploymentHistoryEntity
import com.wizytowki.data.db.entities.PersonEntity
import com.wizytowki.data.db.entities.BusinessCardEntity
import com.wizytowki.data.db.dao.PersonDao
import com.wizytowki.data.db.dao.CompanyDao
import com.wizytowki.data.db.dao.EmploymentHistoryDao
import com.wizytowki.data.db.dao.BusinessCardDao
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Database(
    entities = [
        PersonEntity::class,
        CompanyEntity::class,
        EmploymentHistoryEntity::class,
        BusinessCardEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class WizytowkiDatabase : RoomDatabase() {
    abstract fun personDao(): PersonDao
    abstract fun companyDao(): CompanyDao
    abstract fun employmentHistoryDao(): EmploymentHistoryDao
    abstract fun businessCardDao(): BusinessCardDao

    companion object {
        @Volatile
        private var instance: WizytowkiDatabase? = null

        fun getInstance(context: Context, passphrase: String = "default_passphrase"): WizytowkiDatabase {
            return instance ?: synchronized(this) {
                val factory = SupportFactory(SQLiteDatabase.getBytes(passphrase.toCharArray()))
                val db = Room.databaseBuilder(
                    context.applicationContext,
                    WizytowkiDatabase::class.java,
                    "wizytowki.db"
                )
                    .openHelperFactory(factory)
                    .build()
                instance = db
                db
            }
        }
    }
}

class Converters {
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    @TypeConverter
    fun fromLocalDateTime(value: String?): LocalDateTime? {
        return value?.let { LocalDateTime.parse(it, formatter) }
    }

    @TypeConverter
    fun toLocalDateTime(value: LocalDateTime?): String? {
        return value?.format(formatter)
    }
}
