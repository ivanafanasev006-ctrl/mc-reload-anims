# Аддон к Better Combat: справочник и база

Это база для своего аддона к Better Combat с новыми механиками боя в сборке **Otherworld**
(Forge 1.20.1), где Kinetrix даёт анимации тела. Здесь описано:

1. как Better Combat 1.9.0 устроен внутри (данные, анимации, ход удара от клиента к серверу, API, подводные камни);
2. какие его аддоны и соседи стоят в Otherworld и как каждый из них встраивается;
3. все способы встроиться в Better Combat, от самого безопасного до миксинов;
4. как сделать Kinetrix зависимостью и какое API ему для этого добавить;
5. идеи механик с планом реализации, проверенные на конфликты со сборкой.

Рядом лежит **рабочий шаблон аддона**: [`addon-template/`](../addon-template/README.md). Это
отдельная Gradle-сборка (Forge 1.20.1 + Better Combat 1.9.0 + playerAnimator), её собирает CI,
прогоняет её автотесты GameTest и запускает с ней выделенный сервер и клиент. В нём по одному примеру на каждый способ из раздела 3:
парирование оружием на своей клавише, тяжёлый удар через подмену атаки, свои данные на оружие,
свой слой анимаций, клиентские события Better Combat.

Откуда всё взято: исходники Better Combat (ветка `1.20.1`, версия 1.9.0), playerAnimator
(ветка `1.20`), Iron's Spells; список модов Otherworld v8 HF2 (435 модов) и содержимое jar-файлов
модов сборки (mods.toml, миксины, данные, ключевые классы декомпилированы). Всё это прогонялось
через `.github/workflows/research.yml`, потому что из контейнера разработки нет доступа к
CurseForge и Modrinth.

---

## 1. Better Combat изнутри

### 1.1. Что это за jar

- Проект на Architectury (общий код + Fabric + Forge), исходники в маппингах **yarn**
  (`PlayerEntity`, `MinecraftClient`...). В Forge-jar имена Minecraft переведены в SRG, при
  подключении через `fg.deobf` в разработке видны официальные имена (`Player`, `Minecraft`).
- Зависимости на Forge: **playerAnimator** и **Cloth Config**. Fabric API ему не нужен: в Forge-jar
  лежат маленькие заглушки `net.fabricmc.*` (события, сеть), через которые общий код работает на Forge.
- Подключение к своему моду (так сделано в шаблоне):

  ```gradle
  repositories { maven { url = 'https://api.modrinth.com/maven'; content { includeGroup 'maven.modrinth' } } }
  dependencies {
      implementation fg.deobf("maven.modrinth:better-combat:1.9.0+1.20.1-forge")
      implementation fg.deobf("dev.kosmx.player-anim:player-animation-lib-forge:1.0.2-rc1+1.20")
      runtimeOnly    fg.deobf("me.shedaniel.cloth:cloth-config-forge:11.1.136")
  }
  ```

### 1.2. Данные оружия: `data/<ns>/weapon_attributes/<item>.json`

Имя файла совпадает с id предмета (`data/minecraft/weapon_attributes/iron_sword.json` для
`minecraft:iron_sword`). Файл, имя которого не совпадает ни с одним предметом, это «абстрактный»
пресет, от него наследуются (`bettercombat:sword`, `bettercombat:claymore`...).

```json
{
  "parent": "bettercombat:sword",
  "attributes": {
    "attack_range": 2.5,
    "category": "sword",
    "two_handed": false,
    "attacks": [
      {
        "conditions": ["NOT_DUAL_WIELDING"],
        "hitbox": "HORIZONTAL_PLANE",
        "damage_multiplier": 1.0,
        "angle": 120,
        "upswing": 0.5,
        "animation": "bettercombat:one_handed_slash_horizontal_right",
        "swing_sound": { "id": "bettercombat:sword_slash", "volume": 1, "pitch": 1, "randomness": 0.1 }
      }
    ]
  }
}
```

