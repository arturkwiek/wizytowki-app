# Wymagania – aplikacja do zarządzania wizytówkami (Android)

> **Dla agenta AI / dewelopera (Codex, Copilot, Claude Code itp.):**
> Ten plik to specyfikacja wymagań (wersja 1). Każde wymaganie ma identyfikator (WF-xx, WNF-xx, UI-xx) – odwołuj się do niego w commitach, testach i komentarzach.
> Zakres wersji 1: sekcje 1–6, 8, 8a, 9–11. Sekcja 7 to funkcje na później – NIE implementuj ich teraz.
> Platforma docelowa: Android, Kotlin, Jetpack Compose. Aplikacja dla jednej osoby prywatnej, dane wyłącznie lokalnie.
> W razie niejasności pytaj zamiast zgadywać. Otwarte kwestie są w sekcji 12.

## 1. Cel i zakres

Aplikacja mobilna na Androida do szybkiego skanowania papierowych wizytówek telefonem i budowania z nich uporządkowanej bazy kontaktów (osoby i firmy). Głównym celem jest wprowadzanie danych i budowa bazy. Wyszukiwanie i porządkowanie są funkcjami wtórnymi.

## 2. Założenia

- Platforma: Android (w pierwszej kolejności), minimalnie Android 8.0 (API 26).
- Języki wizytówek: polski i angielski, także mieszane (np. przód PL, tył EN).
- Wizytówka ma zawsze dwie strony, z możliwością pominięcia pustego tyłu.
- Skanowanie wyłącznie pojedynczo: jedna wizytówka na raz (bez trybu seryjnego).
- Aplikacja dla osoby prywatnej: jeden użytkownik, bez współdzielenia bazy i funkcji dla firm czy zespołów.
- Dane przechowywane lokalnie na urządzeniu (wersja 1).

## 3. Wymagania funkcjonalne – skanowanie

- WF-01: Użytkownik robi zdjęcie przodu wizytówki w aplikacji.
- WF-02: Po zdjęciu przodu aplikacja od razu prosi o zdjęcie tyłu i daje opcję „Pomiń – tył pusty”.
- WF-03: Automatyczne wykrywanie krawędzi, kadrowanie i prostowanie perspektywy.
- WF-04: Kontrola jakości zdjęcia (ostrość, oświetlenie), z prośbą o powtórzenie, jeśli jakość jest za słaba.
- WF-05: Możliwość powtórzenia zdjęcia każdej strony przed analizą.
- WF-06: Opcjonalny import zdjęć z galerii.

## 4. Wymagania funkcjonalne – rozpoznawanie (OCR) i ekstrakcja danych

- WF-10: OCR obu stron, z obsługą polskich znaków (ą, ć, ę, ł, ń, ó, ś, ź, ż).
- WF-11: Automatyczne wykrywanie języka każdego fragmentu tekstu (PL/EN).
- WF-12: Rozpoznawanie pól: imię, nazwisko, stanowisko, dział, firma, telefon(y), e-mail, strona WWW, adres, NIP, profile społecznościowe (np. LinkedIn).
- WF-13: Obsługa formatów polskich i międzynarodowych: +48, kody pocztowe 00-000, NIP, adresy PL i zagraniczne.
- WF-14: Połączenie danych z obu stron w jeden rekord.
- WF-15: Scalanie pól powtórzonych na obu stronach. Przy sprzecznych danych aplikacja pyta użytkownika.
- WF-16: Dla pól zależnych od języka (stanowisko, dział, adres, opis firmy) przechowywane są obie wersje: polska i angielska.
- WF-17: Pola niezależne od języka (imię, nazwisko, telefon, e-mail, WWW) zapisywane są raz.

## 5. Wymagania funkcjonalne – weryfikacja i komentarz

