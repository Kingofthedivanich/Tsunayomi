# Tsunayomi — проект MVP

Дата: 2026-10-23
Связанные документы: [competitors.md](competitors.md), [auto-translation.md](auto-translation.md)

## 1. Цель MVP

Читалка манги для Android, которая умеет: открыть локальную мангу и мангу из онлайн-источника, удобно её читать и **переводить страницы на лету**. Всё остальное — после MVP.

## 2. Состав

### Входит в MVP
| Область | Функции |
|---|---|
| **Локальные файлы** | Выбор папки через SAF; серия = папка; глава = подпапка с картинками, CBZ/ZIP или PDF (`PdfRenderer`); `cover.jpg` и ComicInfo.xml (название, автор, описание, жанры); пересканирование по pull-to-refresh |
| **Онлайн** | Единый `Source` API; 2 встроенных источника: **MangaDex** (официальный публичный API) и **Komga/OPDS** (self-hosted). Поиск, популярное/новое, фильтры источника |
| **Библиотека** | Избранное с категориями, сетка/список, сортировка, бейдж непрочитанных |
| **Детали тайтла** | Обложка, описание, жанры, список глав, «продолжить чтение», отметки прочитано/не прочитано |
| **Ридер** | Постраничный LTR/RTL, вертикальный, вебтун (непрерывная лента); зум с субсэмплингом; зоны тапа; кнопки громкости; предзагрузка ±3 стр.; сохранение прогресса; режим ридера на тайтл |
| **История** | Последние прочитанные, продолжить, удалить запись |
| **Автоперевод** | Детекция + OCR на устройстве; движки **ML Kit** (без ключа) и **Gemini API** (свой ключ); оверлей поверх страницы; переключатель «оригинал ↔ перевод»; предперевод следующих страниц; кэш |
| **Настройки** | Тема (светлая/тёмная/AMOLED, Material You), ридер по умолчанию, язык перевода, движок и ключ, папка локальной библиотеки |

### После MVP
Загрузки глав · фоновая проверка обновлений и уведомления · OpenAI-совместимый движок, DeepL, офлайн-модель · глоссарий имён · трекеры (AniList, MAL, Shikimori) · бэкап/синхронизация · плагины источников (совместимость с расширениями Mihon) · CBR/EPUB · двойные страницы для планшета · инкогнито, блокировка.

## 3. Стек

| Слой | Выбор | Почему |
|---|---|---|
| Язык / сборка | Kotlin 2.x, Gradle KTS, version catalog, convention plugins | стандарт |
| SDK | minSdk 26, compileSdk 37, targetSdk 36 | ML Kit и ONNX Runtime без хаков; охват >95% устройств |
| UI | Jetpack Compose + Material 3, Navigation Compose (type-safe) | Mihon уже на Compose; адаптивная вёрстка для планшетов |
| Зум в ридере | **Telephoto** (`ZoomableImage`, субсэмплинг) | Compose-нативный, Apache-2.0; не нужен форк SSIV |
| Картинки | Coil 3 (+ OkHttp, GIF, AVIF) | |
| DI | Hilt | проще всего для старта, интеграция с WorkManager и ViewModel |
| БД | Room + Paging 3 | |
| Настройки | DataStore | |
| Сеть | OkHttp 5 + kotlinx.serialization (+ Retrofit для MangaDex/Komga) | |
| Архивы | `java.util.zip` (CBZ), `PdfRenderer` (PDF); junrar — после MVP | |
| Фон | WorkManager, корутины + Flow | |
| ML | ML Kit Text Recognition v2, ML Kit Translation, ONNX Runtime Android (детектор пузырей, manga-ocr) | см. auto-translation.md |
| Тесты | JUnit 5, Turbine, MockK, Robolectric для Room, Compose UI tests | |

Архитектура: **MVVM + UDF** (ViewModel → `StateFlow<UiState>`), слои `ui → domain (use cases) → data (repositories) → source/db/network`.

## 4. Модули

