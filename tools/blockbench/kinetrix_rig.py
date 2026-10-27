"""Blockbench rig of the Kinetrix player, and its animations converted for playerAnimator.

    python3 tools/blockbench/kinetrix_rig.py make OUT.bbmodel
        Writes the rig: the player model cut at the joints Kinetrix bends (elbows, knees, waist),
        pivots where Kinetrix puts them, the standard skin layout (any 64x64 skin can be dropped
        on it), a sword and a bow in the right hand, and a few example animations.

    python3 tools/blockbench/kinetrix_rig.py make-opponent OUT.bbmodel [--distance PX]
        A second figure (bones enemy_*, grey skin, no animations) standing in front of the player and
        facing them, to import into a rig model (File > Import > Import Project) and pose against.
        The converter ignores it.

    python3 tools/blockbench/kinetrix_rig.py convert IN.bbmodel OUT_DIR [--namespace NS]
        Every animation of a .bbmodel made from the rig becomes an emotecraft-format
        player_animation JSON (pitch/yaw/roll, positions, bend + bend axis per part), sampled per
        game tick. Kinetrix draws the bends of any playerAnimator animation, so these play with
        real elbows, knees and waist. Prints how far the game pose is from the Blockbench pose.

    python3 tools/blockbench/kinetrix_rig.py preview IN.bbmodel OUT_DIR [--frames N]
        Pictures of every animation (front, side, three-quarter) at a few moments: the Blockbench
        pose next to the pose rebuilt the way Kinetrix will draw it from the converted JSON.

Coordinates. Blockbench works in its own space: y up, pixels, the model faces -z, the player's
right side at +x. Java model space is the same turned half a turn about z (x and y negated,
y = 24 - y). Hence Java rotations are (-X, -Y, Z) of Blockbench ones. Blockbench 5 stores
keyframes like that; files before 5.0 stored rotation X, Y and position X negated.
"""
import argparse
import base64
import io
import json
import math
import os
import sys
import uuid as uuidlib

import numpy as np

# ----------------------------------------------------------------------------------------------
# Rig: bones, pivots, cubes. Blockbench coordinates (pixels, y up, front = -z).
# ----------------------------------------------------------------------------------------------

#: playerAnimator rotates the whole body about 0.7 blocks above the feet (entity space); the
#: player model is drawn at 0.9375 scale, so that is 11.95 model pixels.
MODEL_SCALE = 0.9375
BODY_PIVOT_Y = 0.7 / MODEL_SCALE * 16.0

# name, parent, origin (pivot). Lower halves pivot on the outer edge of the joint for the natural
# bend, as Kinetrix does: elbows on the back edge, knees on the front edge, waist on the back.
BONES = [
    ("body", None, (0.0, BODY_PIVOT_Y, 0.0)),
    ("torso", "body", (0.0, 24.0, 0.0)),
    ("chest", "torso", (0.0, 18.0, 2.0)),
    ("head", "chest", (0.0, 24.0, 0.0)),
    ("right_arm", "chest", (5.0, 22.0, 0.0)),
    ("right_forearm", "right_arm", (6.0, 18.0, 2.0)),
    ("left_arm", "chest", (-5.0, 22.0, 0.0)),
    ("left_forearm", "left_arm", (-6.0, 18.0, 2.0)),
    ("right_leg", "body", (1.9, 12.0, 0.0)),
    ("right_shin", "right_leg", (1.9, 6.0, -2.0)),
    ("left_leg", "body", (-1.9, 12.0, 0.0)),
    ("left_shin", "left_leg", (-1.9, 6.0, -2.0)),
]
BONE = {b[0]: b for b in BONES}

# Java part -> (upper bone, lower bone or None, Java default pivot, playerAnimator name).
PARTS = {
    "torso": ("torso", "chest", (0.0, 0.0, 0.0)),
    "head": ("head", None, (0.0, 0.0, 0.0)),
    "rightArm": ("right_arm", "right_forearm", (-5.0, 2.0, 0.0)),
    "leftArm": ("left_arm", "left_forearm", (5.0, 2.0, 0.0)),
    "rightLeg": ("right_leg", "right_shin", (-1.9, 12.0, 0.0)),
    "leftLeg": ("left_leg", "left_shin", (1.9, 12.0, 0.0)),
}
# Kinetrix joints (JointRig/Joint): joint line, centre and half size in part space, which half moves.
JOINTS = {
    "torso": dict(jy=6.0, cx=0.0, cz=0.0, hx=4.0, hz=2.0, upper=True),
    "rightArm": dict(jy=4.0, cx=-1.0, cz=0.0, hx=2.0, hz=2.0, upper=False),
    "leftArm": dict(jy=4.0, cx=1.0, cz=0.0, hx=2.0, hz=2.0, upper=False),
    "rightLeg": dict(jy=6.0, cx=0.0, cz=0.0, hx=2.0, hz=2.0, upper=False),
    "leftLeg": dict(jy=6.0, cx=0.0, cz=0.0, hx=2.0, hz=2.0, upper=False),
}

# Skin layout: (u, v) of the full box, width, height, depth; the overlay layer's (u, v) and inflation.
SKIN = {
    "head": ((0, 0), (8, 8, 8), (32, 0), 0.5),
    "torso": ((16, 16), (8, 12, 4), (16, 32), 0.25),
    "right_arm": ((40, 16), (4, 12, 4), (40, 32), 0.25),
    "left_arm": ((32, 48), (4, 12, 4), (48, 48), 0.25),
    "right_leg": ((0, 16), (4, 12, 4), (0, 32), 0.25),
    "left_leg": ((16, 48), (4, 12, 4), (0, 48), 0.25),
}

# Cubes: name, bone, skin key, from, to, first row of the box this piece starts at, rows.
CUBES = [
    ("head", "head", "head", (-4, 24, -4), (4, 32, 4), 0, 8),
    ("torso_lower", "torso", "torso", (-4, 12, -2), (4, 18, 2), 6, 6),
    ("torso_upper", "chest", "torso", (-4, 18, -2), (4, 24, 2), 0, 6),
    ("right_upper_arm", "right_arm", "right_arm", (4, 18, -2), (8, 24, 2), 0, 6),
    ("right_forearm", "right_forearm", "right_arm", (4, 12, -2), (8, 18, 2), 6, 6),
    ("left_upper_arm", "left_arm", "left_arm", (-8, 18, -2), (-4, 24, 2), 0, 6),
    ("left_forearm", "left_forearm", "left_arm", (-8, 12, -2), (-4, 18, 2), 6, 6),
    ("right_thigh", "right_leg", "right_leg", (-0.1, 6, -2), (3.9, 12, 2), 0, 6),
    ("right_shin", "right_shin", "right_leg", (-0.1, 0, -2), (3.9, 6, 2), 6, 6),
    ("left_thigh", "left_leg", "left_leg", (-3.9, 6, -2), (0.1, 12, 2), 0, 6),
    ("left_shin", "left_shin", "left_leg", (-3.9, 0, -2), (0.1, 6, 2), 6, 6),
]

S_FLIP = np.diag([-1.0, -1.0, 1.0])
C_OFF = np.array([0.0, 24.0, 0.0])


def to_java(b):
    """Blockbench point (pixels) -> Java model space."""
    return S_FLIP @ (np.asarray(b, float) - C_OFF)


def to_bb(j):
    return S_FLIP @ np.asarray(j, float) + C_OFF


# ----------------------------------------------------------------------------------------------
# Math
# ----------------------------------------------------------------------------------------------

def rx(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]], float)


def ry(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]], float)


def rz(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]], float)


def rot_zyx(x, y, z):
    """Rz(z) Ry(y) Rx(x): Blockbench (Euler order ZYX) and Java's rotationZYX alike. Radians."""
    return rz(z) @ ry(y) @ rx(x)


