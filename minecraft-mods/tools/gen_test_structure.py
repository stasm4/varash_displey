#!/usr/bin/env python3
"""Writes a tiny GameTest structure (stone floor + air) as a gzipped NBT structure file for both mods.

usage: gen_test_structure.py
"""
import gzip
import os
import struct

DATA_VERSION = 3465  # Minecraft 1.20.1
SIZE = (7, 5, 7)


def _str(s):
    b = s.encode('utf-8')
    return struct.pack('>H', len(b)) + b


def tag(type_id, name, payload):
    return bytes([type_id]) + _str(name) + payload


def t_int(v):
    return struct.pack('>i', v)


def t_list(elem_type, items):
    return bytes([elem_type]) + struct.pack('>i', len(items)) + b''.join(items)


def t_compound(entries):
    return b''.join(entries) + b'\x00'


def structure():
    palette = [t_compound([tag(8, 'Name', _str('minecraft:stone'))])]
    blocks = []
    for x in range(SIZE[0]):
        for z in range(SIZE[2]):
            blocks.append(t_compound([
                tag(9, 'pos', t_list(3, [t_int(x), t_int(0), t_int(z)])),
                tag(3, 'state', t_int(0)),
            ]))
    root = t_compound([
        tag(3, 'DataVersion', t_int(DATA_VERSION)),
        tag(9, 'size', t_list(3, [t_int(v) for v in SIZE])),
        tag(9, 'palette', t_list(10, palette)),
        tag(9, 'blocks', t_list(10, blocks)),
        tag(9, 'entities', t_list(10, [])),
    ])
    return tag(10, '', root)


def main():
    here = os.path.dirname(__file__)
    for project, modid in (('EnhancedCampfire', 'enhancedcampfire'), ('WoodenBench', 'woodenbench')):
        out = os.path.join(here, '..', project, 'src', 'main', 'resources', 'data', modid, 'structures', 'platform.nbt')
        os.makedirs(os.path.dirname(out), exist_ok=True)
        with gzip.open(out, 'wb') as f:
            f.write(structure())
        print('wrote', os.path.abspath(out))


if __name__ == '__main__':
    main()
