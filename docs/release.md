# Выпуск Tsunayomi

## 1. Ключ подписи (один раз)

Все обновления должны быть подписаны **одним и тем же ключом** — потеряете его, и установленное приложение
нельзя будет обновить, только удалить и поставить заново.

```bash
keytool -genkeypair -v -keystore tsunayomi-release.jks -alias tsunayomi -keyalg RSA -keysize 4096 -validity 10000
```

`keytool` спросит пароль и данные владельца. Сохраните `.jks` и пароль в менеджере паролей и в резервной копии.
Файл ключа и пароли **никогда** не коммитятся (`*.jks` и `keystore.properties` в `.gitignore`).

## 2. Локальная подписанная сборка

```bash
cp keystore.properties.example keystore.properties   # заполнить пароли
./gradlew assembleRelease -PversionName=0.9.0 -PversionCode=2
```

APK: `app/build.nosync/outputs/apk/release/app-<abi>-release.apk` (для телефонов — `arm64-v8a`).
Без `keystore.properties` и переменных окружения release собирается неподписанным.

## 3. Выпуск через GitHub

1. Секреты репозитория (Settings → Secrets and variables → Actions):
   - `TSUNAYOMI_KEYSTORE_BASE64` — `base64 -i tsunayomi-release.jks | pbcopy`
   - `TSUNAYOMI_KEYSTORE_PASSWORD`, `TSUNAYOMI_KEY_ALIAS`, `TSUNAYOMI_KEY_PASSWORD`
2. Тег: `git tag v0.9.0 && git push origin v0.9.0`
3. Workflow `Release` соберёт APK для `arm64-v8a`, `armeabi-v7a`, `x86_64`, приложит их к GitHub Release
   вместе с `SHA256SUMS.txt`. Файл `mapping.txt` (нужен, чтобы читать стектрейсы из отчётов о падениях —
   код release обфусцирован) лежит в артефактах запуска 90 дней.

`versionName` берётся из тега, `versionCode` — число коммитов, поэтому он растёт с каждым выпуском.

## 4. Перед выпуском — проверка

- `./gradlew lintDebug test testDebugUnitTest`
- Подписанный release на телефоне: каталог MangaDex, детали, чтение, перевод (ML Kit и Gemini), загрузка главы.
  R8 в release может вырезать то, что в debug работает (так было с регистрацией компонентов ML Kit).

## 5. Отчёты о падениях

Без сервера и без сбора данных: при падении отчёт сохраняется на устройстве, при следующем запуске пользователь
видит его целиком и сам решает, поделиться ли им. Чтобы прочитать стектрейс release-сборки:

```bash
$ANDROID_HOME/cmdline-tools/latest/bin/retrace mapping-0.9.0.txt crash.txt
```

## 6. Ограничения распространения

- Google вводит обязательную верификацию разработчика для установки APK на сертифицированные Android-устройства
  (с 30.09.2026 в Бразилии, Индонезии, Сингапуре и Таиланде, глобально — в 2027). Для распространения вне
  Google Play понадобится пройти её (аккаунт разработчика) — иначе пользователям придётся обходить защиту.
- MangaDex: указывать MangaDex и группы-переводчики (есть в «О приложении» и у глав), никакой рекламы и платных
  функций.
