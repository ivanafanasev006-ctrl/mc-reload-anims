package dev.mcreload.moves;

/**
 * The poses a mob takes in our moves (drawn by client/MobPoses, sent by MovesNet.mobPose): its arms, legs, head and
 * whole body set for a while, on top of what it does the vanilla way. Humanoid mobs (zombies, skeletons, piglins...) are
 * posed limb by limb; the rest lean, fall and fly with their whole body only.
 */
public enum MobPose {
    NONE,
    /** Its striking arm caught by the wrist on the blade: held out in front, the body pulled after it. */
    CAUGHT,
    /** The arm wrenched behind its back, turned about, bent over (the block's finisher). */
    TWISTED,
    /** Kicked in the back: it stumbles, falls on its face and lies there (dying, not tipped onto its side). */
    FALL_FACE,
    /** Flung back by the blade and the body behind it (the perfect parry): arched, arms out, through the air. */
    FLUNG,
    /** Run through from behind and held on the blade, a hand on its shoulder (the living shield): sagging. */
    HOSTAGE,
    /** Kicked off the blade like a shot: flat through the air head first, then lying where it lands. */
    THROWN;

    public static MobPose byId(int id) {
        MobPose[] all = values();
        return id >= 0 && id < all.length ? all[id] : NONE;
    }
}
