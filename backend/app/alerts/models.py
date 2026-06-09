from __future__ import annotations

from datetime import datetime

from sqlalchemy import ForeignKey, String, func
from sqlalchemy.orm import Mapped, mapped_column

from app.core.db import Base


class Alerta(Base):
    __tablename__ = "alertas"
    id: Mapped[int] = mapped_column(primary_key=True)
    partido_id: Mapped[int] = mapped_column(ForeignKey("partidos.id"))
    tipo: Mapped[str] = mapped_column(String(40))
    mensaje: Mapped[str] = mapped_column(String(255))
    creado_en: Mapped[datetime] = mapped_column(server_default=func.now())
