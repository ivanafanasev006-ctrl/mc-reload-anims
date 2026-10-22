package dev.mcreload.anim.client.mob;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.mcreload.anim.client.joint.JointHolder;
import dev.mcreload.anim.client.joint.JointRig;
import dev.mcreload.anim.client.joint.Joints;
import dev.mcreload.anim.client.pose.Signs;
import dev.mcreload.anim.config.Cfg;
import dev.mcreload.anim.mixin.ChickenModelAccessor;
import dev.mcreload.anim.mixin.HorseModelAccessor;
import dev.mcreload.anim.mixin.LlamaModelAccessor;
import dev.mcreload.anim.mixin.OcelotModelAccessor;
import dev.mcreload.anim.mixin.QuadrupedModelAccessor;
import dev.mcreload.anim.mixin.WolfModelAccessor;
import net.minecraft.client.model.ChickenModel;
import net.minecraft.client.model.CreeperModel;
import net.minecraft.client.model.EndermanModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.HorseModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.IronGolemModel;
import net.minecraft.client.model.LlamaModel;
import net.minecraft.client.model.OcelotModel;
import net.minecraft.client.model.QuadrupedModel;
import net.minecraft.client.model.SpiderModel;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.WolfModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Makes mobs feel alive without touching how they work: breathing, weight shifting, a bounce and
 * sway in the step, heads that look around and nod, tails that swish and wag, and for humanoid
 * mobs (zombies, skeletons, piglins...) the same jointed knees, elbows and waist as the player.
 *
 * <p>Runs right after vanilla posed the model for this frame. Every part it touches is recorded and
 * put back after the entity is drawn, so nothing accumulates on the shared model.</p>
 */
public final class MobAnimator {
    private static final List<ModelPart> touched = new ArrayList<>();
    private static final List<PartPose> saved = new ArrayList<>();
    private static JointRig bentRig;
    /** Models that cannot be jointed (another mod's model parts). */
    private static final java.util.Map<HumanoidModel<?>, Boolean> NO_JOINTS = new java.util.WeakHashMap<>();

    public static void apply(LivingEntity e, EntityModel<?> model, PoseStack ps, float pt) {
        if (!Cfg.enabled || !Cfg.mobs || e instanceof Player || e instanceof ArmorStand || e.isInvisible()
                || e.isSleeping() || e.deathTime > 0) return;
        // Fresh Animations & co. (Entity Model Features) animate this model themselves.
        if (dev.mcreload.anim.compat.EmfCompat.isEmfModel(model)) return;
        try {
            float t = (e.tickCount + pt) / 20f;
            float seed = (e.getUUID().hashCode() & 1023) / 1023f * Mth.TWO_PI;
            float wp = e.walkAnimation.position(pt);
            float ws = Math.min(1f, e.walkAnimation.speed(pt));
            Ctx c = new Ctx(e, ps, t, seed, wp, ws, pt);

            if (model instanceof HumanoidModel<?> hm) humanoid(c, hm);
            else if (model instanceof VillagerModel<?> vm) villager(c, vm);
            else if (model instanceof IronGolemModel<?> gm) golem(c, gm);
            else if (model instanceof CreeperModel<?> cm) creeper(c, cm);
            else if (model instanceof SpiderModel<?> sm) spider(c, sm);
            else if (model instanceof WolfModel<?> wm) wolf(c, wm);
            else if (model instanceof OcelotModel<?> om) cat(c, om);
            else if (model instanceof ChickenModel<?> chm) chicken(c, chm);
            else if (model instanceof HorseModel<?> hom) horse(c, hom);
            else if (model instanceof LlamaModel<?> lm) llama(c, lm);
            else if (model instanceof QuadrupedModel<?> qm) quadruped(c, qm);
            else generic(c);
            reactions(c);
        } catch (RuntimeException ex) {
            // A modded model that doesn't look like what we expect: leave it alone.
            restore();
        }
    }

