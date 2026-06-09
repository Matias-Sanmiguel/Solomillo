from __future__ import annotations

from datetime import datetime

from sqlalchemy import String, func
from sqlalchemy.orm import Mapped, mapped_column

from app.core.db import Base


class ModeloPredictivo(Base):
    __tablename__ = "modelos"
    id: Mapped[int] = mapped_column(primary_key=True)
    nombre: Mapped[str] = mapped_column(String(80))
    version: Mapped[int]
    tipo: Mapped[str] = mapped_column(String(40))
    ruta: Mapped[str] = mapped_column(String(255))
    accuracy: Mapped[float] = mapped_column(default=0)
    activo: Mapped[bool] = mapped_column(default=False)
    creado_en: Mapped[datetime] = mapped_column(server_default=func.now())
