"""The author's two drafts of 2026-10-29, refined: hop_on_kick (the vault over a mob, a hang in the
air turned to its back, a kick, a turn back and the landing) and slide_under_attack (the slide, a
half turn clockwise with the sword drawn out from the chest, back into the stance).

Both keep the author's key poses and timing; what changed and why is said at each pose. Poses are
designed as key poses with eases, then every frame is put on the floor (or, in the air, on the
bottom of the hitbox the mechanic lifts) and keyed.
"""
import math

import kit
from kit import L


def _bake(name, length, keys, lift=None, loop='once', partner=None):
    """keys: [(t, pose, ease)] whole poses. Every frame is grounded (the lowest point on the floor,
    or on the bottom of the hitbox when the mechanic lifts the player). partner(t) -> grey keys."""
    d = L.Anim(name + '_design', length)
    for t, pose, ease in keys:
        d.key(t, pose, ease, whole=True)
    a = L.Anim(name, length, loop)
    prev = {}
    for t in kit.frames(0, length):
        p = kit.grounded(kit.design(d, t))
        p['body'] = tuple(kit.continuous(prev.get('body'), p['body']))
        prev['body'] = p['body']
        if partner:
            p.update(partner(t))
        a.key(t, p)
    a.lift = lift
    return a


# --------------------------------------------------------------------------------------------
# hop_on_kick
# --------------------------------------------------------------------------------------------

#: The mechanic's lift of the player (px of the model above the ground) while the model rides on the
#: bottom of its hitbox: over the mob's stooped back (a zombie's is 1.4 blocks, 24 px), a hang that
#: sinks slowly to the height of its upper back for the kick, then the fall to the landing.
KICK_TAKEOFF, KICK_HIT, KICK_LAND = 0.25, 1.27, 1.95
LIFT = kit.keys((0.0, 0), (KICK_TAKEOFF, 0), (0.42, 20), (0.55, 26), (0.8, 24), (KICK_HIT, 16),
                (1.55, 13), (KICK_LAND, 0), (2.45, 0))


#: Where the mob is in front of the player (px, -z is ahead): it runs in, the player goes over it,
#: and from the kick it flies away. Until the kick, the player's own way forward is this less where
#: it started (the mechanic's path, moves.paths).
KICK_Z = kit.keys((0.0, -40), (0.25, -24), (0.5, -6), (0.75, 8), (KICK_HIT, 12), (1.6, 30), (2.45, 34))


def _kick_partner(t):
    """The grey mob of the author's draft: it runs in, stoops under the hand, is kicked away. In
    Blockbench it is lowered by the lift, so the two stand as they will in the game."""
    z = KICK_Z(t)
    chest = kit.keys((0.0, 0), (0.25, -20), (0.4, -60), (0.55, -72), (1.1, -40), (KICK_HIT, -30), (1.45, -55),
                     (2.0, -25), (2.45, -15))(t)
    head = kit.keys((0.0, 0), (0.25, -10), (0.4, -30), (0.6, -25), (KICK_HIT, -10), (1.45, -30), (2.45, -10))(t)
    step = math.sin(2 * math.pi * t / 0.5) * (1 - kit.span(t, 0.6, 0.8))
    return {'body2@p': (-21, -LIFT(t), z), 'body2': (0, -180, 0), 'chest2': (chest, 0, 0), 'head2': (head, 0, 0),
            'right_leg2': (28 * step, 0, 0), 'left_leg2': (-28 * step, 0, 0),
            'right_arm2': (-10, 1, 10), 'left_arm2': (-15, 0, -10), 'right_forearm2': (28, 0, 0), 'left_forearm2': (28, 0, 0)}


