from __future__ import annotations

import numpy as np

LABELS = ("local", "empate", "visitante")


def generar_dataset(n: int = 2000, seed: int = 42) -> tuple[np.ndarray, np.ndarray]:
    rng = np.random.default_rng(seed)
    gf_l = rng.integers(0, 30, n)
    gc_l = rng.integers(0, 30, n)
    gf_v = rng.integers(0, 30, n)
    gc_v = rng.integers(0, 30, n)
    X = np.column_stack([gf_l, gc_l, gf_v, gc_v]).astype(float)

    margen = (gf_l - gc_l) - (gf_v - gc_v) + rng.normal(0, 3, n)
    y = np.where(margen > 2, 0, np.where(margen < -2, 2, 1))
    return X, y


def generar_rendimiento(n: int = 2000, seed: int = 42) -> tuple[np.ndarray, np.ndarray]:
    rng = np.random.default_rng(seed)
    goles = rng.integers(0, 12, n)
    X = goles.reshape(-1, 1).astype(float)
    y = np.clip(5 + 0.7 * goles + rng.normal(0, 0.6, n), 1, 10)
    return X, y
