from __future__ import annotations

from sqlalchemy import ForeignKey, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column

from app.core.db import Base


class Posicion(Base):
    __tablename__ = "posiciones"
    __table_args__ = (UniqueConstraint("torneo_id", "equipo_id"),)
    id: Mapped[int] = mapped_column(primary_key=True)
    torneo_id: Mapped[int] = mapped_column(ForeignKey("torneos.id"))
    equipo_id: Mapped[int] = mapped_column(ForeignKey("equipos.id"))
    puntos: Mapped[int] = mapped_column(default=0)
    ganados: Mapped[int] = mapped_column(default=0)
    empatados: Mapped[int] = mapped_column(default=0)
    perdidos: Mapped[int] = mapped_column(default=0)
    goles_favor: Mapped[int] = mapped_column(default=0)
    goles_contra: Mapped[int] = mapped_column(default=0)
