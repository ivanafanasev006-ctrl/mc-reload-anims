package dev.mcreload.moves;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.function.DoubleSupplier;

/**
 * The moves, each tied to the keys of its animation (docs/moves.md). Times are game ticks from the
 * start of the move (20 a second); a move played faster than its animation has its ticks already
 * divided by the speed.
 *
 * <p>Both ends use the same table: the client to move the player and play the animation, the
 * server to make them untouchable or low at the right ticks.</p>
 */
public enum Move {
    /** R + A: push at 0.08 s, about 2.2 blocks by 0.29 s, untouchable from the push to the landing. */
    DODGE_LEFT("dodge_left", 1f, 13, 2, 6, -1, -1, Side.LEFT, MovesConfig::costDodge, 0.62f, 0.52f, 0.40f, 0.26f),
    /** R + D: the mirror of the left dodge. */
    DODGE_RIGHT("dodge_right", 1f, 13, 2, 6, -1, -1, Side.RIGHT, MovesConfig::costDodge, 0.62f, 0.52f, 0.40f, 0.26f),
    /** R + S: roll back, about 3 blocks by 0.5 s; low (0.6 block) while tucked, untouchable 0.1-0.45 s. */
    DODGE_BACK("dodge_back", 1f, 18, 2, 9, 2, 12, Side.BACK, MovesConfig::costRoll, 0.45f, 0.43f, 0.40f, 0.36f, 0.31f, 0.27f, 0.22f, 0.18f),
    /** R + W: roll forward, the same as the back roll. */
    DODGE_FRONT("dodge_front", 1f, 18, 2, 9, 2, 12, Side.FRONT, MovesConfig::costRoll, 0.45f, 0.43f, 0.40f, 0.36f, 0.31f, 0.27f, 0.22f, 0.18f),
    /**
     * Sprint + C: a slide along the look, faster than running; low from the first tick (a sprint
     * into a one-block gap must not hit its edge before the body drops). The animation
     * (1.7 s) plays 1.4 times faster, so its slide (0.15-1.2 s) lasts as long as the push does:
     * 4 blocks tapped, 5 held (then the player crawls on). See {@link #slideSpeed}.
     */
    SLIDE("slide", 1.4f, 25, -1, -1, 1, 18, Side.FRONT, MovesConfig::costSlide),
    /**
     * R + W at point blank to a mob of some size (a zombie, a skeleton, a pig, a sheep; not a
     * chicken): a vault over its back (hop_roll_dodge, docs/combat-spec.md, 5), quick and on the
     * run (author, 2026-10-29: "run, press, over, run on without a stop"). Crouch until 0.2 s (its
     * blow in this window is parried), the hand on its head, take-off at 0.2 s pressing it down, a
     * sideways roll over it, the foot shoving it back at 0.63 s, landing beyond it at 0.72 s and
     * running on. It cannot hurt the player until the landing. See {@link #HOP_TAKEOFF}.
     */
    HOP("hop_roll_dodge", 1f, 22, -1, -1, -1, -1, Side.FRONT, MovesConfig::costHop),
    /**
     * G with a bow, tapped: the volley (docs/combat-spec.md, 6.2; author, 2026-10-30, 23:01: "the bow's
     * ability is on G now, a hail of arrows: by default he slides back and looses 3 arrows; the 3 arrows
     * of G a fraction of a second slower, one after another: now they are so quick the hit does not
     * register, the arrow bounces off or pushes the mob and there is no time to aim a little higher").
     * A sharp step back down onto a knee, sliding on into the kneel (1.85 blocks in 0.1-0.55 s), and
     * three arrows 0.55 s apart (Bow.VOLLEY_SHOTS; the draws between them played slower,
     * MoveAnimator.pace), each at a marked mob in turn if there are any in sight (Marks). It was the
     * full draw and the left button, which marks mobs now. 10 s before G again (author, 2026-10-31).
     */
    BARRAGE("bow_barrage", 1f, 36, -1, -1, -1, -1, Side.BACK, MovesConfig::costBarrage,
            0.30f, 0.28f, 0.26f, 0.23f, 0.20f, 0.17f, 0.14f, 0.11f, 0.09f, 0.07f),
    /**
     * G held: the hail (author: "on G+, 5 to 10 arrows the way he looks, with a spread, not all to the
     * middle, and the slide back a block further"; "as quick as the volley"). 2.85 blocks back, then an
     * arrow every 4 ticks from 0.55 s (Bow.HAIL_FROM, HAIL_EVERY): five at least, on while G is held, ten
     * at most, each after the third for MovesConfig.costHailExtra; spread up to 8 degrees about the look,
     * or at the marked mobs in turn. 40 s before G again (author, 2026-10-31).
     */
    HAIL("bow_barrage", 1f, 53, -1, -1, -1, -1, Side.BACK, MovesConfig::costBarrage,
            0.40f, 0.38f, 0.35f, 0.32f, 0.29f, 0.26f, 0.23f, 0.20f, 0.17f, 0.14f, 0.11f),

    // ---- the Legolas stance (docs/combat-spec.md, 6.4): the bow fully drawn, R once. Each time the
    // draw is full again the arrow leaves by itself, then a dodge spinning round: to the side of A or
    // D, the way the keys point (S + A: back and to the left), almost in place with none. The halves
    // of bow_walk_around, 1 s each. It took the place of the old combat flow (author, 2026-10-31) and
    // waits MovesConfig.legolasCooldown from its end (the button let go) before it can be taken again.
    /** A (or every other dodge without it): the spin to the left (the first half), pushed like a side dodge, untouchable as one. */
    LEGOLAS_LEFT("bow_walk_around", Bow.shot(0, 20, -1, 17), 20, 2, 6, -1, -1, Side.LEFT, MovesConfig::costBowDodge,
            0.62f, 0.52f, 0.40f, 0.26f),
    /** D: the spin back to the right (the second half). */
    LEGOLAS_RIGHT("bow_walk_around", Bow.shot(20, 40, -1, 17), 20, 2, 6, -1, -1, Side.RIGHT, MovesConfig::costBowDodge,
            0.62f, 0.52f, 0.40f, 0.26f),

