# Политика конфиденциальности «Расписание студента» для Android

Дата вступления в силу: 23 октября 2026 г.

«Расписание студента» — неофициальное приложение для студентов РАНХиГС. Оно не связано с РАНХиГС и не поддерживается академией.

## Что приложение не делает

- Не собирает и не передаёт разработчику никаких данных. У приложения нет своих серверов.
- Не видит и не сохраняет логин и пароль от личного кабинета.
- Не использует аналитику, рекламу и трекинг, не передаёт данные третьим лицам.

## Как работает вход

Вход выполняется на странице личного кабинета РАНХиГС, открытой внутри приложения. Логин и пароль вводятся на сайте академии и отправляются только ему. После входа сайт сохраняет на устройстве свои cookie и данные сессии. Приложение использует их, чтобы загружать расписание и данные профиля из личного кабинета.

Обработку данных на сайте личного кабинета регулируют правила РАНХиГС.

## Какие данные загружаются из личного кабинета

- Расписание: пары, аудитории, преподаватели и их рабочая почта, группы, ссылки на курсы в СДО.
- Профиль: фамилия, имя и отчество, группа, программа обучения и фотография из личного кабинета. Они показываются на экране аккаунта. Фотография уменьшается и сохраняется на устройстве; заново она загружается не чаще раза в неделю.
- План обучения: предметы семестров, формы контроля и даты сессий.

## Какие данные хранятся на устройстве

- Расписание, профиль и план обучения из списка выше — чтобы приложение работало без интернета.
- Время последнего обновления и результат последнего фонового обновления.
- Настройки приложения.

Эти данные хранятся только на устройстве, во внутренней памяти приложения, и никуда не передаются. Они не попадают в резервные копии Android.

## Карта корпуса

В карточке пары показывается корпус на карте OpenStreetMap. Чтобы её нарисовать, приложение загружает фрагменты карты с серверов OpenStreetMap (tile.openstreetmap.org). В запросах передаются только координаты фрагментов карты и название приложения — без расписания и данных профиля. Обработку запросов регулирует политика OpenStreetMap: https://osmfoundation.org/wiki/Privacy_Policy

Для адресов вне кампуса на Вернадского приложение может искать точку на карте через геокодер Android. Им передаётся только адрес корпуса.

## Фоновое обновление

Приложение обновляет расписание в фоне: в будни днём — примерно раз в час, ночью и в субботу — реже, в воскресенье — раз в 12 часов. Время запуска выбирает система телефона. Запросы идут только на сайт личного кабинета РАНХиГС — так же, как при ручном обновлении. Каждое обновление продлевает сессию личного кабинета, поэтому входить заново не нужно.

## Разрешения

- **Уведомления** — для напоминаний перед парами, текущей пары в строке состояния (Live Update), сообщений об изменениях в расписании (перенос, отмена, новая пара, другая аудитория) и напоминания открыть приложение, если оно давно не обновлялось и сессия скоро истечёт. Все уведомления создаются на самом устройстве.
- **Будильники и напоминания** — чтобы напоминания приходили точно в срок.
- **Календарь** — только если включить выгрузку пар. Приложение создаёт на телефоне отдельный локальный календарь «РАНХиГС» и работает только с ним. Он не синхронизируется с облаком.

Все разрешения необязательные. Их можно отозвать в настройках телефона.

## Удаление данных

Кнопка «Выйти из ЛК» на экране аккаунта (нажмите на свою фотографию на главном экране) удаляет:

- сохранённое расписание, профиль и план, включая фотографию;
- cookie и данные сессии личного кабинета;
- запланированные напоминания и фоновое обновление;
- данные виджетов;
- календарь «РАНХиГС».

При удалении приложения с телефона удаляются все его данные.

## Магазины приложений

Google Play и RuStore могут собирать отчёты о сбоях и статистику установок по своим правилам. Приложение само ничего им не передаёт.

## Изменения и контакты

Если политика изменится, новая версия появится на этой странице.

Вопросы можно отправить в Telegram: https://t.me/qtebest

---

# Privacy Policy — "Расписание студента" (Student Timetable) for Android

Effective date: October 23, 2026

This is an unofficial app for RANEPA students. It is not affiliated with or endorsed by RANEPA.

## What the app does not do

- It does not collect any data or send it to the developer. The app has no servers of its own.
- It never sees or stores your student portal username or password.
- It uses no analytics, advertising or tracking, and shares no data with third parties.

## How sign-in works

You sign in on the RANEPA student portal page, opened inside the app. Your username and password are entered on the university's website and sent only to it. After sign-in, the website stores its own cookies and session data on your device. The app uses them to download your timetable and profile details from the portal.

RANEPA's own policies govern how the portal handles your data.

## Data downloaded from the portal

- Timetable: classes, rooms, lecturers and their work email addresses, groups, and links to courses in the university's learning system.
- Profile: your full name, group, study programme and profile photo from the portal. They are shown on the account screen. The photo is downscaled and stored on your device, and is downloaded again at most once a week.
- Study plan: subjects by semester, assessment types and exam session dates.

## Data stored on your device

- The timetable, profile and study plan listed above, so the app works offline.
- The time of the last update and the result of the last background update.
- App settings.

This data stays on your device, in the app's private storage, and is never transmitted elsewhere. It is excluded from Android backups.

## Campus map

The class card shows the building on an OpenStreetMap map. To draw it, the app downloads map tiles from OpenStreetMap servers (tile.openstreetmap.org). These requests contain only tile coordinates and the app name, never your timetable or profile. OpenStreetMap's policy applies: https://osmfoundation.org/wiki/Privacy_Policy

For addresses outside the Vernadsky campus, the app may look up the location with Android's geocoder, which receives only the building address.

## Background updates

The app updates your timetable in the background: about once an hour on weekday daytime, less often at night and on Saturdays, and every 12 hours on Sundays. Your phone's system decides exactly when to run them. Requests go only to the RANEPA student portal, the same as a manual refresh. Each update extends your portal session, so you don't need to sign in again.

## Permissions

- **Notifications**: for reminders before classes, the current class in the status bar (Live Update), alerts about timetable changes (a class moved, cancelled, added or moved to another room), and a reminder to open the app if it hasn't updated for a long time and your session is about to expire. All notifications are created on the device itself.
- **Alarms & reminders**: so that reminders arrive on time.
- **Calendar**: only if you turn on calendar export. The app creates a separate local "РАНХиГС" calendar on the phone and works only with that calendar. It is not synced to the cloud.

All permissions are optional. You can revoke them in your phone's settings.

## Deleting your data

"Выйти из ЛК" (Sign out) on the account screen (tap your photo on the main screen) deletes:

- the saved timetable, profile and study plan, including the photo;
- the portal's cookies and session data;
- scheduled reminders and background updates;
- the widgets' data;
- the "РАНХиГС" calendar.

Deleting the app removes all of its data.

## App stores

Google Play and RuStore may collect crash reports and install statistics under their own policies. The app itself sends them nothing.

## Changes and contact

If this policy changes, the updated version will be posted on this page.

Questions: https://t.me/qtebest
