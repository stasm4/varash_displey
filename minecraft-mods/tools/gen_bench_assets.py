#!/usr/bin/env python3
"""Generates textures, models, blockstates, lang, loot tables, recipes and tags of the Wooden Bench mod.

Two seats per wood type:
  <wood>_seat_board - a thick rough-hewn board with live (bark) edges resting on two octagonal stumps
  <wood>_bench      - a slatted bench with backrest, armrests and legs; neighbours facing the same way merge
                      into one long bench (shape = single / left / middle / right)
"""
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(__file__))
from pixelart import fbm, hex_rgb, rng, save, to_image, value_noise, write_json  # noqa: E402

MODID = 'woodenbench'
ROOT = os.path.join(os.path.dirname(__file__), '..', 'WoodenBench', 'src', 'main', 'resources')
ASSETS = os.path.join(ROOT, 'assets', MODID)
DATA = os.path.join(ROOT, 'data')
TEX = os.path.join(ASSETS, 'textures', 'block')

# wood id: (plank base colour, bark colour, log id, planks id, slab id, english, ukrainian adjective (fem), (masc))
WOODS = {
    'oak': ('#b08d57', '#5a4529', 'oak_log', 'oak_planks', 'oak_slab', 'Oak', 'Дубова', 'Дубова'),
    'spruce': ('#7b5a37', '#3d2c1b', 'spruce_log', 'spruce_planks', 'spruce_slab', 'Spruce', 'Ялинова', 'Ялинова'),
    'birch': ('#d2bd84', '#e6e2d6', 'birch_log', 'birch_planks', 'birch_slab', 'Birch', 'Березова', 'Березова'),
    'jungle': ('#ac7c55', '#56491f', 'jungle_log', 'jungle_planks', 'jungle_slab', 'Jungle', 'Тропічна', 'Тропічна'),
    'acacia': ('#ad5e33', '#6a625a', 'acacia_log', 'acacia_planks', 'acacia_slab', 'Acacia', 'Акацієва', 'Акацієва'),
    'dark_oak': ('#4e331a', '#3a2c19', 'dark_oak_log', 'dark_oak_planks', 'dark_oak_slab', 'Dark Oak', 'Темно-дубова', 'Темно-дубова'),
    'mangrove': ('#7a3a33', '#55432d', 'mangrove_log', 'mangrove_planks', 'mangrove_slab', 'Mangrove', 'Мангрова', 'Мангрова'),
    'cherry': ('#dfb2a8', '#3a222a', 'cherry_log', 'cherry_planks', 'cherry_slab', 'Cherry', 'Вишнева', 'Вишнева'),
    'crimson': ('#6c3550', '#5b1c2f', 'crimson_stem', 'crimson_planks', 'crimson_slab', 'Crimson', 'Багряна', 'Багряна'),
    'warped': ('#2c6b65', '#3b3a4f', 'warped_stem', 'warped_planks', 'warped_slab', 'Warped', 'Химерна', 'Химерна'),
}
NETHER = {'crimson', 'warped'}
SHAPES = ['single', 'left', 'middle', 'right']


def shades(base, factors=(0.60, 0.72, 0.84, 0.94, 1.03, 1.12)):
    c = np.array(hex_rgb(base), dtype=float)
    return [tuple(np.clip(c * f, 0, 255)) for f in factors]


def pal_map(values, pal):
    pal = np.array(pal, dtype=float)
    idx = np.clip((values * len(pal)).astype(int), 0, len(pal) - 1)
    return pal[idx]


