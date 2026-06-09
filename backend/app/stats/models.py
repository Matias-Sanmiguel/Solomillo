from __future__ import annotations

from sqlalchemy import ForeignKey, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column

from app.core.db import Base


class EstadisticaJugador(Base):
    __tablename__ = "estadisticas_jugador"
    __table_args__ = (UniqueConstraint("jugador_id", "torneo_id", "metrica"),)
    id: Mapped[int] = mapped_column(primary_key=True)
    jugador_id: Mapped[int] = mapped_column(ForeignKey("jugadores.id"))
    torneo_id: Mapped[int] = mapped_column(ForeignKey("torneos.id"))
    metrica: Mapped[str]
    valor: Mapped[float] = mapped_column(default=0)


class EstadisticaEquipo(Base):
    __tablename__ = "estadisticas_equipo"
    __table_args__ = (UniqueConstraint("equipo_id", "torneo_id", "metrica"),)
    id: Mapped[int] = mapped_column(primary_key=True)
    equipo_id: Mapped[int] = mapped_column(ForeignKey("equipos.id"))
    torneo_id: Mapped[int] = mapped_column(ForeignKey("torneos.id"))
    metrica: Mapped[str]
    valor: Mapped[float] = mapped_column(default=0)
