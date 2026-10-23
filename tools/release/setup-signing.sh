#!/usr/bin/env bash
# Creates the release signing key and uploads it to GitHub Actions secrets in one go.
# You type the password; it is never echoed, never passed on the command line and never written to a file.
set -euo pipefail
cd "$(dirname "$0")/../.."

REPO="${1:-Kingofthedivanich/Tsunayomi}"
KEYSTORE="tsunayomi-release.jks"
ALIAS="tsunayomi"

KEYTOOL="$(command -v keytool || true)"
if [ -z "$KEYTOOL" ] && [ -x /opt/homebrew/opt/openjdk@21/bin/keytool ]; then KEYTOOL=/opt/homebrew/opt/openjdk@21/bin/keytool; fi
[ -n "$KEYTOOL" ] || { echo "keytool не найден (нужен JDK)."; exit 1; }
command -v gh >/dev/null || { echo "gh не найден."; exit 1; }
gh auth status >/dev/null 2>&1 || { echo "gh не авторизован: выполните gh auth login."; exit 1; }
if [ -e "$KEYSTORE" ]; then
  echo "$KEYSTORE уже существует — не перезаписываю. Удалите его вручную, если точно нужен новый ключ."
  exit 1
fi

echo "Пароль для ключа подписи Tsunayomi (от 12 символов). При вводе ничего не отображается."
while true; do
  read -r -s -p "Пароль: " TSUNAYOMI_SIGNING_PW; echo
  read -r -s -p "Ещё раз: " confirm; echo
  if [ "$TSUNAYOMI_SIGNING_PW" != "$confirm" ]; then echo "Не совпадает, попробуйте ещё раз."; continue; fi
  if [ "${#TSUNAYOMI_SIGNING_PW}" -lt 12 ]; then echo "Слишком короткий, нужно от 12 символов."; continue; fi
  break
done
unset confirm
export TSUNAYOMI_SIGNING_PW

echo "Создаю ключ ${KEYSTORE}…"
"$KEYTOOL" -genkeypair -keystore "$KEYSTORE" -storetype PKCS12 -alias "$ALIAS" \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=$(git config user.name || echo Tsunayomi)" \
  -storepass:env TSUNAYOMI_SIGNING_PW -keypass:env TSUNAYOMI_SIGNING_PW >/dev/null
"$KEYTOOL" -list -keystore "$KEYSTORE" -storepass:env TSUNAYOMI_SIGNING_PW -alias "$ALIAS" >/dev/null

echo "Загружаю секреты в ${REPO}…"
base64 -i "$KEYSTORE" | gh secret set TSUNAYOMI_KEYSTORE_BASE64 -R "$REPO"
printf '%s' "$TSUNAYOMI_SIGNING_PW" | gh secret set TSUNAYOMI_KEYSTORE_PASSWORD -R "$REPO"
printf '%s' "$TSUNAYOMI_SIGNING_PW" | gh secret set TSUNAYOMI_KEY_PASSWORD -R "$REPO"
printf '%s' "$ALIAS" | gh secret set TSUNAYOMI_KEY_ALIAS -R "$REPO"
unset TSUNAYOMI_SIGNING_PW

BACKUP="$HOME/Tsunayomi-signing-backup"
mkdir -p "$BACKUP"
cp "$KEYSTORE" "$BACKUP/"
chmod 600 "$KEYSTORE" "$BACKUP/$KEYSTORE"

echo
echo "Готово."
echo "  Ключ:            $(pwd)/$KEYSTORE (в git не попадает)"
echo "  Копия:           $BACKUP/$KEYSTORE"
echo "  Секреты GitHub:  TSUNAYOMI_KEYSTORE_BASE64, TSUNAYOMI_KEYSTORE_PASSWORD, TSUNAYOMI_KEY_ALIAS, TSUNAYOMI_KEY_PASSWORD"
echo
echo "Сохраните пароль и файл ключа в менеджере паролей: без них обновления выпустить нельзя."
