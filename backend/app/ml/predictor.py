from __future__ import annotations

import joblib
import numpy as np
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.domain.models import Partido
from app.ml.dataset import LABELS
from app.ml.models import ModeloPredictivo
from app.rankings.models import Posicion
from app.stats.models import EstadisticaJugador


class SinModeloError(Exception):
    pass


def _modelo_activo(db: Session, nombre: str) -> ModeloPredictivo:
    modelo = db.scalar(
        select(ModeloPredictivo).where(
            ModeloPredictivo.nombre == nombre, ModeloPredictivo.activo.is_(True)
        )
    )
    if modelo is None:
        raise SinModeloError(f"No hay modelo activo '{nombre}'. Entrenar primero.")
    return modelo


def _gf_gc(db: Session, torneo_id: int, equipo_id: int) -> tuple[int, int]:
    pos = db.scalar(
        select(Posicion).where(Posicion.torneo_id == torneo_id, Posicion.equipo_id == equipo_id)
    )
    return (pos.goles_favor, pos.goles_contra) if pos else (0, 0)


def predecir(partido_id: int, db: Session) -> dict:
    modelo = _modelo_activo(db, "resultado_partido")
    partido = db.get(Partido, partido_id)
    if partido is None:
        raise ValueError("Partido inexistente")

    gf_l, gc_l = _gf_gc(db, partido.torneo_id, partido.equipo_local_id)
    gf_v, gc_v = _gf_gc(db, partido.torneo_id, partido.equipo_visitante_id)

    clf = joblib.load(modelo.ruta)
    X = np.array([[gf_l, gc_l, gf_v, gc_v]], dtype=float)
    probs = clf.predict_proba(X)[0]
    return {
        "modelo_version": modelo.version,
        "probabilidades": {
            LABELS[int(c)]: round(float(probs[i]), 4) for i, c in enumerate(clf.classes_)
        },
    }


def rendimiento_jugador(jugador_id: int, db: Session) -> dict:
    modelo = _modelo_activo(db, "rendimiento_jugador")
    goles = db.scalar(
        select(EstadisticaJugador.valor).where(
            EstadisticaJugador.jugador_id == jugador_id, EstadisticaJugador.metrica == "goles"
        )
    ) or 0
    reg = joblib.load(modelo.ruta)
    rating = float(reg.predict(np.array([[goles]], dtype=float))[0])
    return {
        "modelo_version": modelo.version,
        "jugador_id": jugador_id,
        "goles": float(goles),
        "rating_esperado": round(min(max(rating, 1), 10), 2),
    }
