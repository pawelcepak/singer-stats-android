# Singer Stats Visualizer — Platform Parity Contract

## Cel
Singer Stats Visualizer jest jednym produktem wydawanym na trzy platformy: Windows, Linux/Xubuntu i Android.

Od wersji 2.x obowiązuje zasada: **żadna platforma nie może mieć funkcji, której nie mają pozostałe dwie**. Różnić może się tylko sposób obsługi wynikający z interfejsu systemu (mysz/klawiatura vs dotyk, dialog systemowy wyboru pliku, lokalizacja zapisu pliku itp.).

## Source of truth
Ten dokument jest kontraktem funkcjonalnym. Każda nowa opcja ma być projektowana i wdrażana jednocześnie dla Windows, Linux i Android. Wydanie nie jest uznawane za pełne, dopóki wszystkie trzy platformy nie mają tej samej funkcjonalności.

## Wspólny format projektu
Wszystkie platformy używają kompatybilnego pliku `.ssp.json`.

Zasady:
- starsze projekty muszą się otwierać bez ręcznego przepisywania danych,
- zmiany schematu są addytywne i mają bezpieczne wartości domyślne,
- nazwy pól nie są zmieniane bez migracji,
- brak lokalnego pliku audio/obrazu nie może usuwać wokalistów, kolorów, tekstu, przypisań ani timingów,
- ustawienia czysto chwilowe (np. eksport z/bez audio) nie muszą trafiać do projektu, jeśli nie wpływają na jego treść.

## Wspólny zestaw funkcji

### Projekt
- otwieranie i zapisywanie `.ssp.json`,
- wybór audio,
- nazwa utworu,
- okładka/tło,
- kolor ramki tekstu,
- dowolna liczba wokalistów,
- edycja nazwy wokalisty,
- dowolny kolor RGB wokalisty,
- zdjęcie wokalisty: dodaj / zmień / usuń,
- wklejenie tekstu,
- dzielenie tekstu na fragmenty po `.`, `!`, `?`,
- znacznik `(Wokalista)` ustawia bieżącego wokalistę aż do następnego znacznika,
- wiele wokalistów może być przypisanych do jednego fragmentu.

### Timing
- odtwarzanie / pauza,
- skok -5 s / +5 s,
- ręczne START i KONIEC dla każdego fragmentu,
- wybór jednego lub wielu wokalistów dla fragmentu,
- Auto Timing AI,
- wskaźnik postępu i pewności Auto Timing,
- Kalibracja 1 punkt,
- Kalibracja 2 punkty,
- precyzyjny waveform audio,
- kliknięcie/tapnięcie waveformu ustawia kursor,
- zaznaczenie zakresu na waveformie,
- START = kursor,
- KONIEC = kursor,
- START/KONIEC = zaznaczenie,
- precyzyjne przesuwanie kursora: -100 ms, -10 ms, -1 ms, +1 ms, +10 ms, +100 ms,
- zoom i przewijanie waveformu dostosowane do platformy.

### Podgląd i wizualizacja
- pionowy format 9:16,
- TikTok Safe Area,
- nagłówek „KTO ILE ŚPIEWA?”,
- nazwa utworu,
- aktualny czas / długość utworu,
- aktywny tekst,
- zdjęcia i nazwy wokalistów,
- ranking wg skumulowanego czasu śpiewania,
- sekundy i procenty,
- paski w kolorach wokalistów,
- płynna animacja zmiany miejsc, która zawsze kończy przejście,
- ranking docelowy zawsze zgodny z czasem/procentem,
- ten sam układ i logika w podglądzie i finalnym eksporcie.

### Eksport
- MP4 1080×1920,
- 24 fps,
- eksport całego utworu,
- eksport fragmentu OD–DO,
- opcja „Dołącz muzykę / audio do MP4”,
- po wyłączeniu: MP4 całkowicie bez ścieżki audio,
- brak audio nie zmienia obrazu, tekstu, timingów, rankingu ani animacji,
- podgląd ostatniego eksportu lub łatwe otwarcie pliku po eksporcie, zależnie od platformy.

### Publikacje / notatki
- osobna sekcja „Publikacje / notatki”,
- lista wpisów publikacyjnych,
- dodawanie, duplikowanie i usuwanie wpisów,
- pola planera dostępne identycznie na wszystkich platformach,
- kopiowanie tekstu,
- kopiowanie całego wpisu,
- lokalny zapis planera,
- import CSV,
- eksport CSV.

## Zasady UI
Funkcje muszą być identyczne, ale kontrolki mogą być dostosowane do platformy:
- Windows/Linux: mysz, klawiatura, przeciąganie i kółko myszy,
- Android: dotyk, pinch-to-zoom, przeciąganie i systemowy picker plików.

Adaptacja UI nie może usuwać możliwości.

## Zasada wydawania wersji
Nowy numer funkcjonalny (np. 2.1, 2.2) jest wspólny dla wszystkich platform.

Przed wydaniem należy sprawdzić macierz:

| Funkcja | Windows | Linux | Android |
|---|---|---|---|
| Projekt | ✅ | ✅ | ✅ |
| Timing | ✅ | ✅ | ✅ |
| Waveform/precyzja | ✅ | ✅ | ✅ |
| Auto Timing AI | ✅ | ✅ | ✅ |
| Kalibracje | ✅ | ✅ | ✅ |
| TikTok Safe | ✅ | ✅ | ✅ |
| Podgląd | ✅ | ✅ | ✅ |
| Eksport z audio | ✅ | ✅ | ✅ |
| Eksport bez audio | ✅ | ✅ | ✅ |
| Publikacje/notatki | ✅ | ✅ | ✅ |
| `.ssp.json` compatibility | ✅ | ✅ | ✅ |

Jeżeli choć jedna komórka nie jest ✅, wersja nie jest traktowana jako pełne wydanie parity.

## Reguła dla przyszłych zmian
Każde nowe życzenie użytkownika dotyczące Singer Stats Visualizer ma być traktowane jako zmiana wspólnego produktu. Nie wdrażamy już funkcji tylko na jednej platformie, chyba że użytkownik wyraźnie poprosi o wyjątek.