    // ---- the volley at the marked mobs (Marks; author, 2026-10-30, 23:01): the right button let go at
    // the full draw with marked mobs in sight, once in 25 s: an arrow at each in turn, the turns and
    // poses of the bow's old combat flow (bow_flow_*, 2026-10-28) played very fast, the player turned to
    // each. W, A, S, D: a spin forward, a roll to that side, a hop back that way; none: a spin and a pose
    // in turn, almost in place. The arrow leaves at the step's release, aimed at its mob by the server;
    // the next step may start a tick after it.
    /** A spin to the left (bow_flow_turn_left, twice as fast). */
    MARKED_SPIN_LEFT("bow_flow_turn_left", 2f, Bow.shot(0, 13, 6, 7), 8, 1, 4, -1, -1, Side.FRONT, () -> 0,
            0.30f, 0.25f, 0.15f, 0.08f),
    /** A spin to the right. */
    MARKED_SPIN_RIGHT("bow_flow_turn_right", 2f, Bow.shot(0, 13, 6, 7), 8, 1, 4, -1, -1, Side.FRONT, () -> 0,
            0.30f, 0.25f, 0.15f, 0.08f),
    /** A: a roll to the left, low. */
    MARKED_ROLL_LEFT("bow_flow_roll_left_shot", 2f, Bow.shot(0, 11, 5, 6), 7, 1, 5, 1, 5, Side.LEFT, () -> 0,
            0.50f, 0.45f, 0.35f, 0.25f, 0.15f),
    /** D: a roll to the right. */
    MARKED_ROLL_RIGHT("bow_flow_roll_right_shot", 2f, Bow.shot(0, 11, 5, 6), 7, 1, 5, 1, 5, Side.RIGHT, () -> 0,
            0.50f, 0.45f, 0.35f, 0.25f, 0.15f),
    /** S: a hop back. */
    MARKED_BACKSTEP("bow_flow_backstep_shot", 2f, Bow.shot(0, 10, 5, 6), 7, 1, 3, -1, -1, Side.BACK, () -> 0,
            0.35f, 0.30f, 0.20f, 0.10f),
    /** The old flow's poses, twice as fast: crouched side-on. */
    MARKED_CROUCH("bow_flow_crouch_shot", 2f, Bow.shot(3, -1, 5, 6), 7, -1, -1, -1, -1, Side.FRONT, () -> 0),
    /** Standing side-on. */
    MARKED_STAND("bow_flow_stand_shot", 2f, Bow.shot(3, -1, 5, 6), 7, -1, -1, -1, -1, Side.FRONT, () -> 0),
    /** Down onto one knee. */
    MARKED_KNEEL("bow_barrage", 2f, Bow.shot(8, -1, 2, 3), 4, -1, -1, -1, -1, Side.FRONT, () -> 0),

    // ---- sword moves (author, 2026-10-28)
    /**
     * A sword in hand, the left and the right button held together (author, 2026-10-30; it was S and
     * the left button held, the right button once): a dash into a lunge, the blade into the
     * belly (dash_strike). The run carries on and speeds up into the lunge (about 3.4 blocks by
     * 0.45 s, less if someone is in the way); the blade hits the first one it reaches on the way,
     * from 0.25 s to 0.55 s (Strikes.DASH_REACH_FROM, DASH_REACH_TO), with a crit, as a blow in a
     * jump does. The run follows the look and goes on to the mob's side. It neither stops nor slows
     * the player (author, 2026-10-29): from the stab the player runs on at once, the animation
     * playing out over the run. Then 30 s before the next one (MovesConfig.dashCooldownTicks).
     */
    DASH("dash_strike", 1f, 31, -1, -1, -1, -1, Side.FRONT, MovesConfig::costDash),
    /**
     * A weakened mob (a fifth of its health or less) at point blank, the left button held with a
     * sword: the finishing stab (finisher_stab; an axe has its own, FINISHER_AXE). Author, 2026-10-30:
     * as quick as the axe's, the blade into the belly, no lead-up: the free hand takes it by the
     * shoulder as the sword draws back, the blade goes in at 0.3 s and it dies then
     * (Strikes.FINISH_KILL), it is pulled out at 0.6 s (Strikes.FINISH_OUT: running on may break off
     * the rest), the stance again by 0.95 s. Not on bosses. Nothing hurts the player until the stab.
     */
    FINISHER("finisher_stab", 1f, 19, 0, Strikes.FINISH_KILL + 1, -1, -1, Side.FRONT, MovesConfig::costFinisher),
    /**
     * The same with an axe (author, 2026-10-30: "not stabbed into the belly like the sword, but struck
     * from above, at the head or just at the body, with a vertical swing"): the chop (finisher_chop).
     * The free hand takes it by the shoulder, the axe goes up behind the back and comes down onto it
     * by the neck at 0.58 s, killing it (Strikes.CHOP_HIT), on into its chest; it is wrenched out at
     * 0.9 s (Strikes.CHOP_OUT). Nothing hurts the player until the blow.
     */
    FINISHER_AXE("finisher_chop", 1f, 31, 0, Strikes.CHOP_HIT + 1, -1, -1, Side.FRONT, MovesConfig::costFinisher),
    /**
     * The same with a bow (author, after 0.9.1: "LMB at point blank as with the sword; he shoves the enemy and shoots it in
     * the body and it dies"; an arrow in the inventory needed): both hands shove it back at 0.18 s (Strikes.BOW_SHOVE), the
     * bow drawn, the arrow into its body at 0.6 s (BOW_KILL) kills it (bow_finisher, round11.py).
     */
    FINISHER_BOW("bow_finisher", 1f, 23, 0, Strikes.BOW_KILL + 1, -1, -1, Side.FRONT, MovesConfig::costFinisher),
    /**
     * With a crossbow ("he turns a whole turn, aims like a pistol with one hand and shoots a finishing arrow into the body
     * or the head"; an arrow needed): the turn, the arm out, the arrow into its head at 0.6 s (XBOW_KILL) kills it
     * (crossbow_finisher, round11.py).
     */
    FINISHER_XBOW("crossbow_finisher", 1f, 25, 0, Strikes.XBOW_KILL + 1, -1, -1, Side.BACK, MovesConfig::costFinisher,
            // the recoil carries the player back about 2 blocks from the shot (author, after 0.9.6: "a little further back")
            0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0.50f, 0.45f, 0.38f, 0.30f, 0.22f, 0.14f, 0.07f),

