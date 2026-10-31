package dev.mcreload.moves.client;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonConfiguration;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.AnimationStack;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Pair;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import dev.mcreload.moves.Bow;
import dev.mcreload.moves.HudConfig;
import dev.mcreload.moves.KinetrixMoves;
import dev.mcreload.moves.Move;
import dev.mcreload.moves.Stamina;
import dev.mcreload.moves.Strikes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Both arms seen in first person in every attack (author, 2026-10-31, video 14:59: "just the arms
 * should be seen in attacks, as when you catch a ledge: both arms are seen, and so everywhere"; the
 * camera behind the player only for the volley at the marked and the kicks' cutscenes).
 *
 * <p><b>The mod's attacks.</b> In first person playerAnimator draws the player's own model from the
 * eyes, only its arms and what they hold. In the first person pass ({@link #bodyRotation},
 * {@link #bodyPosition}) the whole-body turn is left out and the model's eyes are put at the camera,
 * so the arms stay in front of it, on a knee too. Not G's spins with a sword or an axe: there the
 * whole body turns and the blade goes round the camera, as before 0.8.0 (author, 2026-10-31, 15:56:
 * "as in the old versions, it was perfect"; folded into a sweep across the view it jerked to and fro).</p>
 *
 * <p><b>Everyone else's attacks.</b> Better Combat's strikes are drawn in first person with the weapon
 * only (its arms setting is off by default): the layer here ({@link Layer}) sits over every other mod's
 * layer and, while one of them draws the model in first person, keeps that and shows both arms; also
 * while a sword, an axe or a bow swings at something (not while breaking a block). Not the drawn bow:
 * vanilla's own draw (from the eyes the model's bow lies askew across the view, author, 15:59). It
 * changes nothing else: every transform passes through. Switched off in the menu ("Arms in first person").</p>
 */
public final class FirstPersonArms {
    /** Over every other mod's layer, under Kinetrix's harmonizer (100 000), which draws nothing in first person. */
    public static final int PRIORITY = 90_000;
    private static final ResourceLocation LAYER = new ResourceLocation(KinetrixMoves.MODID, "first_person_arms");
    private static final FirstPersonConfiguration ARMS = new FirstPersonConfiguration(true, true, true, true);
    /** Ticks a layer below may keep the model drawn from the eyes with no move, guard or item in use (a strike lasts less). */
    private static final int STALE = 40;
    /** Other attacks may still turn the body this far (a twist into a blow), no further. */
    private static final float TWIST = 40f * Mth.DEG_TO_RAD;

    static void init() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, PRIORITY, Layer::new);
    }

    /** The local player seen from their own eyes now (the first person pass of their model). */
    static boolean ownFirstPerson(AbstractClientPlayer p) {
        return FirstPersonMode.isFirstPersonPass() && p == Minecraft.getInstance().getCameraEntity();
    }

    // ------------------------------------------------------------------ the mod's attacks

    /** The mod's move is an attack whose arms are drawn in first person, its whole-body turn left out. */
    private static boolean attack(Move m) {
        return m == Move.DASH || m.finisher() || m == Move.SLIDE_ATTACK || m == Move.PLUNGE || m == Move.PLUNGE_FALL
                || m.turning() || bow(m);
    }

    /** A move with the bow drawn or about to shoot: the bow stays ahead. */
    private static boolean bow(Move m) {
        return m.volley() || m.legolas() || m.marked() || m == Move.SLIDE_SHOT || m == Move.SLIDE_SHOT_END;
    }

    /**
     * The mod's move playing on {@code p} is seen from their own eyes with its whole-body turn left out
     * and the model put where those eyes are (a bow pose held between moves counts: on the knee, side-on
     * after a Legolas dodge). The ledge, the vault, the rolls, the sword's guard stay as they are: the
     * arms are ahead in them already.
     */
    static boolean applies(AbstractClientPlayer p) {
        if (!HudConfig.firstPersonArms()) return false;
        Move m = Stamina.of(p).move;
        // G's spins with a sword or an axe: the whole body turns, the arms round the camera, as before 0.8.0
        // (author, 2026-10-31, 15:56: "as it was in the old versions, it was perfect")
        return m == null ? Bow.isBow(p.getMainHandItem()) : attack(m) && !m.turning();
    }

    /**
     * The whole body's turn ({@code v}, playerAnimator's "body" rotation, radians) as the first person pass
     * draws it: no turn round, a blade's spin a sweep across the view, a twist into a blow kept up to TWIST;
     * bending and leaning as they are.
     */
    static Vec3f bodyRotation(AbstractClientPlayer p, Vec3f v) {
        Move m = Stamina.of(p).move;
        float yaw = Mth.wrapDegrees(v.getY() * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
        if (m == null || bow(m)) {
            yaw = 0f;     // the bow's spins (onto the knee, the Legolas dodges): the drawn bow stays ahead
        } else {
            yaw = Mth.clamp(yaw, -TWIST, TWIST);
        }
        return new Vec3f(v.getX(), yaw, v.getZ());
    }

    /** Where the model's eyes are on a standing player (blocks over the feet), and playerAnimator's pivot for the body's turn. */
    private static final float EYES = 1.62f, PIVOT = 0.7f;

    /**
     * The whole body's place in the first person pass: the model moved so its eyes, after the body's turn
     * {@code rot} (pitch, yaw, roll as playerAnimator applies them: about z, then y, then x, round a point
     * PIVOT over the feet), are where the camera is. Down on a knee or bent into a stab the arms are then
     * where those eyes would see them, not lower: the camera does not go down with the animation (and in a
     * slide, low, it goes lower than the animation does).
     */
    static Vec3f bodyPosition(AbstractClientPlayer p, Vec3f rot) {
        float a = rot.getX(), b = rot.getY(), c = rot.getZ(), h = EYES - PIVOT;
        // R = Rz(c) Ry(b) Rx(a) applied to (0, h, 0)
        float y1 = h * Mth.cos(a), z1 = h * Mth.sin(a);
        float x2 = z1 * Mth.sin(b), z2 = z1 * Mth.cos(b);
        float x3 = x2 * Mth.cos(c) - y1 * Mth.sin(c), y3 = x2 * Mth.sin(c) + y1 * Mth.cos(c);
        return new Vec3f(-x3, p.getEyeHeight() - PIVOT - y3, -z2);
    }

    // ------------------------------------------------------------------ the arms layer

    /** The other layers of a player's stack (playerAnimator keeps them private). */
    private static final Field LAYERS;

    static {
        Field f = null;
        try {
            f = AnimationStack.class.getDeclaredField("layers");
            f.setAccessible(true);
        } catch (ReflectiveOperationException | RuntimeException e) {
            KinetrixMoves.LOG.warn("[kinetrix_moves] cannot see the animation layers: other mods' attacks keep their own first person look", e);
        }
        LAYERS = f;
    }

    /** A pass-through layer that, while the player's own model is drawn in first person, shows both arms. */
    private static final class Layer implements IAnimation {
        private final AbstractClientPlayer player;
        private AnimationStack stack;
        private boolean asking;

        Layer(AbstractClientPlayer player) {
            this.player = player;
        }

        @Override
        public boolean isActive() {
            return mode() != FirstPersonMode.NONE;
        }

        @Override
        public FirstPersonMode getFirstPersonMode(float tickDelta) {
            return mode();
        }

        @Override
        public FirstPersonConfiguration getFirstPersonConfiguration(float tickDelta) {
            return ARMS;
        }

        @Override
        public Vec3f get3DTransform(String part, TransformType type, float tickDelta, Vec3f value0) {
            return value0;
        }

        @Override
        public void setupAnim(float tickDelta) {
        }

        /** THIRD_PERSON_MODEL when the arms are to be seen in first person now, else NONE. */
        private FirstPersonMode mode() {
            Minecraft mc = Minecraft.getInstance();
            if (asking || player != mc.player || !HudConfig.firstPersonArms() || !mc.options.getCameraType().isFirstPerson()) {
                return FirstPersonMode.NONE;
            }
            // a sword, an axe or a bow swung at something (not breaking a block). Not the drawn bow: from the eyes
            // the model's bow lies across the view askew (author, 2026-10-31, 15:59), vanilla's own draw is straight
            boolean weapon = Strikes.finisherWeapon(player) || Bow.isBow(player.getMainHandItem());
            if (weapon && player.swinging && (mc.gameMode == null || !mc.gameMode.isDestroying())) {
                return FirstPersonMode.THIRD_PERSON_MODEL;
            }
            if (below() != FirstPersonMode.THIRD_PERSON_MODEL) {
                drawnSince = -1;
                return FirstPersonMode.NONE;
            }
            // A layer that keeps the model drawn from the eyes with nothing going on (a frozen strike of
            // another mod's, author, 2026-10-31, 20:23: "the arms stop showing until I press F5"): after
            // STALE ticks vanilla's own hands again (this layer is the one the stack asks first).
            if (drawnSince < 0) drawnSince = player.tickCount;
            Stamina s = Stamina.of(player);
            boolean busy = s.move != null || s.guard != dev.mcreload.moves.Guard.Kind.NONE || player.isUsingItem();
            if (busy) drawnSince = player.tickCount;
            return player.tickCount - drawnSince > STALE ? FirstPersonMode.VANILLA : FirstPersonMode.THIRD_PERSON_MODEL;
        }

        /** Since when (player ticks) a layer below has drawn the model from the eyes with nothing of ours going on; -1: none does. */
        private int drawnSince = -1;

        /** What the layers under this one draw in first person (Better Combat's strike, the mod's own moves). */
        @SuppressWarnings("unchecked")
        private FirstPersonMode below() {
            if (LAYERS == null) return FirstPersonMode.NONE;
            asking = true;
            try {
                if (stack == null) stack = PlayerAnimationAccess.getPlayerAnimLayer(player);
                List<Pair<Integer, IAnimation>> layers = (List<Pair<Integer, IAnimation>>) LAYERS.get(stack);
                for (int i = layers.size() - 1; i >= 0; i--) {
                    IAnimation a = layers.get(i).getRight();
                    if (a == this || layers.get(i).getLeft() >= PRIORITY || !a.isActive()) continue;
                    FirstPersonMode m = a.getFirstPersonMode(0f);
                    if (m != FirstPersonMode.NONE) return m;
                }
                return FirstPersonMode.NONE;
            } catch (ReflectiveOperationException | RuntimeException e) {
                return FirstPersonMode.NONE;
            } finally {
                asking = false;
            }
        }
    }

    private FirstPersonArms() {}
}
