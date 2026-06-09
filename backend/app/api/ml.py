from __future__ import annotations

from typing import Any

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.db import get_db
from app.ml.analytics import proyectar_posiciones, tendencias
from app.ml.models import ModeloPredictivo
from app.ml.predictor import SinModeloError, predecir, rendimiento_jugador
from app.ml.trainer import entrenar, entrenar_rendimiento
from app.users.audit import registrar
from app.users.deps import require_role
from app.users.models import Usuario

router = APIRouter(prefix="/ml", tags=["ml"])

_ROLES_ML = ("admin_modelos_ia", "cientifico_datos")


@router.post("/modelos/entrenar", status_code=201)
def entrenar_modelo(
    db: Session = Depends(get_db),
    user: Usuario = Depends(require_role(*_ROLES_ML)),
) -> dict[str, Any]:
    modelo = entrenar(db)
    registrar(db, user.email, "ml:entrenar:resultado", "ok", f"v{modelo.version} acc={modelo.accuracy:.3f}")
    return {"version": modelo.version, "accuracy": modelo.accuracy, "activo": modelo.activo}


@router.post("/modelos/entrenar-rendimiento", status_code=201)
def entrenar_modelo_rendimiento(
    db: Session = Depends(get_db),
    user: Usuario = Depends(require_role(*_ROLES_ML)),
) -> dict[str, Any]:
    modelo = entrenar_rendimiento(db)
    registrar(db, user.email, "ml:entrenar:rendimiento", "ok", f"v{modelo.version} r2={modelo.accuracy:.3f}")
    return {"version": modelo.version, "r2": modelo.accuracy, "activo": modelo.activo}


@router.get("/modelos")
def listar_modelos(db: Session = Depends(get_db)) -> list[dict[str, Any]]:
    rows = db.scalars(select(ModeloPredictivo).order_by(ModeloPredictivo.version.desc())).all()
    return [
        {"version": m.version, "tipo": m.tipo, "accuracy": m.accuracy, "activo": m.activo}
        for m in rows
    ]


@router.get("/predicciones/{partido_id}")
def prediccion(partido_id: int, db: Session = Depends(get_db)) -> dict[str, Any]:
    try:
        return predecir(partido_id, db)
    except SinModeloError as exc:
        raise HTTPException(status.HTTP_409_CONFLICT, str(exc))
    except ValueError as exc:
        raise HTTPException(status.HTTP_404_NOT_FOUND, str(exc))


@router.get("/rendimiento/{jugador_id}")
def rendimiento(jugador_id: int, db: Session = Depends(get_db)) -> dict[str, Any]:
    try:
        return rendimiento_jugador(jugador_id, db)
    except SinModeloError as exc:
        raise HTTPException(status.HTTP_409_CONFLICT, str(exc))


@router.get("/proyeccion/{torneo_id}")
def proyeccion(torneo_id: int, db: Session = Depends(get_db)) -> list[dict[str, Any]]:
    try:
        return proyectar_posiciones(torneo_id, db)
    except SinModeloError as exc:
        raise HTTPException(status.HTTP_409_CONFLICT, str(exc))


@router.get("/tendencias/{torneo_id}")
def tendencias_torneo(torneo_id: int, db: Session = Depends(get_db)) -> list[dict[str, Any]]:
    return tendencias(torneo_id, db)
