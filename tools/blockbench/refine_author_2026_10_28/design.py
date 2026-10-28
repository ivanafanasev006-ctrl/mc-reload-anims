"""All new and reworked animations. Blockbench 5 internal degrees and pixels."""
import numpy as np

import animlib as L
import ik
import poses as PS
from animlib import Anim, add

G = PS.get
REST = {}


def place(pose, yaw=0.0, z=0.0, pitch=None):
    """Pose turned by extra yaw (degrees) and moved back by z, for the combo."""
    p = dict(pose)
    b = p.get('body', (0, 0, 0))
    p['body'] = (b[0] if pitch is None else pitch, b[1] + yaw, b[2])
    bp = p.get('body@p', (0, 0, 0))
    p['body@p'] = (bp[0], bp[1], bp[2] + z)
    return p


def body_of(pose, **kw):
    p = dict(pose)
    for k, v in kw.items():
        p[k.replace('__p', '@p')] = v
    return p


def tense(pose):
    """Full draw held: the hand pulls a little more, the head settles on the string."""
    p = dict(pose)
    lf = p.get('left_forearm', (0, 0, 0))
    p['left_forearm'] = (lf[0], lf[1], lf[2] + 4)
    h = p.get('head', (0, 0, 0))
    p['head'] = (h[0] - 1.0, h[1], h[2])
    return p


def released(fam_pose, arm_pose, amount=1.0, lean=2.5):
    """Whole pose after the release: body of fam_pose, draw arm from arm_pose, bow arm recoiled."""
    p = dict(fam_pose)
    p.update(PS.pick(arm_pose, ('left_arm', 'left_forearm', 'left_arm@p')))
    p.update(PS.bow_recoil(fam_pose, amount))
    c = p.get('chest', (0, 0, 0))
    p['chest'] = (c[0] + lean * amount, c[1], c[2])
    return p


def with_arms(base, arms_from):
    p = dict(base)
    p.update(PS.pick(arms_from, PS.ARMS))
    return p


def breath(a, t0, t1, pose):
    """Pause: still pose that breathes once (chest and head barely move)."""
    a.key(t0, pose, whole=True)
    mid = add(PS.pick(pose, ('chest', 'head')), {'chest': (1.2, 0, 0), 'head': (0.6, 0, 0)})
    a.key((t0 + t1) / 2, mid)
    a.key(t1, pose, whole=True)


def shot(a, t, drawn, fam, hold=0.12, flick=0.08, follow=0.14, amount=1.0):
    """The release: hold at full draw from t, hand thrown back past the jaw, follow-through.
    Returns the time the follow-through ends."""
    a.key(t, drawn, whole=True)
    r = t + hold
    a.key(r, tense(drawn), whole=True)
    a.key(r + flick, released(drawn, G(fam + '_flick'), amount), 'out3', whole=True)
    a.key(r + flick + follow, released(drawn, G(fam + '_follow'), amount * 0.5, lean=1.5), 'out', whole=True)
    return r + flick + follow


# ------------------------------------------------------------------------------------------------
# bow_barrage: four parts, each longer, with a shot and a pause after it
# ------------------------------------------------------------------------------------------------

def tuck(base_yaw, z, y=-6.3):
    """Mid-spin of the drop into the kneel: both legs folded under (the author's 0.25 s pose)."""
    return {'body': (0, base_yaw, 0), 'body@p': (0, y, z), 'chest': (-10, 0, 0), 'head': (6, 0, 0),
            'right_leg': (84, -12, 8.7), 'right_shin': (-30, 0, 0), 'left_leg': (80, 7.5, -5), 'left_shin': (-75, 0, 0),
            'right_arm': (45, -2, -20), 'right_forearm': (0, 0, -15), 'left_arm': (40, -14, 7), 'left_forearm': (0, 0, 25)}


def rise_mid(yaw, z, y=-3.0):
    """Halfway up from the kneel during the counter-turn (the author's 1.25 s pose)."""
    return {'body': (0, yaw, 0), 'body@p': (0, y, z), 'chest': (-6, 0, 0), 'head': (13.5, -21.9, -5.1),
            'right_leg': (23.7, -22.2, 9.8), 'right_shin': (-40, 0, 0), 'left_leg': (23.2, 20.3, -30.1), 'left_shin': (-32.5, 0, 0),
            'right_arm': (87.9, -36, -41.5), 'right_forearm': (0, 0, -7.5), 'left_arm': (78.1, -5, 8.6), 'left_forearm': (0, 0, 42.5)}


