# Arquitectura de SOLOMILLO

Plataforma de información y estadísticas deportivas en tiempo real (Propuesta 4).
Este documento mapea los **requisitos del enunciado** con las **decisiones de diseño** y los
**patrones** aplicados, para evidenciar una arquitectura desacoplada, modular y extensible.

## Flujo de un evento (de la fuente al usuario)

```
Proveedor externo (formato propio)
        │  payload crudo (Map)
        ▼
FuenteAdapter (Adapter)            ── ApiDeportivaAdapter / ApiFootballAdapter / FootballDataAdapter / EjemploAdapter
        │  normaliza → EventoInterno (formato único interno)
        ▼
IngestRegistry (Registry/Factory)  ── resuelve el adapter por nombre
        ▼
MotorProcesamiento (Mediator)
        ├─► persiste EventoDeportivo
        ├─► List<CalculadorEstadistica> (Strategy)   ── CalculadorGoles / CalculadorTarjetas
        ├─► RankingsService                          ── recalcula posiciones / ELO
        ├─► GeneradorAlertas (reglas)                ── ReglaAlerta
        └─► Publisher (Observer / Pub-Sub)
                   └─► List<CanalDistribucion> (Strategy)
                              ├─ RedisChannel   ── Redis pub/sub → WebSocket → frontend (tiempo real)
                              └─ ReporteChannel ── log/reportes
```

La clave es que **cada borde es un punto de extensión por interfaz**: se agregan fuentes,
métricas o canales nuevos creando una clase que implementa la interfaz correspondiente, sin
tocar el núcleo. Spring inyecta automáticamente todas las implementaciones (`List<T>` / `Map<String,T>`).

## Mapeo requisito → solución → patrón

| Requisito del enunciado | Solución en el código | Patrón |
|---|---|---|
| Fuentes externas con **formatos diferentes** | `ingest/FuenteAdapter` + adapters concretos normalizan a `EventoInterno` | **Adapter** |
| **Incorporar nuevas fuentes sin afectar el sistema** | `ingest/IngestRegistry` resuelve adapters vía `Map<String,FuenteAdapter>` inyectado por Spring | **Registry / Factory** |
| **Procesar eventos en tiempo real** | `events/MotorProcesamiento` orquesta el pipeline; Redis pub/sub + WebSocket entregan al instante | **Mediator** + Publish/Subscribe |
| **Estadísticas según nuevos criterios** | `stats/CalculadorEstadistica` + `CalculadorGoles`, `CalculadorTarjetas`; el motor recorre `List<CalculadorEstadistica>` y aplica `aplica()` | **Strategy** |
| **Distribuir a distintos canales/dispositivos** | `distribution/Publisher` → `List<CanalDistribucion>` → `RedisChannel`, `ReporteChannel` | **Observer / Pub-Sub** + **Strategy** |
| Registrar partidos, equipos, jugadores, torneos | `domain/*` + `repository/*` | Repository |
| Generar estadísticas de partidos y jugadores | `stats/EstadisticaEquipo`, `EstadisticaJugador` | — |
| Extras: rankings, alertas, análisis, historial | `rankings/`, `alerts/`, `ml/`, `ml/EloHistorial` | Strategy (reglas de alerta) |

## Puntos de extensión (cómo crecer sin romper)

- **Nueva fuente de datos** → crear una clase en `ingest/` que implemente `FuenteAdapter`
  y anotarla `@Component("nombre-fuente")`. Queda disponible en `IngestRegistry` sin más cambios.
- **Nueva estadística** → crear una clase en `stats/` que implemente `CalculadorEstadistica`.
  El `MotorProcesamiento` la ejecuta automáticamente para los eventos donde `aplica()` sea `true`.
  Ejemplo agregado: `CalculadorTarjetas` (general + amarillas/rojas).
- **Nuevo canal de publicación** → crear una clase en `distribution/` que implemente
  `CanalDistribucion`. El `Publisher` la recorre sin modificaciones.
  Ejemplo agregado: `ReporteChannel` (canal de reportes, separado del tiempo real).

## Decisiones de dominio

- **ELO de selecciones**: basado en el sistema de eloratings.net (no propio), en `ml/EloService`.
- **Datos de referencia**: orientados al Mundial 2026 (`seed/selecciones.json` con ELO real +
  puntos FIFA oficiales).
- **Predicciones ML**: existen (`ml/`) pero son secundarias en la UI; el foco es el dashboard
  deportivo clásico.