def continue_angles(prev, ang):
    """The Z-Y-X angles of the same rotation closest to the previous tick's. The game blends ticks
    angle by angle, so a full roll written as +179 then -179 degrees would whip back the long way:
    pick the Euler solution (x, y, z) or (x + pi, pi - y, z + pi), shifted by whole turns."""
    if prev is None:
        return tuple(ang)
    best = None
    for cand in (ang, (ang[0] + math.pi, math.pi - ang[1], ang[2] + math.pi)):
        c = tuple(a + 2 * math.pi * round((q - a) / (2 * math.pi)) for a, q in zip(cand, prev))
        d = sum((a - q) ** 2 for a, q in zip(c, prev))
        if best is None or d < best[0]:
            best = (d, c)
    return best[1]


def euler_zyx(m):
    """Inverse of rot_zyx: (x, y, z) radians."""
    sy = -m[2, 0]
    sy = max(-1.0, min(1.0, sy))
    y = math.asin(sy)
    if abs(sy) < 0.999999:
        x = math.atan2(m[2, 1], m[2, 2])
        z = math.atan2(m[1, 0], m[0, 0])
    else:  # gimbal lock: put everything in x
        x = math.atan2(-m[1, 2], m[1, 1])
        z = 0.0
    return x, y, z


def mat4(r=None, t=None):
    m = np.eye(4)
    if r is not None:
        m[:3, :3] = r
    if t is not None:
        m[:3, 3] = t
    return m


def tr(t):
    return mat4(t=t)


def apply(m, p):
    return (m @ np.append(np.asarray(p, float), 1.0))[:3]


def quat_from_matrix(m):
    t = m[0, 0] + m[1, 1] + m[2, 2]
    if t > 0:
        s = math.sqrt(t + 1.0) * 2
        w, x, y, z = 0.25 * s, (m[2, 1] - m[1, 2]) / s, (m[0, 2] - m[2, 0]) / s, (m[1, 0] - m[0, 1]) / s
    elif m[0, 0] > m[1, 1] and m[0, 0] > m[2, 2]:
        s = math.sqrt(1.0 + m[0, 0] - m[1, 1] - m[2, 2]) * 2
        w, x, y, z = (m[2, 1] - m[1, 2]) / s, 0.25 * s, (m[0, 1] + m[1, 0]) / s, (m[0, 2] + m[2, 0]) / s
    elif m[1, 1] > m[2, 2]:
        s = math.sqrt(1.0 + m[1, 1] - m[0, 0] - m[2, 2]) * 2
        w, x, y, z = (m[0, 2] - m[2, 0]) / s, (m[0, 1] + m[1, 0]) / s, 0.25 * s, (m[1, 2] + m[2, 1]) / s
    else:
        s = math.sqrt(1.0 + m[2, 2] - m[0, 0] - m[1, 1]) * 2
        w, x, y, z = (m[1, 0] - m[0, 1]) / s, (m[0, 2] + m[2, 0]) / s, (m[1, 2] + m[2, 1]) / s, 0.25 * s
    q = np.array([w, x, y, z])
    return q / np.linalg.norm(q)


def quat_mul(a, b):
    w1, x1, y1, z1 = a
    w2, x2, y2, z2 = b
    return np.array([w1 * w2 - x1 * x2 - y1 * y2 - z1 * z2,
                     w1 * x2 + x1 * w2 + y1 * z2 - z1 * y2,
                     w1 * y2 - x1 * z2 + y1 * w2 + z1 * x2,
                     w1 * z2 + x1 * y2 - y1 * x2 + z1 * w2])


def hinge_from_rotation(r_java, prev_axis=0.0):
    """A relative rotation of the lower half (Java part space) as playerAnimator's (axis, bend):
    the swing about a horizontal axis; the twist about the limb (y) is returned separately,
    because a hinge cannot hold it."""
    q = quat_from_matrix(r_java)
    if q[0] < 0:
        q = -q
    w, x, y, z = q
    tn = math.hypot(w, y)
    twist = np.array([w / tn, 0.0, y / tn, 0.0]) if tn > 1e-9 else np.array([1.0, 0, 0, 0])
    swing = quat_mul(q, np.array([twist[0], 0.0, -twist[2], 0.0]))
    if swing[0] < 0:
        swing = -swing
    sw = max(-1.0, min(1.0, swing[0]))
    angle = 2.0 * math.acos(sw)
    twist_angle = 2.0 * math.atan2(twist[2], twist[0])
    s = math.sqrt(max(0.0, 1.0 - sw * sw))
    if angle < 1e-6 or s < 1e-9:
        return prev_axis, 0.0, twist_angle
    ux, uz = swing[1] / s, swing[3] / s
    if ux < -1e-9 or (abs(ux) <= 1e-9 and uz < 0):
        ux, uz, angle = -ux, -uz, -angle
    axis = -math.atan2(uz, ux)
    return axis, angle, twist_angle


def kinetrix_hinge(j, axis, bend):
    """Joint.hinge: pivot on the outer edge, rotation about (cos -axis, 0, sin -axis). 4x4, part space."""
    if abs(bend) <= 1e-6:
        return np.eye(4)
    ax, az = math.cos(-axis), math.sin(-axis)
    s = -1.0 if j["upper"] else 1.0
    dx, dz = -az * s, ax * s
    if bend < 0:
        dx, dz = -dx, -dz
    pivot = np.array([j["cx"] - j["hx"] * dx, j["jy"], j["cz"] - j["hz"] * dz])
    n = np.array([ax, 0.0, az])
    k = np.array([[0, -n[2], n[1]], [n[2], 0, -n[0]], [-n[1], n[0], 0]])
    r = np.eye(3) + math.sin(bend) * k + (1 - math.cos(bend)) * (k @ k)
    return tr(pivot) @ mat4(r) @ tr(-pivot)


# ----------------------------------------------------------------------------------------------
# Texture: a guide skin in the standard 64x64 layout (right side red, left side blue)
# ----------------------------------------------------------------------------------------------

def guide_texture():
    from PIL import Image
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    px = img.load()
    skin = (214, 168, 124, 255)

    def box(u0, v0, w, h, d, color):
        """color(face, col, row) for every pixel of a box's layout; face in n/s/e/w/u/d."""
        regions = {
            "e": (u0, v0 + d, d, h), "n": (u0 + d, v0 + d, w, h), "w": (u0 + d + w, v0 + d, d, h),
            "s": (u0 + 2 * d + w, v0 + d, w, h), "u": (u0 + d, v0, w, d), "d": (u0 + d + w, v0, w, d),
        }
        for face, (x0, y0, rw, rh) in regions.items():
            for yy in range(rh):
                for xx in range(rw):
                    c = color(face, xx, yy)
                    if c:
                        px[x0 + xx, y0 + yy] = c

    def shade(c, f):
        return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (255,)

    def head(face, x, y):
        if face == "u":
            return (92, 62, 40, 255)
        c = skin
        if y <= 1:
            c = (92, 62, 40, 255)
        if face == "s" and y <= 5:
            c = (92, 62, 40, 255)
        if face == "n":
            if y == 4 and x in (1, 6):
                c = (250, 250, 250, 255)
            if y == 4 and x in (2, 5):
                c = (40, 50, 120, 255)
            if y == 6 and x in (3, 4):
                c = (150, 70, 60, 255)
        return c

    def torso(face, x, y):
        base = (46, 139, 139, 255)
        if face == "s":
            base = shade(base, 0.7)
        if face in ("e", "w"):
            base = shade(base, 0.85)
        if face in ("u", "d"):
            return shade(base, 0.8)
        if y == 6:
            return shade(base, 0.55)  # waist line (the joint)
        if face == "n" and y <= 1 and x in (3, 4):
            return (240, 240, 240, 255)  # collar: the front
        if face == "n" and 2 <= y <= 4 and x in (3, 4):
            return (240, 240, 240, 255)
        return base

    def limb(upper, lower, foot, foot_rows):
        def f(face, x, y):
            if face == "u":
                return upper
            if face == "d":
                return foot
            c = upper if y < 6 else lower
            if y >= 12 - foot_rows:
                c = foot
            if face == "s":
                c = shade(c, 0.75)  # the back: the elbow point is there
            if y == 6:
                c = shade(c, 0.55)  # joint line
            return c
        return f

    box(0, 0, 8, 8, 8, head)
    box(16, 16, 8, 12, 4, torso)
    box(40, 16, 4, 12, 4, limb((192, 57, 43, 255), (229, 115, 104, 255), skin, 2))
    box(32, 48, 4, 12, 4, limb((44, 111, 187, 255), (110, 160, 220, 255), skin, 2))
    box(0, 16, 4, 12, 4, limb((142, 43, 43, 255), (176, 74, 74, 255), (80, 80, 80, 255), 2))
    box(16, 48, 4, 12, 4, limb((30, 60, 140, 255), (70, 100, 180, 255), (80, 80, 80, 255), 2))
    out = io.BytesIO()
    img.save(out, "PNG")
    return out.getvalue()


