"""Two moves of the author's third message of 2026-10-29, made from the description (no draft):

- dive_roll: "a deep jump, as if diving, and at the end a forward roll";
- land_heavy: "a heavy landing on the ground, as if it took a lot of damage, and hard to get up".

As in the other moves, the model does not fly: the mechanic lifts and carries the player (DIVE_LIFT,
DIVE_FORWARD; written into the mod's paths), and every frame the lowest point of the body is put on
the bottom of the hitbox.
"""
import math

import kit
import ledge
from kit import L

# --------------------------------------------------------------------------------------------
# dive_roll
# --------------------------------------------------------------------------------------------

#: The dive: the crouch to CROUCH, the push off at TAKEOFF, the flight with the body along the way
#: it flies, the hands on the ground at TOUCH, a forward roll over the shoulders to ROLL_END, up from
#: the squat by DIVE_LEN. Author, 2026-10-29, fifth message: "sharper, it must at least fly over a
#: one-block obstacle; now it is sluggish": the crouch and the getting up are quicker (1.35 s in all
#: before), the flight as long but higher.
CROUCH, TAKEOFF, APEX, TOUCH, ROLL_END, DIVE_LEN = 0.07, 0.13, 0.33, 0.55, 0.88, 1.12
#: How high the mechanic lifts the player (px of the model) and how far it carries them (blocks):
#: the feet 1.3 blocks up at the top, over a block in the way, then the roll goes on [default].
DIVE_LIFT = kit.keys((0.0, 0), (TAKEOFF, 0), (APEX, 22), (TOUCH, 0), (DIVE_LEN, 0))
DIVE_FORWARD = kit.keys((0.0, 0), (0.08, 0), (TOUCH, 3.4), (ROLL_END, 5.0), (DIVE_LEN, 5.0))


def dive_roll():
    """From the stance: a crouch with the arms swung back, the push off with the arms swinging
    forward over the head, the flight stretched out like a dive into water (arms ahead, legs
    together behind, the body tipping nose down as it comes down), the hands meet the ground and
    give, the chin goes to the chest, a forward roll tucked over the shoulders, and up out of the
    squat. Ends turned one whole turn (it stops on its last frame, like the rolls)."""
    blade_in = (-90, 0, 0)  # the blade turned along the forearm from the push off, so the dive and the roll do not cut the player
    keys = [
        (0.0, {'right_item_sword': (0, 0, 0)}, 'auto'),
        (CROUCH, {'chest': (-28, 0, 0), 'head': (14, 0, 0),
                'right_arm': (-50, 0, 10), 'left_arm': (-50, 0, -10), 'right_forearm': (20, 0, 0), 'left_forearm': (20, 0, 0),
                'right_leg': (45, 0, 3), 'right_shin': (-80, 0, 0), 'left_leg': (45, 0, -3), 'left_shin': (-80, 0, 0),
                'right_item_sword': (0, 0, 0)}, 'inout'),
        (TAKEOFF, {'body': (-35, 0, 0), 'chest': (-12, 0, 0), 'head': (22, 0, 0),
                   'right_arm': (150, 0, 8), 'left_arm': (150, 0, -8), 'right_forearm': (8, 0, 0), 'left_forearm': (8, 0, 0),
                   'right_leg': (-10, 0, 2), 'right_shin': (-15, 0, 0), 'left_leg': (-6, 0, -2), 'left_shin': (-25, 0, 0),
                   'right_item_sword': blade_in}, 'hit'),
        (0.24, {'body': (-66, 0, 0), 'chest': (-4, 0, 0), 'head': (26, 0, 0),
                'right_arm': (172, 0, 6), 'left_arm': (172, 0, -6), 'right_forearm': (4, 0, 0), 'left_forearm': (4, 0, 0),
                'right_leg': (-8, 0, 1), 'right_shin': (-6, 0, 0), 'left_leg': (-8, 0, -1), 'left_shin': (-10, 0, 0),
                'right_item_sword': blade_in}, 'out'),
        (0.45, {'body': (-90, 0, 0), 'chest': (-6, 0, 0), 'head': (18, 0, 0),
                'right_arm': (178, 0, 8), 'left_arm': (178, 0, -8), 'right_forearm': (6, 0, 0), 'left_forearm': (6, 0, 0),
                'right_leg': (-10, 0, 1), 'right_shin': (-14, 0, 0), 'left_leg': (-6, 0, -1), 'left_shin': (-10, 0, 0),
                'right_item_sword': blade_in}, 'inout'),
        # the hands take the ground and give, the chin to the chest, the legs begin to tuck
        (TOUCH, {'body': (-112, 0, 0), 'chest': (-22, 0, 0), 'head': (-35, 0, 0),
                 'right_arm': (150, 0, 12), 'left_arm': (150, 0, -12), 'right_forearm': (45, 0, 0), 'left_forearm': (45, 0, 0),
                 'right_leg': (25, 0, 2), 'right_shin': (-60, 0, 0), 'left_leg': (25, 0, -2), 'left_shin': (-60, 0, 0),
                 'right_item_sword': blade_in}, 'in'),
        # the roll, tucked tight (the refined forward roll's group)
        (0.65, {'body': (-185, 0, 0), 'chest': (-35, 0, 0), 'head': (-28, 0, 0),
                'right_arm': (80, 0, 18), 'right_forearm': (75, 0, 0), 'left_arm': (80, 0, -18), 'left_forearm': (75, 0, 0),
                'right_leg': (105, 0, 5), 'right_shin': (-128, 0, 0), 'left_leg': (105, 0, -5), 'left_shin': (-128, 0, 0),
                'right_item_sword': blade_in}, 'linear'),
        (0.765, {'body': (-280, 0, 0), 'chest': (-35, 0, 0), 'head': (-28, 0, 0),
                'right_arm': (72, 0, 18), 'right_forearm': (80, 0, 0), 'left_arm': (72, 0, -18), 'left_forearm': (80, 0, 0),
                'right_leg': (108, 0, 5), 'right_shin': (-128, 0, 0), 'left_leg': (108, 0, -5), 'left_shin': (-128, 0, 0),
                'right_item_sword': blade_in}, 'linear'),
        # out of the roll on both feet in a squat, the arms forward for balance
        (ROLL_END, {'body': (-350, 0, 0), 'chest': (-30, 0, 0), 'head': (-6, 0, 0),
                    'right_arm': (60, 0, 15), 'right_forearm': (40, 0, 0), 'left_arm': (55, 0, -15), 'left_forearm': (40, 0, 0),
                    'right_leg': (95, 0, 6), 'right_shin': (-122, 0, 0), 'left_leg': (90, 0, -6), 'left_shin': (-124, 0, 0),
                    'right_item_sword': (-40, 0, 0)}, 'out'),
        (0.99, {'body': (-360, 0, 0), 'chest': (-18, 0, 0), 'head': (2, 0, 0),
                'right_arm': (28, 0, 12), 'right_forearm': (25, 0, 0), 'left_arm': (24, 0, -12), 'left_forearm': (25, 0, 0),
                'right_leg': (42, 0, 3), 'right_shin': (-70, 0, 0), 'left_leg': (40, 0, -3), 'left_shin': (-72, 0, 0),
                'right_item_sword': (0, 0, 0)}, 'inout'),
        (DIVE_LEN, {'body': (-360, 0, 0), 'right_item_sword': (0, 0, 0)}, 'inout'),
    ]
    d = L.Anim('dive_design', DIVE_LEN)
    for t, pose, ease in keys:
        d.key(t, pose, ease, whole=True)
    a = L.Anim('dive_roll', DIVE_LEN, 'once')
    prev = {}
    for t in kit.frames(0, DIVE_LEN):
        p = kit.grounded(kit.design(d, t))
        p['body'] = tuple(kit.continuous(prev.get('body'), p['body']))
        prev['body'] = p['body']
        a.key(t, p)
    a.tol_rot, a.tol_pos = 0.4, 0.08
    a.lift = DIVE_LIFT
    return a


