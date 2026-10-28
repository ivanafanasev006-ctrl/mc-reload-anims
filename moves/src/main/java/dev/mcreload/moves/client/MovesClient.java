package dev.mcreload.moves.client;

import com.mojang.blaze3d.platform.InputConstants;
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
 * own.</p>
 */
public final class MovesClient {
    public static final KeyMapping DODGE = new KeyMapping("key.kinetrix_moves.dodge", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.kinetrix_moves");
    /** Only registered without Kinetrix; with it the slide uses Kinetrix's crawl key. */
    private static final KeyMapping OWN_SLIDE = new KeyMapping("key.kinetrix_moves.slide", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, "key.categories.kinetrix_moves");
    private static final boolean KINETRIX = ModList.get().isLoaded("kinetrix");

    /** R pressed without a direction: waits this many more ticks for A, D, S or W. */
    private static int dodgeWait;
    private static boolean slideWasDown, jumpWasDown, attackWasDown;
    /** Facing at the start of the local move: the push keeps this direction. */
    private static float moveYaw;
    /** The local move's push direction when it is not the move's own side (a bow dodge: the way the keys point), else null. */
    private static Vec3 pushDir;
    /** Ticks the slide has been off the ground. */
    private static int airTicks;
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
        MinecraftForge.EVENT_BUS.addListener(MovesSettingsScreen::registerCommand);
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new MovesSettingsScreen(parent)));
        GuardInput.init();
        StoopFx.init();
                if (MovesSelfTest.enabled()) MovesSelfTest.init();
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(DODGE);
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
        boolean dodgePressed = false;
        while (DODGE.consumeClick()) dodgePressed = true;
        boolean slideDown = held(mc, slideKey(mc));
        boolean slidePressed = slideDown && !slideWasDown;
        slideWasDown = slideDown;
        boolean jumpDown = mc.options.keyJump.isDown(), attackDown = mc.options.keyAttack.isDown();
        boolean jumpPressed = jumpDown && !jumpWasDown, attackPressed = attackDown && !attackWasDown;
        jumpWasDown = jumpDown;
        attackWasDown = attackDown;
        if (p == null || mc.screen != null || !serverHasMod()) {
            dodgeWait = 0;
            StrikeInput.reset();
            return;
        }
        Stamina s = Stamina.of(p);

        // ---- the slide: sprint + C. While a slide is possible Kinetrix must not take C for its crawl.
        if (s.move == Move.SLIDE) {
            KinetrixBridge.blockCrawl(250);
            if (s.moveTick <= Move.SLIDE_CRAWL_TICK) s.held &= slideDown;
            airTicks = p.onGround() ? 0 : airTicks + 1;
            if (jumpPressed || attackPressed || airTicks > 4) {
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
        } else if (p.isSprinting() && p.onGround() && MoveServer.allowed(p, s, Move.SLIDE)) {
            KinetrixBridge.blockCrawl(250);
            if (slidePressed) start(p, s, Move.SLIDE, true, -1);
        }

        // ---- once a move's push is over, running, jumping or striking ends it: a dodge, a roll, a
        // slide or the hop does not hold the player in place while its animation plays out
        if (s.move != null && recoverable(s) && (moving(mc) || jumpPressed || attackPressed)) cancel(p, s, 3);

        // ---- the sword moves of the left button: the finisher, the dash strike (they take the click)
        if (StrikeInput.tick(mc, p, s, attackDown, attackPressed)) attackPressed = false;

        // ---- the bow: the flow and the Legolas stance (their arrows, poses and dodges), then the
        // volley: a fully drawn bow and the left button (which in the flow is the flow's next step)
        BowStance.tickLocal(p, s);
        if (attackPressed && !BowStance.attackKey(p, s) && s.move == null && dev.mcreload.moves.Bow.volleyReady(p)) {
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
                // R + W with a mob of some size at point blank: vault over it instead of rolling.
                net.minecraft.world.entity.LivingEntity enemy = move == Move.DODGE_FRONT ? dev.mcreload.moves.Hop.target(p, true) : null;
                if (enemy != null) start(p, s, Move.HOP, false, enemy.getId());
                else start(p, s, move, false, -1);
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

    private static KeyMapping slideKey(Minecraft mc) {
        if (!KINETRIX) return OWN_SLIDE;
        for (KeyMapping k : mc.options.keyMappings) if ("key.kinetrix.crawl".equals(k.getName())) return k;
        return OWN_SLIDE;
    }

    /**
     * The key is held: through its binding, or read straight from the keyboard (vanilla binds C
     * too, and a key shared by two bindings may reach only one of them).
     */
    private static boolean held(Minecraft mc, KeyMapping km) {
        if (km.isDown()) return true;
        if (mc.screen != null || km.isUnbound()) return false;
        InputConstants.Key k = km.getKey();
        if (k.getType() != InputConstants.Type.KEYSYM || k.getValue() < 0) return false;
        return InputConstants.isKeyDown(mc.getWindow().getWindow(), k.getValue());
    }

    // ------------------------------------------------------------------ the local move

    /** Starts a bow move pushed along {@code dir} (null: the move's own side). Returns whether it started. */
    static boolean start(LocalPlayer p, Stamina s, Move move, Vec3 dir) {
        if (!start(p, s, move, false, -1)) return false;
        pushDir = dir;
        return true;
    }

    /** Starts a move made at a mob (the vault, the finisher). Returns whether it started. */
    static boolean start(LocalPlayer p, Stamina s, Move move, int target) {
        return start(p, s, move, false, target);
    }

    private static boolean start(LocalPlayer p, Stamina s, Move move, boolean held, int target) {
        if (!MoveServer.allowed(p, s, move)) {
            if (!s.canAfford(move, p)) flash = 12;
            return false;
        }
        s.spend(move.cost(), p);
        GuardInput.lowerLocal(p, s, false);     // the server lowers it too
        s.start(p, move, held);
        moveYaw = p.getYRot();
        pushDir = null;
        airTicks = 0;
        s.target = target;
        if (move == Move.DASH) s.dashCooldown = MovesConfig.dashCooldownTicks();
        MoveAnimator.play(p, move);
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
    private static float hopUp, hopGravity;

    /**
     * The vault: the run goes on through the crouch (stopping short of the mob), then a jump over
     * its back, as high as that back is as it stoops (Hop.back: a pig needs less than a zombie),
     * 13 ticks in the air whatever the height, landing {@link Move#HOP_BEYOND} blocks beyond it
     * and running on the way the player was going.
     */
    private static void hop(AbstractClientPlayer p, Stamina s, int t) {
        Vec3 v = p.getDeltaMovement();
        net.minecraft.world.entity.Entity enemy = p.level().getEntity(s.target);
        if (t < Move.HOP_TAKEOFF) {
            Vec3 dir = move(Move.HOP);
            double speed = Math.min(Math.max(v.horizontalDistance(), 0.15), 0.28);
            if (enemy != null && gap(p, enemy) < 0.35) speed = 0;
            p.setDeltaMovement(dir.x * speed, v.y, dir.z * speed);
            return;
        }
        int air = Move.HOP_LAND - Move.HOP_TAKEOFF;
        if (t == Move.HOP_TAKEOFF) {
            Vec3 over = enemy != null ? enemy.position() : p.position().add(move(Move.HOP).scale(1.5));
            Vec3 dir = new Vec3(over.x - p.getX(), 0, over.z - p.getZ());
            if (dir.lengthSqr() < 1.0E-4) dir = move(Move.HOP);
            Vec3 land = over.add(dir.normalize().scale(Move.HOP_BEYOND));
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
        } else if (t <= Move.HOP_LAND + 3) {
            // Landed: running on, the way the vault went.
            Vec3 dir = hopVelocity.lengthSqr() > 1.0E-6 ? hopVelocity.normalize() : move(Move.HOP);
            p.setDeltaMovement(dir.x * 0.28, v.y, dir.z * 0.28);
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
     * through it.
     */
    private static void dash(AbstractClientPlayer p, int t) {
        float[] run = dev.mcreload.moves.Strikes.DASH_RUN;
        Vec3 v = p.getDeltaMovement();
        float speed = t < run.length ? run[t] : 0f;
        net.minecraft.world.entity.LivingEntity ahead = dev.mcreload.moves.Strikes.dashTarget(p);
        if (ahead != null && gap(p, ahead) <= dev.mcreload.moves.Strikes.DASH_STOP) speed = 0f;
        Vec3 dir = Move.DASH.direction(p.getYRot());
        p.setDeltaMovement(dir.x * speed, v.y, dir.z * speed);
    }

    private static Vec3 move(Move m) {
        return m.direction(moveYaw);
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
        if (move == Move.HOP) {
            if (t == Move.HOP_LAND) step(p, ground, 0.7f);
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

    /** Another player started (or, with null, stopped) a move. */
    public static void remoteMove(int entityId, Move move) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity e = mc.level.getEntity(entityId);
        if (!(e instanceof AbstractClientPlayer p) || p == mc.player) return;
        Stamina s = Stamina.of(p);
        if (move == null) {
            s.end(p);
            MoveAnimator.stop(p, 3);
        } else {
            s.start(p, move, false);
            MoveAnimator.play(p, move);
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
        maxEnergy = m.maxEnergy();
        maxShields = m.maxShields();
    }

    private MovesClient() {}
}