def planks_texture(seed, base, boards=2):
    """Seat surface: `boards` long boards per 16 px, grain along U, knots, a crack, dents and worn edges."""
    r = rng(seed)
    pal = shades(base)
    h = w = 16
    grain_rows = value_noise(1, h, 2, seed)[:, :1]
    streak = fbm(w, h, seed + 3, base_cell=8, octaves=2)
    vals = 0.45 * grain_rows + 0.35 * streak + r.random((h, w)) * 0.18
    board_h = h // boards
    for b in range(boards):  # every board gets its own tint
        vals[b * board_h:(b + 1) * board_h] += (r.random() - 0.5) * 0.18
    vals = (vals - vals.min()) / (vals.max() - vals.min() + 1e-9) * 0.999
    rgb = pal_map(vals * 0.8 + 0.12, pal)
    for b in range(boards):
        y0 = b * board_h
        rgb[y0] = np.array(pal[0]) * 0.9  # seam between boards
        rgb[y0 + 1, :] = np.minimum(np.array(pal[-1]) * 1.02, rgb[y0 + 1, :] * 1.12)  # worn, lighter edge
        # long grain lines
        for _ in range(2):
            gy = y0 + 2 + r.integers(0, board_h - 3)
            x0 = r.integers(0, w)
            for i in range(r.integers(6, 14)):
                rgb[gy, (x0 + i) % w] = pal[1]
        # knot
        if r.random() < 0.85:
            kx, ky = r.integers(2, 14), y0 + 2 + r.integers(0, max(1, board_h - 4))
            for dy in (-1, 0, 1):
                for dx in (-1, 0, 1):
                    yy, xx = ky + dy, kx + dx
                    if 0 <= yy < h and abs(dx) + abs(dy) <= 1:
                        rgb[yy, xx % w] = pal[0] if (dx, dy) == (0, 0) else pal[1]
            rgb[ky, (kx + 2) % w] = pal[2]
            rgb[ky, (kx - 2) % w] = pal[2]
        # hairline crack from the board end
        if r.random() < 0.6:
            cy = y0 + 2 + r.integers(0, max(1, board_h - 3))
            for i in range(r.integers(3, 6)):
                rgb[cy, (w - 1 - i) % w] = np.array(pal[0]) * 0.8
        # a couple of dents / nail holes
        for _ in range(2):
            rgb[y0 + r.integers(1, board_h), r.integers(0, w)] = np.array(pal[0]) * 0.85
    return to_image(rgb)


def leg_texture(seed, base):
    """Vertical grain (posts and legs)."""
    img = np.array(planks_texture(seed, base, boards=1))[:, :, :3]
    return to_image(np.transpose(img, (1, 0, 2)) * 0.93)


def end_grain_texture(seed, base):
    r = rng(seed)
    pal = shades(base)
    out = np.zeros((16, 16, 3))
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5 + (r.random() - 0.5) * 0.6, (y - 7.5) * 2.2)
            ring = int(d * 0.9 + r.random() * 0.5) % 3
            out[y, x] = pal[2 + ring] if ring < 2 else pal[1]
    return to_image(out)


def log_top_texture(seed, base, bark):
    r = rng(seed)
    pal = shades(base)
    bpal = shades(bark, (0.7, 0.85, 1.0))
    out = np.zeros((16, 16, 3))
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d > 7.2:
                out[y, x] = bpal[r.integers(0, 3)]
            elif d < 0.9:
                out[y, x] = pal[1]
            else:
                ring = int(d * 1.15 + r.random() * 0.35) % 3
                out[y, x] = pal[[3, 4, 2][ring]]
    return to_image(out)


def bark_texture(seed, bark, birch=False):
    r = rng(seed)
    pal = shades(bark, (0.55, 0.7, 0.85, 1.0, 1.12))
    cols = value_noise(16, 1, 2, seed)[:1, :]
    vals = 0.55 * cols + 0.3 * fbm(16, 16, seed + 2, 8, 2) + r.random((16, 16)) * 0.2
    vals = (vals - vals.min()) / (vals.max() - vals.min() + 1e-9) * 0.999
    rgb = pal_map(vals, pal)
    for x in range(0, 16, 3):  # vertical fissures
        xx = (x + r.integers(0, 2)) % 16
        y0 = r.integers(0, 16)
        for i in range(r.integers(5, 12)):
            rgb[(y0 + i) % 16, xx] = pal[0]
    if birch:  # dark lenticels
        for _ in range(7):
            y, x = r.integers(0, 16), r.integers(0, 13)
            rgb[y, x:x + r.integers(2, 4)] = hex_rgb('#2b2622')
    return to_image(rgb)


def write_textures():
    for i, (wood, (base, bark, *_)) in enumerate(WOODS.items()):
        s = 100 * (i + 1)
        save(planks_texture(s + 1, base), os.path.join(TEX, f'{wood}_seat_planks.png'))
        save(planks_texture(s + 2, base, boards=1), os.path.join(TEX, f'{wood}_board.png'))
        save(leg_texture(s + 3, base), os.path.join(TEX, f'{wood}_leg.png'))
        save(end_grain_texture(s + 4, base), os.path.join(TEX, f'{wood}_end.png'))
        save(log_top_texture(s + 5, base, bark), os.path.join(TEX, f'{wood}_stump_top.png'))
        save(bark_texture(s + 6, bark, birch=wood == 'birch'), os.path.join(TEX, f'{wood}_bark.png'))


