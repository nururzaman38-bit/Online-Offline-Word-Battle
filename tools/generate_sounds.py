#!/usr/bin/env python3
"""Generates the Word Battle sound effects into app/src/main/res/raw.

The whole soundtrack is synthesised offline with the Python standard library so the
repository stays free of third-party audio assets and licences. Run it from the repo root:

    python3 tools/generate_sounds.py
"""
from __future__ import annotations

import math
import os
import struct
import wave

SAMPLE_RATE = 22050
OUT_DIR = os.path.join("app", "src", "main", "res", "raw")

# ---------------------------------------------------------------------------
# tiny synth helpers
# ---------------------------------------------------------------------------


def note(name: str) -> float:
    """Frequency of a scientific-pitch note such as 'A4' or 'C#5'."""
    semitones = {"C": 0, "C#": 1, "D": 2, "D#": 3, "E": 4, "F": 5,
                 "F#": 6, "G": 7, "G#": 8, "A": 9, "A#": 10, "B": 11}
    pitch, octave = name[:-1], int(name[-1])
    index = semitones[pitch] + 12 * (octave + 1)
    return 440.0 * (2 ** ((index - 69) / 12.0))


def silence(seconds: float) -> list[float]:
    return [0.0] * int(SAMPLE_RATE * seconds)


def mix_into(buffer: list[float], start: float, samples: list[float]) -> None:
    offset = int(start * SAMPLE_RATE)
    needed = offset + len(samples) - len(buffer)
    if needed > 0:
        buffer.extend([0.0] * needed)
    for i, value in enumerate(samples):
        buffer[offset + i] += value


def envelope(length: int, attack: float, decay: float, sustain: float, release: float) -> list[float]:
    attack_n = max(1, int(length * attack))
    decay_n = max(1, int(length * decay))
    release_n = max(1, int(length * release))
    hold_n = max(0, length - attack_n - decay_n - release_n)
    out: list[float] = []
    out += [i / attack_n for i in range(attack_n)]
    out += [1.0 - (1.0 - sustain) * (i / decay_n) for i in range(decay_n)]
    out += [sustain] * hold_n
    out += [sustain * (1.0 - i / release_n) for i in range(release_n)]
    return out[:length] + [0.0] * max(0, length - len(out))


def tone(freq: float, seconds: float, gain: float = 0.3, wave_shape: str = "sine",
         attack: float = 0.02, decay: float = 0.15, sustain: float = 0.75,
         release: float = 0.35, detune: float = 0.0, glide: float = 0.0) -> list[float]:
    length = int(SAMPLE_RATE * seconds)
    env = envelope(length, attack, decay, sustain, release)
    out = []
    phase = 0.0
    phase2 = 0.0
    for i in range(length):
        progress = i / max(1, length - 1)
        f = freq * (2 ** (glide * progress / 12.0))
        phase += 2 * math.pi * f / SAMPLE_RATE
        phase2 += 2 * math.pi * (f * (1 + detune)) / SAMPLE_RATE
        if wave_shape == "sine":
            value = math.sin(phase)
            if detune:
                value = 0.6 * value + 0.4 * math.sin(phase2)
        elif wave_shape == "triangle":
            value = 2.0 / math.pi * math.asin(math.sin(phase))
        elif wave_shape == "square":
            value = 1.0 if math.sin(phase) >= 0 else -1.0
            value *= 0.55
        elif wave_shape == "soft_square":
            # band-limited-ish: fundamental plus a couple of odd harmonics
            value = (math.sin(phase) + math.sin(3 * phase) / 3.0 + math.sin(5 * phase) / 5.0) * 0.7
        else:
            raise ValueError(wave_shape)
        out.append(value * env[i] * gain)
    return out


def noise_burst(seconds: float, gain: float = 0.2, decay_power: float = 6.0) -> list[float]:
    length = int(SAMPLE_RATE * seconds)
    out = []
    state = 12345
    for i in range(length):
        state = (1103515245 * state + 12345) & 0x7FFFFFFF
        value = (state / 0x3FFFFFFF) - 1.0
        out.append(value * gain * ((1.0 - i / length) ** decay_power))
    return out


def low_pass(samples: list[float], alpha: float = 0.35) -> list[float]:
    out = []
    previous = 0.0
    for value in samples:
        previous += alpha * (value - previous)
        out.append(previous)
    return out