| Поле | Смысл |
|---|---|
| `parent` | Наследование. `attacks` можно укоротить (`[{}, {}]` берёт первые две атаки родителя) или удлинить; поля атак переопределяются по одному. |
| `attack_range` | Дальность всех атак оружия, блоки. |
| `category` | Любая строка: «семейство» оружия (`sword`, `katana`, `spear`...). Нужна условию `DUAL_WIELDING_SAME_CATEGORY`, и аддонам удобно матчить по ней своё (так делает шаблон). |
| `two_handed` | Двуручное: пока оно в руке, слот второй руки «пустой» для игры (см. 1.7). |
| `pose`, `off_hand_pose` | Анимация стойки, пока оружие в руке (обычно только для двуручных). |
| `attacks[]` | Серия ударов. Каждый следующий клик берёт следующую атаку, после последней по кругу. |
| `conditions` | Все должны выполняться, иначе атака пропускается: `NOT_DUAL_WIELDING`, `DUAL_WIELDING_ANY`, `DUAL_WIELDING_SAME`, `DUAL_WIELDING_SAME_CATEGORY`, `NO_OFFHAND_ITEM`, `OFF_HAND_SHIELD`, `MAIN_HAND_ONLY`, `OFF_HAND_ONLY`, `MOUNTED`, `NOT_MOUNTED`. |
| `hitbox` | `FORWARD_BOX`, `VERTICAL_PLANE`, `HORIZONTAL_PLANE`, масштабируется `attack_range`. |
| `angle` | Сектор вокруг взгляда, градусы (0 = без проверки угла; больше 180 = удар вокруг себя). |
| `upswing` | Доля времени перезарядки до момента удара. Перезарядка в тиках `20 / attack_speed`; реальный замах ещё умножается на `upswing_multiplier` из конфига сервера (по умолчанию 0.5). |
| `damage_multiplier` | Множитель урона этой атаки серии (временный модификатор `MULTIPLY_BASE` к урону на время удара). |
| `animation` | Id анимации из `attack_animations` (см. 1.3). |
| `swing_sound` | Звук взмаха. `impact_sound` есть в формате, но не работает. |

Ещё два источника атрибутов:
- **NBT предмета**: строка JSON в теге `weapon_attributes` (`WeaponAttributesHelper.readFromNBT/writeToNBT`) — так можно дать атрибуты одному конкретному предмету.
- **`fallback_compatibility_enabled`** (конфиг сервера): предметам без данных атрибуты подбираются по шаблону имени id (`claymore`, `katana`...). Поэтому у модов вроде Fantasy Weapons (в jar нет ни одного `weapon_attributes`) всё равно есть удары Better Combat.

Атрибуты читаются **один раз при старте сервера** (`SERVER_STARTED`), отправляются каждому
входящему игроку пакетом `weapon_registry` (со сжатием) и лежат в `WeaponRegistry`. `/reload` их
не перечитывает.

Пресеты самого Better Combat (32): `anchor, axe, battlestaff, bow_two_handed_heavy/light, claw,
claymore, coral_blade, crossbow_two_handed_heavy/light, cutlass, dagger, double_axe, fist, glaive,
halberd, hammer, heavy_axe, katana, lance, mace, pickaxe, rapier, scythe, sickle, soul_knife, spear,
staff, sword, trident, twin_blade, wand`.

### 1.3. Анимации

- **Атаки и стойки**: `assets/<ns>/attack_animations/<name>.json`, id = `<ns>:<name>` (по имени файла).
  Формат Emotecraft (`"emote": {"beginTick", "endTick", "stopTick", "moves": [...]}`), делаются в Blender
  (исходные `.blend` лежат в папке `animations/` репозитория Better Combat). Грузятся при перезагрузке ресурсов клиента (F3+T тоже).
- **Свои анимации аддона** удобнее класть в `assets/<ns>/player_animation/*.json`: их грузит сам
  playerAnimator, id = `<ns>:<"name" внутри файла>`, формат тот же (или GeckoLib). Так сделан
  `guard.json` в шаблоне.
- **Слои Better Combat в стеке playerAnimator** (у каждого игрока свой стек, больший приоритет выше):

  | Приоритет | Слой |
  |---|---|
  | 1 | стойка второй руки: предмет |
  | 2 | стойка второй руки: тело |
  | 3 | стойка основной руки: предмет |
  | 4 | стойка основной руки: тело |
  | 2000 | анимация атаки (скорость подгоняется под замах и перезарядку, зеркалится для левой руки) |

- Своя анимация атаки показывается другим игрокам пакетом `attack_animation`: клиент отправляет
  его серверу, сервер пересылает всем, кто видит игрока (и обратно самому игроку для ReplayMod).
- Первое лицо: Better Combat показывает руки в первом лице через playerAnimator
  (`firstPersonAnimations`, по умолчанию AUTO: выключается, если стоит First-person Model или Real Camera).

### 1.4. Ход одного удара

**Клиент** (`MinecraftClientInject`, миксин в `Minecraft`):

1. ЛКМ (`doAttack`) или удержание ЛКМ (`handleBlockBreaking`, если включено «hold to attack»): если у
   предмета в главной руке есть атрибуты и прицел не на блоке, который можно копать, ванильная атака
   отменяется и начинается **замах** (`startUpswing`).
2. Замах не начнётся, если: игрок «занят руками» (`isHandsBusy`, например лежит в нокауте),
   замах уже идёт, идёт перезарядка добычи, игрок что-то использует, или перезарядка атаки ещё не
   дошла до `1 − upswing`.
