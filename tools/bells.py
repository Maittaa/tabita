"""
Генератор звуков колокольчиков Tabita (5.68). Модальный синтез: сумма затухающих синусоид
с негармоническими обертонами, как у колоколов и металлических пластин. Результат — res/raw/bell_*.ogg.
Запуск: python3 tools/bells.py <папка res/raw>
"""
import sys, os, subprocess, tempfile
import numpy as np
from scipy.io import wavfile

SR = 44100
rng = np.random.default_rng(7)

def strike(f0, ratios, amps, decays, dur, beat=0.0, attack=0.003):
    t = np.arange(int(SR * dur)) / SR
    y = np.zeros_like(t)
    for r, a, d in zip(ratios, amps, decays):
        f = f0 * r
        if f > SR / 2 - 500: continue
        env = np.exp(-t / d)
        if beat:  # пара близких мод — «дыхание» чаши
            y += a * env * 0.5 * (np.sin(2*np.pi*f*t) + np.sin(2*np.pi*(f+beat*r**0.5)*t + 0.7))
        else:
            y += a * env * np.sin(2*np.pi*f*t + rng.uniform(0, 2*np.pi))
    att = np.minimum(1, t / attack)
    # плавное затухание последней трети — без щелчка там, где удар обрезается
    k = int(len(t) * 0.35); tail = np.ones_like(t); tail[-k:] = np.cos(np.linspace(0, np.pi / 2, k)) ** 2
    return y * att * tail

def place(total, parts):
    out = np.zeros(int(SR * total))
    for at, sig, g in parts:
        i = int(SR * at); n = min(len(sig), len(out) - i)
        out[i:i+n] += g * sig[:n]
    return out

def finish(y, fade=0.4):
    n = int(SR * fade); y[-n:] *= np.linspace(1, 0, n) ** 2
    return y / np.max(np.abs(y)) * 0.89  # пик ≈ −1 дБ

BAR = [1.0, 2.756, 5.404, 8.933]           # свободная пластина / трубка
def note(n): return 440.0 * 2 ** ((n - 69) / 12)

sounds = {}
# 1. Колокольчик — маленький ручной колокол, два удара
hb = lambda: strike(note(91), [1, 2.32, 3.0, 4.16, 5.43], [1, .5, .35, .2, .12], [1.1, .7, .5, .35, .25], 2.2)
sounds['bell_hand'] = finish(place(2.6, [(0, hb(), 1), (0.32, hb(), .75)]))
# 2. Поющая чаша — низкий долгий звук с биениями
sounds['bell_bowl'] = finish(strike(note(60), [1, 2.71, 5.15, 8.17], [1, .55, .25, .1], [3.2, 2.2, 1.2, .7], 4.5, beat=1.3, attack=0.006), fade=0.8)
# 3. Ветряные колокольчики — пентатоника, трубки
penta = [84, 86, 88, 91, 93, 96]
hits = []; at = 0.0
for i in range(7):
    n = penta[rng.integers(len(penta))]
    hits.append((at, strike(note(n), BAR, [1, .35, .15, .06], [1.6, .8, .4, .25], 2.0), rng.uniform(.55, 1)))
    at += rng.uniform(.12, .3)
sounds['bell_chimes'] = finish(place(3.2, hits), fade=0.6)
# 4. Динь-дон — вибрафон, две ноты вниз
vib = lambda n: strike(note(n), [1, 3.98, 9.9], [1, .25, .06], [1.4, .5, .2], 2.0) * (1 + .25*np.sin(2*np.pi*5.5*np.arange(int(SR*2.0))/SR))
sounds['bell_dingdong'] = finish(place(2.8, [(0, vib(76), 1), (0.45, vib(72), .95)]))
# 5. Глокеншпиль — восходящее арпеджио
glk = lambda n: strike(note(n), BAR, [1, .3, .1, .04], [.9, .4, .2, .1], 1.6)
sounds['bell_glock'] = finish(place(2.3, [(0, glk(84), .85), (.13, glk(88), .85), (.26, glk(91), .9), (.39, glk(96), 1)]))

out = sys.argv[1]; os.makedirs(out, exist_ok=True)
for k, y in sounds.items():
    with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as f: tmp = f.name
    wavfile.write(tmp, SR, (y * 32767).astype(np.int16))
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', tmp, '-c:a', 'libvorbis', '-q:a', '4', os.path.join(out, k + '.ogg')], check=True)
    os.unlink(tmp)
    print(k, f'{len(y)/SR:.1f} с', os.path.getsize(os.path.join(out, k + '.ogg')), 'байт')