PROP_COLORS = {"blade": (0, 0), "edge": (4, 0), "handle": (8, 0), "guard": (12, 0), "bow": (0, 4), "string": (4, 4)}


def props_texture():
    from PIL import Image
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    colors = {"blade": (190, 200, 215), "edge": (240, 245, 250), "handle": (110, 70, 40),
              "guard": (220, 180, 50), "bow": (130, 82, 40), "string": (235, 235, 235)}
    for k, (u, v) in PROP_COLORS.items():
        for y in range(v, v + 4):
            for x in range(u, u + 4):
                img.putpixel((x, y), colors[k] + (255,))
    out = io.BytesIO()
    img.save(out, "PNG")
    return out.getvalue()


# ----------------------------------------------------------------------------------------------
# Props: where a held item is, at rest (ItemInHandLayer + item display transforms)
# ----------------------------------------------------------------------------------------------

HAND_ROT = rx(math.radians(-90)) @ ry(math.radians(180))  # ItemInHandLayer.renderArmWithItem


def item_frame(side, display_rot, display_trans, scale, sprite_turn):
    """The chain from the arm to an item sprite, at rest, for the given hand: ItemInHandLayer
    (hand offset, -90 about x, 180 about y) and the item's third-person display transform
    (mirrored for the left hand, as ItemTransform.apply does). Returns a dict with the Blockbench
    group origin and rotation (internal Euler degrees) whose local axes are the sprite's (x, y
    flipped), and the pieces the converter needs."""
    left = side == "left"
    # ItemTransform.apply(leftHand): rotation y and z and translation x change sign.
    arm_pivot = np.array(PARTS["leftArm" if left else "rightArm"][2])
    h0 = np.array([-1.0 if left else 1.0, 2.0, -10.0])
    rot = np.array(display_rot, float)
    trans = np.array(display_trans, float)
    if left:
        rot = np.array([rot[0], -rot[1], -rot[2]])
        trans = np.array([-trans[0], trans[1], trans[2]])
    r_disp = rx(math.radians(rot[0])) @ ry(math.radians(rot[1])) @ rz(math.radians(rot[2]))
    r_d = r_disp @ rz(math.radians(sprite_turn))
    t = arm_pivot + HAND_ROT @ (h0 + trans)
    r_bb = S_FLIP @ HAND_ROT @ r_d @ S_FLIP
    x, y, z = euler_zyx(r_bb)
    return dict(origin=to_bb(t), rotation=[math.degrees(x), math.degrees(y), math.degrees(z)], scale=scale,
                h0=h0, trans=trans, r_d=r_d, side=side)


def sprite_cubes(origin, scale, pieces):
    """Cubes of an item sprite drawn along its diagonal. pieces: (name, colour, sprite-local x
    centre, y from, y to, width), sprite-local = turned so the diagonal is +y, centre (8, 8).
    Blockbench coordinates of the group flip x and y."""
    o = np.asarray(origin)
    out = []
    for name, tex, xc, y0, y1, w in pieces:
        lo = np.array([-(xc + w / 2), -y1, -0.5]) * scale + o
        hi = np.array([-(xc - w / 2), -y0, 0.5]) * scale + o
        out.append((name, tex, lo.tolist(), hi.tolist()))
    return out


# Silhouettes of the vanilla sword and bow sprites (handle at the lower left, tip or arc at the
# upper right, 45 degrees), in sprite-local pixels. Shapes only, coloured by props.png.
SWORD_PIECES = [("sword_handle", "handle", 0.0, -9.6, -3.6, 1.4), ("sword_guard", "guard", 0.0, -3.6, -2.4, 6.0),
                ("sword_blade", "blade", 0.0, -2.4, 9.4, 2.4), ("sword_tip", "edge", 0.0, 9.4, 10.4, 1.0)]
BOW_PIECES = [("bow_upper_1", "bow", -1.5, 6.0, 9.2, 1.2), ("bow_upper_2", "bow", -3.0, 3.0, 6.0, 1.2),
              ("bow_upper_3", "bow", -3.8, 1.0, 3.0, 1.2), ("bow_grip", "handle", -3.9, -2.5, 1.0, 1.6),
              ("bow_lower_1", "bow", -3.4, -5.0, -2.5, 1.2), ("bow_lower_2", "bow", -2.0, -7.5, -5.0, 1.2),
              ("bow_lower_3", "bow", 0.0, -9.2, -7.5, 1.2), ("bow_string", "string", 0.3, -8.0, 8.0, 0.3)]


def sword_cubes(origin, scale):
    return sprite_cubes(origin, scale, SWORD_PIECES)


def bow_cubes(origin, scale):
    return sprite_cubes(origin, scale, BOW_PIECES)


# ----------------------------------------------------------------------------------------------
# Writing the .bbmodel
# ----------------------------------------------------------------------------------------------

def uid():
    return str(uuidlib.uuid4())


def limb_face_uvs(u0, v0, w, h, d, top, rows):
    vt, vb = v0 + d + top, v0 + d + top + rows
    first, last = top == 0, top + rows == h
    f = {
        "east": [u0, vt, u0 + d, vb],
        "west": [u0 + d + w, vt, u0 + 2 * d + w, vb],
        "north": [u0 + d, vt, u0 + d + w, vb],
        "south": [u0 + 2 * d + w, vt, u0 + 2 * d + 2 * w, vb],
        # Faces inside a joint take one row of the limb's colour, so a bend shows no hole.
        "up": [u0 + d + w, v0 + d, u0 + d, v0] if first else [u0 + d, vt, u0 + d + w, vt + 1],
        "down": [u0 + d + 2 * w, v0, u0 + d + w, v0 + d] if last else [u0 + d, vb - 1, u0 + d + w, vb],
    }
    return f


def cube_json(name, frm, to, origin, faces, texture, inflate=0.0, visible=True):
    c = {
        "name": name, "box_uv": False, "rescale": False, "locked": False, "render_order": "default",
        "allow_mirror_modeling": True, "from": [round(v, 4) for v in frm], "to": [round(v, 4) for v in to],
        "autouv": 0, "color": 0, "origin": [round(v, 4) for v in origin],
        "faces": {k: {"uv": v, "texture": texture} for k, v in faces.items()},
        "type": "cube", "uuid": uid(),
    }
    if inflate:
        c["inflate"] = inflate
    if not visible:
        c["visibility"] = False
    return c


