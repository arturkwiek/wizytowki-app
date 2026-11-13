plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("kotlin-parcelize")
}

android {
    namespace = "com.wizytowki"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.wizytowki"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    sourceSets {
        // Accuracy scoring (#19) is shared by JVM tests and the on-device OCR accuracy test.
        getByName("test").java.srcDir("src/sharedTest/java")
        getByName("androidTest").java.srcDir("src/sharedTest/java")
    }
}

/**
 * Copies the OCR accuracy sets (#19) into the test APK assets: test-data/{cards,expected} and the
 * git-ignored test-data/private/{cards,expected} with real cards. test-data/samples/ is left out.
 */
abstract class CopyTestDataAssets : DefaultTask() {
    @get:Internal
    abstract val root: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val sources: FileTree
        get() = root.asFileTree.matching { include(SETS.map { "$it/**" }) }

    @get:OutputDirectory
    abstract val output: DirectoryProperty

    @TaskAction
    fun copy() {
        val out = output.get().asFile
        out.deleteRecursively()
        val base = root.get().asFile
        SETS.map { File(base, it) }.filter { it.isDirectory }
            .forEach { it.copyRecursively(File(out, "test-data/" + it.relativeTo(base).invariantSeparatorsPath), overwrite = true) }
    }

    private companion object {
        val SETS = listOf("cards", "expected", "private/cards", "private/expected")
    }
}

val copyTestDataAssets = tasks.register<CopyTestDataAssets>("copyTestDataAssets") {
    root.set(rootProject.layout.projectDirectory.dir("test-data"))
}

// Test APK only – the app itself never contains test cards.
androidComponents {
    onVariants { variant ->
        variant.androidTest?.sources?.assets?.addGeneratedSourceDirectory(copyTestDataAssets, CopyTestDataAssets::output)
    }
}

dependencies {
    // AndroidX Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    // Jetpack Compose
    implementation("androidx.activity:activity-compose:1.8.1")
    implementation(platform("androidx.compose:compose-bom:2024.02.02"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.2.1")
    implementation("androidx.compose.material:material-icons-extended:1.6.4")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    // Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // SQLCipher for encryption
    implementation("net.zetetic:android-database-sqlcipher:4.5.4")

    // Coroutines & Flow
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Skaner: ML Kit Document Scanner przez Google Play services (decyzja w PR #82, wyjątek w AGENTS.md).
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0")

    // OCR: ML Kit Text Recognition v2 (alfabet łaciński) z modelem wbudowanym w APK – działa offline
    // od pierwszego uruchomienia, bez pobierania modułu (WF-10, WNF-01).
    implementation("com.google.mlkit:text-recognition:16.0.1")
    // Język fragmentów: ML Kit Language Identification, model wbudowany w APK (WF-11, offline).
    implementation("com.google.mlkit:language-id:17.0.6")
    // SpeechRecognizer jest częścią frameworka Androida (android.speech) – nie wymaga zależności.

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("io.mockk:mockk:1.13.10")

    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.02.02"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
