# Анализ конкурентов: читалки манги для Android

Дата: 2026-10-23

## 1. Обзор рынка

| Приложение | Платформа | Статус | Лицензия | UI-стек | Источники |
|---|---|---|---|---|---|
| **Kotatsu** | Android 6+ | ❌ Архивирован 04.11.2025 (давление Kakao + политика Google по sideloading) | GPL-3.0 | Views/XML, Hilt, Room | 1200+ парсеров, **вкомпилированы** в APK (библиотека `kotatsu-parsers`) |
| **Futon** (форк Kotatsu) | Android | ✅ Активен (v9.7, апр. 2026) | GPL-3.0 | как у Kotatsu | Парсеры Kotatsu-Redo + совместимость с расширениями Tachiyomi (keiyoushi) |
| **Mihon** (наследник Tachiyomi) | Android 8+ | ✅ Активен, ~24.6k★ | Apache-2.0 | Compose, Voyager, SQLDelight | **Внешние APK-расширения**, загружаются через ClassLoader; репозитории расширений сторонние |
| **Komikku** (форк Mihon/SY) | Android 8+ | ✅ Активен | Apache-2.0 | как у Mihon | Расширения Mihon |
| **Mangayomi** | Android/iOS/desktop | ✅ Активен | Apache-2.0 | Flutter + Rust | Расширения на Dart (dart_eval) и JavaScript |
| **Aidoku** | iOS/macOS | ✅ Бета | GPL-3.0 | Swift | WASM-источники; встроены Komga/Kavita/Suwayomi |
| **Suwayomi** | Сервер (JVM) + веб | ✅ | MPL-2.0 | — | Запускает расширения Tachiyomi на сервере |

## 2. Функционал (что считается «стандартом»)

**Библиотека**
- Избранное с пользовательскими категориями (в т.ч. скрытые — Komikku)
- История чтения, закладки на страницах, режим инкогнито
- Плановое обновление библиотеки (WorkManager) → уведомления о новых главах, лента обновлений
- Рекомендации/похожие тайтлы (Kotatsu, Komikku — с сайтов источников и через MAL/AniList)
- Объединение одного тайтла из разных источников (merge) и миграция между источниками (SY/Komikku)

**Источники**
- Каталоги онлайн: поиск, фильтры по жанрам/статусу/сортировке, «популярное/новое»
- Локальные файлы: папки с изображениями, CBZ/ZIP, CBR/RAR, EPUB (Mihon); `cover.jpg` в папке серии; ComicInfo.xml
- Self-hosted серверы: Komga, Kavita, Suwayomi (Aidoku встроенно, Mihon — расширениями)
- Обход Cloudflare через WebView, DNS-over-HTTPS

**Ридер**
- Режимы: постраничный (LTR/RTL), вертикальный, **вебтун** (непрерывная лента)
- Зум с субсэмплингом больших изображений (Subsampling Scale Image View — оба лидера используют свои форки)
- Настраиваемые зоны тапа, жесты, кнопки громкости, обрезка полей, цветовые фильтры, двойные страницы для планшетов
- Предзагрузка страниц, AVIF/WebP/GIF
- OCR + словарь (Aidoku) — редкая фича

**Офлайн и данные**
- Загрузки глав (очередь, CBZ), бэкапы (локально/облако)
- Синхронизация между устройствами (Kotatsu — свой сервер синхронизации; Komikku — 2-way sync с трекерами)
- Трекеры: AniList, MyAnimeList, Kitsu, Shikimori, MangaUpdates, Bangumi

**Прочее**
- Material You, тёмная/AMOLED тема, автоцвет темы по обложке (Komikku)
- Блокировка паролем/биометрией, планшетная вёрстка, виджеты

## 3. Архитектура двух главных подходов

### Kotatsu: источники внутри приложения
- Монолит `app` + отдельная JVM-библиотека `kotatsu-parsers` (подключается как зависимость, KSP генерирует реестр источников из аннотаций).
- Хост реализует `MangaLoaderContext` (HTTP-клиент, cookie, выполнение JS и т.п.), парсер получают так: `context.newParserInstance(MangaParserSource.X)`.
- **Плюсы:** просто, нет загрузки чужого кода, одна сборка; библиотеку переиспользуют другие проекты (Doki, kotatsu-dl).
- **Минусы:** сломанный источник чинится только релизом приложения; весь «контент» в одном APK → юридическая мишень (что и привело к закрытию).