def build_rig():
    """(elements, outliner, bone uuids)."""
    elements = []
    children = {b[0]: [] for b in BONES}
    for name, bone, key, frm, to, top, rows in CUBES:
        (u0, v0), (w, h, d), (ou, ov), infl = SKIN[key]
        origin = BONE[bone][2]
        c = cube_json(name, frm, to, origin, limb_face_uvs(u0, v0, w, h, d, top, rows), 0)
        elements.append(c)
        children[bone].append(c["uuid"])
        o = cube_json(name + "_layer", frm, to, origin, limb_face_uvs(ou, ov, w, h, d, top, rows), 0, inflate=infl)
        elements.append(o)
        children[bone].append(o["uuid"])

    groups = {}
    for name, parent, origin in BONES:
        groups[name] = {"name": name, "origin": list(origin), "color": 0, "uuid": uid(), "export": True,
                        "mirror_uv": False, "isOpen": True, "locked": False, "visibility": True,
                        "autouv": 0, "children": list(children[name])}

    # Items in the hands. Rotating one of these groups turns the held item in the fist.
    for item, spec in ITEMS.items():
        fr = item_frame(spec["side"], *spec["display"])
        origin, rot, visible = fr["origin"], fr["rotation"], spec["visible"]
        g = {"name": item, "origin": [round(v, 4) for v in origin], "rotation": [round(v, 3) for v in rot],
             "color": 3, "uuid": uid(), "export": False, "mirror_uv": False, "isOpen": False, "locked": False,
             "visibility": visible, "autouv": 0, "children": []}
        cubes_fn = sword_cubes if spec["shape"] == "sword" else bow_cubes
        for cname, tex, lo, hi in cubes_fn(origin, fr["scale"]):
            u, v = PROP_COLORS[tex]
            faces = {f: [u, v, u + 4, v + 4] for f in ("north", "south", "east", "west", "up", "down")}
            c = cube_json(cname, lo, hi, origin, faces, 1, visible=visible)
            elements.append(c)
            g["children"].append(c["uuid"])
        groups[spec["side"] + "_forearm"]["children"].append(g)
        groups[item] = g

    for name, parent, origin in reversed(BONES):
        if parent:
            groups[parent]["children"].insert(0, groups[name])
    outliner = [groups["body"]]
    return elements, outliner, {n: g["uuid"] for n, g in groups.items()}


# Held items: which hand, sprite shape, the third-person display of that hand as the vanilla item
# model writes it (item/handheld for swords, item/bow; thirdperson_righthand / _lefthand), with
# the sprite's diagonal turn, and whether it is shown.
ITEMS = {
    "right_item_sword": dict(side="right", shape="sword", display=((0, -90, 55), (0, 4, 0.5), 0.85, -45), visible=True),
    "right_item_bow": dict(side="right", shape="bow", display=((-80, 260, -40), (-1, -2, 2.5), 0.9, -45), visible=False),
    "left_item_sword": dict(side="left", shape="sword", display=((0, 90, -55), (0, 4, 0.5), 0.85, -45), visible=False),
}


def texture_json(name, png, w, h, idx):
    return {"path": "", "name": name, "folder": "", "namespace": "", "id": str(idx), "particle": idx == 0,
            "render_mode": "default", "render_sides": "auto", "frame_time": 1, "frame_order_type": "loop",
            "frame_order": "", "frame_interpolate": False, "visible": True, "internal": True, "saved": False,
            "uuid": uid(), "width": w, "height": h, "uv_width": w, "uv_height": h, "mode": "bitmap",
            "source": "data:image/png;base64," + base64.b64encode(png).decode()}


# Example animations, Blockbench (internal, Blockbench 5) degrees and pixels:
#   thigh / whole arm forward = +X, forearm folds forward (natural elbow) = +X,
#   shin folds back (natural knee) = -X, chest leans forward = -X, head looks down = -X,
#   whole body leans back = +X, right arm out to the side = +Z, left arm out = -Z,
#   +Y turns a part to the model's left.
EXAMPLES = {
    "bend_demo": dict(length=2.0, loop="loop", keys={
        0.0: {},
        0.4: {"right_forearm": dict(r=(90, 0, 0)), "left_forearm": dict(r=(90, 0, 0))},
        0.8: {"right_forearm": dict(r=(90, 0, 0)), "left_forearm": dict(r=(90, 0, 0)),
              "right_leg": dict(r=(45, 0, 0)), "left_leg": dict(r=(45, 0, 0)),
              "right_shin": dict(r=(-90, 0, 0)), "left_shin": dict(r=(-90, 0, 0)),
              "body": dict(p=(0, -3.5, 0)), "chest": dict(r=(-30, 0, 0))},
        1.3: {"right_arm": dict(r=(160, 0, 0)), "left_arm": dict(r=(160, 0, 0)), "chest": dict(r=(10, 0, 0))},
        2.0: {},
    }),
    "slide": dict(length=0.9, loop="once", keys={
        0.0: {},
        0.15: {"body": dict(p=(0, -9.0, 0), r=(35, 0, 0)), "chest": dict(r=(-16, 0, 0)),
               "head": dict(r=(-22, 0, 0)),
               "right_leg": dict(r=(55, 0, 4)), "right_shin": dict(r=(-6, 0, 0)),
               "left_leg": dict(r=(52, 0, -22)), "left_shin": dict(r=(-112, 0, 0)),
               "right_arm": dict(r=(-48, 0, 16)), "right_forearm": dict(r=(10, 0, 0)),
               "left_arm": dict(r=(80, 0, -12)), "left_forearm": dict(r=(30, 0, 0))},
        0.6: {"body": dict(p=(0, -8.6, 0), r=(32, 0, 0)), "chest": dict(r=(-18, 0, 0)),
              "head": dict(r=(-20, 0, 0)),
              "right_leg": dict(r=(52, 0, 4)), "right_shin": dict(r=(-8, 0, 0)),
              "left_leg": dict(r=(50, 0, -22)), "left_shin": dict(r=(-110, 0, 0)),
              "right_arm": dict(r=(-44, 0, 18)), "right_forearm": dict(r=(14, 0, 0)),
              "left_arm": dict(r=(74, 0, -12)), "left_forearm": dict(r=(34, 0, 0))},
        0.9: {},
    }),
    "cross_guard": dict(length=1.0, loop="hold", keys={
        0.0: {},
        0.2: {"right_arm": dict(r=(35, 22, 0)), "right_forearm": dict(r=(60, 0, 0)),
              "left_arm": dict(r=(35, -22, 0)), "left_forearm": dict(r=(60, 0, 0)),
              "right_item_sword": dict(r=(-45, 0, 0)), "left_item_sword": dict(r=(45, 0, 0)),
              "chest": dict(r=(-8, 0, 0)), "head": dict(r=(-6, 0, 0)),
              "right_leg": dict(r=(14, 0, 0)), "right_shin": dict(r=(-20, 0, 0)),
              "left_leg": dict(r=(-10, 0, 0)), "body": dict(p=(0, -0.6, 0))},
        1.0: {"right_arm": dict(r=(35, 22, 0)), "right_forearm": dict(r=(60, 0, 0)),
              "left_arm": dict(r=(35, -22, 0)), "left_forearm": dict(r=(60, 0, 0)),
              "right_item_sword": dict(r=(-45, 0, 0)), "left_item_sword": dict(r=(45, 0, 0)),
              "chest": dict(r=(-8, 0, 0)), "head": dict(r=(-6, 0, 0)),
              "right_leg": dict(r=(14, 0, 0)), "right_shin": dict(r=(-20, 0, 0)),
              "left_leg": dict(r=(-10, 0, 0)), "body": dict(p=(0, -0.6, 0))},
    }),
}

#: The rig is written in the pre-5.0 project format, which both Blockbench 4 and 5 open; in that
#: format keyframes store rotation X, Y and position X negated.
WRITE_FORMAT = "4.10"


