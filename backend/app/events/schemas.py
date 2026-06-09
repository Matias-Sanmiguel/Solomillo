from __future__ import annotations

from typing import Any

from pydantic import BaseModel


class EventoInterno(BaseModel):
    tipo: str
    partido_id: int
    minuto: int
    jugador_id: int | None = None
    datos: dict[str, Any] = {}
