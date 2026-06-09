from __future__ import annotations

from sqlalchemy.orm import Session

from app.users.models import AuditLog


def registrar(db: Session, usuario: str, accion: str, resultado: str, detalle: str | None = None) -> None:
    db.add(AuditLog(usuario=usuario, accion=accion, resultado=resultado, detalle=detalle))
    db.commit()