- WF-20: Ekran podglądu z rozpoznanymi polami, z możliwością edycji każdego pola przed zapisem.
- WF-21: Wersje PL i EN wyświetlane obok siebie.
- WF-22: Oznaczenie pól o niskiej pewności rozpoznania.
- WF-23: Komentarz użytkownika: tekstowy oraz dyktowany głosowo (zamiana mowy na tekst, PL i EN).
- WF-24: Automatyczny zapis daty skanowania. Opcjonalnie miejsce (lokalizacja GPS) i wydarzenie, na którym poznano kontakt.
- WF-25: Oba oryginalne zdjęcia (przód i tył) zapisywane w rekordzie.

## 6. Wymagania funkcjonalne – baza kontaktów

- WF-30: Model danych rozdziela byty: Osoba, Firma, Wizytówka (źródło danych).
- WF-31: Osoba jest powiązana z firmą. Wiele osób może należeć do jednej firmy (bez dublowania danych firmy).
- WF-32: Wykrywanie duplikatów osób po e-mailu, telefonie lub imieniu i nazwisku. Aplikacja proponuje aktualizację istniejącego rekordu zamiast tworzenia nowego.
- WF-33: Wykrywanie duplikatów firm po nazwie, domenie WWW, domenie e-mail lub NIP.
- WF-34: Historia kontaktu: poprzednie stanowiska i firmy z datami oraz wszystkie zeskanowane wizytówki danej osoby.
- WF-35: Możliwość ręcznego scalenia i rozdzielenia rekordów.

## 7. Funkcje wtórne (kolejne wersje – poza zakresem wersji 1)

- Wyszukiwanie pełnotekstowe (także bez polskich znaków, np. „Lodz” znajduje „Łódź”).
- Tagi, filtry, sortowanie.
- Eksport do kontaktów Androida, vCard i CSV.
- Integracja z CRM.
- Własna cyfrowa wizytówka (QR, NFC).
- Synchronizacja w chmurze i wersja na iOS.

## 8. Wymagania niefunkcjonalne

- WNF-01: OCR działa offline, na urządzeniu.
- WNF-02: Czas od zdjęcia tyłu do ekranu podglądu: poniżej 3 sekund na typowym telefonie.
- WNF-03: Zgodność z RODO: dane lokalne, szyfrowanie bazy, możliwość usunięcia i eksportu danych kontaktu.
- WNF-04: Uprawnienia tylko niezbędne (aparat, opcjonalnie mikrofon i lokalizacja), z wyjaśnieniem celu.
- WNF-05: Interfejs w języku polskim i angielskim.
- WNF-06: Kopia zapasowa bazy (eksport i import pliku).

## 8a. Interfejs użytkownika

Najważniejsze jest szybkie zeskanowanie wizytówki: od otwarcia aplikacji do zapisu rekordu w mniej niż 30 sekund, przy minimalnej liczbie dotknięć.

### Zasady

- UI-01: Jedna główna akcja na ekranie. Na ekranie głównym dominuje przycisk „Skanuj”.
- UI-02: Aplikacja pyta tylko wtedy, gdy musi (duplikat, sprzeczne dane, słaba jakość zdjęcia). Bez zbędnych okien potwierdzeń.
- UI-03: Jasne komunikaty krok po kroku w aparacie: „Zrób zdjęcie przodu”, „Teraz tył”, „Pomiń – tył pusty”.
- UI-04: Automatyczne wyzwalanie zdjęcia po wykryciu ostrej, wykadrowanej wizytówki, z możliwością zrobienia go ręcznie.
- UI-05: Każdy krok można cofnąć bez utraty danych (np. powtórzenie zdjęcia tyłu nie kasuje przodu).
- UI-06: Pola o niskiej pewności rozpoznania są wyróżnione kolorem i ikoną. Kolor nie jest jedynym sygnałem.
- UI-07: Wersje PL i EN pokazane jako przełącznik lub obok siebie przy polach zależnych od języka. Pola niezależne od języka pokazane raz.
- UI-08: Komentarz dodawany jednym dotknięciem ikony mikrofonu albo z klawiatury.
- UI-09: Przerwany skan (np. telefon przychodzący) zapisuje się jako szkic i można do niego wrócić.
- UI-10: Obsługa jedną ręką: najważniejsze przyciski w dolnej części ekranu.
- UI-11: Dostępność: duże obszary dotyku (min. 48 dp), kontrast WCAG AA, obsługa TalkBack i powiększonej czcionki.
- UI-12: Tryb jasny i ciemny, zgodnie z Material Design 3.
- UI-13: Szybka informacja zwrotna: po zapisie krótki komunikat z opcją „Cofnij”.

