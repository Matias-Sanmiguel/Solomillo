# Solomillo

Plataforma de información y estadísticas deportivas en tiempo real. Centraliza datos de múltiples fuentes externas heterogéneas, los normaliza, calcula estadísticas en vivo, distribuye multicanal e incorpora predicciones de Machine Learning, sobre una arquitectura modular y extensible.

## Stack

| Capa | Tecnología | Motivo |
|------|-----------|--------|
| API / procesamiento | Spring Boot 3.3 (Java 21) | tipado fuerte, DI, ecosistema enterprise |
| Persistencia | PostgreSQL 16 + Spring Data JPA | histórico + integridad referencial (RNF07) |
| Tiempo real | Redis 7 (pub/sub) + Spring WebSocket | publicación < 500 ms (RNF02) |
| ML | Weka 3.8 | modelos predictivos académicos (RF18-27) |
| Frontend | Next.js (TypeScript) | dashboard web en vivo |
| Orquestación | Docker Compose | todo contenedorizado |

## Arranque

```bash
cp .env.example .env
docker compose up --build
# tests de integración (requiere stack levantado):
docker compose exec api mvn test -DbaseUrl=http://localhost:8000
```

- API: http://localhost:8000
- Frontend: http://localhost:3000

## Arquitectura

Modular monolith. Cada módulo = un área funcional (RNF05). El núcleo no depende de fuentes externas ni de la librería ML concreta.

```
backend/src/main/java/dev/solomillo/
  core/          Config, JwtUtil, SecurityConfig, JwtAuthFilter, RedisConfig
  domain/        Torneo, Equipo, Jugador, Partido               RF01-04
  ingest/        FuenteAdapter + IngestRegistry (Adapter)       RF05-06, RF15, RNF04
  events/        EventoInterno (record), EventoDeportivo,
                 MotorProcesamiento                              CU03-05
  stats/         CalculadorEstadistica (Strategy),
                 CalculadorGoles                                 RF07-09, RF16
  rankings/      Posicion, RankingsService                      RF10, CU10
  distribution/  CanalDistribucion (Observer), Publisher,
                 RedisChannel                                    RF12, CU07
  alerts/        ReglaAlerta (Strategy), GeneradorAlertas        RF11, RF23
  ml/            DatasetGenerator, MlTrainer, MlPredictor,
                 MlAnalytics, ModeloPredictivo                   RF17-27, CU12
  users/         Usuario, AuditLog, AuditService                RNF06, RNF08
  repository/    12 JpaRepository interfaces
  api/           AuthController, DeportivoController, MlController,
                 EventosWebSocketHandler, WebSocketConfig
  seed/          DataLoader (Qatar 2022)
```

### Patrones de diseño

| Patrón | Ubicación | Requisito que resuelve |
|--------|-----------|------------------------|
| Adapter | `ingest/FuenteAdapter` + implementaciones | RNF04 — nueva fuente sin tocar el núcleo |
| Strategy | `stats/CalculadorEstadistica`, `alerts/ReglaAlerta` | RF16 — nueva métrica/regla sin tocar las existentes |
| Observer | `distribution/Publisher` + `CanalDistribucion` | RF12 — distribución multicanal desacoplada |

Spring inyecta automáticamente todos los `FuenteAdapter` en el `IngestRegistry` (`Map<String, FuenteAdapter>`) y todos los `CalculadorEstadistica` en el motor (`List<CalculadorEstadistica>`). Agregar una implementación = agregarla al contexto, sin modificar código existente (Abierto/Cerrado).

## Estado del build

- [x] 0 · Scaffold + Docker Compose
- [x] 1 · Capa de datos: entidades JPA, seed Qatar 2022
- [x] 2 · Auth JWT + roles (RNF06) + audit log (RNF08)
- [x] 3 · Tiempo real: Redis pub/sub → WebSocket
- [x] 4 · Pipeline stats / rankings / alertas
- [x] 5 · ML Weka (entrenamiento, versionado, predicción)
- [x] 6 · Frontend Next.js (live scores, estadísticas, predicciones)

## Endpoints

| Método | Ruta | Rol |
|--------|------|-----|
| POST | `/auth/register` · `/auth/login` | público |
| GET | `/auth/me` | autenticado |
| GET | `/health` `/torneos` `/equipos` `/partidos` | público |
| GET | `/equipos/{id}/jugadores` `/equipos/{id}/estadisticas` | público |
| GET | `/jugadores/{id}/estadisticas` | público |
| GET | `/torneos/{id}/posiciones` | público |
| GET | `/partidos/{id}/alertas` | público |
| POST | `/ingest/{fuente}` | `admin_sistema` |
| WS | `/ws/eventos` | público (eventos + alertas) |
| POST | `/ml/modelos/entrenar` · `/ml/modelos/entrenar-rendimiento` | `admin_modelos_ia` · `cientifico_datos` |
| GET | `/ml/modelos` `/ml/predicciones/{id}` `/ml/rendimiento/{id}` | público |
| GET | `/ml/proyeccion/{torneoId}` `/ml/tendencias/{torneoId}` | público |

## Equipo

Matías Miranda · Matías Adrián Sanmiguel · Thomás Joaquín Bergamo
