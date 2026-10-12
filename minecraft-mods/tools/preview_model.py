#!/usr/bin/env python3
"""Tiny software renderer for Minecraft JSON block models (used to eyeball generated models).

usage: preview_model.py <resources_dir> <namespace:block/model> [more models...] -o out.png
Renders each model from three isometric angles (orthographic, z-buffered, textured, simple
Minecraft-like face shading; faces with forge_data block_light are drawn unshaded).
"""
import argparse
import json
import math
import os

import numpy as np
from PIL import Image

FACE_CORNERS = {
    'north': lambda a, b: [(b[0], b[1], a[2]), (a[0], b[1], a[2]), (a[0], a[1], a[2]), (b[0], a[1], a[2])],
    'south': lambda a, b: [(a[0], b[1], b[2]), (b[0], b[1], b[2]), (b[0], a[1], b[2]), (a[0], a[1], b[2])],
    'east': lambda a, b: [(b[0], b[1], b[2]), (b[0], b[1], a[2]), (b[0], a[1], a[2]), (b[0], a[1], b[2])],
    'west': lambda a, b: [(a[0], b[1], a[2]), (a[0], b[1], b[2]), (a[0], a[1], b[2]), (a[0], a[1], a[2])],
    'up': lambda a, b: [(a[0], b[1], a[2]), (b[0], b[1], a[2]), (b[0], b[1], b[2]), (a[0], b[1], b[2])],
    'down': lambda a, b: [(a[0], a[1], b[2]), (b[0], a[1], b[2]), (b[0], a[1], a[2]), (a[0], a[1], a[2])],
}


def load_model(res, name):
    ns, path = name.split(':') if ':' in name else ('minecraft', name)
    with open(os.path.join(res, 'assets', ns, 'models', path + '.json')) as f:
        m = json.load(f)
    if 'parent' in m and not m['parent'].endswith('block/block') and 'elements' not in m:
        parent = load_model(res, m['parent'])
        tex = dict(parent.get('textures', {}))
        tex.update(m.get('textures', {}))
        parent['textures'] = tex
        return parent
    return m


def resolve(tex, key, depth=0):
    v = tex.get(key.lstrip('#'))
    if v is None:
        return None
    if v.startswith('#') and depth < 8:
        return resolve(tex, v, depth + 1)
    return v


_tex_cache = {}


def texture(res, name):
    if name not in _tex_cache:
        ns, path = name.split(':')
        img = Image.open(os.path.join(res, 'assets', ns, 'textures', path + '.png')).convert('RGBA')
        w, h = img.size
        if h > w:
            img = img.crop((0, 0, w, w))  # first animation frame
        _tex_cache[name] = np.array(img).astype(np.float64)
    return _tex_cache[name]


