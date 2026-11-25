import sys, numpy as np
from PIL import Image, ImageFilter
W, H = 1280, 720
out = sys.argv[1]
rng = np.random.default_rng(7)
yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
u, v = xx / W, yy / H

def grad(stops):
    """vertical gradient: [(pos, (r,g,b)), ...]"""
    img = np.zeros((H, W, 3), np.float32)
    ps = [p for p, _ in stops]
    for c in range(3):
        img[:, :, c] = np.interp(v, ps, [col[c] for _, col in stops])
    return img

def blend(img, mask, color, alpha=1.0):
    m = (mask * alpha)[..., None]
    return img * (1 - m) + np.array(color, np.float32) * m

def ridge(base, amp, seed, freq=1.0):
    r = np.random.default_rng(seed)
    y = np.full(W, base, np.float32)
    for k in range(1, 7):
        y += amp / k * np.sin(2 * np.pi * (freq * k * 0.7) * (xx[0] / W) + r.uniform(0, 6.28))
    return y

def mountains(img, layers):
    for base, amp, seed, color in layers:
        top = ridge(base * H, amp * H, seed)
        mask = (yy >= top[None, :]).astype(np.float32)
        mask = np.array(Image.fromarray((mask * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(0.8))) / 255.0
        img = blend(img, mask, color)
    return img

def stars(img, n, bright=1.0, maxy=0.7):
    for _ in range(n):
        x, y = rng.integers(0, W), rng.integers(0, int(H * maxy))
        b = rng.uniform(0.4, 1.0) * bright
        img[y, x] = np.minimum(255, img[y, x] + 255 * b)
        if rng.random() < 0.12:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if 0 <= x + dx < W and 0 <= y + dy < H:
                    img[y + dy, x + dx] = np.minimum(255, img[y + dy, x + dx] + 120 * b)
    return img

def glow(img, cx, cy, r, color, strength=1.0):
    d = np.sqrt((xx - cx * W) ** 2 + (yy - cy * H) ** 2)
    m = np.clip(1 - d / (r * W), 0, 1) ** 2 * strength
    return img + m[..., None] * np.array(color, np.float32)

def disc(img, cx, cy, r, color):
    d = np.sqrt((xx - cx * W) ** 2 + (yy - cy * H) ** 2)
    m = np.clip((r * W - d) / 1.5, 0, 1)
    return blend(img, m, color)

def save(img, name):
    img = img + rng.normal(0, 1.2, img.shape)
    Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)).save(f"{out}/{name}.png", optimize=True)

# sunset
im = grad([(0, (38, 30, 84)), (0.45, (196, 74, 110)), (0.7, (255, 150, 80)), (1, (255, 205, 120))])
im = glow(im, 0.5, 0.66, 0.5, (255, 170, 80), 0.6); im = disc(im, 0.5, 0.64, 0.055, (255, 235, 190))
im = mountains(im, [(0.70, 0.04, 1, (92, 44, 90)), (0.78, 0.05, 2, (58, 28, 70)), (0.88, 0.04, 3, (28, 16, 40))])
save(im, "sunset")

# night
im = grad([(0, (4, 6, 22)), (0.6, (14, 22, 58)), (1, (40, 48, 92))])
im = stars(im, 900); im = disc(im, 0.78, 0.2, 0.035, (240, 240, 225)); im = glow(im, 0.78, 0.2, 0.18, (120, 130, 170), 0.5)
im = mountains(im, [(0.74, 0.05, 11, (18, 24, 52)), (0.84, 0.05, 12, (9, 12, 30)), (0.93, 0.03, 13, (4, 5, 14))])
save(im, "night")

# aurora
im = grad([(0, (2, 6, 18)), (0.7, (8, 20, 40)), (1, (16, 36, 56))])
for i, (col, off, ph) in enumerate([((60, 255, 150), 0.30, 0.0), ((120, 90, 255), 0.22, 1.7), ((40, 220, 220), 0.38, 3.1)]):
    cy = off + 0.07 * np.sin(2 * np.pi * (1.3 * u) + ph) + 0.03 * np.sin(2 * np.pi * (3.1 * u) + ph * 2)
    band = np.exp(-((v - cy) / 0.10) ** 2) * (0.55 + 0.45 * np.sin(2 * np.pi * (7 * u) + ph))
    im = im + band[..., None] * np.array(col, np.float32) * 0.55
im = stars(im, 600, 0.8, 0.5)
im = mountains(im, [(0.86, 0.04, 21, (6, 14, 24)), (0.94, 0.03, 22, (2, 6, 12))])
save(im, "aurora")

# ocean
im = grad([(0, (120, 200, 255)), (0.5, (190, 235, 255)), (0.5001, (20, 120, 190)), (1, (6, 40, 90))])
im = disc(im, 0.72, 0.25, 0.05, (255, 250, 220)); im = glow(im, 0.72, 0.25, 0.25, (255, 250, 200), 0.4)
for k in range(60):
    y0 = 0.52 + (k / 60) ** 1.6 * 0.46
    amp = 0.002 + 0.01 * (k / 60)
    ys = y0 * H + amp * H * np.sin(2 * np.pi * (3 + k * 0.13) * u[0] + k)
    line = np.exp(-((yy - ys[None, :]) / (1.0 + 2 * k / 60)) ** 2)
    im = blend(im, line, (230, 248, 255), 0.35)
save(im, "ocean")

# forest
im = grad([(0, (170, 215, 170)), (0.6, (235, 240, 200)), (1, (250, 245, 215))])
im = glow(im, 0.3, 0.3, 0.4, (255, 255, 210), 0.5)
im = mountains(im, [(0.55, 0.05, 31, (140, 190, 150)), (0.65, 0.06, 32, (92, 150, 108)), (0.76, 0.06, 33, (52, 110, 70)), (0.88, 0.05, 34, (26, 70, 44))])
save(im, "forest")

# nether
im = grad([(0, (40, 4, 6)), (0.55, (120, 18, 10)), (1, (255, 110, 20))])
im = glow(im, 0.5, 1.0, 0.7, (255, 140, 30), 0.8)
im = mountains(im, [(0.72, 0.07, 41, (70, 10, 10)), (0.84, 0.06, 42, (40, 6, 8)), (0.95, 0.04, 43, (18, 3, 4))])
for _ in range(220):
    x, y = rng.integers(0, W), rng.integers(0, H)
    im = glow(im, x / W, y / H, 0.004, (255, 150, 40), 0.8)
save(im, "nether")

# end
im = grad([(0, (10, 4, 22)), (0.6, (28, 14, 52)), (1, (50, 36, 84))])
im = stars(im, 700, 0.7, 0.95); im = glow(im, 0.5, 0.55, 0.45, (110, 70, 170), 0.5)
im = mountains(im, [(0.80, 0.07, 51, (190, 188, 150)), (0.90, 0.05, 52, (128, 126, 100))])
save(im, "end")

# desert
im = grad([(0, (255, 190, 120)), (0.55, (255, 225, 170)), (1, (255, 240, 200))])
im = disc(im, 0.25, 0.28, 0.06, (255, 250, 230)); im = glow(im, 0.25, 0.28, 0.35, (255, 240, 200), 0.5)
im = mountains(im, [(0.68, 0.03, 61, (232, 170, 100)), (0.78, 0.045, 62, (214, 144, 78)), (0.90, 0.04, 63, (176, 108, 58))])
save(im, "desert")
