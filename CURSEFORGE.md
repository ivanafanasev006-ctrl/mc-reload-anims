# Kinetrix

**Living, jointed player animations — Forge 1.20.1 (beta).** By **Inclyae**.

Kinetrix replaces Minecraft's stiff six-block puppet movement with procedural, physically-driven animation in the spirit of Mo' Bends — with real **elbows, knees and a waist**. Every animation is generated live from what your character is doing, so it reacts to speed, turns, landings, items and the world around you. Purely visual: it never changes how anything plays.

## Players

- **Walking, sprinting, sneaking** with bending knees and elbows, counter-swinging shoulders, a lean into the run, and loose arms that lag and spring with momentum.
- **Jumps, falls and landings**: a tuck on the jump, flailing fall, skydiver pose on long drops, legs that absorb the landing; a forward roll on hard landings while holding W, a back roll when falling backwards off a ledge, and a superhero landing: the sword held point down for the whole drop, a short wind-up over the head just before touchdown, then driven into the ground (walk off right away and it lets go at once).
- **Swimming**: front crawl, treading water upright, a head-first dolphin dive from a height, a smooth climb out of the water.
- **Combat**: a four-hit sword combo, heavy axe chops, a boxing guard and alternating punches, bow drawn with the string hand close to the bow, crossbow held like a shotgun, shield guard (also while sneaking), trident throw and thrust.
- **Tools and items**: one-handed mining, hand digging, block placing, fishing (cast, wait, strike), eating with the food at your mouth.
- **Crawl (hold C)**: get down and crawl through one-block gaps, for as long as you hold the key. Its own animation, not a swim stroke: a low cave-diver crawl, forearm reaching ahead and dragging the body up to it while the opposite knee, drawn up to the side, pushes; resting on the forearms when still.
- **Sit (X)**: sit down on the ground; now and then you shift between leaning back on your hands and resting an arm on a raised knee. The hitbox and the camera go down too.
- **Idle life**: a few clean, polished fidgets (stretching, looking around, cracking the neck, kicking a pebble) — and when you hold something, you play with it: twirl and inspect a sword, practice swings, rest an axe or pickaxe on your shoulder, lean on a shovel, raise a lantern, peek into a bucket, look a block over.
- **Riding**: horse gaits from walk to a gallop and jumps with the legs down round the horse's sides (never pressed into the belly), pigs and striders with the bait on a stick, rowing a boat, rattling along in a minecart (arms up at full speed), and several things to do while sitting still. Big horses (DragN's Livestock Overhaul) get their own seat: lower, legs wider, hands low on the reins.
- **Two on one horse**: right-click a tamed, saddled horse (donkey, mule, or a modded horse) someone is riding to climb on behind them, like sharing a boat; the one in front steers. Needs Kinetrix on the server or world host.
- **Reactions**: knock-back from hits, shielding your face from explosions (or bracing on a planted sword), the Totem of Undying.
- **20 emotes** (default key **B**): Default Dance, Floss, Running Man, Robot, Air Guitar, Flex, Meditate, Wave, Clap, Facepalm, Salute, Bow, Sit, Thumbs Up, Victory, Point, Laugh, Cry, Push-ups and more.

## Compatibility

- **Requires:** [Player Animator](https://www.curseforge.com/minecraft/mc-mods/playeranimator) (by KosmX) for Forge 1.20.1.
- **Works with:** Better Combat (and its animation packs such as Malfu Combat Animation), Iron's Spells 'n Spellbooks, Emotecraft and any other Player Animator based mod — attacks and casts play on top and blend in smoothly, while the vanilla-replacement animations stay Kinetrix's.
- **Entity Model Features / Fresh Animations / Fresh Moves:** the player keeps the vanilla model so Kinetrix alone animates it, with joints — no half-and-half mix with Fresh Moves (any EMF 2.x or newer). Mobs are left alone and keep their Fresh Animations. Can be turned off in the config.
- **Skin Layers 3D:** the 3D outer skin layer bends at the elbows, knees and waist too.
- **Oculus shaders, Embeddium, Xenon, Shoulder Surfing Reloaded:** fine — body, joints and armour stay visible with shaders on.
- **Personality:** its crawl and sit get Kinetrix animations; Kinetrix's own C / X keys start unbound so nothing is bound twice.
- **ParCool:** while ParCool plays one of its parkour moves, Kinetrix steps aside for that player and comes back when it is over.
- **DragN's Livestock Overhaul:** riding its wide horses, donkeys and mules the rider sits lower with the legs a little wider apart and the hands low on the reins (other horse mods can be added in the config).
- **Not needed:** bendy-lib — Kinetrix draws the bends itself (if bendy-lib is installed, Kinetrix steps aside and lets it).
- **Client side mod, optional on servers.** Put it on the server (or have it on the host of a LAN world) and players who have Kinetrix see each other's emotes, rolls, landings, idle fidgets and sitting exactly as chosen, and can crawl (crawling needs the server's agreement). Players without it can still join.

## Configuration

`config/kinetrix-client.toml` — every module can be switched off (locomotion, combat, swimming, idle fidgets, crawl and sit, weapon trails...), sit as toggle or hold (crawl is always hold), plus overall intensity and "liveliness" (how loose and springy the limbs are). Key bindings under *Controls → Kinetrix*.

## Beta

This is a beta: please report anything that looks wrong (a short video helps a lot).

---

### По-русски

**Kinetrix** — живые анимации игрока со сгибами в локтях, коленях и поясе (в духе Mo' Bends) для Forge 1.20.1. Автор: **Inclyae**.

- Ходьба, бег, присед, прыжки, падения, кувырки, плавание, нырки, бой (меч, топор, кулаки, лук, арбалет, щит, трезубец), инструменты, рыбалка, еда.
- Ползание на **C** (пролезает в проём в 1 блок, по-пластунски, не как плавание) и сидение на **X** (несколько поз, хитбокс и камера опускаются).
- Игра с предметом в руке, пока стоишь: крутит и осматривает меч, кладёт топор на плечо, опирается на лопату, поднимает фонарь.
- Езда на лошади, свинье, лавомерке, в лодке и вагонетке, несколько вариантов безделья в сиденье. Большие лошади DragN's Livestock Overhaul: посадка ниже, ноги шире, руки низко на поводьях.
- Вдвоём на одной лошади, как в лодке: ПКМ по лошади с всадником — садишься сзади (нужен Kinetrix на сервере или у хоста мира).
- 20 эмоций (клавиша **B**).

**Нужен:** Player Animator. **Совместим:** Better Combat (и Malfu Combat Animation), Iron's Spells 'n Spellbooks, Emotecraft, Fresh Animations / Fresh Moves (игроку остаётся ванильная модель, без смеси анимаций), Skin Layers 3D (3D-слой гнётся), шейдеры Oculus, Embeddium, Xenon, Shoulder Surfing, Personality, DragN's Livestock Overhaul, ParCool (во время его паркур-движений Kinetrix уступает). Клиентский мод; если поставить его ещё и на сервер (или он есть у хоста LAN-мира), игроки видят эмоции и кувырки друг друга.