def pivot_mid(yaw, z, y, aim_from, lean=0.0, lift='right'):
    """Mid-spin: turning on one foot, the other leg swinging round, bow kept up."""
    p = with_arms({}, aim_from)
    p.update({'body': (lean, yaw, 0), 'body@p': (0, y, z), 'chest': (-8, 0, 0), 'head': aim_from.get('head', (0, 0, 0))})
    if lift == 'right':
        p.update({'right_leg': (38, -10, 16), 'right_shin': (-55, 0, 0), 'left_leg': (14, 4, -6), 'left_shin': (-26, 0, 0)})
    else:
        p.update({'left_leg': (38, 10, -16), 'left_shin': (-55, 0, 0), 'right_leg': (14, -4, 6), 'right_shin': (-26, 0, 0)})
    return p


def part_kneel(a, t0, z, yaw0=0.0, combo=True):
    """Spin a full turn dropping into the kneel (1 s), two shots, pause. Returns end time."""
    K = place(G('kneel'), yaw0 + 360, z)
    a.key(t0 + 0.50, tuck(yaw0 + 205, z * 0.6), 'auto', whole=True)
    a.key(t0 + 0.78, with_arms(place(G('kneel'), yaw0 + 338, z * 0.92), G('kneel_nock')), 'auto', whole=True)
    a.key(t0 + 1.00, K, 'out', whole=True)
    t = shot(a, t0 + 1.00, K, 'kneel')
    nock = with_arms(K, G('kneel_nock'))
    a.key(t + 0.20, nock, 'auto', whole=True)
    t = shot(a, t + 0.44, K, 'kneel')
    a.key(t + 0.26, nock, 'auto', whole=True)
    breath(a, t + 0.26, t + 0.70, nock)
    return t + 0.70


def part_stand(a, t0, z, yaw_from, yaw_to, from_pose=None):
    """Counter-turn up to the standing side aim (about 1 s), one shot, pause."""
    S = place(G('stand'), yaw_to - 87.5, z)
    if from_pose is not None:  # rising from the kneel: the author's middle pose
        a.key(t0 + 0.30, rise_mid(yaw_from - 0.07 * (yaw_from - yaw_to), z, -4.8), 'auto', whole=True)
        a.key(t0 + 0.62, rise_mid(yaw_from - 0.65 * (yaw_from - yaw_to), z, -2.2), 'auto', whole=True)
    else:
        a.key(t0 + 0.50, pivot_mid(yaw_from + 0.55 * (yaw_to - yaw_from), z, -1.0, with_arms(G('stand'), G('stand_nock')), lift='left'),
              'auto', whole=True)
    a.key(t0 + 0.88, with_arms(place(G('stand'), yaw_to - 87.5 + 0.03 * (yaw_from - yaw_to), z), G('stand_nock')), 'auto', whole=True)
    a.key(t0 + 1.05, S, 'out', whole=True)
    t = shot(a, t0 + 1.05, S, 'stand', hold=0.25)
    nock = with_arms(S, G('stand_nock'))
    a.key(t + 0.27, nock, 'auto', whole=True)
    breath(a, t + 0.27, t + 0.72, nock)
    return t + 0.72


def part_crouch(a, t0, z, yaw_from, yaw_to):
    """Spin into the crouch (1.1 s), one shot with a soft recoil, pause."""
    C = place(G('crouch'), yaw_to - 77.5, z)
    a.key(t0 + 0.55, pivot_mid(yaw_from + 0.52 * (yaw_to - yaw_from), z, -1.8, with_arms(G('crouch'), G('crouch_nock'))),
          'auto', whole=True)
    a.key(t0 + 0.90, with_arms(place(G('crouch'), yaw_to - 77.5 - 12, z), G('crouch_nock')), 'auto', whole=True)
    a.key(t0 + 1.10, C, 'out', whole=True)
    t = shot(a, t0 + 1.10, C, 'crouch', hold=0.20, amount=1.3)
    nock = with_arms(C, G('crouch_nock'))
    a.key(t + 0.27, nock, 'auto', whole=True)
    breath(a, t + 0.27, t + 0.72, nock)
    return t + 0.72