3. Начало замаха: проигрывается анимация атаки, серверу уходит `attack_animation`, вызывается
   событие **`ATTACK_START`**. Замах длится `перезарядка × upswing × upswing_multiplier` тиков.
4. Каждый тик: если предмет в руке сменился, замах отменяется; если зажата клавиша финта (feint),
   удар в конце замаха отменяется.
5. Конец замаха (`performAttack`): **цели ищутся на клиенте** (`TargetFinder`: повёрнутый по взгляду
   параллелепипед из `hitbox` × дальность, затем сектор `angle`, затем проверка, что между игроком
   и целью нет стены), дальность меняют `AttackRangeExtensions`. Серверу уходит
   **`c2s_request_attack`** `(номер в серии, присел ли, слот, id целей)`, клиент локально бьёт цели,
   вызывается **`ATTACK_HIT`**, счётчик серии +1.
6. Серия сбрасывается, если не бить дольше `перезарядка × combo_reset_rate` (3) или сменить оружие.

**Сервер** (`ServerNetwork`):

1. По номеру в серии выбирает ту же атаку: `PlayerAttackHelper.getCurrentAttack(player, combo)`.
   Это вызывается ещё **в сетевом потоке**, до перехода в главный.
2. В главном потоке: ставит игроку счётчик серии, вешает временные модификаторы урона (множитель
   атаки, двуручный бой, «переработанный» размашистый удар по нескольким целям), играет звук взмаха.
3. Для каждой цели: ванильный пакет атаки → `player.attack(target)`. То есть срабатывают все обычные
   события Forge (`AttackEntityEvent`, `LivingAttackEvent`, `LivingHurtEvent`...), зачарования,
   аффиксы Apotheosis и т.д.
4. Снимает модификаторы, ставит счётчик серии **−1**.

**Доверие**: сервер **не проверяет дальность** и угол (проверка закомментирована), только тип
цели (нельзя бить предметы, стрелы, себя). Цели решает клиент. Для своих механик, где это важно
(PvP), проверку надо делать самому.

### 1.5. API для аддонов

| Что | Где | Для чего |
|---|---|---|
| `BetterCombatClientEvents.ATTACK_START` | клиент | начало замаха `(LocalPlayer, AttackHand)` |
| `BetterCombatClientEvents.ATTACK_HIT` | клиент | удар `(LocalPlayer, AttackHand, List<Entity> цели, Entity под прицелом)` |
| `AttackRangeExtensions.register(ctx -> Modifier)` | клиент | менять дальность (ADD, затем MULTIPLY) |
| `(MinecraftClient_BetterCombat) Minecraft.getInstance()` | клиент | `getComboCount()`, `hasTargetsInReach()`, `getCursorTarget()`, `getUpswingTicks()`, `getSwingProgress()`, `isWeaponSwingInProgress()`, `cancelUpswing()` |
| `((EntityPlayer_BetterCombat) player).getCurrentAttack()` | оба | текущая атака или `null`; на сервере не `null` только во время обработки удара |
| `AttackHand` (record) | оба | `attack()`, `combo()` (`current`, `total`), `isOffHand()`, `attributes()`, `itemStack()`, `upswingRate()` |
| `WeaponRegistry.getAttributes(ItemStack)` | оба | атрибуты предмета (с учётом NBT) или `null` = не оружие Better Combat |
| `WeaponAttributesHelper` | оба | `override`, `validate`, `readFromNBT`, `writeToNBT`, `decode`, `encode` |
| `PlayerAttackHelper` (`logic`, не `api`) | оба | `getCurrentAttack(player, combo)`, `isDualWielding`, `isTwoHandedWielding`, `getAttackCooldownTicksCapped` |
| `PlayerAttackProperties` (`logic`) | оба | `getComboCount()`/`setComboCount()` |

**Серверных событий у Better Combat нет.** Серверная логика аддона ставится на обычные события
Forge и спрашивает `getCurrentAttack()`, идёт ли сейчас удар Better Combat и какой (шаблон,
`combat/BcAttacks`).

### 1.6. Конфиги

Сервер (`config/bettercombat/server.json5`, отправляется клиентам): `upswing_multiplier` (0.5),
`allow_fast_attacks`, `attack_interval_cap` (2), `allow_vanilla_sweeping` (false),
`allow_reworked_sweeping` и его параметры, `allow_attacking_thru_walls` (false),
`movement_speed_while_attacking` (0.5), `knockback_reduced_for_fast_attacks`, `combo_reset_rate` (3),
`target_search_range_multiplier` (2), `dual_wielding_attack_speed_multiplier` (1.2),
`dual_wielding_main/off_hand_damage_multiplier`, `player_relations` и отношения к мирным,
враждебным и прочим, `fallback_compatibility_enabled`, `weapon_registry_compression`.

