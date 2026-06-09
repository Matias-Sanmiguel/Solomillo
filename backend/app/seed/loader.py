from __future__ import annotations

import json
from datetime import date, datetime
from pathlib import Path

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.domain.models import Equipo, Jugador, Partido, Torneo

DATA = Path(__file__).parent / "mundial.json"


def seed(db: Session) -> None:
    raw = json.loads(DATA.read_text(encoding="utf-8"))
    t = raw["torneo"]

    existe = db.scalar(
        select(Torneo).where(Torneo.nombre == t["nombre"], Torneo.temporada == t["temporada"])
    )
    if existe:
        return

    torneo = Torneo(
        nombre=t["nombre"],
        categoria=t["categoria"],
        temporada=t["temporada"],
        fecha_inicio=date.fromisoformat(t["fecha_inicio"]),
        fecha_fin=date.fromisoformat(t["fecha_fin"]),
    )
    db.add(torneo)
    db.flush()

    equipos: dict[str, Equipo] = {}
    for e in raw["equipos"]:
        equipo = Equipo(nombre=e["nombre"], entrenador=e["entrenador"], sede=e["sede"])
        db.add(equipo)
        db.flush()
        equipos[e["nombre"]] = equipo
        for j in e["jugadores"]:
            db.add(
                Jugador(
                    equipo_id=equipo.id,
                    nombre=j["nombre"],
                    posicion=j["posicion"],
                    numero_camiseta=j["numero_camiseta"],
                    nacionalidad=j["nacionalidad"],
                    fecha_nacimiento=date.fromisoformat(j["fecha_nacimiento"]),
                )
            )

    for p in raw["partidos"]:
        db.add(
            Partido(
                torneo_id=torneo.id,
                equipo_local_id=equipos[p["local"]].id,
                equipo_visitante_id=equipos[p["visitante"]].id,
                fecha_hora=datetime.fromisoformat(p["fecha_hora"]),
                estadio=p["estadio"],
            )
        )

    db.commit()