    // ---- the author's second message of 2026-10-29
    /**
     * W and Space held (or pressed), R at a mob of some size: the vault with a kick (hop_on_kick),
     * a cutscene. The vault over it as in hop_roll_dodge (landing at 0.72 s), the half turn to face
     * it (the camera with the player), a jump spinning a whole turn with the leg swung out, which
     * meets its back at 1.31 s (Kick.HIT: 1 heart, flung away), the landing at 1.46 s facing it
     * (author, 2026-10-29, fourth message), 2.0 s in all (was 2.45). The mob is held still until the
     * kick and nothing hurts the player until the landing.
     */
    KICK("hop_on_kick", 1f, 41, 0, Kick.LAND, -1, -1, Side.FRONT, MovesConfig::costKick),
    /**
     * W and Space held (or pressed), R with no mob to vault: the deep jump (dive_roll; author,
     * 2026-10-29, fourth message): a dive stretched out like into water, 3.4 blocks forward and the
     * feet 1.3 blocks up at the top (fifth message: "sharper, at least over a one-block obstacle"),
     * the hands take the ground at 0.55 s and a forward roll 1.6 blocks on (low and untouchable in
     * it, as a roll), up by 1.12 s (it was 1.35). Also from a jump already begun.
     */
    DIVE("dive_roll", 1f, 23, Move.DIVE_TOUCH, Move.DIVE_UP, Move.DIVE_TOUCH, Move.DIVE_UP, Side.FRONT, MovesConfig::costDive),
    /**
     * Ctrl (the fast run), W and Space down, R at a mob of some size: the roll kick (hop_roll_kick,
     * the author's draft of 2026-10-29, fifth message; the keys are a default): over it in a forward
     * flip, turned half round to its back in the air (the camera with the player), the kick into its
     * back at 0.8 s (Kick.RK_HIT, flung away as by hop_on_kick), the landing facing it at 0.95 s and a
     * back roll after it, away from it, 1.5 blocks (author, 2026-10-30: it rolled towards the mob); 1.9 s
     * in all. Nothing hurts the player until the landing.
     */
    ROLL_KICK("hop_roll_kick", 1f, 38, 0, Kick.RK_LAND, Kick.RK_ROLL_FROM, Kick.RK_ROLL_TO, Side.FRONT, MovesConfig::costRollKick),
    /**
     * The left button while sliding, a sword, an axe or a bow in hand (the bow since 2026-10-31): a
     * half turn clockwise with the blade out to the right (slide_under_attack; a bow held as it is), a
     * crit to everything it sweeps through at 0.3 s, and the push of a tapped slide carrying the player
     * on the way they slid, now backwards; they stand up facing the way they turned. Low like the slide
     * while it slides. 15 s before the next one.
     */
    SLIDE_ATTACK("slide_under_attack", 1f, 28, -1, -1, 0, 14, Side.FRONT, MovesConfig::costSlideAttack),
    /**
     * The right button while sliding with a bow (author, 2026-10-31: "like C + LMB, but the turn is not
     * 180 degrees but 360 round himself; he starts drawing the bow and sits on one knee like that"): off
     * the hip, a whole turn clockwise down onto the left knee by 0.5 s (bow_slide_shot; the slide carries
     * on and brakes to a stop, MovesConfig.slideShotBlocks), the bow drawing as vanilla draws it; then
     * held on the knee while the button is held, aiming where the player looks, creeping very slowly
     * (MovesConfig.kneelMoveFactor). Low while it comes up off the hip. 20 s before the next one.
     */
    SLIDE_SHOT("bow_slide_shot", 1f, Move.FOREVER, -1, -1, 0, 4, Side.FRONT, MovesConfig::costSlideShot),
    /**
     * The button let go (not before the knee): three arrows at once, side by side (Bow.SLIDE_SHOT_RELEASE),
     * as strong as the draw; then up calmly (bow_slide_shot from 0.5 s), moving breaks off the rest.
     */
    SLIDE_SHOT_END("bow_slide_shot", 1f, 23, -1, -1, -1, -1, Side.FRONT, () -> 0),
    /** R held while falling: tucked until the landing (fall_tuck, looped). */
    FALL_TUCK("fall_tuck", 1f, Move.FOREVER, -1, -1, -1, -1, Side.FRONT, () -> 0),
    /**
     * The landing out of the tuck: a roll forward (1.6 blocks, 0.08-0.5 s), then the heavy landing
     * on the right knee and both hands, a breath, the push up at 1.05 s (the stun ends: Fall.STAND),
     * up by 1.5 s (fall_roll; author, 2026-10-29, fourth message: the heavy landing merged in, up 50 %
     * faster: it was on the knee until 1.5 s, up by 1.9). The fall hurts 70 % less (Fall).
     */
    FALL_ROLL("fall_roll", 1f, 31, -1, -1, 2, 10, Side.FRONT, MovesConfig::costFallRoll),
    /**
     * Falling far enough to be hurt with a sword or an axe in hand (and R not held): the blade held
     * point down in both fists in front of the chin, ready to drive into the ground (plunge_fall,
     * looped; author, 2026-10-29, fifth message).
     */
    PLUNGE_FALL("plunge_fall", 1f, Move.FOREVER, -1, -1, -1, -1, Side.FRONT, () -> 0),
    /**
     * The landing out of it: down onto the right knee, the blade driven into the ground in front, a
     * short stun, the blade heaved out at 0.68 s (Plunge.STAND: moving breaks off the rest), up by
     * 1.0 s (plunge_land). The fall hurts 20 % less (author).
     */
    PLUNGE("plunge_land", 1f, 20, -1, -1, -1, -1, Side.FRONT, MovesConfig::costPlunge),
    /**
     * G with an axe in hand (author, 2026-10-30; it was S and the left button held, then the right
     * one): the berserker's whirlwind (axe_whirlwind; author, 2026-10-29, fifth message): a wind-up,
     * turns round with the axe out, carried forward like a top the way the camera looks (A, S, D do
     * nothing meanwhile), the blade hitting everything round once a turn (Whirl). Three turns (to
     * 1.7 s); G held, up to five (to 2.6 s), each more one for more energy; then the turning slows to a
     * stop facing ahead and the axe comes back in front (WHIRL_END, or the animation's own ending after
     * the fifth). 45 s before the next one.
     */
    WHIRL("axe_whirlwind", 1f, 60, -1, -1, -1, -1, Side.FRONT, MovesConfig::costWhirl),
    /** The whirlwind's ending, in place of its next turn when G is up: the slow-down and the stance (0.45 s). */
    WHIRL_END("axe_whirlwind_end", 1f, 9, -1, -1, -1, -1, Side.FRONT, () -> 0),
    /**
     * G held with an axe, 3 to 6 mobs about (author, 2026-11-10; in the evening: "EACH one 70 % of his health", the animation
     * built anew from his example; 20:00: "no second of the stance at the start; each blow a different one, the poses taking
     * turns as in the sword's chain finisher"): the combo, a blow for each mob, one after another (round19.py). This is its
     * first blow, a whole turn of the body, at once from the stance (axe_combo_open); then in turn the overhead chop, the
     * second turning blow, the chop across, the third, the chop again (Whirl.comboNext), one a mob, and AXE_COMBO_END. The
     * client turns and steps the player to each mob (AxeComboInput); Whirl.tripleTick its blows.
     */
    AXE_TRIPLE("axe_combo_open", 1f, 12, -1, -1, -1, -1, Side.FRONT, MovesConfig::costAxeTriple),
    /** The combo's blows after the first, one a mob, from the ready pose to it: the overhead chop, the second turning blow, the chop
     * down across from the right, the third turning blow (axe_combo_chop, _2, _diag, _3). */
    AXE_COMBO_CHOP("axe_combo_chop", 1f, 7, -1, -1, -1, -1, Side.FRONT, () -> 0),
    AXE_COMBO_2("axe_combo_2", 1f, 11, -1, -1, -1, -1, Side.FRONT, () -> 0),
    AXE_COMBO_DIAG("axe_combo_diag", 1f, 8, -1, -1, -1, -1, Side.FRONT, () -> 0),
    AXE_COMBO_3("axe_combo_3", 1f, 12, -1, -1, -1, -1, Side.FRONT, () -> 0),
    /** The combo's end: from the ready pose back to the stance (axe_combo_end). */
    AXE_COMBO_END("axe_combo_end", 1f, 9, -1, -1, -1, -1, Side.FRONT, () -> 0),
    /**
     * G with a sword in hand (author, 2026-10-30: "a quick turn round on the spot, the sword held out
     * turned flat"; and, after his video: "no stun: he just turns round and strikes in the moment; G
     * held, he may move any way and makes up to 3 turns, for more energy"): the sword's turn
     * (sword_spin), a short wind-up, a turn to the left from 0.1 to 0.4 s with the blade out flat,
     * cutting everything within reach as it passes (Spin); G held, up to two more of 0.25 s each,
     * each for more energy; then the turning slows to a stop facing ahead (SPIN_END, or the
     * animation's own ending after the third). The keys walk the player meanwhile. 20 s before the
     * next one [default].
     */
    SWORD_SPIN("sword_spin", 1f, 26, -1, -1, -1, -1, Side.FRONT, MovesConfig::costSwordSpin),
    /**
     * W + Space + G held with a sword (author, 2026-11-10): straight over head down, five turns of the blade along the look (no
     * steering), down on the knees at 1.65 s sliding a couple of blocks, up (round16.py, his example remade). Spin.AIR_* its keys.
     */
    AIR_SPIN("sword_air_spin", 1f, 54, -1, -1, -1, -1, Side.FRONT, MovesConfig::costAirSpin, Spin.airPush()),
    /** The sword's turn's ending, in place of its next turn when G is up: the slow-down and the stance (0.45 s). */
    SPIN_END("sword_spin_end", 1f, 9, -1, -1, -1, -1, Side.FRONT, () -> 0),

