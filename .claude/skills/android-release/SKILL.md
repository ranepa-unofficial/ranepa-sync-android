---
name: android-release
description: Выпуск Android-версии — PR с versionCode, аннотированный тег, workflow Release (подписанные APK/AAB, GitHub Release, автоматическая отправка в RuStore), update.json, AAB для Google Play и что делать, если workflow упал. Использовать, когда просят «выпусти версию», «сделай релиз», «собери APK/AAB», «залей в RuStore», «обнови update.json», «подними версию», или упал workflow Release.
---

# Выпуск Android-версии

Сначала прочитай [`docs/release.md`](../../../docs/release.md): поля и сроки `update.json`, теги, ключ RuStore. Здесь — то, на чём спотыкались.

## Порядок

1. **PR «Сборка X.Y.Z (N)»** — `versionCode` +1 (магазины не примут уже загруженный номер), `versionName` по выбору пользователя.
2. **Аннотированный тег на влитом `main`** — его сообщение становится «Что нового» в RuStore; без `-m` там будет «Версия X.Y.Z». Тег публикует **публичный** релиз — просьба выпустить версию и есть согласие, иначе спроси.
   ```bash
   git fetch && git tag -a v1.2.0 origin/main -m "1–3 предложения для пользователей" && git push origin v1.2.0
   ```
3. **Workflow Release** — следи в фоне: `gh run list --workflow release.yml -L 1`, `gh run watch <id> --exit-status`. Успех RuStore — в логе «Версия N отправлена на модерацию».
4. **`update.json`** — PR открывает сам workflow («update.json: X.Y.Z (N)»). Проверь `notes`, влей после того как `ranepa.apk` скачивается из релиза, затем сбрось jsDelivr (команды в `docs/release.md`). Если PR не открылся (предупреждение в логе), открой его из ветки `update-json-X.Y.Z-N`.
5. **Google Play** — AAB из релиза пользователь загружает сам. В консоли магазинов за него не входить.
6. **Отчёт:** ссылка на релиз, APK, AAB, статус RuStore; если менялись собираемые данные — напомни про формы магазинов (скилл `android-analytics`).

Подписанные сборки делает только CI: локально release не собирать, `keystore.properties` и `*.jks` не читать и не печатать. Локально можно `./gradlew testDebugUnitTest assembleDebug`.

## Если упал workflow

- `gh run view <id> --log-failed`. Ошибка RuStore: `gh run view <id> --log | grep "Версия в RuStore" | grep -E "error|Черновик|модерац"`.
- **Временная** (сеть, «предыдущая версия на модерации») — `gh run rerun <id>`: релиз на GitHub повторно не создаётся.
- **В коде или скрипте** — исправить PR-ом в `main` и **перевесить тег**: перезапуск собирает коммит тега, а не `main`.
  ```bash
  msg=$(git tag -l --format='%(contents)' v1.2.0)
  git tag -fa v1.2.0 origin/main -m "$msg" && git push -f origin v1.2.0
  ```
  Если изменился код приложения, сначала удали релиз (`gh release delete v1.2.0`) — только с «ок» пользователя.

## Секреты

Пользователь задаёт их сам через `gh secret set`; в чат не просить. Не коммитить `keystore.properties`, `*.jks`, `store/**/RELEASE*.md`.
