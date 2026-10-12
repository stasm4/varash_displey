#!/usr/bin/env python3
"""Procedurally synthesises the Enhanced Campfire sound effects (original audio, no third-party samples).

crackle*: clusters of tiny wood crackles      pop*:  a single loud wood crack with a resonant body
hiss*:    rain sizzling on embers             roar*: low, slowly breathing fire rumble (fades in/out, overlaps)

Writes mono 44.1 kHz OGG Vorbis files (ffmpeg with libvorbis required) and sounds.json.
"""
import os
import subprocess
import sys
import tempfile
import wave

import numpy as np

sys.path.insert(0, os.path.dirname(__file__))
from pixelart import write_json  # noqa: E402

SR = 44100
MODID = 'enhancedcampfire'
ASSETS = os.path.join(os.path.dirname(__file__), '..', 'EnhancedCampfire', 'src', 'main', 'resources', 'assets', MODID)
OUT = os.path.join(ASSETS, 'sounds')


def biquad(x, b, a):
    """Direct-form I biquad (pure python loop on numpy arrays; fine for a few seconds of audio)."""
    y = np.zeros_like(x)
    x1 = x2 = y1 = y2 = 0.0
    b0, b1, b2 = b
    _, a1, a2 = a
    for i in range(len(x)):
        xi = x[i]
        yi = b0 * xi + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2, x1 = x1, xi
        y2, y1 = y1, yi
        y[i] = yi
    return y


def bandpass(x, f, q):
    w = 2 * np.pi * f / SR
    alpha = np.sin(w) / (2 * q)
    a0 = 1 + alpha
    b = (alpha / a0, 0.0, -alpha / a0)
    a = (1.0, -2 * np.cos(w) / a0, (1 - alpha) / a0)
    return biquad(x, b, a)


def lowpass(x, f, q=0.707):
    w = 2 * np.pi * f / SR
    alpha = np.sin(w) / (2 * q)
    cw = np.cos(w)
    a0 = 1 + alpha
    b = ((1 - cw) / 2 / a0, (1 - cw) / a0, (1 - cw) / 2 / a0)
    a = (1.0, -2 * cw / a0, (1 - alpha) / a0)
    return biquad(x, b, a)


def highpass(x, f, q=0.707):
    w = 2 * np.pi * f / SR
    alpha = np.sin(w) / (2 * q)
    cw = np.cos(w)
    a0 = 1 + alpha
    b = ((1 + cw) / 2 / a0, -(1 + cw) / a0, (1 + cw) / 2 / a0)
    a = (1.0, -2 * cw / a0, (1 - alpha) / a0)
    return biquad(x, b, a)


def crackle_event(r, strength):
    """One tiny crackle: a sharp click followed by a few milliseconds of resonant noise."""
    n = int(SR * (0.004 + r.random() * 0.02))
    t = np.arange(n) / SR
    noise = r.standard_normal(n)
    body = bandpass(noise, 1200 + r.random() * 4200, 2.5 + r.random() * 4)
    env = np.exp(-t / (0.0015 + r.random() * 0.006))
    click = np.zeros(n)
    click[:3] = [1.0, -0.6, 0.3]
    return (body * 2.2 + click) * env * strength


def place(buf, ev, at):
    end = min(len(buf), at + len(ev))
    buf[at:end] += ev[:end - at]


def normalise(x, peak=0.9):
    m = np.max(np.abs(x)) + 1e-9
    return x / m * peak


def fade(x, fin=0.01, fout=0.05):
    n_in, n_out = int(SR * fin), int(SR * fout)
    x[:n_in] *= np.linspace(0, 1, n_in)
    x[-n_out:] *= np.linspace(1, 0, n_out)
    return x


def crackle(seed):
    r = np.random.default_rng(seed)
    dur = 0.7 + r.random() * 0.6
    buf = np.zeros(int(SR * dur))
    t = 0.02
    while t < dur - 0.05:
        burst = r.random() < 0.3
        for _ in range(r.integers(3, 9) if burst else 1):
            place(buf, crackle_event(r, 0.2 + r.random() * (1.0 if burst else 0.6)), int(SR * t))
            t += 0.004 + r.random() * 0.02
        t += 0.02 + r.exponential(0.09)
    # faint breathy hiss of burning gas underneath
    hiss = highpass(r.standard_normal(len(buf)), 2500) * 0.025
    return fade(normalise(buf + hiss, 0.85), 0.005, 0.08)


