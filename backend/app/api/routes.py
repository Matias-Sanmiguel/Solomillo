from __future__ import annotations

from typing import Any

from fastapi import APIRouter, Depends
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.alerts.models import Alerta
from app.core.db import get_db
from app.domain.models import Equipo, Jugador, Partido, Torneo
from app.events.motor import motor
from app.ingest.adapters import crear_adapter
from app.rankings.models import Posicion
from app.stats.models import EstadisticaEquipo, EstadisticaJugador
from app.users.audit import registrar
from app.users.deps import require_role
from app.users.models import Usuario

router = APIRouter()


@router.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@router.get("/torneos")
def listar_torneos(db: Session = Depends(get_db)) -> list[dict[str, Any]]:
    rows = db.scalars(select(Torneo)).all()
    return [{"id": t.id, "nombre": t.nombre, "temporada": t.temporada} for t in rows]


@router.get("/equipos")
def listar_equipos(db: Session = Depends(get_db)) -> list[dict[str, Any]]:
    rows = db.scalars(select(Equipo)).all()
    return [{"id": e.id, "nombre": e.nombre, "entrenador": e.entrenador} for e in rows]


@router.get("/equipos/{equipo_id}/jugadores")
def listar_jugadores(equipo_id: int, db: Session = Depends(get_db)) -> list[dict[str, Any]]:
    rows = db.scalars(select(Jugador).where(Jugador.equipo_id == equipo_id)).all()
    return [
        {"id": j.id, "nombre": j.nombre, "posicion": j.posicion, "numero": j.numero_camiseta}
        for j in rows
    ]


@router.get("/partidos")
def listar_partidos(db: Session = Depends(get_db)) -> list[dict[str, Any]]:
    rows = db.scalars(select(Partido)).all()
    return [
        {
            "id": p.id,
            "torneo_id": p.torneo_id,
            "local_id": p.equipo_local_id,
            "visitante_id": p.equipo_visitante_id,
            "fecha_hora": p.fecha_hora.isoformat(),
            "estadio": p.estadio,
        }
        for p in rows
    ]


@router.get("/torneos/{torneo_id}/posiciones")
def listar_posiciones(torneo_id: int, db: Session = Depends(get_db)) -> list[dict[str, Any]]:
    rows = db.scalars(
        select(Posicion)
        .where(Posicion.torneo_id == torneo_id)
        .order_by(Posicion.puntos.desc(), (Posicion.goles_favor - Posicion.goles_contra).desc())
    ).all()
    return [
        {
            "equipo_id": p.equipo_id,
            "puntos": p.puntos,
            "gf": p.goles_favor,
            "gc": p.goles_contra,
            "dif": p.goles_favor - p.goles_contra,
        }
        for p in rows
    ]


@router.get("/jugadores/{jugador_id}/estadisticas")
def stats_jugador(jugador_id: int, db: Session = Depends(get_db)) -> list[dict[str, Any]]:
    rows = db.scalars(
        select(EstadisticaJugador).where(EstadisticaJugador.jugador_id == jugador_id)
    ).all()
    return [{"metrica": s.metrica, "valor": s.valor, "torneo_id": s.torneo_id} for s in rows]


@router.get("/equipos/{equipo_id}/estadisticas")
def stats_equipo(equipo_id: int, db: Session = Depends(get_db)) -> list[dict[str, Any]]:
    rows = db.scalars(
        select(EstadisticaEquipo).where(EstadisticaEquipo.equipo_id == equipo_id)
    ).all()
    return [{"metrica": s.metrica, "valor": s.valor, "torneo_id": s.torneo_id} for s in rows]


@router.get("/partidos/{partido_id}/alertas")
def listar_alertas(partido_id: int, db: Session = Depends(get_db)) -> list[dict[str, Any]]:
    rows = db.scalars(
        select(Alerta).where(Alerta.partido_id == partido_id).order_by(Alerta.creado_en.desc())
    ).all()
    return [{"tipo": a.tipo, "mensaje": a.mensaje, "creado_en": a.creado_en.isoformat()} for a in rows]


@router.post("/ingest/{fuente}", status_code=202)
def ingestar(
    fuente: str,
    payload: dict[str, Any],
    db: Session = Depends(get_db),
    user: Usuario = Depends(require_role("admin_sistema")),
) -> dict[str, str]:
    try:
        adapter = crear_adapter(fuente, config={})
        evento = adapter.normalizar(payload)
        motor.procesar(evento, db, fuente)
    except Exception as exc:
        db.rollback()
        registrar(db, user.email, f"ingest:{fuente}", "error", str(exc))
        raise
    registrar(db, user.email, f"ingest:{fuente}", "ok", evento.tipo)
    return {"status": "accepted"}
