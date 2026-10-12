#!/usr/bin/env python3
"""Procedurally synthesises the Enhanced Campfire sounds (original audio, no third-party samples).

Goal: a calm, warm campfire you can sit next to for a long time.  Every clip is
  * band-limited: steep low-pass (24 dB/oct) at ~3 kHz (hiss: 4.5 kHz) and a high-pass against rumble/DC,
  * free of hard clicks: every crackle has a 1.5-3 ms raised-cosine attack instead of a one-sample impulse,
  * peak-limited well below full scale and normalised to a quiet loudness,
  * faded in/out smoothly, so overlapping clips blend instead of stacking.

roar*    8 s "fire bed": a low, slowly breathing murmur with a few distant soft ticks (overlapping loop)
crackle* 3.5-4.5 s of sparse, soft wood crackles and settling thumps
pop*     a single gentle wood crack with a warm body (rare)
hiss*    quiet sizzle of rain drops on embers

Writes mono 44.1 kHz OGG Vorbis (ffmpeg + libvorbis) and sounds.json, and prints loudness statistics.
usage: gen_sounds.py [--stats-only DIR ...]
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


# ------------------------------------------------------------------------------------------- filters

def biquad(x, b, a):
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


def _coef(kind, f, q):
    w = 2 * np.pi * f / SR
    alpha = np.sin(w) / (2 * q)
    cw = np.cos(w)
    a0 = 1 + alpha
    if kind == 'lp':
        b = ((1 - cw) / 2, 1 - cw, (1 - cw) / 2)
    elif kind == 'hp':
        b = ((1 + cw) / 2, -(1 + cw), (1 + cw) / 2)
    else:  # band-pass, 0 dB peak
        b = (alpha, 0.0, -alpha)
    return tuple(v / a0 for v in b), (1.0, -2 * cw / a0, (1 - alpha) / a0)


def lowpass(x, f, q=0.707):
    return biquad(x, *_coef('lp', f, q))


def highpass(x, f, q=0.707):
    return biquad(x, *_coef('hp', f, q))


def bandpass(x, f, q):
    return biquad(x, *_coef('bp', f, q))


def lowpass24(x, f):
    """Two cascaded Butterworth sections: a steep 24 dB/octave roll-off."""
    return lowpass(lowpass(x, f, 0.541), f, 1.307)


# ------------------------------------------------------------------------------------------- helpers

def db(x):
    return 20 * np.log10(max(x, 1e-12))


def raised_cosine_env(n, attack, tau):
    t = np.arange(n) / SR
    att = np.clip(t / attack, 0, 1)
    return (0.5 - 0.5 * np.cos(np.pi * att)) * np.exp(-np.maximum(t - attack, 0) / tau)


def place(buf, ev, at):
    at = max(0, at)
    end = min(len(buf), at + len(ev))
    if end > at:
        buf[at:end] += ev[:end - at]


def fade(x, fin, fout):
    n_in, n_out = int(SR * fin), int(SR * fout)
    if n_in:
        x[:n_in] *= 0.5 - 0.5 * np.cos(np.linspace(0, np.pi, n_in))
    if n_out:
        x[-n_out:] *= 0.5 + 0.5 * np.cos(np.linspace(0, np.pi, n_out))
    return x


def finish(x, peak_db, rms_db=None, lp=3000.0, hp=70.0):
    """Band-limit, remove DC, then scale to the RMS target without exceeding the peak ceiling."""
    x = highpass(lowpass24(x, lp), hp)
    peak = np.max(np.abs(x)) + 1e-12
    gain = 10 ** (peak_db / 20) / peak
    if rms_db is not None:
        rms = np.sqrt(np.mean(x ** 2)) + 1e-12
        gain = min(gain, 10 ** (rms_db / 20) / rms)
    return x * gain


def brown(r, n):
    w = np.cumsum(r.standard_normal(n))
    w -= np.convolve(w, np.ones(4096) / 4096, mode='same')
    return w / (np.max(np.abs(w)) + 1e-9)


def soft_tick(r, strength, centre=None):
    """A soft wood crackle: band-limited noise with a raised-cosine attack - no single-sample click."""
    n = int(SR * 0.06)
    centre = centre or (600 + r.random() * 1300)
    burst = bandpass(r.standard_normal(n), centre, 1.0 + r.random() * 0.8)
    env = raised_cosine_env(n, 0.0015 + r.random() * 0.0015, 0.004 + r.random() * 0.008)
    return burst * env * strength


def thump(r, strength):
    """Low 'settling' knock of a log shifting in the fire."""
    n = int(SR * 0.2)
    body = lowpass(r.standard_normal(n), 220 + r.random() * 80, 0.9)
    return body * raised_cosine_env(n, 0.004, 0.03 + r.random() * 0.02) * strength * 3.0


# ------------------------------------------------------------------------------------------- clips

def bed(seed):
    r = np.random.default_rng(seed)
    dur = 8.0
    n = int(SR * dur)
    t = np.arange(n) / SR
    low = lowpass(brown(r, n), 380 + r.random() * 80, 0.8)
    warmth = bandpass(r.standard_normal(n), 170 + r.random() * 40, 0.7) * 0.25
    air = lowpass(bandpass(r.standard_normal(n), 700, 0.6), 1200) * 0.05
    breath = 0.75 + 0.18 * np.sin(2 * np.pi * (0.22 + r.random() * 0.15) * t + r.random() * 6) \
        + 0.07 * np.sin(2 * np.pi * (0.7 + r.random() * 0.3) * t + r.random() * 6)
    x = (low + warmth + air) * breath
    # a few distant, very soft ticks for life
    for _ in range(r.integers(4, 8)):
        place(x, soft_tick(r, 0.08 + r.random() * 0.06, 500 + r.random() * 600), int(r.random() * (n - SR * 0.1)))
    x = fade(x, 1.6, 1.6)
    return finish(x, peak_db=-12.0, rms_db=-27.0, lp=1800.0, hp=45.0)


def crackle(seed):
    r = np.random.default_rng(seed)
    dur = 3.5 + r.random() * 1.0
    n = int(SR * dur)
    x = lowpass(brown(r, n), 450) * 0.04  # faint continuity, so the clip never starts from silence
    t = 0.15 + r.random() * 0.2
    while t < dur - 0.25:
        kind = r.random()
        at = int(SR * t)
        if kind < 0.68:
            place(x, soft_tick(r, 0.25 + r.random() * 0.35), at)
        elif kind < 0.9:  # a little cluster of crackles
            for k in range(r.integers(2, 5)):
                place(x, soft_tick(r, 0.15 + r.random() * 0.25), at + int(SR * (k * (0.012 + r.random() * 0.02))))
        else:
            place(x, thump(r, 0.3 + r.random() * 0.2), at)
        t += 0.12 + r.exponential(0.32)
    x = fade(x, 0.08, 0.3)
    return finish(x, peak_db=-6.5, rms_db=-30.0, lp=2800.0)


def pop(seed):
    r = np.random.default_rng(seed)
    n = int(SR * 1.0)
    t = np.arange(n) / SR
    x = np.zeros(n)
    start = int(SR * 0.03)
    crack = bandpass(r.standard_normal(n - start), 800 + r.random() * 300, 0.8)
    x[start:] += crack * raised_cosine_env(n - start, 0.0025, 0.022) * 1.2
    body = np.sin(2 * np.pi * (150 + r.random() * 70) * t) * np.exp(-np.maximum(t - 0.03, 0) / 0.07) * (t > 0.03)
    x += body * 0.45
    for _ in range(r.integers(3, 6)):
        place(x, soft_tick(r, 0.12 + r.random() * 0.12), int(SR * (0.08 + r.random() * 0.6)))
    x = fade(x, 0.02, 0.25)
    return finish(x, peak_db=-9.0, lp=2600.0)


def hiss(seed):
    r = np.random.default_rng(seed)
    dur = 1.8 + r.random() * 0.6
    n = int(SR * dur)
    t = np.arange(n) / SR
    noise = bandpass(r.standard_normal(n), 2000 + r.random() * 600, 0.7) * 0.25
    for _ in range(int(dur * 30)):
        place(noise, soft_tick(r, 0.04 + r.random() * 0.05, 1500 + r.random() * 1200), r.integers(0, n))
    env = np.minimum(1, t / 0.35) * np.minimum(1, (dur - t) / 0.6)
    return finish(noise * env, peak_db=-16.0, rms_db=-32.0, lp=4500.0, hp=200.0)


# ------------------------------------------------------------------------------------------- io / stats

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


def read_audio(path):
    raw = subprocess.run(['ffmpeg', '-loglevel', 'error', '-i', path, '-f', 's16le', '-ac', '1', '-ar', str(SR), '-'],
                         check=True, capture_output=True).stdout
    return np.frombuffer(raw, dtype='<i2').astype(np.float64) / 32768.0


def stats(x):
    """peak dBFS, RMS dBFS, loudest 50 ms window dBFS, share of energy above 4 kHz, spectral centroid Hz."""
    peak = db(np.max(np.abs(x)))
    rms = db(np.sqrt(np.mean(x ** 2)))
    win = int(SR * 0.05)
    frames = x[:len(x) // win * win].reshape(-1, win)
    loudest = db(np.max(np.sqrt(np.mean(frames ** 2, axis=1))))
    spec = np.abs(np.fft.rfft(x)) ** 2
    freqs = np.fft.rfftfreq(len(x), 1 / SR)
    high = spec[freqs > 4000].sum() / (spec.sum() + 1e-12) * 100
    centroid = (spec * freqs).sum() / (spec.sum() + 1e-12)
    return peak, rms, loudest, high, centroid


def print_stats(root, title):
    print(f'\n{title}')
    print(f'{"file":28} {"peak dBFS":>9} {"RMS dBFS":>9} {"max50ms":>8} {">4kHz %":>8} {"centroid":>9}')
    for sub in sorted(os.listdir(root)):
        d = os.path.join(root, sub)
        if not os.path.isdir(d):
            continue
        for f in sorted(os.listdir(d)):
            if f.endswith('.ogg'):
                p, r_, l, h, c = stats(read_audio(os.path.join(d, f)))
                print(f'{sub + "/" + f:28} {p:9.1f} {r_:9.1f} {l:8.1f} {h:8.1f} {c:8.0f}Hz')


def main():
    if len(sys.argv) > 2 and sys.argv[1] == '--stats-only':
        for root in sys.argv[2:]:
            print_stats(root, root)
        return
    sets = {'roar': (bed, 4), 'crackle': (crackle, 8), 'pop': (pop, 3), 'hiss': (hiss, 3)}
    for name in sets:
        d = os.path.join(OUT, name)
        if os.path.isdir(d):
            for f in os.listdir(d):
                os.remove(os.path.join(d, f))
    for name, (fn, count) in sets.items():
        for i in range(count):
            write_ogg(fn(5000 + 100 * len(name) + i), os.path.join(OUT, name, f'{name}{i + 1}.ogg'))

    def entries(name, count, **extra):
        return [dict({'name': f'{MODID}:{name}/{name}{i + 1}'}, **extra) for i in range(count)]

    write_json(os.path.join(ASSETS, 'sounds.json'), {
        'block.enhanced_campfire.crackle': {'subtitle': f'subtitles.{MODID}.crackle',
                                            'sounds': entries('crackle', 8, attenuation_distance=14)},
        'block.enhanced_campfire.pop': {'subtitle': f'subtitles.{MODID}.pop',
                                        'sounds': entries('pop', 3, attenuation_distance=16)},
        'block.enhanced_campfire.hiss': {'subtitle': f'subtitles.{MODID}.hiss',
                                         'sounds': entries('hiss', 3, attenuation_distance=10)},
        'block.enhanced_campfire.roar': {'subtitle': f'subtitles.{MODID}.roar',
                                         'sounds': entries('roar', 4, attenuation_distance=12)},
    })
    print_stats(OUT, 'new sounds')


if __name__ == '__main__':
    main()
