from __future__ import annotations

from abc import ABC, abstractmethod

from sqlalchemy.orm import Session

from app.alerts.models import Alerta
from app.events.schemas import EventoInterno

_REGLAS: list["ReglaAlerta"] = []


def registrar_regla(cls: type["ReglaAlerta"]) -> type["ReglaAlerta"]:
    _REGLAS.append(cls())
    return cls


class ReglaAlerta(ABC):
    @abstractmethod
    def evaluar(self, evento: EventoInterno) -> str | None: ...


@registrar_regla
class ReglaGol(ReglaAlerta):
    def evaluar(self, evento: EventoInterno) -> str | None:
        if evento.tipo == "gol":
            return f"Gol al minuto {evento.minuto}"
        return None


@registrar_regla
class ReglaExpulsion(ReglaAlerta):
    def evaluar(self, evento: EventoInterno) -> str | None:
        if evento.tipo == "tarjeta" and evento.datos.get("color") == "roja":
            return f"Expulsión al minuto {evento.minuto}"
        return None


@registrar_regla
class ReglaFinPartido(ReglaAlerta):
    def evaluar(self, evento: EventoInterno) -> str | None:
        if evento.tipo == "fin_partido":
            return "Fin del partido"
        return None


class GeneradorAlertas:
    def evaluar(self, evento: EventoInterno, db: Session) -> list[Alerta]:
        alertas: list[Alerta] = []
        for regla in _REGLAS:
            mensaje = regla.evaluar(evento)
            if mensaje:
                alerta = Alerta(partido_id=evento.partido_id, tipo=evento.tipo, mensaje=mensaje)
                db.add(alerta)
                alertas.append(alerta)
        if alertas:
            db.flush()
        return alertas


generador = GeneradorAlertas()
