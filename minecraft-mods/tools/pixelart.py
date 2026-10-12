"""Small helpers for procedurally generating Minecraft-style pixel-art textures.

Everything is deterministic (seeded) so re-running the generators reproduces
exactly the same PNG files.
"""
import json
import os

import numpy as np
from PIL import Image


def rng(seed):
    return np.random.default_rng(seed)


def value_noise(w, h, cell, seed, tile=True):
    """Smooth value noise in [0,1]. If tile is True the result wraps around."""
    r = rng(seed)
    gw = max(1, int(np.ceil(w / cell)))
    gh = max(1, int(np.ceil(h / cell)))
    grid = r.random((gh + 1, gw + 1))
    if tile:
        grid[-1, :] = grid[0, :]
        grid[:, -1] = grid[:, 0]
    ys = np.arange(h) / cell
    xs = np.arange(w) / cell
    y0 = np.floor(ys).astype(int)
    x0 = np.floor(xs).astype(int)
    fy = ys - y0
    fx = xs - x0
    fy = fy * fy * (3 - 2 * fy)
    fx = fx * fx * (3 - 2 * fx)
    a = grid[y0][:, x0]
    b = grid[y0][:, x0 + 1]
    c = grid[y0 + 1][:, x0]
    d = grid[y0 + 1][:, x0 + 1]
    top = a + (b - a) * fx[None, :]
    bot = c + (d - c) * fx[None, :]
    return top + (bot - top) * fy[:, None]


def fbm(w, h, seed, base_cell=8, octaves=3, tile=True):
    total = np.zeros((h, w))
    amp = 1.0
    norm = 0.0
    cell = base_cell
    for o in range(octaves):
        total += amp * value_noise(w, h, max(1, cell), seed + o * 101, tile)
        norm += amp
        amp *= 0.5
        cell = max(1, cell // 2)
    return total / norm


def hex_rgb(h):
    h = h.lstrip('#')
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def palette_map(values, palette):
    """Map values in [0,1] onto a list of colours (dark -> light), pixel-art style (no blending)."""
    pal = np.array([hex_rgb(c) if isinstance(c, str) else c for c in palette], dtype=np.float64)
    idx = np.clip((values * len(pal)).astype(int), 0, len(pal) - 1)
    return pal[idx]


def to_image(rgb, alpha=None):
    rgb = np.clip(rgb, 0, 255).astype(np.uint8)
    if alpha is None:
        return Image.fromarray(rgb, 'RGB').convert('RGBA')
    a = np.clip(alpha, 0, 255).astype(np.uint8)
    return Image.fromarray(np.dstack([rgb, a]), 'RGBA')


def save(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, optimize=True)


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write('\n')


def shade(rgb, factor):
    return np.clip(np.array(rgb, dtype=np.float64) * factor, 0, 255)
