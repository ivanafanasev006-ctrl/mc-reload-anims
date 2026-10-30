"""The author's message of 2026-10-30: the moves it asks for that need animations.

- sword_spin: "on G a sword turns once round on the spot, fast (the sword held out turned flat, as
  in the attack out of a slide or as the axe turns round)". No draft: made from the description, on
  the author's axe_whirlwind (round5.py). After his video of 2026-10-30: up to three turns while G is
  held, and the ending its own animation (sword_spin_end); axe_whirlwind made even and up to five
  turns the same way (axe_whirlwind_end). See Turning below.
- finisher_stab: "the axe's finisher is now shorter than the sword's, in speed and in all; let the
  sword's be the same, but with a stab into the belly, and quick, without a lead-up". Made again on
  the axe's finisher_chop (round5.py): the free hand on the mob's shoulder as the sword draws back to
  the hip, the thrust into the belly at 0.3 s (the mob dies then, as at the axe's blow), the blade
  pulled out at 0.6 s, the stance by 0.95 s (it was 1.5 s, the mob dying at 0.95 s). Kept from the
  author's stab (animations/author_2026-10-28b.bbmodel): the step into the lunge on the left foot,
  the arm reaching straight out at the belly, the free hand holding the mob's shoulder.

The model does not travel: the mechanic places the player; every frame the lowest point of the body is
put on the floor where the feet are down.
"""
import math

import numpy as np

import kit
import round5
from kit import L
from round5 import ITEM, LEGS, TIP_LOCAL, aimed, aimed_flat, held, item_continuous, turned_between

kit.POINTS.setdefault('swordtip', (ITEM, tuple(TIP_LOCAL)))

# --------------------------------------------------------------------------------------------
# G: the sword's turn and the axe's whirlwind, held for more turns (author, 2026-10-30, the video)
# --------------------------------------------------------------------------------------------
#
# "G with a sword: no stun, he just turns round and strikes in the moment; G+, held: he may move any
# way and makes up to 3 turns, for more energy. The axe's animation should be more even in the lean of
# the body; G+ held: up to 5 turns." So each is two animations: the turns at an even speed, as many as
# G may give (3 and 5), with their own ending after the last; and the ending alone (*_end), which the
# mechanic starts in place of the next turn when G is up: it slows the turn down over its last 0.1 s
# (one tick's worth of the turn: it starts a tick before the turn would end) to face ahead again, and
# brings the stance back. The body stays upright (it leant into the turn and bobbed: it rocked), the
# feet step round under it.


