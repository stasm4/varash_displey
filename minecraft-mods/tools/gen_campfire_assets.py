#!/usr/bin/env python3
"""Generates every texture, model, blockstate and particle definition of the
Enhanced Campfire mod.  Run from anywhere:  python3 tools/gen_campfire_assets.py

All output is deterministic.  The geometry of the three log layouts lives here
(LAYOUTS); the flame emitter positions used by the Java renderer are derived
from the same layout description and printed at the end so they stay in sync
with CampfireLayouts.java.
"""
import math
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
from pixelart import (fbm, hex_rgb, palette_map, rng, save, to_image, value_noise, write_json)  # noqa: E402

MODID = 'enhancedcampfire'
ROOT = os.path.join(os.path.dirname(__file__), '..', 'EnhancedCampfire', 'src', 'main', 'resources')
ASSETS = os.path.join(ROOT, 'assets', MODID)
DATA = os.path.join(ROOT, 'data')
TEX = os.path.join(ASSETS, 'textures')


# ---------------------------------------------------------------------------------------------
# Block textures
# ---------------------------------------------------------------------------------------------

BARK = ['#2c1e13', '#3e2b1a', '#523921', '#66482a', '#795733', '#8c673d']
CHAR = ['#0b0908', '#141110', '#1d1815', '#28211b', '#342a21', '#433629']
WOOD = ['#5e4024', '#7a5631', '#94693b', '#ad7f48', '#c29457', '#d4a868']
ASH = ['#332f2c', '#433e3a', '#55504b', '#68625c', '#7c756e', '#918a82']
STONE = ['#2d2c2b', '#3c3b39', '#4c4a48', '#5e5c59', '#72706c', '#86837e']
EMBER = ['#1a0e0b', '#3a110a', '#651a0b', '#97290b', '#c9460f', '#ea7a1d', '#ffb347', '#ffe08a']


def bark_texture(seed, palette, fissure_color=None):
    """Bark with fibres running horizontally (along U) - logs map their length onto U."""
    w = h = 16
    r = rng(seed)
    rows = value_noise(1, h, 2, seed)[:, :1]  # per-row base value
    streak = fbm(w, h, seed + 7, base_cell=8, octaves=2)
    fine = r.random((h, w)) * 0.25
    vals = 0.55 * rows + 0.30 * streak + fine
    vals = (vals - vals.min()) / (vals.max() - vals.min() + 1e-9)
    rgb = palette_map(vals, palette)
    # deep fissures: broken horizontal dark lines
    for y in range(0, h, 3):
        yy = (y + r.integers(0, 2)) % h
        x = r.integers(0, w)
        length = r.integers(5, 13)
        for i in range(length):
            xx = (x + i) % w
            jitter = (yy + (1 if r.random() < 0.18 else 0)) % h
            rgb[jitter, xx] = hex_rgb(palette[0])
    # bright plate edges
    for _ in range(14):
        x, y = r.integers(0, w), r.integers(0, h)
        rgb[y, x] = hex_rgb(palette[-1])
    if fissure_color is not None:
        for _ in range(10):
            x, y = r.integers(0, w), r.integers(0, h)
            rgb[y, x] = hex_rgb(fissure_color)
    return to_image(rgb)