def part_low(a, t0, z, yaw_from, yaw_to):
    """Spin leaning back into the low squat (1.1 s), one shot, pause."""
    Lo = place(G('low'), yaw_to, z)
    a.key(t0 + 0.55, pivot_mid(yaw_from + 0.55 * (yaw_to - yaw_from), z, -3.5, with_arms(G('low'), G('low_nock')), lean=6),
          'auto', whole=True)
    a.key(t0 + 0.90, with_arms(place(G('low'), yaw_to - 14, z), G('low_nock')), 'auto', whole=True)
    a.key(t0 + 1.10, Lo, 'out', whole=True)
    t = shot(a, t0 + 1.10, Lo, 'low', hold=0.20)
    nock = with_arms(Lo, G('low_nock'))
    a.key(t + 0.27, nock, 'auto', whole=True)
    breath(a, t + 0.27, t + 0.62, nock)
    return t + 0.62


def stand_up(a, t0, dur, yaw, z):
    a.key(t0 + dur * 0.5, {'body': (0, yaw, 0), 'body@p': (0, -2.5, z), 'right_leg': (20, -8, 4), 'right_shin': (-30, 0, 0),
                           'left_leg': (16, 6, -4), 'left_shin': (-26, 0, 0), 'chest': (-5, 0, 0),
                           'right_arm': (25, 0, 6), 'right_forearm': (0, 0, -6), 'left_arm': (15, 0, -6), 'left_forearm': (0, 0, 6)},
          'auto', whole=True)
    a.key(t0 + dur, {'body': (0, yaw, 0), 'body@p': (0, 0, z)}, 'auto', whole=True)
    return t0 + dur


def bow_barrage_combo():
    a = Anim('bow_barrage_combo', 10.0, 'hold')
    a.dummy((-20, 0, -27), (0, -180, 0))
    a.key(0, {}, whole=True)
    # In place: the author's model slid 14 px back and stood beside its hitbox; moving the player
    # is the mechanic's job (as in the refined bow_barrage).
    t = part_kneel(a, 0.0, 0)                                   # 0 .. 2.8
    t = part_stand(a, t, 0, 360, 87.5, from_pose='kneel')       # .. 5.1
    t = part_crouch(a, t, 0, 87.5, 437.5)                       # .. 7.3
    t = part_low(a, t, 0, 437.5, 720)                           # .. 9.4
    end = stand_up(a, t, 10.0 - t, 720, 0)
    a.length = L.q(end)
    return a


def bow_barrage_parts():
    out = []
    # 1: spin into the kneel, two shots
    a = Anim('bow_barrage_1_kneel', 0, 'once')
    a.dummy((-20, 0, -27), (0, -180, 0))
    a.key(0, {}, whole=True)
    t = part_kneel(a, 0.0, 0)
    a.length = L.q(stand_up(a, t, 0.6, 360, 0))
    out.append(a)
    # 2: three-quarter turn into the standing side aim
    a = Anim('bow_barrage_2_stand', 0, 'once')
    a.dummy((-20, 0, -27), (0, -180, 0))
    a.key(0, {}, whole=True)
    a.key(0.12, {'body@p': (0, -1.2, 0), 'right_leg': (8, 0, 0), 'right_shin': (-18, 0, 0), 'left_leg': (8, 0, 0),
                 'left_shin': (-18, 0, 0), 'chest': (-4, 0, 0), 'right_arm': (20, 0, 4), 'left_arm': (16, 0, -4),
                 'right_forearm': (15, 0, 0), 'left_forearm': (20, 0, 0)}, 'auto', whole=True)
    t = part_stand(a, 0.0, 0, 0, -272.5)
    a.length = L.q(stand_up(a, t, 0.65, -360, 0))
    out.append(a)
    # 3: spin into the crouch
    a = Anim('bow_barrage_3_crouch', 0, 'once')
    a.dummy((-20, 0, -27), (0, -180, 0))
    a.key(0, {}, whole=True)
    t = part_crouch(a, 0.0, 0, 0, 437.5)
    a.length = L.q(stand_up(a, t, 0.65, 360, 0))
    out.append(a)
    # 4: spin leaning back into the low squat
    a = Anim('bow_barrage_4_low', 0, 'once')
    a.dummy((-20, 0, -27), (0, -180, 0))
    a.key(0, {}, whole=True)
    t = part_low(a, 0.0, 0, 0, 360)
    a.length = L.q(stand_up(a, t, 0.65, 360, 0))
    out.append(a)
    return out