# --------------------------------------------------------------------------------------------
# land_heavy
# --------------------------------------------------------------------------------------------

#: The heavy landing: the feet touch at 0, it crumples onto the right knee and both hands by DOWN,
#: stays there breathing hard until TRY, a hand on the knee, a push up that nearly fails (SWAY), up
#: on the feet bent over by UP, straight by HEAVY_LEN.
DOWN, TRY, SWAY, UP, HEAVY_LEN = 0.22, 1.25, 1.75, 2.35, 3.0
NOT_ARMS = [b for b in kit.PLAYER_BONES if 'arm' not in b]


def land_heavy():
    """The fall hits hard: the legs fold at once into a deep squat, the right knee goes down to the
    ground and both hands slap onto it in front, the head hangs. Heavy breathing (the back rises and
    falls, the shoulders with it). Then the left hand goes onto the left knee and pushes; the body
    comes up, wobbles and sinks back a little (it nearly does not make it), comes up again bent
    over, a hand still on the knee, and straightens slowly into the stance."""
    # On the right knee (the shin along the ground), the left foot ahead, the whole body leaning
    # from the hips so the straight arms reach the ground in front; the legs make up for the lean.
    kneel = {'body': (-29, 0, 0), 'chest': (-52, 0, 0), 'head': (-26, 0, 0),
             'right_leg': (44, 0, 4), 'right_shin': (-112, 0, 0), 'left_leg': (112, 0, -6), 'left_shin': (-100, 0, 0),
             'right_arm': (75, 0, 10), 'right_forearm': (8, 0, 0), 'left_arm': (75, 0, -10), 'left_forearm': (8, 0, 0)}
    keys = [
        # falling into it: legs a little bent, arms up and out
        (0.0, {'right_leg': (18, 0, 4), 'right_shin': (-26, 0, 0), 'left_leg': (14, 0, -4), 'left_shin': (-22, 0, 0),
               'right_arm': (40, 0, 48), 'left_arm': (40, 0, -48), 'right_forearm': (20, 0, 0), 'left_forearm': (20, 0, 0),
               'head': (6, 0, 0)}, 'auto'),
        # the squat at once (the legs give)
        (0.08, {'body': (-15, 0, 0), 'chest': (-35, 0, 0), 'head': (-24, 0, 0),
                'right_leg': (103, 0, 6), 'right_shin': (-138, 0, 0), 'left_leg': (103, 0, -6), 'left_shin': (-138, 0, 0),
                'right_arm': (60, 0, 18), 'right_forearm': (30, 0, 0), 'left_arm': (60, 0, -18), 'left_forearm': (30, 0, 0)}, 'hit'),
        (DOWN, kneel, 'out'),
        (TRY, kneel, 'auto'),
        # the left hand onto the left knee, then the push up
        (1.45, {**kneel, 'body': (-18, 0, 0), 'chest': (-38, 0, 0), 'head': (-24, 0, 0), 'right_leg': (33, 0, 4),
                'left_leg': (101, 0, -6), 'left_arm': (35, 0, -12), 'left_forearm': (40, 0, 0)}, 'inout'),
        (1.62, {'body': (-12, 0, 0), 'chest': (-34, 0, 0), 'head': (-20, 0, 0),
                'right_leg': (62, 0, 4), 'right_shin': (-90, 0, 0), 'left_leg': (82, 0, -6), 'left_shin': (-82, 0, 0),
                'right_arm': (40, 0, 14), 'right_forearm': (30, 0, 0), 'left_arm': (35, 0, -12), 'left_forearm': (40, 0, 0)}, 'inout'),
        # the wobble: it sways to the right and sinks back a little
        (SWAY, {'body': (-14, 0, -7), 'chest': (-40, 0, 0), 'head': (-28, 0, 4),
                'right_leg': (76, 0, 6), 'right_shin': (-108, 0, 0), 'left_leg': (92, 0, -6), 'left_shin': (-96, 0, 0),
                'right_arm': (30, 0, 30), 'right_forearm': (25, 0, 0), 'left_arm': (38, 0, -12), 'left_forearm': (42, 0, 0)}, 'inout'),
        # up again, bent over, the hand still on the knee
        (UP, {'body': (-6, 0, 0), 'chest': (-26, 0, 0), 'head': (-10, 0, 0),
              'right_leg': (20, 0, 3), 'right_shin': (-24, 0, 0), 'left_leg': (28, 0, -3), 'left_shin': (-30, 0, 0),
              'right_arm': (8, 0, 10), 'right_forearm': (12, 0, 0), 'left_arm': (30, 0, -8), 'left_forearm': (34, 0, 0)}, 'inout'),
        (2.7, {'chest': (-8, 0, 0), 'head': (-4, 0, 0), 'right_leg': (4, 0, 1), 'right_shin': (-6, 0, 0),
               'left_leg': (6, 0, -1), 'left_shin': (-8, 0, 0), 'right_arm': (4, 0, 6), 'right_forearm': (8, 0, 0),
               'left_arm': (10, 0, -6), 'left_forearm': (14, 0, 0)}, 'inout'),
        (HEAVY_LEN, {}, 'inout'),
    ]
    d = L.Anim('heavy_design', HEAVY_LEN)
    for t, pose, ease in keys:
        d.key(t, pose, ease, whole=True)
    # the hands on the ground (both, from the fall until the left one goes to the knee, the right
    # until the push up), and the left hand on the left knee while it pushes
    rhand_w = kit.keys((0.0, 0), (0.1, 0), (DOWN, 1), (1.5, 1), (1.62, 0))
    lhand_w = kit.keys((0.0, 0), (0.1, 0), (DOWN, 1), (TRY, 1), (1.36, 0))
    lknee_w = kit.keys((0.0, 0), (1.36, 0), (1.45, 1), (UP, 1), (2.55, 0))
    breath = lambda t: math.sin(2 * math.pi * (t - DOWN) / 0.72) * (1 - kit.span(t, TRY, 1.6)) if t > DOWN else 0.0  # noqa: E731
    a = L.Anim('land_heavy', HEAVY_LEN, 'once')
    prev = {}
    for t in kit.frames(0, HEAVY_LEN):
        p = kit.design(d, t)
        b = breath(t)
        c = p['chest']
        p['chest'] = (c[0] + 3.5 * b, c[1], c[2])
        h = p['head']
        p['head'] = (h[0] - 2.0 * b, h[1], h[2])
        p = kit.grounded(p, bones=NOT_ARMS)
        W = kit.fk(p)
        fr, fl = kit.pt(W, 'rshoulder'), kit.pt(W, 'lshoulder')
        knee = kit.pt(W, 'lknee')
        tg = [('rhand', (fr[0] + 0.5, 0.6, fr[2] - 1.5), rhand_w(t)),
              ('lhand', (fl[0] - 0.5, 0.6, fl[2] - 1.5), lhand_w(t)),
              ('lhand', (knee[0], knee[1] + 1.0, knee[2] + 0.5), lknee_w(t))]
        p = ledge.on_targets(p, [x for x in tg if x[2] > 1e-3][:3], prev, 'heavy')
        a.key(t, p)
    a.tol_rot, a.tol_pos = 0.4, 0.08
    return a