def animation_json(name, spec, bone_uuids):
    animators = {}
    bones = sorted({b for keys in spec["keys"].values() for b in keys})
    for bone in bones:
        kfs = []
        for t, keys in sorted(spec["keys"].items()):
            k = keys.get(bone, {})
            r = k.get("r", (0, 0, 0))
            p = k.get("p", (0, 0, 0))
            # pre-5.0 storage
            r_s = (-r[0], -r[1], r[2])
            p_s = (-p[0], p[1], p[2])
            for channel, v in (("rotation", r_s), ("position", p_s)):
                kfs.append({"channel": channel, "data_points": [{"x": round(v[0], 4), "y": round(v[1], 4), "z": round(v[2], 4)}],
                            "uuid": uid(), "time": t, "color": -1, "interpolation": "catmullrom"})
        animators[bone_uuids[bone]] = {"name": bone, "type": "bone", "keyframes": kfs}
    return {"uuid": uid(), "name": name, "loop": spec["loop"], "override": False, "length": spec["length"],
            "snapping": 20, "selected": False, "anim_time_update": "", "blend_weight": "", "start_delay": "",
            "loop_delay": "", "animators": animators}


def make(path):
    elements, outliner, bone_uuids = build_rig()
    model = {
        "meta": {"format_version": WRITE_FORMAT, "model_format": "free", "box_uv": False},
        "name": "kinetrix_player", "model_identifier": "", "visible_box": [1, 2, 0],
        "variable_placeholders": "", "variable_placeholder_buttons": [], "timeline_setups": [],
        "unhandled_root_fields": {}, "resolution": {"width": 64, "height": 64},
        "elements": elements, "outliner": outliner,
        "textures": [texture_json("kinetrix_guide_skin.png", guide_texture(), 64, 64, 0),
                     texture_json("props.png", props_texture(), 16, 16, 1)],
        "animations": [animation_json(n, s, bone_uuids) for n, s in EXAMPLES.items()],
    }
    os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
    with open(path, "w") as f:
        json.dump(model, f, indent=1)
    print(f"wrote {path}: {len(elements)} cubes, {len(BONES)} bones, {len(EXAMPLES)} example animations")


def enemy_texture():
    """The guide skin turned grey-green, so the opponent never passes for the player."""
    from PIL import Image
    img = Image.open(io.BytesIO(guide_texture())).convert("RGBA")
    px = img.load()
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = px[x, y]
            if a:
                lum = 0.3 * r + 0.59 * g + 0.11 * b
                px[x, y] = (int(lum * 0.55), int(lum * 0.8), int(lum * 0.55), a)
    out = io.BytesIO()
    img.save(out, "PNG")
    return out.getvalue()


def make_opponent(path, distance=32.0):
    """A second figure to pose against (an attacker to slide under, a blade to block): the rig with its
    bones named enemy_*, `distance` px (16 = one block) in front of the player and facing them, a grey
    skin and no animations. Meant for File > Import > Import Project into a model made from the rig;
    the converter reads only the player's bones, so the opponent never ends up in the game files."""
    elements, outliner, _ = build_rig()
    for e in elements:
        e["name"] = "enemy_" + e["name"]
        for key in ("from", "to", "origin"):
            e[key][2] = round(e[key][2] - distance, 4)

    def place(g):
        g["name"] = "enemy_" + g["name"]
        g["origin"][2] = round(g["origin"][2] - distance, 4)
        for c in g["children"]:
            if isinstance(c, dict):
                place(c)

    place(outliner[0])
    # Turned half a turn about its own feet, it faces the player.
    root = {"name": "enemy", "origin": [0, 0, -distance], "rotation": [0, 180, 0], "color": 1, "uuid": uid(),
            "export": True, "mirror_uv": False, "isOpen": False, "locked": False, "visibility": True,
            "autouv": 0, "children": [outliner[0]]}
    model = {
        "meta": {"format_version": WRITE_FORMAT, "model_format": "free", "box_uv": False},
        "name": "kinetrix_opponent", "model_identifier": "", "visible_box": [1, 2, 0],
        "variable_placeholders": "", "variable_placeholder_buttons": [], "timeline_setups": [],
        "unhandled_root_fields": {}, "resolution": {"width": 64, "height": 64},
        "elements": elements, "outliner": [root],
        "textures": [texture_json("kinetrix_enemy_skin.png", enemy_texture(), 64, 64, 0),
                     texture_json("props.png", props_texture(), 16, 16, 1)],
        "animations": [],
    }
    os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
    with open(path, "w") as f:
        json.dump(model, f, indent=1)
    print(f"wrote {path}: opponent {distance / 16:g} blocks in front, bones enemy_*")


# ----------------------------------------------------------------------------------------------
# Reading a .bbmodel (Blockbench 4 or 5)
# ----------------------------------------------------------------------------------------------

def version_tuple(v):
    out = []
    for p in str(v).split("."):
        try:
            out.append(int(p))
        except ValueError:
            out.append(0)
    return tuple(out + [0] * (3 - len(out)))


class Model:
    def __init__(self, path, data=None):
        if data is None:
            with open(path) as f:
                data = json.load(f)
        self.data = data
        self.version = version_tuple(self.data.get("meta", {}).get("format_version", "4.0"))
        self.groups = {}  # uuid -> dict(name, origin, rotation, parent uuid)
        known = {g["uuid"]: g for g in self.data.get("groups", [])}

        def walk(items, parent):
            for it in items:
                if isinstance(it, str):
                    continue
                g = dict(known.get(it.get("uuid"), {}))
                g.update({k: v for k, v in it.items() if k != "children"})
                self.groups[g["uuid"]] = dict(name=g.get("name"), origin=np.array(g.get("origin", [0, 0, 0]), float),
                                              rotation=np.array(g.get("rotation", [0, 0, 0]), float), parent=parent,
                                              visible=g.get("visibility", True),
                                              children=[c if isinstance(c, str) else c.get("uuid") for c in it.get("children", [])])
                walk(it.get("children", []), g["uuid"])

        walk(self.data.get("outliner", []), None)
        self.by_name = {}
        for u, g in self.groups.items():
            self.by_name.setdefault(g["name"], u)
        self.elements = {e["uuid"]: e for e in self.data.get("elements", [])}
        self.animations = self.data.get("animations", [])

    # --- keyframes -----------------------------------------------------------------------------
    def channels(self, anim):
        """{bone name: {channel: [(time, (x, y, z), interpolation)]}} in Blockbench 5 (internal) values."""
        old = self.version < (5, 0, 0)
        out = {}
        for auuid, a in (anim.get("animators") or {}).items():
            if a.get("type", "bone") != "bone":
                continue
            g = self.groups.get(auuid)
            name = g["name"] if g else a.get("name")
            for kf in a.get("keyframes", []):
                ch = kf.get("channel")
                if ch not in ("rotation", "position"):
                    continue
                dp = kf["data_points"][0]
                v = []
                for axis in "xyz":
                    raw = dp.get(axis, 0)
                    try:
                        v.append(float(raw) if str(raw).strip() != "" else 0.0)
                    except ValueError:
                        raise SystemExit(f"{anim.get('name')}: {name} {ch} {axis} = {raw!r}: Molang expressions are not supported, use numbers")
                if old:
                    v[0] = -v[0]
                    if ch == "rotation":
                        v[1] = -v[1]
                out.setdefault(name, {}).setdefault(ch, []).append((float(kf.get("time", 0)), np.array(v), kf.get("interpolation", "linear")))
        for chans in out.values():
            for lst in chans.values():
                lst.sort(key=lambda k: k[0])
        return out


def sample(keys, t):
    """Blockbench interpolation of one channel at time t (linear, step, catmullrom; bezier as linear)."""
    if not keys:
        return np.zeros(3)
    if t <= keys[0][0]:
        return keys[0][1]
    if t >= keys[-1][0]:
        return keys[-1][1]
    for i in range(len(keys) - 1):
        t0, v0, i0 = keys[i]
        t1, v1, i1 = keys[i + 1]
        if t0 <= t <= t1:
            if t1 - t0 < 1e-9:
                return v1
            a = (t - t0) / (t1 - t0)
            if i0 == "step":
                return v0
            if i0 == "catmullrom" or i1 == "catmullrom":
                vp = keys[i - 1][1] if i > 0 else v0
                vn = keys[i + 2][1] if i + 2 < len(keys) else v1
                a2, a3 = a * a, a * a * a
                return 0.5 * ((2 * v0) + (-vp + v1) * a + (2 * vp - 5 * v0 + 4 * v1 - vn) * a2 + (-vp + 3 * v0 - 3 * v1 + vn) * a3)
            return v0 + (v1 - v0) * a
    return keys[-1][1]