# ------------------------------------------------------------------------------------------------
# Combat flow: one shot each, the arrow leaves at 0.75 s, the hand is thrown back by 1.0 s
# ------------------------------------------------------------------------------------------------

RELEASE = 0.75


def flow_shot_tail(a, drawn, fam, t_drawn, length, end_pose=None):
    """Draw reached at t_drawn; release at RELEASE; follow-through until 1.0; back to rest."""
    a.key(t_drawn, drawn, 'auto', whole=True)
    a.key(RELEASE, tense(drawn), 'auto', whole=True)
    a.key(RELEASE + 0.08, released(drawn, G(fam + '_flick')), 'out3', whole=True)
    a.key(1.00, released(drawn, G(fam + '_follow'), 0.5, lean=1.5), 'out', whole=True)
    if end_pose is None:
        end_pose = {}
    a.key(length, end_pose, 'auto', whole=True)


def flow_stand():
    a = Anim('bow_flow_stand_shot', 1.5, 'once')
    a.dummy((-20, 0, -40), (0, -180, 0))
    S = G('stand')
    a.key(0, {}, whole=True)
    a.key(0.14, {'body': (0, 40, 0), 'body@p': (0, -0.6, 0), 'right_leg': (14, -8, 6), 'right_shin': (-22, 0, 0),
                 'left_leg': (-2, 4, -3), 'left_shin': (-8, 0, 0), 'chest': (-3, 0, 0), 'head': (0, -38, 0),
                 'right_arm': (55, -35, -10), 'right_forearm': (0, 0, -10), 'left_arm': (45, -10, 6), 'left_forearm': (0, 0, 30)},
          'auto', whole=True)
    a.key(0.32, with_arms(S, G('stand_nock')), 'out', whole=True)
    a.key(1.18, with_arms(S, G('stand_nock')), 'auto', whole=True)
    flow_shot_tail(a, S, 'stand', 0.60, 1.5)
    return a


def flow_crouch():
    a = Anim('bow_flow_crouch_shot', 1.5, 'once')
    a.dummy((-20, 0, -40), (0, -180, 0))
    C = G('crouch')
    a.key(0, {}, whole=True)
    a.key(0.15, {'body': (0, 35, 0), 'body@p': (0, -2.6, 0), 'right_leg': (36, -30, 8), 'right_shin': (-40, 0, 0),
                 'left_leg': (24, 10, -10), 'left_shin': (-42, 0, 0), 'chest': (-8, 0, 0), 'head': (8, -33, 0),
                 'right_arm': (55, -30, -10), 'right_forearm': (0, 0, -10), 'left_arm': (45, -10, 6), 'left_forearm': (0, 0, 30)},
          'auto', whole=True)
    a.key(0.34, with_arms(C, G('crouch_nock')), 'out', whole=True)
    a.key(1.15, with_arms(C, G('crouch_nock')), 'auto', whole=True)
    flow_shot_tail(a, C, 'crouch', 0.60, 1.5)
    return a


