# Выпуск новой версии

Порядок для каждой сборки, которая уходит пользователям через GitHub Releases,
RuStore или Google Play. Последний шаг — манифест обновлений `update.json`. Без него
APK с GitHub не узнает о новой версии, а при неверных номерах предложит обновиться
на версию, которой ещё нет.

## 1. Номер сборки и версия

В [`app/build.gradle.kts`](../app/build.gradle.kts):

- `versionCode` — номер сборки, на 1 больше предыдущего. RuStore и Google Play не
  примут сборку с тем же номером.
- `versionName` — версия, которую видят пользователи (`1.1.0`). Меняется, когда
  выходит новая версия, а не очередная сборка той же.

Изменение — отдельной веткой и PR «Сборка 1.1.0 (5)».

## 2. Тег и GitHub Releases

```bash
git tag v1.1.0 && git push origin v1.1.0
```

Тег запускает [release.yml](../.github/workflows/release.yml): тесты, подписанные
APK и AAB. В релиз попадают `ranepa-v1.1.0.apk`, `ranepa-v1.1.0.aab` и
`ranepa.apk` — то же APK под постоянным именем. На него ведёт кнопка «Обновить»
в приложении: `releases/latest/download/ranepa.apk` всегда отдаёт файл последнего
релиза. Подпись — из секретов репозитория, см. [development.md](development.md#релизы).

## 3. RuStore и Google Play

AAB из релиза загружается в консоль магазина вручную. О новой версии в этих
каналах приложение узнаёт через SDK магазина (RuStore In-app updates, Google Play
In-App Updates), когда сборка прошла модерацию и доступна. `update.json` для них
не нужен, кроме `minimum`.

## 4. Манифест обновлений `update.json`

Файл в корне репозитория. Приложение читает его **из ветки `main`**: с GitHub,
а при ошибке — через jsDelivr. Скачивает при открытии, но не чаще раза в 3 часа.

```json
{
  "github":  { "latest": "1.1.0", "latestCode": 5, "notes": "Баллы БРС и посещаемость",
               "url": "https://github.com/ranepa-unofficial/ranepa-sync-android/releases/latest/download/ranepa.apk" },
  "minimum": { "version": "1.0.0", "code": 0 }
}
```

Канал приложение определяет по тому, кто его установил: Google Play
(`com.android.vending`), RuStore (`ru.vk.store`), всё остальное — APK с GitHub.
Обновление предлагается только из того же канала: подписи сборок в магазинах и
на GitHub разные. Номера сравниваются по `versionCode`.

| Поле | Что значит | Что увидит пользователь |
|---|---|---|
| `github.latest` + `latestCode` | последняя версия, **уже лежащая** в GitHub Releases | сборка ниже — лист «Вышла версия», точка на аватаре и строка в аккаунте (только APK с GitHub) |
| `github.notes` | одна-три строки о главном, по желанию | блок «Что нового» на листе; в магазинах — если номер сборки совпал |
| `github.url` | куда ведёт «Обновить» для APK | браузер скачивает файл, Android предлагает установить |
| `minimum.version` + `code` | самая старая сборка, которая ещё работает, **во всех каналах** | сборка ниже — экран «Нужно обновить» поверх всего, закрыть нельзя |

Лист «Вышла версия» показывается при открытии приложения. После «Позже» он
вернётся через 3 дня, а о более новой версии — сразу.

### Когда менять

- **Релиз появился в GitHub Releases** (workflow прошёл, `ranepa.apk` скачивается)
  → `github.latest`, `latestCode`, `notes`.
- **Старые версии сломались** (ЛК поменял API, старая логика портит данные) →
  `minimum`, но только после того, как исправленная версия доступна **во всех
  каналах**: в GitHub Releases, RuStore и Google Play. Иначе людей заблокирует без выхода:
  магазин не предложит сборку, которая ещё на модерации.

Не поднимай `latestCode` раньше времени: человек нажмёт «Обновить» и скачает свою
же версию.

### Как выложить

Правка `update.json` — PR в `main`, отдельно или вместе с PR номера сборки, но
**влить его только когда релиз уже опубликован** (см. «Когда менять»).

После влития GitHub отдаёт новый файл через несколько минут. jsDelivr может
кэшировать до 12 часов. Он нужен только как запасной путь, но кэш можно сбросить:

```bash
curl -s https://purge.jsdelivr.net/gh/ranepa-unofficial/ranepa-sync-android@main/update.json
```

Проверить, что отдаётся:

```bash
curl -s https://raw.githubusercontent.com/ranepa-unofficial/ranepa-sync-android/main/update.json
```

### Проверка экранов без публикации

В отладочной сборке канал и манифест задаются в «Аккаунт → Отладка» или
параметрами запуска (трёхчасовой интервал при этом не действует):

```bash
adb shell am start -n com.qteb.ranepa.sync.debug/com.qteb.ranepa.sync.MainActivity \
  --es updateChannel github \
  --es updateManifest '{"github":{"latest":"9.9.0","latestCode":999,"notes":"Что нового","url":"https://example.org"},"minimum":{"version":"1.0.0","code":0}}'
```

`updateChannel` — `github`, `rustore` или `play`; `"minimum"` с `code` выше текущей
сборки — экран «Нужно обновить». Пустая строка (`--es updateManifest ''`) сбрасывает
подмену.

## Чек-лист

- [ ] `versionCode` (+ `versionName`) подняты, PR сборки влит
- [ ] Тег запушен, в релизе есть `ranepa.apk`
- [ ] AAB загружен в RuStore и Google Play, модерация пройдена
- [ ] `update.json`: `github.latest` / `latestCode` / `notes` — после публикации релиза
- [ ] `minimum` поднят, только если старые версии действительно не работают
