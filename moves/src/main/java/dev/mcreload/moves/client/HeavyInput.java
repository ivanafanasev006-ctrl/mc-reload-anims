package dev.mcreload.moves.client;

import dev.mcreload.moves.Chain;
import dev.mcreload.moves.HeavyMoves;
import dev.mcreload.moves.MovesConfig;
import dev.mcreload.moves.Move;
import dev.mcreload.moves.Skill;
import dev.mcreload.moves.Stamina;
import dev.mcreload.moves.Strikes;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.common.MinecraftForge;

/**
 * The moves of 2026-11-20 (made again 2026-11-23) on the player's side (HeavyMoves and BowPhantoms on the server): what
 * starts them and how they carry the player (the slam's jump, the charge's run and skid, the kick's leap and hang, the
 * slide on the back, the phantoms' places, the skid backwards).
 */
public final class HeavyInput {
    private static Move was;
    private static Vec3 from;
    private static double hoverY;
    /** The camera before the sword's charge (author, 2026-11-23: "third person from behind, NOT the 360"). */
    private static CameraType chargeCamera;

    static void init() {
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

    /** The sword's finisher (Ctrl + W + Space + R with a sword): at the weak one in the look. */
    static boolean knee(LocalPlayer p, Stamina s) {
        LivingEntity mob = inLook(p, 8.5, 35, e -> HeavyMoves.kneeTarget(p, e));
        return mob != null && MovesClient.start(p, s, Move.SW_KNEE, mob.getId());
    }

    /**
     * The living shield on V (a perk, author 2026-11-23): with a sword the wrist caught and the blade into its back
     * (V again kicks it off, as the right button), with a crossbow one held at the left hand (V again lets it go).
     */
    static void grab(LocalPlayer p, Stamina s) {
        if (s.move == Move.XB_HOLD || s.move == Move.XB_SHOT || s.move == Move.XB_GRAB) {
            MovesClient.cancel(p, s, 3);
            return;
        }
        if (s.move == Move.HOSTAGE_HOLD) {
            MovesClient.start(p, s, Move.HOSTAGE_KICK, s.target);
            return;
        }
        if (s.move == Move.GUARD_GRAB || s.move == Move.HOSTAGE_KICK) return;
        LivingEntity mob = inLook(p, 3.5, 50, e -> HeavyMoves.grabTarget(p, e));
        if (mob == null) {
            p.displayClientMessage(net.minecraft.network.chat.Component.translatable("kinetrix_combat.xbow_hostage.none"), true);
            return;
        }
        MovesClient.start(p, s, Strikes.sword(p) ? Move.GUARD_GRAB : Move.XB_GRAB, mob.getId());
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
            case XB_GRAB, GUARD_GRAB -> s.holdCooldown = s.cooled(MovesConfig.holdCooldownTicks());
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

    /** The sword's charge and what follows it: the camera behind him (third person), as it was after. */
    private static boolean charging(Move m) {
        return m == Move.SW_CHARGE || m == Move.SW_SKEWER || m == Move.SW_CUT || m == Move.SW_HALT;
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
        Minecraft mc = Minecraft.getInstance();
        if (charging(m) && chargeCamera == null) {
            chargeCamera = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        } else if (!charging(m) && chargeCamera != null) {
            if (mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK) mc.options.setCameraType(chargeCamera);
            chargeCamera = null;
        }
        if (m == null) return;
        int t = s.moveTick;
        Vec3 look = p.getViewVector(1f);
        look = new Vec3(look.x, 0, look.z).normalize();
        Vec3 v = p.getDeltaMovement();
        switch (m) {
            case AX_SLAM -> {
                // up off both feet, the knees pulled up; driven down onto the ground for the hit
                p.fallDistance = 0f;
                if (t == 4) p.setDeltaMovement(look.x * 0.12, 0.62, look.z * 0.12);
                else if (t >= HeavyMoves.SLAM_HIT - 4 && t < HeavyMoves.SLAM_HIT && !p.onGround()) p.setDeltaMovement(0, -1.4, 0);
            }
            case SW_CHARGE -> {
                double speed = MovesConfig.chargeSpeed();
                p.setDeltaMovement(look.x * speed, v.y, look.z * speed);
                p.setSprinting(true);
            }
            case SW_SKEWER -> {
                double speed = MovesConfig.chargeSpeed() * 0.75;
                p.setDeltaMovement(look.x * speed, v.y, look.z * speed);
            }
            case SW_CUT -> {
                if (t < HeavyMoves.CUT_HIT) p.setDeltaMovement(look.x * 0.25, v.y, look.z * 0.25);
                else p.setDeltaMovement(0, v.y, 0);
            }
            case SW_HALT -> {
                // the run dies in the time stop's skid, forward
                if (t == 0 || from == null || from.lengthSqr() > 2) from = look;
                double speed = t < 14 ? 0.6 * Math.pow(0.84, t) : 0;
                p.setDeltaMovement(from.x * speed, v.y, from.z * speed);
            }
            case SW_KNEE -> {
                p.fallDistance = 0f;
                Entity e = p.level().getEntity(s.target);
                if (t <= HeavyMoves.KNEE_ARRIVE && e != null && from != null) {
                    // an arc up beside its head
                    Vec3 toward = from.subtract(e.position());
                    toward = new Vec3(toward.x, 0, toward.z);
                    toward = toward.lengthSqr() < 1.0E-4 ? look.scale(-1) : toward.normalize();
                    Vec3 top = e.position().add(toward.scale(1.1)).add(0, Math.max(0.4, e.getBbHeight() - 1.2), 0);
                    float f = t / (float) HeavyMoves.KNEE_ARRIVE;
                    p.setPos(from.lerp(top, f).add(0, 0.9 * Math.sin(Math.PI * f), 0));
                    p.setDeltaMovement(Vec3.ZERO);
                    hoverY = p.getY();
                } else if (t <= HeavyMoves.KNEE_HIT + 4) {
                    // all stops: hanging there through the turn and the wind-up, the kick
                    p.setPos(p.getX(), hoverY, p.getZ());
                    p.setDeltaMovement(Vec3.ZERO);
                }
            }
            case XB_BACKSLIDE -> {
                // down onto the back and on, 5-7 blocks more (author), the way the slide went
                if (from == null || from.lengthSqr() > 1.5) {
                    Vec3 going = new Vec3(v.x, 0, v.z);
                    from = going.lengthSqr() > 0.01 ? going.normalize() : look;
                }
                if (t >= HeavyMoves.SLIDE_FROM && t <= HeavyMoves.SLIDE_TO && from != null && from.lengthSqr() < 1.5) {
                    double speed = 0.55 * Math.pow(0.92, t - HeavyMoves.SLIDE_FROM);
                    p.setDeltaMovement(from.x * speed, v.y, from.z * speed);
                }
            }
            case XB_BELLY -> {
                if (t < 16) p.setDeltaMovement(0, v.y, 0);
                else if (t == 18 && p.onGround()) p.setDeltaMovement(look.x * 0.1, 0.42, look.z * 0.1);
            }
            case BW_RAIN, BW_HIDDEN, BW_AIM, BW_JUMP, BW_FLIP, BW_TURN -> {
                // shaking where he stands; gone; each phantom where the server put it (in the air or on the ground)
                p.fallDistance = 0f;
                p.setPos(p.getX(), hoverY, p.getZ());
                p.setDeltaMovement(Vec3.ZERO);
            }
            case BW_FALLBACK -> {
                // from its back, falling back and down
                p.fallDistance = 0f;
                Entity e = p.level().getEntity(s.target);
                Vec3 away = e == null ? look.scale(-1) : new Vec3(p.getX() - e.getX(), 0, p.getZ() - e.getZ());
                away = away.lengthSqr() < 1.0E-4 ? look.scale(-1) : away.normalize();
                hoverY -= 0.06;
                p.setPos(p.getX() + away.x * 0.1, hoverY, p.getZ() + away.z * 0.1);
                p.setDeltaMovement(Vec3.ZERO);
            }
            case BW_BRAKE -> {
                // back where he began, skidding backwards (the way he faces, s.target, behind him)
                // one push, the ground's friction stops it (about 1.6 blocks, onto his place; as the time stop's skid)
                if (from == null || from.lengthSqr() > 2) {
                    float yaw = s.target * Mth.DEG_TO_RAD;
                    from = new Vec3(Mth.sin(yaw), 0, -Mth.cos(yaw));
                    p.setDeltaMovement(from.x * 0.75, v.y, from.z * 0.75);
                }
                p.fallDistance = 0f;
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
            case HOSTAGE -> {
                grab(p, s);
                yield true;
            }
            case BACK_SLIDE -> MovesClient.start(p, s, Move.XB_BACKSLIDE, -1);
            default -> false;
        };
    }

    private HeavyInput() {}
}