def rot_matrix(axis, deg):
    t = math.radians(deg)
    c, s = math.cos(t), math.sin(t)
    if axis == 'x':
        return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
    if axis == 'y':
        return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def collect_quads(res, model, block_y_rot=0):
    tex = model.get('textures', {})
    quads = []
    for el in model.get('elements', []):
        a, b = el['from'], el['to']
        r = el.get('rotation')
        for d, face in el['faces'].items():
            pts = np.array(FACE_CORNERS[d](a, b), dtype=float)
            if r:
                o = np.array(r['origin'], dtype=float)
                pts = (rot_matrix(r['axis'], r['angle']) @ (pts - o).T).T + o
            if block_y_rot:
                # blockstate y rotation is clockwise seen from above
                pts = (rot_matrix('y', -block_y_rot) @ (pts - 8).T).T + 8
            u0, v0, u1, v1 = face.get('uv', [0, 0, 16, 16])
            uvs = [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]
            k = (face.get('rotation', 0) // 90) % 4
            uvs = [uvs[(i - k) % 4] for i in range(4)]
            tname = resolve(tex, face['texture'])
            emissive = face.get('forge_data', {}).get('block_light', 0) > 0
            quads.append((pts, uvs, texture(res, tname), emissive))
    return quads


def render(quads, yaw, pitch, size=420, night=False, center=(8, 4, 8), span=30.0):
    img = np.zeros((size, size, 3))
    img[:] = (24, 26, 34) if night else (150, 170, 190)
    zbuf = np.full((size, size), np.inf)
    R = rot_matrix('x', -pitch) @ rot_matrix('y', yaw)
    scale = size / span
    light_dir = np.array([0.35, 1.0, 0.6])
    light_dir /= np.linalg.norm(light_dir)
    for pts, uvs, tex, emissive in quads:
        p = (R @ (pts - np.array(center)).T).T
        n = np.cross(pts[1] - pts[0], pts[3] - pts[0])
        nl = np.linalg.norm(n)
        if nl < 1e-9:
            continue
        n /= nl
        if night:
            shade = 1.0 if emissive else 0.18 + 0.25 * max(0, n[1])
        else:
            shade = 1.0 if emissive else 0.55 + 0.45 * max(0.0, float(n @ light_dir))
        sx = p[:, 0] * scale + size / 2
        sy = -p[:, 1] * scale + size / 2
        depth = p[:, 2]
        uv = np.array(uvs, dtype=float)
        for tri in ((0, 1, 2), (0, 2, 3)):
            xs, ys, zs, tuv = sx[list(tri)], sy[list(tri)], depth[list(tri)], uv[list(tri)]
            minx, maxx = int(max(0, np.floor(xs.min()))), int(min(size - 1, np.ceil(xs.max())))
            miny, maxy = int(max(0, np.floor(ys.min()))), int(min(size - 1, np.ceil(ys.max())))
            if minx > maxx or miny > maxy:
                continue
            gx, gy = np.meshgrid(np.arange(minx, maxx + 1) + 0.5, np.arange(miny, maxy + 1) + 0.5)
            d = (ys[1] - ys[2]) * (xs[0] - xs[2]) + (xs[2] - xs[1]) * (ys[0] - ys[2])
            if abs(d) < 1e-9:
                continue
            w0 = ((ys[1] - ys[2]) * (gx - xs[2]) + (xs[2] - xs[1]) * (gy - ys[2])) / d
            w1 = ((ys[2] - ys[0]) * (gx - xs[2]) + (xs[0] - xs[2]) * (gy - ys[2])) / d
            w2 = 1 - w0 - w1
            inside = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
            if not inside.any():
                continue
            z = w0 * zs[0] + w1 * zs[1] + w2 * zs[2]
            u = w0 * tuv[0, 0] + w1 * tuv[1, 0] + w2 * tuv[2, 0]
            v = w0 * tuv[0, 1] + w1 * tuv[1, 1] + w2 * tuv[2, 1]
            th, tw = tex.shape[:2]
            ti = np.clip((v / 16 * th).astype(int), 0, th - 1)
            tj = np.clip((u / 16 * tw).astype(int), 0, tw - 1)
            col = tex[ti, tj]
            ys_i = (gy - 0.5).astype(int)
            xs_i = (gx - 0.5).astype(int)
            mask = inside & (col[..., 3] > 127) & (z < zbuf[ys_i, xs_i])
            zbuf[ys_i[mask], xs_i[mask]] = z[mask]
            img[ys_i[mask], xs_i[mask]] = col[..., :3][mask] * shade
    return img


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('res')
    ap.add_argument('models', nargs='+')
    ap.add_argument('-o', '--out', required=True)
    ap.add_argument('--night', action='store_true')
    ap.add_argument('--yrot', type=int, default=0)
    ap.add_argument('--big', action='store_true', help='2x2 model spanning 0..32 px')
    args = ap.parse_args()
    rows = []
    views = [(-35, 30), (145, 30), (-120, 62)]  # yaw, pitch (camera looks at the model from these angles)
    for name in args.models:
        quads = collect_quads(args.res, load_model(args.res, name), args.yrot)
        kw = {'center': (16, 6, 16), 'span': 52.0} if args.big else {}
        rows.append(np.hstack([render(quads, y, p, night=args.night, **kw) for y, p in views]))
    Image.fromarray(np.vstack(rows).clip(0, 255).astype(np.uint8)).save(args.out)
    print('wrote', args.out)


if __name__ == '__main__':
    main()
