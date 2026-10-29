"""The moves of the author's second message of 2026-10-29 that need animations of their own, and the
paths the mechanic carries the player along (written for the mod, see paths()).

- slide_under_attack, made again: the author now wants it as an attack out of the slide (LMB while
  sliding), not after it: "a turn of 180 degrees clockwise and a push forward as with one press of
  C, the sword carried outward to the right; you stay turned 180 degrees and just stand up as after
  a held slide". The PC session's version (a whole slide, the turn, and back into the stance, 2.5 s)
  started standing and turned back at the end; this one starts in the slide's own pose, as the
  game's slide looks when the button is pressed, and ends standing the other way round.
- fall_tuck and fall_roll (author: "R held while falling: you tuck, roll on landing, are stunned
  for about a second on one knee in a half crouch, then stand; the fall hurts 70% less"). No
  draft: made from the refined forward roll (dodge_front) and the volley's kneel (bow_barrage).
- ledge_climb_down: the automatic hang (Ctrl + W + C at a drop) lowers the player over the edge;
  it is ledge_climb_up played backwards, after a moment standing while the player turns round.

Poses are key poses with eases (kit.design), every frame put on the floor (kit.grounded), as in
combat.py. Turns of the whole player (the 180 of the slide attack) are the mechanic's: it turns
the player, so the camera turns too and the player ends facing the new way, and the model's body
does not turn in these animations.
"""
import json
import math
import os

import numpy as np

import kit
import kinetrix_rig as kr
from kit import L

PX = 0.9375 / 16   # blocks a pixel of the model (the game draws the player 15/16 of the rig's size)
REF27 = os.path.join(kit.BB, 'refined', 'author_2026-10-27.bbmodel')


def _sampled(path, name, t, bones=None):
    """The pose of a refined animation at time t (kit shorthand)."""
    m = kr.Model(path)
    a = next(x for x in m.animations if x['name'] == name)
    out = {}
    for bone, chans in m.channels(a).items():
        if bone.endswith('2') or (bones and bone not in bones):
            continue
        if 'rotation' in chans:
            out[bone] = tuple(float(v) for v in kr.sample(chans['rotation'], t))
        if 'position' in chans:
            out[bone + '@p'] = tuple(float(v) for v in kr.sample(chans['position'], t))
    return out


def _bake(name, length, keys, loop='once', shake=None):
    d = L.Anim(name + '_design', length)
    for t, pose, ease in keys:
        d.key(t, pose, ease, whole=True)
    a = L.Anim(name, length, loop)
    prev = {}
    for t in kit.frames(0, length):
        p = kit.grounded(kit.design(d, t))
        if shake:
            p = shake(t, p)
        p['body'] = tuple(kit.continuous(prev.get('body'), p['body']))
        prev['body'] = p['body']
        a.key(t, p)
    a.tol_rot, a.tol_pos = 0.4, 0.08
    return a


# --------------------------------------------------------------------------------------------
# slide_under_attack: out of the slide, a half turn clockwise with the blade out, up the other way
# --------------------------------------------------------------------------------------------

#: The mechanic turns the player 180 degrees clockwise over 0-SPIN s; the blade, out to the right,
#: sweeps through everything round them by SLASH_HIT (the blow lands then); the push of a tapped
#: slide carries them on backwards (about 0.65 s for 5 blocks), then they stand up by RISE_END.
SPIN, SLASH_HIT, RISE_FROM, RISE_END, SUA_LEN = 0.3, 0.3, 0.7, 1.1, 1.25