Клиент: `isHoldToAttackEnabled`, `isMiningWithWeaponsEnabled`, `isSwingThruGrassEnabled`,
`isAttackInsteadOfMineWhenEnemiesCloseEnabled`, подсветка прицела, руки в первом лице,
`firstPersonAnimations`, громкость взмаха, отладка OBB.

### 1.7. Подводные камни

- **Неизвестные ключи в `weapon_attributes` теряются**: их разбирает GSON в фиксированные классы.
  Свои поля аддона храните в своей папке (шаблон: `data/<ns>/weapon_extras`) и синхронизируйте сами.
- **`/reload` не перечитывает атрибуты** Better Combat (только при старте сервера).
- **Счётчик серии на сервере у нового игрока равен 0**, а не −1: до первого удара Better Combat
  `getCurrentAttack()` на сервере вернёт «первую атаку», хотя удара нет. Ставьте −1 при входе,
  возрождении и смене мира (шаблон: `BcAttacks.resetCombo` на `EntityJoinLevelEvent`).
- **`getCurrentAttack` вызывается в сетевом потоке** сервера: всё, что ваш миксин в него читает,
  должно быть потокобезопасным.
- **`OFF_HAND_SHIELD` всегда истинно**: в коде `offhandStack != null || ...` вместо `&&`.
- **`impact_sound` не работает** (так и написано в исходниках).
- **Дальность из `AttackRangeExtensions`** применяется уже после сбора кандидатов, а кандидаты
  берутся в радиусе `дальность × target_search_range_multiplier` (2): большие прибавки не находят целей.
- **Двуручное оружие** делает вторую руку пустой через `getEquippedStack(OFFHAND)` → `EMPTY`.
  Моды, которые смотрят на вторую руку (щиты, факелы, Shield Expansion), видят там пустоту.
- **Двойное владение** даёт временный +20% к скорости атаки (`dual_wielding_attack_speed_multiplier`),
  удары чередуют руки, для удара второй рукой атрибуты рук временно меняются местами.
- **Во время удара скорость ходьбы снижается** (`movement_speed_while_attacking`).
- **Ванильный размах (sweeping) отключён** для оружия с атрибутами; вместо него «переработанный».
- **Верховая езда**: `isHandsBusy` (у Better Combat это `isRiding`) запрещает замах. Бить своего
  скакуна нельзя (`allow_attacking_mount`), кроме враждебных «транспортов» из списка.

---

## 2. Better Combat в сборке Otherworld

Otherworld v8 HF2: 435 модов, Forge 1.20.1. Всё, что касается боя и анимаций игрока
(id проекта и файла на CurseForge в скобках):

