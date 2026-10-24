# Changelog / История изменений

All notable changes to Tsunayomi are documented in this file.
Все заметные изменения проекта Tsunayomi документируются в этом файле.

---

## [0.10.0] - 2026-10-24

### Русский

#### Новое
- **Экран WebView для Cloudflare:** встроенный экран браузера для ручной проверки капчи / Cloudflare challenge с сохранением cookies в `CookieManager`, кнопками обновления, открытия в браузере и автоматической перезагрузкой каталога/тайтла при возврате.
- **Индивидуальная память перевода:** переключатель перевода теперь сохраняется для каждого тайтла отдельно в базе данных (миграция схемы БД 6 → 7).
- **Офлайн manga-ocr для японского:** встроенная легковесная модель `manga-ocr` на ONNX Runtime для качественного распознавания вертикального японского текста. При автоопределении языка японский OCR запускается только на страницах с каной.
- **Прогресс чтения и категории в библиотеке:**
  - Отображение прогресса в бейдже карточки манги: «Прочитано / Всего».
  - Категории «Читаю», «Прочитано», «Буду читать», «Брошено».
  - Уведомления о новых главах приходят только для тайтлов в категории «Читаю».
- **Мастер настройки при первом запуске:** приветственный экран онбординга с конфигурацией API-ключей, автоматической проверкой доступности моделей перевода и загрузкой офлайн-моделей.
- **Безопасность и приватность:**
  - Режим инкогнито (главы не попадают в историю).
  - Биометрическая блокировка приложения (App Lock: отпечаток пальца / распознавание лица).
  - Полная блокировка сбора и отправки телеметрии/аналитики ML Kit в сервисы Google.
- **Локальные файлы:** поддержка форматов CBR (.cbr, RAR4/RAR5) и EPUB для чтения локальных файлов наряду с CBZ.
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
- **Offline manga-ocr for Japanese:** Embedded lightweight ONNX Runtime `manga-ocr` model for vertical Japanese text recognition. When language is auto-detected, manga-ocr activates only on pages containing kana characters.
- **Library Reading Progress & Categories:**
  - Badge counter showing "Read / Total" chapters.
  - Categories: Reading, Completed, Plan to read, Dropped.
  - Background chapter notifications triggered exclusively for titles in the "Reading" category.
- **Onboarding & API Configuration:** First-launch setup wizard with live model validation and optional offline OCR download.
- **Privacy & Security:**
  - Incognito reading mode.
  - Biometric app lock (Fingerprint / Face Unlock).
  - Strict blocking of ML Kit telemetry and diagnostic logging to Google.
- **Local Reading Support:** Added support for CBR (.cbr, RAR4/RAR5) and EPUB formats alongside CBZ.
- **New Translation Providers:**
  - DeepL API Free with real-time monthly character quota tracking.
  - OpenAI-compatible LLM endpoints (DeepSeek, OpenRouter, Groq, local Ollama).
- **Backup & Restore:** Full backup and restore for library titles, categories, history, and custom glossaries.
- **Built-in Sources:** Webtoons (LINE Webtoon EN), Naver Webtoon (KO Raw), Remanga (RU), Asura Scans (EN).
- **Extension Support:** Compatibility with Tachiyomi / Mihon extensions and Keiyoushi repositories.
- **Library Updates:** Periodic background chapter update checks via WorkManager with system notifications and a dedicated Updates tab.