def flow_backstep():
    a = Anim('bow_flow_backstep_shot', 1.5, 'once')
    a.dummy((-20, 0, -40), (0, -180, 0))
    S = G('stand_low')
    a.key(0, {}, whole=True)
    # push off: knees give, weight forward
    a.key(0.10, {'body@p': (0, -1.6, 0), 'body': (0, 12, 0), 'right_leg': (14, 0, 2), 'right_shin': (-28, 0, 0),
                 'left_leg': (12, 0, -2), 'left_shin': (-28, 0, 0), 'chest': (-8, 0, 0), 'head': (6, -10, 0),
                 'right_arm': (35, -10, -6), 'right_forearm': (0, 0, -8), 'left_arm': (30, -6, 4), 'left_forearm': (0, 0, 22)},
          'auto', whole=True)
    # in the air going back: left leg reaches back, body leans back and turns side-on
    a.key(0.28, {'body@p': (0, 1.0, 0), 'body': (6, 58, 0), 'right_leg': (26, -12, 8), 'right_shin': (-38, 0, 0),
                 'left_leg': (-28, 6, -8), 'left_shin': (-22, 0, 0), 'chest': (2, 0, 0), 'head': (-4, -55, -4),
                 'right_arm': (70, -60, -20), 'right_forearm': (0, 0, -12), 'left_arm': (60, -20, 10), 'left_forearm': (0, 0, 35)},
          'out', whole=True)
    # landing, knees take it
    a.key(0.44, place(with_arms(S, G('stand_low_nock')), 0, 0, pitch=2), 'in', whole=True)
    a.key(0.54, with_arms(body_of(S, body__p=(0, S['body@p'][1] - 0.5, 0)), G('stand_low_nock')), 'out', whole=True)
    a.key(1.18, with_arms(S, G('stand_low_nock')), 'auto', whole=True)
    flow_shot_tail(a, S, 'stand_low', 0.68, 1.5)
    return a


def tuck_roll(yaw, roll, y):
    """Curled up in a forward roll (as in dodge_front), bow held to the chest."""
    return {'body': (roll, yaw, 0), 'body@p': (0, y, 0), 'chest': (-55, 0, 0), 'head': (-45, 0, 0),
            'right_leg': (112, 0, 2), 'right_shin': (-130, 0, 0), 'left_leg': (112, 0, -2), 'left_shin': (-130, 0, 0),
            'right_arm': (95, 10, 18), 'right_forearm': (80, 0, 0), 'left_arm': (100, -12, -18), 'left_forearm': (80, 0, 0)}


def flow_roll(side):
    left = side == 'left'
    name = 'bow_flow_roll_%s_shot' % side
    a = Anim(name, 1.75, 'once')
    a.dummy((-20, 0, -40), (0, -180, 0))
    yaw = 90 if left else -90
    fam = 'side_kneel' if left else 'kneel'
    K = G(fam)
    a.key(0, {}, whole=True)
    a.key(0.08, {'body': (-8, yaw * 0.25, 0), 'body@p': (0, -2.2, 0), 'chest': (-18, 0, 0), 'head': (-8, 0, 0),
                 'right_leg': (26, 0, 0), 'right_shin': (-45, 0, 0), 'left_leg': (26, 0, 0), 'left_shin': (-45, 0, 0),
                 'right_arm': (50, 6, 12), 'right_forearm': (60, 0, 0), 'left_arm': (55, -8, -12), 'left_forearm': (60, 0, 0)},
          'auto', whole=True)
    # rolling over the shoulders: each moment kept touching the ground
    a.key(0.16, ik.on_ground(tuck_roll(yaw, -60, 0), 0.6), 'auto', whole=True)
    a.key(0.25, ik.on_ground(tuck_roll(yaw, -150, 0)), 'linear', whole=True)
    a.key(0.34, ik.on_ground(tuck_roll(yaw, -240, 0)), 'linear', whole=True)
    a.key(0.43, ik.on_ground(tuck_roll(yaw, -315, 0)), 'linear', whole=True)
    # coming up onto one knee, already turning to the target
    up = place(with_arms(K, G(fam + '_nock')), 0, 0, pitch=-360)
    if left:
        mid_yaw = 89
    else:
        mid_yaw = -60
    up_mid = dict(up)
    up_mid['body'] = (-352, mid_yaw, 0)
    up_mid['chest'] = (-20, 0, 0)
    up_mid['body@p'] = (0, K['body@p'][1] - 0.6, 0)
    a.key(0.53, up_mid, 'out', whole=True)
    a.key(0.64, up, 'auto', whole=True)
    drawn = place(K, 0, 0, pitch=-360)
    flow_shot_tail(a, drawn, fam, 0.72, 1.75, end_pose={'body': (-360, 0, 0)})
    # stand up after the shot
    a.key(1.30, {'body': (-360, (K['body'][1]) * 0.35, 0), 'body@p': (0, -2.8, 0), 'chest': (-6, 0, 0),
                 'right_leg': (22, -6, 4), 'right_shin': (-34, 0, 0), 'left_leg': (12, 4, -4), 'left_shin': (-34, 0, 0),
                 'right_arm': (30, 0, 6), 'right_forearm': (0, 0, -6), 'left_arm': (20, 0, -6), 'left_forearm': (0, 0, 6)},
          'auto', whole=True)
    return a