    // ---- the crossbow's attacks on the left button (author, 2026-11-08; Crossbow): each click the next
    // blow of the chain, in place (the keys do not walk the player through a blow)
    /** LMB 1, the push: the crossbow's front into the chest at 0.2 s (Crossbow.THRUST_HIT), the arm locked out, back (0.55 s). */
    XBOW_THRUST("crossbow_thrust", 1f, 14, -1, -1, -1, -1, Side.FRONT, MovesConfig::costCrossbowBlow),
    /** LMB 2, the slap: a whole turn clockwise, the crossbow's flat through the face at 0.35 s (SLAP_HIT), round to the front (0.75 s). */
    XBOW_SLAP("crossbow_slap", 1f, 18, -1, -1, -1, -1, Side.FRONT, MovesConfig::costCrossbowBlow),
    /** LMB 3, onto the spine: a whole turn the other way, the crossbow overhead and down onto the back at 0.55 s (SPINE_HIT), up (0.95 s). */
    XBOW_SPINE("crossbow_spine", 1f, 22, -1, -1, -1, -1, Side.FRONT, MovesConfig::costCrossbowBlow),
    /**
     * LMB 4, loaded: the hop back (about 2 blocks, 0.1-0.4 s, untouchable off the floor, as a side dodge) into a
     * crouch, the shot at 0.55 s (LEAP_SHOT), up (1.15 s). [default] distance
     */
    XBOW_SHOT("crossbow_leap_shot", 1f, 26, 2, 9, -1, -1, Side.BACK, MovesConfig::costCrossbowHop,
            0.62f, 0.62f, 0.58f, 0.52f, 0.44f, 0.34f, 0.24f, 0.14f),
    /** LMB 4, not loaded: the dash back, low along the floor (about 2.7 blocks, 0.1-0.45 s, untouchable meanwhile), up (0.72 s). [default] distance */
    XBOW_DASH("crossbow_dash_back", 1f, 17, 2, 9, -1, -1, Side.BACK, MovesConfig::costCrossbowHop,
            0.70f, 0.66f, 0.60f, 0.52f, 0.42f, 0.32f, 0.20f, 0.10f),

    // ---- the crossbow's G and the rolls (author, 2026-11-08, the second half; Crossbow): their animations from
    // tools/blockbench/refine_author_2026_10_29/round10.py, every number a [default] in the server config
    /**
     * G tapped, loaded: the hop back (about 2.6 blocks, untouchable off the floor) and three arrows spread at its top
     * at 0.3 s (Crossbow.VOLLEY_SHOT), down into a crouch, up (1.05 s).
     */
    XBOW_VOLLEY("crossbow_hop_volley", 1f, 24, 2, 10, -1, -1, Side.BACK, MovesConfig::costCrossbowVolley,
            0.62f, 0.62f, 0.58f, 0.52f, 0.44f, 0.34f, 0.24f, 0.14f),
    /**
     * G tapped, not loaded: a whole turn clockwise with the crossbow out at arm's length, every mob about the player
     * hit and flung away at 0.25 s (Crossbow.SPIN_SWEEP), then both hands shove off the nearest mob at 0.46 s
     * (SPIN_SHOVE) and the player springs back away from it, about 3.4 blocks (author, after 0.9.1: "a push off the
     * nearest enemy, away from it"; the client pushes the way from it), untouchable in the air; up (1.15 s).
     */
    XBOW_SPIN("crossbow_spin_push", 1f, 26, 10, 15, -1, -1, Side.BACK, MovesConfig::costCrossbowSpin,
            0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0.66f, 0.64f, 0.58f, 0.50f, 0.40f, 0.30f, 0.20f, 0.10f),
    /**
     * G held: the fan of arrows loading, an arrow every 0.5 s while G is held (Crossbow.FAN_EVERY), 8 at most; the
     * loading loops (Crossbow.FAN_LOOP_FROM - FAN_LOOP_TO) until G is let go, the player creeping.
     */
    XBOW_FAN("crossbow_fan", 1f, Move.FOREVER, -1, -1, -1, -1, Side.FRONT, MovesConfig::costCrossbowFan),
    /** G let go: the fan's shot, all its arrows at once at 0.1 s (Crossbow.FAN_SHOT), the recoil, the carry (0.7 s). */
    XBOW_FAN_SHOT("crossbow_fan", 1f, 17, -1, -1, -1, -1, Side.FRONT, () -> 0),
    /**
     * The rolls (the right button held, R; it ends when the button is let go or the crossbow put away): a roll the
     * way the keys point (back with none; about 2.4 blocks, untouchable and low in it), up on one knee, an arrow
     * along the look (Crossbow.ROLL_SHOT); the next roll once it is out (ROLL_NEXT). Played at Crossbow.ROLL_SPEED
     * (author, after 0.9.1: "a little slower, he rolls but not super fast"): its ticks are the animation's slowed.
     */
    XBOW_ROLL_BACK("crossbow_roll_back", Crossbow.ROLL_SPEED, 29, 2, 11, 2, 12, Side.BACK, MovesConfig::costCrossbowRoll,
            0.34f, 0.34f, 0.32f, 0.30f, 0.27f, 0.23f, 0.19f, 0.15f, 0.11f, 0.07f, 0.04f),
    XBOW_ROLL_FRONT("crossbow_roll_front", Crossbow.ROLL_SPEED, 29, 2, 11, 2, 12, Side.FRONT, MovesConfig::costCrossbowRoll,
            0.34f, 0.34f, 0.32f, 0.30f, 0.27f, 0.23f, 0.19f, 0.15f, 0.11f, 0.07f, 0.04f),
    XBOW_ROLL_LEFT("crossbow_roll_left", Crossbow.ROLL_SPEED, 29, 2, 11, 2, 12, Side.LEFT, MovesConfig::costCrossbowRoll,
            0.34f, 0.34f, 0.32f, 0.30f, 0.27f, 0.23f, 0.19f, 0.15f, 0.11f, 0.07f, 0.04f),
    XBOW_ROLL_RIGHT("crossbow_roll_right", Crossbow.ROLL_SPEED, 29, 2, 11, 2, 12, Side.RIGHT, MovesConfig::costCrossbowRoll,
            0.34f, 0.34f, 0.32f, 0.30f, 0.27f, 0.23f, 0.19f, 0.15f, 0.11f, 0.07f, 0.04f),

