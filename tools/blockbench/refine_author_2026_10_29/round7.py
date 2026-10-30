"""The author's video of 2026-10-30, 23:01: "the bug is still there that does nonsense with the item:
it teleports forward and hangs in the air; in the fall it is fixed, in the deep dodge it is not".

The rolls: the author's dodge_front and dodge_back turn the sword in the fist -90 about x while the body
is tucked, and every roll made on them since took that turn along (dive_roll, fall_tuck, fall_roll,
hop_roll_kick). On the game's grip (the blade out of the fist across the forearm) that turn is about
the forearm's own length: the blade stands out to the side, level, a fist's length away from the
body, and as the body turns over it the weapon seems to hang in the air beside it (Blockbench shows
the same: tools/blockbench/kinetrix_rig.py preview). The intent, a blade out of the way of the roll,
is kept: the blade now lies along the forearm, its point at the elbow, flat against the arm, while
the body is tucked; it comes into the fist's grip again as the body opens out.
"""
import numpy as np

import kit
from kit import L, kr
from round5 import ITEM, aimed, held, turned_between


def _pose(chans, t):
    """The animation's pose at t, the item left out (it is what is made here)."""
    out = {}
    for bone, ch in chans.items():
        if bone.endswith('2') or bone == ITEM:
            continue
        if 'rotation' in ch:
            out[bone] = tuple(float(v) for v in kr.sample(ch['rotation'], t))
        if 'position' in ch:
            out[bone + '@p'] = tuple(float(v) for v in kr.sample(ch['position'], t))
    return out


def tucked(pose, start=(0.0, 0.0, 0.0)):
    """The pose with the blade along the right forearm, its point at the elbow."""
    W = kit.fk(pose)
    return aimed(pose, kit.pt(W, 'relbow') - kit.pt(W, 'rhand'), start)


def tuck_blade(model, plan):
    """The blade of each animation named in plan ({name: weight(t)}) keyed again, every frame: tucked
    along the forearm as far as weight(t) says (1: tucked, 0: the fist's grip), the grip in the fist."""
    m = kr.Model(None, data=model)
    uuid = L.bone_uuids(model)[ITEM]
    for a in model['animations']:
        weight = plan.get(a['name'])
        if weight is None:
            continue
        chans = m.channels(a)
        rots, poss = [], []
        prev, start = None, (0.0, 0.0, 0.0)
        for t in kit.frames(0, a['length']):
            pose = _pose(chans, t)
            w = min(1.0, max(0.0, weight(t)))
            if w < 1e-3:
                rot, pos = (0.0, 0.0, 0.0), (0.0, 0.0, 0.0)
            else:
                start = tucked(pose, start)[ITEM]
                rot = turned_between((0.0, 0.0, 0.0), start, w, prev)
                pos = held(pose, rot)[ITEM + '@p']
            prev = rot
            rots.append(L.kf('rotation', t, np.asarray(rot, float), 'linear'))
            poss.append(L.kf('position', t, np.asarray(pos, float), 'linear'))
        anim = a['animators'].setdefault(uuid, {'name': ITEM, 'type': 'bone', 'rotation_global': False,
                                                'quaternion_interpolation': False, 'keyframes': []})
        anim['keyframes'] = rots + poss


def _in_out(t_in, t_out_from, t_out_to, t_from=0.0):
    return lambda t: kit.span(t, t_from, t_in) * (1.0 - kit.span(t, t_out_from, t_out_to))  # noqa: E731


#: The rolls and how long each is tucked (seconds).
#: dodge_front / dodge_back (0.75 s): tucked by 0.15 s as the author turned it, out from 0.5 to 0.7 s.
ROLLS_27 = {'dodge_front': _in_out(0.15, 0.5, 0.7), 'dodge_back': _in_out(0.15, 0.5, 0.7)}


def rolls_29():
    """dive_roll (out of the roll by landings.ROLL_END), fall_tuck (all of it), fall_roll (tucked
    from the tuck, out by the kneel)."""
    import landings
    import moves
    return {'dive_roll': _in_out(landings.TAKEOFF, landings.ROLL_END - 0.1, landings.ROLL_END + 0.12, landings.CROUCH),
            'fall_tuck': lambda t: 1.0,
            'fall_roll': lambda t: 1.0 - kit.span(t, moves.ROLL_TO, moves.HANDS_DOWN + 0.2)}


def rolls_29b():
    """hop_roll_kick: tucked through the flip, the kick and the back roll; out as it comes up."""
    import round5
    return {'hop_roll_kick': _in_out(round5.RK_TAKEOFF, round5.RK_ROLL_TO, round5.RK_ROLL_TO + 0.25)}


# --------------------------------------------------------------------------------------------
# The ledge: the leap to an edge looked at (ledge_leap)
# --------------------------------------------------------------------------------------------
#
# The author, 2026-10-30, 23:01: "climb onto the block above or below or the next one if it is close;
# if it is far, aim by looking at it, it lights up green or red, then Space and he leaps there; keep
# only the mode that was on R, so he only hops and does not creep along the edge". The leap is one
# animation for every way (up, down, aside, across a gap, round to a wall behind): the mechanic carries
# the player from the hang to the other edge's hang along an arc between LEAP_PUSH and LEAP_CATCH and
# turns them to face its wall; the body gathers against the wall, pushes off with the feet and the
# arms, flies with the arms reaching ahead over the head, the hands take the new edge, the legs swing
# on under it and settle. It ends in the two-handed hang the hops end in.