# ---------------------------------------------------------------------------------------------- models

def auto_uv(face, a, b):
    x1, y1, z1 = a
    x2, y2, z2 = b
    if face in ('north', 'south'):
        return [x1, 16 - y2, x2, 16 - y1]
    if face in ('east', 'west'):
        return [z1, 16 - y2, z2, 16 - y1]
    return [x1, z1, x2, z2]


def box(a, b, tex, rotation=None, skip=(), uv=None):
    """tex: a texture variable for all faces or a dict per face ('side', 'top' shortcuts allowed)."""
    faces = {}
    for f in ('north', 'south', 'east', 'west', 'up', 'down'):
        if f in skip:
            continue
        if isinstance(tex, dict):
            t = tex.get(f) or (tex.get('top') if f in ('up', 'down') else tex.get('side'))
        else:
            t = tex
        u = (uv or {}).get(f) or auto_uv(f, a, b)
        u = [round(float(max(0.0, min(16.0, v))), 4) for v in u]
        faces[f] = {'uv': u, 'texture': t}
    el = {'from': [round(float(v), 4) for v in a], 'to': [round(float(v), 4) for v in b], 'faces': faces}
    if rotation:
        el['rotation'] = rotation
    return el


def seat_board_template():
    els = []
    for cx in (4.0, 12.0):  # two octagonal stumps
        stump_tex = {'side': '#bark', 'top': '#stump_top'}
        els.append(box([cx - 2.5, 0, 5.5], [cx + 2.5, 6, 10.5], stump_tex,
                       uv={'up': [5.5, 5.5, 10.5, 10.5], 'down': [5.5, 5.5, 10.5, 10.5]}))
        els.append(box([cx - 2.5, 0, 5.5], [cx + 2.5, 5.99, 10.5], stump_tex,
                       rotation={'origin': [cx, 0, 8], 'axis': 'y', 'angle': 45.0},
                       uv={'up': [5.5, 5.5, 10.5, 10.5], 'down': [5.5, 5.5, 10.5, 10.5]}))
    # the board itself (2 px thick, 7 px wide)
    els.append(box([0, 6, 4.5], [16, 8, 11.5], {'up': '#board', 'down': '#board', 'north': '#board', 'south': '#board',
                                               'east': '#end', 'west': '#end'},
                   uv={'up': [0, 4.5, 16, 11.5], 'down': [0, 4.5, 16, 11.5], 'north': [0, 7, 16, 9],
                       'south': [0, 10, 16, 12], 'east': [4.5, 7, 11.5, 9], 'west': [4.5, 7, 11.5, 9]}))
    # live (waney) edges: bark left on both long edges, a bit shorter and lower than the board
    els.append(box([1.0, 6.25, 3.75], [14.5, 7.75, 4.5], '#bark', skip=('down',)))
    els.append(box([2.0, 6.25, 11.5], [15.25, 7.75, 12.25], '#bark', skip=('down',)))
    # a few chips / irregular bites taken out of the top surface edge
    els.append(box([5.0, 7.75, 4.5], [7.0, 8.0, 5.0], '#board', skip=('down',)))
    return {'parent': 'minecraft:block/block', 'textures': {'particle': '#board'}, 'elements': els,
            'display': SEAT_DISPLAY}


SEAT_DISPLAY = {
    'gui': {'rotation': [30, 225, 0], 'translation': [0, 0, 0], 'scale': [0.625, 0.625, 0.625]},
    'ground': {'rotation': [0, 0, 0], 'translation': [0, 3, 0], 'scale': [0.25, 0.25, 0.25]},
    'fixed': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [0.5, 0.5, 0.5]},
    'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [0.375, 0.375, 0.375]},
    'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 0, 0], 'scale': [0.4, 0.4, 0.4]},
    'firstperson_lefthand': {'rotation': [0, 225, 0], 'translation': [0, 0, 0], 'scale': [0.4, 0.4, 0.4]},
}


