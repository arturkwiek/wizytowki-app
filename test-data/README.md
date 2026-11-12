# Dane testowe OCR

- `cards/` – zdjęcia wizytówek: `NNN_front.jpg`, `NNN_back.jpg` (brak pliku back = pusty tył).
- `expected/` – oczekiwany wynik: `NNN.json`.
- `private/cards/`, `private/expected/` – ten sam układ dla **prawdziwych** wizytówek. Katalog
  `private/` jest w `.gitignore`: zdjęcia i dane zostają na komputerze, test widzi je tak samo.
- `samples/` – zdjęcia właściciela do kalibracji skanera (Etap 1), w `.gitignore`, test ich nie czyta.

Do `cards/` trafiają tylko wizytówki fikcyjne. Nie commituj cudzych danych bez zgody.
Cel: min. 30 zestawów razem (PL, EN, mieszane, ciemne tło, ozdobne czcionki).

## Format `NNN.json`

```json
{
  "firstName": "Jan",
  "lastName": "Kowalski",
  "phones": ["+48 600 123 456"],
  "emails": ["jan.kowalski@przyklad.pl"],
  "website": "www.przyklad.pl",
  "social": ["linkedin.com/in/jan-kowalski"],
  "company": { "name": "Przykład Sp. z o.o.", "nip": "1234563218" },
  "position": { "pl": "Dyrektor sprzedaży", "en": "Sales Director" },
  "department": { "pl": "Dział handlowy", "en": null },
  "address": { "pl": "ul. Długa 5, 00-001 Warszawa", "en": null }
}
```

Pola, których na wizytówce nie ma, pomiń albo wpisz `null`. `website` może być też listą.
Wpisuj tekst tak, jak jest wydrukowany, z polskimi znakami.

## Jak liczona jest dokładność

Każda oczekiwana wartość to jeden punkt (każdy telefon i e-mail osobno). Pole jednowartościowe
(imię, nazwisko, firma, NIP, stanowisko, dział) rozpoznane, choć na wizytówce go nie ma, to błąd.
Porównanie pomija wielkość liter, spacje i interpunkcję, ale nie polskie znaki: „Łódź” odczytane
jako „Lodz” to błąd. Telefony porównywane po numerze (`+48 600 123 456` = `600-123-456`).
Szczegóły: `app/src/sharedTest/java/com/wizytowki/accuracy/AccuracyScorer.kt`.

Kryterium Etapu 2: ≥ 90 % pól poprawnie i analiza < 3 s na wizytówkę.

## Uruchomienie (emulator albo telefon przez USB)

```
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.wizytowki.accuracy.OcrAccuracyTest
```

Wynik: komunikat testu i logcat (`adb logcat -s OcrAccuracy`), z listą błędnych pól.
Przy mniej niż 30 wizytówkach test jest pomijany. Próbny przebieg na mniejszym zestawie:
dodaj `-Pandroid.testInstrumentationRunnerArguments.minCards=1`.

## Fikcyjne wizytówki

`scripts/render_test_card.py` rysuje syntetyczną wizytówkę (Python + Pillow), np.:

```
python scripts/render_test_card.py test-data/cards/002_front.jpg "#Anna Nowak" "Kierownik projektu" "ul. Polna 1" "00-625 Warszawa"
```

Linia zaczynająca się od `#` jest duża (imię i nazwisko). Takie obrazy są czyste i proste,
więc sprawdzają tylko, czy cały tor działa. O dokładności decydują prawdziwe zdjęcia w `private/`.
