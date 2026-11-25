"""Generates the blast and contrail textures of Bomb Run (deterministic, no inputs).

    python tools/gen_fx_textures.py

fire.png     8 frames of 32x32 in a row: a fire puff from a white-hot core to orange flame to red embers and
             dark smoke, ragged at the edge. Pixel art, nearest filtering, like vanilla's explosion sprites.
contrail.png 8x32: soft white across the trail (u along it, v across it), alpha falling off to the edges.
"""
import os

import numpy as np
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "farmstead_bombrun", "textures", "fx")
FRAMES, S = 8, 32


def value_noise(seed, size, cells):
    rng = np.random.default_rng(seed)
    grid = rng.random((cells + 1, cells + 1))
    xs = np.linspace(0, cells, size, endpoint=False)
    x0 = xs.astype(int)
    f = xs - x0
    f = f * f * (3 - 2 * f)
    a = grid[x0][:, x0]
    b = grid[x0][:, x0 + 1]
    c = grid[x0 + 1][:, x0]
    d = grid[x0 + 1][:, x0 + 1]
    fx, fy = f[None, :], f[:, None]
    return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy


def ramp(heat):
    """heat 0..1 -> rgb: dark smoke, red, orange, yellow, white."""
    stops = [(0.0, (40, 32, 30)), (0.25, (120, 30, 12)), (0.45, (220, 70, 18)), (0.65, (255, 150, 30)),
             (0.82, (255, 220, 90)), (1.0, (255, 252, 230))]
    out = np.zeros(heat.shape + (3,))
    for (h0, c0), (h1, c1) in zip(stops, stops[1:]):
        m = (heat >= h0) & (heat <= h1)
        t = ((heat - h0) / (h1 - h0))[..., None]
        out[m] = (np.array(c0) * (1 - t) + np.array(c1) * t)[m]
    return out


def fire():
    sheet = np.zeros((S, S * FRAMES, 4), dtype=np.uint8)
    yy, xx = np.mgrid[0:S, 0:S]
    cx = cy = (S - 1) / 2
    r = np.hypot(xx - cx, yy - cy) / (S / 2)
    for k in range(FRAMES):
        age = k / (FRAMES - 1)
        n = 0.6 * value_noise(11 + k, S, 4) + 0.4 * value_noise(101 + k, S, 8)
        # the puff grows and frays with age
        edge = 0.72 + 0.22 * age + (n - 0.5) * (0.35 + 0.4 * age)
        body = np.clip(1 - r / edge, 0, 1)
        heat = np.clip(body * (1.25 - 0.95 * age) + (n - 0.5) * 0.35, 0, 1)
        rgb = ramp(heat)
        alpha = np.clip(body * 3.0, 0, 1) * (1 - 0.55 * age ** 1.5)
        alpha = np.where(alpha > 0.12, alpha, 0)
        # posterise a little: pixel art, not a gradient
        rgb = (np.round(rgb / 12) * 12).clip(0, 255)
        alpha = (np.round(alpha * 8) / 8).clip(0, 1)
        sheet[:, k * S:(k + 1) * S, :3] = rgb.astype(np.uint8)
        sheet[:, k * S:(k + 1) * S, 3] = (alpha * 255).astype(np.uint8)
    Image.fromarray(sheet, "RGBA").save(os.path.join(OUT, "fire.png"))


def contrail():
    w, h = 8, 32
    v = (np.arange(h) + 0.5) / h * 2 - 1
    a = np.clip(1 - np.abs(v) ** 1.6, 0, 1)
    img = np.zeros((h, w, 4), dtype=np.uint8)
    img[..., :3] = 255
    img[..., 3] = (a[:, None] * 255).astype(np.uint8)
    Image.fromarray(img, "RGBA").save(os.path.join(OUT, "contrail.png"))


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    fire()
    contrail()
