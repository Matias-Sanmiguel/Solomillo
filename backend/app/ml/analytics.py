from __future__ import annotations

import joblib
import numpy as np
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.ml.predictor import _modelo_activo
from app.rankings.models import Posicion


def proyectar_posiciones(torneo_id: int, db: Session) -> list[dict]:
    modelo = _modelo_activo(db, "resultado_partido")
    clf = joblib.load(modelo.ruta)
    filas = db.scalars(select(Posicion).where(Posicion.torneo_id == torneo_id)).all()
    if not filas:
        return []

    avg_gf = float(np.mean([f.goles_favor for f in filas]))
    avg_gc = float(np.mean([f.goles_contra for f in filas]))
    idx_local = list(clf.classes_).index(0)
    idx_empate = list(clf.classes_).index(1)

    proyeccion = []
    for f in filas:
        probs = clf.predict_proba(np.array([[f.goles_favor, f.goles_contra, avg_gf, avg_gc]]))[0]
        xpts = 3 * probs[idx_local] + 1 * probs[idx_empate]
        proyeccion.append(
            {"equipo_id": f.equipo_id, "puntos_actuales": f.puntos, "puntos_esperados": round(float(xpts), 3)}
        )

    proyeccion.sort(key=lambda p: p["puntos_esperados"], reverse=True)
    for i, p in enumerate(proyeccion, 1):
        p["posicion_proyectada"] = i
    return proyeccion


def tendencias(torneo_id: int, db: Session) -> list[dict]:
    filas = db.scalars(select(Posicion).where(Posicion.torneo_id == torneo_id)).all()
    out = []
    for f in filas:
        dif = f.goles_favor - f.goles_contra
        if dif >= 2:
            estado = "ofensiva fuerte"
        elif dif <= -2:
            estado = "defensiva débil"
        else:
            estado = "estable"
        out.append({"equipo_id": f.equipo_id, "diferencia": dif, "tendencia": estado})
    out.sort(key=lambda x: x["diferencia"], reverse=True)
    return out
