from __future__ import annotations

from datetime import date, datetime

from sqlalchemy import ForeignKey, String
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.core.db import Base


class Torneo(Base):
    __tablename__ = "torneos"
    id: Mapped[int] = mapped_column(primary_key=True)
    nombre: Mapped[str] = mapped_column(String(120))
    categoria: Mapped[str] = mapped_column(String(60))
    temporada: Mapped[str] = mapped_column(String(20))
    fecha_inicio: Mapped[date]
    fecha_fin: Mapped[date]

    partidos: Mapped[list[Partido]] = relationship(back_populates="torneo")


class Equipo(Base):
    __tablename__ = "equipos"
    id: Mapped[int] = mapped_column(primary_key=True)
    nombre: Mapped[str] = mapped_column(String(120))
    escudo: Mapped[str | None] = mapped_column(String(255), nullable=True)
    entrenador: Mapped[str | None] = mapped_column(String(120), nullable=True)
    sede: Mapped[str | None] = mapped_column(String(120), nullable=True)
    estadio: Mapped[str | None] = mapped_column(String(120), nullable=True)

    jugadores: Mapped[list[Jugador]] = relationship(back_populates="equipo")


class Jugador(Base):
    __tablename__ = "jugadores"
    id: Mapped[int] = mapped_column(primary_key=True)
    equipo_id: Mapped[int] = mapped_column(ForeignKey("equipos.id"))
    nombre: Mapped[str] = mapped_column(String(120))
    posicion: Mapped[str] = mapped_column(String(40))
    numero_camiseta: Mapped[int]
    nacionalidad: Mapped[str] = mapped_column(String(60))
    fecha_nacimiento: Mapped[date]

    equipo: Mapped[Equipo] = relationship(back_populates="jugadores")


class Partido(Base):
    __tablename__ = "partidos"
    id: Mapped[int] = mapped_column(primary_key=True)
    torneo_id: Mapped[int] = mapped_column(ForeignKey("torneos.id"))
    equipo_local_id: Mapped[int] = mapped_column(ForeignKey("equipos.id"))
    equipo_visitante_id: Mapped[int] = mapped_column(ForeignKey("equipos.id"))
    fecha_hora: Mapped[datetime]
    estadio: Mapped[str | None] = mapped_column(String(120), nullable=True)

    torneo: Mapped[Torneo] = relationship(back_populates="partidos")
