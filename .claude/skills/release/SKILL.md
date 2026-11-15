---
name: release
description: Выпуск новой версии Android-приложения «Расписание студента» — номер сборки, тег, GitHub Releases, автоматическая отправка в RuStore, update.json, Google Play. Использовать, когда просят «выпусти версию», «сделай релиз», «залей в RuStore», «обнови update.json», «подними версию», или когда упал workflow Release.
---

# Выпуск версии

Порядок и поля `update.json` описаны в [`docs/release.md`](../../../docs/release.md) — прочитай его. Здесь — то, на чём спотыкались.

## Порядок

1. **PR «Сборка X.Y.Z (N)»**: в `app/build.gradle.kts` `versionCode` +1 (RuStore и Play не примут номер, который уже загружали), `versionName`. Влить.
2. **Аннотированный тег** на `main`: его сообщение — «Что нового» в RuStore. Без `-m` RuStore получит «Версия X.Y.Z».
   ```bash
   git tag -a v1.2.0 -m "Что нового — 1–3 предложения для пользователей"
   git push origin v1.2.0
   ```
3. **Workflow Release** (`gh run watch <id>`): тесты → подписанные APK/AAB → релиз на GitHub с `ranepa.apk` → шаг «Версия в RuStore» (`scripts/rustore-release.sh`). Успех — в логе «Версия N отправлена на модерацию».
4. **`update.json`** — отдельный PR, **только после** того как релиз с `ranepa.apk` опубликован: `latest`, `latestCode`, при желании `notes`. Потом сбросить jsDelivr и проверить ссылку:
   ```bash
   curl -s https://purge.jsdelivr.net/gh/ranepa-unofficial/ranepa-sync-android@main/update.json
   curl -sL -o /dev/null -w "%{http_code}\n" https://github.com/ranepa-unofficial/ranepa-sync-android/releases/latest/download/ranepa.apk
   ```
5. **Google Play** — AAB из релиза пользователь загружает в консоль сам. В консоли магазинов за пользователя не входить.

`minimum` в `update.json` поднимать, только если старые версии сломаны и исправление уже доступно во всех трёх каналах.

## Теги

- `vX.Y.Z` — обычный релиз: GitHub (станет «последним») + RuStore на модерацию.
- `vX.Y.Z-alpha.N` — только пре-релиз на GitHub без `ranepa.apk`. В RuStore не уходит: альфу через API создать нельзя (см. скилл `rustore-api`), её загружают в консоли вручную из APK пре-релиза.

## Если упал шаг RuStore

- Релиз на GitHub уже создан, повторно он не создаётся — шаг пропускается, если релиз есть.
- Ошибка RuStore — в логе: `gh run view <id> --log | grep "Версия в RuStore" | grep -E "error|Черновик|модерац"`.
- Временная ошибка (сеть, «предыдущая версия на модерации») — `gh run rerun <id>`.
- Ошибка в скрипте — исправить PR-ом в `main`, затем **перевесить тег** на новый коммит: перезапуск job собирает коммит тега, а не `main`.
  ```bash
  msg=$(git tag -l --format='%(contents)' v1.2.0)
  git tag -fa v1.2.0 -m "$msg" && git push -f origin v1.2.0
  ```
  Код приложения тот же, поэтому APK в GitHub-релизе остаётся верным.

## Правила репозитория

- `main` защищён: всё через PR, вливать `gh pr merge N --squash --admin --delete-branch` — после «ок» пользователя.
- В коммитах и PR — без строк про Claude/AI и Co-Authored-By.
- Не коммитить `keystore.properties`, `*.jks`, `store/**/RELEASE*.md`. Секреты пользователь задаёт сам через `gh secret set`; в чат их не просить.
- Если меняется, что приложение отправляет в сеть (SDK магазина, аналитика), — поправить `PRIVACY.md` и `app/src/main/assets/PRIVACY.md` (одинаковые) и раздел о данных в карточке магазина: фразы вроде «аналитики нет» становятся неправдой.
