package dev.solomillo.api;

import dev.solomillo.events.MotorProcesamiento;
import dev.solomillo.ingest.IngestRegistry;
import dev.solomillo.repository.*;
import dev.solomillo.users.AuditService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
public class DeportivoController {

    private final TorneoRepository torneoRepo;
    private final EquipoRepository equipoRepo;
    private final JugadorRepository jugadorRepo;
    private final PartidoRepository partidoRepo;
    private final PosicionRepository posicionRepo;
    private final EstadisticaJugadorRepository ejRepo;
    private final EstadisticaEquipoRepository eeRepo;
    private final AlertaRepository alertaRepo;
    private final IngestRegistry registry;
    private final MotorProcesamiento motor;
    private final AuditService audit;
    private final dev.solomillo.ml.ResultadoService resultados;

    public DeportivoController(TorneoRepository t, EquipoRepository e, JugadorRepository j,
                                PartidoRepository p, PosicionRepository pos,
                                EstadisticaJugadorRepository ej, EstadisticaEquipoRepository ee,
                                AlertaRepository al, IngestRegistry reg,
                                MotorProcesamiento motor, AuditService audit,
                                dev.solomillo.ml.ResultadoService resultados) {
        this.torneoRepo = t; this.equipoRepo = e; this.jugadorRepo = j; this.partidoRepo = p;
        this.posicionRepo = pos; this.ejRepo = ej; this.eeRepo = ee; this.alertaRepo = al;
        this.registry = reg; this.motor = motor; this.audit = audit; this.resultados = resultados;
    }

    @GetMapping("/health")
    public Map<String, String> health() { return Map.of("status", "ok"); }

    @GetMapping("/torneos")
    public List<Map<String, Object>> torneos() {
        return torneoRepo.findAll().stream().map(t ->
                Map.<String, Object>of("id", t.getId(), "nombre", t.getNombre(), "temporada", t.getTemporada())
        ).toList();
    }

    @GetMapping("/equipos")
    public List<Map<String, Object>> equipos() {
        return equipoRepo.findAll().stream().map(e ->
                Map.<String, Object>of("id", e.getId(), "nombre", e.getNombre(),
                        "entrenador", e.getEntrenador() != null ? e.getEntrenador() : "",
                        "escudo", e.getEscudo() != null ? e.getEscudo() : "",
                        "estadio", e.getEstadio() != null ? e.getEstadio() : "")
        ).toList();
    }

    @GetMapping("/equipos/{id}/jugadores")
    public List<Map<String, Object>> jugadores(@PathVariable Long id) {
        return jugadorRepo.findByEquipoId(id).stream().map(j ->
                Map.<String, Object>of("id", j.getId(), "nombre", j.getNombre(),
                        "posicion", j.getPosicion(), "numero", j.getNumeroCamiseta())
        ).toList();
    }