def flow_turn(side):
    left = side == 'left'
    s = 1 if left else -1
    a = Anim('bow_flow_turn_%s' % side, 0.875, 'once')
    a.dummy((-20, 0, -40), (0, -180, 0))
    ready = {'right_arm': (52, 12 * s, 4), 'right_forearm': (38, 0, 0), 'left_arm': (48, -24, -2), 'left_forearm': (62, 0, 0)}
    a.key(0, {}, whole=True)
    a.key(0.12, dict(ready, **{'body': (0, -16 * s, 0), 'body@p': (0, -1.4, 0), 'chest': (-6, 0, 0), 'head': (0, 10 * s, 0),
                              'right_leg': (12, 0, 0), 'right_shin': (-24, 0, 0), 'left_leg': (12, 0, 0), 'left_shin': (-24, 0, 0)}),
          'auto', whole=True)
    lift = 'right_leg' if left else 'left_leg'
    plant = 'left_leg' if left else 'right_leg'
    shin_l = lift.replace('leg', 'shin')
    shin_p = plant.replace('leg', 'shin')
    a.key(0.30, dict(ready, **{'body': (0, 120 * s, 0), 'body@p': (0, -0.4, 0), 'chest': (-8, 0, 0), 'head': (0, -24 * s, 0),
                              lift: (34, 0, 14 * s), shin_l: (-58, 0, 0), plant: (6, 0, 0), shin_p: (-12, 0, 0)}), 'auto', whole=True)
    a.key(0.48, dict(ready, **{'body': (0, 285 * s, 0), 'body@p': (0, -0.6, 0), 'chest': (-8, 0, 0), 'head': (0, -30 * s, 0),
                              lift: (24, 0, 10 * s), shin_l: (-40, 0, 0), plant: (8, 0, 0), shin_p: (-16, 0, 0)}), 'linear', whole=True)
    a.key(0.62, dict(ready, **{'body': (0, 366 * s, 0), 'body@p': (0, -1.6, 0), 'chest': (-7, 0, 0), 'head': (2, -6 * s, 0),
                              'right_leg': (14, 0, 0), 'right_shin': (-26, 0, 0), 'left_leg': (12, 0, 0), 'left_shin': (-26, 0, 0)}),
          'out', whole=True)
    a.key(0.875, {'body': (0, 360 * s, 0)}, 'auto', whole=True)
    return a


# ------------------------------------------------------------------------------------------------
# Bow strikes: blunt hits with the bow held by the grip. They stop on the target and bounce off
# instead of cutting through (no long follow-through arc).
# ------------------------------------------------------------------------------------------------

def mix_pose(a, b, f):
    return L.mix(L.full(a), L.full(b), f)


def strike(name, length, windup, peak, impact, rebound, t_wind=0.25, t_peak=0.292, t_hit=0.417, t_reb=0.5, t_hold=0.583):
    a = Anim(name, length, 'once')
    a.dummy((-20, 0, -15), (0, -180, 0))
    a.key(0, {}, whole=True)
    a.key(t_wind, windup, 'auto', whole=True)
    a.key(t_peak, peak, 'auto', whole=True)
    a.key(t_hit, impact, 'hit', whole=True)
    a.key(t_hit - 0.042, mix_pose(peak, impact, 0.72), 'in2', whole=True)
    a.key(t_reb, rebound, 'out2', whole=True)
    a.key(t_hold, rebound, 'auto', whole=True)
    a.key(length, {}, 'auto', whole=True)
    return a