def bench_template(left_legs, right_legs):
    """Bench facing north (the sitter looks north, the backrest is on the south side)."""
    S = {'side': '#planks', 'top': '#planks', 'east': '#end', 'west': '#end'}
    L = {'side': '#leg', 'top': '#end'}
    els = []
    # seat: three slats with 0.5 px gaps
    for z1, z2 in ((2.0, 5.5), (6.0, 9.5), (10.0, 13.5)):
        els.append(box([0, 7, z1], [16, 8.5, z2], S))
    # aprons under the seat
    els.append(box([0, 6, 2.5], [16, 7, 3.5], S, skip=('up',)))
    els.append(box([0, 6, 12.0], [16, 7, 13.0], S, skip=('up',)))
    # backrest: two horizontal rails
    els.append(box([0, 10, 13.5], [16, 12.5, 15.0], S))
    els.append(box([0, 13.25, 13.5], [16, 15.5, 15.0], S))
    for side, enabled in (('left', left_legs), ('right', right_legs)):
        if not enabled:
            continue
        x1 = 1.0 if side == 'left' else 13.0
        x2 = x1 + 2
        els.append(box([x1, 0, 2.5], [x2, 7, 4.5], L, skip=('up',)))                  # front leg
        els.append(box([x1, 0, 12.75], [x2, 16, 14.75], L))                           # back leg + backrest post
        els.append(box([x1 + 0.5, 2, 4.5], [x2 - 0.5, 3, 12.75], L))                 # stretcher
        els.append(box([x1 + 0.25, 8.5, 3.25], [x2 - 0.25, 11.0, 4.75], L, skip=('up',)))  # armrest support
        els.append(box([x1 - 0.5, 11.0, 2.5], [x2 + 0.5, 12.0, 13.0], {'side': '#planks', 'top': '#planks',
                                                                       'north': '#end', 'south': '#end'}))  # armrest
    return {'parent': 'minecraft:block/block', 'textures': {'particle': '#planks'}, 'elements': els,
            'display': SEAT_DISPLAY}


def write_models():
    mdir = os.path.join(ASSETS, 'models', 'block')
    write_json(os.path.join(mdir, 'template_seat_board.json'), seat_board_template())
    legs = {'single': (True, True), 'left': (True, False), 'middle': (False, False), 'right': (False, True)}
    for shape, (l, r) in legs.items():
        write_json(os.path.join(mdir, f'template_bench_{shape}.json'), bench_template(l, r))
    facing_rot = {'north': 0, 'east': 90, 'south': 180, 'west': 270}
    for wood in WOODS:
        t = f'{MODID}:block/{wood}'
        write_json(os.path.join(mdir, f'{wood}_seat_board.json'), {
            'parent': f'{MODID}:block/template_seat_board',
            'textures': {'board': f'{t}_board', 'end': f'{t}_end', 'bark': f'{t}_bark', 'stump_top': f'{t}_stump_top'}})
        for shape in SHAPES:
            write_json(os.path.join(mdir, f'{wood}_bench_{shape}.json'), {
                'parent': f'{MODID}:block/template_bench_{shape}',
                'textures': {'planks': f'{t}_seat_planks', 'leg': f'{t}_leg', 'end': f'{t}_end'}})
        write_json(os.path.join(ASSETS, 'models', 'item', f'{wood}_seat_board.json'),
                   {'parent': f'{MODID}:block/{wood}_seat_board'})
        write_json(os.path.join(ASSETS, 'models', 'item', f'{wood}_bench.json'),
                   {'parent': f'{MODID}:block/{wood}_bench_single'})
        write_json(os.path.join(ASSETS, 'blockstates', f'{wood}_seat_board.json'), {'variants': {
            'axis=x': {'model': f'{MODID}:block/{wood}_seat_board'},
            'axis=z': {'model': f'{MODID}:block/{wood}_seat_board', 'y': 90}}})
        variants = {}
        for facing, y in facing_rot.items():
            for shape in SHAPES:
                v = {'model': f'{MODID}:block/{wood}_bench_{shape}'}
                if y:
                    v['y'] = y
                variants[f'facing={facing},shape={shape}'] = v
        write_json(os.path.join(ASSETS, 'blockstates', f'{wood}_bench.json'), {'variants': variants})


# ---------------------------------------------------------------------------------------------- data

