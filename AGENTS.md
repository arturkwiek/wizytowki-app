# Instrukcje dla agenta AI

Pracujesz nad prywatną aplikacją Android do skanowania wizytówek. Jedynym źródłem prawdy o zakresie jest `REQUIREMENTS.md`, a o kolejności – `docs/ROADMAP.md`.

## Zasady nadrzędne
- Realizuj wyłącznie zadania z bieżącego etapu. Nie zaczynaj kolejnego etapu bez zgody właściciela.
- Sekcja 7 wymagań (funkcje wtórne) jest poza zakresem – nie implementuj jej.
- Przy niejasności zadaj pytanie w issue lub PR zamiast zgadywać.
- Nie dodawaj zależności spoza stosu z sekcji 9 bez uzasadnienia w PR.
- Żadnych danych wysyłanych do sieci. OCR i cała logika działają offline.
  - Wyjątek zaakceptowany przez właściciela (2026-11-09, PR #82): Google ML Kit przez Google Play services – Play services może pobrać moduł przy pierwszym użyciu i wysyłać anonimowe metryki użycia ML Kit. Zdjęcia, tekst OCR i dane kontaktów nigdy nie opuszczają telefonu; aplikacja sama nie wykonuje żadnych połączeń sieciowych.
- Nigdy nie commituj sekretów, kluczy ani prawdziwych danych osobowych.

## Przepływ pracy
1. Weź jedno issue z etykietą bieżącego etapu (np. `etap-1`).
2. Utwórz gałąź `feat/<numer-issue>-<krotki-opis>`.
3. Małe, logiczne commity w formacie: `[WF-02] Prośba o zdjęcie tyłu po zdjęciu przodu`.
4. Napisz testy do nowej logiki (jednostkowe; dla UI – testy Compose, gdy to możliwe).
5. Uruchom lokalnie: `./gradlew lint testDebugUnitTest assembleDebug`.
6. Otwórz PR według szablonu, z `Closes #<numer>` i listą zrealizowanych wymagań.
7. Jeden PR = jedno issue. PR do ok. 400 linii zmian; większe zadania dziel.

## Stos technologiczny
Kotlin, Jetpack Compose, Material 3, MVVM, Hilt, Coroutines/Flow, Room + SQLCipher, ML Kit Document Scanner (decyzja w PR #82; bez CameraX), ML Kit Text Recognition v2, Language Identification, Entity Extraction, Android SpeechRecognizer. minSdk 26.

## Struktura kodu (docelowa)
```
app/src/main/java/.../
  ui/          ekrany Compose i ViewModele (jeden pakiet na ekran)
  scan/        aparat, przepływ przód/tył, szkice
  ocr/         rozpoznawanie tekstu, wykrywanie języka, ekstrakcja pól
  data/        Room: encje, DAO, repozytoria
  domain/      modele i przypadki użycia (scalanie, duplikaty)
  di/          moduły Hilt
```

## Standardy
- Logika ekstrakcji i wykrywania duplikatów w czystym Kotlinie (pakiet `domain`/`ocr`), w pełni testowana jednostkowo.
- Teksty UI wyłącznie w `strings.xml` (PL i EN).
- Obsługa polskich znaków wszędzie (testy z ą, ę, ł, ż itd.).
- Test OCR: zdjęcia z `test-data/cards/` i oczekiwane wyniki z `test-data/expected/` (JSON). Nie obniżaj progu dokładności.

## Definicja ukończenia zadania
- Kryteria z issue spełnione, CI zielone, testy dodane, brak nowych ostrzeżeń lint.
- Opis PR mówi, jak ręcznie sprawdzić zmianę na telefonie.
