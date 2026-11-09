# Plan rozwoju

Pełna wersja z ryzykami: dokument „Plan rozwoju – aplikacja do wizytówek (Android)”. Bieżący etap: **1**.

## Etap 0. Fundamenty
- Projekt Android Studio: Kotlin, Jetpack Compose, minSdk 26
- Architektura MVVM, Hilt, Coroutines i Flow
- Baza Room z szyfrowaniem SQLCipher (tabele według modelu danych)
- Nawigacja między ekranami (zaślepki 9 ekranów)
- Motyw Material 3, tryb jasny i ciemny, teksty PL i EN
- Wrapper Gradle i działające CI
Kryterium: aplikacja uruchamia się, przechodzi między ekranami, baza tworzy się zaszyfrowana.

## Etap 1. Skanowanie
- Prototyp: ML Kit Document Scanner vs CameraX – decyzja w PR
- Przepływ: przód, potem tył albo „Pomiń – tył pusty”
- Wykrywanie krawędzi, kadrowanie, prostowanie
- Kontrola jakości i powtórzenie zdjęcia
- Zapis zdjęć w pamięci wewnętrznej, szkic przy przerwaniu
- Import z galerii
Kryterium: 10 różnych wizytówek bez błędów, przerwany skan da się wznowić.

## Etap 2. OCR i ekstrakcja
- Text Recognition v2 na obu stronach, offline
- Wykrywanie języka fragmentów
- Ekstrakcja encji: telefony, e-maile, WWW, adresy
- Reguły: NIP, kody pocztowe, +48, imię i nazwisko, stanowisko
- Scalanie danych z obu stron, wykrywanie konfliktów
- Pewność rozpoznania dla każdego pola
- Test dokładności na zestawie test-data (min. 30 wizytówek)
Kryterium: ≥ 90% pól poprawnie, analiza < 3 s.

## Etap 3. Weryfikacja i zapis
- Ekran podglądu z edycją pól i miniaturami
- Wyróżnienie pól o niskiej pewności
- Wersje PL i EN obok siebie lub przełącznik
- Komentarz tekstowy i dyktowany
- Data skanu, opcjonalnie lokalizacja i wydarzenie
- Zapis z komunikatem „Cofnij”
Kryterium: od zdjęcia do zapisu < 30 s.

## Etap 4. Baza kontaktów
- Powiązanie osoba–firma
- Duplikaty osób (e-mail, telefon, imię i nazwisko)
- Duplikaty firm (nazwa, domena, NIP)
- Ekran duplikatu: aktualizuj albo utwórz nowy
- Historia zatrudnienia i wszystkie wizytówki osoby
- Karta osoby i karta firmy z szybkimi akcjami
- Ręczne scalanie i rozdzielanie rekordów
Kryterium: wizytówka po zmianie pracy aktualizuje rekord z zachowaniem historii.

## Etap 5. Dopracowanie i wydanie 1.0
- Dostępność: TalkBack, duża czcionka, kontrast, obszary dotyku
- Obsługa jedną ręką, przegląd UX
- Uprawnienia z wyjaśnieniem celu
- RODO: usuwanie i eksport danych, polityka prywatności
- Kopia zapasowa: eksport i import pliku
- Testy na kilku telefonach
- Beta zamknięta w Google Play
- Karta w sklepie
Kryterium: 1.0 w Google Play bez krytycznych błędów.
