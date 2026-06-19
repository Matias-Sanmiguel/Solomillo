package dev.solomillo.api;

import dev.solomillo.events.MotorProcesamiento;
import dev.solomillo.ingest.IngestRegistry;
import dev.solomillo.rankings.Posicion;
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
                        "estadio", e.getEstadio() != null ? e.getEstadio() : "",
                        "grupo", e.getGrupo() != null ? e.getGrupo() : "")
        ).toList();
    }

    /** Tabla de posiciones del Mundial 2026 agrupada por grupo (A-L), con forma reciente. */
    @GetMapping("/clasificacion")
    public Map<String, List<Map<String, Object>>> clasificacion() {
        var mundial = torneoRepo.findByNombreAndTemporada("Copa Mundial FIFA 2026", "2026").orElse(null);
        if (mundial == null) return Map.of();
        Long tid = mundial.getId();

        // Forma reciente (últimos 5) por equipo a partir de los partidos finalizados del torneo.
        Map<Long, List<String>> forma = new java.util.HashMap<>();
        partidoRepo.findByEstadoOrderByFechaHoraAsc(dev.solomillo.domain.EstadoPartido.FINALIZADO).stream()
                .filter(p -> p.getTorneo().getId().equals(tid)
                        && p.getEquipoLocal() != null && p.getEquipoVisitante() != null)
                .forEach(p -> {
                    int gl = p.getGolesLocal() == null ? 0 : p.getGolesLocal();
                    int gv = p.getGolesVisitante() == null ? 0 : p.getGolesVisitante();
                    Long l = p.getEquipoLocal().getId(), v = p.getEquipoVisitante().getId();
                    forma.computeIfAbsent(l, k -> new java.util.ArrayList<>()).add(gl > gv ? "W" : gl == gv ? "D" : "L");
                    forma.computeIfAbsent(v, k -> new java.util.ArrayList<>()).add(gv > gl ? "W" : gl == gv ? "D" : "L");
                });

        Map<String, List<Map<String, Object>>> porGrupo = new java.util.TreeMap<>();
        for (Posicion pos : posicionRepo.findByTorneoId(tid)) {
            var e = equipoRepo.findById(pos.getEquipoId()).orElse(null);
            if (e == null || e.getGrupo() == null) continue;
            int pj = pos.getGanados() + pos.getEmpatados() + pos.getPerdidos();
            List<String> f = forma.getOrDefault(pos.getEquipoId(), List.of());
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("equipo_id", e.getId());
            row.put("nombre", e.getNombre());
            row.put("escudo", e.getEscudo());
            row.put("pj", pj);
            row.put("g", pos.getGanados());
            row.put("e", pos.getEmpatados());
            row.put("p", pos.getPerdidos());
            row.put("gf", pos.getGolesFavor());
            row.put("gc", pos.getGolesContra());
            row.put("dg", pos.getGolesFavor() - pos.getGolesContra());
            row.put("pts", pos.getPuntos());
            row.put("forma", f.subList(Math.max(0, f.size() - 5), f.size()));
            porGrupo.computeIfAbsent(e.getGrupo(), k -> new java.util.ArrayList<>()).add(row);
        }
        // Orden dentro del grupo: puntos, luego diferencia de gol, luego goles a favor.
        porGrupo.values().forEach(list -> list.sort((a, b) -> {
            int c = Integer.compare((int) b.get("pts"), (int) a.get("pts"));
            if (c != 0) return c;
            c = Integer.compare((int) b.get("dg"), (int) a.get("dg"));
            if (c != 0) return c;
            return Integer.compare((int) b.get("gf"), (int) a.get("gf"));
        }));
        return porGrupo;
    }

    /** Máximos goleadores y asistentes del Mundial 2026 (top 12). */
    @GetMapping("/goleadores")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Map<String, List<Map<String, Object>>> goleadores() {
        var mundial = torneoRepo.findByNombreAndTemporada("Copa Mundial FIFA 2026", "2026").orElse(null);
        if (mundial == null) return Map.of("goleadores", List.of(), "asistentes", List.of());
        Long tid = mundial.getId();
        return Map.of(
                "goleadores", topJugadores(tid, "goles"),
                "asistentes", topJugadores(tid, "asistencias"));
    }

    private List<Map<String, Object>> topJugadores(Long torneoId, String metrica) {
        return ejRepo.findByTorneoIdAndMetricaOrderByValorDesc(torneoId, metrica).stream()
                .filter(s -> s.getValor() > 0)
                .limit(12)
                .map(s -> {
                    var j = jugadorRepo.findById(s.getJugadorId()).orElse(null);
                    var eq = j != null ? j.getEquipo() : null;
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("jugador_id", s.getJugadorId());
                    m.put("nombre", j != null ? j.getNombre() : "—");
                    m.put("posicion", j != null && j.getPosicion() != null ? j.getPosicion() : "");
                    m.put("equipo", eq != null ? eq.getNombre() : "");
                    m.put("escudo", eq != null && eq.getEscudo() != null ? eq.getEscudo() : "");
                    m.put("valor", (int) s.getValor());
                    return m;
                }).toList();
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
            // Partidos de llave aún sin definir (TBD) tienen equipos null.
            m.put("local_id", p.getEquipoLocal() != null ? p.getEquipoLocal().getId() : null);
            m.put("visitante_id", p.getEquipoVisitante() != null ? p.getEquipoVisitante().getId() : null);
            m.put("fecha_hora", p.getFechaHora() != null ? p.getFechaHora().toString() : null);
            m.put("estadio", p.getEstadio() != null ? p.getEstadio() : "");
            m.put("grupo", p.getGrupo());
            m.put("ronda", p.getRonda() != null ? p.getRonda().name() : null);
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

    @GetMapping("/jugadores/{id}/estadisticas")
    public List<Map<String, Object>> statsJugador(@PathVariable Long id) {
        return ejRepo.findByJugadorId(id).stream().map(s ->
                Map.<String, Object>of("metrica", s.getMetrica(), "valor", s.getValor(), "torneo_id", s.getTorneoId())
        ).toList();
    }

    @GetMapping("/equipos/{id}/estadisticas")
    public List<Map<String, Object>> statsEquipo(@PathVariable Long id) {
        List<Posicion> posiciones = posicionRepo.findByEquipoId(id);
        int pj = 0, v = 0, e = 0, d = 0, gf = 0, gc = 0, pts = 0;
        for (Posicion p : posiciones) {
            v += p.getGanados(); e += p.getEmpatados(); d += p.getPerdidos();
            gf += p.getGolesFavor(); gc += p.getGolesContra(); pts += p.getPuntos();
        }
        pj = v + e + d;

        java.util.Map<String, Double> tarjMap = new java.util.HashMap<>();
        for (var s : eeRepo.findByEquipoId(id)) {
            if (s.getMetrica().equals("tarjetas_amarillas") || s.getMetrica().equals("tarjetas_rojas")) {
                tarjMap.merge(s.getMetrica(), s.getValor(), Double::sum);
            }
        }

        List<Map<String, Object>> result = new java.util.ArrayList<>();
        long torneoId = posiciones.isEmpty() ? 0 : posiciones.get(0).getTorneoId();
        result.add(stat("partidos_jugados", pj, torneoId));
        result.add(stat("victorias", v, torneoId));
        result.add(stat("empates", e, torneoId));
        result.add(stat("derrotas", d, torneoId));
        result.add(stat("goles_a_favor", gf, torneoId));
        result.add(stat("goles_en_contra", gc, torneoId));
        result.add(stat("diferencia_gol", gf - gc, torneoId));
        result.add(stat("puntos", pts, torneoId));
        result.add(stat("promedio_gol", pj > 0 ? Math.round((double) gf / pj * 100.0) / 100.0 : 0.0, torneoId));
        result.add(stat("tarjetas_amarillas", tarjMap.getOrDefault("tarjetas_amarillas", 0.0), torneoId));
        result.add(stat("tarjetas_rojas", tarjMap.getOrDefault("tarjetas_rojas", 0.0), torneoId));
        return result;
    }

    private Map<String, Object> stat(String metrica, Number valor, long torneoId) {
        return Map.of("metrica", metrica, "valor", valor, "torneo_id", torneoId);
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