def slide_under_attack():
    """From the slide's pose (the game's slide at its middle: sitting back on the hip, legs ahead,
    the sword hand trailing): the sword hand comes across the chest (the wind-up) as the turn starts,
    the body comes up off the hip onto both feet, and as the turn ends the arm sweeps out to the
    right, straight, the blade flat and outward (the author's grip of the PC version's slash). The
    push carries the player on backwards low in that crouch, the arm out (follow-through), then they
    stand up facing the way they now look, the sword lowered."""
    slide = _sampled(REF27, 'slide', 0.6)
    slide['right_item_sword'] = (0, 0, 0)
    slide['torso'] = (0, 0, 0)
    windup = {'body': (24, 0, 0), 'torso': (0, 14, 0), 'chest': (-10, 0, 0), 'head': (-14, -28, 0),
              'right_arm': (62, 30, -40), 'right_forearm': (85, 0, 0), 'left_arm': (45, 0, -35), 'left_forearm': (35, 0, 0),
              'right_leg': (66, 0, 8), 'right_shin': (-62, 0, 0), 'left_leg': (62, 0, -16), 'left_shin': (-100, 0, 0),
              'right_item_sword': (0, 0, 0), 'body@p': (0, -7, 0)}
    sweep = {'body': (12, 0, 0), 'torso': (0, -4, 0), 'chest': (-10, 0, 0), 'head': (-8, -14, 0),
             'right_arm': (55, 10, 20), 'right_forearm': (40, 0, 0), 'left_arm': (45, 0, -45), 'left_forearm': (25, 0, 0),
             'right_leg': (45, 0, 18), 'right_shin': (-75, 0, 0), 'left_leg': (72, 0, -12), 'left_shin': (-112, 0, 0),
             'right_item_sword': (4, 30, -80), 'body@p': (0, -6, 0)}
    slash = {'body': (6, 0, 0), 'torso': (0, -10, 0), 'chest': (-10, 0, 0), 'head': (-6, 4, 0),
             'right_arm': (12, 0, 90), 'right_forearm': (4, 0, 0), 'left_arm': (40, 0, -40), 'left_forearm': (20, 0, 0),
             'right_leg': (32, 0, 12), 'right_shin': (-62, 0, 0), 'left_leg': (62, 0, -10), 'left_shin': (-100, 0, 0),
             'right_item_sword': (8, 57, -157), 'body@p': (0, -6, 0)}
    follow = dict(slash, torso=(0, -14, 0), right_arm=(8, -8, 95), left_arm=(36, 0, -42))
    keys = [
        (0.0, slide, 'auto'),
        (0.1, windup, 'inout'),
        (0.2, sweep, 'in'),
        (SLASH_HIT, slash, 'hit'),
        (0.5, follow, 'out'),
        (RISE_FROM, follow, 'auto'),
        (0.9, {'chest': (-12, 0, 0), 'head': (-4, 0, 0), 'right_arm': (30, 0, 35), 'right_forearm': (20, 0, 0),
               'left_arm': (20, 0, -20), 'left_forearm': (15, 0, 0), 'right_leg': (30, 0, 6), 'right_shin': (-40, 0, 0),
               'left_leg': (35, 0, -6), 'left_shin': (-50, 0, 0), 'right_item_sword': (4, 28, -78)}, 'inout'),
        (RISE_END, {'right_arm': (6, 0, 6), 'right_forearm': (8, 0, 0), 'left_arm': (4, 0, -4), 'left_forearm': (6, 0, 0),
                    'right_item_sword': (0, 0, 0)}, 'inout'),
        (SUA_LEN, {'right_item_sword': (0, 0, 0)}, 'inout'),
    ]
    return _bake('slide_under_attack', SUA_LEN, keys)


# --------------------------------------------------------------------------------------------
# fall_tuck and fall_roll
# --------------------------------------------------------------------------------------------

TUCK = {'body': (-20, 0, 0), 'chest': (-35, 0, 0), 'head': (-28, 0, 0),
        'right_arm': (72, 0, 18), 'right_forearm': (80, 0, 0), 'left_arm': (72, 0, -18), 'left_forearm': (80, 0, 0),
        'right_leg': (108, 0, 5), 'right_shin': (-128, 0, 0), 'left_leg': (108, 0, -5), 'left_shin': (-128, 0, 0),
        'right_item_sword': (-90, 0, 0)}
TUCK_LEN = 1.0


def fall_tuck():
    """Falling with R held: knees to the chest, arms round the shins, chin down; the body turns a
    little in the air (it is not a statue) and the loop closes without a jump."""
    keys = [(0.0, {**TUCK, 'body': (-14, 0, 3)}, 'auto'),
            (0.25, {**TUCK, 'body': (-22, 0, 0)}, 'inout'),
            (0.5, {**TUCK, 'body': (-26, 0, -3)}, 'inout'),
            (0.75, {**TUCK, 'body': (-20, 0, 0)}, 'inout'),
            (TUCK_LEN, {**TUCK, 'body': (-14, 0, 3)}, 'inout')]
    a = _bake('fall_tuck', TUCK_LEN, keys, loop='loop')
    return a


