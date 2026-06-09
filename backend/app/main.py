from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.api.auth import router as auth_router
from app.api.ml import router as ml_router
from app.api.routes import router
from app.api.ws import router as ws_router
from app.core.config import settings
from app.core.db import SessionLocal, init_db
from app.seed.loader import seed


@asynccontextmanager
async def lifespan(app: FastAPI):
    init_db()
    with SessionLocal() as db:
        seed(db)
    yield


app = FastAPI(title=settings.app_name, lifespan=lifespan)
app.include_router(auth_router)
app.include_router(router)
app.include_router(ws_router)
app.include_router(ml_router)


@app.get("/")
def root() -> dict[str, str]:
    return {"app": settings.app_name, "docs": "/docs"}