def hop_on_kick():
    """The author's draft, 2.375 s: up and over the mob's head turning clockwise, a hang facing its
    back, a kick into it, the turn back and the landing.

    What changed: the author's turn over the head went through flipped angles (194, -43, -231 and
    back) and in Blockbench tumbled head over heels the long way; it is now one clean half turn
    clockwise with the body pitched over the hand. The hand is on the head and presses it down as
    the player goes over (as in hop_roll_dodge). The kicking leg is chambered in the hang and the
    kick lands fast and straight, the body leaning back against it; the author's kick was a slow
    sideways swing. The landing had no give: it now lands on bent knees and stands up. The model
    no longer rises inside the hitbox (the mechanic lifts the player, LIFT)."""
    keys = [
        (0.0, {}, 'auto'),
        # the run-in crouch, turning clockwise, the left hand going up for the head (author: 0.25 s)
        (0.12, {'body': (0, -18, 0), 'chest': (-12, 0, 0), 'head': (-5, 10, 0),
                'right_leg': (30, 0, 2), 'right_shin': (-45, 0, 0), 'left_leg': (25, 0, -2), 'left_shin': (-40, 0, 0),
                'left_arm': (95, 0, -10), 'left_forearm': (20, 0, 0), 'right_arm': (-25, 0, 15), 'right_forearm': (20, 0, 0)},
         'inout'),
        (KICK_TAKEOFF, {'body': (0, -45, 0), 'chest': (-22, 0, 0), 'head': (-12, 25, 0),
                        'right_leg': (45, 0, 4), 'right_shin': (-80, 0, 0), 'left_leg': (55, 0, -4), 'left_shin': (-85, 0, 0),
                        'left_arm': (118, 5, -12), 'left_forearm': (25, 0, 0), 'right_arm': (-40, 0, 25), 'right_forearm': (30, 0, 0)},
         'auto'),
        # over the head: pitched over the pressing hand, tucked, halfway round
        (0.45, {'body': (-45, -110, 0), 'chest': (-30, 0, 0), 'head': (-20, 0, 0),
                'right_leg': (85, 0, 6), 'right_shin': (-120, 0, 0), 'left_leg': (80, 0, -6), 'left_shin': (-115, 0, 0),
                'left_arm': (60, 5, -15), 'left_forearm': (5, 0, 0), 'right_arm': (40, 0, 60), 'right_forearm': (20, 0, 0)},
         'auto'),
        # the hang, facing the mob's back: the kicking (right) leg chambered, guard up
        (0.7, {'body': (-10, -180, 0), 'chest': (-15, 0, 0), 'head': (-12, 0, 0),
               'right_leg': (90, 0, 4), 'right_shin': (-125, 0, 0), 'left_leg': (55, 0, -6), 'left_shin': (-95, 0, 0),
               'left_arm': (45, 10, -30), 'left_forearm': (30, 0, 0), 'right_arm': (30, -10, 45), 'right_forearm': (40, 0, 0)},
         'out'),
        (1.12, {'body': (-6, -180, 0), 'chest': (-10, 0, 0), 'head': (-15, 0, 0),
                'right_leg': (100, 0, 4), 'right_shin': (-130, 0, 0), 'left_leg': (52, 0, -6), 'left_shin': (-92, 0, 0),
                'left_arm': (48, 10, -30), 'left_forearm': (34, 0, 0), 'right_arm': (34, -10, 48), 'right_forearm': (44, 0, 0)},
         'inout'),
        # the kick: fast and straight into its upper back, the body leaning back against it
        (KICK_HIT, {'body': (12, -180, 0), 'chest': (5, 0, 0), 'head': (-18, 0, 0),
                    'right_leg': (62, 0, 3), 'right_shin': (-4, 0, 0), 'left_leg': (35, 0, -6), 'left_shin': (-70, 0, 0),
                    'left_arm': (70, 10, -20), 'left_forearm': (15, 0, 0), 'right_arm': (-20, 0, 50), 'right_forearm': (20, 0, 0)},
         'hit'),
        (1.37, {'body': (10, -180, 0), 'chest': (4, 0, 0), 'head': (-16, 0, 0),
                'right_leg': (60, 0, 3), 'right_shin': (-10, 0, 0), 'left_leg': (36, 0, -6), 'left_shin': (-72, 0, 0),
                'left_arm': (66, 10, -22), 'left_forearm': (16, 0, 0), 'right_arm': (-16, 0, 50), 'right_forearm': (22, 0, 0)},
         'auto'),
        (1.52, {'body': (0, -170, 0), 'chest': (-6, 0, 0), 'head': (-10, -6, 0),
                'right_leg': (70, 0, 3), 'right_shin': (-85, 0, 0), 'left_leg': (40, 0, -6), 'left_shin': (-75, 0, 0),
                'left_arm': (40, 0, -30), 'left_forearm': (25, 0, 0), 'right_arm': (30, 0, 40), 'right_forearm': (30, 0, 0)},
         'inout'),
        # the turn back (anticlockwise), coming down with the legs under the body
        (1.85, {'body': (5, 0, 0), 'chest': (-5, 0, 0), 'head': (-4, 0, 0),
                'right_leg': (25, 0, 3), 'right_shin': (-30, 0, 0), 'left_leg': (20, 0, -3), 'left_shin': (-25, 0, 0),
                'left_arm': (30, 0, -45), 'left_forearm': (15, 0, 0), 'right_arm': (30, 0, 45), 'right_forearm': (15, 0, 0)},
         'inout'),
        # the landing on bent knees, then up into the stance
        (KICK_LAND, {'body': (2, 0, 0), 'chest': (-15, 0, 0), 'head': (-2, 0, 0),
                     'right_leg': (35, 0, 3), 'right_shin': (-55, 0, 0), 'left_leg': (30, 0, -3), 'left_shin': (-50, 0, 0),
                     'left_arm': (35, 0, -30), 'left_forearm': (20, 0, 0), 'right_arm': (35, 0, 30), 'right_forearm': (20, 0, 0)},
         'in'),
        (2.1, {'body': (0, 0, 0), 'chest': (-25, 0, 0), 'head': (8, 0, 0),
               'right_leg': (55, 0, 3), 'right_shin': (-95, 0, 0), 'left_leg': (50, 0, -3), 'left_shin': (-90, 0, 0),
               'left_arm': (25, 0, -20), 'left_forearm': (25, 0, 0), 'right_arm': (25, 0, 20), 'right_forearm': (25, 0, 0)},
         'out'),
        (2.45, {}, 'inout'),
    ]
    a = _bake('hop_on_kick', 2.45, keys, lift=LIFT, partner=_kick_partner)
    a.tol_rot, a.tol_pos = 0.4, 0.08
    return a


