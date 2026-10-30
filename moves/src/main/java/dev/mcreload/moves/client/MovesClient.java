package dev.mcreload.moves.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mcreload.moves.HudConfig;
import dev.mcreload.moves.Kick;
import dev.mcreload.moves.KinetrixMoves;
import dev.mcreload.moves.Move;
import dev.mcreload.moves.MoveServer;
import dev.mcreload.moves.MovesConfig;
import dev.mcreload.moves.Stamina;
import dev.mcreload.moves.compat.KinetrixBridge;
import dev.mcreload.moves.net.MovesNet;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import dev.mcreload.moves.Paths;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import org.lwjgl.glfw.GLFW;

/**
 * The player's side of the moves: the keys, the move started at once (and sent to the server to
 * check), the push that carries the player, and the moves of other players as the server reports
 * them.
 *
 * <p>Keys (docs/combat-spec.md, 1): R with A / D dodges to that side, R with S / W rolls back /
 * forward; R first and the direction a moment later works too. Sprinting, a sharp press of C
 * slides: Kinetrix's crawl key when Kinetrix is installed (its crawl is the same key), else our
 * own. W and Space down (held, or pressed a moment before), R: the vault with a kick at a mob,
 * else the deep jump. Each move can be switched off for this player (HudConfig.Moves).</p>
 */
public final class MovesClient {
    public static final KeyMapping DODGE = new KeyMapping("key.kinetrix_moves.dodge", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.kinetrix_moves");
    /** Only registered without Kinetrix; with it the slide uses Kinetrix's crawl key. */
    private static final KeyMapping OWN_SLIDE = new KeyMapping("key.kinetrix_moves.slide", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, "key.categories.kinetrix_moves");
    /** The ability (author, 2026-10-30: G, rebindable): the sword's turn with a sword, the whirlwind with an axe. */
    public static final KeyMapping ABILITY = new KeyMapping("key.kinetrix_moves.ability", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.kinetrix_moves");
    private static final boolean KINETRIX = ModList.get().isLoaded("kinetrix");

    /** R pressed without a direction: waits this many more ticks for A, D, S or W. */
    private static int dodgeWait;
    private static boolean slideWasDown, jumpWasDown, attackWasDown;
    /** What the server was last told of the fast run's key (Ctrl). */
    private static boolean runSent;
    /** Facing at the start of the local move: the push keeps this direction. */
    private static float moveYaw;
    /** The local move's push direction when it is not the move's own side (a bow dodge: the way the keys point), else null. */
    private static Vec3 pushDir;
    /** Ticks the slide has been off the ground. */
    private static int airTicks;
    /** Ticks since Space was last pressed (the kick and the dive: W, Space and R down together, give or take a few ticks). */
    private static int sinceJump = 100;
    /** Space pressed this many ticks before R still counts as down with it (author: no timing needed). */
    private static final int JUMP_WITH_R = 6;
    /** The player was sprinting when the move started: a vault or a dash that ends in a run keeps the sprint. */
    private static boolean sprinted;
    /** The kick: the turn to the mob (yaw from and to) and the height its spinning jump starts from. */
    private static float turnFrom, turnTo;
    private static double spinFrom;
    /** The dive: started on the ground (its own jump; in a jump already begun, that jump's arc) and the run's speed into it. */
    private static boolean diveGround;
    private static float diveRun;
    /** The bars as the server last sent them. */
    static float maxEnergy = 100f, maxShields = 10f;
    /** Ticks left of the energy bar's flash (a move was refused for lack of energy). */
    static int flash;
    /** Ticks left of the shield row's shake (a shield was spent on a block). */
    static int shieldFlash;

    public static void init(IEventBus modBus) {
        modBus.addListener(MovesClient::registerKeys);
        modBus.addListener(MovesHud::register);
        MoveAnimator.init();
        MinecraftForge.EVENT_BUS.addListener(MovesClient::clientTick);
        MinecraftForge.EVENT_BUS.addListener(MovesClient::playerTick);
        MinecraftForge.EVENT_BUS.addListener(MovesClient::movementInput);
        MinecraftForge.EVENT_BUS.addListener(MovesClient::interaction);
        MinecraftForge.EVENT_BUS.addListener(MovesMenuScreen::registerCommand);
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new MovesMenuScreen(parent)));
        GuardInput.init();
        StoopFx.init();
        Cutscene.init();
                if (MovesSelfTest.enabled()) MovesSelfTest.init();
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(DODGE);
        event.register(ABILITY);
        if (!KINETRIX) event.register(OWN_SLIDE);
    }

    /** The server has this mod: moves are possible and the bars have data. */
    public static boolean serverHasMod() {
        Minecraft mc = Minecraft.getInstance();
        return mc.getConnection() != null && MovesNet.CHANNEL.isRemotePresent(mc.getConnection().getConnection());
    }

    // ------------------------------------------------------------------ keys

    private static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (flash > 0) flash--;
        if (shieldFlash > 0) shieldFlash--;
        boolean dodgePressed = false, abilityPressed = false;
        while (DODGE.consumeClick()) dodgePressed = true;
        while (ABILITY.consumeClick()) abilityPressed = true;
        boolean slideDown = held(mc, slideKey(mc));
        boolean slidePressed = slideDown && !slideWasDown;
        slideWasDown = slideDown;
        boolean jumpDown = mc.options.keyJump.isDown(), attackDown = mc.options.keyAttack.isDown();
        boolean jumpPressed = jumpDown && !jumpWasDown, attackPressed = attackDown && !attackWasDown;
        jumpWasDown = jumpDown;
        attackWasDown = attackDown;
        sinceJump = jumpPressed ? 0 : Math.min(sinceJump + 1, 100);
        if (p == null || mc.screen != null || !serverHasMod()) {
            if (runSent && p != null && serverHasMod()) MovesNet.CHANNEL.sendToServer(new MovesNet.Run(false));
            runSent = false;
            dodgeWait = 0;
            StrikeInput.reset();
            LedgeInput.reset();
            if (p != null) Cutscene.tick(mc, p, Stamina.of(p));
            return;
        }
        Stamina s = Stamina.of(p);
        Cutscene.tick(mc, p, s);

