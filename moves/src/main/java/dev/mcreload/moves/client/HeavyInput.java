package dev.mcreload.moves.client;

import dev.mcreload.moves.Chain;
import dev.mcreload.moves.HeavyMoves;
import dev.mcreload.moves.MovesConfig;
import dev.mcreload.moves.Move;
import dev.mcreload.moves.Skill;
import dev.mcreload.moves.Stamina;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.common.MinecraftForge;

/**
 * The moves of 2026-11-20, night, on the player's side (HeavyMoves on the server): what starts them and how they carry
 * the player (the slam's jump, the charge's run, the flying knee's arc, the slide on the back, the rain's flight).
 */
public final class HeavyInput {
    private static Move was;
    private static Vec3 from;
    private static double hoverY;

    static void init() {
        MinecraftForge.EVENT_BUS.addListener(HeavyInput::slowWalk);
        MinecraftForge.EVENT_BUS.addListener(HeavyInput::useKey);
    }

    // ------------------------------------------------------------------ starting them

    /** The weak mob in the look a move is made at: within {@code reach} and {@code half} degrees, the nearest. */
    private static LivingEntity inLook(LocalPlayer p, double reach, double half, java.util.function.Predicate<Entity> ok) {
        Vec3 look = p.getViewVector(1f);
        look = new Vec3(look.x, 0, look.z).normalize();
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(reach + 1))) {
            if (e == p || !ok.test(e)) continue;
            Vec3 to = e.position().subtract(p.position());
            double d = to.length();
            Vec3 flat = new Vec3(to.x, 0, to.z);
            if (d > reach || (flat.lengthSqr() > 1.0E-4 && Math.toDegrees(Math.acos(Mth.clamp(look.dot(flat.normalize()), -1, 1))) > half)) continue;
            if (d < bestD) {
                best = e;
                bestD = d;
            }
        }
        return best;
    }

    /** The flying knee (Ctrl + W + Space + R with a sword): at the weak one in the look. */
    static boolean knee(LocalPlayer p, Stamina s) {
        LivingEntity mob = inLook(p, 8.5, 35, e -> HeavyMoves.kneeTarget(p, e));
        return mob != null && MovesClient.start(p, s, Move.SW_KNEE, mob.getId());
    }

    /** The crossbow's living shield (its key): caught, or let go if one is held. */
    static void grab(LocalPlayer p, Stamina s) {
        if (s.move == Move.XB_HOLD || s.move == Move.XB_SHOT || s.move == Move.XB_GRAB) {
            MovesClient.cancel(p, s, 3);
            return;
        }
        LivingEntity mob = inLook(p, 3.5, 50, e -> HeavyMoves.grabTarget(p, e));
        if (mob == null) {
            p.displayClientMessage(net.minecraft.network.chat.Component.translatable("kinetrix_combat.xbow_hostage.none"), true);
            return;
        }
        MovesClient.start(p, s, Move.XB_GRAB, mob.getId());
    }

    /** The axe's chain (Ctrl + W + Space + LMB with an axe): at the weak one in front, if another is near it. */
    static boolean axeChain(LocalPlayer p, Stamina s) {
        LivingEntity mob = inLook(p, Chain.FIRST_REACH, 50, e -> e instanceof LivingEntity le && Chain.weak(le) && dev.mcreload.moves.Marks.seen(p, le));
        if (mob == null || !Chain.enough(p)) return false;
        return MovesClient.start(p, s, Move.AXC_OPEN, mob.getId());
    }

    /** Its wait on this side at once (the server's comes with the next bars). */
    static void started(Stamina s, Move m) {
        switch (m) {
            case AX_SLAM -> s.slamCooldown = s.cooled(MovesConfig.slamCooldownTicks());
            case AX_HEAVY -> s.heavyCooldown = s.cooled(MovesConfig.heavyCooldownTicks());
            case AXC_OPEN -> s.axeChainCooldown = s.cooled(MovesConfig.axeChainCooldownTicks());
            case SW_CHARGE -> s.chargeCooldown = s.cooled(MovesConfig.chargeCooldownTicks());
            case SW_KNEE -> s.kneeCooldown = s.cooled(MovesConfig.kneeCooldownTicks());
            case XB_GRAB -> s.holdCooldown = s.cooled(MovesConfig.holdCooldownTicks());
            case XB_BACKSLIDE -> s.backSlideCooldown = s.cooled(MovesConfig.backSlideCooldownTicks());
            case BW_RAIN -> s.rainCooldown = s.cooled(MovesConfig.rainCooldownTicks());
            default -> {
            }
        }
    }

    /** Holding the living shield: the right button shoots (no crossbow loading of vanilla's). */
    private static void useKey(InputEvent.InteractionKeyMappingTriggered event) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || !event.isUseItem()) return;
        Stamina s = Stamina.of(p);
        if (s.move != Move.XB_HOLD && s.move != Move.XB_SHOT && s.move != Move.XB_GRAB) return;
        event.setCanceled(true);
        event.setSwingHand(false);
        if (s.move == Move.XB_HOLD) MovesClient.start(p, s, Move.XB_SHOT, s.target);
    }

    /** Holding the living shield, the player walks slowly. */
    private static void slowWalk(MovementInputUpdateEvent event) {
        Stamina s = Stamina.of(event.getEntity());
        if (s.move != Move.XB_HOLD && s.move != Move.XB_SHOT && s.move != Move.XB_GRAB) return;
        Input in = event.getInput();
        in.forwardImpulse *= 0.45f;
        in.leftImpulse *= 0.45f;
        in.jumping = false;
    }

    // ------------------------------------------------------------------ carrying the player

    /** Every client tick after moving: the moves that carry the player. */
    static void tick(LocalPlayer p, Stamina s) {
        Move m = s.move;
        if (m != was) {
            from = p.position();
            hoverY = p.getY();
            was = m;
        }
        if (m == null) return;
        int t = s.moveTick;
        Vec3 look = p.getViewVector(1f);
        look = new Vec3(look.x, 0, look.z).normalize();
        Vec3 v = p.getDeltaMovement();
        switch (m) {
            case AX_SLAM -> {
                p.fallDistance = 0f;
                if (t == 2) p.setDeltaMovement(look.x * 0.1, 0.6, look.z * 0.1);
                else if (t >= HeavyMoves.SLAM_HIT - 3 && t < HeavyMoves.SLAM_HIT && !p.onGround()) p.setDeltaMovement(0, -1.2, 0);
            }
            case SW_CHARGE, SW_SKEWER -> {
                double speed = MovesConfig.chargeSpeed();
                p.setDeltaMovement(look.x * speed, v.y, look.z * speed);
                p.setSprinting(true);
            }
            case SW_FLING -> {
                if (t < HeavyMoves.FLING_HIT) p.setDeltaMovement(look.x * 0.3, v.y, look.z * 0.3);
            }
            case SW_KNEE -> {
                p.fallDistance = 0f;
                Entity e = p.level().getEntity(s.target);
                if (t <= HeavyMoves.KNEE_HIT && e != null && from != null) {
                    // an arc up to its head, the knee first
                    Vec3 toward = from.subtract(e.position());
                    toward = new Vec3(toward.x, 0, toward.z);
                    toward = toward.lengthSqr() < 1.0E-4 ? look.scale(-1) : toward.normalize();
                    Vec3 top = e.position().add(toward.scale(0.7)).add(0, Math.max(0.3, e.getBbHeight() - 1.4), 0);
                    float f = t / (float) HeavyMoves.KNEE_HIT;
                    p.setPos(from.lerp(top, f).add(0, 0.8 * Math.sin(Math.PI * f), 0));
                    p.setDeltaMovement(Vec3.ZERO);
                    hoverY = p.getY();
                } else if (t <= HeavyMoves.KNEE_HIT + 8) {
                    // hanging there a moment as time crawls
                    p.setPos(p.getX(), hoverY, p.getZ());
                    p.setDeltaMovement(Vec3.ZERO);
                }
            }
            case XB_BACKSLIDE -> {
                if (t >= HeavyMoves.SLIDE_FROM && t <= HeavyMoves.SLIDE_TO) {
                    double speed = 0.8 * Math.pow(0.94, t - HeavyMoves.SLIDE_FROM);
                    if (t == HeavyMoves.SLIDE_FROM) from = look;
                    Vec3 dir = from == null ? look : from;
                    p.setDeltaMovement(dir.x * speed, v.y, dir.z * speed);
                }
            }
            case XB_BELLY -> {
                if (t < 12) p.setDeltaMovement(0, v.y, 0);
                else if (t == 13 && p.onGround()) p.setDeltaMovement(look.x * 0.1, 0.4, look.z * 0.1);
            }
            case BW_RAIN -> {
                p.fallDistance = 0f;
                if (t == 6) p.setDeltaMovement(0, 0.62, 0);
                else if (t == 12) hoverY = p.getY();
                else if (t > 12 && t <= 40) {
                    p.setPos(p.getX(), hoverY, p.getZ());
                    p.setDeltaMovement(Vec3.ZERO);
                }
            }
            default -> {
            }
        }
    }

    /** The skills of these from the wheel or their own keys. */
    static boolean use(LocalPlayer p, Stamina s, Skill k) {
        return switch (k) {
            case AXE_SLAM -> MovesClient.start(p, s, Move.AX_SLAM, -1);
            case AXE_HEAVY -> MovesClient.start(p, s, Move.AX_HEAVY, -1);
            case AXE_CHAIN -> axeChain(p, s);
            case SWORD_CHARGE -> MovesClient.start(p, s, Move.SW_CHARGE, -1);
            case SWORD_KNEE -> knee(p, s);
            case XBOW_HOSTAGE -> {
                grab(p, s);
                yield true;
            }
            case BACK_SLIDE -> MovesClient.start(p, s, Move.XB_BACKSLIDE, -1);
            default -> false;
        };
    }

    private HeavyInput() {}
}