#: fall_roll: the feet touch at 0, the roll over the shoulder 0.08-0.5 s (the mechanic carries the
#: player ROLL_BLOCKS forward), up onto one knee by 0.6, stunned there to KNEEL_END, standing by FALL_END.
ROLL_FROM, ROLL_TO, KNEEL_AT, KNEEL_END, FALL_END = 0.08, 0.5, 0.68, 1.5, 1.9
ROLL_BLOCKS = 1.6


def fall_roll():
    """The landing out of the tuck: the feet take it in a deep squat and the body goes on forward
    into a roll over the shoulder (the refined forward roll: one turn, tucked), comes up onto the
    right foot and the left knee (the volley's kneel), the head down and a hand on the knee, a
    moment to breathe (the stun), then stands up."""
    roll = lambda t: _sampled(REF27, 'dodge_front', t)  # noqa: E731
    r20, r30, r45 = roll(0.2), roll(0.3), roll(0.45)
    for r in (r20, r30, r45):
        r.pop('body@p', None)
    kneel = _sampled(REF27, 'bow_barrage', 0.5, bones=('right_leg', 'right_shin', 'left_leg', 'left_shin'))
    stunned = {**kneel, 'body': (-360, 0, 0), 'chest': (-18, 0, 0), 'head': (24, 0, 0),
               'right_arm': (42, 0, 12), 'right_forearm': (48, 0, 0), 'left_arm': (18, 0, -14), 'left_forearm': (22, 0, 0),
               'right_item_sword': (0, 0, 0)}
    breathe = {**stunned, 'chest': (-14, 0, 0), 'head': (20, 0, 0)}
    keys = [
        (0.0, {**TUCK, 'body': (-20, 0, 0), 'right_leg': (80, 0, 5), 'right_shin': (-118, 0, 0),
               'left_leg': (80, 0, -5), 'left_shin': (-118, 0, 0)}, 'auto'),
        (ROLL_FROM, {**TUCK, 'body': (-55, 0, 0), 'chest': (-45, 0, 0), 'right_arm': (95, 0, 15), 'right_forearm': (55, 0, 0),
                     'left_arm': (95, 0, -15), 'left_forearm': (55, 0, 0), 'right_leg': (95, 0, 4), 'right_shin': (-125, 0, 0),
                     'left_leg': (95, 0, -4), 'left_shin': (-125, 0, 0)}, 'in'),
        (0.2, {**r20, 'body': (-110, 0, 0)}, 'linear'),
        (0.32, {**r30, 'body': (-200, 0, 0)}, 'linear'),
        (0.44, {**r45, 'body': (-290, 0, 0)}, 'linear'),
        # out of the roll on both feet, still tucked (a deep squat), then the left knee goes down
        (ROLL_TO + 0.04, {**TUCK, 'body': (-338, 0, 0), 'chest': (-35, 0, 0), 'head': (-10, 0, 0),
                          'right_leg': (95, 0, 6), 'right_shin': (-120, 0, 0), 'left_leg': (88, 0, -6), 'left_shin': (-125, 0, 0),
                          'right_arm': (60, 0, 15), 'right_forearm': (40, 0, 0), 'left_arm': (50, 0, -15), 'left_forearm': (40, 0, 0),
                          'right_item_sword': (-40, 0, 0)}, 'out'),
        (KNEEL_AT, stunned, 'out'),
        (1.05, breathe, 'inout'),
        (KNEEL_END, stunned, 'inout'),
        (1.7, {'body': (-360, 0, 0), 'chest': (-14, 0, 0), 'head': (4, 0, 0), 'right_arm': (14, 0, 8), 'right_forearm': (16, 0, 0),
               'left_arm': (10, 0, -8), 'left_forearm': (12, 0, 0), 'right_leg': (24, 0, 3), 'right_shin': (-30, 0, 0),
               'left_leg': (16, 0, -3), 'left_shin': (-34, 0, 0), 'right_item_sword': (0, 0, 0)}, 'inout'),
        (FALL_END, {'body': (-360, 0, 0), 'right_item_sword': (0, 0, 0)}, 'inout'),
    ]
    return _bake('fall_roll', FALL_END, keys)