class Turning:
    """A weapon turned round with the body: the wind-up to FROM, turns of TURN seconds at an even speed
    (the first, from the wind-up, of FIRST seconds with a ramp), up to TURNS of them; the ending (END_LEN:
    a 0.1 s slow-down, the weapon back in front by GUARD after it, the stance) after the last."""

    def __init__(self, name, frm, first, turn, turns, guard, end_len, wind, spin, guard_pose, step, item_up):
        self.name, self.FROM, self.FIRST, self.TURN, self.TURNS = name, frm, first, turn, turns
        self.GUARD, self.END_LEN = guard, end_len
        self.wind, self.spin, self.guard, self.step, self.item_up = wind, spin, guard_pose, step, item_up
        self.V = 360.0 / turn
        # the first turn makes up the wind-up's 40 degrees too, from a standstill: its ramp
        self.RAMP = 2 * (self.FIRST - 400.0 / self.V)
        self.SWITCH = self.turn_end(turns) - 0.05     # the last turn's ending starts here
        self.LEN = self.SWITCH + end_len

    def turn_end(self, k):
        return self.FROM + self.FIRST + (k - 1) * self.TURN

    def yaw(self, t):
        """The body's turn (degrees, to the left) at t."""
        if t <= self.FROM:
            return -SS_WIND * kit.smooth(t / self.FROM)
        x = t - self.FROM
        if x < self.RAMP:
            d = self.V * x * x / (2 * self.RAMP)
        else:
            d = self.V * (x - self.RAMP / 2)
        if t > self.SWITCH:
            # the slow-down: from full speed to a stop in 0.1 s, square at a whole turn
            y = min(t, self.SWITCH + 0.1) - self.SWITCH
            d = self.V * (self.SWITCH - self.FROM - self.RAMP / 2) + self.V * (y - y * y / 0.2)
        return -SS_WIND + d

    def frames(self, times, item=(0.0, 0.0, 0.0), out_rot=None, prev_item=None, prev_body=None, shift=0.0):
        """Poses at these times of the whole animation; the body's turn less `shift` degrees."""
        keys = [(0.0, {}, 'auto'), (self.FROM, self.wind, 'inout'), (self.FROM + 0.06, self.spin, 'out'),
                (self.SWITCH, self.spin, 'linear'), (self.SWITCH + 0.1 + self.GUARD, self.guard, 'inout'),
                (self.LEN, {}, 'inout')]
        d = L.Anim(self.name + '_design', self.LEN)
        for t, pose, ease in keys:
            d.key(t, pose, ease, whole=True)
        stop = self.SWITCH + 0.1
        out = []
        for t in times:
            p = kit.design(d, t)
            yaw = self.yaw(t)
            # the feet step round under the body, a step a half turn; the body upright
            inspin = kit.span(t, self.FROM - 0.03, self.FROM + 0.06) * (1 - kit.span(t, stop - 0.05, stop + 0.05))
            phase = math.radians(yaw) * 2
            rl, ll = p['right_leg'], p['left_leg']
            p['right_leg'] = (rl[0] + self.step * inspin * max(0.0, math.sin(phase)), rl[1], rl[2])
            p['left_leg'] = (ll[0] + self.step * inspin * max(0.0, -math.sin(phase)), ll[1], ll[2])
            rs, ls = p['right_shin'], p['left_shin']
            p['right_shin'] = (rs[0] - 1.5 * self.step * inspin * max(0.0, math.sin(phase)), 0, 0)
            p['left_shin'] = (ls[0] - 1.5 * self.step * inspin * max(0.0, -math.sin(phase)), 0, 0)
            p['body'] = (0.0, yaw - shift, 0.0)
            p = kit.grounded(p, bones=LEGS)
            p['body'] = tuple(kit.continuous(prev_body, p['body']))
            prev_body = p['body']
            # the weapon out along the arm, a little up, flat, its edge leading into the turn; the wrist
            # turns it so over the wind-up and back once the turn stops, the shortest way in the fist
            r = math.radians(yaw)
            outward = np.array((math.cos(r), self.item_up, -math.sin(r)))
            lead = np.array((-math.sin(r), 0.0, -math.cos(r)))
            if t <= stop:
                q = aimed_flat(p, outward, lead, start=item)
                item = item_continuous(item, q[ITEM])
                out_rot = item
                f = kit.smooth(t / (self.FROM - 0.02))
                p = held(p, item if f >= 1.0 else turned_between((0.0, 0.0, 0.0), item, f, prev_item))
            else:
                f = kit.smooth(min(1.0, (t - stop) / self.GUARD))
                p = held(p, turned_between(out_rot, (0.0, 0.0, 0.0), f, prev_item))
            prev_item = p[ITEM]
            out.append((t, p))
        return out, (item, out_rot, prev_item, prev_body)

    def whole(self):
        a = L.Anim(self.name, self.LEN, 'once')
        poses, _ = self.frames(kit.frames(0, self.LEN))
        for t, p in poses:
            a.key(t, p)
        a.tol_rot, a.tol_pos = 0.4, 0.08
        return a

    def ending(self):
        """The ending alone: from 0.05 s before a turn would end (full speed, a whole turn short of
        square less one tick's worth), the slow-down and the stance."""
        n = int(round(self.END_LEN * 24))
        times = [round(self.SWITCH + i / 24, 6) for i in range(n + 1)]
        # the weapon's grip as the turns hold it, from the frame just before
        _, state = self.frames(kit.frames(0, self.SWITCH))
        poses, _ = self.frames(times, item=state[0], out_rot=state[1], prev_item=state[2],
                               shift=360.0 * self.TURNS)
        a = L.Anim(self.name + '_end', self.END_LEN, 'once')
        for t, p in poses:
            a.key(round(t - self.SWITCH, 6), p)
        a.tol_rot, a.tol_pos = 0.4, 0.08
        return a


#: The wind-up turns the body this far to the right (degrees); the first turn makes up for it (Spin.BLADE_FROM).
SS_WIND = 40.0

