from __future__ import annotations

from abc import ABC, abstractmethod
from typing import Any

from app.events.schemas import EventoInterno

_REGISTRY: dict[str, type["FuenteAdapter"]] = {}


def registrar_fuente(nombre: str):
    def deco(cls: type["FuenteAdapter"]) -> type["FuenteAdapter"]:
        _REGISTRY[nombre] = cls
        return cls
    return deco


def crear_adapter(nombre: str, config: dict[str, Any]) -> "FuenteAdapter":
    if nombre not in _REGISTRY:
        raise KeyError(f"Fuente no registrada: {nombre}")
    return _REGISTRY[nombre](config)


class FuenteAdapter(ABC):
    def __init__(self, config: dict[str, Any]) -> None:
        self.config = config

    @abstractmethod
    def normalizar(self, payload: dict[str, Any]) -> EventoInterno: ...


@registrar_fuente("ejemplo")
class EjemploAdapter(FuenteAdapter):
    def normalizar(self, payload: dict[str, Any]) -> EventoInterno:
        return EventoInterno(
            tipo=payload["type"],
            partido_id=payload["match_id"],
            minuto=payload.get("minute", 0),
            jugador_id=payload.get("player_id"),
            datos=payload,
        )


@registrar_fuente("football-data")
class FootballDataAdapter(FuenteAdapter):
    _TIPOS = {"GOAL": "gol", "CARD": "tarjeta", "SUBSTITUTION": "sustitucion", "FULL_TIME": "fin_partido"}

    def normalizar(self, payload: dict[str, Any]) -> EventoInterno:
        externo = payload.get("type", "")
        match = payload.get("match", {})
        scorer = payload.get("scorer") or payload.get("player") or {}
        return EventoInterno(
            tipo=self._TIPOS.get(externo, externo.lower()),
            partido_id=int(match.get("id") or payload["match_id"]),
            minuto=int(payload.get("minute", 0)),
            jugador_id=scorer.get("id"),
            datos=payload,
        )