```
:app                      — точка входа, навигация, DI-граф
:core:model               — чистые модели (Manga, Chapter, Page, Category…)
:core:common              — утилиты, Result, dispatchers
:core:database            — Room: entities, DAO, миграции
:core:network             — OkHttp-клиент, cookie, user-agent, CloudflareInterceptor (WebView) — заготовка
:core:datastore           — настройки
:core:designsystem        — тема, компоненты (обложка, сетка, плейсхолдеры)
:core:image               — Coil-настройка, загрузчики для локальных страниц (archive://, pdf://)

:source:api               — интерфейсы Source, CatalogSource, фильтры, модели источника
:source:local             — локальные файлы (SAF, CBZ, PDF, ComicInfo)
:source:mangadex
:source:komga             — Komga REST / OPDS

:data                     — репозитории (Library, Manga, Chapter, History, Source)
:domain                   — use cases (GetLibrary, ToggleFavorite, SaveProgress, …)

:feature:library
:feature:browse           — список источников + каталог + поиск
:feature:details
:feature:reader
:feature:history
:feature:settings

:translation:api          — TextDetector, OcrEngine, Translator, PageTranslator, модели регионов
:translation:core         — оркестратор, кэш, батчинг, fallback, TranslatorRegistry
:translation:ocr-mlkit
:translation:ocr-onnx     — детектор пузырей + manga-ocr
:translation:engine-mlkit
:translation:engine-gemini
```

Правило зависимостей: `feature:*` знают только `domain`, `core:*`, `translation:api`; источники и движки подключаются в `:app` через Hilt multibinding — добавление нового = новый модуль без правок фич.

## 5. API источника (черновик)

```kotlin
interface Source {
    val id: Long            // стабильный хэш от "name/lang/version"
    val name: String
    val lang: String        // "ja", "en", "multi", ...
}

interface CatalogSource : Source {
    val supportsLatest: Boolean
    fun getFilters(): FilterList

    suspend fun getPopular(page: Int): MangaPage
    suspend fun getLatest(page: Int): MangaPage
    suspend fun search(query: String, filters: FilterList, page: Int): MangaPage

    suspend fun getDetails(manga: SManga): SManga
    suspend fun getChapters(manga: SManga): List<SChapter>
    suspend fun getPages(chapter: SChapter): List<SPage>
}

data class MangaPage(val items: List<SManga>, val hasNext: Boolean)

data class SManga(
    val url: String,        // ключ внутри источника
    val title: String,
    val coverUrl: String?,
    val author: String? = null,
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val status: MangaStatus = MangaStatus.Unknown,
    val readingDirection: ReadingDirection? = null, // подсказка ридеру/переводу
    val originalLang: String? = null,               // подсказка OCR
)

data class SChapter(val url: String, val name: String, val number: Float, val uploadedAt: Long?, val scanlator: String?)

sealed interface SPage {
    val index: Int
    data class Remote(override val index: Int, val imageUrl: String, val headers: Map<String, String> = emptyMap()) : SPage
    data class Local(override val index: Int, val uri: String) : SPage   // file://, content://, archive://, pdf://
}
```

Сознательно похоже на Mihon `source-api` — так проще потом сделать адаптер к их расширениям.

## 6. Данные (Room)

| Таблица | Ключевые поля |
|---|---|
| `manga` | id, source_id, url, title, cover_url, author, description, genres, status, favorite, date_added, reader_mode?, original_lang?, translate_enabled? · unique(source_id, url) |
| `chapter` | id, manga_id, url, name, number, scanlator, uploaded_at, read, last_page_read, bookmark, source_order |
| `category` | id, name, order, flags |
| `manga_category` | manga_id, category_id |
| `history` | chapter_id, last_read_at, time_read |
| `translation_page` | page_key (sha1 изображения), source_lang, target_lang, engine, prompt_version, regions_json, created_at |

`regions_json` — список `{bbox, polygon?, srcText, dstText, fontSizeHint, isBubble}` в координатах исходного изображения.

## 7. Перевод в ридере