def roll_path(t):
    """Blocks the fall roll has carried the player forward at time t."""
    return ROLL_BLOCKS * kit.span(t, ROLL_FROM, ROLL_TO, kit.smooth)


# --------------------------------------------------------------------------------------------
# ledge_climb_down: the automatic hang, the climb up backwards
# --------------------------------------------------------------------------------------------

TURN = 0.3        # standing while the mechanic turns the player round, back to the drop
DOWN_LEN = 1.55   # the climb (2.0 s) backwards in 1.25 s: going down is quicker than up


def _down_src(t):
    """Time of the climb up shown at time t of the climb down."""
    if t <= TURN:
        return 2.0
    return 2.0 * (1 - (t - TURN) / (DOWN_LEN - TURN))


def ledge_climb_down(climb):
    """`climb`: the baked ledge_climb_up (with its root path). The same poses backwards: the player
    steps to the edge, kneels on the top with the right knee, puts the hands on it, lowers the legs
    over and walks the right foot down the wall, and hangs on both hands. Returns the animation with
    its root path (px, as the climb up's)."""
    a = L.Anim('ledge_climb_down', DOWN_LEN, 'once')
    a.tol_rot, a.tol_pos = 0.3, 0.06
    ts = sorted(climb.root)
    root = {}
    for t in kit.frames(0, DOWN_LEN):
        s = _down_src(t)
        p = kit.design(climb, s)
        a.key(t, p)
        r = np.array([np.interp(s, ts, [climb.root[x][i] for x in ts]) for i in range(3)])
        root[round(t, 4)] = r
    a.root = root
    return a


# --------------------------------------------------------------------------------------------
# The paths, for the mod
# --------------------------------------------------------------------------------------------

def _per_tick(length, f):
    n = int(round(length * 20))
    return [f(i / 20) for i in range(n + 1)]


def _root_path(root):
    """{time: (x, y, z) px} -> per tick [up, forward] in blocks (forward: towards the wall, -z)."""
    ts = sorted(root)
    def at(t):
        y = float(np.interp(t, ts, [root[x][1] for x in ts]))
        z = float(np.interp(t, ts, [root[x][2] for x in ts]))
        return [round(y * PX, 4), round(-z * PX, 4)]
    return at


def paths(climb, down, pulled, helper, hop_path, kick_lift, kick_forward, kick_len):
    """Every path the mechanic carries the player along, a value a game tick (20 a second) from the
    move's start, in blocks: [up, forward] for climbs, along the edge for the ledge hop, back from
    the edge for the friend, [up, forward] for the kick (forward to the mob at the scale of the
    author's: the mob 2.34 blocks ahead), forward for the fall roll."""
    hroot = sorted(helper.root)
    return {
        'ledge_climb_up': _per_tick(climb.length, _root_path(climb.root)),
        'ledge_climb_down': _per_tick(down.length, _root_path(down.root)),
        'ledge_pulled_up': _per_tick(pulled.length, _root_path(pulled.root)),
        # the friend steps back from the edge (their own +z is away from it)
        'ledge_help_pull': _per_tick(helper.length, lambda t: round(
            float(np.interp(t, hroot, [helper.root[x][2] for x in hroot])) * PX, 4)),
        'ledge_hop': _per_tick(1.85, lambda t: round(hop_path(t), 4)),
        'hop_on_kick': _per_tick(kick_len, lambda t: [round(kick_lift(t) * PX, 4), round(kick_forward(t) * PX, 4)]),
        'fall_roll': _per_tick(FALL_END, lambda t: round(roll_path(t), 4)),
    }


def write_paths(data, path):
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        f.write('{\n')
        items = list(data.items())
        for i, (k, v) in enumerate(items):
            f.write(f'  "{k}": {json.dumps(v, separators=(",", ":"))}' + (',\n' if i < len(items) - 1 else '\n'))
        f.write('}\n')
