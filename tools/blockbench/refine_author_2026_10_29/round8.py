"""The author's message of 2026-10-31: "make an attack C + RMB with the bow, like C + LMB, but the turn
is not 180 degrees but 360 round himself: he starts drawing the bow and sits on one knee like that, and
when he lets go of RMB he shoots 3 arrows at once, side by side (meanwhile he can turn the camera and
move only very slowly); after the 3 arrows he just stands up calmly and walks on; 20 s cooldown".

bow_slide_shot: no draft. Made from the game's slide (its pose at 0.6 s, as slide_under_attack starts)
and the author's own spin onto one knee with the bow drawn (bow_barrage, 2026-10-27): its legs and arms
frame by frame, the spin the other way, clockwise, as the attack out of a slide with LMB turns.

    0    - SPIN      off the hip, a whole turn clockwise, down onto the left knee, the bow drawn
                     (bow_barrage's kneel at 0.5 s exactly: the game holds that kneel while RMB is
                     held, the head and arms aiming where the player looks, MoveAnimator.playKneel)
    SPIN - LEN       the release (RMB let go): the string hand flies open (REL), a breath, the bow
                     lowered; up calmly over the front foot (RISE_FROM - RISE_TO), standing at LEN

The body ends turned a whole turn (-360): upright, the converter stops it on its last frame.
"""
import numpy as np

import kit
import moves
from kit import L

#: Times (s). The game plays 0 - SPIN as Move.SLIDE_SHOT, then SPIN - LEN as Move.SLIDE_SHOT_END,
#: whose arrows leave at REL (Bow.SLIDE_SHOT_RELEASE, two ticks after SPIN).
SPIN, REL, RISE_FROM, RISE_TO, LEN = 0.5, 0.6, 0.85, 1.35, 1.5
#: Off the hip by this time: the slide's pose blends into the author's spin.
UP = 0.22

BOW = 'right_item_bow'
BONES = ('body', 'chest', 'head', 'right_arm', 'right_forearm', 'left_arm', 'left_forearm',
         'right_leg', 'right_shin', 'left_leg', 'left_shin')


def _barrage(t):
    p = moves._sampled(moves.REF27, 'bow_barrage', t, bones=BONES)
    p.pop('body@p', None)       # the height is the floor's (kit.grounded)
    return p


def _blend(a, b, f):
    out = dict(a)
    for k, v in b.items():
        out[k] = kit.lerp(a.get(k, (0.0, 0.0, 0.0)), v, f)
    return out


def bow_slide_shot():
    slide = {k: v for k, v in moves._sampled(moves.REF27, 'slide', 0.6).items() if k in BONES}
    kneel = _barrage(SPIN)
    kneel['body'] = (0.0, -360.0, 0.0)
    # the string hand: a hair more pull, open (the string gone), as the author's releases in bow_barrage
    pull, loose, opened = _barrage(0.55), _barrage(0.6), _barrage(0.65)
    hand = ('left_arm', 'left_forearm', 'right_forearm')
    after = dict(kneel, left_arm=(55.0, -30.0, 12.0), left_forearm=(12.0, 0.0, 0.0),
                 right_arm=(80.0, -3.0, -38.0), right_forearm=(0.0, 0.0, -16.0), head=(10.0, 0.0, 0.0))
    # up over the front (right) foot: the back knee off the ground, the body over the foot, standing
    lift = dict(after, body=(8.0, -360.0, 0.0), chest=(-3.0, 0.0, 0.0), head=(4.0, 0.0, 0.0),
                right_leg=(52.0, -12.0, 6.0), right_shin=(-46.0, 0.0, 0.0),
                left_leg=(-14.0, 8.0, -10.0), left_shin=(-62.0, 0.0, 0.0),
                right_arm=(36.0, -2.0, -15.0), right_forearm=(0.0, 0.0, -12.0),
                left_arm=(20.0, -8.0, 6.0), left_forearm=(8.0, 0.0, 0.0))
    nearly = dict(lift, body=(3.0, -360.0, 0.0), chest=(-1.0, 0.0, 0.0), head=(1.0, 0.0, 0.0),
                  right_leg=(18.0, -4.0, 2.0), right_shin=(-18.0, 0.0, 0.0),
                  left_leg=(4.0, 1.0, -3.0), left_shin=(-10.0, 0.0, 0.0),
                  right_arm=(12.0, 0.0, -6.0), right_forearm=(0.0, 0.0, -6.0),
                  left_arm=(8.0, -2.0, 3.0), left_forearm=(4.0, 0.0, 0.0))
    rest = {b: (0.0, 0.0, 0.0) for b in BONES}
    rest['body'] = (0.0, -360.0, 0.0)
    rise = [(RISE_FROM, after), (1.05, lift), (1.25, nearly), (RISE_TO, rest), (LEN, rest)]
    prev = {}

    def pose(t):
        if t <= SPIN:
            # the author's spin, clockwise; off the slide's hip over the first UP seconds
            p = _barrage(t)
            p['body'] = (0.0, -p['body'][1], 0.0)
            p = _blend(slide, p, kit.smooth(t / UP))
            p['body'] = (p['body'][0], -_barrage(t)['body'][1], p['body'][2])
        elif t <= 0.55:
            p = _blend(kneel, {k: pull[k] for k in hand}, kit.smooth((t - SPIN) / 0.05))
        elif t <= REL:
            p = _blend(dict(kneel, **{k: pull[k] for k in hand}), {k: loose[k] for k in hand}, kit.smooth((t - 0.55) / 0.05))
        elif t <= 0.65:
            p = _blend(dict(kneel, **{k: loose[k] for k in hand}), {k: opened[k] for k in hand}, kit.smooth((t - REL) / 0.05))
        elif t <= RISE_FROM:
            p = _blend(dict(kneel, **{k: opened[k] for k in hand}), after, kit.smooth((t - 0.65) / (RISE_FROM - 0.65)))
        else:
            p = after
            for (t0, a), (t1, b) in zip(rise, rise[1:]):
                if t0 <= t <= t1:
                    p = _blend(a, b, kit.smooth((t - t0) / (t1 - t0)))
                    break
        p = kit.grounded(dict(p))
        p['body'] = tuple(kit.continuous(prev.get('body'), p['body']))
        prev['body'] = p['body']
        p[BOW] = (0.0, 0.0, 0.0)
        return p

    a = L.Anim('bow_slide_shot', LEN, 'once')
    a.tol_rot, a.tol_pos = 0.4, 0.08
    # the spin's last frame is the held kneel exactly (the game holds it), the release keyed tightly
    a.protect = (SPIN, REL)
    kit.bake(a, sorted(set(kit.frames(0, LEN)) | {SPIN, 0.55, REL, 0.65}), pose)
    return a
