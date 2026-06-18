# Solomillo

Plataforma de información y estadísticas deportivas en tiempo real. Centraliza datos de múltiples fuentes externas heterogéneas, los normaliza, calcula estadísticas en vivo, distribuye multicanal e incorpora predicciones de Machine Learning, sobre una arquitectura modular y extensible.

## Stack

| Capa | Tecnología | Motivo |
|------|-----------|--------|
| API / procesamiento | Spring Boot 3.3 (Java 21) | tipado fuerte, DI, ecosistema enterprise |
| Persistencia | PostgreSQL 16 + Spring Data JPA | histórico + integridad referencial (RNF07) |
| Tiempo real | Redis 7 (pub/sub) + Spring WebSocket | publicación < 500 ms (RNF02) |
| ML | Weka 3.8 | modelos predictivos académicos (RF18-27) |
| Frontend | Next.js + Tailwind + Recharts (TypeScript) | dashboard de predicciones |
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
  prode/         Pronostico, Signo, PolizaPuntaje (Strategy),
                 PuntajeClasico, ResolutorProde,
                 RankingProde, RankingProdeService               Prode de usuarios
  distribution/  CanalDistribucion (Observer), Publisher,
                 RedisChannel                                    RF12, CU07
  alerts/        ReglaAlerta (Strategy), GeneradorAlertas        RF11, RF23
  ml/            EloService, FeatureExtractor, MlTrainer,
                 MlPredictor, MetricsService, ResultadoService,
                 ModeloPredictivo, Prediccion, EloHistorial      RF17-27, CU12
  users/         Usuario, AuditLog, AuditService                RNF06, RNF08
  repository/    JpaRepository interfaces
  api/           AuthController, DeportivoController, MlController,
                 EventosWebSocketHandler, WebSocketConfig
  seed/          FifaEloSeedService (selecciones + histórico simulado),
                 ApiFootballSeedService (datos reales si hay API key)
```

### Modelo de predicción

El modelo de resultados (`Logistic`, Weka) se entrena con partidos **finalizados**
reales. Cada partido aporta un vector de features:

- diferencia de **Elo** y de **puntos FIFA** entre local y visitante,
- **forma** reciente (puntos y goles promedio de los últimos 5 partidos),
- **head-to-head** histórico.

El Elo sigue el sistema de **[eloratings.net](https://eloratings.net/about)**:
`Rₙ = Rₒ + K·(W − Wₑ)`, con `Wₑ = 1/(10^(−dr/400)+1)`, ventaja de local de **+100**
(suprimida en cancha neutral, p. ej. fase final del Mundial) y factor **K según el
nivel del torneo** (`NivelTorneo`: Mundial 60 · continental 50 · clasificatorio 40 ·
otro 30 · amistoso 20) ajustado por la diferencia de goles. Se recalcula
cronológicamente tras cada resultado y se versiona en `elo_historial`. Cada predicción se **persiste**
(`predicciones`); al registrar el resultado real se rellena `resultado_real`,
lo que habilita métricas de calidad: **accuracy, log-loss, Brier y calibración**.

Sin `FOOTBALL_API_KEY`, el seed genera selecciones con puntos FIFA y un
histórico simulado coherente con el Elo para poder entrenar y poblar el tablero.

### Patrones de diseño

| Patrón | Ubicación | Requisito que resuelve |
|--------|-----------|------------------------|
| Adapter | `ingest/FuenteAdapter` + implementaciones | RNF04 — nueva fuente sin tocar el núcleo |
| Strategy | `stats/CalculadorEstadistica`, `alerts/ReglaAlerta`, `prode/PolizaPuntaje` | RF16 — nueva métrica/regla/política de puntaje sin tocar las existentes |
| Observer | `distribution/Publisher` + `CanalDistribucion` | RF12 — distribución multicanal desacoplada |

Spring inyecta automáticamente todos los `FuenteAdapter` en el `IngestRegistry` (`Map<String, FuenteAdapter>`) y todos los `CalculadorEstadistica` en el motor (`List<CalculadorEstadistica>`). Agregar una implementación = agregarla al contexto, sin modificar código existente (Abierto/Cerrado).

## Estado del build

- [x] 0 · Scaffold + Docker Compose
- [x] 1 · Capa de datos: entidades JPA, seed Qatar 2022
- [x] 2 · Auth JWT + roles (RNF06) + audit log (RNF08)
- [x] 3 · Tiempo real: Redis pub/sub → WebSocket
- [x] 4 · Pipeline stats / rankings / alertas
- [x] 5 · ML Weka: features reales (Elo + FIFA + forma), predicciones persistidas, métricas (accuracy/log-loss/Brier/calibración)
- [x] 6 · Frontend Next.js: tablero de predicciones, detalle por partido, ranking Elo, panel de modelos (Tailwind + Recharts)

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
| POST | `/partidos/{id}/resultado` | `admin_sistema` · `cientifico_datos` |
| WS | `/ws/eventos` | público (eventos + alertas) |
| POST | `/ml/modelos/entrenar` · `/ml/modelos/entrenar-rendimiento` | `admin_modelos_ia` · `cientifico_datos` |
| GET | `/ml/modelos` `/ml/modelos/{v}/metricas` `/ml/calibracion/{v}` | público |
| GET | `/ml/predicciones` (tablero) `/ml/predicciones/{id}` `/ml/rendimiento/{id}` | público |
| GET | `/ml/elo` `/ml/elo/{equipoId}/historial` | público |
| GET | `/prode/partidos` (abiertos + pronóstico propio) | público / autenticado |
| PUT | `/prode/pronosticos/{partidoId}` | autenticado |
| GET | `/prode/mis-pronosticos` `/prode/ranking/me?torneoId=` | autenticado |
| GET | `/prode/ranking?torneoId=` (omitir = global) | público |

## Equipo

Matías Miranda · Matías Adrián Sanmiguel · Thomás Joaquín Bergamo
