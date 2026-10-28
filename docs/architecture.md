# Как это устроено

Код лежит в [`app/src/main/java/com/qteb/ranepa/sync`](../app/src/main/java/com/qteb/ranepa/sync):
`core` — модель и правила без Android, `lk` — работа с ЛК, `sync` — загрузка,
фон и демо, `system` — уведомления, Live Update, календарь, карты, `widget` —
виджеты, `ui` — экраны.

- **Данные.** Сайт закрыт JS-проверкой браузера, поэтому все запросы идут
  изнутри `WebView`. Приложение перехватывает запрос расписания, который делает
  сама страница (`n-api/schedule` с `uid_org` и всеми группами студента), и по
  его образцу запрашивает нужные недели. В запросе меняются только даты,
  остальная строка запроса остаётся как у страницы
  ([`lk/LkClient.kt`](../app/src/main/java/com/qteb/ranepa/sync/lk/LkClient.kt)).
- **План обучения.** Тем же способом приложение берёт `uid_student` из
  `manual/student-groups` и загружает план (`education/plan`), даты сессий
  (`education/plan/sessions`) и справочники семестров и форм контроля. Из плана
  строятся «Предметы» ([`core/StudyPlan.kt`](../app/src/main/java/com/qteb/ranepa/sync/core/StudyPlan.kt),
  [`core/Subjects.kt`](../app/src/main/java/com/qteb/ranepa/sync/core/Subjects.kt)) и «Сессия»:
  экзамены и зачёты из плана сопоставляются с парами расписания, откуда берутся
  дата и аудитория ([`core/Exams.kt`](../app/src/main/java/com/qteb/ranepa/sync/core/Exams.kt)).
- **Изменения.** Новое расписание сравнивается с сохранённым; изменения в
  ближайшие 7 дней приходят одним уведомлением
  ([`core/Changes.kt`](../app/src/main/java/com/qteb/ranepa/sync/core/Changes.kt)).
- **Напоминания.** Будильники `AlarmManager`: за выбранное время до пары и
  вечером накануне экзамена или зачёта. Точные — если пользователь разрешил
  «Будильники и напоминания», иначе Android может сдвинуть их на несколько минут
  ([`system/Notifications.kt`](../app/src/main/java/com/qteb/ranepa/sync/system/Notifications.kt)).
- **Календарь.** Отдельный локальный календарь «РАНХиГС» через `CalendarContract`;
  события пересоздаются при каждом обновлении
  ([`system/CalendarExport.kt`](../app/src/main/java/com/qteb/ranepa/sync/system/CalendarExport.kt)).
- **Карта.** Корпус в карточке пары — на карте OpenStreetMap (osmdroid),
  контуры корпусов на Вернадского сверены со схемой кампуса
  ([`system/Campus.kt`](../app/src/main/java/com/qteb/ranepa/sync/system/Campus.kt)). Нажатие
  открывает выбранное приложение карт или спрашивает, каким открыть.
- **Виджеты.** Jetpack Glance, читают сохранённое расписание. Пока идёт пара или
  до неё меньше трёх часов, перерисовываются раз в минуту, иначе — на ближайшей
  границе пары ([`widget/Widgets.kt`](../app/src/main/java/com/qteb/ranepa/sync/widget/Widgets.kt),
  что показывать — [`widget/WidgetModel.kt`](../app/src/main/java/com/qteb/ranepa/sync/widget/WidgetModel.kt)).
- **Live Update.** Постоянное уведомление с идущей или ближайшей парой,
  обратным отсчётом и полоской прогресса; на Android 16 — чипом в строке
  состояния. Видно, пока идёт пара или следующая начнётся в ближайшие 2 часа;
  появившись, остаётся до конца последней пары дня (`LiveUpdatePlan` в
  [`system/LiveUpdates.kt`](../app/src/main/java/com/qteb/ranepa/sync/system/LiveUpdates.kt),
  тесты — [`LiveUpdatePlanTest.kt`](../app/src/test/java/com/qteb/ranepa/sync/LiveUpdatePlanTest.kt)).
  Без открытого приложения оно появляется будильником в момент напоминания о
  первой паре дня — это и есть напоминание, обычное о той же паре не ставится.
  Будильники на границах пар обновляют его и убирают после последней.
- **Плитка «Расписание».** В быстрых настройках показывает следующую пару,
  нажатие открывает сегодняшний день
  ([`system/TodayTile.kt`](../app/src/main/java/com/qteb/ranepa/sync/system/TodayTile.kt)).
- **Интерфейс.** Jetpack Compose на Material 3: цвета Material You из обоев
  (на Android 12+), навигация снизу на телефоне и сбоку на планшете, на широком
  экране карточка пары — рядом с лентой.
- **Статистика.** Анонимная, через TelemetryDeck: SDK запускается из
  `MainActivity.onStart`, только с App ID в сборке (`telemetrydeck.appId` в
  `gradle.properties`) и не в демо. Сессии и новые
  установки SDK отмечает сам; свои события — `Login.completed`, `Sync.failed`,
  `Screen.opened`, `Support.opened` и раз в день `Settings.daily`, схема общая
  с iOS ([`system/Analytics.kt`](../app/src/main/java/com/qteb/ranepa/sync/system/Analytics.kt)).
