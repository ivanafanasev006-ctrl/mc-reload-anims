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
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, PRIORITY, MoveLayer::new);
        FirstPersonArms.init();
    }

    /**
     * The layer, with a speed control (the slide plays faster than in Blockbench). Seen from the
     * player's own eyes, an attack's whole-body turn is left out so the arms stay in front of the camera
     * (FirstPersonArms.body).
     */
    private static final class MoveLayer extends ModifierLayer<IAnimation> {
        final SpeedModifier speed = new SpeedModifier(1f);
        private final AbstractClientPlayer player;

        MoveLayer(AbstractClientPlayer player) {
            this.player = player;
            addModifierBefore(speed);
        }

        @Override
        public Vec3f get3DTransform(String part, TransformType type, float tickDelta, Vec3f value0) {
            Vec3f v = super.get3DTransform(part, type, tickDelta, value0);
            if (!"body".equals(part) || !isActive() || !FirstPersonArms.ownFirstPerson(player) || !FirstPersonArms.applies(player)) return v;
            if (type == TransformType.ROTATION) return FirstPersonArms.bodyRotation(player, v);
            if (type != TransformType.POSITION) return v;
            Vec3f rot = FirstPersonArms.bodyRotation(player, super.get3DTransform(part, TransformType.ROTATION, tickDelta, Vec3f.ZERO));
            return FirstPersonArms.bodyPosition(player, rot);
        }
    }

    private static MoveLayer layer(AbstractClientPlayer p) {
        return PlayerAnimationAccess.getPlayerAssociatedData(p).get(LAYER) instanceof MoveLayer l ? l : null;
    }

    /** The hang's loop starts here (ledge_hang's marker, 1.25 s): its first part is the catch. */
    private static final int HANG_LOOP = 25;

    /** Starts the move's animation, blended in over two ticks. */
    public static void play(AbstractClientPlayer p, Move move) {
        play(p, move, null);
    }

    /**
     * The same, after the move {@code was} (or null): the hang after a move on the ledge starts in
     * its loop (it has no edge to catch again).
     */
    public static void play(AbstractClientPlayer p, Move move, Move was) {
        MoveLayer layer = layer(p);
        KeyframeAnimation anim = PlayerAnimationRegistry.getAnimation(move.animation);
        if (layer == null) return;
        if (anim == null) {
            KinetrixMoves.LOG.warn("[kinetrix_moves] animation {} is missing", move.animation);
            return;
        }
        layer.speed.speed = move.animSpeed;
        // The hail: the volley's draw and release over and over (MoveAnimator.pace sets the pace).
        if (move == Move.HAIL) anim = hailLoop(anim);
        // Bow moves may play a part of their animation (a half of bow_walk_around) and hold its last frame.
        // The ending of the shot out of a slide plays bow_slide_shot from the knee.
        int from = move.shot != null ? move.shot.animStart() : move == Move.SLIDE_SHOT_END ? Move.SLIDE_SHOT_SPIN : 0;
        if (move == Move.LEDGE_HANG && was != null && was.ledge()) from = HANG_LOOP;
        KeyframeAnimationPlayer keyframes = new KeyframeAnimationPlayer(anim, from);
        if (move.firstPerson()) firstPerson(keyframes);
        IAnimation played = endsTurned(anim) ? new EndsOnLastFrame(keyframes) : keyframes;
        if (move.shot != null && move.shot.holdAt() >= 0) played = new Segment(played, move.shot.holdAt() - from);
        // A bow in the attack out of a slide: held as it is, not turned in the fist as the blade is
        if (move == Move.SLIDE_ATTACK && dev.mcreload.moves.Bow.isBow(p.getMainHandItem())) played = new ItemAsHeld(played);
        // On the ledge the moves follow each other quickly: a longer blend, the hands do not jump.
        int fade = move.ledge() && was != null && was.ledge() ? 4 : 2;
        layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(fade, Ease.INOUTSINE), unwound(layer, anim, from, played));
        KinetrixBridge.wholeBody(p.getUUID(), move.length * 50L + 100L);
    }

    /** bow_barrage looped over its draw and release from Bow.HAIL_LOOP_FROM to HAIL_LOOP_TO, made once. */
    private static KeyframeAnimation hailLoop, hailOf;

    private static KeyframeAnimation hailLoop(KeyframeAnimation anim) {
        if (hailOf != anim) {
            KeyframeAnimation.AnimationBuilder b = anim.mutableCopy();
            b.isLooped = true;
            b.returnTick = dev.mcreload.moves.Bow.HAIL_LOOP_FROM;
            b.endTick = dev.mcreload.moves.Bow.HAIL_LOOP_TO;
            b.stopTick = dev.mcreload.moves.Bow.HAIL_LOOP_TO + 1;
            hailLoop = b.build();
            hailOf = anim;
        }
        return hailLoop;
    }

    /**
     * Every tick, every player: the pace of G's animations. The volley's draws between its arrows are
     * played slower, so the arrows leave 0.55 s apart on the animation's releases (Bow.VOLLEY_PACE);
     * the hail's looped draw and release a quarter faster, an arrow every 4 ticks (Bow.HAIL_PACE).
     */
    static void pace(AbstractClientPlayer p, dev.mcreload.moves.Stamina s) {
        if (s.move != Move.BARRAGE && s.move != Move.HAIL) return;
        MoveLayer layer = layer(p);
        if (layer == null) return;
        int t = s.moveTick, from = dev.mcreload.moves.Bow.VOLLEY_SHOTS[0];
        if (s.move == Move.BARRAGE) {
            int last = dev.mcreload.moves.Bow.VOLLEY_SHOTS[dev.mcreload.moves.Bow.VOLLEY_SHOTS.length - 1];
            layer.speed.speed = t >= from && t < last ? dev.mcreload.moves.Bow.VOLLEY_PACE : 1f;
        } else {
            layer.speed.speed = t >= from ? dev.mcreload.moves.Bow.HAIL_PACE : 1f;
        }
    }

    /**
     * The animation ends with the whole player turned by whole turns (a roll at -360 degrees, a spin
     * at 360): upright already. playerAnimator never stops an animation on its last frame, it eases
     * every value back to zero over three more ticks, and from -360 that is a whole turn backwards
     * in three ticks (author, 2026-10-29: "a very quick flip back" at the end of the fall's roll).
     */
    private static boolean endsTurned(KeyframeAnimation anim) {
        if (anim.isInfinite) return false;
        KeyframeAnimation.StateCollection body = anim.getPart("body");
        if (body == null) return false;
        for (KeyframeAnimation.StateCollection.State axis : new KeyframeAnimation.StateCollection.State[]{body.pitch, body.yaw, body.roll}) {
            java.util.List<KeyframeAnimation.KeyFrame> frames = axis.getKeyFrames();
            if (!frames.isEmpty() && Math.abs(frames.get(frames.size() - 1).value) > Mth.PI) return true;
        }
        return false;
    }

    /** An animation whose turns of the held items are left out (a bow swung where the animation turns a blade). */
    private record ItemAsHeld(IAnimation inner) implements IAnimation {
        @Override
        public void tick() {
            inner.tick();
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
            inner.setupAnim(tickDelta);
        }

        @Override
        public Vec3f get3DTransform(String part, TransformType type, float tickDelta, Vec3f value0) {
            return "rightItem".equals(part) || "leftItem".equals(part) ? value0 : inner.get3DTransform(part, type, tickDelta, value0);
        }
    }

    /** An animation stopped on its last frame (see {@link #endsTurned}): over once it gets there. */
    private record EndsOnLastFrame(KeyframeAnimationPlayer inner) implements IAnimation {
        @Override
        public void tick() {
            inner.tick();
        }

        @Override
        public boolean isActive() {
            return inner.isActive() && inner.getTick() < inner.getData().endTick;
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
            inner.setupAnim(tickDelta);
        }

        @Override
        public Vec3f get3DTransform(String part, TransformType type, float tickDelta, Vec3f value0) {
            return inner.get3DTransform(part, type, tickDelta, value0);
        }
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
     * Holds the volley's kneel (the right button still down after it, BowStance; the shot out of a slide
     * on its knee, bow_slide_shot's 0.5 s being the same pose): the legs and body stay as in
     * {@code bow_barrage}, the head and arms are left to the drawn bow, so they follow where the player aims.
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
