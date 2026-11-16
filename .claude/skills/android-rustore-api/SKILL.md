---
name: android-rustore-api
description: RuStore Public API — хост, токен по ключу, черновик версии, загрузка APK, отправка на модерацию и подводные камни, которых нет в документации. Использовать при правке scripts/rustore-release.sh, при ошибке шага «Версия в RuStore» или когда нужно что-то сделать в RuStore из скрипта или CI.
---

# RuStore Public API

Рабочая реализация — [`scripts/rustore-release.sh`](../../../scripts/rustore-release.sh), прочитай её первой. Ниже — выясненное опытом.

## Хост и токен

- Хост — `https://public-api.rustore.ru/public`. Хост из документации `public-api-m.rustore.ru` подписан корнем НУЦ Минцифры, и на раннерах GitHub curl падает с ошибкой 60. Проверку сертификата не отключать.
- Подпись: SHA512withRSA строки `keyId + timestamp` (`2026-11-14T10:00:00.123+00:00`), ключ из `RUSTORE_PRIVATE_KEY` (base64 PKCS8 DER → PEM через `openssl pkey -inform DER`).
- `POST /auth` `{keyId, timestamp, signature}` → `body.jwe` на 900 с, дальше заголовок `Public-Token`.
- Токен выдан и черновик создан — значит ключи верные, перезадавать секреты не нужно.

## Версия

| Шаг | Запрос |
|---|---|
| Черновик | `POST /v1/application/{pkg}/version` `{whatsNew}` → `body` = id |
| Подробности | `GET /v2/application/{pkg}/version/{id}` |
| APK | `POST /v1/application/{pkg}/version/{id}/apk?isMainApk=true`, multipart `file` |
| На модерацию | `POST /v1/application/{pkg}/version/{id}/commit` |
| Удалить черновик | `DELETE /v1/application/{pkg}/version/{id}` |

Ответ всегда `{"code": "OK"|"ERROR", "message", "body"}` — проверяй `code`, а не HTTP-статус.

## Подводные камни

- **Альфу и бету через API не создать:** `testingType: "ALPHA"` принимается молча, а версия получается обычной. Альфа — только вручную в консоли.
- **`publishType: "INSTANT"`** → «Illegal INSTANT publishing type». Не передавать — действует настройка консоли.
- **Брошенный черновик блокирует следующий** — при ошибке после создания `DELETE` (в скрипте `trap cleanup EXIT` до `commit`).
- Пока предыдущая версия на модерации, новый черновик может не создаться.
- `versionCode` должен быть больше всех, что уже загружались.

## Эксперименты

Каждый прогон до `commit` реально отправляет версию на модерацию. Проверяй параметры через `GET v2` до загрузки APK и удаляй черновик при несовпадении.
