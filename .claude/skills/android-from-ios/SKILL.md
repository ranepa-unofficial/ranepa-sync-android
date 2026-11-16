---
name: android-from-ios
description: Перенос функции из iOS-версии в Android-приложение — где лежит эталонный iOS-код, что переносить один в один, а что адаптировать под Android. Использовать, когда просят «сделай как в iOS», «перенеси из iOS», присылают промпт из iOS-сессии или нужно свериться с поведением iOS.
---

# Перенос из iOS

Эталон — `~/Developer/ranepa-unofficial/ranepa-timetable` (GitHub `ranepa-unofficial/ranepa-sync-ios`). Читай нужные файлы там сам, а не только присланный промпт.

| Что | Где в iOS |
|---|---|
| Запросы к ЛК | `app/Ranepa/Services/LKClient.swift` |
| Синхронизация, изменения, уведомления | `app/Ranepa/Services/Sync.swift`, `Notifications.swift` |
| Модели и разбор ответов ЛК | `app/Ranepa/Core/*.swift` |
| Проверка обновлений | `app/Ranepa/Services/Updates.swift` |
| Статистика | `app/Ranepa/Services/Analytics.swift` |
| Тексты экранов | `app/Ranepa/Views/*.swift` |
| Тесты — переносить 1:1 | `app/RanepaTests/*.swift` |
| Архитектура | `docs/architecture.md`, `docs/sync.md` |

## Один в один

- **Разбор ЛК:** эндпоинты, заголовок `version: 1.7` (без него ЛК отвечает 400), имена предметов, пустой ответ не стирает кэш.
- **Тексты** — дословно, включая склонения.
- **Ссылка поддержки:** `https://t.me/ranepa_sync_robot?start=app-<версия>-<сборка>_android-<версия ОС>_<модель>`, до 64 символов, при нехватке укорачивается только модель.
- **Уведомления:** одно на все изменения (3 строки и «и ещё N»), постоянный id; пока приложение открыто — тихо (`setSilent`/`setOnlyAlertOnce`).
- **События статистики** — те же имена и параметры.

## Адаптировать

- **Дизайн:** Material 3 с динамическими цветами, новый экран — сначала прототип на согласование.
- **Live Activity** → Live Update на Android 16, на старых — постоянное уведомление.
- **Обновления по каналу** (`getInstallSourceInfo().installingPackageName`): `com.android.vending` → Play In-App Updates, `ru.vk.store` → RuStore SDK, иначе `update.json` этого репозитория. Вести только в свой канал — подписи разные.
