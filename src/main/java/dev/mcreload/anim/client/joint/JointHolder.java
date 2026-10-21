package dev.mcreload.anim.client.joint;

/** Implemented by HumanoidModel (via mixin): the jointed rig built for that model, if any. */
public interface JointHolder {
    JointRig mcreload$getJoints();

    void mcreload$setJoints(JointRig rig);
}