def normalize(samples: list[float], peak: float = 0.86) -> list[float]:
    top = max((abs(v) for v in samples), default=0.0)
    if top == 0:
        return samples
    factor = peak / top
    return [v * factor for v in samples]


def fade_edges(samples: list[float], seconds: float = 0.01) -> list[float]:
    n = min(int(SAMPLE_RATE * seconds), len(samples) // 2)
    if n <= 0:
        return samples
    out = list(samples)
    for i in range(n):
        factor = i / n
        out[i] *= factor
        out[-1 - i] *= factor
    return out


def write_wav(name: str, samples: list[float]) -> None:
    os.makedirs(OUT_DIR, exist_ok=True)
    path = os.path.join(OUT_DIR, name)
    data = b"".join(
        struct.pack("<h", max(-32767, min(32767, int(v * 32767)))) for v in samples
    )
    with wave.open(path, "wb") as handle:
        handle.setnchannels(1)
        handle.setsampwidth(2)
        handle.setframerate(SAMPLE_RATE)
        handle.writeframes(data)
    print(f"{path}  {len(samples) / SAMPLE_RATE:.2f}s  {len(data) // 1024} KiB")


# ---------------------------------------------------------------------------
# the sounds
# ---------------------------------------------------------------------------


def sfx_letter_place() -> list[float]:
    """Short wooden 'clack' with a bright ping: a tile landing on the board."""
    buffer: list[float] = []
    mix_into(buffer, 0.0, low_pass(noise_burst(0.07, gain=0.55, decay_power=9.0), 0.55))
    mix_into(buffer, 0.0, tone(note("A5"), 0.13, gain=0.40, wave_shape="triangle",
                               attack=0.01, decay=0.30, sustain=0.25, release=0.60))
    mix_into(buffer, 0.01, tone(note("E6"), 0.10, gain=0.22, wave_shape="sine",
                                attack=0.01, decay=0.30, sustain=0.15, release=0.65))
    return fade_edges(normalize(buffer, 0.80))


def sfx_word_scored() -> list[float]:
    """Rising three-note sparkle when the placement completes a real word."""
    buffer: list[float] = []
    for index, name in enumerate(["E5", "A5", "C#6"]):
        mix_into(buffer, index * 0.075,
                 tone(note(name), 0.24, gain=0.30, wave_shape="sine", detune=0.004,
                      attack=0.01, decay=0.25, sustain=0.55, release=0.55))
    mix_into(buffer, 0.15, tone(note("E6"), 0.30, gain=0.14, wave_shape="triangle",
                                attack=0.02, decay=0.3, sustain=0.4, release=0.6))
    return fade_edges(normalize(buffer, 0.82))


def sfx_timer_tick() -> list[float]:
    """Dry clock tick used for the last seconds of a turn."""
    buffer: list[float] = []
    mix_into(buffer, 0.0, tone(note("B5"), 0.055, gain=0.5, wave_shape="sine",
                               attack=0.02, decay=0.30, sustain=0.20, release=0.60))
    mix_into(buffer, 0.0, low_pass(noise_burst(0.03, gain=0.25, decay_power=12.0), 0.7))
    return fade_edges(normalize(buffer, 0.70), 0.004)


def sfx_timer_warning() -> list[float]:
    """Urgent double beep for the final five seconds."""
    buffer: list[float] = []
    for index in range(2):
        mix_into(buffer, index * 0.11,
                 tone(note("F#6"), 0.09, gain=0.42, wave_shape="soft_square",
                      attack=0.05, decay=0.25, sustain=0.5, release=0.5))
    return fade_edges(normalize(buffer, 0.78), 0.005)


def sfx_victory() -> list[float]:
    """Bright major fanfare for the winner."""
    buffer: list[float] = silence(0.02)
    melody = [
        ("C5", 0.00, 0.20), ("E5", 0.16, 0.20), ("G5", 0.32, 0.20),
        ("C6", 0.48, 0.55), ("G5", 0.98, 0.18), ("C6", 1.14, 0.24),
        ("E6", 1.36, 0.85),
    ]
    for name, start, length in melody:
        mix_into(buffer, start, tone(note(name), length, gain=0.26, wave_shape="soft_square",
                                     detune=0.005, attack=0.02, decay=0.18, sustain=0.7, release=0.35))
        mix_into(buffer, start, tone(note(name), length, gain=0.13, wave_shape="sine",
                                     attack=0.03, decay=0.2, sustain=0.6, release=0.4))
    bass = [("C3", 0.00, 0.46), ("G3", 0.48, 0.46), ("C4", 0.98, 0.36), ("C4", 1.36, 0.85)]
    for name, start, length in bass:
        mix_into(buffer, start, tone(note(name), length, gain=0.24, wave_shape="triangle",
                                     attack=0.01, decay=0.2, sustain=0.7, release=0.4))
    # sparkle tail
    for index, name in enumerate(["G6", "C7", "E7"]):
        mix_into(buffer, 1.45 + index * 0.09,
                 tone(note(name), 0.5, gain=0.10, wave_shape="sine",
                      attack=0.01, decay=0.2, sustain=0.4, release=0.7))
    return fade_edges(normalize(buffer, 0.88), 0.02)


def sfx_defeat() -> list[float]:
    """Gentle descending phrase when the battle ends without a win."""
    buffer: list[float] = []
    for index, name in enumerate(["G5", "E5", "C5", "G4"]):
        mix_into(buffer, index * 0.20,
                 tone(note(name), 0.42, gain=0.24, wave_shape="triangle",
                      attack=0.03, decay=0.25, sustain=0.55, release=0.5))
    mix_into(buffer, 0.60, tone(note("C3"), 0.7, gain=0.18, wave_shape="sine",
                                attack=0.05, decay=0.3, sustain=0.5, release=0.5))
    return fade_edges(normalize(buffer, 0.75), 0.02)


def music_theme() -> list[float]:
    """Looping, low-key battle theme: 8 bars of arpeggio, pad and soft bass."""
    bpm = 96.0
    beat = 60.0 / bpm
    bar = beat * 4
    bars = 8
    total = bar * bars
    buffer: list[float] = silence(total)

    progression = [
        ("A3", ["A4", "C5", "E5", "C5"]),
        ("F3", ["F4", "A4", "C5", "A4"]),
        ("C3", ["C4", "E4", "G4", "E4"]),
        ("G3", ["G3", "B3", "D4", "B3"]),
    ]

    for bar_index in range(bars):
        root_name, arp = progression[bar_index % len(progression)]
        bar_start = bar_index * bar

        # bass pulse on every beat
        for beat_index in range(4):
            gain = 0.20 if beat_index % 2 == 0 else 0.13
            mix_into(buffer, bar_start + beat_index * beat,
                     tone(note(root_name), beat * 0.85, gain=gain, wave_shape="triangle",
                          attack=0.03, decay=0.25, sustain=0.55, release=0.45))

        # eighth-note arpeggio
        for step in range(8):
            name = arp[step % len(arp)]
            if step >= 4:
                name = name[:-1] + str(int(name[-1]))
            mix_into(buffer, bar_start + step * (beat / 2),
                     tone(note(name), beat * 0.46, gain=0.115, wave_shape="sine", detune=0.006,
                          attack=0.06, decay=0.25, sustain=0.5, release=0.5))

        # airy pad holding the chord
        for name in arp[:3]:
            mix_into(buffer, bar_start,
                     tone(note(name), bar * 0.98, gain=0.045, wave_shape="sine", detune=0.01,
                          attack=0.25, decay=0.2, sustain=0.8, release=0.35))

        # light percussion: soft hats on the off-beats
        for step in range(8):
            if step % 2 == 1:
                mix_into(buffer, bar_start + step * (beat / 2),
                         noise_burst(0.045, gain=0.055, decay_power=10.0))

    faded = low_pass(buffer, 0.55)
    # A loop must not click: cross-fade the last bar into the first.
    return fade_edges(normalize(faded, 0.55), 0.06)


def main() -> None:
    write_wav("snd_letter_place.wav", sfx_letter_place())
    write_wav("snd_word_scored.wav", sfx_word_scored())
    write_wav("snd_timer_tick.wav", sfx_timer_tick())
    write_wav("snd_timer_warning.wav", sfx_timer_warning())
    write_wav("snd_victory.wav", sfx_victory())
    write_wav("snd_defeat.wav", sfx_defeat())
    write_wav("music_theme.wav", music_theme())


if __name__ == "__main__":
    main()