### Mihon: источники как внешние плагины
- Многомодульный: `app`, `core`, `domain`, `data`, `presentation-*`, `source-api`, `source-local`.
- Расширение — отдельный APK с `uses-feature tachiyomi.extension` и метаданными (`tachiyomi.extension.class`, `tachiyomix.extensionLib` и т.п.).
- `ExtensionLoader`: проверка версии lib API (сейчас 1.4–1.6), проверка подписи и доверия, загрузка через delegate-last ClassLoader, инстанцирование `Source` / `SourceFactory`, изоляция ошибок по каждому расширению.
- Каталог расширений — `index.min.json` в стороннем репозитории, который пользователь добавляет сам.
- **Плюсы:** приложение «чистое» (не содержит источников), источники обновляются независимо, огромная экосистема (keiyoushi).
- **Минусы:** сложнее; чужой код с полными правами приложения; привязка к Android-APK.

### Альтернативные модели
- **WASM (Aidoku)** и **JS/Dart-скрипты (Mangayomi)** — песочница и кроссплатформенность, но ниже производительность и беднее API.

## 4. Технологии лидеров

| | Kotatsu | Mihon |
|---|---|---|
| UI | Views, RecyclerView, ViewPager2 | Jetpack Compose, Material3, Voyager |
| DI | Hilt | Injekt → Metro |
| БД | Room | SQLDelight |
| Сеть | OkHttp 5, Moshi, Conscrypt | OkHttp 5 (Brotli, Zstd, DoH), Apollo GraphQL, jsoup |
| Картинки | Coil 3 + AVIF, форк SSIV | Coil 3, форк SSIV |
| Фон | WorkManager | WorkManager |
| Прочее | ACRA, Markwon | libarchive, QuickJS, Shizuku, kotlinx-serialization |

## 5. Риски

- **Юридический:** Kotatsu закрыт из-за претензий правообладателя. Безопаснее модель Mihon — приложение без встроенных источников, источники — сторонние плагины.
- **Верификация разработчиков Google:** с 30.09.2026 в Бразилии, Индонезии, Сингапуре, Таиланде, глобально — 2027. Установка APK (в т.ч. через F-Droid) потребует верифицированного разработчика. Для расширений-APK это двойная проблема — плагинная модель **без APK** (JS/WASM/декларативные) переживёт это лучше.

## 6. Выводы для нашего приложения

1. **Стек:** Kotlin + Jetpack Compose + Material3, Room или SQLDelight, OkHttp + Coil 3, WorkManager, Hilt/Koin. Модульная архитектура как у Mihon (`domain` / `data` / `source-api` / `source-local` / `feature-*`).
2. **Абстракция источника** — с первого дня единый интерфейс `Source` (каталог, поиск, детали, главы, страницы) с реализациями: `LocalSource` (папки/CBZ/CBR/EPUB/PDF, ComicInfo.xml), `KomgaSource`/`KavitaSource` (OPDS/REST), плагины для онлайн-источников.
3. **Плагины:** стоит рассмотреть совместимость с расширениями Mihon (Futon показал, что это реально) — сразу получаем тысячи источников, не поставляя ни одного своего. Альтернатива/дополнение — скриптовые источники (QuickJS) без APK.
4. **MVP:** локальная библиотека + ридер (страничный/вебтун, зум) + один-два онлайн-источника + избранное/история. Потом загрузки, обновления, трекеры, бэкап/синхронизация.
5. **Отличия, на которых можно выиграть:** современный Compose-UI с хорошей планшетной вёрсткой, сильная работа с локальными файлами (Kotatsu здесь был слаб), встроенные Komga/Kavita/OPDS, OCR/перевод, синхронизация без своего сервера (WebDAV / Google Drive).

## Источники
- https://github.com/KotatsuApp/Kotatsu
- https://github.com/KotatsuApp/kotatsu-parsers
- https://github.com/mihonapp/mihon (+ `gradle/libs.versions.toml`, `ExtensionLoader.kt`)
- https://mihon.app/docs/guides/local-source/
- https://github.com/komikku-app/komikku
- https://github.com/kodjodevf/mangayomi
- https://github.com/Aidoku/Aidoku
- https://cloudflare.f-droid.org/ko/packages/io.github.landwarderer.futon
- https://f-droid.org/fr/2026/02/24/open-letter-opposing-developer-verification.html
- https://pinggy.io/blog/f_droid_2_0_android_developer_verification/