    /** Puts back every part touched for the entity just drawn. */
    public static void restore() {
        for (int i = 0; i < touched.size(); i++) touched.get(i).loadPose(saved.get(i));
        touched.clear();
        saved.clear();
        if (bentRig != null) {
            bentRig.setBends(0f, 0f, 0f, 0f, 0f);
            bentRig = null;
        }
    }

    // ------------------------------------------------------------------ helpers

    private record Ctx(LivingEntity e, PoseStack ps, float t, float seed, float wp, float ws, float pt) {
        float idle() {
            return 1f - ws;
        }

        /** Vanilla's limb phase. */
        float phase() {
            return wp * 0.6662f;
        }
    }

    private static void save(ModelPart p) {
        for (ModelPart q : touched) if (q == p) return;
        touched.add(p);
        saved.add(p.storePose());
    }

    private static void rot(ModelPart p, float dx, float dy, float dz) {
        if (p == null) return;
        save(p);
        p.xRot += dx;
        p.yRot += dy;
        p.zRot += dz;
    }

    private static void move(ModelPart p, float dx, float dy, float dz) {
        if (p == null) return;
        save(p);
        p.x += dx;
        p.y += dy;
        p.z += dz;
    }

    private static ModelPart child(HierarchicalModel<?> m, String name) {
        try {
            return m.root().getChild(name);
        } catch (NoSuchElementException ex) {
            return null;
        }
    }

    private static ModelPart child(ModelPart parent, String name) {
        if (parent == null) return null;
        try {
            return parent.getChild(name);
        } catch (NoSuchElementException ex) {
            return null;
        }
    }

    /** Model space here is y-down, in blocks, with the feet at y = 1.5. */
    private static void rollAboutFeet(PoseStack ps, float angle) {
        if (Math.abs(angle) < 1e-4f) return;
        ps.translate(0f, 1.5f, 0f);
        ps.mulPose(Axis.ZP.rotation(angle));
        ps.translate(0f, -1.5f, 0f);
    }

    private static void pitchAboutFeet(PoseStack ps, float angle) {
        if (Math.abs(angle) < 1e-4f) return;
        ps.translate(0f, 1.5f, 0f);
        ps.mulPose(Axis.XP.rotation(angle));
        ps.translate(0f, -1.5f, 0f);
    }

    /** Positive = up. */
    private static void lift(PoseStack ps, float blocks) {
        ps.translate(0f, -blocks, 0f);
    }

    /** Step bounce: highest when the legs pass each other. */
    private static float stepBounce(Ctx c) {
        return (1f - Math.abs(Mth.cos(c.phase()))) * c.ws;
    }

    /** A smooth 0..1 pulse that happens now and then (period seconds, lasting len seconds). */
    private static float now_and_then(Ctx c, float period, float len) {
        float x = (c.t + c.seed * 3f) % period;
        if (x > len) return 0f;
        return Mth.sin(Mth.PI * x / len);
    }

    // ------------------------------------------------------------------ hurt / general

    private static void reactions(Ctx c) {
        if (c.e.hurtTime > 0) {
            float h = (c.e.hurtTime - c.pt) / 10f;
            if (h > 0f) pitchAboutFeet(c.ps, -0.16f * h * h);
        }
    }

    /** Anything else: breathing and a little bounce in the step. */
    private static void generic(Ctx c) {
        lift(c.ps, 0.03f * stepBounce(c));
        rollAboutFeet(c.ps, 0.03f * Mth.sin(c.phase()) * c.ws);
        float breathe = Mth.sin(c.t * 1.6f + c.seed);
        c.ps.translate(0f, 1.5f, 0f);
        c.ps.scale(1f + 0.008f * breathe, 1f - 0.006f * breathe, 1f + 0.008f * breathe);
        c.ps.translate(0f, -1.5f, 0f);
    }

    // ------------------------------------------------------------------ humanoids