```
ReaderViewModel
  └─ pages: List<ReaderPage>  ── PageLoader (Coil) ──► Bitmap
                                     │
                     translateEnabled ▼
                PageTranslator.translate(bitmap, ctx)  ── cache hit? ──► regions
                     │ detect → ocr → (batch) translate → regions
                     ▼
     ReaderPage.translation: StateFlow<Idle | Loading | Done(regions) | Error>

UI: ZoomableImage + Canvas-оверлей в том же transform-пространстве (Telephoto отдаёт матрицу),
    заливка маски пузыря + вписанный текст. Тап по кнопке → показать оригинал.
```

- Очередь перевода — приоритет текущей страницы, затем +1…+N, отмена при уходе из главы.
- Gemini: батч по главе (или окну из 5–10 страниц, если глава ещё грузится), ответ в JSON по номерам пузырей.
- Ошибка/лимит → fallback на ML Kit + снэкбар.

## 8. Экраны и навигация

```
Bottom bar: Библиотека · Обзор · История · Ещё
  Библиотека ─► Детали ─► Ридер
  Обзор ─► Источник (каталог/поиск/фильтры) ─► Детали ─► Ридер
          └─ Глобальный поиск (по всем источникам)
  История ─► Ридер
  Ещё ─► Настройки (Общие · Ридер · Перевод · Локальная библиотека · Источники: Komga-сервер) · О приложении
Ридер: верхняя панель (глава, меню режима), нижняя (слайдер страниц, пред/след глава, кнопка «перевод»)
```

## 9. Этапы

| # | Этап | Результат (Definition of Done) |
|---|---|---|
| M0 | Каркас | Многомодульный проект собирается, CI (GitHub Actions: build + lint + unit tests), тема, навигация-заглушки |
| M1 | Локальная библиотека + ридер | Открыть папку, увидеть серии, прочитать CBZ/папку/PDF во всех режимах, прогресс сохраняется |
| M2 | Онлайн-источники | MangaDex и Komga: каталог, поиск, детали, чтение; глобальный поиск |
| M3 | Библиотека и история | Избранное, категории, история, «продолжить» |
| M4 | Автоперевод | ML Kit + Gemini, оверлей, кэш, предперевод; проверено на тестовом наборе JA/KO/EN |
| M5 | Полировка и релиз | Настройки, крэш-репорты (ACRA, opt-in), подписанный APK на GitHub Releases |

M4 можно начинать параллельно с M2 (прототип OCR на тестовых страницах), он зависит только от `:translation:api` и ридера.

## 10. Ограничения и правила

- **MangaDex:** обязательно указывать MangaDex и сканлейт-группы, никакой рекламы и платных функций, выполнять запросы групп на удаление.
- Приложение не хостит контент; встроенные источники — только с официальным API или self-hosted.
- Ключи пользователей — в `EncryptedSharedPreferences`/Keystore, не попадают в логи и бэкапы.
- Распространение: GitHub Releases (+ F-Droid позже); учесть верификацию разработчиков Google (2026–2027).

## 11. Принятые решения

| Вопрос | Решение | Следствия |
|---|---|---|
| Лицензия | **GPL-3.0** | Можно переиспользовать код Kotatsu/Futon (GPL-3.0) и Mihon (Apache-2.0 совместима с GPL-3.0 в одну сторону). Файл `LICENSE` + заголовки в исходниках. AGPL-модели (Ultralytics YOLO) юридически совместимы, но всё равно предпочитаем Apache-2.0 модели |
| Языки оригинала для тестов перевода | **японский, корейский, английский** | Тестовый набор ~10 страниц на язык. OCR: JA — manga-ocr (ONNX), KO и EN — ML Kit Text Recognition v2 (Korean / Latin). Направление чтения по умолчанию: JA — RTL, KO — вебтун, EN — LTR |
| Язык интерфейса | **русский + английский** с первого дня | Все строки только в `strings.xml` (`values/` — английский как базовый, `values-ru/`), per-app language (Android 13+ `LocaleManager`, `locales_config.xml`), lint `HardcodedText` = error |
| Идентификатор пакета | **`app.tsunayomi`** | `applicationId` и корневой пакет `app.tsunayomi`, модули — `app.tsunayomi.core.*`, `app.tsunayomi.feature.*` и т.д. |
