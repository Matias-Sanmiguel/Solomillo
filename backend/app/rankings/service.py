from __future__ import annotations

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.domain.models import Jugador, Partido
from app.events.models import EventoDeportivo
from app.events.schemas import EventoInterno
from app.rankings.models import Posicion


def _posicion(db: Session, torneo_id: int, equipo_id: int) -> Posicion:
    fila = db.scalar(
        select(Posicion).where(Posicion.torneo_id == torneo_id, Posicion.equipo_id == equipo_id)
    )
    if fila is None:
        fila = Posicion(torneo_id=torneo_id, equipo_id=equipo_id)
        db.add(fila)
        db.flush()
    return fila


def actualizar_por_evento(evento: EventoInterno, db: Session) -> None:
    if evento.tipo == "gol" and evento.jugador_id is not None:
        _gol(evento, db)
    elif evento.tipo == "fin_partido":
        cerrar_partido(evento.partido_id, db)


def _gol(evento: EventoInterno, db: Session) -> None:
    partido = db.get(Partido, evento.partido_id)
    jugador = db.get(Jugador, evento.jugador_id)
    if partido is None or jugador is None:
        return
    anota = jugador.equipo_id
    rival = (
        partido.equipo_visitante_id
        if anota == partido.equipo_local_id
        else partido.equipo_local_id
    )
    _posicion(db, partido.torneo_id, anota).goles_favor += 1
    _posicion(db, partido.torneo_id, rival).goles_contra += 1


def _goles_equipo(db: Session, partido_id: int, equipo_id: int) -> int:
    return db.scalar(
        select(func.count(EventoDeportivo.id))
        .join(Jugador, Jugador.id == EventoDeportivo.jugador_id)
        .where(
            EventoDeportivo.partido_id == partido_id,
            EventoDeportivo.tipo == "gol",
            Jugador.equipo_id == equipo_id,
        )
    ) or 0


def cerrar_partido(partido_id: int, db: Session) -> None:
    partido = db.get(Partido, partido_id)
    if partido is None:
        return
    local, visit = partido.equipo_local_id, partido.equipo_visitante_id
    g_local = _goles_equipo(db, partido_id, local)
    g_visit = _goles_equipo(db, partido_id, visit)

    pos_local = _posicion(db, partido.torneo_id, local)
    pos_visit = _posicion(db, partido.torneo_id, visit)

    if g_local > g_visit:
        pos_local.ganados += 1
        pos_local.puntos += 3
        pos_visit.perdidos += 1
    elif g_local < g_visit:
        pos_visit.ganados += 1
        pos_visit.puntos += 3
        pos_local.perdidos += 1
    else:
        pos_local.empatados += 1
        pos_visit.empatados += 1
        pos_local.puntos += 1
        pos_visit.puntos += 1
