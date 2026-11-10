# ADR 0002: Ekstrakcja encji – własne reguły offline zamiast ML Kit Entity Extraction

- **Status:** przyjęta przez agenta w PR dla #15 (2026-11-10), do potwierdzenia przez właściciela
- **Zadania:** #15 (telefony, e-maile, WWW, adresy), #16 (NIP, kody pocztowe, +48, imię i nazwisko, stanowisko)

## Kontekst
REQUIREMENTS (sekcja 9) przewiduje ML Kit Entity Extraction do telefonów, e-maili, adresów i URL.
Ta biblioteka (`com.google.mlkit:entity-extraction`, wciąż w wersji beta) **nie ma modelu
wbudowanego w APK**. Model trzeba pobrać przez `downloadModelIfNeeded()` z procesu aplikacji.
Wymagałoby to:
- uprawnienia `INTERNET` – aplikacja nie ma dziś żadnych uprawnień (WNF-04);
- połączenia sieciowego wykonywanego przez samą aplikację, a wyjątek z AGENTS.md (PR #82)
  obejmuje tylko moduły pobierane przez proces Google Play services;
- obsługi braku modelu przy pierwszym skanie bez sieci, co łamie WNF-01.

## Decyzja
Ekstrakcja działa na własnych regułach w Kotlinie (`extract/EntityExtractor.kt`), w pełni offline:
- telefony: normalizacja do E.164, numery PL 9-cyfrowe → `+48`, rodzaj numeru (komórka /
  stacjonarny / faks) z etykiety lub z prefiksu PL; odrzucane są NIP, kody pocztowe, konta i daty;
- e-maile: naprawa spacji wokół „@” po OCR (z niższą pewnością);
- WWW i profile społecznościowe (LinkedIn itd.) rozpoznawane po hoście, bez domeny z e-maila;
- adresy PL (kod 00-000 i linia ulicy) oraz zagraniczne (Street/Road/…straße, kody UK i 5-cyfrowe).

Każda encja ma pewność = pewność linii OCR × pewność reguły (podstawa dla #19).
Ekstraktor jest zwykłą klasą wstrzykiwaną przez Hilt, więc ML Kit można później dodać jako drugie
źródło – gdy właściciel zgodzi się na uprawnienie INTERNET.

## Konsekwencje
- (+) Brak sieci i nowych uprawnień, działa od pierwszego uruchomienia, APK bez dodatkowego modelu.
- (+) Pełna kontrola nad formatami polskimi (WF-13), testy jednostkowe bez urządzenia.
- (−) Reguły trzeba dostrajać na prawdziwych wizytówkach; dokładność zmierzy #19/test na
  `test-data` (kryterium etapu: ≥ 90% pól).
- (−) Odstępstwo od stosu z REQUIREMENTS sekcja 9 – zgłoszone w PR do akceptacji.
