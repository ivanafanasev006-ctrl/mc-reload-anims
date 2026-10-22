package dev.mcreload.anim.client.joint;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Which joint a player model part belongs to, for mods that draw extra geometry on top of a
 * part with that part's transform (Skin Layers 3D draws the outer skin layer as voxels this way):
 * they can bend their geometry with the same hinge.
 */
public final class PartBinding {
    public static final int HEAD = 0, BODY = 1, RIGHT_ARM = 2, LEFT_ARM = 3, RIGHT_LEG = 4, LEFT_LEG = 5;

    public final JointRig rig;
    /** Null for the head. */
    public final Joint joint;
    public final int kind;
    public final boolean slim;
    /** The torso the upper body swings around. */
    public final ModelPart body;

    private PartBinding(JointRig rig, Joint joint, int kind, boolean slim, ModelPart body) {
        this.rig = rig;
        this.joint = joint;
        this.kind = kind;
        this.slim = slim;
        this.body = body;
    }

    public boolean upper() {
        return kind == HEAD || kind == RIGHT_ARM || kind == LEFT_ARM;
    }

    private static final Map<ModelPart, PartBinding> BINDINGS = Collections.synchronizedMap(new WeakHashMap<>());

    public static PartBinding of(ModelPart part) {
        return part == null ? null : BINDINGS.get(part);
    }

    static void bind(PlayerModel<?> m, JointRig rig) {
        boolean slim = rig.rightArm != null && rig.rightArm.hx < 1.75f;
        put(rig, null, HEAD, slim, m.body, m.head, m.hat);
        put(rig, rig.torso, BODY, slim, m.body, m.body, m.jacket);
        put(rig, rig.rightArm, RIGHT_ARM, slim, m.body, m.rightArm, m.rightSleeve);
        put(rig, rig.leftArm, LEFT_ARM, slim, m.body, m.leftArm, m.leftSleeve);
        put(rig, rig.rightLeg, RIGHT_LEG, slim, m.body, m.rightLeg, m.rightPants);
        put(rig, rig.leftLeg, LEFT_LEG, slim, m.body, m.leftLeg, m.leftPants);
    }

    private static void put(JointRig rig, Joint j, int kind, boolean slim, ModelPart body, ModelPart... parts) {
        PartBinding b = new PartBinding(rig, j, kind, slim, body);
        for (ModelPart p : parts) if (p != null) BINDINGS.put(p, b);
    }
}
