# ADR 0001: Skaner wizytówek – ML Kit Document Scanner

- **Status:** przyjęta (właściciel, 2026-11-09, PR #82)
- **Zadania:** #7 (prototyp), #8 (przepływ), #9 (krawędzie, kadrowanie, prostowanie)

## Kontekst
Etap 1 wymaga zdjęcia przodu i tyłu wizytówki z automatycznym wykrywaniem krawędzi, kadrowaniem
i prostowaniem perspektywy (WF-03), kontrolą jakości (WF-04), automatycznym wyzwalaniem (UI-04)
i importem z galerii (WF-06). Rozważane: CameraX z własną wizją komputerową albo
Google ML Kit Document Scanner (`play-services-mlkit-document-scanner`).

## Decyzja
Używamy **ML Kit Document Scanner** w trybie `SCANNER_MODE_FULL`, 1 strona na wywołanie,
wynik JPEG, import z galerii włączony (`ui/scan/DocumentScanner.kt`). CameraX usunięty w #8.

## Jak wymagania są pokryte

| Wymaganie | Kto realizuje | Gdzie |
|---|---|---|
| WF-03 wykrywanie krawędzi | ML Kit – automatycznie podczas zdjęcia | skaner Google |
| WF-03 kadrowanie | ML Kit – automatycznie + ręcznie „Kadruj i obróć” | skaner Google |
| WF-03 prostowanie perspektywy | ML Kit – wynik jest już wyprostowany | skaner Google |
| UI-04 automatyczne zdjęcie | ML Kit (auto-capture), z możliwością ręcznego | skaner Google |
| WF-05 ponowienie strony | ML Kit („Ponów” w podglądzie) + nasze „Ponów przód/tył” | skaner + `ScanScreen` |
| WF-04 kontrola jakości | ML Kit tylko częściowo – własna analiza ostrości i jasności | #10 |
| WF-06 import z galerii | ML Kit (`setGalleryImportAllowed(true)`) | skaner Google, #12 |
| WNF-04 uprawnienia | brak uprawnienia CAMERA – zdjęcie robi proces Play services | `AndroidManifest.xml` |

## Konsekwencje
- **Wymagane Google Play services.** Bez nich `ScanScreen` pokazuje komunikat `scan_unavailable`.
- **Sieć:** Play services pobiera moduł skanera przy pierwszym użyciu i może wysyłać anonimowe
  metryki ML Kit. Zdjęcia i dane nie opuszczają telefonu. Wyjątek zapisany w `AGENTS.md`.
- **Brak kontroli nad UI skanera:** język i wygląd zależą od systemu i wersji modułu
  (zaobserwowana zmiana UI po aktualizacji modułu w trakcie testu). Podpowiedzi UI-03
  pokazujemy na naszych ekranach przed otwarciem skanera.
- **Rozdzielczość** wyniku zależy od aparatu i wielkości wizytówki w kadrze – do sprawdzenia
  na telefonie przed OCR (Etap 2).

## Do sprawdzenia na telefonie (kryterium Etapu 1)
- Wizytówka pod kątem ~30° – wynik prostokątny, bez tła.
- Wizytówka na podobnym kolorystycznie tle (np. biała na białym) – czy krawędzie są wykryte.
- Błyszcząca / ciemna wizytówka.
