package com.wizytowki.di

import com.wizytowki.scan.AndroidQualityChecker
import com.wizytowki.scan.QualityChecker
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ScanModule {
    @Binds
    abstract fun bindQualityChecker(impl: AndroidQualityChecker): QualityChecker
}
