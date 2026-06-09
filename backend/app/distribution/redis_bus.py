from __future__ import annotations

import json
from typing import Any

import redis

from app.core.config import settings

_client = redis.Redis.from_url(settings.redis_url, decode_responses=True)


def publish(topico: str, mensaje: dict[str, Any]) -> None:
    _client.publish(topico, json.dumps(mensaje))