    // ---- the sword and the axe thrown (author, after 0.9.1; Thrown): weapon_throw_aim, weapon_throw, weapon_pull (round12.py)
    /** The left and the right button held with a sword or an axe: the weapon drawn back over the shoulder, held while they are. */
    THROW_AIM("weapon_throw_aim", 1f, Move.FOREVER, -1, -1, -1, -1, Side.FRONT, () -> 0),
    /** The left button let go: the throw over the top, the weapon gone at 0.1 s (Thrown.THROW_RELEASE). */
    THROW("weapon_throw", 1f, 16, -1, -1, -1, -1, Side.FRONT, MovesConfig::costThrow),
    /** The left or the right button at point blank to a mob with the player's weapon in it: pulled out at 0.3 s (Thrown.PULL_TICK). */
    WEAPON_PULL("weapon_pull", 1f, 17, -1, -1, -1, -1, Side.FRONT, () -> 0),

    // ---- the chain finisher (author, after 0.9.1; Chain, ChainInput): round15.py, the author's parts of 2026-11-09; the mobs
    // taken from the front (the blade over the shoulder into the back) and from behind (into the head) in turn
    /** Ctrl + W + Space + LMB with a sword: onto the first weak mob from the front, the blade into its back at 0.65 s (Chain.kill). */
    CHAIN_LEAP("chain_leap", 1f, 18, 0, 16, -1, -1, Side.FRONT, MovesConfig::costChain),
    /** On to the next weak mob near, turning clockwise, onto its back: the blade into its head at 0.85 s (each kill 10 energy). */
    CHAIN_HOP("chain_hop", 1f, 20, 0, 18, -1, -1, Side.FRONT, MovesConfig::costChain),
    /** The same, turning the other way (the shorter way round). */
    CHAIN_HOP_CCW("chain_hop_ccw", 1f, 20, 0, 18, -1, -1, Side.FRONT, MovesConfig::costChain),
    /** On to the one after, clockwise, in front of it: the blade into its back at 1.05 s. */
    CHAIN_HOP_STAB("chain_hop_stab", 1f, 22, 0, 20, -1, -1, Side.FRONT, MovesConfig::costChain),
    /** The same, turning the other way. */
    CHAIN_HOP_STAB_CCW("chain_hop_stab_ccw", 1f, 22, 0, 20, -1, -1, Side.FRONT, MovesConfig::costChain),
    /** None left (or six done): the back flip off the last one, down on the feet at 0.85 s, a back roll on the ground (as the roll kick's), up. */
    CHAIN_FLIP("chain_flip", 1f, 29, 0, 14, -1, -1, Side.BACK, () -> 0),

    // ---- the ledge (Ledge): the mechanic carries the player, the animations hold on to the edge
    /** Hanging by one hand, swinging (ledge_hang, looped from 1.25 s). */
    LEDGE_HANG("ledge_hang", 1f, Move.FOREVER, -1, -1, -1, -1, Side.FRONT, MovesConfig::costLedgeGrab),
    /**
     * Hanging, A held: a hop a block along the edge to the left, played 1.4 times faster (carried
     * 0.39-0.61 s); held, the next one starts as the second hand catches the edge (0.68 s): one after
     * another, almost without a pause (author, 2026-10-29, fourth message). The only way along the
     * edge (author, 2026-10-30, 23:01: "keep only the mode that was on R, so he only hops and does not
     * creep along the edge").
     */
    LEDGE_HOP_LEFT("ledge_hop_left", Move.HOP_SPEED, 29, -1, -1, -1, -1, Side.LEFT, MovesConfig::costLedgeHop),
    /** D held: the same to the right. */
    LEDGE_HOP_RIGHT("ledge_hop_right", Move.HOP_SPEED, 29, -1, -1, -1, -1, Side.RIGHT, MovesConfig::costLedgeHop),
    /**
     * Space while hanging, another edge looked at and within reach (author, 2026-10-30, 23:01: "if it is
     * far, aim by looking at it, it lights up green or red, then Space and he leaps there"): the leap
     * there (0.85 s; the mechanic carries the player along an arc from 0.22 s to 0.5 s, turning them to
     * its wall), into the hang on it.
     */
    LEDGE_LEAP("ledge_leap", 1f, 17, -1, -1, -1, -1, Side.FRONT, MovesConfig::costLedgeLeap),
    /** W + Space: a quick pull-up onto the top (0.75 s; author, 2026-10-29: as short as a dodge). */
    LEDGE_CLIMB("ledge_climb_up", 1f, 18, -1, -1, -1, -1, Side.FRONT, MovesConfig::costLedgeClimb),
    /** Ctrl + W + C at a drop: the player turns round and lowers themself over the edge into the hang (1.2 s). */
    LEDGE_DOWN("ledge_climb_down", 1f, 27, -1, -1, -1, -1, Side.FRONT, MovesConfig::costLedgeDown),
    /** Pulled up by a friend (the server starts it, when the friend takes their hand). */
    LEDGE_PULLED("ledge_pulled_up", 1f, 55, -1, -1, -1, -1, Side.FRONT, () -> 0),
    /** The friend: the right button on a player hanging below them; they kneel, take the hand and pull. */
    LEDGE_HELP("ledge_help_pull", 1f, 55, -1, -1, -1, -1, Side.BACK, MovesConfig::costLedgeHelp);

