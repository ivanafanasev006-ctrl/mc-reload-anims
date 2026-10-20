package dev.mcreload.anim.client.module;

import dev.mcreload.anim.client.pose.PoseFrame;
import dev.mcreload.anim.client.pose.Signs;
import dev.mcreload.anim.client.rig.MotionState;
import dev.mcreload.anim.client.rig.PlayerRig;

/**
 * One family of procedural motion. Modules only ADD to the frame, scaled by smoothed weights,
 * so any number of them can be active at once and every transition is continuous.
 */
public interface AnimModule {
    boolean enabled();

    void apply(PlayerRig rig, MotionState m, PoseFrame f, Signs s, float partialTick);
}
