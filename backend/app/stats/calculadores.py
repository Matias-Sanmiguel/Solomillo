from __future__ import annotations

from abc import ABC, abstractmethod

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.domain.models import Jugador, Partido
from app.events.schemas import EventoInterno
from app.stats.models import EstadisticaEquipo, EstadisticaJugador

_CALCULADORES: list["CalculadorEstadistica"] = []


def registrar_calculador(cls: type["CalculadorEstadistica"]) -> type["CalculadorEstadistica"]:
    _CALCULADORES.append(cls())
    return cls


def calculadores() -> list["CalculadorEstadistica"]:
    return _CALCULADORES


def _inc_jugador(db: Session, jugador_id: int, torneo_id: int, metrica: str, delta: float) -> None:
    fila = db.scalar(
        select(EstadisticaJugador).where(
            EstadisticaJugador.jugador_id == jugador_id,
            EstadisticaJugador.torneo_id == torneo_id,
            EstadisticaJugador.metrica == metrica,
        )
    )
    if fila is None:
        fila = EstadisticaJugador(jugador_id=jugador_id, torneo_id=torneo_id, metrica=metrica, valor=0)
        db.add(fila)
    fila.valor += delta


def _inc_equipo(db: Session, equipo_id: int, torneo_id: int, metrica: str, delta: float) -> None:
    fila = db.scalar(
        select(EstadisticaEquipo).where(
            EstadisticaEquipo.equipo_id == equipo_id,
            EstadisticaEquipo.torneo_id == torneo_id,
            EstadisticaEquipo.metrica == metrica,
        )
    )
    if fila is None:
        fila = EstadisticaEquipo(equipo_id=equipo_id, torneo_id=torneo_id, metrica=metrica, valor=0)
        db.add(fila)
    fila.valor += delta


class CalculadorEstadistica(ABC):
    nombre: str

    @abstractmethod
    def aplica(self, evento: EventoInterno) -> bool: ...

    @abstractmethod
    def actualizar(self, evento: EventoInterno, db: Session) -> None: ...


@registrar_calculador
class CalculadorGoles(CalculadorEstadistica):
    nombre = "goles"

    def aplica(self, evento: EventoInterno) -> bool:
        return evento.tipo == "gol" and evento.jugador_id is not None

    def actualizar(self, evento: EventoInterno, db: Session) -> None:
        partido = db.get(Partido, evento.partido_id)
        jugador = db.get(Jugador, evento.jugador_id)
        if partido is None or jugador is None:
            return
        _inc_jugador(db, jugador.id, partido.torneo_id, "goles", 1)
        _inc_equipo(db, jugador.equipo_id, partido.torneo_id, "goles", 1)