    /** The hops along the edge play this much faster than their animation (author: one after another). */
    public static final float HOP_SPEED = 1.4f;
    /** A held hop goes on into the next from this tick of it (the second hand on the edge, 0.95 s of the animation). */
    public static final int HOP_CHAIN = 14;

    /** The length of a move that lasts until it is ended (a hang, a fall): never runs out. */
    public static final int FOREVER = 1 << 24;

    /** The vault: take-off and landing ticks (0.2 s and 0.72 s of the animation; author, 2026-10-29: quick,
     *  "1 s or 1.25" in all, so the PC session's longer push-off, landing at 1.0 s, was taken back). */
    public static final int HOP_TAKEOFF = 4, HOP_LAND = 14;
    /** The vault shoves the mob back as the foot kicks off its back (0.63 s of the animation). */
    public static final int HOP_SHOVE = 13;
    /** The deep jump: the hands take the ground (0.55 s; its own jump ends there), out of the roll (0.88 s), moving breaks off the rest of the getting up. */
    public static final int DIVE_TOUCH = 11, DIVE_UP = 18;
    /** The climb: on the top (0.7 s), moving breaks off the rest (the player runs on, no stand-still). */
    public static final int CLIMB_ON_TOP = 14;
    /** The attack out of a slide: up from its crouch (0.8 s), moving breaks off the rest of the getting up. */
    public static final int SLIDE_ATTACK_UP = 16;
    /** The shot out of a slide: down on the knee (0.5 s of bow_slide_shot, where SLIDE_SHOT_END starts); up enough to walk on (0.9 s). */
    public static final int SLIDE_SHOT_SPIN = 10, SLIDE_SHOT_UP = 8;
    /** The vault lands this far beyond the mob it goes over. */
    public static final float HOP_BEYOND = 1.4f;
    /** Side dodges knock an arrow aside with the weapon in these ticks (0-0.21 s, docs/moves.md). */
    public static final int DEFLECT_FROM = 0, DEFLECT_TO = 4;

    /** The slide push: full speed (12 blocks a second), then an even slowdown to a stop at the chosen
     *  length. 4 blocks take 0.6 s, 5 blocks 0.7 s; running takes 0.7 and 0.9 s. */
    public static final float SLIDE_TOP = 0.6f, SLIDE_BRAKE = 0.035f;
    /** Slide tick at which a held C hands the player over to crawling (the end of the slide in the animation). */
    public static final int SLIDE_CRAWL_TICK = 17;
    /** The push starts here (the animation's 0.08 s for dodges and rolls, 0.15 s for the slide). */
    public static final int PUSH_TICK = 2;

    public final ResourceLocation animation;
    public final float animSpeed;
    /** Ticks until the move is over and the player is free again (the animation's length plus its fade). */
    public final int length;
    /** Untouchable from {@code iFrom} to {@code iTo} (inclusive); -1 for none. */
    public final int iFrom, iTo;
    /** A 0.6-block hitbox from {@code lowFrom} to {@code lowTo} (inclusive); -1 for none. */
    public final int lowFrom, lowTo;
    public final Side side;
    /** Bow moves: where the animation starts and stops, when the arrow leaves; null for the rest. */
    public final Bow.Shot shot;
    private final DoubleSupplier cost;
    /** Horizontal speed (blocks a tick) for each tick from {@link #PUSH_TICK}; empty for the slide. */
    private final float[] push;

    Move(String anim, float animSpeed, int length, int iFrom, int iTo, int lowFrom, int lowTo, Side side, DoubleSupplier cost, float... push) {
        this(anim, animSpeed, null, length, iFrom, iTo, lowFrom, lowTo, side, cost, push);
    }

    Move(String anim, Bow.Shot shot, int length, int iFrom, int iTo, int lowFrom, int lowTo, Side side, DoubleSupplier cost, float... push) {
        this(anim, 1f, shot, length, iFrom, iTo, lowFrom, lowTo, side, cost, push);
    }

    Move(String anim, float animSpeed, Bow.Shot shot, int length, int iFrom, int iTo, int lowFrom, int lowTo, Side side, DoubleSupplier cost, float... push) {
        this.animation = new ResourceLocation(KinetrixMoves.MODID, anim);
        this.shot = shot;
        this.animSpeed = animSpeed;
        this.length = length;
        this.iFrom = iFrom;
        this.iTo = iTo;
        this.lowFrom = lowFrom;
        this.lowTo = lowTo;
        this.side = side;
        this.cost = cost;
        this.push = push;
    }

    public float cost() {
        return (float) cost.getAsDouble();
    }

    /** What it costs after {@code was}: back to the hang on an edge already held (after a hop, the climb down) costs nothing. */
    public float cost(Move was) {
        if (this == LEDGE_HANG && was != null && was.ledge()) return 0f;
        return cost();
    }

    public boolean untouchable(int tick) {
        return iFrom >= 0 && tick >= iFrom && tick <= iTo;
    }

    public boolean low(int tick) {
        return lowFrom >= 0 && tick >= lowFrom && tick <= lowTo;
    }

    public boolean rolls() {
        return this == DODGE_BACK || this == DODGE_FRONT;
    }

    public boolean sideDodge() {
        return this == DODGE_LEFT || this == DODGE_RIGHT;
    }

    /** A move of the drawn bow (the Legolas stance's dodges): the bow stays drawn through it. */
    public boolean drawnBow() {
        return shot != null && !marked();
    }

    /** A step of the volley at the marked mobs: its arrow is the server's, at the step's mob. */
    public boolean marked() {
        return this == MARKED_SPIN_LEFT || this == MARKED_SPIN_RIGHT || this == MARKED_ROLL_LEFT || this == MARKED_ROLL_RIGHT
                || this == MARKED_BACKSTEP || this == MARKED_CROUCH || this == MARKED_STAND || this == MARKED_KNEEL;
    }

    /** G with a bow: the volley or the hail; the bow need not be drawn, the move takes it over. */
    public boolean volley() {
        return this == BARRAGE || this == HAIL;
    }

    public boolean legolas() {
        return this == LEGOLAS_LEFT || this == LEGOLAS_RIGHT;
    }

