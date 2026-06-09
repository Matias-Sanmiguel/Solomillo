from __future__ import annotations

from sqlalchemy.orm import Session

from app.alerts.generador import generador
from app.distribution.publisher import publisher
from app.events.models import EventoDeportivo
from app.events.schemas import EventoInterno
from app.rankings.service import actualizar_por_evento
from app.stats.calculadores import calculadores


class MotorProcesamiento:
    def procesar(self, evento: EventoInterno, db: Session, fuente: str) -> None:
        db.add(
            EventoDeportivo(
                partido_id=evento.partido_id,
                jugador_id=evento.jugador_id,
                tipo=evento.tipo,
                minuto=evento.minuto,
                fuente=fuente,
            )
        )

        for calc in calculadores():
            if calc.aplica(evento):
                calc.actualizar(evento, db)

        actualizar_por_evento(evento, db)
        alertas = generador.evaluar(evento, db)
        db.commit()

        publisher.publicar("evento", evento.model_dump())
        for alerta in alertas:
            publisher.publicar(
                "alerta",
                {"partido_id": alerta.partido_id, "tipo": alerta.tipo, "mensaje": alerta.mensaje},
            )


motor = MotorProcesamiento()
