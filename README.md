# Расписание студента

Неофициальное приложение для Android с расписанием РАНХиГС.
Расписание берётся из личного кабинета студента (my.ranepa.ru) и открывается
сразу, без браузера и без входа в ЛК каждый раз. Версия для iPhone —
[ranepa-sync-ios](https://github.com/ranepa-unofficial/ranepa-sync-ios).

> Приложение не связано с РАНХиГС и не поддерживается академией.

<p align="center">
  <a href="https://www.rustore.ru/catalog/app/com.qteb.ranepa.sync"><img src="docs/badges/rustore-badge-ru.svg" height="48" alt="Доступно в RuStore"></a>
  &nbsp;
  <a href="https://github.com/ranepa-unofficial/ranepa-sync-android/releases/latest/download/ranepa.apk"><img src="docs/badges/apk-badge-ru.svg" height="48" alt="Скачать APK с GitHub"></a>
</p>

<p align="center">
  <img src="docs/images/schedule.jpg" width="24%" alt="Расписание">
  <img src="docs/images/lesson.jpg" width="24%" alt="Карточка пары">
  <img src="docs/images/session.jpg" width="24%" alt="Сессия">
  <img src="docs/images/subject.jpg" width="24%" alt="Экран предмета">
</p>

## Установка

Удобнее всего — из [RuStore](https://www.rustore.ru/catalog/app/com.qteb.ranepa.sync):
обновления будут приходить сами. Без RuStore можно
[скачать APK](https://github.com/ranepa-unofficial/ranepa-sync-android/releases/latest/download/ranepa.apk)
последней версии и открыть его на телефоне; прошлые версии — в
[релизах](https://github.com/ranepa-unofficial/ranepa-sync-android/releases).
Нужен Android 8.0 или новее.

## Что умеет

- Пары дня с текущей и следующей парой, на планшете — карточка пары рядом с лентой.
- Расписание до конца семестра, работает без интернета.
- Уведомления о переносах, отменах и смене аудитории, напоминания о паре.
- «Предметы» и «Сессия» из плана обучения: экзамены, зачёты, преподаватели.
- Баллы БРС и посещаемость, уведомление о новых баллах.
- Виджеты, Live Update, плитка в быстрых настройках, календарь «РАНХиГС».
- Корпус на карте и выбор приложения карт: Яндекс Карты, 2ГИС, Google Maps.

## Приватность

Своих серверов и рекламы нет. Логин и пароль вводятся на настоящей
странице ЛК, приложение их не видит. Расписание, профиль и баллы хранятся только
на телефоне. Разработчику уходит только анонимная статистика через
[TelemetryDeck](https://telemetrydeck.com/privacy/): сколько пользователей и какие
экраны открывают, без логина, ФИО и группы.
Подробнее — в [политике конфиденциальности](PRIVACY.md) и в разделе
[«Вход и токены»](docs/auth.md).

## Документация

- [Вход и токены](docs/auth.md)
- [Как обновляется расписание](docs/sync.md)
- [Как это устроено](docs/architecture.md)
- [Сборка и разработка](docs/development.md)
- [Выпуск новой версии](docs/release.md)

## Сборка

Нужны JDK 21 и Android SDK с платформой 37.

```bash
./gradlew assembleDebug
```

Тесты, демо-режим, релизы и своя сборка — в [docs/development.md](docs/development.md).

## Состояние

Версия 1.0 — первая для Android (Kotlin, Jetpack Compose, Material 3).
Приложение опубликовано в RuStore, релизы с готовым APK — в
[GitHub Releases](https://github.com/ranepa-unofficial/ranepa-sync-android/releases).

## Лицензия

[MIT](LICENSE)