    /** A move made at someone (the vault, the kick, the finisher, a friend's hand): the client names them, the server checks them. */
    public boolean targeted() {
        return this == HOP || this == KICK || this == ROLL_KICK || finisher() || this == LEDGE_HELP || this == WEAPON_PULL
                || this == CHAIN_LEAP || chainHop() || axeCombo();
    }

    /** The axe's combo: its blows (each at a mob), not its end. */
    public boolean axeCombo() {
        return this == AXE_TRIPLE || this == AXE_COMBO_CHOP || this == AXE_COMBO_2 || this == AXE_COMBO_DIAG || this == AXE_COMBO_3;
    }

    /** The finisher: the sword's stab or the axe's chop. */
    public boolean finisher() {
        return this == FINISHER || this == FINISHER_AXE || this == FINISHER_BOW || this == FINISHER_XBOW;
    }

    /**
     * A strike started by the left button (or G): while it plays, until it may be broken off, the buttons do
     * nothing of vanilla's (no swing, no block dug, the crossbow not loaded mid-blow).
     */
    public boolean strike() {
        return this == DASH || finisher() || this == SLIDE_ATTACK || turning() || crossbow() || crossbowSkill() || thrown() || chain()
                || this == AIR_SPIN || axeCombo() || this == AXE_COMBO_END;
    }

    /** The chain finisher's leaps and its flip. */
    public boolean chain() {
        return this == CHAIN_LEAP || chainHop() || this == CHAIN_FLIP;
    }

    /** The chain's leap on to the next mob, either way round, from behind or from the front. */
    public boolean chainHop() {
        return this == CHAIN_HOP || this == CHAIN_HOP_CCW || this == CHAIN_HOP_STAB || this == CHAIN_HOP_STAB_CCW;
    }

    /** The throw (its aim, the throw itself) and the pull. */
    public boolean thrown() {
        return this == THROW_AIM || this == THROW || this == WEAPON_PULL;
    }

    /** G's moves: the sword's turns, the whirlwind, and their endings. */
    public boolean turning() {
        return this == WHIRL || this == WHIRL_END || this == SWORD_SPIN || this == SPIN_END;
    }

    /** A blow of the crossbow's chain on the left button (Crossbow). */
    public boolean crossbow() {
        return this == XBOW_THRUST || this == XBOW_SLAP || this == XBOW_SPINE || this == XBOW_SHOT || this == XBOW_DASH;
    }

    /** The crossbow's G (the hop with three arrows, the turn with the shove, the fan) and its rolls (Crossbow). */
    public boolean crossbowSkill() {
        return this == XBOW_VOLLEY || this == XBOW_SPIN || this == XBOW_FAN || this == XBOW_FAN_SHOT || crossbowRoll();
    }

    /** A roll of the crossbow's rolls (the right button held, R). */
    public boolean crossbowRoll() {
        return this == XBOW_ROLL_BACK || this == XBOW_ROLL_FRONT || this == XBOW_ROLL_LEFT || this == XBOW_ROLL_RIGHT;
    }

    /**
     * The whole body turns round in it, and in first person too (the arms go round the camera, the weapon
     * sweeping past the eyes): G's turns, the crossbow's slap and its blow onto the spine.
     */
    public boolean spins() {
        return turning() || this == XBOW_SLAP || this == XBOW_SPINE || this == XBOW_SPIN || this == FINISHER_XBOW;
    }

    /** On the ledge: hanging, moving along it, climbing onto it or off it, being pulled up (not the friend pulling). */
    public boolean ledge() {
        return this == LEDGE_HANG || this == LEDGE_HOP_LEFT || this == LEDGE_HOP_RIGHT || this == LEDGE_LEAP
                || this == LEDGE_CLIMB || this == LEDGE_DOWN || this == LEDGE_PULLED;
    }

    /** Hanging on the ledge by the hands, as a friend can pull them up and the keys can let go. */
    public boolean hanging() {
        return this == LEDGE_HANG;
    }

    /**
     * The mechanic carries the player through the corner of the edge, or round it (a hop round a
     * block's corner): no collisions on the server (Ledge.serverTick), which would stop the step along
     * the wall at the corner and put the player back.
     */
    public boolean throughEdge() {
        return this == LEDGE_CLIMB || this == LEDGE_DOWN || this == LEDGE_PULLED || this == LEDGE_HOP_LEFT || this == LEDGE_HOP_RIGHT
                || this == LEDGE_LEAP;
    }

    /**
     * This move may take over from {@code now} at any moment (not only once it has played out): the
     * attack out of a slide, the roll out of a tuck, the moves on a ledge from the hang and back.
     */
    public boolean follows(Move now) {
        if (now == null) return false;
        return switch (this) {
            case SLIDE_ATTACK, SLIDE_SHOT -> now == SLIDE;
            case SLIDE_SHOT_END -> now == SLIDE_SHOT;
            case FALL_ROLL -> now == FALL_TUCK;
            // R while falling with the blade held for the plunge: the tuck instead; the plunge's landing
            case FALL_TUCK -> now == PLUNGE_FALL;
            case PLUNGE -> now == PLUNGE_FALL;
            // G let go: the turns end with the next one's place
            case SPIN_END -> now == SWORD_SPIN;
            case WHIRL_END -> now == WHIRL;
            // the edge caught out of a fall, a slide or a roll off a drop, or back to the hang on it
            case LEDGE_HANG -> now == FALL_TUCK || now == PLUNGE_FALL || now.hanging() || now == LEDGE_HOP_LEFT || now == LEDGE_HOP_RIGHT
                    || now == LEDGE_LEAP || now == LEDGE_DOWN
                    || now == SLIDE || now == SLIDE_ATTACK || now == SLIDE_SHOT || now.rolls() || now.sideDodge() || now == DIVE;
            case LEDGE_HOP_LEFT, LEDGE_HOP_RIGHT -> now.hanging() || now == LEDGE_HOP_LEFT || now == LEDGE_HOP_RIGHT;
            case LEDGE_LEAP, LEDGE_CLIMB, LEDGE_PULLED -> now.hanging();
            // the volley at the marked: each step from the one before (the arrow left at its release)
            case MARKED_SPIN_LEFT, MARKED_SPIN_RIGHT, MARKED_ROLL_LEFT, MARKED_ROLL_RIGHT, MARKED_BACKSTEP, MARKED_CROUCH,
                 MARKED_STAND, MARKED_KNEEL -> now.marked();
            // the crossbow's chain: each blow from the one before it, once that has followed through (Crossbow.follows)
            case XBOW_SLAP -> now == XBOW_THRUST;
            case XBOW_SPINE -> now == XBOW_SLAP;
            case XBOW_SHOT, XBOW_DASH -> now == XBOW_SPINE;
            // the fan's shot out of its loading (each roll goes on from the one before once its arrow is out: freeFrom)
            case XBOW_FAN_SHOT -> now == XBOW_FAN;
            // the throw out of its aim; the finisher right after the pull (the author's "pulling it out, he drives it in again")
            case THROW -> now == THROW_AIM;
            case CHAIN_HOP, CHAIN_HOP_CCW, CHAIN_HOP_STAB, CHAIN_HOP_STAB_CCW, CHAIN_FLIP -> now == CHAIN_LEAP || now.chainHop();
            // the chain on its own after the sword's finisher, more weak mobs about (author, 2026-11-09)
            case CHAIN_LEAP -> now == FINISHER;
            // the axe's combo: each blow, and its end, from the blow before
            case AXE_COMBO_CHOP, AXE_COMBO_2, AXE_COMBO_DIAG, AXE_COMBO_3, AXE_COMBO_END -> now.axeCombo();
            case FINISHER, FINISHER_AXE -> now == WEAPON_PULL;
            default -> false;
        };
    }

