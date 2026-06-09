from __future__ import annotations

from datetime import datetime

from sqlalchemy import ForeignKey, String, func
from sqlalchemy.orm import Mapped, mapped_column

from app.core.db import Base


class EventoDeportivo(Base):
    __tablename__ = "eventos"
    id: Mapped[int] = mapped_column(primary_key=True)
    partido_id: Mapped[int] = mapped_column(ForeignKey("partidos.id"))
    jugador_id: Mapped[int | None] = mapped_column(ForeignKey("jugadores.id"), nullable=True)
    tipo: Mapped[str] = mapped_column(String(40))
    minuto: Mapped[int]
    fuente: Mapped[str] = mapped_column(String(60))
    creado_en: Mapped[datetime] = mapped_column(server_default=func.now())
