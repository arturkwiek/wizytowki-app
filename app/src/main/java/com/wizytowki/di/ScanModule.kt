package com.wizytowki.di

import com.wizytowki.ocr.LanguageIdentifier
import com.wizytowki.ocr.MlKitLanguageIdentifier
import com.wizytowki.ocr.MlKitTextRecognizer
import com.wizytowki.ocr.TextRecognizer
import com.wizytowki.scan.AndroidQualityChecker
import com.wizytowki.scan.DraftStore
import com.wizytowki.scan.FileDraftStore
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

    @Binds
    abstract fun bindDraftStore(impl: FileDraftStore): DraftStore

    @Binds
    abstract fun bindTextRecognizer(impl: MlKitTextRecognizer): TextRecognizer

    @Binds
    abstract fun bindLanguageIdentifier(impl: MlKitLanguageIdentifier): LanguageIdentifier
}