# --------------------------------------------------------------------------------------------
# slide_under_attack
# --------------------------------------------------------------------------------------------

SLASH_FROM, SLASH_HIT = 1.62, 1.82


def slide_under_attack():
    """The author's draft, 2.5 s: the sitting slide (0.17-1.5 s), then a half turn clockwise low
    on the feet, the sword drawn from the chest outward, and (author: "back to where it was") the
    turn back into the stance.

    What changed: the slide poses and the shake are the author's; the body lay up to 3 px in the
    floor and floats off it at the end: every frame is on the floor now. The turn went through
    tumbling angles (126, -78, -115) in 0.04 s steps: it is one clean half turn clockwise in 0.27 s,
    the left foot under the body as the pivot. The sword arm swung out straight from where it hung
    behind; it now comes across the chest first (the wind-up) and sweeps outward as the turn ends,
    so the blade is drawn from the chest out (author). The draft stopped there, turned round and
    crouched (held to 2.5 s): the player now turns back and stands up by 2.4 s, so the move ends in
    the stance the game expects."""
    shake = lambda t: 0.0 if t < 0.3 or t > 1.45 else math.sin(2 * math.pi * 11 * t)  # noqa: E731
    # The author's slide sits low with the legs ahead: with the feet on the floor instead of in it,
    # the legs lie nearer the floor (the front one almost along it, the other folded under) and
    # the body leans back onto the hip, so it stays as low as the author had it.
    slide = {'body': (12, 0, 0), 'chest': (-6, 0, 0), 'head': (-21, 0, 0),
             'right_arm': (-48, -7, 24), 'right_forearm': (14, 0, 0), 'left_arm': (86, -15, -70), 'left_forearm': (40, 0, 0),
             'right_leg': (80, 0, 4), 'right_shin': (-14, 0, 0), 'left_leg': (74, 0, -22), 'left_shin': (-96, 0, 0)}
    lean = dict(slide, body=(20, 0, 0), chest=(-8, 0, 0), left_arm=(87, -16, -92), left_forearm=(47, 0, 0),
                right_leg=(80, 0, 4), right_shin=(-12, 0, 0), left_leg=(76, 0, -22), left_shin=(-100, 0, 0), head=(-20, 0, 0))
    keys = [
        (0.0, {'right_item_sword': (0, 0, 0)}, 'auto'),
        (0.17, {'head': (-6, 0, 0), 'right_arm': (-10, 12, 25), 'right_forearm': (25, 0, 0), 'left_arm': (40, -5, -10),
                'left_forearm': (42, 0, 0), 'right_leg': (35, -10, -3), 'right_shin': (-25, 0, 0),
                'left_leg': (60, 0, -8), 'left_shin': (-50, 0, 0)}, 'inout'),
        (0.29, slide, 'out'),
        (1.2, lean, 'inout'),
        (1.45, lean, 'auto'),
        # the wind-up: the left foot comes under the body, the sword hand across to the left of the chest
        (1.55, {'body': (8, -12, 0), 'chest': (-8, 0, 0), 'head': (-12, -30, 0),
                'right_arm': (62, 30, -40), 'right_forearm': (85, 0, 0), 'left_arm': (55, 0, -40), 'left_forearm': (30, 0, 0),
                'right_leg': (50, 0, 10), 'right_shin': (-45, 0, 0), 'left_leg': (78, 0, -15), 'left_shin': (-115, 0, 0),
                'right_item_sword': (0, 0, 0)}, 'inout'),
        # the half turn clockwise, the sword sweeping out from the chest
        (1.7, {'body': (6, -105, 0), 'chest': (-10, 0, 0), 'head': (-8, -15, 0),
               'right_arm': (55, 10, 20), 'right_forearm': (40, 0, 0), 'left_arm': (45, 0, -45), 'left_forearm': (25, 0, 0),
               'right_leg': (45, 0, 18), 'right_shin': (-60, 0, 0), 'left_leg': (72, 0, -12), 'left_shin': (-110, 0, 0),
               'right_item_sword': (4, 30, -80)}, 'in'),
        (SLASH_HIT, {'body': (5, -180, 0), 'chest': (-10, 0, 0), 'head': (-6, 0, 0),
                     'right_arm': (12, 0, 90), 'right_forearm': (4, 0, 0), 'left_arm': (40, 0, -40), 'left_forearm': (20, 0, 0),
                     'right_leg': (32, 0, 12), 'right_shin': (-62, 0, 0), 'left_leg': (62, 0, -10), 'left_shin': (-100, 0, 0),
                     'right_item_sword': (8, 57, -157)}, 'hit'),
        (1.95, {'body': (4, -183, 0), 'chest': (-8, 0, 0), 'head': (-6, 0, 0),
                'right_arm': (8, -8, 95), 'right_forearm': (4, 0, 0), 'left_arm': (36, 0, -42), 'left_forearm': (20, 0, 0),
                'right_leg': (32, 0, 12), 'right_shin': (-62, 0, 0), 'left_leg': (62, 0, -10), 'left_shin': (-100, 0, 0),
                'right_item_sword': (8, 57, -157)}, 'out'),
        # back to where it was (author): the turn back anticlockwise, standing up, the sword lowered
        (2.4, {'right_arm': (8, 0, 6), 'right_forearm': (10, 0, 0), 'left_arm': (4, 0, -4), 'left_forearm': (6, 0, 0),
               'right_item_sword': (0, 0, 0)}, 'inout'),
        (2.5, {'right_item_sword': (0, 0, 0)}, 'inout'),
    ]
    d = L.Anim('slide_design', 2.5)
    for t, pose, ease in keys:
        d.key(t, pose, ease, whole=True)
    a = L.Anim('slide_under_attack', 2.5, 'once')
    prev = {}
    for t in kit.frames(0, 2.5):
        p = kit.design(d, t)
        s = shake(t)
        h = p['head']
        p['head'] = (h[0] + 1.2 * s, h[1], h[2] + 1.6 * math.sin(2 * math.pi * 7 * t) * (s != 0))
        p = kit.grounded(p)
        if s:
            p = kit.shifted(p, (0, 0.3 * abs(s), 0))   # the ground shakes the body up, never into the floor
        p['body'] = tuple(kit.continuous(prev.get('body'), p['body']))
        prev['body'] = p['body']
        p.update({'body2@p': (35, 0, 0)})
        a.key(t, p)
    a.tol_rot, a.tol_pos = 0.4, 0.08
    return a