def pop(seed):
    r = np.random.default_rng(seed)
    dur = 0.55
    n = int(SR * dur)
    t = np.arange(n) / SR
    buf = np.zeros(n)
    # sharp crack
    crack = r.standard_normal(int(SR * 0.012)) * np.exp(-np.arange(int(SR * 0.012)) / (SR * 0.002))
    place(buf, highpass(crack, 900) * 1.4, int(SR * 0.01))
    # resonant wooden body: two decaying modes plus filtered noise
    for f, d, a in ((180 + r.random() * 120, 0.05, 0.5), (520 + r.random() * 400, 0.03, 0.35)):
        mode = np.sin(2 * np.pi * f * t) * np.exp(-t / d) * a
        buf[int(SR * 0.01):] += mode[:n - int(SR * 0.01)]
    buf += bandpass(r.standard_normal(n), 700 + r.random() * 600, 1.2) * np.exp(-t / 0.04) * 0.8
    # a few after-crackles
    for _ in range(r.integers(2, 6)):
        place(buf, crackle_event(r, 0.15 + r.random() * 0.25), int(SR * (0.06 + r.random() * 0.4)))
    return fade(normalise(buf, 0.95), 0.001, 0.1)


def hiss(seed):
    r = np.random.default_rng(seed)
    dur = 1.4 + r.random() * 0.6
    n = int(SR * dur)
    t = np.arange(n) / SR
    noise = highpass(r.standard_normal(n), 3000 + r.random() * 1500)
    sizzle = np.zeros(n)
    for _ in range(int(dur * 60)):
        place(sizzle, crackle_event(r, 0.05 + r.random() * 0.12), r.integers(0, n))
    mod = 0.6 + 0.4 * np.sin(2 * np.pi * (1.3 + r.random()) * t + r.random() * 6)
    env = np.minimum(1, t / 0.25) * np.minimum(1, (dur - t) / 0.5)
    return normalise((noise * 0.5 * mod + sizzle) * env, 0.7)


def roar(seed):
    r = np.random.default_rng(seed)
    dur = 4.0
    n = int(SR * dur)
    t = np.arange(n) / SR
    brown = np.cumsum(r.standard_normal(n))
    brown -= np.convolve(brown, np.ones(2048) / 2048, mode='same')  # remove drift
    low = lowpass(brown, 260 + r.random() * 120)
    air = bandpass(r.standard_normal(n), 900, 0.6) * 0.06
    breath = 0.7 + 0.3 * np.sin(2 * np.pi * (0.35 + r.random() * 0.3) * t + r.random() * 6) \
        + 0.15 * np.sin(2 * np.pi * (1.1 + r.random()) * t)
    env = np.sin(np.pi * t / dur) ** 0.8  # smooth fade in and out so overlapping copies never click
    return normalise((normalise(low) + air) * breath * env, 0.8)


def write_ogg(samples, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    pcm = (np.clip(samples, -1, 1) * 32767).astype('<i2')
    with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as tmp:
        wav_path = tmp.name
    with wave.open(wav_path, 'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', wav_path, '-c:a', 'libvorbis', '-q:a', '4',
                    '-map_metadata', '-1', path], check=True)
    os.unlink(wav_path)


def main():
    sets = {'crackle': (crackle, 6), 'pop': (pop, 4), 'hiss': (hiss, 3), 'roar': (roar, 3)}
    for name, (fn, count) in sets.items():
        for i in range(count):
            write_ogg(fn(1000 * len(name) + i), os.path.join(OUT, name, f'{name}{i + 1}.ogg'))
            print('wrote', name, i + 1)

    def entries(name, count, **extra):
        return [dict({'name': f'{MODID}:{name}/{name}{i + 1}'}, **extra) for i in range(count)]

    vanilla_crackle = [{'name': f'minecraft:block/campfire/crackle{i}', 'volume': 0.8, 'weight': 1} for i in range(1, 7)]
    write_json(os.path.join(ASSETS, 'sounds.json'), {
        'block.enhanced_campfire.crackle': {
            'subtitle': f'subtitles.{MODID}.crackle',
            'sounds': entries('crackle', 6, weight=2) + vanilla_crackle,
        },
        'block.enhanced_campfire.pop': {'subtitle': f'subtitles.{MODID}.pop', 'sounds': entries('pop', 4)},
        'block.enhanced_campfire.hiss': {
            'subtitle': f'subtitles.{MODID}.hiss',
            'sounds': entries('hiss', 3) + [{'name': 'minecraft:random/fizz', 'volume': 0.4}],
        },
        'block.enhanced_campfire.roar': {
            'subtitle': f'subtitles.{MODID}.roar',
            'sounds': entries('roar', 3, attenuation_distance=12) + [
                {'name': 'minecraft:fire/fire', 'volume': 0.7, 'attenuation_distance': 12}],
        },
    })


if __name__ == '__main__':
    main()
