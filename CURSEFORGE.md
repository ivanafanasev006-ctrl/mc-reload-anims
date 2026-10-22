# Kinetrix

**Living, jointed animations for players and mobs — Forge 1.20.1 (beta).** By **Inclyae**.

Kinetrix replaces Minecraft's stiff six-block puppet movement with procedural, physically-driven animation in the spirit of Mo' Bends — with real **elbows, knees and a waist**. Every animation is generated live from what your character is doing, so it reacts to speed, turns, landings, items and the world around you. Purely visual: it never changes how anything plays.

## Players

- **Walking, sprinting, sneaking** with bending knees and elbows, counter-swinging shoulders, a lean into the run, and loose arms that lag and spring with momentum.
- **Jumps, falls and landings**: a tuck on the jump, flailing fall, skydiver pose on long drops, legs that absorb the landing; a forward roll on hard landings while holding W, a back roll when falling backwards off a ledge, and a superhero landing that drives your sword into the ground.
- **Swimming**: front crawl, treading water upright, a head-first dolphin dive from a height, a smooth climb out of the water.
- **Combat**: a four-hit sword combo, heavy axe chops, a boxing guard and alternating punches, bow drawn with the string hand close to the bow, crossbow held like a shotgun, shield guard (also while sneaking), trident throw and thrust.
- **Tools and items**: one-handed mining, hand digging, block placing, fishing (cast, wait, strike), eating with the food at your mouth.
- **Idle life**: stretching, looking around, yawning and more — and when you hold something, you play with it: twirl and inspect a sword, toss a pickaxe, rest an axe on your shoulder, lean on a shovel, shake a lantern, peek into a bucket, turn a block over in your hand.
- **Riding**: horse gaits from walk to a jockey gallop and jumps, pigs and striders with the bait on a stick, rowing a boat, rattling along in a minecart (arms up at full speed), and several things to do while sitting still.
- **Reactions**: knock-back from hits, shielding your face from explosions (or bracing on a planted sword), the Totem of Undying.
- **20 emotes** (default key **B**): Default Dance, Floss, Running Man, Robot, Air Guitar, Flex, Meditate, Wave, Clap, Facepalm, Salute, Bow, Sit, Thumbs Up, Victory, Point, Laugh, Cry, Push-ups and more.

## Mobs

Zombies, skeletons, piglins, villagers, wandering traders, witches, iron golems, creepers, spiders, wolves, cats, chickens, cows, pigs, sheep, horses, llamas and other creatures get a breath of life: a bounce and sway in their step, breathing, heads that look around and nod, tails that wag and swish, a chicken that bobs and pecks, and for humanoid mobs the same jointed knees, elbows and waist as the player (zombies shamble bent over). Looks only — AI and hitboxes are untouched.

## Compatibility

- **Requires:** [Player Animator](https://www.curseforge.com/minecraft/mc-mods/playeranimator) (by KosmX) for Forge 1.20.1.
- **Works with:** Better Combat (and its animation packs such as Malfu Combat Animation), Iron's Spells 'n Spellbooks, Emotecraft and any other Player Animator based mod — attacks and casts play on top and blend in smoothly, while the vanilla-replacement animations stay Kinetrix's.
- **Entity Model Features / Fresh Animations / Fresh Moves:** the player keeps the vanilla model so Kinetrix can animate it with joints (mobs keep their Fresh Animations models). Can be turned off in the config.
- **Skin Layers 3D:** the 3D outer skin layer bends at the elbows, knees and waist too.
- **Embeddium / Oculus / Shoulder Surfing Reloaded:** fine.
- **Not needed:** bendy-lib — Kinetrix draws the bends itself (if bendy-lib is installed, Kinetrix steps aside and lets it).
- **Client side mod, optional on servers.** Put it on the server (or have it on the host of a LAN world) and players who have Kinetrix see each other's emotes, rolls, landings and idle fidgets exactly as chosen. Players without it can still join.

## Configuration

`config/kinetrix-client.toml` — every module can be switched off (locomotion, combat, swimming, idle fidgets, mobs, mob joints, weapon trails...), plus overall intensity and "liveliness" (how loose and springy the limbs are). Key bindings under *Controls → Kinetrix*.

## Beta

This is a beta: please report anything that looks wrong (a short video helps a lot).

---

### По-русски

**Kinetrix** — живые анимации игрока и мобов со сгибами в локтях, коленях и поясе (в духе Mo' Bends) для Forge 1.20.1. Автор: **Inclyae**.

- Ходьба, бег, присед, прыжки, падения, кувырки, плавание, нырки, бой (меч, топор, кулаки, лук, арбалет, щит, трезубец), инструменты, рыбалка, еда.
- Игра с предметом в руке, пока стоишь: крутит и осматривает меч, подкидывает кирку, кладёт топор на плечо, опирается на лопату, трясёт фонарём.
- Езда на лошади, свинье, лавомерке, в лодке и вагонетке, несколько вариантов безделья в сиденье.
- 20 эмоций (клавиша **B**).
- Мобы оживают: дыхание, покачивание при ходьбе, головы и хвосты; зомби, скелеты и пиглины сгибают колени и локти.

**Нужен:** Player Animator. **Совместим:** Better Combat (и Malfu Combat Animation), Iron's Spells 'n Spellbooks, Emotecraft, Fresh Animations / Fresh Moves (игроку остаётся ванильная модель), Skin Layers 3D (3D-слой гнётся), Embeddium, Shoulder Surfing. Клиентский мод; если поставить его ещё и на сервер (или он есть у хоста LAN-мира), игроки видят эмоции и кувырки друг друга.
