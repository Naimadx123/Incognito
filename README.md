# Incognito

Plugin dla Paper i Folia od 1.21.8 wzwyż (sprawdzany także na 26.x). `/incognito` ukrywa tożsamość gracza: losowy pseudonim `Anon_…`, ukryty lub wspólny skin oraz przesunięte współrzędne. Działa na poziomie pakietów, więc obejmuje F3, listę graczy, nametagi, czat, scoreboard, bossbary, hologramy, tab-complete i teksty z dowolnych pluginów bez integracji po ich stronie.

## Funkcje

- **Pseudonim i skin** — podmiana w profilu, TAB, nametagu i we wszystkich tekstach wysyłanych do klientów; własny skin gracz nadal widzi u siebie. Kolory i formatowanie wiadomości zostają.
- **Współrzędne** — losowe, stałe przesunięcie X/Z całego świata dla gracza (chunki, encje, dźwięki, granica świata, F3, minimapy). Liczby w tekstach i podpowiedziach komend są przesuwane tak samo, a komendy z wpisanymi współrzędnymi przeliczane z powrotem.
- **PlaceholderAPI** — wyniki wszystkich placeholderów przechodzą przez ten sam filtr (`%player_x%`, `%player_name%` itd.), plus własne: `%incognito_enabled%`, `%incognito_name%`, `%incognito_realname%`, `%incognito_x/y/z/world%`.
- **Administracja** — `/incognito player <gracz> [on|off]` (`incognito.admin`), a `incognito.reveal` pozwala widzieć prawdziwe nicki w tekstach.

## Instalacja

Wrzuć `build/libs/Incognito-v1.0-SNAPSHOT.jar` do `plugins` i zrestartuj serwer. Konfiguracja w `plugins/Incognito/config.yml`: każdą funkcję (`names`, `skin`, `coordinates`, `placeholders`, tab-complete) można osobno wyłączyć, komunikaty dla graczy są w sekcji `messages` (MiniMessage), `debug: true` loguje przeliczenia pakietów. Włączenie lub wyłączenie przesunięcia współrzędnych wymaga ponownego połączenia.

## Komendy i uprawnienia

| Komenda | Uprawnienie | Działanie |
| --- | --- | --- |
| `/incognito`, `/incognito on\|off` | `incognito.use` (wszyscy) | Przełącza tryb |
| `/incognito status` | `incognito.use` | Stan i pseudonim |
| `/incognito player <gracz> [on\|off]` | `incognito.admin` (OP) | Tryb innego gracza |

## Budowanie

```powershell
.\gradlew.bat build
.\gradlew.bat build '-PpaperApiVersion=26.3.build.+' -PtargetJava=25
```

Kotlin 2.4, Gradle 9.7, JDK 21+ (Java 25 dla API 26.x).
