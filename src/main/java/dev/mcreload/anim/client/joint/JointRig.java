package dev.mcreload.anim.client.joint;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kosmx.playerAnim.core.util.Pair;
import dev.kosmx.playerAnim.impl.IAnimatedPlayer;
import dev.kosmx.playerAnim.impl.animation.AnimationApplier;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * The five joints of one humanoid model (player model or the player's armour models): both
 * elbows, both knees and the waist. Bends come from playerAnimator's final pose, so animations
 * from any playerAnimator mod bend too, without bendy-lib.
 */
public final class JointRig {
    public static final float ARM_JOINT = 4f, LEG_JOINT = 6f, WAIST_JOINT = 6f;

    public final Joint rightArm, leftArm, rightLeg, leftLeg, torso;
    /** Parts carried by the upper torso (head, arms and their overlays). */
    private final Set<ModelPart> upper = Collections.newSetFromMap(new IdentityHashMap<>());
    /** Of those, the head: it rides along with the neck but keeps looking where the player looks. */
    private final Set<ModelPart> heads = Collections.newSetFromMap(new IdentityHashMap<>());

    private JointRig(Joint ra, Joint la, Joint rl, Joint ll, Joint t) {
        rightArm = ra;
        leftArm = la;
        rightLeg = rl;
        leftLeg = ll;
        torso = t;
    }

    /**
     * Parts owned by another model system (Entity Model Features swaps their cubes and children
     * between variants at will) are never cut: the model stays unjointed but intact.
     */
    private static boolean foreign(ModelPart... parts) {
        for (ModelPart p : parts) {
            if (dev.mcreload.anim.compat.EmfCompat.foreignPart(p)) {
                if (!warnedForeign) {
                    warnedForeign = true;
                    dev.mcreload.anim.McReloadAnim.LOG.warn("[kinetrix] A model is made of another mod's parts ({}); it will not get"
                            + " jointed elbows/knees. With Entity Model Features, enable compat.playerOverEmf.", p.getClass().getName());
                }
                return true;
            }
        }
        return false;
    }

    private static boolean warnedForeign;

    public static JointRig forPlayer(PlayerModel<?> m) {
        if (foreign(m.body, m.head, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg, m.jacket, m.rightSleeve, m.leftSleeve)) return null;
        JointRig rig = new JointRig(
                Joint.build(ARM_JOINT, false, m.rightArm, m.rightSleeve),
                Joint.build(ARM_JOINT, false, m.leftArm, m.leftSleeve),
                Joint.build(LEG_JOINT, false, m.rightLeg, m.rightPants),
                Joint.build(LEG_JOINT, false, m.leftLeg, m.leftPants),
                Joint.build(WAIST_JOINT, true, m.body, m.jacket));
        rig.upper.add(m.head);
        rig.upper.add(m.hat);
        rig.heads.add(m.head);
        rig.heads.add(m.hat);
        rig.upper.add(m.rightArm);
        rig.upper.add(m.leftArm);
        rig.upper.add(m.rightSleeve);
        rig.upper.add(m.leftSleeve);
        PartBinding.bind(m, rig);
        return rig;
    }

    public static JointRig forArmor(HumanoidModel<?> m) {
        if (foreign(m.body, m.head, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg)) return null;
        JointRig rig = new JointRig(
                Joint.build(ARM_JOINT, false, m.rightArm),
                Joint.build(ARM_JOINT, false, m.leftArm),
                Joint.build(LEG_JOINT, false, m.rightLeg),
                Joint.build(LEG_JOINT, false, m.leftLeg),
                Joint.build(WAIST_JOINT, true, m.body));
        rig.upper.add(m.head);
        rig.upper.add(m.hat);
        rig.heads.add(m.head);
        rig.heads.add(m.hat);
        rig.upper.add(m.rightArm);
        rig.upper.add(m.leftArm);
        return rig;
    }

    /** Held item tipped in the main hand and in the other hand (radians), and which hand is main. */
    private float itemTilt, itemTiltOff, itemRoll, itemRollOff;
    private boolean mainRight = true;

    public boolean isUpper(ModelPart p) {
        return upper.contains(p);
    }

    public boolean isHead(ModelPart p) {
        return heads.contains(p);
    }

    /** Moves a head part along with the bent neck without turning it. */
    public void applyNeck(PoseStack ps, ModelPart body, ModelPart head) {
        if (!torsoBent()) return;
        PoseStack tmp = new PoseStack();
        applyUpperBody(tmp, body);
        org.joml.Vector4f p = new org.joml.Vector4f(head.x / 16f, head.y / 16f, head.z / 16f, 1f);
        tmp.last().pose().transform(p);
        ps.translate(p.x() - head.x / 16f, p.y() - head.y / 16f, p.z() - head.z / 16f);
    }

    /** Any joint bent this frame: the model then draws its parts through {@link #draw}. */
    public boolean anyBent() {
        return bent(rightArm) || bent(leftArm) || bent(rightLeg) || bent(leftLeg) || bent(torso);
    }

    private static boolean bent(Joint j) {
        return j != null && j.bent();
    }

    /**
     * Draws a part of this model: bent if one of the joints cuts it and is bent, otherwise the
     * vanilla way. The caller has already applied the upper-body transform if needed.
     */
    public void draw(ModelPart p, com.mojang.blaze3d.vertex.PoseStack ps, com.mojang.blaze3d.vertex.VertexConsumer vc,
                     int light, int overlay, float r, float g, float b, float a) {
        Joint j = jointOf(p);
        if (j != null && j.bent() && !dev.mcreload.anim.compat.SkinLayersCompat.drawsPart(p)
                && j.draw(p, ps, vc, light, overlay, r, g, b, a)) return;
        p.render(ps, vc, light, overlay, r, g, b, a);
    }

    private Joint jointOf(ModelPart p) {
        for (Joint j : new Joint[]{rightArm, leftArm, rightLeg, leftLeg, torso}) {
            if (j != null && j.owns(p)) return j;
        }
        return null;
    }

    public boolean torsoBent() {
        return torso != null && torso.bent();
    }

    /** Reads the bends for this frame from the entity's playerAnimator stack. */
    public void update(LivingEntity entity) {
        AnimationApplier anim = null;
        if (Joints.active() && !Joints.firstPerson && entity instanceof IAnimatedPlayer ap) {
            anim = ap.playerAnimator_getAnimation();
            if (anim != null && !anim.isActive()) anim = null;
        }
        set(rightArm, anim, "rightArm");
        set(leftArm, anim, "leftArm");
        set(rightLeg, anim, "rightLeg");
        set(leftLeg, anim, "leftLeg");
        set(torso, anim, "torso");
        itemTilt = 0f;
        itemTiltOff = 0f;
        itemRoll = 0f;
        itemRollOff = 0f;
        if (!Joints.firstPerson && entity instanceof net.minecraft.client.player.AbstractClientPlayer p) {
            dev.mcreload.anim.client.rig.PlayerRig r = dev.mcreload.anim.client.rig.RigRegistry.get(p);
            if (r != null) {
                itemTilt = r.motion.itemTilt.v;
                itemTiltOff = r.motion.itemTiltOff.v;
                itemRoll = r.motion.itemRoll;
                itemRollOff = r.motion.itemRollOff;
                mainRight = r.motion.mainRight;
            }
        }
    }

    private static void set(Joint j, AnimationApplier anim, String name) {
        if (j == null) return;
        if (anim == null) {
            j.set(0f, 0f);
            return;
        }
        Pair<Float, Float> b = anim.getBend(name);
        j.set(b.getLeft(), b.getRight());
    }

    /** Bends set directly (mobs: no playerAnimator). Axis 0 = forward/back. */
    public void setBends(float rightArmBend, float leftArmBend, float rightLegBend, float leftLegBend, float torsoBend) {
        if (rightArm != null) rightArm.set(0f, rightArmBend);
        if (leftArm != null) leftArm.set(0f, leftArmBend);
        if (rightLeg != null) rightLeg.set(0f, rightLegBend);
        if (leftLeg != null) leftLeg.set(0f, leftLegBend);
        if (torso != null) torso.set(0f, torsoBend);
        itemTilt = 0f;
        itemTiltOff = 0f;
        itemRoll = 0f;
        itemRollOff = 0f;
    }

    public void copyFrom(JointRig o) {
        copy(rightArm, o.rightArm);
        copy(leftArm, o.leftArm);
        copy(rightLeg, o.rightLeg);
        copy(leftLeg, o.leftLeg);
        copy(torso, o.torso);
    }

    private static void copy(Joint dst, Joint src) {
        if (dst == null) return;
        if (src == null) dst.set(0f, 0f);
        else dst.set(src.axis, src.bend);
    }

    /**
     * Carries the upper body around the waist: the body part's transform, the waist hinge in body
     * space, then back out of body space, so head and arms swing with the bent torso.
     */
    public void applyUpperBody(PoseStack ps, ModelPart body) {
        if (!torsoBent()) return;
        Quaternionf q = new Quaternionf().rotationZYX(body.zRot, body.yRot, body.xRot);
        ps.translate(body.x / 16f, body.y / 16f, body.z / 16f);
        ps.mulPose(q);
        torso.apply(ps);
        ps.mulPose(q.conjugate());
        ps.translate(-body.x / 16f, -body.y / 16f, -body.z / 16f);
    }

    public void applyElbow(PoseStack ps, HumanoidArm arm) {
        Joint j = arm == HumanoidArm.RIGHT ? rightArm : leftArm;
        if (j != null) j.apply(ps);
        // Turn the held weapon in the hand (pivot at the fist).
        boolean mainArm = (arm == HumanoidArm.RIGHT) == mainRight;
        float tilt = mainArm ? itemTilt : itemTiltOff;
        if (Math.abs(tilt) > 1e-3f) {
            ps.translate(0f, 10f / 16f, -1f / 16f);
            ps.mulPose(new Quaternionf().rotationX(tilt));
            ps.translate(0f, -10f / 16f, 1f / 16f);
        }
        // Spun about the forearm, round the grip (the item sits a pixel in from the arm's middle).
        float roll = mainArm ? itemRoll : itemRollOff;
        if (Math.abs(roll) > 1e-3f) {
            float gx = (arm == HumanoidArm.RIGHT ? -1f : 1f) / 16f;
            ps.translate(gx, 10f / 16f, -1.5f / 16f);
            ps.mulPose(new Quaternionf().rotationY(roll));
            ps.translate(-gx, -10f / 16f, 1.5f / 16f);
        }
    }
}
