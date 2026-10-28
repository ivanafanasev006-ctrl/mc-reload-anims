"""Pose library: the author's bow_barrage poses (grounded), release poses, flow and strike poses."""
import functools

import numpy as np

import ik
import userpose

ARMS = ('right_arm', 'right_forearm', 'left_arm', 'left_forearm', 'right_arm@p', 'left_arm@p')
LEGS = ('right_leg', 'right_shin', 'left_leg', 'left_shin')


def pick(pose, keys):
    return {k: pose[k] for k in keys if k in pose}


def drop(pose, keys):
    return {k: v for k, v in pose.items() if k not in keys}


def rnd(pose):
    return {k: tuple(round(float(x), 2) for x in v) for k, v in pose.items()}


def ground(pose, contacts, vary):
    out, err = ik.solve(pose, [(c, 1, 0.0, 1.0) for c in contacts], vary, reg=0.02)
    return rnd(out)


LEG_VARY = [('body', 1, 'p'), ('left_leg', 0, 'r'), ('left_shin', 0, 'r'), ('right_leg', 0, 'r'), ('right_shin', 0, 'r')]
LARM = [('left_arm', 0, 'r'), ('left_arm', 1, 'r'), ('left_arm', 2, 'r'), ('left_forearm', 2, 'r')]


def release_set(drawn, flick_off=(-3.5, 0.8, 5.0), nock_off=(1.0, -0.5, -4.5)):
    """From a drawn pose: the hand thrown back past the jaw (flick), a little further (follow),
    and the hand reaching to the bow for the next arrow (nock)."""
    W = ik.fk(drawn)
    lh = ik.pt(W, 'lhand')
    Rh = W['head'][:3, :3]

    def reach(start, target):
        # a deep elbow fold does not carry over well to the game: past 75 deg the shoulder does the rest
        p, _ = ik.solve(start, [('lhand', target, 1)], LARM, reg=0.01)
        if p['left_forearm'][2] > 75:
            p['left_forearm'] = (0, 0, 75)
            p, _ = ik.solve(p, [('lhand', target, 1)], LARM[:3], reg=0.01)
        return p
    flick = reach(drawn, lh + Rh @ np.array(flick_off))
    follow = reach(flick, lh + Rh @ (np.array(flick_off) * 1.18 + np.array([0, -0.6, 0])))
    nock, _ = ik.solve(drawn, [('lhand', lh + Rh @ np.array(nock_off), 1)], LARM, reg=0.01)
    lf = nock['left_forearm']
    nock['left_forearm'] = (lf[0], lf[1], max(lf[2], 4.0))
    return rnd(flick), rnd(follow), rnd(nock)


def bow_recoil(drawn, amount=1.0):
    """Bow arm after the release: the bow dips and the arm gives forward a little."""
    ra = drawn['right_arm']
    rf = drawn.get('right_forearm', (0, 0, 0))
    return {'right_arm': (ra[0] - 7 * amount, ra[1], ra[2]), 'right_forearm': (rf[0], rf[1], rf[2] * (1 - 0.35 * amount))}


@functools.lru_cache(None)
def build():
    U = userpose.at
    P = {}

    # --- the author's four poses, with feet and knees put on the ground ---------------------------
    # Kneel: arms from 0.5 s (aim), legs settled (0.75 s).
    k = U(0.8)
    k.update(pick(U(0.5), ('head', 'chest', 'right_arm', 'left_arm')))
    k['right_forearm'] = (0, 0, -27.5)
    k['left_forearm'] = (0, 0, 50)
    k['body'] = (0, 0, 0)
    k['body@p'] = (0, -8, 0)
    k = ground(k, ['lknee', 'rfoot', 'rtoe'], LEG_VARY)
    P['kneel'] = k

    s = U(1.65)
    s['left_forearm'] = (0, 0, 55)
    s['body'] = (0, 87.5, 0)
    s['body@p'] = (0, -2.5, 0)
    s = ground(s, ['lfoot', 'rfoot'], LEG_VARY)
    P['stand'] = s

    c = U(2.3)
    c['left_forearm'] = (0, 0, 50)
    c['body'] = (0, 77.5, 0)
    c['body@p'] = (0, -6, 0)
    c = ground(c, ['lfoot', 'rfoot'], LEG_VARY)
    P['crouch'] = c

    lo = U(2.875)
    lo['left_forearm'] = (0, 0, 50)
    lo['body'] = (15, 0, 0)
    lo['body@p'] = (0, -10, 0)
    lo = ground(lo, ['lfoot', 'rfoot', 'ltoe', 'rtoe'], LEG_VARY)
    P['low'] = lo

    # Side kneel for the left roll: legs kneeling with the right foot toward the target, arms and
    # head as in the standing side aim.
    sk = dict(s)
    sk.update({'right_leg': (62, -38, 6), 'right_shin': (-68, 0, 0), 'left_leg': (-22, 8, -10), 'left_shin': (-96, 0, 0),
               'body@p': (0, -7.5, 0)})
    sk = ground(sk, ['lknee', 'rfoot', 'rtoe'], LEG_VARY)
    P['side_kneel'] = sk

    # Wider, lower stance for landing from the back step.
    sl = dict(s)
    sl.update({'right_leg': (22, -19.4, 12), 'right_shin': (-30, 0, 0), 'left_leg': (-14, 7.4, -9), 'left_shin': (-26, 0, 0),
               'body@p': (0, -2.5, 0), 'chest': (-6, 0, 0)})
    sl = ground(sl, ['lfoot', 'rfoot'], LEG_VARY)
    P['stand_low'] = sl

    for name in ('kneel', 'stand', 'crouch', 'low', 'side_kneel', 'stand_low'):
        flick, follow, nock = release_set(P[name])
        P[name + '_flick'] = flick
        P[name + '_follow'] = follow
        P[name + '_nock'] = nock
    return P


def get(name):
    return {k: tuple(v) for k, v in build()[name].items()}
