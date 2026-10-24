# Шаблон аддона для Better Combat (Forge 1.20.1)

Отдельная Gradle-сборка, не часть Kinetrix. Это стартовая база для своего аддона с новыми
механиками боя: в ней по одному рабочему примеру на каждый способ встроиться в Better Combat.
Подробный разбор устройства Better Combat, его аддонов из сборки Otherworld и идеи механик лежат в
[`../docs/better-combat-addon.md`](../docs/better-combat-addon.md).

## Что внутри

| Файл | Что показывает |
|---|---|
| `combat/BcAttacks` | На сервере отличить удар Better Combat от любого другого урона и узнать, какой это удар серии (анимация, номер в комбо, рука, категория оружия). Сюда ставятся «стойкость», оглушение, добивания. |
| `combat/Guard` | Новое действие на своей клавише (Z): короткое окно парирования оружием. Сервер проверяет и решает, всем игрокам показывается анимация. Со щитом не работает: щиты остаются за ванилью и Shield Expansion. |
| `combat/Heavy` + `mixin/PlayerAttackHelperMixin` | Подмена атаки, которую выполняет Better Combat: пока зажата X, идёт тяжёлый удар (другая анимация, больше урона, медленнее, чуть дальше). |
| `data/WeaponExtras`, `data/WeaponExtrasLoader` | Свои данные на оружие в своей папке `data/<ns>/weapon_extras/*.json` (по id предмета или по категории Better Combat), перезагрузка по /reload, отправка клиентам. |
| `client/ClientHooks` | Клиентский API Better Combat: события `ATTACK_START`/`ATTACK_HIT`, `AttackRangeExtensions`, `cancelUpswing`. |
| `client/MoveLayer` | Свой слой playerAnimator (приоритет 2500, выше атак Better Combat) и проигрывание анимации из `assets/<ns>/player_animation`. С Kinetrix смешивается сам. |
| `net/*` | Свой сетевой канал: запросы клиента, рассылка анимации, синхронизация данных. |

## Сборка

Из корня репозитория (используется общий Gradle wrapper):

```
./gradlew -p addon-template build
```

Готовый jar: `addon-template/build/libs/bcaddon-1.20.1-0.1.0.jar`. Запуск в разработке:
`./gradlew -p addon-template runClient` (Better Combat, playerAnimator и Cloth Config подтягиваются
сами; чтобы проверить вместе с Kinetrix, положите jar Kinetrix в `addon-template/run/mods`).

CI (`.github/workflows/addon-template.yml`) собирает шаблон и запускает выделенный сервер с Better
Combat: проверяет, что миксин встал и данные загрузились.

## Как сделать из шаблона свой мод

1. Скопируйте папку `addon-template` в новый репозиторий, добавьте туда `gradlew`, `gradlew.bat` и
   папку `gradle/` из корня этого репозитория.
2. В `gradle.properties` поменяйте `mod_id`, `mod_name`, `mod_group_id`.
3. Переименуйте пакет `com.example.bcaddon` и папки `assets/bcaddon`, `data/bcaddon`.
4. Файл `bcaddon.mixins.json` переименуйте в `<ваш mod_id>.mixins.json` (`build.gradle` ищет его
   по `mod_id`) и поправьте внутри `package` и `refmap`.
5. Поменяйте `MODID` в `BcAddon.java`.

Клавиши по умолчанию: Z (защита), X (тяжёлый удар, удерживать); меняются в настройках управления.
