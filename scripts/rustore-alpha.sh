#!/usr/bin/env bash
# Альфа-версия в RuStore через RuStore API: черновик → APK → модерация.
#
#   RUSTORE_KEY_ID=… RUSTORE_PRIVATE_KEY=… scripts/rustore-alpha.sh app-release.apk "Что нового"
#
# Ключ создаётся в консоли RuStore: приложение → API RuStore → «Создать ключ».
# Параметр альфы в документации к созданию черновика не описан, поэтому после
# создания черновик проверяется: не альфа — удаляется, на модерацию ничего не уходит.
set -euo pipefail

APK=$1
WHATS_NEW=$2
PACKAGE=${RUSTORE_PACKAGE:-com.qteb.ranepa.sync}
# public-api-m.rustore.ru из документации подписан российским корнем (НУЦ Минцифры),
# которого нет на раннерах GitHub; у public-api.rustore.ru — общедоступный сертификат.
API=https://public-api.rustore.ru/public

work=$(mktemp -d)
token=
version=
committed=false

# Любая ошибка после создания черновика — удалить его, иначе следующий
# запуск упрётся в старый черновик.
cleanup() {
    rm -rf "$work"
    if [[ -n $version && $committed == false ]]; then
        curl -sS -X DELETE "$API/v1/application/$PACKAGE/version/$version" -H "Public-Token: $token" >/dev/null || true
        echo "Черновик $version удалён" >&2
    fi
}
trap cleanup EXIT

fail() { echo "::error::$*" >&2; exit 1; }

# Ответ API: {"code": "OK", "message": …, "body": …}. Не OK — ошибка с текстом RuStore.
check() {
    local code
    code=$(jq -r .code <<<"$1" 2>/dev/null || true)
    [[ $code == OK ]] || fail "$2: $1"
}

# Токен: подпись SHA512withRSA строки keyId + timestamp приватным ключом (base64 PKCS8).
echo "$RUSTORE_PRIVATE_KEY" | base64 -d > "$work/key.der"
openssl pkey -inform DER -in "$work/key.der" -out "$work/key.pem"
timestamp=$(date -u +%Y-%m-%dT%H:%M:%S.%3N+00:00)
signature=$(printf '%s%s' "$RUSTORE_KEY_ID" "$timestamp" | openssl dgst -sha512 -sign "$work/key.pem" | base64 -w0)
response=$(curl -sS "$API/auth" -H 'Content-Type: application/json' \
    -d "$(jq -n --arg k "$RUSTORE_KEY_ID" --arg t "$timestamp" --arg s "$signature" '{keyId: $k, timestamp: $t, signature: $s}')")
check "$response" "Токен"
token=$(jq -r .body.jwe <<<"$response")

response=$(curl -sS -X POST "$API/v1/application/$PACKAGE/version" \
    -H "Public-Token: $token" -H 'Content-Type: application/json' \
    -d "$(jq -n --arg w "$WHATS_NEW" '{whatsNew: $w, testingType: "ALPHA"}')")
check "$response" "Черновик"
version=$(jq -r .body <<<"$response")
[[ $version =~ ^[0-9]+$ ]] || { version=; fail "Черновик: нет номера версии в ответе: $response"; }
echo "Черновик $version"

response=$(curl -sS "$API/v2/application/$PACKAGE/version/$version" -H "Public-Token: $token")
check "$response" "Черновик $version"
testing=$(jq -r .body.testingType <<<"$response")
[[ $testing == ALPHA ]] || fail "RuStore создал черновик с testingType=$testing, а не ALPHA — на модерацию ничего не ушло."

response=$(curl -sS -X POST "$API/v1/application/$PACKAGE/version/$version/apk?isMainApk=true" \
    -H "Public-Token: $token" -F "file=@$APK")
check "$response" "Загрузка APK"

response=$(curl -sS -X POST "$API/v1/application/$PACKAGE/version/$version/commit" -H "Public-Token: $token")
check "$response" "Отправка на модерацию"
committed=true
echo "Альфа-версия $version отправлена на модерацию"