#: The sword (Spin): the wind-up to 0.1 s, the first turn to 0.4 s (0.3 s), then 0.25 s a turn, up to 3;
#: the ending 0.45 s (stopped 0.1 s in, the sword in front 0.18 s after, the stance).
SWORD = Turning('sword_spin', frm=0.1, first=0.3, turn=0.25, turns=3, guard=0.18, end_len=0.45,
                wind={'torso': (0, -24, 0), 'chest': (-6, 0, 0), 'head': (-4, 20, 0),
                      'right_leg': (14, 0, 10), 'right_shin': (-26, 0, 0), 'left_leg': (10, 0, -10), 'left_shin': (-22, 0, 0),
                      'right_arm': (-20, 0, 74), 'right_forearm': (8, 0, 0), 'left_arm': (30, 0, -30), 'left_forearm': (36, 0, 0)},
                spin={'torso': (0, 0, 0), 'chest': (-3, 0, 0), 'head': (-2, 0, 0),
                      'right_leg': (10, 0, 8), 'right_shin': (-18, 0, 0), 'left_leg': (10, 0, -8), 'left_shin': (-18, 0, 0),
                      'right_arm': (6, 0, 88), 'right_forearm': (2, 0, 0), 'left_arm': (6, 0, -78), 'left_forearm': (10, 0, 0)},
                guard_pose={'torso': (0, 6, 0), 'chest': (-4, 0, 0), 'head': (0, -4, 0),
                            'right_leg': (8, 0, 4), 'right_shin': (-14, 0, 0), 'left_leg': (8, 0, -4), 'left_shin': (-14, 0, 0),
                            'right_arm': (42, 0, 12), 'right_forearm': (34, 0, 0), 'left_arm': (18, 0, -12), 'left_forearm': (28, 0, 0)},
                step=8.0, item_up=0.12)

#: The axe (Whirl): the wind-up to 0.25 s, the first turn to 0.8 s (0.55 s), then 0.45 s a turn, up to
#: 5 (3 if G is let go); the ending 0.45 s. "Not very fast, but not sluggish", as it was (0.5 s a turn).
AXE = Turning('axe_whirlwind', frm=0.25, first=0.55, turn=0.45, turns=5, guard=0.15, end_len=0.45,
              wind={'body': (0, 0, 0), 'torso': (0, -30, 0), 'chest': (-8, 0, 0), 'head': (-4, 25, 0),
                    'right_leg': (18, 0, 12), 'right_shin': (-34, 0, 0), 'left_leg': (12, 0, -12), 'left_shin': (-28, 0, 0),
                    'right_arm': (-25, 0, 70), 'right_forearm': (10, 0, 0), 'left_arm': (35, 0, -35), 'left_forearm': (40, 0, 0)},
              spin={'torso': (0, 0, 0), 'chest': (-3, 0, 0), 'head': (-2, 0, 0),
                    'right_leg': (10, 0, 10), 'right_shin': (-20, 0, 0), 'left_leg': (10, 0, -10), 'left_shin': (-20, 0, 0),
                    'right_arm': (8, 0, 86), 'right_forearm': (4, 0, 0), 'left_arm': (8, 0, -80), 'left_forearm': (10, 0, 0)},
              guard_pose={'torso': (0, 0, 0), 'chest': (-4, 0, 0), 'head': (0, 0, 0),
                          'right_leg': (8, 0, 4), 'right_shin': (-14, 0, 0), 'left_leg': (8, 0, -4), 'left_shin': (-14, 0, 0),
                          'right_arm': (40, 0, 10), 'right_forearm': (35, 0, 0), 'left_arm': (20, 0, -12), 'left_forearm': (30, 0, 0)},
              step=10.0, item_up=0.18)


def sword_spin():
    """The wind-up: down a little on the knees, the chest turned away to the right, the sword arm
    drawn back out to the side; the turns: the whole body round to the left, upright, the sword arm
    straight out at shoulder height with the blade out along it, flat, its edge leading, the free arm
    out for balance, the feet stepping round under it; the ending: the turn slows to a stop facing
    ahead, the blade swings in front and the stance comes back."""
    return SWORD.whole()


def sword_spin_end():
    return SWORD.ending()


def axe_whirlwind():
    """As round5's (the author's design of 2026-10-29, the axe flat of 2026-10-30), made even: the body
    upright through the turns (it leant 7 degrees into the turn and bobbed, and as it turned the lean
    rocked it), the steps a little smaller; up to five turns, the ending its own animation too."""
    return AXE.whole()


def axe_whirlwind_end():
    return AXE.ending()


# --------------------------------------------------------------------------------------------
# finisher_stab: quick, the blade into the belly
# --------------------------------------------------------------------------------------------