# ----------------------------------------------------------------------------------------------
# Forward kinematics in Blockbench space, and the same pose as Kinetrix will draw it
# ----------------------------------------------------------------------------------------------

def bb_pose(model, chans, t):
    """World matrix of every group (maps pivot-relative rest coordinates to world), by name."""
    world = {}

    def get(uuid):
        if uuid in world:
            return world[uuid]
        g = model.groups[uuid]
        parent = world_of(g["parent"])
        porigin = model.groups[g["parent"]]["origin"] if g["parent"] else np.zeros(3)
        ch = chans.get(g["name"], {})
        dr = sample(ch.get("rotation", []), t)
        dp = sample(ch.get("position", []), t)
        r = np.radians(g["rotation"] + dr)
        m = parent @ tr(g["origin"] - porigin + dp) @ mat4(rot_zyx(*r))
        world[uuid] = m
        return m

    def world_of(uuid):
        return np.eye(4) if uuid is None else get(uuid)

    return {model.groups[u]["name"]: get(u) for u in model.groups}


def rigid_euler(m3):
    return euler_zyx(m3)


def convert_frame(model, chans, t, prev_axes, items=None):
    """playerAnimator values of one moment: {part: dict(x, y, z, pitch, yaw, roll[, bend, axis])}.
    items: {"right"/"left": item group name} whose turn in the fist becomes rightItem/leftItem."""
    W = bb_pose(model, chans, t)
    names = model.by_name
    missing = [b for b in ("body", "torso", "chest", "head", "right_arm", "right_forearm", "left_arm",
                           "left_forearm", "right_leg", "right_shin", "left_leg", "left_shin") if b not in names]
    if missing:
        raise SystemExit("not a Kinetrix rig, missing bones: " + ", ".join(missing))
    o = {n: model.groups[names[n]]["origin"] for n in names}
    out, warn = {}, []

    # Whole body: entity space = Blockbench space scaled by 0.9375/16, pivot 0.7 blocks up.
    Wb = W["body"]
    rb = euler_zyx(Wb[:3, :3])
    pb = (Wb[:3, 3] - o["body"]) * MODEL_SCALE / 16.0
    out["body"] = dict(x=pb[0], y=pb[1], z=pb[2], pitch=rb[0], yaw=rb[1], roll=rb[2])

    inv_body = np.linalg.inv(Wb)
    MS = mat4(S_FLIP)

    def java_matrix(bone):
        """Java model-space matrix of a part (part space -> model space), with the body undone."""
        return MS @ tr(o["body"] - C_OFF) @ inv_body @ W[bone] @ MS

    # Torso and its waist bend.
    A_t = java_matrix("torso")
    rel = np.linalg.inv(W["torso"]) @ W["chest"]
    r_rel = S_FLIP @ rel[:3, :3] @ S_FLIP
    ax, bend, tw = hinge_from_rotation(r_rel, prev_axes.get("torso", 0.0))
    prev_axes["torso"] = ax
    if abs(tw) > math.radians(3):
        warn.append(f"chest twist {math.degrees(tw):.0f} deg dropped (twist the torso bone instead)")
    rt = euler_zyx(A_t[:3, :3])
    out["torso"] = dict(x=A_t[0, 3], y=A_t[1, 3], z=A_t[2, 3], pitch=rt[0], yaw=rt[1], roll=rt[2], bend=bend, axis=ax)

    # The waist carries head and arms (JointRig.applyUpperBody): undo it for them.
    H_t = kinetrix_hinge(JOINTS["torso"], ax, bend)
    R_t = mat4(A_t[:3, :3])
    carry = tr(A_t[:3, 3]) @ R_t @ H_t @ R_t.T @ tr(-A_t[:3, 3])
    inv_carry = np.linalg.inv(carry)

    for part, (upper, lower, _pivot) in PARTS.items():
        if part == "torso":
            continue
        A = java_matrix(upper)
        if part in ("head", "rightArm", "leftArm"):
            A = inv_carry @ A
        r = euler_zyx(A[:3, :3])
        vals = dict(x=A[0, 3], y=A[1, 3], z=A[2, 3], pitch=r[0], yaw=r[1], roll=r[2])
        if lower:
            rel = np.linalg.inv(W[upper]) @ W[lower]
            r_rel = S_FLIP @ rel[:3, :3] @ S_FLIP
            ax, bend, tw = hinge_from_rotation(r_rel, prev_axes.get(part, 0.0))
            prev_axes[part] = ax
            if abs(tw) > math.radians(3):
                warn.append(f"{lower} twist {math.degrees(tw):.0f} deg dropped (a joint only folds)")
            vals.update(bend=bend, axis=ax)
        out[part] = vals

    # Held items: the turn of the item group in the fist, in the frame playerAnimator turns it
    # (HeldItemMixin: after ItemInHandLayer's hand transform, before the item's display transform).
    for side, item in (items or {}).items():
        if not item or item not in names:
            continue
        fr = item_frame(side, *ITEMS[item]["display"])
        rel = np.linalg.inv(W[side + "_forearm"]) @ W[item]
        o_f, o_a = o[side + "_forearm"], o[side + "_arm"]
        r_q = HAND_ROT.T @ S_FLIP @ rel[:3, :3] @ S_FLIP @ fr["r_d"].T
        pos = HAND_ROT.T @ (S_FLIP @ (o_f - o_a) + S_FLIP @ rel[:3, 3]) - r_q @ fr["trans"] - fr["h0"]
        e = euler_zyx(r_q)
        out[side + "Item"] = dict(x=pos[0], y=pos[1], z=pos[2], pitch=e[0], yaw=e[1], roll=e[2])
    return out, warn


def kinetrix_pose(values, items=None):
    """Blockbench-space world matrices of the rig's bones, rebuilt from playerAnimator values the
    way the game draws them (body transform, parts, waist carry, joints). Same keys as bb_pose,
    but each matrix maps the bone's pivot-relative REST coordinates."""
    rest = {b[0]: np.array(b[2]) for b in BONES}
    MS = mat4(S_FLIP)
    b = values["body"]
    k = 16.0 / MODEL_SCALE
    body = (tr(rest["body"] + np.array([b["x"], b["y"], b["z"]]) * k) @ mat4(rot_zyx(b["pitch"], b["yaw"], b["roll"]))
            @ tr(-rest["body"]))
    F = tr(C_OFF) @ MS  # Java model -> Blockbench

    def A_of(part):
        v = values[part]
        return tr([v["x"], v["y"], v["z"]]) @ mat4(rot_zyx(v["pitch"], v["yaw"], v["roll"]))

    t = values["torso"]
    A_t = A_of("torso")
    H_t = kinetrix_hinge(JOINTS["torso"], t["axis"], t["bend"])
    R_t = mat4(A_t[:3, :3])
    carry = tr(A_t[:3, 3]) @ R_t @ H_t @ R_t.T @ tr(-A_t[:3, 3])
    out = {}

    def bone_matrix(game, bone):
        # rest point b (pivot-relative r = b - origin) -> part space q = S(b - pivot_part_bb)
        # handled by composing: world = body F game F^-1, applied to absolute rest points.
        m = body @ F @ game @ np.linalg.inv(F)
        out[bone] = m @ tr(rest[bone])

    bone_matrix(np.eye(4), "body")
    for part, (upper, lower, pivot) in PARTS.items():
        A = A_of(part)
        base = tr(-np.array(pivot))  # model point at rest -> part space at rest
        if part in ("head", "rightArm", "leftArm"):
            A = carry @ A
        if part == "torso":
            bone_matrix(A @ base, "torso")
            bone_matrix(A @ H_t @ base, "chest")
            continue
        bone_matrix(A @ base, upper)
        if lower:
            v = values[part]
            H = kinetrix_hinge(JOINTS[part], v["axis"], v["bend"])
            bone_matrix(A @ H @ base, lower)
            side = "right" if part == "rightArm" else "left" if part == "leftArm" else None
            item = (items or {}).get(side)
            if item and (side + "Item") in values:
                fr = item_frame(side, *ITEMS[item]["display"])
                iv = values[side + "Item"]
                r_q = rot_zyx(iv["pitch"], iv["yaw"], iv["roll"])
                mq = mat4(HAND_ROT @ r_q @ fr["r_d"] @ S_FLIP,
                          HAND_ROT @ (np.array([iv["x"], iv["y"], iv["z"]]) + r_q @ fr["trans"] + fr["h0"]))
                out[item] = body @ F @ A @ H @ mq
    return out


