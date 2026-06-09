from __future__ import annotations

from abc import ABC, abstractmethod
from typing import Any

from app.distribution.redis_bus import publish


class CanalDistribucion(ABC):
    @abstractmethod
    def enviar(self, topico: str, mensaje: dict[str, Any]) -> None: ...


class RedisChannel(CanalDistribucion):
    def enviar(self, topico: str, mensaje: dict[str, Any]) -> None:
        publish(topico, mensaje)


class APIRest(CanalDistribucion):
    def enviar(self, topico: str, mensaje: dict[str, Any]) -> None: ...


class Publisher:
    def __init__(self) -> None:
        self._canales: list[CanalDistribucion] = []

    def suscribir(self, canal: CanalDistribucion) -> None:
        self._canales.append(canal)

    def publicar(self, topico: str, mensaje: dict[str, Any]) -> None:
        for canal in self._canales:
            canal.enviar(topico, mensaje)


publisher = Publisher()
publisher.suscribir(RedisChannel())
publisher.suscribir(APIRest())