def strike_overhead():
    wind = {'body@p': (0, -0.6, 0.6), 'torso': (0, -12, 0), 'chest': (10, 0, 0), 'head': (6, 12, 0),
            'right_arm': (170, 0, 14), 'right_forearm': (85, 0, 0), 'left_arm': (52, 0, -18), 'left_forearm': (55, 0, 0),
            'right_leg': (6, 0, 4), 'right_shin': (-8, 0, 0), 'left_leg': (-10, 0, -4), 'left_shin': (-10, 0, 0)}
    peak = add(wind, {'right_arm': (4, 0, 0), 'right_forearm': (5, 0, 0), 'chest': (2, 0, 0)})
    hit = {'body@p': (0, -1.8, -1.6), 'torso': (0, 14, 0), 'chest': (-18, 0, 0), 'head': (-4, -14, 0),
           'right_arm': (96, 0, 6), 'right_forearm': (8, 0, 0), 'left_arm': (-22, 0, -16), 'left_forearm': (35, 0, 0),
           'right_leg': (30, 0, 2), 'right_shin': (-32, 0, 0), 'left_leg': (-18, 0, -3), 'left_shin': (-14, 0, 0)}
    hit = PS.ground(hit, ['lfoot', 'rfoot'], PS.LEG_VARY)
    reb = add(hit, {'right_arm': (9, 0, 0), 'right_forearm': (8, 0, 0), 'chest': (3, 0, 0)})
    return strike('bow_strike_overhead', 0.917, wind, peak, hit, reb)


def strike_side():
    wind = {'body': (0, -6, 0), 'body@p': (0.8, -0.8, 0), 'torso': (0, -38, 0), 'chest': (-4, 0, -6), 'head': (0, 40, 0),
            'right_arm': (82, -96, 0), 'right_forearm': (0, 0, -22), 'left_arm': (42, 32, -10), 'left_forearm': (60, 0, 0),
            'right_leg': (0, 0, 6), 'right_shin': (-12, 0, 0), 'left_leg': (6, 0, -8), 'left_shin': (-8, 0, 0)}
    peak = add(wind, {'right_arm': (0, -6, 0), 'torso': (0, -3, 0), 'head': (0, 3, 0)})
    hit = {'body': (0, 8, 0), 'body@p': (-0.6, -1.3, -1.0), 'torso': (0, 30, 0), 'chest': (-8, 0, 6), 'head': (0, -36, 0),
           'right_arm': (88, 32, 0), 'right_forearm': (0, 0, -8), 'left_arm': (-16, 0, -26), 'left_forearm': (40, 0, 0),
           'right_leg': (14, 0, 2), 'right_shin': (-20, 0, 0), 'left_leg': (-6, 0, -6), 'left_shin': (-12, 0, 0)}
    hit = PS.ground(hit, ['lfoot', 'rfoot'], PS.LEG_VARY)
    reb = add(hit, {'right_arm': (0, -9, 0), 'torso': (0, -4, 0), 'head': (0, 4, 0)})
    return strike('bow_strike_side', 0.917, wind, peak, hit, reb)


def strike_thrust():
    wind = {'body@p': (0, -1.0, 1.6), 'torso': (0, -22, 0), 'chest': (6, 0, 0), 'head': (0, 20, 0),
            'right_arm': (38, 0, 14), 'right_forearm': (92, 0, 0), 'left_arm': (60, 16, -10), 'left_forearm': (50, 0, 0),
            'right_leg': (10, 0, 2), 'right_shin': (-16, 0, 0), 'left_leg': (-12, 0, -3), 'left_shin': (-20, 0, 0)}
    peak = add(wind, {'right_forearm': (6, 0, 0), 'torso': (0, -3, 0), 'head': (0, 3, 0)})
    hit = {'body@p': (0, -3.2, -4.0), 'torso': (0, 22, 0), 'chest': (-14, 0, 0), 'head': (2, -22, 0),
           'right_arm': (92, 8, 0), 'right_forearm': (4, 0, 0), 'left_arm': (-36, 0, -12), 'left_forearm': (30, 0, 0),
           'right_leg': (52, 0, 3), 'right_shin': (-58, 0, 0), 'left_leg': (-32, 0, -3), 'left_shin': (-12, 0, 0)}
    hit = PS.ground(hit, ['lfoot', 'rfoot'], PS.LEG_VARY)
    reb = add(hit, {'right_arm': (-5, 0, 0), 'right_forearm': (10, 0, 0), 'body@p': (0, 0.2, 0.6)})
    return strike('bow_strike_thrust', 0.958, wind, peak, hit, reb)


def all_anims():
    out = [bow_barrage_combo()] + bow_barrage_parts()
    out += [flow_stand(), flow_crouch(), flow_backstep(), flow_roll('left'), flow_roll('right'), flow_turn('left'), flow_turn('right')]
    out += [strike_overhead(), strike_side(), strike_thrust()]
    return out