CHECK_POINTS = {  # rest points (Blockbench) on each bone: ends of limbs, joints, head top
    "head": [(0, 32, 0), (0, 28, -4)], "chest": [(0, 24, 0), (4, 24, 2)], "torso": [(0, 12, 0)],
    "right_arm": [(6, 18, 0)], "right_forearm": [(6, 12, 0), (6, 12, -2)],
    "left_arm": [(-6, 18, 0)], "left_forearm": [(-6, 12, 0), (-6, 12, -2)],
    "right_leg": [(1.9, 6, 0)], "right_shin": [(1.9, 0, 0), (1.9, 0, -2)],
    "left_leg": [(-1.9, 6, 0)], "left_shin": [(-1.9, 0, 0), (-1.9, 0, -2)],
}


ITEM_CHECK = [(0, -8, 0), (0, 8, 0), (1, 0, 0)]  # item-local points: tip, handle, side


def pose_error(model, Wbb, Wk):
    worst, where = 0.0, ""
    for item in ITEMS:
        if item in Wbb and item in Wk:
            for p in ITEM_CHECK:
                d = float(np.linalg.norm(apply(Wbb[item], p) - apply(Wk[item], p)))
                if d > worst:
                    worst, where = d, item
    for bone, pts in CHECK_POINTS.items():
        o = model.groups[model.by_name[bone]]["origin"]
        for p in pts:
            a = apply(Wbb[bone], np.array(p) - o)
            b = apply(Wk[bone], np.array(p) - np.array(BONE[bone][2]))
            d = float(np.linalg.norm(a - b))
            if d > worst:
                worst, where = d, bone
    return worst, where


# ----------------------------------------------------------------------------------------------
# Convert
# ----------------------------------------------------------------------------------------------

def item_choice(model, touched):
    """Per hand, the item group whose turn in the fist is exported: an animated one, the shown one first."""
    out = {}
    for side in ("right", "left"):
        cands = [n for n in ITEMS if ITEMS[n]["side"] == side and n in touched and n in model.by_name]
        cands.sort(key=lambda n: not ITEMS[n]["visible"])
        out[side] = cands[0] if cands else None
    return out


def parts_in_use(model, chans):
    """The playerAnimator parts an animation drives, and the item group per hand. Only what the
    animation touches (directly or through a parent) is animated: the rest stays with Kinetrix and
    vanilla (the head keeps following the look unless the head is keyed)."""
    touched = set(chans)
    upper_body = bool(touched & {"torso", "chest"})
    used = {"body"} if "body" in touched else set()
    for part, (upper, lower, _p) in PARTS.items():
        if touched & {upper, lower} or (part in ("torso", "rightArm", "leftArm") and upper_body):
            used.add(part)
    items = item_choice(model, touched)
    for side, item in items.items():
        if item:
            used.add(side + "Item")
    return used, items


def as_in_game(values, used):
    """The values the game ends up with: parts the animation leaves alone stand as in vanilla
    (straight, the head looking ahead), the waist still carries head and arms."""
    out = dict(values)
    for part, (_upper, _lower, pivot) in PARTS.items():
        if part not in used:
            out[part] = dict(x=pivot[0], y=pivot[1], z=pivot[2], pitch=0.0, yaw=0.0, roll=0.0, bend=0.0, axis=0.0)
    if "body" not in used:
        out["body"] = dict(x=0.0, y=0.0, z=0.0, pitch=0.0, yaw=0.0, roll=0.0)
    return out


def anim_name(anim):
    """File name of an animation: "animation.slide" (Bedrock style, as Blockbench MCP names them) -> "slide"."""
    return anim.get("name", "animation").split(".")[-1].lower().replace(" ", "_")


def held_items(model, items):
    """What the animation is meant to be held with, per hand: the animated item, else the one shown in Blockbench."""
    out = {}
    for side in ("right", "left"):
        name = items.get(side) or next((n for n in ITEMS if ITEMS[n]["side"] == side and n in model.by_name
                                        and model.groups[model.by_name[n]]["visible"]), None)
        if name:
            out[side] = ITEMS[name]["shape"]
    return out


def emote_json(name, frames, length_ticks, loop, author, loop_from=None):
    """loop_from: seconds; a held or looped animation with a timeline marker plays to the end once,
    then repeats from the marker (a stance that breathes, a volley that keeps firing)."""
    moves = []
    for tick, values in frames:
        for part, v in values.items():
            entry = {k: round(float(x), 5) for k, x in v.items()}
            moves.append({"tick": tick, "easing": "LINEAR", "turn": 0, part: entry})
    emote = {"beginTick": 0, "endTick": max(1, length_ticks), "stopTick": max(1, length_ticks) + 3,
             "isLoop": loop == "loop", "returnTick": 0, "degrees": False, "moves": moves}
    if loop == "hold":
        emote["stopTick"] = 2 ** 31 - 1
    if loop_from is not None and loop in ("hold", "loop"):
        emote["isLoop"] = True
        emote["returnTick"] = min(max(0, int(round(loop_from * 20))), max(1, length_ticks) - 1)
    return {"name": name, "author": author, "description": "Made in Blockbench on the Kinetrix rig", "emote": emote}


def convert(path, out_dir, namespace=None, quiet=False):
    model = Model(path)
    os.makedirs(out_dir, exist_ok=True)
    results = []
    for anim in model.animations:
        chans = model.channels(anim)
        name = anim_name(anim)
        length = float(anim.get("length", 0) or 0)
        if length <= 0:
            length = max([k[0] for c in chans.values() for lst in c.values() for k in lst] + [0.05])
        ticks = int(round(length * 20))
        parts_used, items = parts_in_use(model, chans)
        frames, worst, worst_at, warns = [], 0.0, "", set()
        prev_axes, prev_rot = {}, {}
        for tick in range(ticks + 1):
            t = tick / 20.0
            vals, w = convert_frame(model, chans, t, prev_axes, items)
            for part, v in vals.items():
                if "pitch" in v:
                    v["pitch"], v["yaw"], v["roll"] = prev_rot[part] = continue_angles(
                        prev_rot.get(part), (v["pitch"], v["yaw"], v["roll"]))
            warns.update(w)
            keep = {p: v for p, v in vals.items() if p in parts_used}
            frames.append((tick, keep))
            err, where = pose_error(model, bb_pose(model, chans, t), kinetrix_pose(vals, items))
            if err > worst:
                worst, worst_at = err, f"{where} at tick {tick}"
        markers = sorted(float(mk.get("time", 0)) for mk in anim.get("markers") or [])
        data = emote_json(name, frames, ticks, anim.get("loop", "once"), "Blockbench", markers[0] if markers else None)
        data["kinetrix_items"] = held_items(model, items)  # not read by playerAnimator; the self-test hands these out
        fn = os.path.join(out_dir, name + ".json")
        with open(fn, "w") as f:
            json.dump(data, f, indent=1)
        results.append((name, fn, ticks, sorted(parts_used), worst, worst_at, sorted(warns)))
        if not quiet:
            print(f"{name}: {ticks} ticks, parts {', '.join(results[-1][3])} -> {fn}")
            print(f"   largest difference from the Blockbench pose: {worst:.2f} px ({worst_at})")
            for w in sorted(warns):
                print("   note:", w)
        if namespace and not quiet:
            print(f"   playerAnimator id: {namespace}:{name}")
    return results


