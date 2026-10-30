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
- **Успеваемость.** С тем же `uid_student` и `uid_org` из `student-groups`
  приложение загружает блок «Текущая успеваемость» (`dashboard/progress`,
  заголовок `version` обязателен — его берём из запроса страницы): баллы БРС
  по предметам семестра (`brs.labels` и `brs.data`, 0–100), посещаемость по
  месяцам (`visits.items`: план и факт) и средний балл (`avg_brs`, `rating`;
  `null` до первой сессии). Метки предметов — вида «Название_приписки
  [1 семестр] [Преподаватель] [группы]»; название до « [» и до первого «_»
  совпадает с названием в расписании. Загружается при входе, при первом
  обновлении за день и по «потянуть вниз»; пустой ответ сохранённое не стирает.
  Баллы по дням хранятся три недели — для «+4 за неделю». Изменившиеся баллы
  приходят уведомлением, если оно включено
  ([`core/Progress.kt`](../app/src/main/java/com/qteb/ranepa/sync/core/Progress.kt),
  [`ui/GradesUi.kt`](../app/src/main/java/com/qteb/ranepa/sync/ui/GradesUi.kt)).
  Зачётную книжку (`grade-book`) пока не загружаем: у первого курса до сессии
  она пустая, а её формат на живых данных не проверен.
- **Изменения.** Новое расписание сравнивается с сохранённым; изменения в
  ближайшие 7 дней приходят одним уведомлением при любом обновлении —
  фоновом, при открытии и по «потянуть вниз». В открытом приложении они
  показываются баннером на экране, а системное уведомление ложится в шторку
  тихо, без всплывающего окна
  ([`core/Changes.kt`](../app/src/main/java/com/qteb/ranepa/sync/core/Changes.kt)).
- **Новая версия.** Обновление предлагается из того же места, откуда
  установлено приложение: Google Play и RuStore — через их SDK обновлений,
  APK с GitHub — по манифесту `update.json` в корне репозитория. Общий
  `minimum` из манифеста закрывает приложение экраном «Нужно обновить»
  ([`system/Updates.kt`](../app/src/main/java/com/qteb/ranepa/sync/system/Updates.kt),
  правила — [`system/UpdateRules.kt`](../app/src/main/java/com/qteb/ranepa/sync/system/UpdateRules.kt),
  как менять манифест — [release.md](release.md)).
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