LEAP_GATHER, LEAP_PUSH, LEAP_CATCH, LEAP_LEN = 0.14, 0.22, 0.5, 0.85


def ledge_leap():
    import ledge
    hang = ledge.HANG2
    tuck = kit.keys((0.0, 0.0), (LEAP_GATHER, 1.0), (LEAP_PUSH, 0.15), (0.32, 0.0), (LEAP_CATCH, 0.3),
                    (0.58, 0.55), (0.72, 0.15), (LEAP_LEN, 0.0))
    lift = kit.keys((0.0, 0.0), (LEAP_GATHER, 3.0), (LEAP_PUSH, 4.0), (LEAP_CATCH, 0.5), (0.56, -1.5), (0.68, 0.3),
                    (LEAP_LEN, 0.0))
    elbow = kit.keys((0.0, 2.0), (LEAP_GATHER, 42.0), (LEAP_PUSH, 14.0), (0.36, 8.0), (LEAP_CATCH, 10.0), (0.56, 30.0),
                     (0.7, 8.0), (LEAP_LEN, 2.0))
    # the swing about the hands after the catch: the legs carry on away from the wall, back, settle
    swing = kit.keys((0.0, 0.0), (LEAP_CATCH, 0.0), (0.62, -12.0), (0.74, 3.0), (LEAP_LEN, 0.0))
    # the arms let go and fly reaching ahead over the head (0 on the edge, 1 free)
    free = kit.keys((0.0, 0.0), (LEAP_PUSH - 0.03, 0.0), (LEAP_PUSH + 0.04, 1.0), (LEAP_CATCH - 0.05, 1.0),
                    (LEAP_CATCH, 0.0), (LEAP_LEN, 0.0))
    head = kit.keys((0.0, 14.0), (LEAP_GATHER, 22.0), (0.3, 28.0), (LEAP_CATCH, 20.0), (LEAP_LEN, 14.0))
    trail = kit.keys((0.0, 0.0), (LEAP_PUSH, 0.0), (0.3, 1.0), (LEAP_CATCH - 0.04, 1.0), (LEAP_CATCH + 0.04, 0.0),
                     (LEAP_LEN, 0.0))
    prev = {}

    def pose(t):
        p = dict(hang)
        tk, tr = tuck(t), trail(t)
        # gathered: the knees up, the soles against the wall; flying: the legs trail a little bent
        p['right_leg'] = (5 + 42 * tk - 10 * tr, 0, 1.5)
        p['left_leg'] = (8 + 46 * tk - 4 * tr, 0, -1.5)
        p['right_shin'] = (-10 - 72 * tk - 22 * tr, 0, 0)
        p['left_shin'] = (-16 - 76 * tk - 36 * tr, 0, 0)
        p['head'] = (head(t), 0, 0)
        p['right_forearm'] = (elbow(t), 0, 0)
        p['left_forearm'] = (elbow(t), 0, 0)
        p = kit.shifted(p, (0, lift(t), 0))
        mid = (ledge.grip(-6.5) + ledge.grip(6.5)) / 2
        p = kit.turned_about_point(p, kit.rot_of((swing(t), 0, 0)), mid)
        p['body'] = tuple(kit.continuous(prev.get('body'), p['body']))
        prev['body'] = p['body']
        vary = kit.arm_vary('right') + kit.arm_vary('left') + [('body@p', 0), ('body@p', 1), ('body@p', 2)]
        b = {**kit.arm_bounds('right', bend=(0, 90)), **kit.arm_bounds('left', bend=(0, 90))}
        pref = [*hang['right_arm'], elbow(t), *hang['left_arm'], elbow(t), *p['body@p']]
        reg = [0.004] * 8 + [0.05] * 3
        held, err, x = kit.solve(p, [('rhand', ledge.grip(6.5), 1), ('lhand', ledge.grip(-6.5), 1)], vary, b, reg=reg,
                                 prefer=pref, start=prev.get('x'))
        prev['x'] = x
        f = free(t)
        if f < 1e-3:
            return held
        # flying: the arms straight up and a little out, reaching for the edge ahead
        fly = dict(held)
        fly['right_arm'], fly['left_arm'] = (176, -2, -12), (176, 2, 12)
        fly['right_forearm'], fly['left_forearm'] = (10, 0, 0), (10, 0, 0)
        out = dict(held)
        for k in ('right_arm', 'left_arm', 'right_forearm', 'left_forearm'):
            out[k] = tuple(kit.lerp(np.asarray(held[k], float), np.asarray(fly[k], float), kit.smooth(f)))
        return out

    a = L.Anim('ledge_leap', LEAP_LEN, 'once')
    a.tol_rot, a.tol_pos = 0.3, 0.06
    kit.bake(a, kit.frames(0, LEAP_LEN), pose)
    return a


def leap_wall(a):
    """The wall where the hands are at the start and at the catch (the leap's own way is the mechanic's)."""
    return {0.0: (0.0, 0.0, 0.0), a.length: (0.0, 0.0, 0.0)}
