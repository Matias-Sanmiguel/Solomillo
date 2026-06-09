from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel, EmailStr
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.db import get_db
from app.core.security import create_token, hash_password, verify_password
from app.users.deps import get_current_user
from app.users.models import ROLES, Usuario

router = APIRouter(prefix="/auth", tags=["auth"])


class RegisterIn(BaseModel):
    email: EmailStr
    nombre: str
    password: str
    rol: str = "usuario_final"


class LoginIn(BaseModel):
    email: EmailStr
    password: str


class TokenOut(BaseModel):
    access_token: str
    token_type: str = "bearer"
    rol: str


@router.post("/register", response_model=TokenOut, status_code=201)
def register(data: RegisterIn, db: Session = Depends(get_db)) -> TokenOut:
    if data.rol not in ROLES:
        raise HTTPException(status.HTTP_400_BAD_REQUEST, f"Rol inválido. Opciones: {ROLES}")
    if db.scalar(select(Usuario).where(Usuario.email == data.email)):
        raise HTTPException(status.HTTP_409_CONFLICT, "Email ya registrado")
    user = Usuario(
        email=data.email,
        nombre=data.nombre,
        hash_password=hash_password(data.password),
        rol=data.rol,
    )
    db.add(user)
    db.commit()
    return TokenOut(access_token=create_token(user.email, user.rol), rol=user.rol)


@router.post("/login", response_model=TokenOut)
def login(data: LoginIn, db: Session = Depends(get_db)) -> TokenOut:
    user = db.scalar(select(Usuario).where(Usuario.email == data.email))
    if user is None or not verify_password(data.password, user.hash_password):
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Credenciales inválidas")
    return TokenOut(access_token=create_token(user.email, user.rol), rol=user.rol)


@router.get("/me")
def me(user: Usuario = Depends(get_current_user)) -> dict:
    return {"email": user.email, "nombre": user.nombre, "rol": user.rol}