    private static void humanoid(Ctx c, HumanoidModel<?> m) {
        LivingEntity e = c.e;
        boolean zombie = e instanceof Zombie;
        boolean skeleton = e instanceof AbstractSkeleton;
        boolean riding = e.isPassenger();
        boolean swimming = e.isVisuallySwimming() || e.isFallFlying();
        float amp = m.young ? 1.35f : 1f;
        float ws = c.ws, idle = c.idle();
        float ph = c.phase();
        float cs = Mth.cos(ph), sn = Mth.sin(ph);
        float t = c.t, seed = c.seed;

        if (!riding && !swimming) {
            // A bounce and a sway in the step, a lean into the walk; zombies shamble bent over.
            lift(c.ps, (0.05f * stepBounce(c) - 0.015f * ws) * amp);
            rollAboutFeet(c.ps, 0.04f * sn * ws * amp);
            float lean = 0.05f * ws + (zombie ? 0.06f + 0.04f * ws : 0f);
            if (!Joints.enabled() || !Cfg.mobJoints || m instanceof EndermanModel<?>) pitchAboutFeet(c.ps, lean);
            // Weight shifting while standing.
            rollAboutFeet(c.ps, 0.025f * Mth.sin(t * 0.45f + seed) * idle);
        }

        // Shoulders counter-swing the hips; breathing in the arms; the head nods with each step
        // and tilts now and then while standing.
        rot(m.body, 0f, 0.08f * cs * ws, 0f);
        float breathe = Mth.sin(t * 1.7f + seed);
        rot(m.rightArm, 0f, 0f, 0.03f + 0.02f * breathe * idle);
        rot(m.leftArm, 0f, 0f, -0.03f - 0.02f * breathe * idle);
        float headTilt = 0.08f * Mth.sin(t * 0.6f + seed) * idle;
        rot(m.head, 0.05f * Mth.sin(ph * 2f) * ws, 0f, headTilt);
        rot(m.hat, 0.05f * Mth.sin(ph * 2f) * ws, 0f, headTilt);

        if (zombie) {
            // Lolling head and arms that drift up and down out of step.
            float loll = 0.14f * Mth.sin(t * 0.8f + seed);
            rot(m.head, 0.06f * Mth.sin(t * 1.1f + seed), 0f, loll);
            rot(m.hat, 0.06f * Mth.sin(t * 1.1f + seed), 0f, loll);
            rot(m.rightArm, 0.08f * Mth.sin(t * 2.1f + seed), 0f, 0.03f * Mth.sin(t * 1.3f));
            rot(m.leftArm, 0.08f * Mth.sin(t * 2.1f + seed + 1.6f), 0f, -0.03f * Mth.sin(t * 1.3f + 1f));
        } else if (skeleton) {
            // Twitchy: little jerks of the head now and then.
            float jerk = now_and_then(c, 3.7f, 0.25f);
            rot(m.head, 0f, 0.35f * jerk * Mth.sin(seed * 5f), 0.15f * jerk);
            rot(m.hat, 0f, 0.35f * jerk * Mth.sin(seed * 5f), 0.15f * jerk);
        }

        // Joints: knees, elbows and waist.
        if (!Joints.enabled() || !Cfg.mobJoints || m instanceof EndermanModel<?> || swimming) return;
        JointHolder holder = (JointHolder) m;
        JointRig rig = holder.mcreload$getJoints();
        if (rig == null) {
            if (NO_JOINTS.containsKey(m)) return;
            rig = JointRig.forArmor(m);
            holder.mcreload$setJoints(rig);
            if (rig == null) {
                NO_JOINTS.put(m, Boolean.TRUE);
                return;
            }
        }
        Signs s = Cfg.signs;
        float rKnee, lKnee, torso;
        if (riding) {
            rKnee = lKnee = 0f;
            torso = 0f;
        } else {
            float base = zombie ? 0.28f : skeleton ? 0.10f : 0.12f;
            rKnee = base + 0.95f * Math.max(0f, sn) * ws;
            lKnee = base + 0.95f * Math.max(0f, -sn) * ws;
            torso = 0.08f * ws + (zombie ? 0.22f + 0.06f * Mth.sin(t * 0.9f + seed) : 0f);
            float attack = m.attackTime;
            if (attack > 0f) torso += 0.25f * Mth.sin(Mth.PI * attack);
            // Bent knees lower the hips.
            lift(c.ps, -0.04f * (rKnee + lKnee) * 0.5f);
        }
        boolean rightEmpty = e.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND).isEmpty();
        boolean leftEmpty = e.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND).isEmpty();
        if (e instanceof Mob mob && mob.isLeftHanded()) {
            boolean tmp = rightEmpty;
            rightEmpty = leftEmpty;
            leftEmpty = tmp;
        }
        float elbowBase = zombie ? 0.30f : 0.15f;
        float rElbow = rightEmpty ? elbowBase + 0.35f * Math.max(0f, -cs) * ws : 0.05f;
        float lElbow = leftEmpty ? elbowBase + 0.35f * Math.max(0f, cs) * ws : 0.05f;
        rig.setBends(s.elbow() * rElbow, s.elbow() * lElbow, s.knee() * rKnee, s.knee() * lKnee, s.torso() * torso);
        bentRig = rig;
    }

    // ------------------------------------------------------------------ villagers, golems

    private static void villager(Ctx c, VillagerModel<?> m) {
        float ws = c.ws, idle = c.idle(), t = c.t, seed = c.seed;
        lift(c.ps, 0.045f * stepBounce(c));
        rollAboutFeet(c.ps, 0.05f * Mth.sin(c.phase()) * ws + 0.025f * Mth.sin(t * 0.4f + seed) * idle);
        ModelPart head = child(m, "head");
        ModelPart arms = child(m, "arms");
        // Looking about while standing; nodding along when walking.
        rot(head, 0.06f * Mth.sin(c.phase() * 2f) * ws + 0.05f * Mth.sin(t * 0.7f + seed) * idle,
                0.35f * Mth.sin(t * 0.33f + seed) * idle, 0.07f * Mth.sin(t * 0.5f + seed + 1f) * idle);
        // Crossed arms rise and fall with the breath, bounce with the step.
        float breathe = Mth.sin(t * 1.5f + seed);
        move(arms, 0f, -0.25f * breathe * idle + 0.4f * stepBounce(c), 0f);
        rot(arms, 0.04f * breathe * idle, 0f, 0f);
        ModelPart nose = child(head, "nose");
        rot(nose, 0f, 0.10f * now_and_then(c, 5.3f, 0.6f) * Mth.sin(t * 25f), 0f);
    }

    private static void golem(Ctx c, IronGolemModel<?> m) {
        float ws = c.ws, idle = c.idle(), t = c.t, seed = c.seed;
        // Heavy: a big side-to-side roll with each stomp.
        lift(c.ps, 0.03f * stepBounce(c));
        rollAboutFeet(c.ps, 0.07f * Mth.sin(c.phase()) * ws + 0.015f * Mth.sin(t * 0.35f + seed) * idle);
        ModelPart head = child(m, "head");
        rot(head, 0.04f * Mth.sin(t * 0.5f + seed) * idle, 0.25f * Mth.sin(t * 0.25f + seed) * idle, 0f);
        float sway = Mth.sin(t * 0.9f + seed);
        rot(child(m, "right_arm"), 0.05f * sway * idle, 0f, 0.04f + 0.02f * sway * idle);
        rot(child(m, "left_arm"), -0.05f * sway * idle, 0f, -0.04f - 0.02f * sway * idle);
        rot(child(m, "body"), 0f, 0.06f * Mth.cos(c.phase()) * ws, 0f);
    }

    // ------------------------------------------------------------------ creepers, spiders

    private static void creeper(Ctx c, CreeperModel<?> m) {
        float ws = c.ws, idle = c.idle(), t = c.t, seed = c.seed;
        float swell = c.e instanceof Creeper cr ? cr.getSwelling(c.pt) : 0f;
        // A waddle; standing, a slow sway; about to blow, a shiver.
        rollAboutFeet(c.ps, 0.06f * Mth.sin(c.phase()) * ws + 0.03f * Mth.sin(t * 0.6f + seed) * idle);
        lift(c.ps, 0.02f * stepBounce(c));
        if (swell > 0f) {
            float jit = 0.012f * swell;
            c.ps.translate(jit * Mth.sin(t * 61f), 0f, jit * Mth.cos(t * 53f));
            pitchAboutFeet(c.ps, 0.06f * swell);
        }
        ModelPart head = child(m, "head");
        rot(head, 0.06f * Mth.sin(t * 0.8f + seed) * idle, 0f, 0.08f * Mth.sin(t * 0.5f + seed) * idle);
    }

    private static final String[] SPIDER_LEGS = {"right_hind_leg", "left_hind_leg", "right_middle_hind_leg",
            "left_middle_hind_leg", "right_middle_front_leg", "left_middle_front_leg", "right_front_leg", "left_front_leg"};

    private static void spider(Ctx c, SpiderModel<?> m) {
        float ws = c.ws, idle = c.idle(), t = c.t, seed = c.seed;
        // The abdomen pulses; standing, one leg or another twitches; the body bobs as it scuttles.
        ModelPart abdomen = child(m, "body1");
        float pulse = Mth.sin(t * 2.2f + seed);
        move(abdomen, 0f, -0.3f * pulse, 0.2f * pulse);
        rot(abdomen, 0.04f * pulse, 0f, 0f);
        lift(c.ps, 0.025f * Math.abs(Mth.sin(c.phase() * 2f)) * ws);
        int leg = (int) ((t * 0.8f + seed * 7f) % SPIDER_LEGS.length);
        float twitch = now_and_then(c, 1.25f, 0.3f) * idle;
        ModelPart l = child(m, SPIDER_LEGS[leg]);
        rot(l, 0f, 0.15f * twitch, (leg % 2 == 0 ? -0.25f : 0.25f) * twitch);
        rot(child(m, "head"), 0f, 0.15f * Mth.sin(t * 0.6f + seed) * idle, 0f);
    }

    // ------------------------------------------------------------------ animals

    private static void quadruped(Ctx c, QuadrupedModel<?> m) {
        QuadrupedModelAccessor a = (QuadrupedModelAccessor) m;
        float ws = c.ws, idle = c.idle(), t = c.t, seed = c.seed;
        lift(c.ps, 0.03f * stepBounce(c));
        rollAboutFeet(c.ps, 0.035f * Mth.sin(c.phase()) * ws);
        // Breathing: the body swells a little.
        float breathe = Mth.sin(t * 1.4f + seed);
        rot(a.kinetrix$body(), 0.012f * breathe * idle, 0f, 0f);
        // Head: nods with the step, browses about while standing, shakes off a fly now and then.
        float shake = now_and_then(c, 7.3f, 0.7f) * idle;
        rot(a.kinetrix$head(), 0.08f * Mth.sin(c.phase() * 2f) * ws + 0.06f * Mth.sin(t * 0.5f + seed) * idle,
                0.25f * Mth.sin(t * 0.3f + seed) * idle, 0.25f * shake * Mth.sin(t * 30f));
    }

    private static void wolf(Ctx c, WolfModel<?> m) {
        WolfModelAccessor a = (WolfModelAccessor) m;
        float ws = c.ws, idle = c.idle(), t = c.t, seed = c.seed;
        boolean happy = c.e instanceof TamableAnimal ta && ta.isTame() && !(c.e instanceof Wolf w && w.isAngry());
        lift(c.ps, 0.025f * stepBounce(c));
        // Tail: a happy wag when tame, a lazy sway otherwise.
        float wag = happy ? 0.45f * Mth.sin(t * 13f) : 0.15f * Mth.sin(t * 1.5f + seed);
        rot(a.kinetrix$tail(), 0f, wag * (0.4f + 0.6f * idle), 0f);
        // Head tilts curiously; panting moves the chest.
        rot(a.kinetrix$realHead(), 0f, 0f, 0.20f * now_and_then(c, 6.1f, 1.6f) * idle * Mth.sin(seed + 1f));
        rot(a.kinetrix$upperBody(), 0.03f * Mth.sin(t * (happy ? 9f : 2f)) * idle, 0f, 0f);
    }

    private static void cat(Ctx c, OcelotModel<?> m) {
        OcelotModelAccessor a = (OcelotModelAccessor) m;
        float ws = c.ws, idle = c.idle(), t = c.t, seed = c.seed;
        // The tail curls slowly side to side, the tip following.
        float sway = Mth.sin(t * 1.1f + seed);
        rot(a.kinetrix$tail1(), 0f, 0.30f * sway * (0.3f + 0.7f * idle), 0f);
        rot(a.kinetrix$tail2(), 0f, 0.45f * Mth.sin(t * 1.1f + seed - 0.9f) * (0.3f + 0.7f * idle), 0f);
        rot(a.kinetrix$head(), 0f, 0.20f * Mth.sin(t * 0.4f + seed) * idle, 0.10f * Mth.sin(t * 0.7f + seed) * idle);
        lift(c.ps, 0.02f * stepBounce(c));
    }

    private static void chicken(Ctx c, ChickenModel<?> m) {
        ChickenModelAccessor a = (ChickenModelAccessor) m;
        float ws = c.ws, idle = c.idle(), t = c.t;
        // The head bobs forward and back with every step; standing, it pecks at the ground now and then.
        float bob = Mth.sin(c.phase() * 2f) * 1.2f * ws;
        float peck = now_and_then(c, 3.4f, 0.45f) * idle;
        for (ModelPart p : new ModelPart[]{a.kinetrix$head(), a.kinetrix$beak(), a.kinetrix$redThing()}) {
            move(p, 0f, 0f, -bob);
            rot(p, 0.9f * peck, 0f, 0f);
        }
        // A ruffle of the wings once in a while.
        float ruffle = now_and_then(c, 5.9f, 0.5f) * idle;
        rot(a.kinetrix$rightWing(), 0f, 0f, 0.5f * ruffle * Math.abs(Mth.sin(t * 20f)));
        rot(a.kinetrix$leftWing(), 0f, 0f, -0.5f * ruffle * Math.abs(Mth.sin(t * 20f)));
        lift(c.ps, 0.02f * stepBounce(c));
    }

    private static void horse(Ctx c, HorseModel<?> m) {
        HorseModelAccessor a = (HorseModelAccessor) m;
        float ws = c.ws, idle = c.idle(), t = c.t, seed = c.seed;
        // The tail swishes; the head nods with the stride and dips while standing.
        rot(a.kinetrix$tail(), 0f, 0.30f * Mth.sin(t * 1.3f + seed) * (0.4f + 0.6f * idle),
                0.10f * Mth.sin(t * 0.9f + seed));
        rot(a.kinetrix$headParts(), 0.10f * Mth.sin(c.phase() * 2f) * ws + 0.05f * Mth.sin(t * 0.5f + seed) * idle,
                0.12f * Mth.sin(t * 0.3f + seed) * idle, 0f);
        lift(c.ps, 0.03f * stepBounce(c));
        rollAboutFeet(c.ps, 0.025f * Mth.sin(c.phase()) * ws);
    }

    private static void llama(Ctx c, LlamaModel<?> m) {
        LlamaModelAccessor a = (LlamaModelAccessor) m;
        float ws = c.ws, idle = c.idle(), t = c.t, seed = c.seed;
        // Chewing and looking about.
        float chew = Mth.sin(t * 7f) * now_and_then(c, 4.5f, 2f) * idle;
        rot(a.kinetrix$head(), 0.06f * Mth.sin(c.phase() * 2f) * ws + 0.03f * chew,
                0.2f * Mth.sin(t * 0.3f + seed) * idle, 0.04f * chew);
        lift(c.ps, 0.03f * stepBounce(c));
        rollAboutFeet(c.ps, 0.03f * Mth.sin(c.phase()) * ws);
    }

    private MobAnimator() {}
}