| Мод (версия) | Что делает | Как встроен в Better Combat |
|---|---|---|
| **Better Combat** 1.9.0 (639842 / 7287334) | удары, серии, двойное владение | — |
| **Malfu Combat Datapack** V3.1 (1367308 / 7132775) | переопределяет 18 пресетов `bettercombat:*` длинными сериями (у катаны больше десятка записей атак), добавляет 5 своих (`er_sword`, `greataxe`, `greathammer`, `greatsword`, `sai`), атрибуты для Cataclysm (12), Simply Swords (~30), Simply More (~280), настройки частиц для bc_particle | только данные |
| **Malfu Combat Animation** (ресурспак) V3.1.1 (1259338 / 7174240) | 102 анимации атак `malfu_combat_animation:*`, анимация кувырка для Combat Roll, настройки bc_particle | только ресурсы |
| **Better Combat Particle** 0.0.4 (1372768 / 7369563, Malfu) | следы-дуги при ударе (slash45/90/180/270/360, stab), цвет по оружию и зачарованиям, тряска камеры | клиент: `ATTACK_HIT` → частицы по имени анимации из `bc_particle/particle_settings.json`; свой пакет на сервер, сервер пересылает **всем игрокам мира** |
| **Shield Expansion** 1.3.7 (583318 / 7742039) | уровни щитов, парирование, «выносливость» щита | клиент: `ATTACK_START` → снимает блок щитом (атака опускает щит) |
| **Apothic Combat** 1.1.0 (986982 / 5236936) | оружие Better Combat получает аффиксы Apotheosis как оружие ближнего боя | миксин в категории лута Apotheosis |
| **Otherworld Origins** 2.0.1 (1127963 / 8071766) | происхождения D&D, превращения (shapeshift) | миксины в Better Combat: `PlayerAttackHelper.getCurrentAttack` (HEAD, своя атака для превращённых), `isDualWielding` (нет двойного владения в облике зверя), `WeaponRegistry.getAttributes` (клиент: атрибуты облика), `TargetFinder.findAttackTargetResult` (прибавка дальности Just Leveling) |
| **JustLevelingFork** 1.2.1 (1073400 / 6189891) | прокачка навыков | свой миксин в `TargetFinder` (дальность) |
| **Iron's Spells 'n Spellbooks** 3.15.4 (855414 / 7691158) | магия; 19 файлов `weapon_attributes` (категории `mace`, `rapier`, `spellbreaker`, `truthseeker`...) | данные; анимации каста на своём слое (42) с поворотом рук к взгляду ×0.65 |
| **T.O Magic 'n Extras** 6.3.0 (1046916 / 7424522) | аддон Iron's; 84 файла `weapon_attributes` (spear, lance, battlestaff, halberd, scythe...) | данные |
| **Marium's Soulslike Weaponry** 1.3.1 (632066 / 7321885) | легендарное оружие и боссы; 56 файлов `weapon_attributes`, свои способности оружия на клавише | данные + свои пакеты |
| **Mine Cells** 1.9.2 (618790 / 6077063) | 10 файлов `weapon_attributes`, миксин поддержки playerAnimator в первом лице | данные |
| **Fantasy Weapons** 0.4 (1090102 / 6761136) | оружие без `weapon_attributes` | атрибуты из Malfu-датапака или по имени (fallback) |
| **Gauntlets** 1.0.0 (1011857 / 5653770) | кастеты, свой удар второй рукой (свой пакет) | — (Fabric-мод через Sinytra Connector) |
| **playerAnimator** 1.0.2-rc1+1.20 (658587 / 4587214) | библиотека анимаций игрока | — |
| **Vintage Animations** 1.3.0 (1134994 / 5923960) | анимации добычи (chop, dig, pick, till) на playerAnimator | — (Kinetrix глушит его по умолчанию: `compat.priorityOverMods`) |
| **YDM's Weapon Master** 4.2.3 (608235 / 5022313) | оружие из хотбара висит на теле | миксины в модель и слой предмета в руке |
| **Shoulder Surfing Reloaded** 4.21.0 (243190 / 7635998) + интеграция с Iron's | камера из-за плеча | — |
| **Fresh Moves** v3.1 (ресурспак EMF) | анимации игрока через EMF | Kinetrix по умолчанию оставляет игрока себе (`compat.playerOverEmf`) |
| **Additional Attributes** 1.3.6 (986624 / 6782138) | дополнительные атрибуты, связка с Iron's | — |

Combat Roll в сборке нет (в ресурспаке Malfu только его анимация); отдельного мода на
выносливость, уклонение или стойкость тоже нет. Это свободная ниша для аддона.

### 2.1. Как устроены соседи, которых аддон касается напрямую

**Shield Expansion** (декомпилировано):
- Данные щита: `data/<ns>/shields/<щит>.json` с `cooldownTicks` (30), `speedFactor` (0.65),
  `parryDamage` (0.1), `parryTicks` (5), `stamina` (2), `blastResistance`, `flatDamage` (1)
  (в скобках значения `shieldexp:default`).
- Начал блок → окно парирования `parryTicks` тиков (×2 в «мягком» режиме); скорость ходьбы при блоке задаёт `speedFactor`.
  Окно, флаг блока, потраченная «выносливость» хранятся в `SynchedEntityData` игрока (миксин в `Player`).
- Удар моба или игрока в окно → парирование: звук, ответный урон `урон × parryDamage + flatDamage`,
  отбрасывание. Вне окна: урон по прочности и «выносливость»; кончилась → щит на перезарядку.
  Снаряды в окно отражаются (скорость × −1).
- Блок мгновенный (миксин в `isBlocking`), и не работает во время взмаха.
- С Better Combat: начало замаха снимает блок.

Вывод для аддона: щиты уже заняты, своё парирование делайте **оружием** (без щита в использовании),
как в шаблоне, или явно встраивайтесь в Shield Expansion.

**bc_particle**: всё на клиенте по событию `ATTACK_HIT`, настройки по **имени анимации**
(`particle_type`, смещения, `roll_set`, `local_yaw`), цвета по предмету (`weapon_settings.json`) и
зачарованиям. Хороший образец «визуальной реакции на удар»: клиент рисует сам и пересылает
пакет, чтобы увидели остальные.

**Otherworld Origins**: подменяет атаку и атрибуты превращённым игрокам. Его хук на `HEAD`
`getCurrentAttack` с ранним возвратом: если ваш миксин стоит на `RETURN` того же метода, для
превращённых он просто не сработает, и это правильно (зверь бьёт по-звериному). Не используйте
`@Overwrite` и не отменяйте метод на `HEAD`, иначе сломаете превращения.

---

## 3. Как аддон встраивается в Better Combat

От самого безопасного к самому глубокому.

