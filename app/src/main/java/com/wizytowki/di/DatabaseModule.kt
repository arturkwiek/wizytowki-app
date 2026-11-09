package com.wizytowki.di

import android.content.Context
import com.wizytowki.data.db.DatabaseKeyProvider
import com.wizytowki.data.db.WizytowkiDatabase
import com.wizytowki.data.db.dao.BusinessCardDao
import com.wizytowki.data.db.dao.CompanyDao
import com.wizytowki.data.db.dao.EmploymentHistoryDao
import com.wizytowki.data.db.dao.PersonDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Singleton
    @Provides
    fun provideDatabase(
        @ApplicationContext context: Context
    ): WizytowkiDatabase {
        return WizytowkiDatabase.getInstance(context, DatabaseKeyProvider(context).getPassphrase())
    }

    @Provides
    fun providePersonDao(db: WizytowkiDatabase): PersonDao = db.personDao()

    @Provides
    fun provideCompanyDao(db: WizytowkiDatabase): CompanyDao = db.companyDao()

    @Provides
    fun provideEmploymentHistoryDao(db: WizytowkiDatabase): EmploymentHistoryDao = db.employmentHistoryDao()

    @Provides
    fun provideBusinessCardDao(db: WizytowkiDatabase): BusinessCardDao = db.businessCardDao()
}