    /** Started by the server on a player who did not ask (pulled up by a friend): their own client plays it when told. */
    public boolean serverStarted() {
        return this == LEDGE_PULLED;
    }

    /**
     * From first person the camera goes behind the player while it plays (Cutscene): only the volley at
     * the marked, aimed by itself (author, 2026-10-31: "the only place where third person may be is the
     * auto-aimed shooting at the marked"; the other attacks show the arms in first person,
     * FirstPersonArms). The kicks have their own cutscene.
     */
    public boolean cameraBehind() {
        return marked();
    }

    /**
     * Seen in first person too (the arms and what they hold), as the sword guard is (author, 2026-10-28;
     * 2026-10-31: "both arms seen in every attack", FirstPersonArms). Not the shot out of a slide's
     * getting up: its string hand comes back past the eyes; vanilla's bow is seen then. Nor the fan's shot (its recoil
     * throws the hand up into the eyes) and the crossbow's rolls (the body goes head over heels): vanilla's crossbow.
     */
    public boolean firstPerson() {
        return this == HOP || this == DASH || finisher() || this == SLIDE_ATTACK || this == FALL_ROLL || this == PLUNGE
                || turning() || ledge() || volley() || marked() || this == SLIDE_SHOT || crossbow()
                || this == XBOW_VOLLEY || this == XBOW_SPIN || this == XBOW_FAN || thrown();
    }

    /**
     * The tick from which moving, jumping or striking ends the move early: once its push is over
     * (a dodge or a roll gets up, the hop has landed), so it does not hold the player in place
     * while it plays out. -1: never (the slide decides by its own push, see the client).
     */
    public int recoverFrom() {
        return switch (this) {
            case DODGE_LEFT, DODGE_RIGHT, DODGE_BACK, DODGE_FRONT -> PUSH_TICK + push.length + 1;
            case HOP -> HOP_LAND + 1;
            case DASH -> Strikes.DASH_FREE;
            case KICK -> Kick.LAND + 5;
            case ROLL_KICK -> Kick.RK_ROLL_TO;
            case PLUNGE -> Plunge.STAND;
            // the turns go on (or end) as G says; their endings may be broken off once the turning has stopped
            case WHIRL_END, SPIN_END -> 2;
            case DIVE -> DIVE_UP;
            case LEDGE_CLIMB -> CLIMB_ON_TOP;
            case SLIDE_ATTACK -> SLIDE_ATTACK_UP;
            case SLIDE_SHOT_END -> SLIDE_SHOT_UP;
            case FALL_ROLL -> Fall.STAND;
            case FINISHER -> Strikes.FINISH_OUT;
            case FINISHER_BOW -> Strikes.BOW_KILL + 4;
            case THROW -> Thrown.THROW_RELEASE + 4;
            case CHAIN_FLIP -> Chain.ROLL_TO + 3;
            case WEAPON_PULL -> Thrown.PULL_TICK + 4;
            case FINISHER_XBOW -> Strikes.XBOW_KILL + 5;
            case FINISHER_AXE -> Strikes.CHOP_OUT;
            // the crossbow's blows from where the next one may follow; the hop once the shot is out, the dash once up
            case XBOW_THRUST -> Crossbow.THRUST_CHAIN;
            case XBOW_SLAP -> Crossbow.SLAP_CHAIN;
            case XBOW_SPINE -> Crossbow.SPINE_CHAIN;
            case XBOW_SHOT -> Crossbow.LEAP_UP;
            case XBOW_DASH -> Crossbow.DASH_UP;
            case XBOW_VOLLEY -> Crossbow.VOLLEY_UP;
            case XBOW_SPIN -> Crossbow.SPIN_UP;
            case XBOW_FAN_SHOT -> Crossbow.FAN_UP;
            case XBOW_ROLL_BACK, XBOW_ROLL_FRONT, XBOW_ROLL_LEFT, XBOW_ROLL_RIGHT -> Crossbow.ROLL_UP;
            default -> -1;
        };
    }

    /** The tick from which another move may cut in: once this one has played out (its fade aside), or earlier for some bow moves. */
    public int freeFrom() {
        if (crossbowRoll()) return Crossbow.ROLL_NEXT;
        return shot != null ? shot.freeFrom() : length - 3;
    }

    /** Push speed at this tick of a dodge or roll (blocks a tick), 0 outside the push. */
    public float pushSpeed(int tick) {
        int i = tick - PUSH_TICK;
        return i >= 0 && i < push.length ? push[i] : 0f;
    }

    /**
     * Slide speed for this tick: full speed, then braking evenly so the player stops after
     * {@code blocks}. {@code done} is how far the slide has gone so far.
     */
    public static float slideSpeed(float blocks, float done) {
        float left = blocks - done;
        if (left <= 0.02f) return 0f;
        return Math.min(SLIDE_TOP, Math.min(left, Mth.sqrt(2f * SLIDE_BRAKE * left) + SLIDE_BRAKE * 0.5f));
    }

    /** The direction of the push for a player facing {@code yaw} (degrees), unit length, horizontal. */
    public Vec3 direction(float yaw) {
        float r = yaw * Mth.DEG_TO_RAD;
        double sin = Mth.sin(r), cos = Mth.cos(r);
        // Minecraft's own strafe maths (Entity.moveRelative): forward = (-sin, cos), left = (cos, sin).
        return switch (side) {
            case FRONT -> new Vec3(-sin, 0, cos);
            case BACK -> new Vec3(sin, 0, -cos);
            case LEFT -> new Vec3(cos, 0, sin);
            case RIGHT -> new Vec3(-cos, 0, -sin);
        };
    }

    public static Move byId(int id) {
        Move[] all = values();
        return id >= 0 && id < all.length ? all[id] : null;
    }

    public enum Side { FRONT, BACK, LEFT, RIGHT }
}
