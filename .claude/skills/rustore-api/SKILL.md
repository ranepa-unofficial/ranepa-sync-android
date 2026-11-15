---
name: rustore-api
description: Работа с RuStore Public API — токен по ключу, черновик версии, загрузка APK/AAB, отправка на модерацию, разбор ошибок. Использовать при правке scripts/rustore-release.sh, при ошибке шага «Версия в RuStore», или когда нужно что-то сделать в RuStore из скрипта или CI.
---

# RuStore Public API

Рабочая реализация — [`scripts/rustore-release.sh`](../../../scripts/rustore-release.sh). Сначала прочитай её; ниже — что выяснено опытом и в документации не сказано.

## Хост

`https://public-api.rustore.ru/public`. Хост из документации `public-api-m.rustore.ru` подписан российским корнем (НУЦ Минцифры): на раннерах GitHub и большинстве машин curl падает с ошибкой 60. Не «чинить» отключением проверки сертификата.

## Токен

- Ключ: консоль RuStore → приложение → **API RuStore** → **Создать ключ**. Секреты репозитория `RUSTORE_KEY_ID` и `RUSTORE_PRIVATE_KEY` (base64 PKCS8, DER).
- Подпись: SHA512withRSA строки `keyId + timestamp`, timestamp вида `2026-11-14T10:00:00.123+00:00`. DER → PEM через `openssl pkey -inform DER`.
- `POST /auth` `{keyId, timestamp, signature}` → `body.jwe`, живёт 900 с. Дальше заголовок `Public-Token: <jwe>`.
- Если токен выдан и черновик создан — ключи верные, перезадавать секреты не нужно.

## Версия

| Шаг | Запрос |
|---|---|
| Черновик | `POST /v1/application/{pkg}/version` `{whatsNew}` → `body` = id версии |
| Подробности | `GET /v2/application/{pkg}/version/{id}` |
| APK | `POST /v1/application/{pkg}/version/{id}/apk?isMainApk=true`, multipart, поле `file` |
| На модерацию | `POST /v1/application/{pkg}/version/{id}/commit` |
| Удалить черновик | `DELETE /v1/application/{pkg}/version/{id}` |

Ответ всегда `{"code": "OK"|"ERROR", "message", "body"}` — проверяй `code`, а не HTTP-статус.

## Подводные камни

- **Альфу/бету создать нельзя.** `testingType: "ALPHA"` при создании черновика принимается молча, а версия получается обычной (`testingType=null`). Если нужна альфа — только вручную в консоли («Тестирование» → «Альфа-тестирование»).
- **`publishType: "INSTANT"` → «Illegal INSTANT publishing type».** Не передавать `publishType` — тогда действует настройка по умолчанию из консоли. Если там ручная публикация, после модерации пользователь нажимает «Опубликовать» сам.
- **Брошенный черновик блокирует следующий.** При любой ошибке после создания — `DELETE` (в скрипте это `trap cleanup EXIT`, пока не прошёл commit).
- Пока предыдущая версия на модерации, новый черновик может не создаться — ждать решения модератора.
- `versionCode` должен быть больше всех, что уже загружались в RuStore.

## Проверять осторожно

Каждый успешный прогон до `commit` реально отправляет версию на модерацию. Эксперименты с параметрами — так, чтобы проверка (`GET v2`) шла до загрузки APK и при несовпадении черновик удалялся.