        // ---- the fast run: Ctrl held (the server gives the speed while sprinting and takes the energy)
        boolean run = HudConfig.Moves.FAST_RUN.on() && mc.options.keySprint.isDown();
        if (run != runSent) {
            runSent = run;
            MovesNet.CHANNEL.sendToServer(new MovesNet.Run(run));
        }

        // ---- the ledge: catching it in the air and everything on it (it takes the right button first)
        if (LedgeInput.tick(mc, p, s, dodgePressed, jumpPressed, slidePressed, attackPressed)) {
            dodgeWait = 0;
            return;
        }

        // ---- the fall: R held while falling tucks; landing tucked rolls; R let go in the air untucks
        if (s.move == Move.FALL_TUCK) {
            if (p.onGround()) {
                start(p, s, Move.FALL_ROLL, false, -1);
            } else if (!DODGE.isDown() || p.isInWater()) {
                cancel(p, s, 4);
            }
            dodgeWait = 0;
            return;
        }
        if ((s.move == null || s.move == Move.PLUNGE_FALL) && DODGE.isDown() && HudConfig.Moves.FALL_ROLL.on()
                && dev.mcreload.moves.Fall.canTuck(p) && MoveServer.allowed(p, s, Move.FALL_TUCK)) {
            start(p, s, Move.FALL_TUCK, false, -1);
            dodgeWait = 0;
            return;
        }

        // ---- the plunge: falling far enough to be hurt with a sword or an axe in hand, the blade is
        // held point down; landing drives it into the ground (R held: the tuck instead, above)
        if (s.move == Move.PLUNGE_FALL) {
            if (p.onGround()) {
                if (!start(p, s, Move.PLUNGE, false, -1)) cancel(p, s, 3);
            } else if (p.isInWater() || !dev.mcreload.moves.Strikes.finisherWeapon(p)) {
                cancel(p, s, 4);
            }
            dodgeWait = 0;
            return;
        }
        if (s.move == null && !DODGE.isDown() && HudConfig.Moves.PLUNGE.on() && dev.mcreload.moves.Plunge.canHold(p)
                && MoveServer.allowed(p, s, Move.PLUNGE_FALL)) {
            start(p, s, Move.PLUNGE_FALL, false, -1);
        }

        // ---- the slide: sprint + C. While a slide is possible Kinetrix must not take C for its crawl.
        if (s.move == Move.SLIDE) {
            KinetrixBridge.blockCrawl(250);
            if (s.moveTick <= Move.SLIDE_CRAWL_TICK) s.held &= slideDown;
            airTicks = p.onGround() ? 0 : airTicks + 1;
            // The left button with a sword or an axe: the attack out of the slide (after its cooldown).
            if (attackPressed && HudConfig.Moves.SLIDE_ATTACK.on() && dev.mcreload.moves.Strikes.finisherWeapon(p)) {
                if (s.slideAttackCooldown > 0) {
                    MovesHud.refused(Move.SLIDE_ATTACK);
                    p.displayClientMessage(net.minecraft.network.chat.Component.translatable("kinetrix_moves.slide_attack.cooldown",
                            (s.slideAttackCooldown + 19) / 20), true);
                } else if (start(p, s, Move.SLIDE_ATTACK, false, -1)) {
                    StrikeInput.swallow(mc);
                    attackPressed = false;
                }
            }
            if (s.move != Move.SLIDE) {
                // the attack took over
            } else if (jumpPressed || attackPressed || airTicks > 4) {
                cancel(p, s, 3);
            } else if (s.held && s.moveTick >= Move.SLIDE_CRAWL_TICK) {
                // Held to the end: the slide becomes Kinetrix's crawl (C is still down). The slide's
                // pose stays and fades while the crawl comes in, instead of standing up first.
                Move move = s.move;
                s.end(p);
                MoveAnimator.handOver(p, 10);
                MovesNet.CHANNEL.sendToServer(new MovesNet.Cancel((byte) move.ordinal()));
                KinetrixBridge.blockCrawl(0);
            }
        } else if (HudConfig.Moves.SLIDE.on() && p.isSprinting() && p.onGround() && MoveServer.allowed(p, s, Move.SLIDE)) {
            KinetrixBridge.blockCrawl(250);
            if (slidePressed) start(p, s, Move.SLIDE, true, -1);
        }

        // ---- once a move's push is over, running, jumping or striking ends it: a dodge, a roll, a
        // slide or the hop does not hold the player in place while its animation plays out
        if (s.move != null && recoverable(s) && (moving(mc) || jumpPressed || attackPressed)) {
            Move was = s.move;
            cancel(p, s, was == Move.DASH || was == Move.HOP ? 5 : 3);
            // a vault or a dash run on: the sprint they started from goes on (the move held the keys)
            if ((was == Move.HOP || was == Move.DASH) && sprinted && mc.options.keyUp.isDown()) p.setSprinting(true);
        }

        // ---- the strikes: the finisher, the dash strike (they take the click), the ability on G
        if (StrikeInput.tick(mc, p, s, attackDown, attackPressed, abilityPressed)) attackPressed = false;

