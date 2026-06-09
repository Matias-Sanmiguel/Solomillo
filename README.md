# Solomillo

Plataforma de información y estadísticas deportivas en tiempo real. Centraliza datos de múltiples fuentes externas heterogéneas, los normaliza, calcula estadísticas en vivo, distribuye multicanal e incorpora predicciones de Machine Learning, sobre una arquitectura modular y extensible.

## Stack

| Capa | Tecnología | Motivo |
|------|-----------|--------|
| API / procesamiento | FastAPI (Python 3.12) | integración nativa con ML |
| Persistencia | PostgreSQL 16 | histórico + integridad referencial (RNF07) |
| Tiempo real | Redis 7 (pub/sub) + WebSocket | publicación < 500 ms (RNF02) |
| ML | scikit-learn | modelos predictivos (RF18-27) |
| Frontend | Next.js (TypeScript) | dashboard web en vivo |
| Orquestación | Docker Compose | todo contenedorizado |

## Arranque

```bash
cp .env.example .env
docker compose up --build
```

- API: http://localhost:8000 · Swagger: http://localhost:8000/docs
- Frontend: http://localhost:3000

## Arquitectura

Modular monolith. Cada módulo = un área funcional, con límites claros (RNF05). El núcleo no depende de fuentes ni de librerías ML concretas.

```
backend/app/
  core/          config + DB (SQLAlchemy)
  domain/        Torneo, Equipo, Jugador, Partido        RF01-04
  ingest/        FuenteAdapter + registry                RF05-06, RF15, RNF04
  events/        EventoInterno, MotorProcesamiento       CU03-05
  stats/         CalculadorEstadistica                   RF07-09, RF16
  rankings/      tablas de posiciones                    RF10, CU10
  distribution/  Publisher, CanalDistribucion            RF12, CU07
  alerts/        GeneradorAlertas, ReglaAlerta           RF11, RF23
  history/       RepositorioHistorial                    RF13-14, CU08
  ml/            ModeloPredictivo, Dataset, Entrenador   RF17-27, CU12
  users/         Usuario, Rol, Permiso                   RNF06, RNF08
  api/           routers REST + WebSocket
```

### Patrones de diseño

| Patrón | Ubicación | Requisito que resuelve |
|--------|-----------|------------------------|
| Adapter | `ingest/adapters.py` | RNF04 — nueva fuente sin tocar el núcleo |
| Strategy | `stats/calculadores.py` | RF16 — nueva métrica sin tocar las existentes |
| Observer | `distribution/publisher.py` | RF12 — distribución multicanal desacoplada |

Registries (`registrar_fuente`, `registrar_calculador`) habilitan extensión por descubrimiento: agregar una clase la activa, sin modificar código existente (Abierto/Cerrado).

## Estado del build

- [x] 0 · Scaffold + Docker Compose
- [x] 1 · Capa de datos: modelos, create_all, seed Mundial
- [x] 2 · Auth JWT + roles (RNF06) + audit log (RNF08)
- [x] 3 · Tiempo real: Redis pub/sub → WebSocket
- [x] 4 · Pipeline stats / rankings / alertas
- [x] 5 · ML scikit-learn (entrenamiento, versionado, predicción)
- [x] 6 · Frontend Next.js (live scores, estadísticas, predicciones)

## Endpoints

| Método | Ruta | Rol |
|--------|------|-----|
| POST | `/auth/register` · `/auth/login` | público |
| GET | `/auth/me` | autenticado |
| GET | `/torneos` `/equipos` `/partidos` | público |
| GET | `/equipos/{id}/jugadores` `/equipos/{id}/estadisticas` | público |
| GET | `/jugadores/{id}/estadisticas` | público |
| GET | `/torneos/{id}/posiciones` | público |
| GET | `/partidos/{id}/alertas` | público |
| POST | `/ingest/{fuente}` | `admin_sistema` |
| WS | `/ws/eventos` | público (eventos + alertas) |
| POST | `/ml/modelos/entrenar` | `admin_modelos_ia` · `cientifico_datos` |
| GET | `/ml/modelos` `/ml/predicciones/{partido_id}` | público |

## Equipo

Matías Miranda · Matías Adrián Sanmiguel · Thomás Joaquín Bergamo