1. **Только данные (датапак/ресурспак).** Новые пресеты и атрибуты оружия, новые серии, новые
   анимации атак. Кода нет, конфликтов почти нет; но в Otherworld пресеты уже переопределены
   Malfu-датапаком, поэтому свои правки должны грузиться после него (порядок датапаков).
2. **Клиентские события и API** (`ATTACK_START`, `ATTACK_HIT`, `AttackRangeExtensions`,
   `MinecraftClient_BetterCombat`). Для визуала, звука, подсказок, отмены замаха. Решения,
   меняющие игру, здесь не принимать: клиент отправит удар в любом случае.
3. **Серверные события Forge + `getCurrentAttack()`.** Любая логика по удару: урон, эффекты,
   стойкость, добивания. Понятно, какая атака серии и каким оружием. Шаблон: `combat/BcAttacks`.
4. **Свои данные на оружие** рядом с `weapon_attributes`, по id предмета или по категории
   Better Combat; загрузка через `AddReloadListenerEvent`, отправка клиентам на
   `OnDatapackSyncEvent`. Шаблон: `data/WeaponExtras`.
5. **Свои действия и пакеты** (новая клавиша → запрос на сервер → сервер проверяет → рассылка
   анимации). Шаблон: `combat/Guard`, `net/*`.
6. **Миксины в Better Combat** (`remap = false`, только `@Inject`/`@ModifyReturnValue`/`@ModifyVariable`):
   - `PlayerAttackHelper.getCurrentAttack` — **какая атака будет**: подменяет её сразу везде
     (анимация, время замаха, поиск целей на клиенте, множитель урона на сервере). Шаблон: тяжёлый удар.
   - `WeaponRegistry.getAttributes(ItemStack)` — атрибуты целиком (стойки, двуручность, дальность).
   - `TargetFinder.findAttackTargetResult` — поиск целей (клиент).
   - Логика клика (`MinecraftClientInject`) сама миксин, обычным миксином её не достать. Чтобы
     запретить замах (например, кончилась выносливость), ставьте свой `@Inject` на `HEAD`
     `Minecraft.startAttack` и `continueAttack` с приоритетом меньше, чем у Better Combat (у него
     по умолчанию 1000): такой обработчик сработает раньше, и его можно отменить. Другие варианты:
     `cancelUpswing()` сразу после `ATTACK_START` или MixinSquared (он в сборке есть, им
     пользуется Otherworld Origins), который умеет цеплять методы чужих миксинов.
   - Чужие миксины в те же методы в Otherworld: Otherworld Origins (`getCurrentAttack` HEAD,
     `isDualWielding`, `getAttributes`, `TargetFinder`), JustLevelingFork (`TargetFinder`).
7. **Анимации на своём слое playerAnimator.** Слой регистрируется фабрикой
   (`PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(id, приоритет, p -> new ModifierLayer<>())`),
   анимация ставится `replaceAnimationWithFade(...)`. Шаблон: `client/MoveLayer`, приоритет 2500.

**Стек слоёв в Otherworld** (снизу вверх; верхний побеждает на тех частях тела, которые двигает):

| Приоритет | Слой |
|---|---|
| −100000 | Kinetrix: процедурное тело (ходьба, бег, стойки, безделье...) |
| 1–4 | Better Combat: стойки рук |
| 42 | Iron's Spells: касты |
| 2000 | Better Combat: атаки |
| 2500 | шаблон аддона (`bcaddon:moves`) |
| 100000 | Kinetrix: гармонизатор (сглаживает вход и выход чужих слоёв, вписывает их в суставы, ноги бегут) |

Выбор приоритета: выше 2000, если ваше движение должно перебить удар (парирование, оглушение);
между 4 и 2000, если удар Better Combat должен играть поверх вашего (стойка, бег с оружием).

Для левой руки (удар второй рукой, левша) Better Combat зеркалит анимацию `MirrorModifier`; свой
слой может сделать так же (`layer.addModifierBefore(new MirrorModifier())` и `setEnabled`).

### 3.1. Как проверять аддон

- **Серверная логика — GameTest** (`./gradlew -p addon-template runGameTestServer`): сервер с Better
  Combat и аддоном прогоняет тесты и выходит. В шаблоне так проверены парирование (спереди один раз,
  сзади нет), подмена атаки тяжёлым ударом и сброс счётчика серии у вошедшего игрока. Удобно для
  механик с числами: окна в тиках, множители, стойкость.
- **Запуск в разработке** (`runClient`): Better Combat, playerAnimator и Cloth Config подтягиваются
  сами; Kinetrix — положить его jar в `addon-template/run/mods`.
- **В самой сборке Otherworld**: jar аддона в папку `mods` сборки, в `config/bcaddon-common.toml`
  включить `logHits` — в логе будет каждый удар Better Combat с анимацией, номером в серии и уроном.
