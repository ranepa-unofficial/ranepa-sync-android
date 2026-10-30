# Сборка и разработка

Нужны JDK 21 и Android SDK с платформой 37. Путь к SDK — в `local.properties`
(`sdk.dir=…`) или в переменной `ANDROID_HOME`.

```bash
./gradlew assembleDebug
```

Тесты:

```bash
./gradlew testDebugUnitTest
```

Отладочная сборка ставится рядом с релизной (`com.qteb.ranepa.sync.debug`).
На экране аккаунта в ней есть раздел «Отладка»: ускоренный день, тестовые
уведомления, запуск фонового обновления, имитация изменения в расписании и
перевод часов. В релизную сборку он не попадает.

## Демо-режим и скриншоты

Долгое нажатие на логотип на стартовом экране открывает демо: выдуманная
студентка с расписанием и планом обучения, без входа и без интернета
([`sync/Demo.kt`](../app/src/main/java/com/qteb/ranepa/sync/sync/Demo.kt)). Работают напоминания, виджеты, календарь и
«потянуть вниз»: каждое обновление меняет одну аудиторию на завтра, чтобы было
видно уведомление об изменении. «Выйти из демо» на экране аккаунта удаляет всё,
включая календарь, и возвращает к стартовому экрану. Демо есть и в релизной
сборке — для проверки модераторами магазинов.

Сегодняшние пары сдвигаются так, чтобы одна из них шла прямо сейчас. Для
одинаковых скриншотов в «Отладке» есть «Часы 13.10, 11:05»: время приложения
переводится на этот момент и дальше идёт как обычно. Чистая строка состояния
в эмуляторе:

```bash
adb shell settings put global sysui_demo_allowed 1
```

```bash
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1105
```

```bash
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false
```

## Тестовая сборка на свой телефон

Включи на телефоне «Для разработчиков» → «Отладка по USB», подключи кабелем:

```bash
./gradlew installDebug
```

В «Отладке» — «ускоренный день»: демо, где сегодня 4 пары по 4 минуты, первая —
через 3 минуты, напоминания за 1 минуту. За 20 минут видно напоминания,
Live Update и виджеты.

## Релизы

Тег `vX.Y.Z` запускает [release.yml](../.github/workflows/release.yml):
подписанные APK и AAB появляются в
[GitHub Releases](https://github.com/ranepa-unofficial/ranepa-sync-android/releases).
Перед тегом подними `versionCode` (и `versionName`, если версия меняется для
пользователей) в [`app/build.gradle.kts`](../app/build.gradle.kts).
Весь порядок выпуска, включая магазины и манифест обновлений `update.json`, —
в [release.md](release.md).

```bash
git tag v1.0.1 && git push origin v1.0.1
```

Подпись берётся из секретов репозитория `RELEASE_KEYSTORE_BASE64`,
`RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`. Локально те
же значения кладутся в `keystore.properties` в корне проекта (он в
`.gitignore`), тогда подписанная сборка — `./gradlew assembleRelease`.

## Своя сборка

Чтобы выпускать приложение под своим именем, замени идентификаторы и подпись:

| Что | Где |
|---|---|
| `applicationId` и `namespace` `com.qteb.ranepa.sync` | [`app/build.gradle.kts`](../app/build.gradle.kts) |
| Ключ подписи | Свой `keystore.properties` и секреты репозитория |
| Ссылки на репозиторий и политику | `Links` в [`ui/AccountScreen.kt`](../app/src/main/java/com/qteb/ranepa/sync/ui/AccountScreen.kt) |
