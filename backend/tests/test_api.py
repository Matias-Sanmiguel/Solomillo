import os
import uuid

import httpx
import pytest

BASE = os.environ.get("BASE_URL", "http://localhost:8000")


@pytest.fixture(scope="session")
def client():
    with httpx.Client(base_url=BASE, timeout=30) as c:
        yield c


def _admin_token(client) -> str:
    email = f"admin-{uuid.uuid4().hex[:8]}@test.dev"
    r = client.post(
        "/auth/register",
        json={"email": email, "nombre": "T", "password": "secret123", "rol": "admin_sistema"},
    )
    assert r.status_code == 201
    return r.json()["access_token"]


def _ds_token(client) -> str:
    email = f"ds-{uuid.uuid4().hex[:8]}@test.dev"
    r = client.post(
        "/auth/register",
        json={"email": email, "nombre": "T", "password": "secret123", "rol": "cientifico_datos"},
    )
    return r.json()["access_token"]


def test_health(client):
    assert client.get("/health").json() == {"status": "ok"}


def test_seed_equipos(client):
    nombres = {e["nombre"] for e in client.get("/equipos").json()}
    assert {"Argentina", "Francia", "Croacia", "Marruecos"} <= nombres


def test_ingest_requires_admin(client):
    r = client.post("/ingest/ejemplo", json={"type": "gol", "match_id": 1, "minute": 5, "player_id": 1})
    assert r.status_code in (401, 403)


def test_ingest_updates_stats(client):
    tok = _admin_token(client)
    h = {"Authorization": f"Bearer {tok}"}
    antes = next((s["valor"] for s in client.get("/jugadores/1/estadisticas").json() if s["metrica"] == "goles"), 0)
    r = client.post("/ingest/ejemplo", json={"type": "gol", "match_id": 1, "minute": 12, "player_id": 1}, headers=h)
    assert r.status_code == 202
    despues = next(s["valor"] for s in client.get("/jugadores/1/estadisticas").json() if s["metrica"] == "goles")
    assert despues == antes + 1


def test_football_data_adapter(client):
    tok = _admin_token(client)
    h = {"Authorization": f"Bearer {tok}"}
    payload = {"type": "GOAL", "match": {"id": 1}, "minute": 50, "scorer": {"id": 3}}
    r = client.post("/ingest/football-data", json=payload, headers=h)
    assert r.status_code == 202


def test_ml_train_and_predict(client):
    tok = _ds_token(client)
    h = {"Authorization": f"Bearer {tok}"}
    assert client.post("/ml/modelos/entrenar", headers=h).status_code == 201
    assert client.post("/ml/modelos/entrenar-rendimiento", headers=h).status_code == 201

    pred = client.get("/ml/predicciones/1").json()
    assert abs(sum(pred["probabilidades"].values()) - 1.0) < 0.01

    rend = client.get("/ml/rendimiento/1").json()
    assert 1 <= rend["rating_esperado"] <= 10

    proy = client.get("/ml/proyeccion/1").json()
    assert proy and proy[0]["posicion_proyectada"] == 1
    assert client.get("/ml/tendencias/1").status_code == 200