- **CI** шаблона: сборка, GameTests, выделенный сервер (миксин встал, данные загрузились), клиент до
  главного меню (хуки Better Combat, слой анимаций, JSON анимации прочитан).

---

## 4. Kinetrix как зависимость

### 4.1. Что Kinetrix уже делает с Better Combat

- `compat/BetterCombatCompat`: через рефлексию спрашивает `WeaponRegistry.getAttributes`. Если у
  оружия есть атрибуты Better Combat, Kinetrix **не запускает свой удар** под ударом Better Combat;
  двуручное по правилам Better Combat держится двумя руками всё время (нет движений безделья с
  оружием, стойки по ЛКМ и выхватывания).
- Гармонизатор (`rig/HarmonizerLayer`, приоритет 100000) видит любой чужой слой: где поза отличается от
  его базовой, часть тела отдаётся чужой анимации с плавным переходом, ноги при движении остаются
  бегущими. **Анимации аддона на своём слое смешиваются с Kinetrix сами**, без кода.
- `AnimPriority`: во время собственных движений всем телом (эмоция, кувырок, приземление) Kinetrix
  глушит чужие слои (`ownMovesFirst`); моды из списка `compat.priorityOverMods` глушатся всегда.
  Значит, анимация аддона не будет видна во время кувырка Kinetrix, и неймспейс аддона не надо
  вписывать в `priorityOverMods`.
- Следы оружия Kinetrix на ударах Better Combat не рисуются, если стоит bc_particle (чтобы не было двух следов).

### 4.2. Мягкая или жёсткая зависимость

- **Сейчас** аддону от Kinetrix ничего вызывать не нужно: в шаблоне он опциональный
  (`mandatory=false`, только клиент). Аддон работает и без Kinetrix (анимации playerAnimator
  на ванильном теле).
- **Жёсткой** (`mandatory=true`) её стоит делать, когда аддон начнёт вызывать API Kinetrix
  (процедурный уворот, оглушение, сгибание суставов в своих движениях).

### 4.3. Черновик API Kinetrix для аддона

Пакет `dev.mcreload.anim.api`, всё только на клиенте, кроме синхронизации движений.

```java
public final class KinetrixApi {
    /** Kinetrix сейчас ведёт тело этого игрока (не отключён, не в спектаторе, не EMF). */
    public static boolean isAnimating(Player player);

    /** Процедурное движение Kinetrix по id: "roll", "dodge_back", "dodge_left", "stagger",
     *  "knockdown", "kneel"... Длительность и направление в параметрах; видно всем (свой пакет Kinetrix). */
    public static void playMove(Player player, ResourceLocation move, MoveParams params);

    /** Не трогать эти части тела N тиков (аддон анимирует их сам и не хочет смешивания). */
    public static void holdParts(Player player, int ticks, BodyPart... parts);

    /** Подправить итоговую позу каждый кадр: наклон корпуса в замахе тяжёлого удара,
     *  опущенные плечи при оглушении. */
    public static void onPoseFrame(PoseFrameListener listener);

    /** Только чтение состояния: скорость, на земле ли, присел, вид оружия, стойка, идёт ли
     *  своё движение всем телом. */
    public static MotionView motion(Player player);
}
```

Что для этого нужно в самом Kinetrix:
- вынести сущности `MotionState`, `PoseFrame`, модули в стабильные интерфейсы `api`;
- собрать уже существующие движения (кувырок, приземление, оглушение-«пошатывание») в реестр движений по id;
- выложить Kinetrix в maven (GitHub Packages, JitPack или Modrinth maven), чтобы аддон компилировался
  против него (`compileOnly fg.deobf("...:kinetrix:...")`);
- держать API обратно совместимым между версиями (аддон проверяет версию в mods.toml).

---

## 5. Идеи механик и как их сделать

Для каждой: где ставить хук, что синхронизировать, какая анимация, с чем в Otherworld может конфликтовать.

**Парирование оружием** (есть в шаблоне). Клавиша → сервер открывает окно на `guard_ticks` из
данных → удар моба или игрока спереди в окно отменяется (`LivingAttackEvent`), атакующий отброшен и
замедлен. Развитие: «идеальное» парирование в первые 2 тика даёт **ответный удар** (следующая
атака серии с бонусом через подмену в `getCurrentAttack`); парирование снарядов; звук и частицы по
типу оружия. Конфликт: Shield Expansion (щиты) — пропускать, если игрок блокирует щитом.

**Тяжёлый (заряжаемый) удар** (есть в шаблоне). Пока зажата клавиша, `getCurrentAttack` отдаёт
тяжёлую версию атаки из данных (другая анимация, урон, замах, хитбокс); замедление скорости атаки
атрибутом. Развитие: уровни заряда по времени удержания (клиент отправляет уровень, сервер
сверяет с временем), отбрасывание и пробитие блока щитом на полном заряде, наклон корпуса через
`onPoseFrame` Kinetrix во время замаха.