        // ---- the bow: the flow and the Legolas stance (their arrows, poses and dodges), then the
        // volley: a fully drawn bow and the left button (which in the flow is the flow's next step)
        BowStance.tickLocal(p, s);
        if (attackPressed && !BowStance.attackKey(p, s) && s.move == null && HudConfig.Moves.VOLLEY.on()
                && dev.mcreload.moves.Bow.volleyReady(p)) {
            start(p, s, Move.BARRAGE, false, -1);
            if (s.move == Move.BARRAGE) p.stopUsingItem();    // the volley takes over the drawn bow
        }
        // The left button is the bow's while the volley plays and in the flow and the Legolas stance
        // (on one knee between two arrows the bow is not drawn): it swings nothing, not even Better
        // Combat's blow, which comes before Forge's interaction event that stops vanilla's.
        if (s.move == Move.BARRAGE || BowStance.active(p) || BowStance.kneeling(p)) StrikeInput.swallow(mc);

        // ---- dodges and rolls: R and a direction (R with the bow fully drawn: the Legolas stance)
        if (dodgePressed && BowStance.dodgeKey(p)) dodgePressed = false;
        if (dodgePressed) dodgeWait = 4;
        if (dodgeWait > 0) {
            dodgeWait--;
            Move move = direction(mc);
            if (move != null) {
                dodgeWait = 0;
                // R + W with a mob of some size at point blank: vault over it instead of rolling. With
                // Space down too (held, or pressed a moment before; author, 2026-10-29: no timing, R at
                // the mob with the others down): the vault with a kick; with no mob near, the dive.
                net.minecraft.world.entity.LivingEntity enemy = move == Move.DODGE_FRONT ? dev.mcreload.moves.Hop.target(p, true) : null;
                boolean jump = move == Move.DODGE_FRONT && (mc.options.keyJump.isDown() || sinceJump <= JUMP_WITH_R);
                // with Ctrl (the fast run) down too: the roll kick, over it in a flip (author's draft; the keys a default)
                if (jump && enemy != null && mc.options.keySprint.isDown() && HudConfig.Moves.ROLL_KICK.on()
                        && start(p, s, Move.ROLL_KICK, false, enemy.getId())) return;
                if (jump && enemy != null && HudConfig.Moves.KICK.on() && start(p, s, Move.KICK, false, enemy.getId())) return;
                if (jump && enemy == null && HudConfig.Moves.DIVE.on() && start(p, s, Move.DIVE, false, -1)) return;
                if (enemy != null && HudConfig.Moves.VAULT.on()) start(p, s, Move.HOP, false, enemy.getId());
                else if (move.sideDodge() ? HudConfig.Moves.DODGES.on() : HudConfig.Moves.ROLLS.on()) start(p, s, move, false, -1);
            }
        }
    }

    /** The dodge for the direction keys held: sides first, then back, then forward. */
    private static Move direction(Minecraft mc) {
        boolean left = mc.options.keyLeft.isDown(), right = mc.options.keyRight.isDown();
        if (left != right) return left ? Move.DODGE_LEFT : Move.DODGE_RIGHT;
        if (mc.options.keyDown.isDown()) return Move.DODGE_BACK;
        if (mc.options.keyUp.isDown()) return Move.DODGE_FRONT;
        return null;
    }

    /** The move's push is over: from now on the player may break it off (Move.recoverFrom; the slide once it has slid its length). */
    private static boolean recoverable(Stamina s) {
        if (s.move == Move.SLIDE) {
            return !s.held && s.moveTick > Move.PUSH_TICK && Move.slideSpeed(MovesConfig.slideBlocks(), s.slid) <= 0f;
        }
        int from = s.move.recoverFrom();
        return from >= 0 && s.moveTick >= from;
    }

    private static boolean moving(Minecraft mc) {
        return mc.options.keyUp.isDown() || mc.options.keyDown.isDown() || mc.options.keyLeft.isDown() || mc.options.keyRight.isDown();
    }

    static KeyMapping slideKey(Minecraft mc) {
        if (!KINETRIX) return OWN_SLIDE;
        for (KeyMapping k : mc.options.keyMappings) if ("key.kinetrix.crawl".equals(k.getName())) return k;
        return OWN_SLIDE;
    }

    /**
     * The key is held: through its binding, or read straight from the keyboard (vanilla binds C
     * too, and a key shared by two bindings may reach only one of them).
     */
    static boolean held(Minecraft mc, KeyMapping km) {
        if (km.isDown()) return true;
        if (mc.screen != null || km.isUnbound()) return false;
        InputConstants.Key k = km.getKey();
        if (k.getValue() < 0) return false;
        // a mouse button: as it is under the hand (a move that took the button lets go of its binding)
        if (k.getType() == InputConstants.Type.MOUSE) return GLFW.glfwGetMouseButton(mc.getWindow().getWindow(), k.getValue()) == GLFW.GLFW_PRESS;
        if (k.getType() != InputConstants.Type.KEYSYM) return false;
        return InputConstants.isKeyDown(mc.getWindow().getWindow(), k.getValue());
    }

    // ------------------------------------------------------------------ the local move

    /** Starts a bow move pushed along {@code dir} (null: the move's own side). Returns whether it started. */
    static boolean start(LocalPlayer p, Stamina s, Move move, Vec3 dir) {
        if (!start(p, s, move, false, -1)) return false;
        pushDir = dir;
        return true;
    }

    /** Starts a move made at a mob (the vault, the finisher), a friend, or on a ledge (target: its wall's way). Returns whether it started. */
    static boolean start(LocalPlayer p, Stamina s, Move move, int target) {
        return start(p, s, move, false, target);
    }

    private static boolean start(LocalPlayer p, Stamina s, Move move, boolean held, int target) {
        if (!MoveServer.allowed(p, s, move)) {
            if (!s.canAfford(move, p)) flash = 12;
            return false;
        }
        Move was = s.move;
        s.spend(move.cost(was), p);
        GuardInput.lowerLocal(p, s, false);     // the server lowers it too
        sprinted = p.isSprinting();
        s.start(p, move, held);
        moveYaw = p.getYRot();
        pushDir = null;
        airTicks = 0;
        s.target = target;
        if (move == Move.DASH) s.dashCooldown = MovesConfig.dashCooldownTicks();
        if (move == Move.WHIRL) s.whirlCooldown = MovesConfig.whirlCooldownTicks();
        if (move == Move.SWORD_SPIN) {
            s.spinCooldown = MovesConfig.swordSpinCooldownTicks();
            s.attackYaw = p.getYRot();
        }
        if (move == Move.SLIDE_ATTACK) {
            s.slideAttackCooldown = MovesConfig.slideAttackCooldownTicks();
            s.attackYaw = p.getYRot();
        }
        if (move.ledge()) LedgeInput.started(p, move, was);
        // the kick goes at the mob, whichever way within reach the player looked
        Entity mob = move == Move.KICK || move == Move.ROLL_KICK ? p.level().getEntity(target) : null;
        if (mob != null && mob.position().subtract(p.position()).horizontalDistanceSqr() > 1.0E-4) {
            moveYaw = (float) (Mth.atan2(-(mob.getX() - p.getX()), mob.getZ() - p.getZ()) * Mth.RAD_TO_DEG);
        }
        MoveAnimator.play(p, move, was);
        MovesNet.CHANNEL.sendToServer(new MovesNet.Request((byte) move.ordinal(), held, target));
        return true;
    }

    static void cancel(LocalPlayer p, Stamina s, int fade) {
        Move move = s.move;
        if (move == null) return;
        s.end(p);
        MoveAnimator.stop(p, fade);
        MovesNet.CHANNEL.sendToServer(new MovesNet.Cancel((byte) move.ordinal()));
    }

    /** The push: the move carries the player, whatever keys are held. Every tick, before moving. */
    private static void playerTick(TickEvent.PlayerTickEvent event) {
        if (!(event.player instanceof AbstractClientPlayer p) || !p.level().isClientSide) return;
        Stamina s = Stamina.of(p);
        if (event.phase == TickEvent.Phase.END) {
            if (p == Minecraft.getInstance().player && p instanceof LocalPlayer lp) {
                LedgeInput.place(lp, s);
                kickTurn(lp, s);
                MoveServer.seen(p);
            } else {
                LedgeInput.faceWall(p, s);
            }
            if (s.move == Move.SLIDE_ATTACK || s.move == Move.KICK || s.move == Move.ROLL_KICK) {
                p.yBodyRot = p.getYRot();     // the turn is the player's own, the body goes with it at once
            }
            cues(p, s);
            return;
        }
        if (p != Minecraft.getInstance().player || s.move == null) return;
        Move move = s.move;
        int t = s.moveTick;
        if (move == Move.HOP) {
            hop(p, s, t);
            return;
        }
        if (move == Move.DASH) {
            dash(p, t);
            return;
        }
        if (move == Move.KICK) {
            kick(p, s, t);
            return;
        }
        if (move == Move.ROLL_KICK) {
            rollKick(p, s, t);
            return;
        }
        if (move == Move.WHIRL || move == Move.WHIRL_END) {
            whirl(p, move, t);
            return;
        }
        // the sword's turns: no stun, the keys walk the player as ever (author, 2026-10-30, after the video)
        if (move == Move.SWORD_SPIN || move == Move.SPIN_END) return;
        if (move == Move.PLUNGE_FALL) return;     // the little steering vanilla gives in the air stays
        if (move == Move.PLUNGE) {
            // down on the knee with the blade in the ground: no sliding on
            Vec3 v = p.getDeltaMovement();
            p.setDeltaMovement(v.x * 0.3, v.y, v.z * 0.3);
            return;
        }
        if (move == Move.DIVE) {
            dive(p, t);
            return;
        }
        if (move == Move.SLIDE_ATTACK) {
            slideAttack(p, s, t);
            return;
        }
        if (move == Move.FALL_ROLL) {
            Vec3 dir = move(Move.FALL_ROLL);
            float speed = t < dev.mcreload.moves.Fall.STAND ? Paths.at("fall_roll", t + 1, 0) - Paths.at("fall_roll", t, 0) : 0f;
            p.setDeltaMovement(dir.x * speed, p.getDeltaMovement().y, dir.z * speed);
            return;
        }
        if (move == Move.FALL_TUCK) return;
        if (move.ledge() || move == Move.LEDGE_HELP) {
            // held where the edge is (LedgeInput.place, after vanilla has moved the player)
            p.setDeltaMovement(Vec3.ZERO);
            return;
        }
        float speed;
        if (move == Move.SLIDE) {
            // The run-up before the push counts towards the slide's length.
            float blocks = s.held ? MovesConfig.slideHeldBlocks() : MovesConfig.slideBlocks();
            speed = t < Move.PUSH_TICK ? (float) Math.max(p.getDeltaMovement().horizontalDistance(), 0.28)
                    : Move.slideSpeed(blocks, s.slid);
            s.slid += speed;
        } else {
            speed = move.pushSpeed(t);
        }
        if (speed <= 0f) return;
        Vec3 dir = pushDir != null ? pushDir : move.direction(moveYaw);
        p.setDeltaMovement(dir.x * speed, p.getDeltaMovement().y, dir.z * speed);
    }

    /** The vault's flight: horizontal velocity, take-off speed and the share of vanilla gravity in the air. */
    private static Vec3 hopVelocity = Vec3.ZERO;
    private static float hopUp, hopGravity, hopRun;

    /**
     * The vault: the run goes on through the crouch (stopping short of the mob), then a jump over
     * its back, as high as that back is as it stoops (Hop.back: a pig needs less than a zombie),
     * 10 ticks in the air whatever the height, landing {@link Move#HOP_BEYOND} blocks beyond it (or
     * further, as fast as the run came in) and running on the way the player was going.
     */
    private static void hop(AbstractClientPlayer p, Stamina s, int t) {
        vault(p, s, t, true);
    }

    /** The vault (the hop, and the start of the kick); {@code runOn}: landed, the run goes on, else the feet brake. */
    private static void vault(AbstractClientPlayer p, Stamina s, int t, boolean runOn) {
        Vec3 v = p.getDeltaMovement();
        net.minecraft.world.entity.Entity enemy = p.level().getEntity(s.target);
        if (t < Move.HOP_TAKEOFF) {
            // The run goes on into the take-off (it is a vault in a run, author: "no stop"), up to the mob.
            Vec3 dir = move(Move.HOP);
            double speed = Math.min(Math.max(v.horizontalDistance(), 0.2), 0.32);
            if (t == 0) hopRun = (float) speed;
            if (enemy != null) speed = Math.min(speed, Math.max(0, gap(p, enemy) - 0.05));
            p.setDeltaMovement(dir.x * speed, v.y, dir.z * speed);
            return;
        }
        int air = Move.HOP_LAND - Move.HOP_TAKEOFF;
        if (t == Move.HOP_TAKEOFF) {
            Vec3 over = enemy != null ? enemy.position() : p.position().add(move(Move.HOP).scale(1.5));
            Vec3 dir = new Vec3(over.x - p.getX(), 0, over.z - p.getZ());
            if (dir.lengthSqr() < 1.0E-4) dir = move(Move.HOP);
            dir = dir.normalize();
            Vec3 land = over.add(dir.scale(Move.HOP_BEYOND));
            // as fast over as the run was: a quicker run lands further on
            double far = Math.max(hopRun, 0.24) * air;
            if (new Vec3(land.x - p.getX(), 0, land.z - p.getZ()).length() < far) land = p.position().add(dir.scale(far));
            hopVelocity = new Vec3((land.x - p.getX()) / air, 0, (land.z - p.getZ()) / air);
            // A jump that tops out at the mob's stooping back and comes down after `air` ticks, tick
            // by tick (vanilla drag aside): gravity 8h/(air^2 - 1) a tick, up at 4h/(air + 1).
            float top = enemy instanceof net.minecraft.world.entity.LivingEntity le
                    ? (float) (dev.mcreload.moves.Hop.back(le) + le.getY() - p.getY()) : 1.0f;
            top = Mth.clamp(top, 0.35f, 2.2f);
            hopUp = 4f * top / (air + 1);
            hopGravity = 8f * top / (air * air - 1) / 0.08f;
            p.setDeltaMovement(hopVelocity.x, hopUp, hopVelocity.z);
            p.setOnGround(false);
            return;
        }
        if (t <= Move.HOP_LAND + 2 && !p.onGround()) {
            p.setDeltaMovement(hopVelocity.x, v.y + 0.08 * (1 - hopGravity), hopVelocity.z);
        } else if (!runOn) {
            p.setDeltaMovement(v.x * 0.35, v.y, v.z * 0.35);
        } else if (t <= Move.HOP_LAND + 3) {
            // Landed: running on, the way the vault went, as fast as it came in.
            Vec3 dir = hopVelocity.lengthSqr() > 1.0E-6 ? hopVelocity.normalize() : move(Move.HOP);
            double speed = Math.max(0.28, hopRun);
            p.setDeltaMovement(dir.x * speed, v.y, dir.z * speed);
        }
    }

    /** Blocks between the player's hitbox and another entity's, horizontally. */
    private static double gap(net.minecraft.world.entity.Entity a, net.minecraft.world.entity.Entity b) {
        double d = new Vec3(b.getX() - a.getX(), 0, b.getZ() - a.getZ()).length();
        return d - a.getBbWidth() / 2 - b.getBbWidth() / 2;
    }

    /**
     * The dash strike: the run goes on and speeds up into the lunge (Strikes.DASH_RUN), the way the
     * player looks (it can be turned on the way), up to the side of the mob in front instead of
     * through it, and runs on after it.
     */
    private static void dash(AbstractClientPlayer p, int t) {
        float[] run = dev.mcreload.moves.Strikes.DASH_RUN;
        Vec3 v = p.getDeltaMovement();
        // after the lunge it does not stop: a sprint on, while the blade comes back (author, 2026-10-29)
        float speed = t < run.length ? run[t] : dev.mcreload.moves.Strikes.DASH_AFTER
                * (float) Math.pow(dev.mcreload.moves.Strikes.DASH_AFTER_DRAG, t - run.length);
        net.minecraft.world.entity.LivingEntity ahead = dev.mcreload.moves.Strikes.dashTarget(p);
        if (ahead != null && gap(p, ahead) <= dev.mcreload.moves.Strikes.DASH_STOP) speed = 0f;
        Vec3 dir = Move.DASH.direction(p.getYRot());
        p.setDeltaMovement(dir.x * speed, v.y, dir.z * speed);
    }

    private static Vec3 move(Move m) {
        return m.direction(moveYaw);
    }

    /** The kick's swung leg meets the mob's side this far from the player's middle: the jump drifts in until it is. */
    private static final double KICK_GAP = 0.7;

    /**
     * The vault with a kick (Kick): the vault of the hop, landing beyond the mob, without running on
     * (the feet brake through the turn to it); the spinning jump low over the ground (Paths
     * "hop_on_kick"), drifting in so the swung leg meets the mob, and the landing, facing it.
     * Carried by velocity, so walls and ceilings still stop the player.
     */
    private static void kick(AbstractClientPlayer p, Stamina s, int t) {
        p.fallDistance = 0f;
        if (t <= Move.HOP_LAND) {
            vault(p, s, t, false);
            return;
        }
        Vec3 v = p.getDeltaMovement();
        if (t < Kick.SPIN || t >= Kick.LAND) {
            p.setDeltaMovement(v.x * 0.35, v.y, v.z * 0.35);
            return;
        }
        if (t == Kick.SPIN) spinFrom = p.getY();
        Vec3 in = Vec3.ZERO;
        Entity mob = p.level().getEntity(s.target);
        if (mob != null && t < Kick.HIT) {
            Vec3 to = new Vec3(mob.getX() - p.getX(), 0, mob.getZ() - p.getZ());
            double gap = to.length() - mob.getBbWidth() / 2;
            if (gap > KICK_GAP && to.lengthSqr() > 1.0E-4) in = to.normalize().scale(Math.min(0.3, (gap - KICK_GAP) / (Kick.HIT - t)));
        }
        double up = spinFrom + Paths.at("hop_on_kick", t + 1, 0) - p.getY();
        p.setDeltaMovement(in.x, up, in.z);
    }

    /**
     * The kick's half turn to the mob after the vault (Kick.TURN_FROM-TURN_TO), the camera with it:
     * after vanilla's tick, so the frames between two ticks turn smoothly.
     */
    private static void kickTurn(LocalPlayer p, Stamina s) {
        if (s.move != Move.KICK && s.move != Move.ROLL_KICK) return;
        boolean roll = s.move == Move.ROLL_KICK;
        int t = s.moveTick, from = roll ? Kick.RK_TURN_FROM : Kick.TURN_FROM, to = roll ? Kick.RK_TURN_TO : Kick.TURN_TO;
        if (t < from || t > to) return;
        if (t == from) {
            turnFrom = p.getYRot();
            Entity mob = p.level().getEntity(s.target);
            Vec3 at = mob == null ? Vec3.ZERO : new Vec3(mob.getX() - p.getX(), 0, mob.getZ() - p.getZ());
            // the roll kick turns in the flip right over the mob: back the way it flies, to where the mob will be behind it
            if (roll) at = new Vec3(-hopVelocity.x, 0, -hopVelocity.z);
            turnTo = at.lengthSqr() > 1.0E-6 ? (float) (Mth.atan2(-at.x, at.z) * Mth.RAD_TO_DEG) : turnFrom + 180f;
        }
        float f = (t - from + 1) / (float) (to - from + 1);
        float yaw = turnFrom + Mth.wrapDegrees(turnTo - turnFrom) * f * f * (3 - 2 * f);
        p.setYRot(yaw);
        p.setYHeadRot(yaw);
        p.yBodyRot = yaw;
    }

    /**
     * The deep jump (dive_roll; Paths "dive_roll", up and forward): the run goes on into it; from the
     * ground its own jump, from a jump already begun that jump's arc; the hands take the ground and
     * the roll carries on. Carried by velocity, so walls and ceilings still stop the player, and a
     * dive off a drop falls on.
     */
    private static void dive(AbstractClientPlayer p, int t) {
        Vec3 v = p.getDeltaMovement();
        if (t == 0) {
            diveGround = p.onGround();
            diveRun = (float) Math.min(v.horizontalDistance(), 0.32);
        }
        float speed = Paths.at("dive_roll", t + 1, 1) - Paths.at("dive_roll", t, 1);
        if (t < Move.DIVE_TOUCH) speed = Math.max(speed, diveRun);
        double vy = v.y;
        if (diveGround && t < Move.DIVE_TOUCH) vy = Paths.at("dive_roll", t + 1, 0) - Paths.at("dive_roll", t, 0);
        Vec3 dir = move(Move.DIVE);
        p.setDeltaMovement(dir.x * speed, vy, dir.z * speed);
    }

    /**
     * The roll kick (hop_roll_kick): the run goes on into the take-off up to the mob, then a jump over
     * it, higher than the vault's (the flip, tucked, goes over its stooping back), landing
     * Kick.RK_BEYOND beyond it; the mechanic turns the player to it in the flip (kickTurn); after the
     * landing the back roll carries them away from it, Kick.RK_ROLL_BLOCKS.
     */
    private static void rollKick(AbstractClientPlayer p, Stamina s, int t) {
        p.fallDistance = 0f;
        Vec3 v = p.getDeltaMovement();
        Entity enemy = p.level().getEntity(s.target);
        if (t < Kick.RK_TAKEOFF) {
            Vec3 dir = move(Move.ROLL_KICK);
            double speed = Math.min(Math.max(v.horizontalDistance(), 0.2), 0.36);
            if (t == 0) hopRun = (float) speed;
            if (enemy != null) speed = Math.min(speed, Math.max(0, gap(p, enemy) - 0.05));
            p.setDeltaMovement(dir.x * speed, v.y, dir.z * speed);
            return;
        }
        int air = Kick.RK_LAND - Kick.RK_TAKEOFF;
        if (t == Kick.RK_TAKEOFF) {
            Vec3 over = enemy != null ? enemy.position() : p.position().add(move(Move.ROLL_KICK).scale(1.5));
            Vec3 dir = new Vec3(over.x - p.getX(), 0, over.z - p.getZ());
            if (dir.lengthSqr() < 1.0E-4) dir = move(Move.ROLL_KICK);
            dir = dir.normalize();
            Vec3 land = over.add(dir.scale(Kick.RK_BEYOND));
            hopVelocity = new Vec3((land.x - p.getX()) / air, 0, (land.z - p.getZ()) / air);
            // the top of the jump over its stooping back, half a block more for the tucked body
            float top = enemy instanceof net.minecraft.world.entity.LivingEntity le
                    ? (float) (dev.mcreload.moves.Hop.back(le) + le.getY() - p.getY() + 0.5) : 1.4f;
            top = Mth.clamp(top, 0.8f, 2.6f);
            hopUp = 4f * top / (air + 1);
            hopGravity = 8f * top / (air * air - 1) / 0.08f;
            p.setDeltaMovement(hopVelocity.x, hopUp, hopVelocity.z);
            p.setOnGround(false);
            return;
        }
        if (t <= Kick.RK_LAND + 1 && !p.onGround()) {
            p.setDeltaMovement(hopVelocity.x, v.y + 0.08 * (1 - hopGravity), hopVelocity.z);
        } else if (t >= Kick.RK_ROLL_FROM && t < Kick.RK_ROLL_TO) {
            // the back roll after it, away from the mob the player faces now (author, 2026-10-30: not at it)
            float span = Kick.RK_ROLL_TO - Kick.RK_ROLL_FROM;
            float f0 = (t - Kick.RK_ROLL_FROM) / span, f1 = (t + 1 - Kick.RK_ROLL_FROM) / span;
            double speed = Kick.RK_ROLL_BLOCKS * (f1 * f1 * (3 - 2 * f1) - f0 * f0 * (3 - 2 * f0));
            Vec3 dir = Move.ROLL_KICK.direction(p.getYRot()).reverse();
            p.setDeltaMovement(dir.x * speed, v.y, dir.z * speed);
        } else {
            p.setDeltaMovement(v.x * 0.35, v.y, v.z * 0.35);
        }
    }

    /**
     * The axe whirlwind: carried forward like a top while it turns, the way the camera looks now (author,
     * 2026-10-30: "he looks and goes only forward; in first person looking the other way with the mouse,
     * he turns there; A, S, D do nothing"), eased in over the first turn's quarter; its ending slows to a stop.
     */
    private static void whirl(AbstractClientPlayer p, Move move, int t) {
        Vec3 v = p.getDeltaMovement();
        float speed = 0f;
        int from = dev.mcreload.moves.Whirl.SPIN_FROM, stop = dev.mcreload.moves.Whirl.STOP;
        if (move == Move.WHIRL_END) {
            speed = dev.mcreload.moves.Whirl.SPEED * Math.max(0f, 1f - (t + 1) / 3f);
        } else if (t >= from && t < stop + 3) {
            speed = dev.mcreload.moves.Whirl.SPEED * Math.min(1f, Math.min(t - from + 1, stop + 3 - t) / 3f);
        }
        Vec3 dir = Move.WHIRL.direction(p.getYRot());
        p.setDeltaMovement(dir.x * speed, v.y, dir.z * speed);
    }

    /**
     * The attack out of a slide: the half turn clockwise (the camera with it: the player ends facing
     * the other way, author) over Strikes.SLIDE_TURN ticks, and the push of a tapped slide on the way
     * they slid, now backwards.
     */
    private static void slideAttack(AbstractClientPlayer p, Stamina s, int t) {
        int turn = dev.mcreload.moves.Strikes.SLIDE_TURN;
        if (t < turn) {
            float f = (t + 1) / (float) turn;
            float yaw = s.attackYaw + 180f * f * f * (3 - 2 * f);
            p.setYRot(yaw);
            p.setYHeadRot(yaw);
            p.yBodyRot = yaw;
        }
        float speed = Move.slideSpeed(MovesConfig.slideBlocks(), s.slid);
        s.slid += speed;
        Vec3 dir = move(Move.SLIDE_ATTACK);
        p.setDeltaMovement(dir.x * speed, p.getDeltaMovement().y, dir.z * speed);
    }

    /**
     * During the volley the bow is not drawn again nor swung: the volley plays out first. In the
     * flow and the Legolas stance the left button is theirs (BowStance), it does not swing the bow.
     */
    private static void interaction(net.minecraftforge.client.event.InputEvent.InteractionKeyMappingTriggered event) {
        LocalPlayer p = Minecraft.getInstance().player;
        Stamina s = p == null ? null : Stamina.of(p);
        boolean striking = s != null && s.move != null && s.move.strike() && s.moveTick < s.move.recoverFrom();
        if (p != null && (s.move == Move.BARRAGE || striking || (event.isAttack() && BowStance.active(p)))) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    /** During a move the keys do not walk (the move does); a jump still breaks off a slide. */
    private static void movementInput(MovementInputUpdateEvent event) {
        BowStance.movementInput(event.getEntity(), event.getInput());
        Stamina s = Stamina.of(event.getEntity());
        if (s.move == null || s.moveTick >= s.move.length - 3) return;
        if (s.move == Move.SLIDE && s.moveTick < Move.PUSH_TICK) return;
        if (s.move == Move.FALL_TUCK || s.move == Move.PLUNGE_FALL) return;     // in the air: the little steering vanilla gives stays
        if (s.move == Move.SWORD_SPIN || s.move == Move.SPIN_END) return;     // the sword's turns walk with the keys
        Input in = event.getInput();
        in.forwardImpulse = 0f;
        in.leftImpulse = 0f;
        in.up = in.down = in.left = in.right = false;
        if (s.move != Move.SLIDE) in.jumping = false;
    }

    // ------------------------------------------------------------------ sounds and dust, every player

    /** The move each player was doing last tick (to stop looping animations when it ends). */
    private static final java.util.Map<AbstractClientPlayer, Move> LAST = new java.util.WeakHashMap<>();
    /** How far each player's move got. */
    private static final java.util.Map<AbstractClientPlayer, Integer> LAST_TICK = new java.util.WeakHashMap<>();

    private static void cues(AbstractClientPlayer p, Stamina s) {
        Move move = s.move;
        // A move ended with none after it: the bow's moves may leave a pose held (on one knee after
        // the volley, side-on after a Legolas spin), or stop their looping animation.
        Move was = LAST.put(p, move);
        Integer reached = move != null ? LAST_TICK.put(p, s.moveTick) : LAST_TICK.remove(p);
        if (was != null && move == null) BowStance.moveEnded(p, was, reached == null ? 0 : reached);
        BowStance.tick(p);
        if (move == null) return;
        int t = s.moveTick;    // already counted on for this tick
        BlockPos below = BlockPos.containing(p.getX(), p.getY() - 0.2, p.getZ());
        BlockState ground = p.level().getBlockState(below);
        if (t == Move.PUSH_TICK) sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.25f, 1.6f);
        if (move == Move.ROLL_KICK) {
            if (t == Kick.RK_TAKEOFF || t == Kick.RK_LAND) step(p, ground, 0.7f);
            if (t == Kick.RK_TAKEOFF + 2) sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.4f, 0.7f);
            if (t == Kick.RK_ROLL_FROM + 3) step(p, ground, 0.5f);
        } else if (move == Move.PLUNGE) {
            if (t == 1) step(p, ground, 0.9f);
        } else if (move == Move.WHIRL) {
            if (t >= dev.mcreload.moves.Whirl.SPIN_FROM && t < dev.mcreload.moves.Whirl.STOP && t % 5 == 0) step(p, ground, 0.3f);
        } else if (move == Move.SWORD_SPIN) {
            if (t == dev.mcreload.moves.Spin.SPIN_FROM) step(p, ground, 0.4f);
        } else if (move == Move.HOP || move == Move.KICK) {
            if (t == Move.HOP_LAND || (move == Move.KICK && t == Kick.LAND)) step(p, ground, 0.7f);
            if (move == Move.KICK && t == Kick.SPIN) sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.4f, 0.8f);
        } else if (move == Move.DIVE) {
            if (t == Move.DIVE_TOUCH || t == Move.DIVE_TOUCH + 5) step(p, ground, 0.7f);
        } else if (move == Move.DASH) {
            // Footfalls of the run-in, the lunge's front foot planting.
            if (t == 3 || t == 6 || t == 9) step(p, ground, t == 9 ? 0.8f : 0.4f);
        } else if (move == Move.DODGE_LEFT || move == Move.DODGE_RIGHT) {
            if (t == 6) step(p, ground, 0.5f);
        } else if (move.rolls()) {
            if (t == 5 || t == 11) step(p, ground, 0.6f);
        } else if (move == Move.SLIDE && t >= 3 && t <= 16) {
            if (t % 3 == 0) step(p, ground, 0.35f);
            if (!ground.isAir()) {
                for (int i = 0; i < 2; i++) {
                    p.level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, ground),
                            p.getX() + (p.getRandom().nextDouble() - 0.5) * 0.6, p.getY() + 0.05,
                            p.getZ() + (p.getRandom().nextDouble() - 0.5) * 0.6, 0, 0.05, 0);
                }
            }
        }
    }

    private static void step(Player p, BlockState ground, float volume) {
        if (ground.isAir()) return;
        SoundType type = ground.getSoundType();
        sound(p, type.getStepSound(), type.getVolume() * volume, type.getPitch());
    }

    private static void sound(Player p, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        p.level().playLocalSound(p.getX(), p.getY(), p.getZ(), sound, SoundSource.PLAYERS, volume,
                pitch * Mth.randomBetween(p.getRandom(), 0.95f, 1.05f), false);
    }

    // ------------------------------------------------------------------ from the server

    /**
     * Another player started (or, with null, stopped) a move; {@code extra}: which way the wall of
     * their ledge is. Our own player only hears of the moves the server starts on them (pulled up).
     */
    public static void remoteMove(int entityId, Move move, int extra) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity e = mc.level.getEntity(entityId);
        if (!(e instanceof AbstractClientPlayer p)) return;
        Stamina s = Stamina.of(p);
        if (p == mc.player) {
            if (move == null || !move.serverStarted()) return;
            Move was = s.move;
            s.start(p, move, false);
            moveYaw = p.getYRot();
            if (extra >= 0) s.ledgeDir = extra;
            LedgeInput.pulled(mc.player, s);
            MoveAnimator.play(p, move, was);
            return;
        }
        if (move == null) {
            s.end(p);
            MoveAnimator.stop(p, 3);
        } else {
            Move was = s.move;
            s.start(p, move, false);
            if (extra >= 0) s.ledgeDir = extra;
            MoveAnimator.play(p, move, was);
        }
    }

    /** The server refused the move this client started: undo it. */
    public static void denied(Move move) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        Stamina s = Stamina.of(p);
        if (s.move != move) return;
        s.end(p);
        MoveAnimator.stop(p, 3);
        KinetrixMoves.LOG.debug("[kinetrix_moves] server refused {}", move);
    }

    public static void bars(MovesNet.Bars m) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        Stamina s = Stamina.of(p);
        if (m.shields() < s.shields) shieldFlash = 10;
        s.energy = m.energy();
        s.shields = m.shields();
        s.freeTicks = m.freeTicks();
        s.freeTotal = m.freeTotal();
        // the cooldowns as the server counts them (the tray; after joining the client knows none of its own)
        s.dashCooldown = m.cooldowns()[0];
        s.whirlCooldown = m.cooldowns()[1];
        s.spinCooldown = m.cooldowns()[2];
        s.slideAttackCooldown = m.cooldowns()[3];
        maxEnergy = m.maxEnergy();
        maxShields = m.maxShields();
    }

    private MovesClient() {}
}
