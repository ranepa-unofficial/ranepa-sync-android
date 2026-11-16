# Расписание студента — Android

Kotlin, Jetpack Compose, Material 3, Android 8.0+. Как всё устроено — [`docs/`](docs/README.md). iOS-версия (эталон поведения) — `~/Developer/ranepa-unofficial/ranepa-timetable`.

## Правила

- `main` защищён: всё через PR, вливать `gh pr merge N --squash --admin --delete-branch` только после «ок» пользователя.
- В коммитах и PR — без Co-Authored-By и любой атрибуции ИИ.
- Репозиторий публичный: `keystore.properties`, `*.jks`, ключи RuStore и `store/**/RELEASE*.md` не коммитить; секреты пользователь задаёт сам.
- Версию выбирает пользователь, `versionCode` — всегда +1.
- Новые экраны — сначала прототип и согласование. Тексты — на русском, как в iOS.
- Меняется, что уходит в сеть, — правь обе копии `PRIVACY.md` в том же PR.
- Сеть за TLS-прокси: JDK с truststore прокси (`JAVA_TOOL_OPTIONS=-Djavax.net.ssl.trustStore=…`), проверку TLS не отключать.

## Скиллы (`.claude/skills`)

| Скилл | Когда |
|---|---|
| `android-release` | версия, тег, workflow Release, `update.json`, Google Play |
| `android-rustore-api` | RuStore API и шаг «Версия в RuStore» |
| `android-analytics` | события TelemetryDeck и проверка сигналов |
| `android-from-ios` | перенос функции из iOS |
