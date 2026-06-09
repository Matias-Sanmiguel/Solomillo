from __future__ import annotations

from datetime import datetime

from sqlalchemy import String, func
from sqlalchemy.orm import Mapped, mapped_column

from app.core.db import Base

ROLES = (
    "usuario_final",
    "analista_deportivo",
    "admin_sistema",
    "admin_modelos_ia",
    "cientifico_datos",
)


class Usuario(Base):
    __tablename__ = "usuarios"
    id: Mapped[int] = mapped_column(primary_key=True)
    email: Mapped[str] = mapped_column(String(255), unique=True)
    nombre: Mapped[str] = mapped_column(String(120))
    hash_password: Mapped[str] = mapped_column(String(255))
    rol: Mapped[str] = mapped_column(String(40), default="usuario_final")


class AuditLog(Base):
    __tablename__ = "audit_log"
    id: Mapped[int] = mapped_column(primary_key=True)
    usuario: Mapped[str] = mapped_column(String(255))
    accion: Mapped[str] = mapped_column(String(120))
    resultado: Mapped[str] = mapped_column(String(40))
    detalle: Mapped[str | None] = mapped_column(String(500), nullable=True)
    creado_en: Mapped[datetime] = mapped_column(server_default=func.now())
