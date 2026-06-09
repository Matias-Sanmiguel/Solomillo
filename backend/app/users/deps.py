from __future__ import annotations

from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.db import get_db
from app.core.security import decode_token
from app.users.models import Usuario

bearer = HTTPBearer(auto_error=True)


def get_current_user(
    cred: HTTPAuthorizationCredentials = Depends(bearer),
    db: Session = Depends(get_db),
) -> Usuario:
    try:
        payload = decode_token(cred.credentials)
    except Exception:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Token inválido")
    user = db.scalar(select(Usuario).where(Usuario.email == payload.get("sub")))
    if user is None:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Usuario inexistente")
    return user


def require_role(*roles: str):
    def guard(user: Usuario = Depends(get_current_user)) -> Usuario:
        if user.rol not in roles:
            raise HTTPException(status.HTTP_403_FORBIDDEN, "Rol no autorizado")
        return user

    return guard