# ----------------------------------------------------------------------------------------------
# Preview pictures
# ----------------------------------------------------------------------------------------------

def texture_image(model, idx):
    from PIL import Image
    t = model.data["textures"][idx]
    src = t.get("source", "")
    if not src.startswith("data:image"):
        return None
    return Image.open(io.BytesIO(base64.b64decode(src.split(",", 1)[1]))).convert("RGBA")


def face_color(img, uv):
    if img is None:
        return (180, 180, 180, 255)
    u1, v1, u2, v2 = uv
    xs = range(int(min(u1, u2)), max(int(min(u1, u2)) + 1, int(math.ceil(max(u1, u2)))))
    ys = range(int(min(v1, v2)), max(int(min(v1, v2)) + 1, int(math.ceil(max(v1, v2)))))
    acc, n = [0, 0, 0, 0], 0
    for y in ys:
        for x in xs:
            if 0 <= x < img.width and 0 <= y < img.height:
                p = img.getpixel((x, y))
                for i in range(4):
                    acc[i] += p[i]
                n += 1
    if n == 0:
        return (0, 0, 0, 0)
    return tuple(int(a / n) for a in acc)


FACE_CORNERS = {
    "north": [(0, 0, 0), (1, 0, 0), (1, 1, 0), (0, 1, 0)], "south": [(0, 0, 1), (1, 0, 1), (1, 1, 1), (0, 1, 1)],
    "west": [(0, 0, 0), (0, 1, 0), (0, 1, 1), (0, 0, 1)], "east": [(1, 0, 0), (1, 1, 0), (1, 1, 1), (1, 0, 1)],
    "down": [(0, 0, 0), (1, 0, 0), (1, 0, 1), (0, 0, 1)], "up": [(0, 1, 0), (1, 1, 0), (1, 1, 1), (0, 1, 1)],
}


def render(model, worlds, size=300, views=("front", "side", "three_quarter"), show=()):
    """Pictures of a posed rig; worlds: bone name -> matrix (pivot-relative rest coords -> world).
    show: hidden groups to draw anyway (items an animation uses)."""
    from PIL import Image, ImageDraw
    texs = {i: texture_image(model, i) for i in range(len(model.data.get("textures", [])))}
    polys = []
    owner = {}
    for u, g in model.groups.items():
        for c in g["children"]:
            owner[c] = g["name"]
    for e in model.data["elements"]:
        bone = owner.get(e["uuid"])
        if e.get("visibility") is False and bone not in show:
            continue
        if bone is None or bone not in worlds or not (bone in BONE or bone in ITEMS):
            continue  # an imported opponent (enemy_*) is not part of the player's pose
        o = model.groups[model.by_name[bone]]["origin"]
        infl = e.get("inflate", 0.0)
        lo = np.array(e["from"], float) - infl
        hi = np.array(e["to"], float) + infl
        # the cube's own rotation (props)
        crot = np.radians(np.array(e.get("rotation", [0, 0, 0]), float))
        corig = np.array(e.get("origin", o), float)
        for fname, corners in FACE_CORNERS.items():
            face = e["faces"].get(fname)
            if not face or face.get("texture") is None:
                continue
            col = face_color(texs.get(face["texture"]), face["uv"])
            if col[3] < 40:
                continue
            pts = []
            for c in corners:
                p = lo + (hi - lo) * np.array(c, float)
                p = corig + rot_zyx(*crot) @ (p - corig)
                pts.append(apply(worlds[bone], p - o))
            polys.append((pts, col))
    images = []
    for view in views:
        img = Image.new("RGB", (size, size), (238, 238, 232))
        d = ImageDraw.Draw(img)
        if view == "front":
            cam = lambda p: (-p[0], p[1], p[2])
        elif view == "side":
            cam = lambda p: (-p[2], p[1], -p[0])
        else:
            a = math.radians(35)
            cam = lambda p: (-(p[0] * math.cos(a) + p[2] * math.sin(a)), p[1], -p[0] * math.sin(a) + p[2] * math.cos(a))
        scale = size / 44.0
        ox, oy = size / 2, size * 0.88

        def scr(p):
            q = cam(p)
            return (ox + q[0] * scale, oy - q[1] * scale), q[2]

        d.line([(0, oy), (size, oy)], fill=(170, 170, 160), width=1)
        drawn = []
        for pts, col in polys:
            sp = [scr(p) for p in pts]
            depth = sum(s[1] for s in sp) / 4
            xy = [s[0] for s in sp]
            v1 = np.array(cam(pts[1])) - np.array(cam(pts[0]))
            v2 = np.array(cam(pts[3])) - np.array(cam(pts[0]))
            n = np.cross(v1, v2)
            nn = np.linalg.norm(n)
            light = 0.55 + 0.45 * abs(n[2] / nn) if nn > 1e-9 else 0.7
            drawn.append((depth, xy, tuple(int(c * light) for c in col[:3])))
        for depth, xy, col in sorted(drawn, key=lambda x: -x[0]):
            d.polygon(xy, fill=col, outline=tuple(max(0, c - 40) for c in col))
        images.append(img)
    return images


def preview(path, out_dir, frames=5):
    from PIL import Image, ImageDraw
    model = Model(path)
    os.makedirs(out_dir, exist_ok=True)
    written = []
    for anim in model.animations:
        chans = model.channels(anim)
        name = anim_name(anim)
        length = float(anim.get("length", 0) or 0) or 1.0
        times = [length * i / (frames - 1) for i in range(frames)]
        rows = []
        prev = {}
        used, items = parts_in_use(model, chans)
        for t in times:
            vals, _ = convert_frame(model, chans, t, prev, items)
            shown = [i for i in items.values() if i]
            a = render(model, bb_pose(model, chans, t), size=220, views=("front", "side", "three_quarter"), show=shown)
            b = render(model, kinetrix_pose(as_in_game(vals, used), items), size=220, views=("three_quarter",), show=shown)
            rows.append((t, a + b))
        w, h = 220 * 4, 220 * len(rows) + 30
        sheet = Image.new("RGB", (w, h), (255, 255, 255))
        d = ImageDraw.Draw(sheet)
        d.text((6, 8), f"{name}: Blockbench front / side / 3-4  |  as Kinetrix draws it (3-4)", fill=(0, 0, 0))
        for i, (t, imgs) in enumerate(rows):
            for j, im in enumerate(imgs):
                sheet.paste(im, (j * 220, 30 + i * 220))
            d.text((4, 32 + i * 220), f"t={t:.2f}s", fill=(0, 0, 0))
        fn = os.path.join(out_dir, name + ".png")
        sheet.save(fn)
        written.append(fn)
        print("wrote", fn)
    return written


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    m = sub.add_parser("make")
    m.add_argument("out")
    o = sub.add_parser("make-opponent")
    o.add_argument("out")
    o.add_argument("--distance", type=float, default=32.0, help="pixels in front of the player (16 = one block)")
    c = sub.add_parser("convert")
    c.add_argument("bbmodel")
    c.add_argument("out_dir")
    c.add_argument("--namespace")
    p = sub.add_parser("preview")
    p.add_argument("bbmodel")
    p.add_argument("out_dir")
    p.add_argument("--frames", type=int, default=5)
    a = ap.parse_args(argv)
    if a.cmd == "make":
        make(a.out)
    elif a.cmd == "make-opponent":
        make_opponent(a.out, a.distance)
    elif a.cmd == "convert":
        convert(a.bbmodel, a.out_dir, a.namespace)
    else:
        preview(a.bbmodel, a.out_dir, a.frames)


if __name__ == "__main__":
    main()
