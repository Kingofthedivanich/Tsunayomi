# Tsunayomi

Читалка манги для Android с автопереводом страниц. Локальные файлы, онлайн-источники и перевод на лету.

> Статус: ранняя разработка (этап M0, каркас проекта).

## Документы

- [Анализ конкурентов](docs/competitors.md)
- [Автоперевод](docs/auto-translation.md)
- [Проект MVP](docs/mvp.md)

## Сборка

Нужны JDK 17+ и Android SDK (platform 36).

```bash
./gradlew assembleDebug
```

APK: `app/build.nosync/outputs/apk/debug/`. Папки сборки названы `*.nosync`, чтобы iCloud их не синхронизировал.

## Структура

| Модуль | Назначение |
|---|---|
| `app` | Точка входа, навигация, DI-граф |
| `core:*` | Модели, БД (Room), настройки (DataStore), сеть (OkHttp), тема |
| `data` | Репозитории |
| `source:api` | Интерфейс источников манги |
| `translation:api` | Интерфейсы пайплайна перевода |
| `feature:*` | Экраны |
| `build-logic` | Convention-плагины Gradle |

## Лицензия

[GPL-3.0](LICENSE)
