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
    THROWN,
    // ---- frozen in the time stop (TimeStop), slowing into it over a second
    /** Mid-stride at a run, the arms out. */
    FROZEN_RUN,
    /** In a deep leap at the player, stretched out, off the ground. */
    FROZEN_LEAP,
    /** Jumping down at him from two blocks up: a knee bent, the arms swung up over the head. */
    FROZEN_POUNCE,
    /** Roaring at him: the head up, the chest out, the arms thrown back. */
    FROZEN_RAGE,
    // ---- flying off as time runs again
    /** As if blown up: flung back high, arms and legs spread, onto its back. */
    BLASTED,
    /** Onto its back. */
    FALL_BACK,
    /** Tripping: a stagger, then onto its face. */
    STUMBLE;

    public static MobPose byId(int id) {
        MobPose[] all = values();
        return id >= 0 && id < all.length ? all[id] : NONE;
    }
}