    @GetMapping("/partidos")
    public List<Map<String, Object>> partidos() {
        return partidoRepo.findAll().stream().map(p -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("torneo_id", p.getTorneo().getId());
            m.put("local_id", p.getEquipoLocal().getId());
            m.put("visitante_id", p.getEquipoVisitante().getId());
            m.put("fecha_hora", p.getFechaHora() != null ? p.getFechaHora().toString() : null);
            m.put("estadio", p.getEstadio() != null ? p.getEstadio() : "");
            m.put("estado", p.getEstado() != null ? p.getEstado().name() : "PROGRAMADO");
            m.put("goles_local", p.getGolesLocal());
            m.put("goles_visitante", p.getGolesVisitante());
            return m;
        }).toList();
    }

    @PostMapping("/partidos/{id}/resultado")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyAuthority('admin_sistema','cientifico_datos')")
    public Map<String, Object> registrarResultado(@PathVariable Long id,
                                                   @RequestBody Map<String, Object> body,
                                                   @AuthenticationPrincipal String email) {
        try {
            int gl = ((Number) body.get("goles_local")).intValue();
            int gv = ((Number) body.get("goles_visitante")).intValue();
            var p = resultados.registrar(id, gl, gv);
            audit.registrar(email, "partido:resultado", "ok", id + " " + gl + "-" + gv);
            return Map.of("id", p.getId(), "estado", p.getEstado().name(),
                    "goles_local", gl, "goles_visitante", gv);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (NullPointerException | ClassCastException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "goles_local y goles_visitante requeridos");
        }
    }

    @GetMapping("/torneos/{id}/posiciones")
    public List<Map<String, Object>> posiciones(@PathVariable Long id) {
        return posicionRepo.findByTorneoIdOrderByPuntosDescGolesFavorDesc(id).stream().map(p ->
                Map.<String, Object>of("equipo_id", p.getEquipoId(), "puntos", p.getPuntos(),
                        "gf", p.getGolesFavor(), "gc", p.getGolesContra(),
                        "dif", p.getGolesFavor() - p.getGolesContra())
        ).toList();
    }

    @GetMapping("/torneos/{id}/goleadores")
    public List<Map<String, Object>> goleadores(@PathVariable Long id) {
        var stats = ejRepo.findByMetricaAndTorneoIdOrderByValorDesc("goles", id).stream()
                .filter(s -> s.getValor() > 0)
                .limit(20)
                .toList();
        var jugadores = jugadorRepo.findAllById(stats.stream().map(s -> s.getJugadorId()).toList()).stream()
                .collect(java.util.stream.Collectors.toMap(dev.solomillo.domain.Jugador::getId, j -> j));
        // Cargamos los equipos en batch por id: open-in-view está deshabilitado, así que no
        // podemos recorrer la asociación LAZY jugador.getEquipo() fuera de la transacción.
        var equipoIds = jugadores.values().stream()
                .map(j -> j.getEquipo() != null ? j.getEquipo().getId() : null)
                .filter(java.util.Objects::nonNull).distinct().toList();
        var equipos = equipoRepo.findAllById(equipoIds).stream()
                .collect(java.util.stream.Collectors.toMap(dev.solomillo.domain.Equipo::getId, e -> e));

        var result = new java.util.ArrayList<Map<String, Object>>();
        int pos = 1;
        for (var s : stats) {
            var jugador = jugadores.get(s.getJugadorId());
            if (jugador == null) continue;
            Long equipoId = jugador.getEquipo() != null ? jugador.getEquipo().getId() : null;
            var equipo = equipoId != null ? equipos.get(equipoId) : null;
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("posicion", pos++);
            m.put("jugador_id", jugador.getId());
            m.put("nombre", jugador.getNombre());
            m.put("equipo_id", equipoId);
            m.put("equipo", equipo != null ? equipo.getNombre() : "");
            m.put("escudo", equipo != null && equipo.getEscudo() != null ? equipo.getEscudo() : "");
            m.put("goles", (int) s.getValor());
            result.add(m);
        }
        return result;
    }

    @GetMapping("/jugadores/{id}/estadisticas")
    public List<Map<String, Object>> statsJugador(@PathVariable Long id) {
        return ejRepo.findByJugadorId(id).stream().map(s ->
                Map.<String, Object>of("metrica", s.getMetrica(), "valor", s.getValor(), "torneo_id", s.getTorneoId())
        ).toList();
    }

    @GetMapping("/equipos/{id}/estadisticas")
    public List<Map<String, Object>> statsEquipo(@PathVariable Long id) {
        return eeRepo.findByEquipoId(id).stream().map(s ->
                Map.<String, Object>of("metrica", s.getMetrica(), "valor", s.getValor(), "torneo_id", s.getTorneoId())
        ).toList();
    }

    @GetMapping("/partidos/{id}/alertas")
    public List<Map<String, Object>> alertas(@PathVariable Long id) {
        return alertaRepo.findByPartidoIdOrderByCreadoEnDesc(id).stream().map(a ->
                Map.<String, Object>of("tipo", a.getTipo(), "mensaje", a.getMensaje(),
                        "creado_en", a.getCreadoEn().toString())
        ).toList();
    }

    @PostMapping("/ingest/{fuente}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('admin_sistema')")
    public Map<String, String> ingestar(@PathVariable String fuente,
                                         @RequestBody Map<String, Object> payload,
                                         @AuthenticationPrincipal String email) {
        try {
            var evento = registry.get(fuente).normalizar(payload);
            motor.procesar(evento, fuente);
            audit.registrar(email, "ingest:" + fuente, "ok", evento.tipo());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            audit.registrar(email, "ingest:" + fuente, "error", e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
        return Map.of("status", "accepted");
    }
}
