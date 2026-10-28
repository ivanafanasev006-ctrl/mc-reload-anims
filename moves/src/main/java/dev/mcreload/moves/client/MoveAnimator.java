package dev.mcreload.moves.client;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonConfiguration;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.api.layered.modifier.SpeedModifier;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import dev.mcreload.moves.KinetrixMoves;
import dev.mcreload.moves.Move;
import dev.mcreload.moves.compat.KinetrixBridge;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Plays the moves on the players through playerAnimator, on a layer of our own. Kinetrix bends the
 * elbows, knees and waist from what this layer says; it sits above Better Combat's attacks (2000)
 * and below Kinetrix's harmonizer.
 */
public final class MoveAnimator {
    public static final ResourceLocation LAYER = new ResourceLocation(KinetrixMoves.MODID, "moves");
    public static final int PRIORITY = 2500;

    static void init() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, PRIORITY, player -> new MoveLayer());
    }

    /** The layer, with a speed control (the slide plays faster than in Blockbench). */
    private static final class MoveLayer extends ModifierLayer<IAnimation> {
        final SpeedModifier speed = new SpeedModifier(1f);

        MoveLayer() {
            addModifierBefore(speed);
        }
    }

    private static MoveLayer layer(AbstractClientPlayer p) {
        return PlayerAnimationAccess.getPlayerAssociatedData(p).get(LAYER) instanceof MoveLayer l ? l : null;
    }

    /** Starts the move's animation, blended in over two ticks. */
    public static void play(AbstractClientPlayer p, Move move) {
        MoveLayer layer = layer(p);
        KeyframeAnimation anim = PlayerAnimationRegistry.getAnimation(move.animation);
        if (layer == null) return;
        if (anim == null) {
            KinetrixMoves.LOG.warn("[kinetrix_moves] animation {} is missing", move.animation);
            return;
        }
        layer.speed.speed = move.animSpeed;
        // Bow moves may play a part of their animation (a half of bow_walk_around) and hold its last frame.
        int from = move.shot != null ? move.shot.animStart() : 0;
        KeyframeAnimationPlayer keyframes = new KeyframeAnimationPlayer(anim, from);
        if (move.firstPerson()) firstPerson(keyframes);
        IAnimation played = keyframes;
        if (move.shot != null && move.shot.holdAt() >= 0) played = new Segment(played, move.shot.holdAt() - from);
        layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(2, Ease.INOUTSINE), unwound(layer, anim, from, played));
        KinetrixBridge.wholeBody(p.getUUID(), move.length * 50L + 100L);
    }

    /**
     * The new animation turned by whole turns so it starts where the body faces now: a spin may end
     * at 360 degrees (or 412, a turn and a bit) and the next animation start at 0; the blend between
     * them would otherwise spin the player round once more in two ticks.
     */
    private static IAnimation unwound(MoveLayer layer, KeyframeAnimation anim, int from, IAnimation played) {
        IAnimation now = layer.getAnimation();
        if (now == null || !now.isActive()) return played;
        float current = now.get3DTransform("body", TransformType.ROTATION, 0f, new Vec3f(0f, 0f, 0f)).getY();
        float start = new KeyframeAnimationPlayer(anim, from).get3DTransform("body", TransformType.ROTATION, 0f, new Vec3f(0f, 0f, 0f)).getY();
        float turns = Math.round((current - start) / Mth.TWO_PI);
        return turns == 0 ? played : new Spun(played, turns * Mth.TWO_PI);
    }

    /** An animation with the whole body turned a little more round (whole turns: it looks the same). */
    private record Spun(IAnimation inner, float yaw) implements IAnimation {
        @Override
        public void tick() {
            inner.tick();
        }

        @Override
        public FirstPersonMode getFirstPersonMode(float tickDelta) {
            return inner.getFirstPersonMode(tickDelta);
        }

        @Override
        public FirstPersonConfiguration getFirstPersonConfiguration(float tickDelta) {
            return inner.getFirstPersonConfiguration(tickDelta);
        }

        @Override
        public boolean isActive() {
            return inner.isActive();
        }

        @Override
        public void setupAnim(float tickDelta) {
            inner.setupAnim(tickDelta);
        }

        @Override
        public Vec3f get3DTransform(String part, TransformType type, float tickDelta, Vec3f value0) {
            Vec3f v = inner.get3DTransform(part, type, tickDelta, value0);
            return "body".equals(part) && type == TransformType.ROTATION ? new Vec3f(v.getX(), v.getY() + yaw, v.getZ()) : v;
        }
    }

    /** An animation played for {@code ticks}, then held on that frame. */
    private static final class Segment implements IAnimation {
        private final IAnimation inner;
        private final int ticks;
        private int played;

        Segment(IAnimation inner, int ticks) {
            this.inner = inner;
            this.ticks = ticks;
        }

        @Override
        public void tick() {
            if (played < ticks) {
                inner.tick();
                played++;
            }
        }

        @Override
        public boolean isActive() {
            return inner.isActive();
        }

        @Override
        public FirstPersonMode getFirstPersonMode(float tickDelta) {
            return inner.getFirstPersonMode(tickDelta);
        }

        @Override
        public FirstPersonConfiguration getFirstPersonConfiguration(float tickDelta) {
            return inner.getFirstPersonConfiguration(tickDelta);
        }

        @Override
        public void setupAnim(float tickDelta) {
            inner.setupAnim(played < ticks ? tickDelta : 0f);
        }

        @Override
        public Vec3f get3DTransform(String part, TransformType type, float tickDelta, Vec3f value0) {
            return inner.get3DTransform(part, type, played < ticks ? tickDelta : 0f, value0);
        }
    }

    /**
     * Raises the sword guard: its animation, held from its loop marker while the button is held.
     * Seen in first person too (the arms and blades in front of the camera), as in 1.7.10. The
     * legs stay Kinetrix's while walking.
     */
    public static void playGuard(AbstractClientPlayer p, dev.mcreload.moves.Guard.Kind kind) {
        MoveLayer layer = layer(p);
        KeyframeAnimation anim = kind.animation == null ? null : PlayerAnimationRegistry.getAnimation(kind.animation);
        if (layer == null || anim == null) return;
        layer.speed.speed = 1f;
        layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(2, Ease.INOUTSINE), firstPerson(new KeyframeAnimationPlayer(anim)));
    }

    /** Seen in first person too: both arms and what they hold, in front of the camera (as in 1.7.10's sword block). */
    private static KeyframeAnimationPlayer firstPerson(KeyframeAnimationPlayer player) {
        return player.setFirstPersonMode(FirstPersonMode.THIRD_PERSON_MODEL)
                .setFirstPersonConfiguration(new FirstPersonConfiguration(true, true, true, true));
    }

    /** The tick of {@code bow_barrage} held after the volley: down on one knee, the bow at full draw. */
    private static final int KNEEL_TICK = 10;

    /**
     * Holds the volley's kneel (the right button still down after it, BowStance): the legs and body
     * stay as in {@code bow_barrage}, the head and arms are left to the drawn bow, so they follow
     * where the player aims.
     */
    public static void playKneel(AbstractClientPlayer p) {
        MoveLayer layer = layer(p);
        KeyframeAnimation anim = PlayerAnimationRegistry.getAnimation(Move.BARRAGE.animation);
        if (layer == null || anim == null) return;
        layer.speed.speed = 1f;
        layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(3, Ease.INOUTSINE),
                unwound(layer, anim, KNEEL_TICK, new Kneel(new KeyframeAnimationPlayer(anim, KNEEL_TICK))));
        KinetrixBridge.wholeBody(p.getUUID(), 200);
    }

    /** A frame held, its head and arms left to the layers below (vanilla's aiming bow). */
    private record Kneel(IAnimation pose) implements IAnimation {
        private static final java.util.Set<String> AIMING = java.util.Set.of("head", "rightArm", "leftArm", "rightItem", "leftItem");

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public void setupAnim(float tickDelta) {
            pose.setupAnim(0f);
        }

        @Override
        public dev.kosmx.playerAnim.core.util.Vec3f get3DTransform(String part, dev.kosmx.playerAnim.api.TransformType type,
                                                                   float tickDelta, dev.kosmx.playerAnim.core.util.Vec3f value0) {
            return AIMING.contains(part) ? value0 : pose.get3DTransform(part, type, 0f, value0);
        }
    }

    /** Ends the animation early, blended out over {@code ticks}. */
    public static void stop(AbstractClientPlayer p, int ticks) {
        MoveLayer layer = layer(p);
        KinetrixBridge.wholeBody(p.getUUID(), 0);
        if (layer == null || layer.getAnimation() == null) return;
        layer.setAnimation(new Upright(layer.getAnimation()));
        layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(ticks, Ease.INOUTSINE), null);
    }

    /**
     * An animation with the whole body's turns taken within half a turn either way (the same pose):
     * blended out from the end of a roll (-340 degrees) it must not turn the player all the way
     * back, only the last 20 degrees.
     */
    private record Upright(IAnimation inner) implements IAnimation {
        @Override
        public void tick() {
            inner.tick();
        }

        @Override
        public FirstPersonMode getFirstPersonMode(float tickDelta) {
            return inner.getFirstPersonMode(tickDelta);
        }

        @Override
        public FirstPersonConfiguration getFirstPersonConfiguration(float tickDelta) {
            return inner.getFirstPersonConfiguration(tickDelta);
        }

        @Override
        public boolean isActive() {
            return inner.isActive();
        }

        @Override
        public void setupAnim(float tickDelta) {
            inner.setupAnim(tickDelta);
        }

        @Override
        public Vec3f get3DTransform(String part, TransformType type, float tickDelta, Vec3f value0) {
            Vec3f v = inner.get3DTransform(part, type, tickDelta, value0);
            if (!"body".equals(part) || type != TransformType.ROTATION) return v;
            return new Vec3f(Mth.wrapDegrees(v.getX() * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD,
                    Mth.wrapDegrees(v.getY() * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD,
                    Mth.wrapDegrees(v.getZ() * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD);
        }
    }

    /**
     * Ends the move by handing the player to another pose (the slide held into Kinetrix's crawl):
     * the move's pose stops where it is, instead of going on to get up, and fades out over
     * {@code ticks} while the next pose comes in under it.
     */
    public static void handOver(AbstractClientPlayer p, int ticks) {
        MoveLayer layer = layer(p);
        KinetrixBridge.wholeBody(p.getUUID(), 0);
        if (layer == null || layer.getAnimation() == null) return;
        layer.setAnimation(new Upright(new Held(layer.getAnimation())));
        layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(ticks, Ease.INOUTSINE), null);
    }

    /** An animation stopped on its current frame. */
    private record Held(IAnimation pose) implements IAnimation {
        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public void setupAnim(float tickDelta) {
            pose.setupAnim(0f);
        }

        @Override
        public dev.kosmx.playerAnim.core.util.Vec3f get3DTransform(String part, dev.kosmx.playerAnim.api.TransformType type,
                                                                   float tickDelta, dev.kosmx.playerAnim.core.util.Vec3f value0) {
            return pose.get3DTransform(part, type, 0f, value0);
        }
    }

    private MoveAnimator() {}
}