#: Times (s): the free hand on the mob's shoulder and the sword drawn back to the hip (FS_DRAW), the
#: thrust into its belly (FS_HIT: Strikes.FINISH_KILL, the mob dies), a little deeper as the body
#: leans in (FS_DEEP), held while the mob sags on it, pulled out (FS_PULL: Strikes.FINISH_OUT,
#: running on may break off the rest), the stance by FS_LEN. The thrust takes 0.12 s from the draw:
#: two and a half of the game's ticks, so it is seen, not skipped.
FS_DRAW, FS_HIT, FS_DEEP, FS_PULL, FS_LEN = 0.18, 0.3, 0.36, 0.6, 0.95
MOB_AT = round5.MOB_AT
#: Where the blade's point goes, from the front of the mob's belly as it is then: the sword is longer
#: than the mob is deep at point blank, so it goes in at the belly and its point out at the back (as
#: the author's stab did), a little further as the body leans in.
HIT_OFFSET, DEEP_OFFSET = np.array((0.0, -0.5, -7.0)), np.array((0.0, -1.0, -8.5))
#: The arm's turn about its own length is held near its design, the elbow only bends.
ARM_LIMITS = {('right_arm', 1): (-50.0, 40.0), ('right_arm', 2): (-30.0, 40.0), ('right_forearm', 0): (0.0, 120.0)}


def _mob(t):
    """The mob's own pose: stooping, weakened; at the thrust it doubles over the blade, the head down,
    the knees giving, and sags onto it."""
    weak = {'chest': (-9, 0, 0), 'head': (-8, 0, 0), 'right_arm': (8, 0, 5), 'right_forearm': (14, 0, 0),
            'left_arm': (8, 0, -5), 'left_forearm': (14, 0, 0), 'right_leg': (5, 0, 2), 'right_shin': (-8, 0, 0),
            'left_leg': (5, 0, -2), 'left_shin': (-8, 0, 0)}
    struck = {**weak, 'body': (6, 0, 0), 'chest': (-26, 0, 0), 'head': (-22, 0, 0),
              'right_arm': (-18, 0, 8), 'right_forearm': (40, 0, 0), 'left_arm': (-18, 0, -8), 'left_forearm': (40, 0, 0),
              'right_leg': (14, 0, 3), 'right_shin': (-26, 0, 0), 'left_leg': (14, 0, -3), 'left_shin': (-26, 0, 0)}
    sag = {**struck, 'body': (10, 0, 0), 'chest': (-30, 0, 0), 'head': (-28, 0, 0), 'right_leg': (34, 0, 4), 'right_shin': (-66, 0, 0),
           'left_leg': (34, 0, -4), 'left_shin': (-66, 0, 0), 'right_arm': (-4, 0, 12), 'left_arm': (-4, 0, -12)}
    d = L.Anim('mob', FS_LEN)
    for tk, pose, ease in ((0.0, weak, 'auto'), (FS_HIT, weak, 'auto'), (FS_HIT + 0.08, struck, 'out'),
                           (FS_PULL + 0.12, sag, 'inout'), (FS_LEN, sag, 'auto')):
        d.key(tk, pose, ease, whole=True)
    return kit.grounded(kit.design(d, t))


def _belly(t, offset):
    """The mob's belly (as it is at t), moved by offset (world px)."""
    return kit.pt(kit.fk(kit.as_partner(_mob(t), MOB_AT)), 'belly2') + offset


def _point_onto(pose, point):
    """The right arm solved so the blade's point is at `point`, the grip as the pose holds it."""
    q, err, x = kit.solve(pose, [('swordtip', np.asarray(point, float), 1.0)], kit.arm_vary('right'), ARM_LIMITS,
                          reg=[0.08, 0.1, 0.08, 0.08])
    return q, err


def stab_grip(pose):
    """The sword turned in the fist to go on along the forearm (a thrust), from the game's grip
    (the blade up out of the fist when the arm points forward)."""
    W = kit.fk(pose)
    fore = kit.pt(W, 'rhand') - kit.pt(W, 'relbow')
    return aimed(pose, fore)[ITEM]