### Ekrany

1. Ekran główny: przycisk „Skanuj”, lista ostatnio dodanych kontaktów, dostęp do bazy osób i firm.
2. Aparat – przód: podgląd z ramką wizytówki, podpowiedź, automatyczne lub ręczne zdjęcie.
3. Aparat – tył: ten sam widok, miniatura przodu, przycisk „Pomiń – tył pusty”.
4. Analiza: krótki wskaźnik postępu (poniżej 3 sekund).
5. Podgląd i edycja: miniatury obu stron, rozpoznane pola z wyróżnieniem wątpliwych, wersje PL i EN, komentarz (tekst lub głos), przycisk „Zapisz”.
6. Duplikat: porównanie nowych i istniejących danych obok siebie, wybór „Zaktualizuj istniejący” lub „Utwórz nowy”.
7. Karta osoby: dane kontaktowe, aktualna firma i stanowisko, historia, wszystkie wizytówki i komentarze, szybkie akcje (zadzwoń, e-mail, otwórz mapę).
8. Karta firmy: dane firmy i lista powiązanych osób.
9. Ustawienia: język interfejsu, uprawnienia, kopia zapasowa, usuwanie danych.

## 9. Proponowany stos technologiczny (Android)

- Język: Kotlin.
- UI: Jetpack Compose.
- Aparat: CameraX lub Google ML Kit Document Scanner (wykrywanie krawędzi, kadrowanie, prostowanie).
- OCR: Google ML Kit Text Recognition v2 (skrypt łaciński – obsługuje polski i angielski, działa offline).
- Wykrywanie języka: ML Kit Language Identification.
- Ekstrakcja encji: ML Kit Entity Extraction (telefony, e-maile, adresy, URL) oraz własne reguły (regex) dla NIP, kodów pocztowych i stanowisk.
- Dyktowanie komentarza: Android SpeechRecognizer.
- Baza danych: Room (SQLite), szyfrowanie SQLCipher.
- Przechowywanie zdjęć: pamięć wewnętrzna aplikacji.
- Architektura: MVVM, Hilt (wstrzykiwanie zależności), Kotlin Coroutines i Flow.

## 10. Model danych (szkic)

- **Osoba**: id, imię, nazwisko, telefony, e-maile, WWW, profile, komentarz, data utworzenia, id aktualnej firmy.
- **Firma**: id, nazwa, NIP, WWW, domena e-mail, adres PL, adres EN, opis PL, opis EN.
- **Zatrudnienie** (historia): id osoby, id firmy, stanowisko PL, stanowisko EN, dział PL, dział EN, data od, data do.
- **Wizytówka**: id, id osoby, zdjęcie przodu, zdjęcie tyłu, surowy tekst OCR, data skanu, lokalizacja, wydarzenie.

## 11. Główny scenariusz użycia

1. Użytkownik klika „Nowa wizytówka”.
2. Robi zdjęcie przodu, potem tyłu (albo pomija tył).
3. Aplikacja wykonuje OCR, rozpoznaje język i pola, a dane łączy w jeden rekord.
4. Aplikacja sprawdza duplikaty osoby i firmy.
5. Użytkownik weryfikuje dane na ekranie podglądu i dodaje komentarz (tekst lub głos).
6. Zapis: rekord nowy lub aktualizacja istniejącego, z zachowaniem historii.

## 12. Otwarte pytania

- Synchronizacja / kopia w chmurze – decyzja odłożona; w wersji 1 dane tylko lokalnie.
