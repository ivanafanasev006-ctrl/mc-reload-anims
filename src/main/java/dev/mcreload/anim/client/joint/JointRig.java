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

    private JointRig(Joint ra, Joint la, Joint rl, Joint ll, Joint t) {
        rightArm = ra;
        leftArm = la;
        rightLeg = rl;
        leftLeg = ll;
        torso = t;
    }

    public static JointRig forPlayer(PlayerModel<?> m) {
        JointRig rig = new JointRig(
                Joint.build(ARM_JOINT, false, m.rightArm, m.rightSleeve),
                Joint.build(ARM_JOINT, false, m.leftArm, m.leftSleeve),
                Joint.build(LEG_JOINT, false, m.rightLeg, m.rightPants),
                Joint.build(LEG_JOINT, false, m.leftLeg, m.leftPants),
                Joint.build(WAIST_JOINT, true, m.body, m.jacket));
        rig.upper.add(m.head);
        rig.upper.add(m.hat);
        rig.upper.add(m.rightArm);
        rig.upper.add(m.leftArm);
        rig.upper.add(m.rightSleeve);
        rig.upper.add(m.leftSleeve);
        return rig;
    }

    public static JointRig forArmor(HumanoidModel<?> m) {
        JointRig rig = new JointRig(
                Joint.build(ARM_JOINT, false, m.rightArm),
                Joint.build(ARM_JOINT, false, m.leftArm),
                Joint.build(LEG_JOINT, false, m.rightLeg),
                Joint.build(LEG_JOINT, false, m.leftLeg),
                Joint.build(WAIST_JOINT, true, m.body));
        rig.upper.add(m.head);
        rig.upper.add(m.hat);
        rig.upper.add(m.rightArm);
        rig.upper.add(m.leftArm);
        return rig;
    }

    public boolean isUpper(ModelPart p) {
        return upper.contains(p);
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
    }
}
