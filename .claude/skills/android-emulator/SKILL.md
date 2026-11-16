---
name: android-emulator
description: Проверка Android-приложения в эмуляторе и скриншот-тестами — запуск эмулятора без окна, установка debug-сборки, демо-режим, раздел «Отладка», нажатия и чтение экрана через adb, скриншоты, раскадровка анимаций, тёмная тема и крупный шрифт, эталоны Roborazzi. Использовать, когда просят «проверь на эмуляторе», «сними скриншоты», «проверь анимацию / уведомление / Live Update», обновить эталоны скриншотов, или эмулятор не запускается.
---

# Проверка в эмуляторе

Сначала прочитай разделы «Демо-режим и скриншоты» и «Тестовая сборка» в [`docs/development.md`](../../../docs/development.md).

## Запуск

```bash
$ANDROID_HOME/emulator/emulator -list-avds
$ANDROID_HOME/emulator/emulator -avd Pixel_8 -no-window -no-audio -no-snapshot-save &   # в фоне
adb wait-for-device && until [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = 1 ]; do sleep 3; done
./gradlew installDebug
adb shell am start -W -n com.qteb.ranepa.sync.debug/com.qteb.ranepa.sync.MainActivity
```

- Debug-сборка (`.debug`) ставится рядом с релизной. Если в ней уже выполнен вход, не выходи из аккаунта без спроса.
- Live Update есть только на Android 16 (образ API 36). Для планшетной раскладки нужен отдельный AVD (`avdmanager create avd -d pixel_tablet …`), спроси перед скачиванием образа.
- «Not enough disk space»: освободи место (DerivedData Xcode, `build/` iOS) с согласия пользователя.
- В конце — `adb emu kill`.

## Управление и чтение экрана

- Что на экране: `adb shell uiautomator dump /sdcard/ui.xml && adb exec-out cat /sdcard/ui.xml`. Compose отдаёт `text` и `content-desc`, а `bounds` дают координаты для нажатий. Это дешевле скриншота.
- Нажатие: `adb shell input tap X Y`. Долгое нажатие (вход в демо по логотипу): `adb shell input swipe X Y X Y 1500`.
- Права: `adb shell pm grant com.qteb.ranepa.sync.debug android.permission.POST_NOTIFICATIONS`.
- Тема и шрифт: `adb shell cmd uimode night yes|no`, `adb shell settings put system font_scale 1.3` (потом верни 1.0).

## Раздел «Отладка» (экран аккаунта, только debug)

«Ускоренный день» (4 пары по 4 минуты: напоминания, Live Update, виджеты за 20 минут), «Напоминание о паре», «Уведомление об изменении», «Обновить Live Update», «Фоновое обновление», «Сымитировать изменение», канал и тестовый манифест обновлений, «Часы 13.10, 11:05» для одинаковых скриншотов. Это заменяет сценарии, которые в iOS-симуляторе не проверить.

## Скриншоты и анимации

- Снимок: `adb exec-out screencap -p > shot.png`. Чистая строка состояния — команды demo mode из `docs/development.md`.
- Анимации — только по видео: `adb shell screenrecord --time-limit 10 /sdcard/rec.mp4`, затем `adb pull`, раскадровка как в iOS: `ffmpeg -i rec.mp4 -vf "fps=12,scale=270:-1,tile=8x6" -frames:v 1 sheet.png`. Смотри её через Read.

## Скриншот-тесты (Roborazzi)

- `ScreenshotTest.kt`: новые экраны в светлой и тёмной теме, эталоны лежат в `app/src/test/screenshots`.
- `./gradlew verifyRoborazziDebug` сравнивает с эталонами, `./gradlew recordRoborazziDebug` перезаписывает их. Перезаписывай только после того, как сам посмотрел новые PNG, и коммить их вместе с изменением экрана.
- Robolectric — только `@Config(sdk = [35])`: SDK 36 не запускается на JDK 21.
- CI запускает `testDebugUnitTest`, но не `verify`, поэтому сверку перед PR делай сам.

## Отчёт

Что проверено, на каком AVD и API, в какой теме, и что **не** проверено. Магазины (In-App Updates Play и RuStore) в эмуляторе с debug-сборкой не проверяются.