**Выносливость.** Своя шкала на игроке (capability или `SynchedEntityData` через миксин в `Player`,
как у Shield Expansion), HUD-полоска. Расход: на сервере при ударе Better Combat (`LivingAttackEvent` +
`getCurrentAttack()` один раз на запрос, а не на каждую цель), на парирование, уворот, бег.
Запрет замаха при нуле: свой `@Inject` на `HEAD` `Minecraft.startAttack`/`continueAttack` раньше
Better Combat. Восстановление по тикам, медленнее при блоке. Конфликт: у Shield Expansion своя
«выносливость» щита (не та же шкала) — их можно связать, но не дублировать.

**Уворот / кувырок.** Combat Roll в сборке нет. Клавиша + направление → сервер проверяет
выносливость и перезарядку, даёт импульс и кадры неуязвимости (отмена `LivingAttackEvent` N тиков
для ближнего боя и снарядов). Анимация: процедурный кувырок Kinetrix через `playMove("roll")` (его
движение уже есть) или своя на слое. Конфликт: Iron's (каст прерывать), Otherworld Origins (у некоторых
происхождений свои рывки `dash`).

**Стойкость и оглушение (posture/poise).** У каждого существа шкала стойкости (capability). Каждый
удар Better Combat добавляет урон стойкости из данных (по категории или анимации: тяжёлые и
финальные удары серии больше, `hand.combo().current() == total()`), парирование — много. Полная
шкала → оглушение: моб на N тиков без ИИ (снять цели, замедление, своя анимация «пошатывания»; для
игроков — `playMove("stagger")` Kinetrix и запрет атак). Шкала восстанавливается, если не получать
урон. Боссам множитель меньше (теги сущностей).

**Добивание.** По оглушённой цели под прицелом следующий удар подменяется на добивание
(`getCurrentAttack` → особая атака и анимация, огромный множитель урона), обе стороны видят одну и ту
же атаку, потому что флаг «цель оглушена» синхронизируется с клиентом.

**Навыки оружия.** Клавиша → навык по категории оружия из данных (выпад копьём, круговой удар
двуручником, рывок катаной): анимация на своём слое, серверный поиск целей своим OBB (можно
перенести логику `TargetFinder` на сервер), перезарядка на предмете (`ItemCooldowns`). Конфликт:
у Soulslike Weaponry и T.O Magic свои способности на тех же клавишах — разные клавиши и
проверка, что предмет не из этих модов.

**Финал серии.** Без новых клавиш: последний удар серии получает бонус (оглушение, отбрасывание,
частицы), а пауза ровно в ритм (окно 3–5 тиков после удара) продлевает серию — «ритмичная серия».

### 5.1. Чек-лист совместимости с Otherworld

- Миксины в Better Combat: только `@Inject`/`@ModifyReturnValue`, никаких `@Overwrite` и отмены на `HEAD`.
- Щиты: не трогать блок щитом и `isBlocking` (Shield Expansion).
- Пресеты: Malfu-датапак уже переопределяет `bettercombat:*`; номер атаки в серии не предполагать
  (серии длинные, у части атак условия).
- Клавиши: Iron's (каст, колесо заклинаний), Soulslike Weaponry, Shoulder Surfing, Kinetrix (B — эмоции),
  Better Combat (финт, копание оружием) — проверить в «Управлении», не занимать их по умолчанию.
- Превращения Otherworld Origins: для превращённого игрока `getCurrentAttack` отдаёт атаку облика;
  механики, завязанные на оружие в руке, должны это учитывать (атака облика не связана с предметом в руке).
- Kinetrix: своё движение всем телом глушит чужие слои; анимации аддона на это время не видны.
- Сервер доверяет клиенту цели: дальность и угол для PvP-механик проверять самому.

---

## 6. Источники

- Better Combat: исходники <https://github.com/ZsoltMolnarrr/BetterCombat> (ветка `1.20.1`, 1.9.0),
  страница <https://modrinth.com/mod/better-combat>.
- playerAnimator: <https://github.com/KosmX/minecraftPlayerAnimator> (ветка `1.20`).
- Iron's Spells 'n Spellbooks: <https://github.com/iron431/irons-spells-n-spellbooks>.
- Сборка Otherworld на CurseForge: <https://www.curseforge.com/minecraft/modpacks/otherworld-dnd>
  (список модов взят из манифеста её клиентского файла).
- Содержимое модов сборки (Shield Expansion, bc_particle, Malfu, Otherworld Origins, JustLevelingFork,
  оружейные моды): jar-файлы по id проекта и файла из манифеста, разобраны и частично декомпилированы
  (CFR 0.152) в `.github/workflows/research.yml`.