def write_data():
    en = {
        'itemGroup.woodenbench': 'Wooden Benches',
        'tooltip.woodenbench.seat': 'Left-click to sit down, Shift to stand up.',
        'tooltip.woodenbench.break': 'Shift + left-click to break it.',
        'message.woodenbench.occupied': 'Someone is already sitting here',
        'message.woodenbench.no_headroom': 'Not enough room to sit here',
        'message.woodenbench.too_far': 'Too far away to sit down',
        'message.woodenbench.obstructed': 'Something is in the way',
        'entity.woodenbench.seat': 'Seat',
        'subtitles.woodenbench.sit': 'Wood creaks',
    }
    uk = {
        'itemGroup.woodenbench': 'Дерев\'яні лави',
        'tooltip.woodenbench.seat': 'ЛКМ — сісти, Shift — встати.',
        'tooltip.woodenbench.break': 'Shift + ЛКМ — зламати.',
        'message.woodenbench.occupied': 'Тут уже хтось сидить',
        'message.woodenbench.no_headroom': 'Тут замало місця, щоб сісти',
        'message.woodenbench.too_far': 'Задалеко, щоб сісти',
        'message.woodenbench.obstructed': 'Щось заважає',
        'entity.woodenbench.seat': 'Сидіння',
        'subtitles.woodenbench.sit': 'Рипить дерево',
    }
    axe_tag = []
    for wood, (base, bark, log, planks, slab, name_en, name_uk_f, _) in WOODS.items():
        en[f'block.{MODID}.{wood}_seat_board'] = f'{name_en} Seat Board'
        en[f'block.{MODID}.{wood}_bench'] = f'{name_en} Bench'
        uk[f'block.{MODID}.{wood}_seat_board'] = f'{name_uk_f} дошка для сидіння'
        uk[f'block.{MODID}.{wood}_bench'] = f'{name_uk_f} лава'
        for kind in ('seat_board', 'bench'):
            block = f'{MODID}:{wood}_{kind}'
            axe_tag.append(block)
            write_json(os.path.join(DATA, MODID, 'loot_tables', 'blocks', f'{wood}_{kind}.json'), {
                'type': 'minecraft:block',
                'pools': [{'rolls': 1, 'bonus_rolls': 0,
                           'entries': [{'type': 'minecraft:item', 'name': block}],
                           'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
        write_json(os.path.join(DATA, MODID, 'recipes', f'{wood}_seat_board.json'), {
            'type': 'minecraft:crafting_shaped', 'category': 'building', 'group': 'seat_board',
            'pattern': ['SSS', 'L L'],
            'key': {'S': {'item': f'minecraft:{slab}'}, 'L': {'item': f'minecraft:{log}'}},
            'result': {'item': f'{MODID}:{wood}_seat_board', 'count': 2}})
        write_json(os.path.join(DATA, MODID, 'recipes', f'{wood}_bench.json'), {
            'type': 'minecraft:crafting_shaped', 'category': 'building', 'group': 'bench',
            'pattern': ['  /', '###', '/ /'],
            'key': {'#': {'item': f'minecraft:{planks}'}, '/': {'item': 'minecraft:stick'}},
            'result': {'item': f'{MODID}:{wood}_bench', 'count': 2}})
    write_json(os.path.join(ASSETS, 'lang', 'en_us.json'), en)
    write_json(os.path.join(ASSETS, 'lang', 'uk_ua.json'), uk)
    write_json(os.path.join(DATA, 'minecraft', 'tags', 'blocks', 'mineable', 'axe.json'),
               {'replace': False, 'values': axe_tag})
    write_json(os.path.join(DATA, MODID, 'tags', 'blocks', 'seats.json'), {'replace': False, 'values': axe_tag})
    write_json(os.path.join(ASSETS, 'sounds.json'), {
        'block.seat.sit': {'subtitle': f'subtitles.{MODID}.sit', 'sounds': [
            {'name': 'minecraft:block/wood/step1', 'volume': 0.7, 'pitch': 0.8},
            {'name': 'minecraft:block/wood/step3', 'volume': 0.7, 'pitch': 0.75},
            {'name': 'minecraft:block/wood/step5', 'volume': 0.7, 'pitch': 0.85},
            {'name': 'minecraft:block/ladder/step2', 'volume': 0.6, 'pitch': 0.7}]}})


def main():
    write_textures()
    write_models()
    write_data()
    print('Wooden Bench assets written to', os.path.abspath(ROOT))


if __name__ == '__main__':
    main()
