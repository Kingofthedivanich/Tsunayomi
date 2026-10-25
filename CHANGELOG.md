# Changelog / История изменений

All notable changes to Tsunayomi are documented in this file.
Все заметные изменения проекта Tsunayomi документируются в этом файле.

---

## [0.11.0] - 2026-10-25

### Русский

#### Новое
- **Около 70 сайтов из Kotatsu.** Встроены парсеры [Kotatsu](https://github.com/KotatsuApp/kotatsu-parsers), которые прошли проверку: каталог, главы и страницы открываются. Ставить ничего не нужно, источники сразу в «Обзоре».
- **Поиск по тегам.** Нажмите на тег в тайтле — и ищите его в этом источнике или во всех сразу. В каталоге источника иконка ярлыка открывает список всех тегов: можно отметить несколько и найти тайтлы, у которых есть все отмеченные.
- **Поиск по авторам.** Авторы и художники показаны чипами перед тегами; нажатие ищет их тайтлы в источнике или везде.
- **Глобальный поиск** с переключателем «Название | Тег | Автор». Источники, которые не умеют так искать, перечислены отдельно.
- **Remanga** показывает автора и художника, ищет по жанрам и авторам. **MangaDex** ищет по тегам и авторам.
- **Расширения:** кнопки «Установить все» и «Обновить все». У расширения с вариантом «Все языки» в «Обзоре» больше нет копий для каждого языка.

#### Исправлено
- WebView открывает сайт с тем же User-Agent и заголовками, что и источник, поэтому пройденная проверка Cloudflare работает и в приложении.
- Страницы источников Kotatsu загружаются через перехватчик самого парсера (с cookies и расшифровкой картинок) и открываются сразу после запуска приложения.
- Ошибки поиска в источниках Kotatsu показываются, а не превращаются в пустой список.
- Описания тайтлов из Kotatsu показываются текстом, а не HTML-разметкой; авторы — без скобок и кавычек.
- Фильтр 18+ решается нашим списком проверенных источников; Klz9 больше не скрыт как 18+.
- Проверка ключа OpenRouter: «Ключ недействителен» только при неверном ключе; в остальных случаях показывается сообщение сервиса, например что модель недоступна в регионе.

---

### English

#### New Features
- **About 70 Kotatsu sites.** Verified [Kotatsu](https://github.com/KotatsuApp/kotatsu-parsers) parsers are built in: catalog, chapters and pages load. Nothing to install; they are in Browse.
- **Tag search.** Tap a tag on a title to search it in that source or everywhere. In a source's catalog the tag icon lists all its tags; tick several to find titles that have all of them.
- **Author search.** Authors and artists are shown as chips before the tags; tapping one searches their titles.
- **Global search** with a Title | Tag | Author switch; sources that can't search that way are listed apart.
- **Remanga** shows the author and artist and searches by genre and author. **MangaDex** searches by tag and author.
- **Extensions:** Install all and Update all buttons. An extension with an all-languages source no longer shows a copy per language in Browse.

#### Fixes
- WebView opens sites with the source's own User-Agent and headers, so a passed Cloudflare check works in the app too.
- Kotatsu pages load through the parser's own interceptor (cookies, unscrambling) and open right after the app starts.
- Search errors in Kotatsu sources are shown instead of an empty list.
- Kotatsu descriptions are shown as text instead of HTML markup, and authors without brackets and quotes.
- The 18+ filter follows our verified source list; Klz9 is no longer hidden as 18+.
- OpenRouter key check: "invalid key" only for a bad key; other failures show the service's message, such as a model unavailable in the region.

---

## [0.10.0] - 2026-10-24

### Русский

#### Новое
- **Экран WebView для Cloudflare:** встроенный экран браузера для ручной проверки капчи / Cloudflare challenge с сохранением cookies в `CookieManager`, кнопками обновления, открытия в браузере и автоматической перезагрузкой каталога/тайтла при возврате.
- **Индивидуальная память перевода:** переключатель перевода теперь сохраняется для каждого тайтла отдельно в базе данных (миграция схемы БД 6 → 7).
- **Офлайн manga-ocr для японского:** модель `manga-ocr` на ONNX Runtime (около 130 МБ, скачивается отдельно по желанию) для качественного распознавания вертикального японского текста. При автоопределении языка японский OCR запускается только на страницах с каной.
- **Прогресс чтения и категории в библиотеке:**
  - Отображение прогресса в бейдже карточки манги: «Прочитано / Всего».
  - Категории «Читаю», «Прочитано», «Буду читать», «Брошено».
  - Уведомления о новых главах приходят только для тайтлов в категории «Читаю».
- **Мастер настройки при первом запуске:** приветственный экран онбординга с конфигурацией API-ключей, автоматической проверкой доступности моделей перевода и загрузкой офлайн-моделей.
- **Безопасность и приватность:**
  - Режим инкогнито (главы не попадают в историю).
  - Биометрическая блокировка приложения (App Lock: отпечаток пальца / распознавание лица).
  - Полная блокировка сбора и отправки телеметрии/аналитики ML Kit в сервисы Google.
- **Локальные файлы:** поддержка форматов CBR (.cbr, только RAR4; RAR5 и архивы с паролем не поддерживаются) и EPUB для чтения локальных файлов наряду с CBZ.
- **Новые движки перевода:**
  - DeepL API Free с визуальным отслеживанием лимита символов в месяц.
  - OpenAI-совместимый API (DeepSeek, OpenRouter, Groq, локальный Ollama).
- **Резервное копирование и восстановление:** экспорт и импорт библиотеки, глав, истории и глоссария в сжатом JSON/GZip формате.
- **Встроенные источники каталогов:** Webtoons (LINE Webtoon EN), Naver Webtoon (KO Raw), Remanga (RU), Asura Scans (EN).
- **Поддержка расширений:** поддержка расширений экосистемы Tachiyomi / Mihon и репозиториев Keiyoushi.
- **Обновления:** автоматическая фоновая проверка новых глав через WorkManager, вкладка «Обновления» и системные пуш-уведомления.

---

### English

#### New Features
- **Cloudflare WebView Screen:** Built-in web view for manual Cloudflare challenge verification with shared `CookieManager` session, refresh/browser controls, and automatic catalog/details reload upon return.
- **Per-title Translation Persistence:** Translation toggle state is now remembered per-title in the database (Room schema migration 6 → 7).
- **Offline manga-ocr for Japanese:** Optional ONNX Runtime `manga-ocr` model (about 130 MB, downloaded on request) for vertical Japanese text recognition. When language is auto-detected, manga-ocr activates only on pages containing kana characters.
- **Library Reading Progress & Categories:**
  - Badge counter showing "Read / Total" chapters.
  - Categories: Reading, Completed, Plan to read, Dropped.
  - Background chapter notifications triggered exclusively for titles in the "Reading" category.
- **Onboarding & API Configuration:** First-launch setup wizard with live model validation and optional offline OCR download.
- **Privacy & Security:**
  - Incognito reading mode.
  - Biometric app lock (Fingerprint / Face Unlock).
  - Strict blocking of ML Kit telemetry and diagnostic logging to Google.
- **Local Reading Support:** Added support for CBR (.cbr, RAR4 only; RAR5 and password-protected archives are not supported) and EPUB formats alongside CBZ.
- **New Translation Providers:**
  - DeepL API Free with real-time monthly character quota tracking.
  - OpenAI-compatible LLM endpoints (DeepSeek, OpenRouter, Groq, local Ollama).
- **Backup & Restore:** Full backup and restore for library titles, categories, history, and custom glossaries.
- **Built-in Sources:** Webtoons (LINE Webtoon EN), Naver Webtoon (KO Raw), Remanga (RU), Asura Scans (EN).
- **Extension Support:** Compatibility with Tachiyomi / Mihon extensions and Keiyoushi repositories.
- **Library Updates:** Periodic background chapter update checks via WorkManager with system notifications and a dedicated Updates tab.