def finisher_stab():
    """Straight into it: the free hand takes the mob by the shoulder as the sword draws back to the
    hip, the point forward, the body coiled back on the right foot; the thrust: a step onto the left
    foot, the hips and the right shoulder driving forward behind the arm, which goes out straight and
    puts the blade into the belly; a little deeper as the body leans in; held while the mob doubles
    over it and sags, the hand on its shoulder holding it; the sword pulled out, the hand shoving the
    mob off, and the stance comes back."""
    stance = {'right_leg': (-12, 0, 4), 'right_shin': (-10, 0, 0), 'left_leg': (16, 0, -3), 'left_shin': (-14, 0, 0)}
    draw = {**stance, 'body@p': (0, 0, 1.0), 'torso': (0, -22, 0), 'chest': (-2, 0, 0), 'head': (0, 14, 0),
            'right_arm': (-30, 0, 8), 'right_forearm': (95, 0, 0)}
    hit = {'body': (-3, 0, 0), 'body@p': (0, 0, -2.0), 'torso': (0, 14, 0), 'chest': (-10, 0, 0), 'head': (4, -8, 0),
           'right_arm': (75, 0, 2), 'right_forearm': (8, 0, 0),
           'right_leg': (-22, 0, 4), 'right_shin': (-6, 0, 0), 'left_leg': (30, 0, -3), 'left_shin': (-44, 0, 0)}
    deep = {**hit, 'body@p': (0, 0, -2.6), 'chest': (-14, 0, 0), 'torso': (0, 16, 0), 'head': (6, -8, 0)}
    stuck = {**deep, 'chest': (-16, 0, 0), 'body@p': (0, 0, -2.4)}
    # out: drawn straight back along the blade's line to the hip, the hand shoving the mob off
    pull = {**draw, 'body@p': (0, 0, 0.0), 'torso': (0, -14, 0), 'chest': (-6, 0, 0), 'head': (2, 8, 0),
            'right_arm': (-18, 0, 8), 'right_forearm': (90, 0, 0), 'left_arm': (68, 0, -8), 'left_forearm': (6, 0, 0)}
    back = {'chest': (-4, 0, 0), 'right_arm': (24, 0, 10), 'right_forearm': (32, 0, 0), 'left_arm': (14, 0, -8),
            'left_forearm': (20, 0, 0), 'right_leg': (-6, 0, 3), 'right_shin': (-6, 0, 0), 'left_leg': (8, 0, -2), 'left_shin': (-8, 0, 0)}

    # the thrust and the push on: the arm solved, the sword along the forearm, so the point goes into
    # the belly, then deeper (the stuck pose keeps that arm)
    grip = stab_grip({'right_arm': (80, 0, 0)})
    hit, deep, stuck = (kit.grounded(x, bones=LEGS) for x in (hit, deep, stuck))
    q, err_hit = _point_onto(held(hit, grip), _belly(FS_HIT, HIT_OFFSET))
    hit['right_arm'], hit['right_forearm'] = q['right_arm'], q['right_forearm']
    q, err_deep = _point_onto(held(deep, grip), _belly(FS_DEEP, DEEP_OFFSET))
    deep['right_arm'], deep['right_forearm'] = q['right_arm'], q['right_forearm']
    stuck['right_arm'], stuck['right_forearm'] = deep['right_arm'], deep['right_forearm']

    keys = [(0.0, {}, 'auto'), (FS_DRAW, draw, 'out'), (FS_HIT, hit, 'in'), (FS_DEEP, deep, 'out'),
            (0.5, stuck, 'inout'), (FS_PULL, pull, 'out'), (0.78, back, 'inout'), (FS_LEN, {}, 'inout')]
    # the free hand on the mob's shoulder from the draw until the blade is out (solved at each key)
    for i, (tk, pose, ease) in enumerate(keys):
        if FS_DRAW <= tk <= 0.5:
            keys[i] = (tk, round5._left_hand_on(pose, _mob(tk)), ease)
    d = L.Anim('stab_design', FS_LEN)
    for tk, pose, ease in keys:
        d.key(tk, pose, ease, whole=True)

    rest = (0.0, 0.0, 0.0)

    def grip_at(t):
        if t <= FS_PULL:
            return turned_between(rest, grip, kit.smooth(t / FS_DRAW))
        return turned_between(grip, rest, kit.smooth((t - FS_PULL) / (0.82 - FS_PULL)))

    a = L.Anim('finisher_stab', FS_LEN, 'once')
    prev_item = None
    for t in kit.frames(0, FS_LEN):
        p = kit.grounded(kit.design(d, t), bones=LEGS)
        p = held(p, item_continuous(prev_item, grip_at(t)))
        prev_item = p[ITEM]
        p.update(kit.as_partner(_mob(t), MOB_AT))
        a.key(t, p)
    a.tol_rot, a.tol_pos = 0.4, 0.08
    a.stab_error = (err_hit, err_deep)
    return a
