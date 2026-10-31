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
 * eyes, only its arms and what they hold. The whole player's turn in an animation turns those arms
 * round the camera: in G's spins they are behind it half the time, out to the sides the rest. So in
 * the first person pass ({@link #body}) the whole-body turn is left out and the arms stay in front of
 * the camera; for the blades' spins (G with a sword or an axe, the arms out straight to the sides)
 * the turn is folded into a sweep instead: on every turn the blade crosses the view from the right
 * to the left, as it would pass in front of the eyes.</p>
 *
 * <p><b>Everyone else's attacks and the bow.</b> Better Combat's strikes are drawn in first person
 * with the weapon only (its arms setting is off by default): the layer here ({@link Layer}) sits over
 * every other mod's layer and, while one of them draws the model in first person, keeps that and
 * shows both arms. It also draws the model's arms (vanilla's own poses) while the bow is drawn
 * and while a sword, an axe or a bow swings at something (not while breaking a block). It changes
 * nothing else: every transform passes through. Switched off in the menu ("Arms in first person").</p>
 */
public final class FirstPersonArms {
    /** Over every other mod's layer, under Kinetrix's harmonizer (100 000), which draws nothing in first person. */
    public static final int PRIORITY = 90_000;
    private static final ResourceLocation LAYER = new ResourceLocation(KinetrixMoves.MODID, "first_person_arms");
    private static final FirstPersonConfiguration ARMS = new FirstPersonConfiguration(true, true, true, true);
    /** Half the arc the blade sweeps across the view in G's spins (radians; the view is about 100 degrees wide). */
    private static final float SWEEP = 45f * Mth.DEG_TO_RAD;
    /** The drawn bow from the eyes: the arms lowered by DRAW_LOWER, the bow turned by DRAW_TURN about its grip (radians). */
    private static final float DRAW_LOWER = 10f * Mth.DEG_TO_RAD, DRAW_TURN = 60f * Mth.DEG_TO_RAD;
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
        return m == null ? Bow.isBow(p.getMainHandItem()) : attack(m);
    }

    /**
     * The whole body's turn ({@code v}, playerAnimator's "body" rotation, radians) as the first person pass
     * draws it: no turn round, a blade's spin a sweep across the view, a twist into a blow kept up to TWIST;
     * bending and leaning as they are.
     */
    static Vec3f bodyRotation(AbstractClientPlayer p, Vec3f v) {
        Move m = Stamina.of(p).move;
        float yaw = Mth.wrapDegrees(v.getY() * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
        if (m != null && m.turning()) {
            // the right arm points ahead at a quarter turn to the left; each turn sweeps it from the right to the left
            float half = Mth.HALF_PI;
            yaw = half + Mth.wrapDegrees((v.getY() - half) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD * (SWEEP / Mth.PI);
        } else if (m == null || bow(m)) {
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
            if (type != TransformType.ROTATION || !HudConfig.firstPersonArms() || !Bow.isBow(player.getMainHandItem())
                    || !ownFirstPerson(player)) return value0;
            // The bow from the eyes is turned to show its face, as vanilla's own first person view shows it
            // (edge-on it is a dark rod across the view); drawn in vanilla's pose (no move of the mod's, or
            // held on the knee) the arms go a little lower too.
            Move m = Stamina.of(player).move;
            boolean vanillaArms = m == null || (m == Move.SLIDE_SHOT && Stamina.of(player).moveTick >= Move.SLIDE_SHOT_SPIN);
            if (!Bow.drawing(player) && (m == null || !bow(m))) return value0;
            return switch (part) {
                case "rightArm", "leftArm" -> vanillaArms && Bow.drawing(player)
                        ? new Vec3f(value0.getX() + DRAW_LOWER, value0.getY(), value0.getZ()) : value0;
                case "rightItem" -> new Vec3f(value0.getX(), value0.getY() + DRAW_TURN, value0.getZ());
                default -> value0;
            };
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
            // the bow drawn; a sword, an axe or a bow swung at something (not breaking a block)
            boolean weapon = Strikes.finisherWeapon(player) || Bow.isBow(player.getMainHandItem());
            if (Bow.drawing(player) || (weapon && player.swinging && (mc.gameMode == null || !mc.gameMode.isDestroying()))) {
                return FirstPersonMode.THIRD_PERSON_MODEL;
            }
            return below() == FirstPersonMode.THIRD_PERSON_MODEL ? FirstPersonMode.THIRD_PERSON_MODEL : FirstPersonMode.NONE;
        }

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
