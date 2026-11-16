---
name: android-analytics
description: Анонимная статистика TelemetryDeck в Android-приложении — как устроен объект Analytics, схема событий (общая с iOS), как добавить событие, что обновить в политике и формах RuStore/Google Play, как проверить, что сигналы доходят. Использовать, когда просят добавить или поменять событие статистики, спрашивают про TelemetryDeck, DAU, удержание, или сигналы не видны в панели.
---

# Статистика (TelemetryDeck)

## Где код

- [`system/Analytics.kt`](../../../app/src/main/java/com/qteb/ranepa/sync/system/Analytics.kt) — единственная точка входа: `foreground`, `stop`, `send`, `screen`, `syncFailed`.
- App ID — `telemetrydeck.appId` в `gradle.properties` → `BuildConfig.TELEMETRYDECK_APP_ID`. Пусто — статистика выключена. Не секрет.
- Тесты — `app/src/test/.../AnalyticsTest.kt` (причины ошибок, параметры настроек, раз в сутки).

## Правила, на которых держится подсчёт

- Автозапуск SDK удалён в манифесте (`TelemetryDeckInitProvider`, `tools:node="remove"`), старт — только `Analytics.foreground` из `MainActivity.onStart`: фон, виджеты и будильники не считаются пользователями. При обновлении SDK проверь, что провайдер в объединённом манифесте всё ещё называется так же.
- Выключена в демо-режиме; debug-сборки — `testMode`.
- Переключателя для пользователя нет, пока не попросят.
- После изменений — один раз `assembleRelease`: R8 не должен ломать SDK.

## Схема событий — одинаковая с iOS

| Событие | Параметры | Где |
|---|---|---|
| `Login.completed` | — | `completeLogin`, не повторный вход |
| `Sync.failed` | `reason` network/session/server/other, `trigger` open/manual | обновление на глазах у пользователя |
| `Screen.opened` | `screen` | смена вкладки и экрана в `ui/App.kt` |
| `Support.opened` | — | кнопка поддержки в аккаунте |
| `Settings.daily` | включённые функции строками | раз в календарный день |

Новое событие или параметр — то же имя, что в iOS (`ios-analytics` в iOS-репозитории). Никогда не отправлять ФИО, логин, группу, расписание, свободный текст.

## Если меняется, что отправляется

1. `PRIVACY.md` **и** `app/src/main/assets/PRIVACY.md` — одинаковые.
2. Формы о данных в RuStore и Google Play: действия в приложении, анонимно, не передаётся третьим лицам, не для рекламы. Фразы вроде «аналитики нет» в карточке магазина становятся неправдой.
3. Выпуск — скилл `android-release`; сигналы пойдут с первой сборки, где есть App ID.

## Проверить, что сигналы доходят

- Панель → Explore → Recent Signals: release-сборки — без **Test Mode**, debug — с ним.
- На бесплатном тарифе отчёты запаздывают, Recent Signals — обычно за минуты.