def charred_bark_texture(seed):
    img = np.array(bark_texture(seed, CHAR, fissure_color='#5d5650'))[:, :, :3].astype(float)
    r = rng(seed + 1)
    # alligator cracking: small light-grey ash ridges in a grid-like pattern
    for y in range(1, 16, 4):
        for x in range(0, 16):
            if r.random() < 0.35:
                img[(y + (x // 5)) % 16, x] = hex_rgb('#4a433d')
    return to_image(img)


def end_grain_cells(seed, wood, bark, pith):
    """16 small 4x4 log-end caps (UV cells).  Each cap: bark rim, growth rings, darker pith."""
    out = np.zeros((16, 16, 3))
    r = rng(seed)
    for cy in range(4):
        for cx in range(4):
            ox, oy = r.random() * 0.6 - 0.3, r.random() * 0.6 - 0.3
            for y in range(4):
                for x in range(4):
                    dx, dy = x - 1.5 + ox, y - 1.5 + oy
                    d = math.hypot(dx, dy)
                    if d > 1.75 or x in (0, 3) and y in (0, 3):
                        col = bark[r.integers(0, 2)]
                    elif d < 0.6:
                        col = pith
                    else:
                        ring = int(d * 2.2 + r.random() * 0.8) % 2
                        col = wood[2 + ring + r.integers(0, 2)]
                    out[cy * 4 + y, cx * 4 + x] = hex_rgb(col)
    return to_image(out)


def ember_frames(seed, frames=8):
    """Animated glowing ember bed (vertical strip of 16x16 frames)."""
    base = fbm(16, 16, seed, base_cell=4, octaves=3)
    cells = value_noise(16, 16, 3, seed + 3)
    phase = rng(seed + 5).random((16, 16)) * math.tau
    strip = []
    for f in range(frames):
        t = f / frames * math.tau
        heat = 0.55 * base + 0.25 * cells + 0.20 * (0.5 + 0.5 * np.sin(phase + t))
        heat = (heat - 0.18) / 0.8
        # dark charcoal network between the hot cells
        crack = value_noise(16, 16, 2, seed + 11) > 0.72
        heat[crack] *= 0.35
        strip.append(palette_map(np.clip(heat, 0, 0.999), EMBER))
    return to_image(np.vstack(strip))


def charcoal_glow_frames(seed, frames=8):
    """Charred wood with glowing cracks; used on burning log tips (lit model only)."""
    r = rng(seed)
    base = palette_map(fbm(16, 16, seed, 4, 2) * 0.99, CHAR)
    cracks = np.zeros((16, 16))
    for _ in range(9):
        x, y = r.integers(0, 16), r.integers(0, 16)
        for _ in range(r.integers(3, 8)):
            cracks[y % 16, x % 16] = 0.6 + r.random() * 0.4
            if r.random() < 0.5:
                x += 1
            else:
                y += 1 if r.random() < 0.5 else -1
    phase = r.random((16, 16)) * math.tau
    strip = []
    for f in range(frames):
        t = f / frames * math.tau
        glow = cracks * (0.65 + 0.35 * np.sin(phase + t))
        col = palette_map(np.clip(glow, 0, 0.999), EMBER[2:])
        frame = np.where((cracks > 0)[:, :, None], col, base)
        strip.append(frame)
    return to_image(np.vstack(strip))


def simple_texture(seed, palette, cell=4, speck=None, speck_chance=0.08):
    vals = fbm(16, 16, seed, base_cell=cell, octaves=3)
    vals = (vals - vals.min()) / (vals.max() - vals.min() + 1e-9) * 0.999
    rgb = palette_map(vals, palette)
    if speck:
        r = rng(seed + 9)
        mask = r.random((16, 16)) < speck_chance
        rgb[mask] = hex_rgb(speck)
    return to_image(rgb)


def stone_texture(seed):
    vals = fbm(16, 16, seed, base_cell=4, octaves=3)
    # rounded cobbles: darken borders of a coarse cell pattern
    cell = value_noise(16, 16, 5, seed + 2)
    edge = np.abs(np.gradient(cell)[0]) + np.abs(np.gradient(cell)[1])
    vals = vals - edge * 2.2
    # soot stains towards the top rows (stones face the fire)
    vals -= np.linspace(0.25, 0.0, 16)[:, None]
    vals = (vals - vals.min()) / (vals.max() - vals.min() + 1e-9) * 0.999
    return to_image(palette_map(vals, STONE))


def write_block_textures():
    b = os.path.join(TEX, 'block')
    save(bark_texture(11, BARK), os.path.join(b, 'log_bark.png'))
    save(bark_texture(23, ['#2f2318', '#433222', '#56412c', '#6a5236', '#7d6241', '#90734d']), os.path.join(b, 'log_bark_alt.png'))
    save(charred_bark_texture(31), os.path.join(b, 'log_charred.png'))
    save(end_grain_cells(41, WOOD, BARK, '#4a321b'), os.path.join(b, 'log_end.png'))
    save(end_grain_cells(43, CHAR[2:] + ['#3e352d', '#4a4038'], CHAR, '#090706'), os.path.join(b, 'log_end_charred.png'))
    save(ember_frames(51), os.path.join(b, 'embers.png'))
    write_json(os.path.join(b, 'embers.png.mcmeta'), {'animation': {'frametime': 5, 'interpolate': True}})
    save(charcoal_glow_frames(61), os.path.join(b, 'charcoal_glow.png'))
    write_json(os.path.join(b, 'charcoal_glow.png.mcmeta'), {'animation': {'frametime': 6, 'interpolate': True}})
    save(simple_texture(71, ['#100e0d', '#1a1715', '#24201d', '#2f2a26', '#3b3530', '#4b443e'], 3, '#6f6861', 0.06),
         os.path.join(b, 'embers_off.png'))
    save(simple_texture(81, ASH, 3, '#1f1c1a', 0.10), os.path.join(b, 'ash.png'))
    save(stone_texture(91), os.path.join(b, 'fire_stone.png'))


# ---------------------------------------------------------------------------------------------
# Flame sheets (non-atlas textures used by the block entity renderer)
# ---------------------------------------------------------------------------------------------

class Noise2:
    """Continuous 2D value noise with a hashed lattice (evaluable at arbitrary coordinates)."""

    def __init__(self, seed):
        self.perm = rng(seed).permutation(512).astype(np.int64)
        self.vals = rng(seed + 1).random(512)

    def __call__(self, x, y):
        xi = np.floor(x).astype(np.int64)
        yi = np.floor(y).astype(np.int64)
        fx, fy = x - xi, y - yi
        fx = fx * fx * (3 - 2 * fx)
        fy = fy * fy * (3 - 2 * fy)

        def v(ix, iy):
            return self.vals[self.perm[(self.perm[ix & 511] + iy) & 511]]

        a, b = v(xi, yi), v(xi + 1, yi)
        c, d = v(xi, yi + 1), v(xi + 1, yi + 1)
        return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fy

    def fbm(self, x, y, octaves=4):
        total, amp, norm, f = 0.0, 1.0, 0.0, 1.0
        for o in range(octaves):
            total = total + amp * self(x * f + o * 17.3, y * f - o * 9.1)
            norm += amp
            amp *= 0.5
            f *= 2.03
        return total / norm


FIRE_RAMP = [
    (0.00, (0, 0, 0)),
    (0.18, (60, 6, 0)),
    (0.35, (150, 28, 4)),
    (0.55, (225, 82, 10)),
    (0.72, (255, 140, 30)),
    (0.86, (255, 200, 85)),
    (1.00, (255, 244, 200)),
]


def fire_color(i):
    out = np.zeros(i.shape + (3,))
    for (a, ca), (b, cb) in zip(FIRE_RAMP, FIRE_RAMP[1:]):
        m = (i >= a) & (i <= b)
        t = ((i - a) / (b - a))[m][:, None]
        out[m] = np.array(ca) + (np.array(cb) - np.array(ca)) * t
    return out


def flame_field(t, n_main, n_side, fw=32, fh=64, tongues=None):
    """Intensity field [0,1] of one flame frame at loop phase t in [0,1).

    Several tongues of different height share a wide glowing base; each tongue sways on its own, turbulence
    eats holes into the body and tears small licks off the tips.  All time dependence is periodic in t, so the
    sheet loops (and the renderer additionally desynchronises tongues and blends frames).
    """
    ys, xs = np.mgrid[0:fh, 0:fw].astype(np.float64)
    u = (xs + 0.5) / fw * 2 - 1           # -1..1
    v = 1 - (ys + 0.5) / fh               # 0 bottom .. 1 top
    ang = t * 2 * np.pi
    speed = 1.6

    def scrolled(fn, x, y, scale_y):
        a = fn(x, y * scale_y - t * speed)
        b = fn(x, y * scale_y - (t - 1) * speed)
        return a * (1 - t) + b * t

    if tongues is None:
        # (centre u, base height, width, phase)
        tongues = [(0.0, 0.95, 0.46, 0.0), (-0.33, 0.68, 0.34, 1.7), (0.31, 0.76, 0.34, 3.9),
                   (-0.15, 0.55, 0.28, 5.1), (0.17, 0.6, 0.28, 2.6)]
    field = np.zeros_like(u)
    for i, (cu, h, w, ph) in enumerate(tongues):
        # periodic height pulse and sway for this tongue
        hh = h * (0.82 + 0.12 * np.sin(ang + ph) + 0.06 * np.sin(2 * ang + ph * 1.7))
        sway = (n_side.fbm(np.full_like(v, 3.1 * i + np.cos(ang + ph) * 0.8),
                           v * 1.6 + np.sin(ang + ph) * 0.8, 3) - 0.5) * 0.9 * v ** 1.2
        vv = v / max(hh, 1e-3)
        width = w * np.sqrt(np.clip(vv * 4.0, 0, 1)) * np.clip(1 - vv, 0, 1) ** 0.7
        body = 1 - np.abs(u - cu - sway) / np.maximum(width, 1e-3)
        body = np.where(vv < 1, body, -1)
        field = np.maximum(field, body)
    # wide glowing base the tongues grow out of
    base = (1 - np.abs(u) / 0.7) * np.clip(1 - v / 0.22, 0, 1)
    field = np.maximum(field, base)
    turb = scrolled(lambda x, y: n_main.fbm(x, y, 4), u * 2.6, v, 2.8)
    shape = field + (turb - 0.5) * (0.5 + 1.5 * v)
    inten = np.clip(shape * 1.7, 0, 1) ** 1.1
    inten *= np.clip(1.15 - v ** 2.0, 0, 1)
    inten *= np.clip((field + 0.3) / 0.3, 0, 1)        # no stray noise far away from any tongue
    inten *= np.clip((1 - np.abs(u)) / 0.18, 0, 1)     # never touch the frame edges
    inten *= np.clip((0.98 - v) / 0.2, 0, 1)            # fade out before the top of the frame
    inten = np.where(inten < 0.14, 0, inten)            # drop faint stray specks
    # hot core near the base
    core = np.clip(1 - np.abs(u) / 0.32, 0, 1) * np.clip(1 - v * 3.0, 0, 1)
    inten = np.clip(inten * 0.88 + core * 0.28 * (inten > 0.05), 0, 1)
    return inten


def write_flame_sheets():
    frames, cols, fw, fh = 16, 4, 32, 64
    n_main, n_side = Noise2(1234), Noise2(987)
    add = np.zeros((fh * 4, fw * cols, 3))
    alpha = np.zeros((fh * 4, fw * cols))
    for f in range(frames):
        inten = flame_field(f / frames, n_main, n_side, fw, fh)
        # pixel-art quantisation to 10 levels keeps a Minecraft look
        q = np.floor(inten * 10) / 10
        cx, cy = (f % cols) * fw, (f // cols) * fh
        add[cy:cy + fh, cx:cx + fw] = fire_color(q)
        alpha[cy:cy + fh, cx:cx + fw] = np.clip(q * 1.35, 0, 1) * 255
    e = os.path.join(TEX, 'effect')
    save(to_image(add, np.full(alpha.shape, 255)), os.path.join(e, 'flame_sheet_add.png'))
    white = np.full(add.shape, 255.0)
    save(to_image(white, alpha), os.path.join(e, 'flame_sheet.png'))
    # radial glow (ground light pool / ember glow), RGB intensity for additive blending
    s = 64
    ys, xs = np.mgrid[0:s, 0:s]
    d = np.hypot(xs - s / 2 + 0.5, ys - s / 2 + 0.5) / (s / 2)
    g = np.clip(1 - d, 0, 1) ** 2.2
    g = np.floor(g * 24) / 24
    save(to_image(np.dstack([g, g, g]) * 255, np.full(g.shape, 255)), os.path.join(e, 'glow_radial.png'))
    return add


# ---------------------------------------------------------------------------------------------
# Particle textures
# ---------------------------------------------------------------------------------------------

def write_particles():
    p = os.path.join(TEX, 'particle')
    r = rng(5)
    # sparks: white so the particle colour can tint them
    for i, (core, glow) in enumerate([(1, 1.2), (1, 2.0)]):
        a = np.zeros((8, 8))
        for y in range(8):
            for x in range(8):
                d = math.hypot(x - 3.5, y - 3.5)
                a[y, x] = 255 if d < core else max(0, 160 * (1 - (d - core) / glow)) if d < core + glow else 0
        save(to_image(np.full((8, 8, 3), 255.0), a), os.path.join(p, f'spark_{i}.png'))
    a = np.zeros((8, 8))
    for y in range(8):
        for x in range(8):
            d = math.hypot(x - 3.5, y - 3.5)
            a[y, x] = 255 if d < 1.6 else max(0, 190 * (1 - (d - 1.6) / 2.4)) if d < 4 else 0
    save(to_image(np.full((8, 8, 3), 255.0), a), os.path.join(p, 'spark_big.png'))
    # ash flakes: irregular grey bits
    for i in range(3):
        a = np.zeros((8, 8))
        rgb = np.zeros((8, 8, 3))
        x, y = 3, 3
        for _ in range(4 + i):
            a[y, x] = 255
            rgb[y, x] = [200 + r.integers(0, 55)] * 3
            x = int(np.clip(x + r.integers(-1, 2), 1, 6))
            y = int(np.clip(y + r.integers(-1, 2), 1, 6))
        save(to_image(rgb, a), os.path.join(p, f'ash_{i}.png'))
    # smoke puffs: soft lumpy clouds, 32x32, white (tinted in code)
    for i in range(8):
        n = fbm(32, 32, 300 + i, base_cell=8, octaves=4, tile=False)
        ys, xs = np.mgrid[0:32, 0:32]
        rr = rng(400 + i)
        a = np.zeros((32, 32))
        for _ in range(4):  # a few overlapping lobes
            cx, cy = 16 + rr.normal(0, 2.6), 16 + rr.normal(0, 2.6)
            rad = 10.5 + rr.random() * 4.0
            d = np.hypot(xs - cx + 0.5, ys - cy + 0.5) / rad
            a = np.maximum(a, np.clip(1 - d, 0, 1) ** 1.4)
        a = np.clip(a * (0.65 + 0.7 * n) - 0.05, 0, 1)
        a = np.floor(a * 24) / 24 * 210
        shade_ = 205 + n * 50
        save(to_image(np.dstack([shade_] * 3), a), os.path.join(p, f'smoke_{i}.png'))
    # small flame licks (6 frames, coloured)
    n_main, n_side = Noise2(77), Noise2(78)
    for i in range(6):
        inten = flame_field(i / 6, n_main, n_side, 16, 32)
        inten = np.array(Image.fromarray((inten * 255).astype(np.uint8)).resize((16, 16), Image.BILINEAR)) / 255
        inten = np.pad(inten[:, :], ((0, 0), (0, 0)))
        col = fire_color(np.floor(inten * 8) / 8)
        save(to_image(col, np.clip(inten * 1.8, 0, 1) * 255), os.path.join(p, f'flame_{i}.png'))

    pj = os.path.join(ASSETS, 'particles')
    write_json(os.path.join(pj, 'spark.json'), {'textures': [f'{MODID}:spark_0', f'{MODID}:spark_1']})
    write_json(os.path.join(pj, 'big_spark.json'), {'textures': [f'{MODID}:spark_big']})
    write_json(os.path.join(pj, 'ash.json'), {'textures': [f'{MODID}:ash_{i}' for i in range(3)]})
    write_json(os.path.join(pj, 'fire_smoke.json'), {'textures': [f'{MODID}:smoke_{i}' for i in range(8)]})
    write_json(os.path.join(pj, 'steam.json'), {'textures': [f'{MODID}:smoke_{i}' for i in range(8)]})
    write_json(os.path.join(pj, 'flame_lick.json'), {'textures': [f'{MODID}:flame_{i}' for i in range(6)]})


# ---------------------------------------------------------------------------------------------
# Models
# ---------------------------------------------------------------------------------------------

EMISSIVE = {'block_light': 15, 'sky_light': 15, 'ambient_occlusion': False}


class Model:
    def __init__(self, lit):
        self.lit = lit
        self.elements = []
        self.seed = 0

    def _next(self):
        self.seed += 1
        return rng(1000 + self.seed)

    def add(self, frm, to, faces, rotation=None, comment=None):
        el = {'from': [round(float(v), 4) for v in frm], 'to': [round(float(v), 4) for v in to], 'faces': faces}
        if rotation:
            el['rotation'] = {**rotation, 'origin': [float(o) for o in rotation['origin']], 'angle': float(rotation['angle'])}
        if comment:
            el['__comment'] = comment
        self.elements.append(el)

    def face(self, uv, tex, rot=0, cull=None, emissive=False):
        f = {'uv': [round(float(v), 4) for v in uv], 'texture': tex}
        if rot:
            f['rotation'] = rot
        if cull:
            f['cullface'] = cull
        if emissive and self.lit:
            f['forge_data'] = dict(EMISSIVE)
        return f

    def log(self, axis, start, length, y, other, t, rotation=None, tip=None, tip_len=2.5,
            inner=None, bark='#bark', comment=None):
        """A log of square section t lying along `axis` ('x' or 'z').

        start/length along the axis, `other` is the coordinate on the perpendicular horizontal axis.
        tip: 'start' / 'end' -> that end is charred (glowing when lit) for tip_len pixels.
        inner: face direction (e.g. 'south') that faces the fire and gets the charred texture.
        """
        segs = [(start, start + length, None)]
        if tip == 'end':
            segs = [(start, start + length - tip_len, None), (start + length - tip_len, start + length, 'tip')]
        elif tip == 'start':
            segs = [(start, start + tip_len, 'tip'), (start + tip_len, start + length, None)]
        r = self._next()
        uv_off = r.integers(0, 4)
        cell = r.integers(0, 16)
        cu, cv = (cell % 4) * 4, (cell // 4) * 4
        for a, b, kind in segs:
            L = b - a
            side = '#charcoal' if kind == 'tip' else bark
            u0 = min(16 - L, (uv_off + a) % 16)
            if u0 < 0:
                u0 = 0
            v0 = min(16 - t, r.integers(0, 16 - int(math.ceil(t)) + 1))
            side_uv = [u0, v0, u0 + L, v0 + t]
            end_tex = '#charcoal' if kind == 'tip' else '#end'
            end_uv = [cu, cv, cu + t, cv + t]
            emis = kind == 'tip'
            if axis == 'x':
                frm, to = [a, y, other], [b, y + t, other + t]
                faces = {
                    'north': self.face(side_uv, '#charred' if inner == 'north' and not kind else side, emissive=emis),
                    'south': self.face(side_uv, '#charred' if inner == 'south' and not kind else side, emissive=emis),
                    'up': self.face(side_uv, side, emissive=emis),
                    'down': self.face(side_uv, '#charred' if not kind else side, emissive=emis),
                }
                if a == start or kind == 'tip' and tip == 'start':
                    faces['west'] = self.face(end_uv, end_tex, emissive=emis)
                if b == start + length:
                    faces['east'] = self.face(end_uv, end_tex, emissive=emis)
            else:
                frm, to = [other, y, a], [other + t, y + t, b]
                faces = {
                    'east': self.face(side_uv, '#charred' if inner == 'east' and not kind else side, emissive=emis),
                    'west': self.face(side_uv, '#charred' if inner == 'west' and not kind else side, emissive=emis),
                    'up': self.face(side_uv, side, rot=90, emissive=emis),
                    'down': self.face(side_uv, '#charred' if not kind else side, rot=90, emissive=emis),
                }
                if a == start:
                    faces['north'] = self.face(end_uv, end_tex, emissive=emis)
                if b == start + length:
                    faces['south'] = self.face(end_uv, end_tex, emissive=emis)
            self.add(frm, to, faces, rotation, comment)

    def cube(self, frm, to, tex, emissive=False, rotation=None, skip_down=True, uv_seed=0):
        r = rng(5000 + uv_seed)
        faces = {}
        dims = {'north': (0, 1), 'south': (0, 1), 'east': (2, 1), 'west': (2, 1), 'up': (0, 2), 'down': (0, 2)}
        for d, (i, j) in dims.items():
            if d == 'down' and skip_down:
                continue
            w = to[i] - frm[i]
            h = to[j] - frm[j]
            u = r.integers(0, max(1, int(16 - w)))
            v = r.integers(0, max(1, int(16 - h)))
            faces[d] = self.face([u, v, u + w, v + h], tex, emissive=emissive)
        self.add(frm, to, faces, rotation)

    def json(self, textures):
        return {
            'parent': 'minecraft:block/block',
            'ambientocclusion': True,
            'textures': textures,
            'elements': [{k: v for k, v in e.items() if k != '__comment'} for e in self.elements],
        }


def rot(axis, angle, origin):
    return {'axis': axis, 'angle': angle, 'origin': list(origin)}


def stone_ring(m, seed, blockers):
    """Ring of small fire stones; stones overlapping low logs (blockers, xz rects) are skipped."""
    r = rng(seed)
    n = 16
    for i in range(n):
        a = i / n * math.tau + r.random() * 0.2
        w = 2.25 + r.random() * 1.0
        d = 2.0 + r.random() * 0.75
        h = 1.75 + r.random() * 1.0
        rad = 6.6
        cx = 8 + math.cos(a) * rad
        cz = 8 + math.sin(a) * rad
        x1, z1 = max(0.25, cx - w / 2), max(0.25, cz - d / 2)
        x2, z2 = min(15.75, x1 + w), min(15.75, z1 + d)
        if any(bx1 + 0.5 < cx < bx2 - 0.5 and bz1 + 0.5 < cz < bz2 - 0.5 for bx1, bz1, bx2, bz2 in blockers):
            continue
        angle = [-22.5, 0, 22.5][r.integers(0, 3)]
        m.cube([x1, 0, z1], [x2, h, z2], '#stone',
               rotation=rot('y', angle, [(x1 + x2) / 2, 0, (z1 + z2) / 2]) if angle else None, uv_seed=seed * 50 + i)


def base_bed(m, ember_rect=(4, 4, 12, 12)):
    m.add([1.5, 0, 1.5], [14.5, 0.25, 14.5], {
        'up': m.face([1.5, 1.5, 14.5, 14.5], '#ash'),
        'north': m.face([1.5, 0, 14.5, 0.25], '#ash'), 'south': m.face([1.5, 0, 14.5, 0.25], '#ash'),
        'east': m.face([1.5, 0, 14.5, 0.25], '#ash'), 'west': m.face([1.5, 0, 14.5, 0.25], '#ash'),
        'down': m.face([1.5, 1.5, 14.5, 14.5], '#ash', cull='down')})
    x1, z1, x2, z2 = ember_rect
    m.add([x1, 0.25, z1], [x2, 1.25, z2], {
        'up': m.face([x1, z1, x2, z2], '#embers', emissive=True),
        'north': m.face([x1, 0, x2, 1], '#embers', emissive=True),
        'south': m.face([x1, 2, x2, 3], '#embers', emissive=True),
        'east': m.face([z1, 4, z2, 5], '#embers', emissive=True),
        'west': m.face([z1, 6, z2, 7], '#embers', emissive=True)})


def ember_chunks(m, spots):
    for i, (x, z, s) in enumerate(spots):
        m.cube([x, 1.25, z], [x + s, 1.25 + s * 0.8, z + s], '#embers', emissive=True, uv_seed=900 + i)


def layout_teepee(lit):
    """Variant 0: a teepee - two low base logs and six leaning logs whose burning tips meet above the embers."""
    m = Model(lit)
    base_bed(m, (5, 5, 11, 11))
    m.log('x', 2.5, 11, 0.25, 2.25, 3, inner='south', comment='base north')
    m.log('x', 2.0, 12, 0.25, 11.0, 3, inner='north', bark='#bark_alt', comment='base south')
    # right-hand rule: +angle around Z lifts the +x end, -angle around X lifts the +z end
    m.log('x', 0.75, 8.5, 0.5, 6.25, 3, rotation=rot('z', 45, [0.75, 0.5, 7.75]), tip='end', comment='lean west')
    m.log('x', 6.75, 8.5, 0.5, 6.75, 3, rotation=rot('z', -45, [15.25, 0.5, 8.25]), tip='start',
          bark='#bark_alt', comment='lean east')
    m.log('z', 0.5, 10, 3.25, 5.25, 3, rotation=rot('x', -22.5, [6.75, 3.25, 0.5]), tip='end', comment='lean north')
    m.log('z', 5.5, 10, 3.25, 8.0, 3, rotation=rot('x', 22.5, [9.5, 3.25, 15.5]), tip='start',
          bark='#bark_alt', comment='lean south')
    m.log('x', 1.0, 9, 0.5, 9.5, 2.5, rotation=rot('z', 22.5, [1.0, 0.5, 10.75]), tip='end', tip_len=2,
          comment='lean west 2')
    m.log('x', 6.5, 9, 0.5, 3.75, 2.5, rotation=rot('z', -22.5, [15.5, 0.5, 5.0]), tip='start', tip_len=2,
          bark='#bark_alt', comment='lean east 2')
    ember_chunks(m, [(5.25, 5.5, 1.5), (9.75, 6.0, 1), (6.25, 9.5, 1), (10.0, 9.75, 1.5)])
    stone_ring(m, 7, [(2.5, 2.25, 13.5, 5.25), (2.0, 11.0, 14.0, 14.0), (0.5, 6, 3, 10), (13, 6, 15.5, 10)])
    return m


def layout_cabin(lit):
    """Variant 1: a low two-layer log cabin with a burning kindling teepee inside."""
    m = Model(lit)
    base_bed(m, (5, 5, 11, 11))
    m.log('x', 2.5, 11, 0.25, 2.5, 3, inner='south')
    m.log('x', 2.0, 12, 0.25, 10.5, 3, inner='north', bark='#bark_alt')
    m.log('z', 1.75, 12.5, 3.25, 2.75, 3, inner='east', bark='#bark_alt', tip='end', tip_len=2)
    m.log('z', 2.25, 11.5, 3.25, 10.25, 3, inner='west', tip='start', tip_len=2)
    # kindling teepee
    m.log('x', 5.5, 4.5, 1.25, 7.25, 1.5, rotation=rot('z', 45, [5.5, 1.25, 8]), tip='end', tip_len=2)
    m.log('x', 6.5, 4.5, 1.25, 8.25, 1.5, rotation=rot('z', -45, [11.0, 1.25, 9]), tip='start', tip_len=2)
    m.log('z', 5.5, 4.5, 1.25, 8.0, 1.5, rotation=rot('x', -45, [8.75, 1.25, 5.5]), tip='end', tip_len=2)
    m.log('z', 6.0, 4.5, 1.25, 6.5, 1.5, rotation=rot('x', 45, [7.25, 1.25, 10.5]), tip='start', tip_len=2)
    ember_chunks(m, [(6.0, 6.0, 1.5), (9.0, 9.5, 1), (9.5, 6.25, 1)])
    stone_ring(m, 13, [(2.5, 2.5, 13.5, 5.5), (2, 10.5, 14, 13.5)])
    return m


def layout_star(lit):
    """Variant 2: 'star fire' - six logs radiating out of the centre, burning ends meeting in the embers."""
    m = Model(lit)
    base_bed(m, (5, 5, 11, 11))
    inner = 5.25  # inner end of a west-pointing spoke (2.75 px from the centre)
    # (axis, pointing, y-rotation): rotation follows the right-hand rule around +Y, pivot = block centre
    spokes = [('x', 'w', 0), ('z', 'n', 22.5), ('x', 'e', 45), ('x', 'e', -22.5), ('z', 's', 22.5), ('x', 'w', 45)]
    blockers = []
    for i, (axis, point, angle) in enumerate(spokes):
        thick = 4 if i % 2 == 0 else 3.5
        length = 5.5 + (i % 3) * 0.75
        bark = '#bark' if i % 2 else '#bark_alt'
        r = rot('y', angle, [8, 0, 8]) if angle else None
        if point in ('w', 'n'):
            m.log(axis, inner - length, length, 0.25, 8 - thick / 2, thick, tip='end', tip_len=2.5, bark=bark, rotation=r)
        else:
            m.log(axis, 16 - inner, length, 0.25, 8 - thick / 2, thick, tip='start', tip_len=2.5, bark=bark, rotation=r)
        d = {'w': (-1, 0), 'e': (1, 0), 'n': (0, -1), 's': (0, 1)}[point]
        t = math.radians(angle)
        dx = d[0] * math.cos(t) + d[1] * math.sin(t)
        dz = -d[0] * math.sin(t) + d[1] * math.cos(t)
        cx, cz = 8 + dx * 6, 8 + dz * 6
        blockers.append((cx - 2.5, cz - 2.5, cx + 2.5, cz + 2.5))
    # one log laid across the spokes
    m.log('z', 2.5, 11, 4.0, 9.5, 3, inner='west', bark='#bark')
    ember_chunks(m, [(6.25, 6.5, 1.5), (8.75, 8.5, 1.25), (7.0, 9.25, 1)])
    stone_ring(m, 21, blockers)
    return m


LAYOUTS = [layout_teepee, layout_cabin, layout_star]


def write_models():
    mdir = os.path.join(ASSETS, 'models', 'block')
    for i, fn in enumerate(LAYOUTS):
        for lit in (True, False):
            m = fn(lit)
            tex = {
                'particle': f'{MODID}:block/log_bark',
                'bark': f'{MODID}:block/log_bark',
                'bark_alt': f'{MODID}:block/log_bark_alt',
                'charred': f'{MODID}:block/log_charred',
                'end': f'{MODID}:block/log_end',
                'ash': f'{MODID}:block/ash',
                'stone': f'{MODID}:block/fire_stone',
                'embers': f'{MODID}:block/embers' if lit else f'{MODID}:block/embers_off',
                'charcoal': f'{MODID}:block/charcoal_glow' if lit else f'{MODID}:block/log_end_charred',
            }
            write_json(os.path.join(mdir, f'campfire_{i}{"" if lit else "_off"}.json'), m.json(tex))
    # item model: the lit layout 0 shown in 3D
    write_json(os.path.join(ASSETS, 'models', 'item', 'enhanced_campfire.json'),
               {'parent': f'{MODID}:block/campfire_0'})

    variants = {}
    facing_rot = {'north': 0, 'east': 90, 'south': 180, 'west': 270}
    for i in range(len(LAYOUTS)):
        for facing, y in facing_rot.items():
            for lit in (True, False):
                key = f'facing={facing},lit={"true" if lit else "false"},variant={i}'
                v = {'model': f'{MODID}:block/campfire_{i}{"" if lit else "_off"}'}
                if y:
                    v['y'] = y
                variants[key] = v
    write_json(os.path.join(ASSETS, 'blockstates', 'enhanced_campfire.json'), {'variants': variants})


# ---------------------------------------------------------------------------------------------
# Lang, data
# ---------------------------------------------------------------------------------------------

def write_lang_and_data():
    write_json(os.path.join(ASSETS, 'lang', 'en_us.json'), {
        'block.enhancedcampfire.enhanced_campfire': 'Enhanced Campfire',
        'tooltip.enhancedcampfire.enhanced_campfire': 'A large, crackling campfire. Cooks food like a campfire.',
        'subtitles.enhancedcampfire.crackle': 'Campfire crackles',
        'subtitles.enhancedcampfire.pop': 'Firewood pops',
        'subtitles.enhancedcampfire.hiss': 'Campfire hisses',
        'subtitles.enhancedcampfire.roar': 'Fire roars',
        'enhancedcampfire.configuration.title': 'Enhanced Campfire',
    })
    write_json(os.path.join(ASSETS, 'lang', 'uk_ua.json'), {
        'block.enhancedcampfire.enhanced_campfire': 'Покращене багаття',
        'tooltip.enhancedcampfire.enhanced_campfire': 'Велике багаття, що потріскує. Готує їжу, як звичайне.',
        'subtitles.enhancedcampfire.crackle': 'Багаття потріскує',
        'subtitles.enhancedcampfire.pop': 'Тріщить поліно',
        'subtitles.enhancedcampfire.hiss': 'Багаття шипить',
        'subtitles.enhancedcampfire.roar': 'Гуде вогонь',
        'enhancedcampfire.configuration.title': 'Покращене багаття',
    })
    # loot: silk touch -> itself, otherwise 2 charcoal (vanilla campfire behaviour) + always 2 cobblestone
    silk = {'condition': 'minecraft:match_tool',
            'predicate': {'enchantments': [{'enchantment': 'minecraft:silk_touch', 'levels': {'min': 1}}]}}
    write_json(os.path.join(DATA, MODID, 'loot_tables', 'blocks', 'enhanced_campfire.json'), {
        'type': 'minecraft:block',
        'pools': [{
            'rolls': 1,
            'bonus_rolls': 0,
            'entries': [{
                'type': 'minecraft:alternatives',
                'children': [
                    {'type': 'minecraft:item', 'name': f'{MODID}:enhanced_campfire', 'conditions': [silk]},
                    {'type': 'minecraft:item', 'name': 'minecraft:charcoal',
                     'functions': [{'function': 'minecraft:set_count', 'count': 2},
                                   {'function': 'minecraft:explosion_decay'}]},
                ]}],
        }, {
            'rolls': 1,
            'bonus_rolls': 0,
            'entries': [{'type': 'minecraft:item', 'name': 'minecraft:cobblestone',
                         'functions': [{'function': 'minecraft:set_count', 'count': 2},
                                       {'function': 'minecraft:explosion_decay'}]}],
            'conditions': [{'condition': 'minecraft:inverted', 'term': silk}],
        }],
    })
    write_json(os.path.join(DATA, MODID, 'recipes', 'enhanced_campfire.json'), {
        'type': 'minecraft:crafting_shaped',
        'category': 'misc',
        'pattern': [' S ', 'SCS', 'BLB'],
        'key': {
            'S': {'item': 'minecraft:stick'},
            'C': {'tag': 'minecraft:coals'},
            'L': {'tag': 'minecraft:logs_that_burn'},
            'B': {'item': 'minecraft:cobblestone'},
        },
        'result': {'item': f'{MODID}:enhanced_campfire'},
    })
    write_json(os.path.join(DATA, MODID, 'recipes', 'enhanced_campfire_from_campfire.json'), {
        'type': 'minecraft:crafting_shapeless',
        'category': 'misc',
        'ingredients': [{'item': 'minecraft:campfire'}, {'item': 'minecraft:cobblestone'},
                        {'tag': 'minecraft:logs_that_burn'}],
        'result': {'item': f'{MODID}:enhanced_campfire'},
    })
    for tag in ('campfires', 'mineable/axe'):
        write_json(os.path.join(DATA, 'minecraft', 'tags', 'blocks', f'{tag}.json'),
                   {'replace': False, 'values': [f'{MODID}:enhanced_campfire']})


def main():
    write_block_textures()
    write_flame_sheets()
    write_particles()
    write_models()
    write_lang_and_data()
    print('Enhanced Campfire assets written to', os.path.abspath(ROOT))


if __name__ == '__main__':
    main()
