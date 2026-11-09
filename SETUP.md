# Zautomatyzowana konfiguracja repozytorium Wizytówki

Ten dokument opisuje, jak w pełni zautomatyzować repozytorium zgodnie z wymaganiami projektu.

## Zrobione (Etap 0: Fundamenty)

✅ **Struktura Android Studio**
- Kotlin 1.9.24, Gradle 8.7, AGP 8.4.1, Compose compiler 1.5.14
- Jetpack Compose, Material Design 3
- minSdk 26, targetSdk 34

✅ **Architektura**
- MVVM z Hilt (dependency injection)
- Coroutines i Flow dla asynchroności
- Room z SQLCipher; losowy klucz bazy chroniony przez Android Keystore

✅ **Model danych**
- Encje: Person, Company, EmploymentHistory, BusinessCard
- DAO z Flow dla reactive updates
- Type converters dla LocalDateTime

✅ **UI i zasoby**
- 9 ekranów nawigacji (zaślepki Compose)
- Material 3: kolory, motyw jasny/ciemny
- Strings.xml: polski (values/) i angielski (values-en/)

✅ **Infrastruktura CI/CD**
- GitHub Actions workflow: `android-ci.yml`
- Linter, unit testy, budowanie APK
- Artefakty do pobrania

✅ **Szablony i skrypty**
- Issue templates: zadanie.md, błąd.md
- PR template z checklist'ą
- `scripts/create_issues.sh` do generacji issues z ROADMAP

## Następne kroki – automacja GitHub

### 1. Włoż dane do GitHub

```bash
git remote add origin https://github.com/TWOJ_LOGIN/wizytowki-app.git
git branch -M master
git push -u origin master
```

### 2. Utwórz etykiety i issues (wymaga `gh` CLI)

```bash
# Zaloguj się
gh auth login

# Uruchom skrypt (w repozytorium)
./scripts/create_issues.sh
```

Skrypt tworzy:
- Etykiety: `etap-0`, `etap-1`, ..., `etap-5`, `zadanie`
- Issues dla każdego zadania z bieżącego etapu + wszystkich następnych
- Każde issue zawiera odniesienia do REQUIREMENTS.md, AGENTS.md, ROADMAP.md

### 3. Konfiguracja Actions

GitHub Actions są już wdrożone. Po pushie do PR lub `master` uruchamia się:

```
./gradlew lint testDebugUnitTest assembleDebug
```

Wyniki:
- Raport lint'u
- Wyniki testów jednostkowych
- APK do pobrania (dla instalacji na telefonie)

### 4. Workflow pracy

Dla każdego issue (np. #1 z `etap-0`):

1. **Checkout**: Pobierz issue, przeczytaj REQUIREMENTS.md + AGENTS.md
2. **Gałąź**: `git checkout -b feat/1-krotki-opis`
3. **Praca**: Implementuj, commituj: `[WF-01] Opis`
4. **Testy**: Dodaj testy jednostkowe
5. **Buduj**: `./gradlew lint testDebugUnitTest assembleDebug`
6. **PR**: Otwórz pull request, dołącz `Closes #1`
7. **Merge**: Właściciel przegląda APK na telefonie → zatwierdza

## Struktura katalogów po zakończeniu wszystkich etapów

```
wizytowki-app/
├── .github/
│   ├── workflows/
│   │   └── android-ci.yml           (CI/CD)
│   ├── ISSUE_TEMPLATE/
│   │   ├── zadanie.md
│   │   └── blad.md
│   └── pull_request_template.md
├── app/src/
│   ├── main/
│   │   ├── java/com/wizytowki/
│   │   │   ├── ui/                  (Compose ekrany)
│   │   │   ├── scan/                (logika aparatu)
│   │   │   ├── ocr/                 (OCR i ekstrakcja)
│   │   │   ├── data/                (Room DAO, repozytoria)
│   │   │   ├── domain/              (case use, modele)
│   │   │   └── di/                  (Hilt moduły)
│   │   ├── res/
│   │   │   ├── values/              (Polish strings, kolory)
│   │   │   └── values-en/           (English strings)
│   │   └── AndroidManifest.xml
│   └── test/                        (testy jednostkowe)
├── docs/
│   └── ROADMAP.md                   (5 etapów rozwoju)
├── test-data/
│   ├── cards/                       (zdjęcia wizytówek)
│   └── expected/                    (oczekiwane wyniki JSON)
├── REQUIREMENTS.md                  (specyfikacja funkcjonalna)
├── AGENTS.md                        (zasady dla AI)
├── build.gradle.kts                 (root)
├── settings.gradle.kts
└── gradlew / gradlew.bat            (Gradle wrapper)
```

## Wskaźniki (monitoring)

### CI/CD status
- ✅ Jeśli wszystkie testy przechodzą na każdy PR
- ❌ Jeśli linter lub testy się nie powodzą → poprawka obowiązkowa

### Jakość kodu
- Testy dla każdej nowej logiki
- Logika OCR i duplikaty w testach z 30+ wizytówkami
- Czas analizy < 3 sekund

### Timing
- Etap 0: Fundamenty – w trakcie (PR z poprawkami buildu)
- Etap 1: Skanowanie (~2-3 tygodnie)
- Etap 2: OCR (~2-3 tygodnie)
- Etap 3: Weryfikacja (1-2 tygodnia)
- Etap 4: Baza kontaktów (2-3 tygodnie)
- Etap 5: Dopracowanie i wydanie (1-2 tygodnia)

## Troubleshooting

### Budowanie lokalnie na Windows
Java jest dołączona do Android Studio:
```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew lint testDebugUnitTest assembleDebug
```
`local.properties` (ignorowany przez git): `sdk.dir=C:/Users/<user>/AppData/Local/Android/Sdk`

### `gradlew: command not found`
```bash
chmod +x gradlew    # Linux/Mac
# Windows: gradlew.bat automatycznie
```

### Baza danych nie szyfruje się
- Klucz generuje `DatabaseKeyProvider` (losowe 32 B, opakowane kluczem AES z Android Keystore, zapisane w `shared_prefs/wizytowki_db_key.xml`).
- Sprawdzenie na emulatorze: `adb exec-out run-as com.wizytowki head -c 16 databases/wizytowki.db` – nie może zaczynać się od `SQLite format 3`.
- Backup Androida jest wyłączony – klucz z Keystore nie przenosi się na inne urządzenie.

### Testy nie znajdują Room tables
- Upewnij się, że `@Entity` jest dodany do klas
- DAO muszą implementować interfejsy w `WizytowkiDatabase` (✅)

## Zasoby

- [REQUIREMENTS.md](REQUIREMENTS.md) – specyfikacja
- [docs/ROADMAP.md](docs/ROADMAP.md) – plan etapów
- [AGENTS.md](AGENTS.md) – zasady pracy AI
- Android Developer: https://developer.android.com/
- Jetpack Compose: https://developer.android.com/jetpack/compose
- ML Kit: https://developers.google.com/ml-kit
