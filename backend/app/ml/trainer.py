from __future__ import annotations

from pathlib import Path

import joblib
from sklearn.linear_model import LinearRegression, LogisticRegression
from sklearn.metrics import accuracy_score, r2_score
from sklearn.model_selection import train_test_split
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.ml.dataset import generar_dataset, generar_rendimiento
from app.ml.models import ModeloPredictivo

MODEL_DIR = Path("/code/models")


def _persistir(db: Session, nombre: str, tipo: str, clf, score: float) -> ModeloPredictivo:
    MODEL_DIR.mkdir(parents=True, exist_ok=True)
    ultimo = db.scalar(
        select(ModeloPredictivo)
        .where(ModeloPredictivo.nombre == nombre)
        .order_by(ModeloPredictivo.version.desc())
    )
    version = (ultimo.version + 1) if ultimo else 1
    ruta = str(MODEL_DIR / f"{nombre}_v{version}.joblib")
    joblib.dump(clf, ruta)

    for m in db.scalars(select(ModeloPredictivo).where(ModeloPredictivo.nombre == nombre)):
        m.activo = False

    modelo = ModeloPredictivo(
        nombre=nombre, version=version, tipo=tipo, ruta=ruta, accuracy=score, activo=True
    )
    db.add(modelo)
    db.commit()
    db.refresh(modelo)
    return modelo


def entrenar(db: Session) -> ModeloPredictivo:
    X, y = generar_dataset()
    X_tr, X_te, y_tr, y_te = train_test_split(X, y, test_size=0.2, random_state=7)
    clf = LogisticRegression(max_iter=1000).fit(X_tr, y_tr)
    acc = float(accuracy_score(y_te, clf.predict(X_te)))
    return _persistir(db, "resultado_partido", "clasificacion", clf, acc)


def entrenar_rendimiento(db: Session) -> ModeloPredictivo:
    X, y = generar_rendimiento()
    X_tr, X_te, y_tr, y_te = train_test_split(X, y, test_size=0.2, random_state=7)
    reg = LinearRegression().fit(X_tr, y_tr)
    r2 = float(r2_score(y_te, reg.predict(X_te)))
    return _persistir(db, "rendimiento_jugador", "regresion", reg, r2)
